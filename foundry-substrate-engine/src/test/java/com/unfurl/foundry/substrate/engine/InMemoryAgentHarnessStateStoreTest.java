package com.unfurl.foundry.substrate.engine;

import com.unfurl.foundry.substrate.agent.AgentDefinition;
import com.unfurl.foundry.substrate.agent.AgentHarnessDefinition;
import com.unfurl.foundry.substrate.agent.AgentHarnessLoopPolicy;
import com.unfurl.foundry.substrate.runstate.AgentHarnessRunState;
import com.unfurl.foundry.substrate.runstate.AgentHarnessStatus;
import com.unfurl.substrate.policy.ExecutionContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Contract test: verifies the default embedded harness state Strategy retains resumable definitions. */
class InMemoryAgentHarnessStateStoreTest {
    /** Verifies definition and state are restored together for an embedded resume. */
    @Test
    void storesCompleteHarnessExecution() {
        AgentDefinition agent = new AgentDefinition("agent", "1", Map.of(), List.of(), List.of(),
                Map.of(), null, List.of());
        AgentHarnessDefinition definition = new AgentHarnessDefinition("harness", "1", Map.of(), agent,
                AgentHarnessLoopPolicy.singleTurn());
        AgentHarnessRunState state = new AgentHarnessRunState(null, "run-1", "harness", "1",
                AgentHarnessStatus.WAITING_FOR_USER, 1, Map.of(), Map.of(), List.of(), Map.of(),
                null, null, Instant.EPOCH, Instant.EPOCH, null);
        AgentHarnessStateStore.AgentHarnessExecution execution =
                new AgentHarnessStateStore.AgentHarnessExecution(definition, state);
        InMemoryAgentHarnessStateStore store = new InMemoryAgentHarnessStateStore();

        store.save(execution, ExecutionContext.empty());

        assertThat(store.load("run-1", ExecutionContext.empty())).contains(execution);
    }
}
