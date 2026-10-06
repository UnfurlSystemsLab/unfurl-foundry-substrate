package com.unfurl.foundry.substrate.ports;

import com.unfurl.foundry.substrate.model.ModelDelta;
import com.unfurl.substrate.policy.ExecutionContext;

/**
 * Observer Port: receives a run's model text deltas with their run, phase and model-call identity, for non-authoritative streaming
 * projections. The runtime isolates observer failures, so an observer can never change a run's result, journal or checkpoints.
 */
@FunctionalInterface
public interface ModelStreamObserver {
    /** Observation: one delta of one model call of one phase of one run, in arrival order. */
    void delta(String runId, String phaseId, String modelCallId, ModelDelta delta, ExecutionContext context);
}
