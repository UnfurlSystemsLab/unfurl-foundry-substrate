package com.unfurl.foundry.substrate.testing;

import com.unfurl.foundry.substrate.model.ModelRequest;
import com.unfurl.foundry.substrate.model.ModelResponse;
import com.unfurl.foundry.substrate.ports.ModelProvider;
import com.unfurl.substrate.policy.ExecutionContext;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Fake model provider that returns a pre-scripted sequence of responses (for tool-loop tests). */
public final class ScriptedModelProvider implements ModelProvider {
    private final Deque<ModelResponse> responses;

    public ScriptedModelProvider(List<ModelResponse> responses) {
        this.responses = new ArrayDeque<>(responses);
    }

    @Override
    public ModelResponse complete(ModelRequest request, ExecutionContext context) {
        if (responses.isEmpty()) {
            throw new IllegalStateException("ScriptedModelProvider exhausted");
        }
        return responses.removeFirst();
    }
}
