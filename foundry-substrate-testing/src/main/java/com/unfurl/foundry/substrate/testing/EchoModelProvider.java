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
public final class EchoModelProvider implements ModelProvider {
    private final long promptTokens;
    private final long completionTokens;

    public EchoModelProvider() {
        this(10, 5);
    }

    public EchoModelProvider(long promptTokens, long completionTokens) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
    }

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
