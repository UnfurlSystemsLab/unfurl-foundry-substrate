package com.unfurl.foundry.substrate.engine;

import java.util.Objects;
import java.util.function.Supplier;

/** Strategy port: lets a host govern one external invocation without owning its implementation. */
public interface ExternalCallBoundary {
    /** Dispatch operation: invokes exactly once or fails before invocation; hosts may journal both transitions. */
    <T> T invoke(Call call, Supplier<T> invocation);

    /** Null Object Strategy: preserves direct in-process invocation for non-durable embedded hosts. */
    static ExternalCallBoundary direct() {
        return new ExternalCallBoundary() {
            /** Direct dispatch: performs no persistence and invokes the supplied operation once. */
            @Override public <T> T invoke(Call call, Supplier<T> invocation) {
                Objects.requireNonNull(call, "external call is required");
                return Objects.requireNonNull(invocation, "invocation is required").get();
            }
        };
    }

    /** Value Object: carries exact dispatch identity and digest input, never a replay instruction. */
    record Call(String tenantId, String runId, String callId, Kind kind, String bindingRef, Object request) {
        /** Validating constructor: rejects identity gaps before a host can record or dispatch the call. */
        public Call {
            if (tenantId != null && tenantId.isBlank()) throw new IllegalArgumentException("tenantId cannot be blank");
            if (runId == null || runId.isBlank()) throw new IllegalArgumentException("runId is required");
            if (callId == null || callId.isBlank()) throw new IllegalArgumentException("callId is required");
            Objects.requireNonNull(kind, "external call kind is required");
            if (bindingRef == null || bindingRef.isBlank()) throw new IllegalArgumentException("bindingRef is required");
            Objects.requireNonNull(request, "external call request is required");
        }
    }

    /** Discriminator: identifies the neutral dispatch family for host-side governance. */
    enum Kind { PROVIDER, TOOL, CHILD_AGENT, WORKFLOW }
}
