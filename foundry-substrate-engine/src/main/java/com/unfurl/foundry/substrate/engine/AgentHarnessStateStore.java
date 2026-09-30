package com.unfurl.foundry.substrate.engine;

import com.unfurl.foundry.substrate.agent.AgentHarnessDefinition;
import com.unfurl.foundry.substrate.runstate.AgentHarnessRunState;
import com.unfurl.substrate.policy.ExecutionContext;

import java.util.Optional;
import java.util.Map;

/** Strategy port: stores and restores an embedded harness definition with its latest snapshot. */
public interface AgentHarnessStateStore {
    /** Store operation: records the definition and immutable latest state as one execution unit. */
    void save(AgentHarnessExecution execution, ExecutionContext context);

    /** Load operation: restores one execution by run identity within the supplied context. */
    Optional<AgentHarnessExecution> load(String runId, ExecutionContext context);

    /** Compare-and-set Strategy: claims a resume/cancel transition before any further agent dispatch.
     * Hosts validate signal authorization and may return a sanitized execution; stale claims return empty.
     */
    Optional<AgentHarnessExecution> transition(AgentHarnessExecution expected,
            AgentHarnessExecution updated, Map<String, Object> signal, ExecutionContext context);

    /** Value Object: couples the definition required for resume with the latest harness snapshot. */
    record AgentHarnessExecution(AgentHarnessDefinition definition, AgentHarnessRunState state) {
        /** Canonical constructor: validates definition/state identity before a store accepts it. */
        public AgentHarnessExecution {
            if (definition == null || state == null) throw new IllegalArgumentException("harness execution is incomplete");
            if (!definition.id().equals(state.harnessId()) || !definition.version().equals(state.harnessVersion())) {
                throw new IllegalArgumentException("harness definition and state identity differ");
            }
        }
    }
}
