package com.unfurl.foundry.substrate.engine;

import com.unfurl.foundry.substrate.agent.AgentDefinition;
import com.unfurl.foundry.substrate.agent.AgentHarnessDefinition;
import com.unfurl.foundry.substrate.agent.AgentHarnessLoopPolicy;
import com.unfurl.foundry.substrate.agent.AgentPhase;
import com.unfurl.foundry.substrate.ports.AgentRuntime;
import com.unfurl.foundry.substrate.runstate.AgentHarnessRunState;
import com.unfurl.foundry.substrate.runstate.AgentHarnessStatus;
import com.unfurl.foundry.substrate.runstate.AgentPhaseState;
import com.unfurl.foundry.substrate.runstate.AgentPhaseStatus;
import com.unfurl.foundry.substrate.runstate.AgentRunState;
import com.unfurl.foundry.substrate.runstate.AgentRunStatus;
import com.unfurl.foundry.substrate.runstate.CostAccounting;
import com.unfurl.foundry.substrate.terminal.AgentTerminalStatus;
import com.unfurl.substrate.policy.ExecutionContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddedAgentHarnessRuntimeTest {

    @Test
    void continuesWithNextInputUntilCompleted() {
        AgentDefinition agent = agent(Map.of());
        ScriptedAgentRuntime agentRuntime = new ScriptedAgentRuntime(List.of(
                completedRun("inner-1", agent, Map.of("kind", "continue", "nextInput", Map.of("step", 2))),
                completedRun("inner-2", agent, Map.of("kind", "complete", "result", "done"))));
        EmbeddedAgentHarnessRuntime harnessRuntime = new EmbeddedAgentHarnessRuntime(agentRuntime);

        AgentHarnessRunState run = harnessRuntime.start(harness(agent, 3), Map.of("step", 1), ExecutionContext.empty());

        assertThat(run.status()).isEqualTo(AgentHarnessStatus.COMPLETED);
        assertThat(run.turn()).isEqualTo(2);
        assertThat(run.output()).containsEntry("result", "done");
        assertThat(run.observations()).hasSize(2);
        assertThat(agentRuntime.inputs()).containsExactly(Map.of("step", 1), Map.of("step", 2));
    }

    @Test
    void waitsWhenAgentAsksClarificationQuestion() {
        AgentDefinition agent = agent(Map.of());
        ScriptedAgentRuntime agentRuntime = new ScriptedAgentRuntime(List.of(
                completedRun("inner-1", agent, Map.of("kind", "clarify", "questions", List.of("Which catalog?")))));
        EmbeddedAgentHarnessRuntime harnessRuntime = new EmbeddedAgentHarnessRuntime(agentRuntime);

        AgentHarnessRunState run = harnessRuntime.start(harness(agent, 3), Map.of(), ExecutionContext.empty());

        assertThat(run.status()).isEqualTo(AgentHarnessStatus.WAITING_FOR_USER);
        assertThat(run.output().get("questions")).isEqualTo(List.of("Which catalog?"));
        assertThat(run.terminalEnvelope().status()).isEqualTo(AgentTerminalStatus.WAITING_FOR_USER);
        assertThat(run.observations().getFirst().terminalEnvelope()).isEqualTo(run.terminalEnvelope());
    }

    @Test
    void resumesWaitingRunWithSignalInput() {
        AgentDefinition agent = agent(Map.of());
        ScriptedAgentRuntime agentRuntime = new ScriptedAgentRuntime(List.of(
                completedRun("inner-1", agent, Map.of("kind", "clarify", "questions", List.of("Which catalog?"))),
                completedRun("inner-2", agent, Map.of("kind", "complete", "answer", "ok"))));
        EmbeddedAgentHarnessRuntime harnessRuntime = new EmbeddedAgentHarnessRuntime(agentRuntime);

        AgentHarnessRunState waiting = harnessRuntime.start(harness(agent, 3), Map.of("step", 1), ExecutionContext.empty());
        AgentHarnessRunState resumed = harnessRuntime.resume(waiting.runId(), Map.of("catalog", "flow"), ExecutionContext.empty());

        assertThat(resumed.status()).isEqualTo(AgentHarnessStatus.COMPLETED);
        assertThat(resumed.turn()).isEqualTo(2);
        assertThat(agentRuntime.inputs().get(1))
                .containsEntry("step", 1)
                .containsEntry("signal", Map.of("catalog", "flow"));
    }

    @Test
    void stopsOnGapOutput() {
        AgentDefinition agent = agent(Map.of());
        ScriptedAgentRuntime agentRuntime = new ScriptedAgentRuntime(List.of(
                completedRun("inner-1", agent, Map.of("kind", "gap", "unmet", List.of("rag.search")))));
        EmbeddedAgentHarnessRuntime harnessRuntime = new EmbeddedAgentHarnessRuntime(agentRuntime);

        AgentHarnessRunState run = harnessRuntime.start(harness(agent, 2), Map.of(), ExecutionContext.empty());

        assertThat(run.status()).isEqualTo(AgentHarnessStatus.GAP);
        assertThat(run.output().get("unmet")).isEqualTo(List.of("rag.search"));
    }

    /** Verifies approval is resumable and its canonical terminal envelope survives the checkpoint. */
    @Test
    void resumesApprovalWaitWithSignalInput() {
        AgentDefinition agent = agent(Map.of());
        ScriptedAgentRuntime agentRuntime = new ScriptedAgentRuntime(List.of(
                completedRun("inner-1", agent, Map.of("kind", "approval", "questions", List.of("Approve?"))),
                completedRun("inner-2", agent, Map.of("kind", "complete", "approved", true))));
        EmbeddedAgentHarnessRuntime harnessRuntime = new EmbeddedAgentHarnessRuntime(agentRuntime);

        AgentHarnessRunState waiting = harnessRuntime.start(harness(agent, 3), Map.of(), ExecutionContext.empty());
        AgentHarnessRunState resumed = harnessRuntime.resume(
                waiting.runId(), Map.of("approved", true), ExecutionContext.empty());

        assertThat(waiting.status()).isEqualTo(AgentHarnessStatus.WAITING_FOR_APPROVAL);
        assertThat(waiting.terminalEnvelope().status()).isEqualTo(AgentTerminalStatus.WAITING_FOR_APPROVAL);
        assertThat(resumed.status()).isEqualTo(AgentHarnessStatus.COMPLETED);
    }

    /** Verifies an escalation can be resumed by explicit operator input within the remaining turn bound. */
    @Test
    void resumesEscalationWithExplicitSignal() {
        AgentDefinition agent = agent(Map.of());
        ScriptedAgentRuntime agentRuntime = new ScriptedAgentRuntime(List.of(
                completedRun("inner-1", agent, Map.of("kind", "escalated", "handoff", Map.of("reason", "review"))),
                completedRun("inner-2", agent, Map.of("kind", "complete"))));
        EmbeddedAgentHarnessRuntime harnessRuntime = new EmbeddedAgentHarnessRuntime(agentRuntime);
        AgentHarnessRunState waiting = harnessRuntime.start(harness(agent, 3), Map.of(), ExecutionContext.empty());

        AgentHarnessRunState resumed = harnessRuntime.resume(waiting.runId(),
                Map.of("reviewed", true), ExecutionContext.empty());

        assertThat(waiting.status()).isEqualTo(AgentHarnessStatus.ESCALATED);
        assertThat(resumed.status()).isEqualTo(AgentHarnessStatus.COMPLETED);
        assertThat(resumed.turn()).isEqualTo(2);
    }

    /** A policy-level approval failure becomes a resumable harness wait with token projection. */
    @Test
    void convertsToolApprovalDecisionIntoHarnessWait() {
        AgentDefinition agent = agent(Map.of());
        ScriptedAgentRuntime agentRuntime = new ScriptedAgentRuntime(List.of(
                approvalRun("inner-1", agent, "approval-1"),
                completedRun("inner-2", agent, Map.of("kind", "complete", "approved", true))));
        EmbeddedAgentHarnessRuntime harnessRuntime = new EmbeddedAgentHarnessRuntime(agentRuntime);

        AgentHarnessRunState waiting = harnessRuntime.start(harness(agent, 3), Map.of(), ExecutionContext.empty());
        AgentHarnessRunState resumed = harnessRuntime.resume(
                waiting.runId(), Map.of("approvalId", "approval-1"), ExecutionContext.empty());

        assertThat(waiting.status()).isEqualTo(AgentHarnessStatus.WAITING_FOR_APPROVAL);
        assertThat(waiting.output()).containsEntry("approvalId", "approval-1");
        assertThat(agentRuntime.contexts().get(1).metadata())
                .containsEntry("toolApprovalTokens", List.of("approval-1"));
        assertThat(resumed.status()).isEqualTo(AgentHarnessStatus.COMPLETED);
    }

    /** Verifies malformed clarification cannot escape the harness as a successful terminal result. */
    @Test
    void failsMalformedClarificationOutput() {
        AgentDefinition agent = agent(Map.of());
        EmbeddedAgentHarnessRuntime harnessRuntime = new EmbeddedAgentHarnessRuntime(new ScriptedAgentRuntime(List.of(
                completedRun("inner-1", agent, Map.of("kind", "clarify")))));

        AgentHarnessRunState run = harnessRuntime.start(harness(agent, 1), Map.of(), ExecutionContext.empty());

        assertThat(run.status()).isEqualTo(AgentHarnessStatus.FAILED);
        assertThat(run.errorCode()).isEqualTo("AGENT_OUTPUT_INVALID");
        assertThat(run.terminalEnvelope().error().code()).isEqualTo("AGENT_OUTPUT_INVALID");
    }

    @Test
    void failsWhenContinueExhaustsTurnPolicy() {
        AgentDefinition agent = agent(Map.of());
        ScriptedAgentRuntime agentRuntime = new ScriptedAgentRuntime(List.of(
                completedRun("inner-1", agent, Map.of("kind", "continue", "nextInput", Map.of("step", 2))),
                completedRun("inner-2", agent, Map.of("kind", "continue", "nextInput", Map.of("step", 3)))));
        EmbeddedAgentHarnessRuntime harnessRuntime = new EmbeddedAgentHarnessRuntime(agentRuntime);

        AgentHarnessRunState run = harnessRuntime.start(harness(agent, 2), Map.of("step", 1), ExecutionContext.empty());

        assertThat(run.status()).isEqualTo(AgentHarnessStatus.FAILED);
        assertThat(run.errorCode()).isEqualTo("HARNESS_MAX_TURNS_EXCEEDED");
        assertThat(run.turn()).isEqualTo(2);
    }

    @Test
    void selectsTerminalPhaseByMetadata() {
        AgentDefinition agent = agent(Map.of("terminalPhaseId", "final"));
        ScriptedAgentRuntime agentRuntime = new ScriptedAgentRuntime(List.of(completedRun("inner-1", agent,
                Map.of("kind", "scratch"), Map.of("kind", "complete", "value", 42))));
        EmbeddedAgentHarnessRuntime harnessRuntime = new EmbeddedAgentHarnessRuntime(agentRuntime);

        AgentHarnessRunState run = harnessRuntime.start(harness(agent, 1), Map.of(), ExecutionContext.empty());

        assertThat(run.status()).isEqualTo(AgentHarnessStatus.COMPLETED);
        assertThat(run.output()).containsEntry("value", 42);
    }

    /**
     * Fixture builder: creates a harness definition with a turn-bounded loop.
     */
    private AgentHarnessDefinition harness(AgentDefinition agent, int maxTurns) {
        return new AgentHarnessDefinition("harness", "1", Map.of(), agent,
                AgentHarnessLoopPolicy.boundedTurns(maxTurns));
    }

    /**
     * Fixture builder: creates either a single-phase or terminal-phase-aware
     * agent accepted by the harness validator.
     */
    private AgentDefinition agent(Map<String, Object> metadata) {
        List<AgentPhase> phases = metadata.containsKey("terminalPhaseId")
                ? List.of(phase("draft"), phase("final"))
                : List.of(phase("turn"));
        return new AgentDefinition("agent", "1", metadata, phases, List.of(),
                Map.of(), null, List.of());
    }

    /**
     * Fixture builder: creates a no-tool phase used only for harness state
     * selection tests.
     */
    private AgentPhase phase(String id) {
        return new AgentPhase(id, null, null, List.of(), null, Map.of(), Map.of(), List.of(), 0);
    }

    /**
     * Fixture builder: creates a completed single-phase agent run with the
     * supplied output as the phase's structured terminal output.
     */
    private AgentRunState completedRun(String runId, AgentDefinition agent, Map<String, Object> output) {
        return completedRun(runId, agent, output, Map.of());
    }

    /**
     * Fixture builder: creates a completed two-phase agent run for terminal
     * phase selection tests.
     */
    private AgentRunState completedRun(
            String runId,
            AgentDefinition agent,
            Map<String, Object> firstOutput,
            Map<String, Object> secondOutput
    ) {
        Map<String, AgentPhaseState> phases = new LinkedHashMap<>();
        Instant now = Instant.now();
        phases.put(agent.phases().get(0).id(), completedPhase(agent.phases().get(0).id(), firstOutput, now));
        if (agent.phases().size() > 1) {
            phases.put(agent.phases().get(1).id(), completedPhase(agent.phases().get(1).id(), secondOutput, now));
        }
        return new AgentRunState(null, runId, agent.id(), agent.version(), AgentRunStatus.COMPLETED,
                Map.of(), phases, CostAccounting.empty(Map.of()), null, null, now, now);
    }

    /**
     * Fixture builder: creates a completed phase state with minimal execution
     * details because harness tests only inspect output.
     */
    private AgentPhaseState completedPhase(String phaseId, Map<String, Object> output, Instant now) {
        return new AgentPhaseState(phaseId, AgentPhaseStatus.COMPLETED, Map.of(), List.of(), List.of(),
                output, null, null, now, now);
    }

    /** Fixture builder: creates a failed inner run carrying an approval id as partial output. */
    private AgentRunState approvalRun(String runId, AgentDefinition agent, String approvalId) {
        Instant now = Instant.now();
        String phaseId = agent.phases().getFirst().id();
        AgentPhaseState failed = new AgentPhaseState(
                phaseId, AgentPhaseStatus.FAILED, Map.of(), List.of(), List.of(),
                Map.of("approvalId", approvalId), "TOOL_APPROVAL_REQUIRED",
                "Tool call requires approval", now, now);
        return new AgentRunState(null, runId, agent.id(), agent.version(), AgentRunStatus.FAILED,
                Map.of(), Map.of(phaseId, failed), CostAccounting.empty(Map.of()),
                "TOOL_APPROVAL_REQUIRED", "Tool call requires approval", now, now);
    }

    /**
     * Test Adapter: returns scripted agent run states and records the inputs
     * supplied by the harness on each turn.
     */
    private static final class ScriptedAgentRuntime implements AgentRuntime {
        private final Deque<AgentRunState> runs;
        private final List<Map<String, Object>> inputs = new ArrayList<>();
        private final List<ExecutionContext> contexts = new ArrayList<>();

        /**
         * Constructs ScriptedAgentRuntime from the ordered runs it should return
         * to the harness.
         */
        private ScriptedAgentRuntime(List<AgentRunState> runs) {
            this.runs = new ArrayDeque<>(runs);
        }

        /**
         * Performs one scripted agent start and records the received input.
         */
        @Override
        public AgentRunState start(AgentDefinition agent, Map<String, Object> input, ExecutionContext context) {
            inputs.add(input == null ? Map.of() : Map.copyOf(input));
            contexts.add(context);
            return runs.removeFirst();
        }

        /**
         * This test adapter does not model inner-agent resume behavior.
         */
        @Override
        public AgentRunState resume(String runId, Map<String, Object> signal, ExecutionContext context) {
            throw new UnsupportedOperationException("resume is not used by harness tests");
        }

        /**
         * This test adapter does not model inner-agent cancel behavior.
         */
        @Override
        public AgentRunState cancel(String runId, ExecutionContext context) {
            throw new UnsupportedOperationException("cancel is not used by harness tests");
        }

        /**
         * Exposes the recorded turn inputs for assertions.
         */
        private List<Map<String, Object>> inputs() {
            return List.copyOf(inputs);
        }

        /** Exposes execution contexts so approval-token projection can be verified. */
        private List<ExecutionContext> contexts() {
            return List.copyOf(contexts);
        }
    }
}
