package com.unfurl.foundry.substrate.testing;

import com.unfurl.foundry.substrate.ports.ToolCallRequest;
import com.unfurl.foundry.substrate.ports.ToolCallResult;
import com.unfurl.foundry.substrate.ports.ToolExecutor;
import com.unfurl.substrate.policy.ExecutionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Fake tool executor that records calls and returns a fixed output. */
/**
 * class for the Foundry AI substrate surface; documents the RecordingToolExecutor contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public final class RecordingToolExecutor implements ToolExecutor {
    private final List<ToolCallRequest> calls = new ArrayList<>();
    private final Map<String, Object> output;

/**
 * Constructs RecordingToolExecutor with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public RecordingToolExecutor(Map<String, Object> output) {
        this.output = Map.copyOf(output);
    }

/**
 * Performs the execute operation for this component, translating validated inputs into the domain result expected by callers.
 */
    @Override
    public ToolCallResult execute(ToolCallRequest request, ExecutionContext context) {
        calls.add(request);
        return ToolCallResult.success(output);
    }

/**
 * Implements the calls helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    public List<ToolCallRequest> calls() {
        return List.copyOf(calls);
    }
}
