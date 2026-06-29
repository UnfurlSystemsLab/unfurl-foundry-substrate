package com.unfurl.foundry.substrate.testing;

import com.unfurl.foundry.substrate.model.Message;
import com.unfurl.foundry.substrate.model.MessageRole;
import com.unfurl.foundry.substrate.model.ModelRequest;
import com.unfurl.foundry.substrate.model.ModelResponse;
import com.unfurl.foundry.substrate.model.ModelUsage;
import com.unfurl.foundry.substrate.ports.ModelProvider;
import com.unfurl.substrate.policy.ExecutionContext;

import java.util.Map;

/** Fake model provider that echoes the last user message with deterministic token usage. */
/**
 * class for the Foundry AI substrate surface; documents the EchoModelProvider contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public final class EchoModelProvider implements ModelProvider {
    private final long promptTokens;
    private final long completionTokens;

/**
 * Constructs EchoModelProvider with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public EchoModelProvider() {
        this(10, 5);
    }

/**
 * Constructs EchoModelProvider with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public EchoModelProvider(long promptTokens, long completionTokens) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
    }

/**
 * Performs the complete operation for this component, translating validated inputs into the domain result expected by callers.
 */
    @Override
    public ModelResponse complete(ModelRequest request, ExecutionContext context) {
        String lastUser = request.messages().stream()
                .filter(m -> m.role() == MessageRole.USER)
                .map(Message::content)
                .reduce((first, second) -> second)
                .orElse("");
        return new ModelResponse(Message.assistant("echo: " + lastUser), java.util.List.of(),
                "stop", new ModelUsage(promptTokens, completionTokens), Map.of(), "echo", java.math.BigDecimal.ZERO);
    }
}
