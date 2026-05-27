package com.unfurl.foundry.substrate.testing;

import com.unfurl.foundry.substrate.ports.ToolCallRequest;
import com.unfurl.foundry.substrate.ports.ToolCallResult;
import com.unfurl.foundry.substrate.ports.ToolExecutor;
import com.unfurl.substrate.policy.ExecutionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Fake tool executor that records calls and returns a fixed output. */
public final class RecordingToolExecutor implements ToolExecutor {
    private final List<ToolCallRequest> calls = new ArrayList<>();
    private final Map<String, Object> output;

    public RecordingToolExecutor(Map<String, Object> output) {
        this.output = Map.copyOf(output);
    }

    @Override
    public ToolCallResult execute(ToolCallRequest request, ExecutionContext context) {
        calls.add(request);
        return ToolCallResult.success(output);
    }

    public List<ToolCallRequest> calls() {
        return List.copyOf(calls);
    }
}
