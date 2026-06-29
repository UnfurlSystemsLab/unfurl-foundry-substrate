package com.unfurl.foundry.substrate.testing;

import com.unfurl.foundry.substrate.model.ModelRequest;
import com.unfurl.foundry.substrate.model.ModelResponse;
import com.unfurl.foundry.substrate.ports.ModelProvider;
import com.unfurl.substrate.policy.ExecutionContext;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Fake model provider that returns a pre-scripted sequence of responses (for tool-loop tests). */
/**
 * class for the Foundry AI substrate surface; documents the ScriptedModelProvider contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public final class ScriptedModelProvider implements ModelProvider {
    private final Deque<ModelResponse> responses;

/**
 * Constructs ScriptedModelProvider with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public ScriptedModelProvider(List<ModelResponse> responses) {
        this.responses = new ArrayDeque<>(responses);
    }

/**
 * Performs the complete operation for this component, translating validated inputs into the domain result expected by callers.
 */
    @Override
    public ModelResponse complete(ModelRequest request, ExecutionContext context) {
        if (responses.isEmpty()) {
            throw new IllegalStateException("ScriptedModelProvider exhausted");
        }
        return responses.removeFirst();
    }
}
