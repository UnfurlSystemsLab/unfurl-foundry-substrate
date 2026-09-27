package com.unfurl.foundry.substrate.agent;

/** Policy outcome used when bounded output-correction attempts are exhausted. */
public enum CorrectionExhaustionAction {
    FAIL,
    ESCALATE
}
