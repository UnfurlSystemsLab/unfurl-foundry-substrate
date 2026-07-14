package com.unfurl.foundry.substrate.offers;

import com.unfurl.dcp.broker.DefaultCompositionBroker;
import com.unfurl.dcp.broker.Disposition;
import com.unfurl.dcp.broker.DispositionKind;
import com.unfurl.dcp.broker.RegistrationHandle;
import com.unfurl.dcp.claim.*;
import com.unfurl.dcp.contract.*;
import com.unfurl.dcp.contract.ContractInvocableAdapter;
import com.unfurl.dcp.spi.CapabilityRegistrar;
import com.unfurl.dcp.spi.ContractStore;
import com.unfurl.dcp.trust.*;
import com.unfurl.foundry.substrate.ports.ToolCallResult;
import com.unfurl.foundry.substrate.ports.ToolExecutor;
import com.unfurl.substrate.composition.ContractInvocable;
import com.unfurl.substrate.composition.ContractInvocation;
import com.unfurl.substrate.composition.ContractInvocationResult;
import com.unfurl.substrate.policy.ExecutionContext;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DcpFoundryBrokerIntegrationTest {
    @Test
    void brokerAcceptRegistersFoundryFactoryInvocableAndInvocationWorksEndToEnd() {
        KeyPair keyPair = keyPair();
        CompositionContract contract = contract();
        FrozenContract frozen = new ContractFreezer(new SigningKeyRef("test-key")).freeze(contract, signer(keyPair));
        InMemoryContractStore store = new InMemoryContractStore(frozen);
        InMemoryCapabilityRegistrar registrar = new InMemoryCapabilityRegistrar();
        AtomicReference<Map<String, Object>> observedToolMetadata = new AtomicReference<>();
        ToolExecutor tool = (request, context) -> {
            observedToolMetadata.set(request.metadata());
            return ToolCallResult.success(Map.of("echo", request.arguments().get("input")));
        };
        FoundryContractInvocableFactory factory = new FoundryContractInvocableFactory(
                null,
                Map.of(),
                Map.of(AiOffers.TOOL_CALL, tool),
                Map.of(),
                Map.of());
        DefaultCompositionBroker broker = new DefaultCompositionBroker(
                store,
                new OfflineContractVerifier(),
                VerificationKeySet.of(List.of(new VerificationKey("test-key", keyPair.getPublic()))),
                null,
                null);

        Disposition disposition = broker.present(providerClaim(), AiOffers.TOOL_CALL, ExecutionContext.empty());
        RegistrationHandle handle = broker.accept(disposition, registrar, factory, ExecutionContext.empty());
        ContractInvocationResult result = registrar.registration(AiOffers.TOOL_CALL).orElseThrow()
                .invoke(new ContractInvocation("ignored", "execute", "consumer", "provider",
                        Map.of("input", "hello"), "corr-1", Map.of(), "hash", Map.of()), ExecutionContext.empty());

        assertThat(disposition.kind()).isEqualTo(DispositionKind.ACCEPT);
        assertThat(handle.exposedCapabilityNames()).containsExactly(AiOffers.TOOL_CALL);
        assertThat(result.success()).isTrue();
        assertThat(result.output()).containsEntry("echo", "hello");
        assertThat(observedToolMetadata.get())
                .containsEntry(ContractInvocableAdapter.CONTRACT_VERSION_KEY, "1.0.0")
                .containsEntry(ContractInvocableAdapter.TRUST_TIER_KEY, "NEUTRAL")
                .containsEntry(ContractInvocableAdapter.REGISTRATION_HANDLE_KEY, "urn:contract@1.0.0");
        assertThat(result.metadata())
                .containsEntry(ContractInvocableAdapter.CONTRACT_VERSION_KEY, "1.0.0")
                .containsEntry(ContractInvocableAdapter.TRUST_TIER_KEY, "NEUTRAL");
    }

    private Claim providerClaim() {
        Offer toolOffer = new Offer(AiOffers.TOOL_CALL, "Call a tool", ConsumerAccess.ANY,
                new OfferInterface(InterfaceKind.IN_PROCESS, Map.of("operation", "execute")),
                Stability.STABLE, "1.0.0", false, null);
        return new Claim(
                new Identity(URI.create("urn:provider"), "Provider", ComponentKind.COMPONENT, "1.0.0", "Unfurl", URI.create("urn:publisher")),
                new DomainAssertion("provider", List.of(new Concern("tools", "tool execution", null, List.of(), List.of())), List.of("no silent ownership changes")),
                List.of(new Refusal("billing", "billing belongs elsewhere", "urn:billing")),
                new Dependencies(List.of()),
                List.of(toolOffer),
                new ConflictResolution(List.of(), List.of(), false),
                null,
                new IntegrationPorts(Map.of()),
                AiOffers.faultPolicyFor(List.of(toolOffer)),
                new ClaimMetadata("0.2.0", "1.0.0", Instant.EPOCH, Map.of()));
    }

    private CompositionContract contract() {
        return new CompositionContract(
                URI.create("urn:contract"),
                "1.0.0",
                new Parties(new Party(URI.create("urn:consumer"), "1.0.0"), new Party(URI.create("urn:provider"), "1.0.0")),
                new Binding("tool-needed", AiOffers.TOOL_CALL, ">=1.0.0"),
                new DataMapping(Map.of(), Map.of()),
                new Transport(TransportKind.IN_PROCESS, Map.of()),
                new Expectations(1000, true, false, true),
                new Provenance(CreatedBy.FABRIC, NegotiationMode.C2C, "fabric-model", "0.2.0", false, Instant.EPOCH),
                new Trust(TrustTier.NEUTRAL),
                new Invalidation(List.of(InvalidationTrigger.CLAIM_VERSION_CHANGED), RuntimeViolationPolicy.HARD_FAIL),
                null);
    }

    private ContractSigner signer(KeyPair keyPair) {
        return (canonicalContractBytes, keyRef) -> {
            try {
                Signature signature = Signature.getInstance("SHA256withRSA");
                signature.initSign(keyPair.getPrivate());
                signature.update(canonicalContractBytes);
                return new SignedContract(canonicalContractBytes, signature.sign(), "SHA256withRSA", keyRef.keyId());
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        };
    }

    private KeyPair keyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static final class InMemoryContractStore implements ContractStore {
        private final FrozenContract frozen;

        private InMemoryContractStore(FrozenContract frozen) {
            this.frozen = frozen;
        }

        @Override
        public Optional<FrozenContract> findByProvider(URI providerClaimUri, String providerClaimVersion, String providerCapability) {
            return providerClaimUri.equals(frozen.contract().parties().provider().claimUri())
                    && providerClaimVersion.equals(frozen.contract().parties().provider().claimVersion())
                    && providerCapability.equals(frozen.contract().binding().providerCapability())
                    ? Optional.of(frozen)
                    : Optional.empty();
        }

        @Override
        public Optional<FrozenContract> findById(URI contractId, String contractVersion) {
            return contractId.equals(frozen.contract().contractId())
                    && contractVersion.equals(frozen.contract().contractVersion())
                    ? Optional.of(frozen)
                    : Optional.empty();
        }
    }

    private static final class InMemoryCapabilityRegistrar implements CapabilityRegistrar {
        private final Map<String, ContractInvocable> registrations = new LinkedHashMap<>();

        @Override
        public void register(String capabilityName, ContractInvocable invocable, ExecutionContext context) {
            registrations.put(capabilityName, invocable);
        }

        @Override
        public void unregister(String capabilityName, ExecutionContext context) {
            registrations.remove(capabilityName);
        }

        private Optional<ContractInvocable> registration(String capabilityName) {
            return Optional.ofNullable(registrations.get(capabilityName));
        }
    }
}
