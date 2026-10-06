package com.unfurl.foundry.substrate.springai;

import com.unfurl.foundry.substrate.model.Message;
import com.unfurl.foundry.substrate.model.ModelDelta;
import com.unfurl.foundry.substrate.model.ModelRequest;
import com.unfurl.foundry.substrate.model.ModelTurnOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Streaming conformance for the Spring AI adapter against in-process {@link ChatModel} stubs: ordered deltas, an authoritative
 * aggregated response mapped exactly like {@code complete}, fallback for non-streaming models, and disposal of the underlying stream
 * on timeout and interruption.
 */
class SpringAiStreamingTest {
    private static final ModelRequest REQUEST = new ModelRequest(List.of(Message.user("hi")), "test-model", Map.of(), List.of(), Map.of());

    /** Ordered text deltas are reported and the final response aggregates text, finish reason and the last reported usage. */
    @Test void streamsOrderedDeltasAndAggregatesTheResponse() {
        var deltas = new CopyOnWriteArrayList<ModelDelta>();
        var response = new SpringAiModelProvider(streaming(Flux.just(chunk("Hel", null, null), chunk("", null, null),
                chunk("lo", "STOP", new DefaultUsage(3, 2))))).stream(REQUEST, null, deltas::add);
        assertThat(deltas).containsExactly(new ModelDelta(0, "Hel"), new ModelDelta(1, "lo"));
        assertThat(response.message().content()).isEqualTo("Hello");
        assertThat(response.outcome()).isEqualTo(ModelTurnOutcome.COMPLETED);
        assertThat(response.usage().promptTokens()).isEqualTo(3);
        assertThat(response.usage().completionTokens()).isEqualTo(2);
    }

    /** Tool calls arriving in chunks are aggregated into the authoritative response without being sent as text deltas. */
    @Test void aggregatesToolCallsAcrossChunks() {
        var deltas = new CopyOnWriteArrayList<ModelDelta>();
        var call = AssistantMessage.builder().content("").toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "lookup",
                "{\"id\":42}"))).build();
        var response = new SpringAiModelProvider(streaming(Flux.just(chunk("checking", null, null), new ChatResponse(List.of(new Generation(call))))))
                .stream(REQUEST, null, deltas::add);
        assertThat(deltas).extracting(ModelDelta::text).containsExactly("checking");
        assertThat(response.outcome()).isEqualTo(ModelTurnOutcome.TOOL_REQUESTED);
        assertThat(response.toolCalls()).singleElement().satisfies(tool -> assertThat(tool.arguments()).containsEntry("id", 42));
    }

    /** A model without streaming support is called through complete and reports no deltas. */
    @Test void fallsBackToCompleteForNonStreamingModels() {
        var deltas = new CopyOnWriteArrayList<ModelDelta>();
        ChatModel blocking = prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage("whole answer"))));
        assertThat(new SpringAiModelProvider(blocking).stream(REQUEST, null, deltas::add).message().content()).isEqualTo("whole answer");
        assertThat(deltas).isEmpty();
    }

    /** A stream that never finishes is disposed at the adapter's finite timeout and reported as a sanitized timeout. */
    @Test void disposesTheStreamOnTimeout() {
        var cancelled = new AtomicBoolean();
        var provider = new SpringAiModelProvider(streaming(Flux.<ChatResponse>never().doOnCancel(() -> cancelled.set(true))), Duration.ofMillis(100));
        assertThatIllegalStateException().isThrownBy(() -> provider.stream(REQUEST, null, delta -> { })).withMessage("Spring AI provider call timed out");
        assertThat(cancelled).isTrue();
    }

    /** Interrupting the calling thread (runtime shutdown or a cancelled worker) disposes the stream and preserves the interrupt. */
    @Test void disposesTheStreamOnInterruption() throws Exception {
        var cancelled = new AtomicBoolean();
        var failure = new AtomicReference<Throwable>();
        var interrupted = new AtomicBoolean();
        var provider = new SpringAiModelProvider(streaming(Flux.<ChatResponse>never().doOnCancel(() -> cancelled.set(true))), Duration.ofMinutes(1));
        var caller = new Thread(() -> {
            try { provider.stream(REQUEST, null, delta -> { }); }
            catch (RuntimeException expected) { failure.set(expected); interrupted.set(Thread.currentThread().isInterrupted()); }
        });
        caller.start();
        Thread.sleep(100);
        caller.interrupt();
        caller.join(5000);
        assertThat(failure.get()).hasMessage("Spring AI provider call interrupted");
        assertThat(interrupted).isTrue();
        assertThat(cancelled).isTrue();
    }

    /** A provider stream error is sanitized exactly like a failed blocking call. */
    @Test void sanitizesStreamErrors() {
        var provider = new SpringAiModelProvider(streaming(Flux.concat(Flux.just(chunk("partial", null, null)),
                Flux.error(new IllegalStateException("SECRET provider detail")))));
        assertThatIllegalStateException().isThrownBy(() -> provider.stream(REQUEST, null, delta -> { })).withMessage("Spring AI provider call failed");
    }

    /** Fixture: a chat model whose blocking call is unused and whose stream returns the given chunks. */
    private static ChatModel streaming(Flux<ChatResponse> chunks) {
        return new ChatModel() {
            /** Blocking call: unused by streaming tests. */
            @Override public ChatResponse call(Prompt prompt) { throw new AssertionError("streaming test used call"); }
            /** Stream: the scripted chunks. */
            @Override public Flux<ChatResponse> stream(Prompt prompt) { return chunks; }
        };
    }

    /** Fixture: one streamed chunk with optional finish reason and usage. */
    private static ChatResponse chunk(String text, String finishReason, DefaultUsage usage) {
        var generation = finishReason == null ? new Generation(new AssistantMessage(text))
                : new Generation(new AssistantMessage(text), ChatGenerationMetadata.builder().finishReason(finishReason).build());
        return usage == null ? new ChatResponse(List.of(generation))
                : new ChatResponse(List.of(generation), ChatResponseMetadata.builder().usage(usage).build());
    }
}
