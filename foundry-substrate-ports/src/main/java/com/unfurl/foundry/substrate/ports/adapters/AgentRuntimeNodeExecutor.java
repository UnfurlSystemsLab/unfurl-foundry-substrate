package com.unfurl.foundry.substrate.ports.adapters;

import com.unfurl.foundry.substrate.agent.AgentDefinition;
import com.unfurl.foundry.substrate.ports.AgentRuntime;
import com.unfurl.foundry.substrate.runstate.AgentPhaseState;
import com.unfurl.foundry.substrate.runstate.AgentRunState;
import com.unfurl.foundry.substrate.runstate.AgentRunStatus;
import com.unfurl.substrate.policy.ExecutionContext;
import com.unfurl.substrate.ports.NodeExecutionRequest;
import com.unfurl.substrate.ports.NodeExecutionResult;
import com.unfurl.substrate.ports.NodeExecutor;

import java.util.LinkedHashMap;
import java.util.Map;

/** Substrate node adapter for the canonical {@code agent.run} AI capability. */
public final class AgentRuntimeNodeExecutor implements NodeExecutor {
    private final AgentRuntime runtime;
    private final AgentDefinition agent;

    public AgentRuntimeNodeExecutor(AgentRuntime runtime, AgentDefinition agent) {
        this.runtime = runtime;
        this.agent = agent;
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionRequest request, ExecutionContext context) {
        AgentRunState run = runtime.start(agent, request.input(), context);
        if (run.status() != AgentRunStatus.COMPLETED) {
            return NodeExecutionResult.failed(
                    run.errorCode() == null ? "AGENT_NOT_COMPLETED" : run.errorCode(),
                    run.errorMessage() == null ? "Agent run ended in status " + run.status() : run.errorMessage());
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("runId", run.runId());
        output.put("status", run.status().name());
        Map<String, Object> phaseOutputs = new LinkedHashMap<>();
        for (AgentPhaseState phase : run.phases().values()) {
            phaseOutputs.put(phase.phaseId(), phase.output());
        }
        output.put("phases", phaseOutputs);
        output.put("cost", run.cost());
        return NodeExecutionResult.completed(output);
    }
}
