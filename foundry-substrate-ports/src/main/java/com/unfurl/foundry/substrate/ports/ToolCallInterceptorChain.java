package com.unfurl.foundry.substrate.ports;

import com.unfurl.substrate.policy.ExecutionContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Composite and Chain of Responsibility applying deterministic tool policy in stable order. */
public final class ToolCallInterceptorChain {
    private static final ToolCallInterceptorChain EMPTY = new ToolCallInterceptorChain(List.of());
    private final List<ToolCallInterceptor> interceptors;

    /** Creates an immutable chain in declared profile order. */
    public ToolCallInterceptorChain(List<ToolCallInterceptor> interceptors) {
        this.interceptors = interceptors == null ? List.of() : List.copyOf(interceptors);
        if (this.interceptors.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("interceptors must not contain null");
        }
    }

    /** Null Object factory: returns the shared no-op chain. */
    public static ToolCallInterceptorChain empty() {
        return EMPTY;
    }

    /** Runs before interceptors in order, threading normalized arguments and merged metadata. */
    public ToolCallDecision before(ToolCallRequest request, ExecutionContext context) {
        Map<String, Object> arguments = request.arguments();
        Map<String, Object> metadata = new LinkedHashMap<>(request.metadata());
        for (ToolCallInterceptor interceptor : interceptors) {
            ToolCallRequest current = new ToolCallRequest(
                    request.callId(), request.toolName(), arguments, Map.copyOf(metadata));
            ToolCallDecision decision = Objects.requireNonNull(interceptor.before(current, context),
                    "interceptor before decision");
            arguments = decision.arguments();
            metadata.putAll(decision.metadata());
            if (decision.type() != ToolCallDecisionType.ALLOW) {
                return new ToolCallDecision(decision.type(), arguments, decision.failure(), metadata);
            }
        }
        return new ToolCallDecision(ToolCallDecisionType.ALLOW, arguments, null, metadata);
    }

    /** Runs after interceptors in reverse while enforcing failure and redaction monotonicity. */
    public ToolCallResult after(
            ToolCallRequest request, ToolCallResult result, ExecutionContext context) {
        ToolCallResult current = Objects.requireNonNull(result, "result");
        for (int index = interceptors.size() - 1; index >= 0; index--) {
            ToolCallResult next = Objects.requireNonNull(
                    interceptors.get(index).after(request, current, context), "interceptor after result");
            if (!current.success() && next.success()) {
                throw new IllegalStateException("after interceptor cannot convert failure into success");
            }
            if (!current.output().keySet().containsAll(next.output().keySet())) {
                throw new IllegalStateException("after interceptor cannot restore or add output fields");
            }
            current = next;
        }
        return current;
    }

    /** Returns the immutable configured interceptor order for diagnostics. */
    public List<ToolCallInterceptor> interceptors() {
        return new ArrayList<>(interceptors);
    }
}
