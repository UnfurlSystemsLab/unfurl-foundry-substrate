package com.unfurl.foundry.substrate.ports.adapters;

import com.unfurl.foundry.substrate.model.Message;
import com.unfurl.foundry.substrate.model.ModelResponse;
import com.unfurl.foundry.substrate.model.ModelUsage;
import com.unfurl.foundry.substrate.ports.ToolCallResult;
import com.unfurl.substrate.domain.NodeDefinition;
import com.unfurl.substrate.ports.NodeExecutionRequest;
import com.unfurl.substrate.ports.NodeExecutionResult;
import com.unfurl.substrate.ports.NodeExecutionResultStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiNodeExecutorAdapterTest {

    @Test
    void toolAdapterInvokesToolExecutor() {
        ToolExecutorNodeExecutor adapter = new ToolExecutorNodeExecutor((request, context) ->
                ToolCallResult.success(Map.of("tool", request.toolName(), "value", request.arguments().get("value"))));

        NodeExecutionResult result = adapter.execute(
                new NodeExecutionRequest("exec", node("lookup"), Map.of("arguments", Map.of("value", 42)), Map.of()),
                null);

        assertThat(result.status()).isEqualTo(NodeExecutionResultStatus.COMPLETED);
        assertThat(result.output()).containsEntry("tool", "lookup").containsEntry("value", 42);
    }

    @Test
    void providerAdapterExposesTypedAttribution() {
        ModelProviderNodeExecutor adapter = new ModelProviderNodeExecutor((request, context) ->
                new ModelResponse(Message.assistant("ok"), List.of(), "stop", new ModelUsage(1, 2),
                        Map.of(), "provider", new BigDecimal("0.12")));

        NodeExecutionResult result = adapter.execute(
                new NodeExecutionRequest("exec", node("model"), Map.of("messages", List.of(Message.user("hi"))), Map.of()),
                null);

        assertThat(result.status()).isEqualTo(NodeExecutionResultStatus.COMPLETED);
        assertThat(result.output()).containsEntry("providerName", "provider");
        assertThat(result.output().get("estimatedCostUsd")).isEqualTo(new BigDecimal("0.12"));
    }

    private NodeDefinition node(String uses) {
        return new NodeDefinition("node", null, uses, null, null, Map.of(), Map.of(), Map.of(),
                List.of(), null, null, null, null, null, Map.of(), null, false);
    }
}
