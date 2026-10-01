package com.unfurl.foundry.substrate.ports;

import com.unfurl.substrate.policy.ExecutionContext;

/** Observer Port: hosts retain correction decisions before validation/dispatch; storage stays outside the substrate. */
@FunctionalInterface
public interface CorrectionProgressObserver {
    /** Records immutable progress or throws; a failed write must not authorize the next repair. */
    void save(CorrectionProgress progress, ExecutionContext context);

    /** Null Object: embedded hosts explicitly choose no durable progress storage. */
    static CorrectionProgressObserver noop() { return (progress, context) -> { }; }
}
