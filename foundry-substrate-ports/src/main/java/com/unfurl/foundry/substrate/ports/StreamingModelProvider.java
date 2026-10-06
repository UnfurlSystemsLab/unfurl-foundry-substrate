package com.unfurl.foundry.substrate.ports;

import com.unfurl.foundry.substrate.model.ModelDelta;
import com.unfurl.foundry.substrate.model.ModelRequest;
import com.unfurl.foundry.substrate.model.ModelResponse;
import com.unfurl.substrate.policy.ExecutionContext;
import java.util.function.Consumer;

/**
 * Optional Port extension: a model provider that can also report its answer incrementally. It never replaces
 * {@link #complete}; callers that do not observe deltas keep using it unchanged.
 */
public interface StreamingModelProvider extends ModelProvider {
    /**
     * Streaming call: delivers ordered text fragments of the assistant message to {@code deltas} while the call runs, then returns
     * the same authoritative response {@link #complete} would. Implementations stop the underlying stream when the calling thread
     * is interrupted or their finite timeout expires, and report the same sanitized failures as {@code complete}.
     */
    ModelResponse stream(ModelRequest request, ExecutionContext context, Consumer<ModelDelta> deltas);
}
