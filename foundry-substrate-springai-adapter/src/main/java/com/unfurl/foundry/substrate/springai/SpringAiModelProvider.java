package com.unfurl.foundry.substrate.springai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unfurl.foundry.substrate.model.Message;
import com.unfurl.foundry.substrate.model.MessageRole;
import com.unfurl.foundry.substrate.model.ModelRequest;
import com.unfurl.foundry.substrate.model.ModelResponse;
import com.unfurl.foundry.substrate.model.ModelToolCall;
import com.unfurl.foundry.substrate.model.ModelTurnOutcome;
import com.unfurl.foundry.substrate.model.ModelUsage;
import com.unfurl.foundry.substrate.model.ModelDelta;
import com.unfurl.foundry.substrate.ports.ModelProvider;
import com.unfurl.foundry.substrate.ports.StreamingModelProvider;
import com.unfurl.substrate.policy.ExecutionContext;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Foundry {@link ModelProvider} backed by a host-supplied Spring AI
 * {@link ChatModel}. Maps foundry's neutral {@link ModelRequest} into a
 * Spring AI {@link Prompt}, invokes the model, and projects the Spring AI
 * {@link ChatResponse} back into a {@link ModelResponse}.
 *
 * <p>Provider-specific configuration (API keys, deployment ids, model
 * names, retries, observability) lives in the host's Spring context —
 * the customer wires their Spring AI starter of choice and this adapter
 * just routes calls through it. Foundry's substrate code stays oblivious
 * to which provider is wired.
 */
public final class SpringAiModelProvider implements StreamingModelProvider {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<java.util.Map<String, Object>> ARGUMENTS_TYPE = new TypeReference<>() {
    };
    // The shared neutral option vocabulary; anything else is not a provider option and fails closed.
    private static final Set<String> SUPPORTED_PARAMETERS = ModelRequest.OPTION_PARAMETERS;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(2);
    private static final ExecutorService CALL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    private final ChatModel chatModel;
    private final Duration timeout;

/**
 * Constructs SpringAiModelProvider with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public SpringAiModelProvider(ChatModel chatModel) {
        this(chatModel, DEFAULT_TIMEOUT);
    }

    /**
     * Adapter constructor: binds a host model and a finite maximum call timeout. A request may shorten,
     * but never lengthen, this deployment-owned limit.
     */
    public SpringAiModelProvider(ChatModel chatModel, Duration timeout) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel is required");
        this.timeout = requirePositive(timeout);
    }

/**
 * Performs the complete operation for this component, translating validated inputs into the domain result expected by callers.
 */
    @Override
    public ModelResponse complete(ModelRequest request, ExecutionContext context) {
        Objects.requireNonNull(request, "request is required");
        Prompt prompt = toPrompt(request);
        Duration callTimeout = requestTimeout(request);
        Future<ChatResponse> call = CALL_EXECUTOR.submit(() -> chatModel.call(prompt));
        try {
            return fromResponse(call.get(callTimeout.toMillis(), TimeUnit.MILLISECONDS));
        } catch (TimeoutException ex) {
            call.cancel(true);
            throw new IllegalStateException("Spring AI provider call timed out");
        } catch (InterruptedException ex) {
            call.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Spring AI provider call interrupted");
        } catch (ExecutionException ex) {
            throw new IllegalStateException("Spring AI provider call failed");
        }
    }

/**
 * Adapter projection: converts Foundry's neutral message list into the Spring AI prompt shape while
 * satisfying providers that accept only one system instruction message.
 */
    private Prompt toPrompt(ModelRequest request) {
        List<org.springframework.ai.chat.messages.Message> springMessages = new ArrayList<>();
        StringBuilder system = new StringBuilder();
        for (Message message : request.messages()) {
            if (message.role() == MessageRole.SYSTEM) {
                appendSystem(system, message.content());
            } else {
                springMessages.add(toSpringMessage(message));
            }
        }
        if (!system.isEmpty()) {
            springMessages.add(0, new SystemMessage(system.toString()));
        }
        return new Prompt(springMessages, toOptions(request));
    }

    /**
     * Adapter projection: maps the closed neutral parameter vocabulary and native tool definitions
     * into per-call Spring AI options. Internal execution is disabled so policy remains in Foundry.
     */
    private ToolCallingChatOptions toOptions(ModelRequest request) {
        Set<String> unknown = new LinkedHashSet<>(request.parameters().keySet());
        unknown.removeAll(SUPPORTED_PARAMETERS);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("unsupported model parameters: " + unknown);
        }
        ToolCallingChatOptions.Builder builder = DefaultToolCallingChatOptions.builder()
                .internalToolExecutionEnabled(false);
        optionalNumber(request.parameters(), "temperature").ifPresent(value -> builder.temperature(value.doubleValue()));
        optionalNumber(request.parameters(), "topP").ifPresent(value -> builder.topP(value.doubleValue()));
        optionalNumber(request.parameters(), "topK").ifPresent(value -> builder.topK(positiveInt("topK", value)));
        optionalNumber(request.parameters(), "maxTokens").ifPresent(value -> builder.maxTokens(positiveInt("maxTokens", value)));
        Object stops = request.parameters().get("stopSequences");
        if (stops != null) builder.stopSequences(stringList("stopSequences", stops));
        if (!request.toolSchemas().isEmpty()) builder.toolCallbacks(request.toolSchemas().stream()
                .map(this::toolCallback).toList());
        return builder.build();
    }

    /** Adapter: creates a definition-only callback that cannot bypass the governed tool executor. */
    private ToolCallback toolCallback(Map<String, Object> schema) {
        String name = requiredText(schema, "name");
        Object input = schema.getOrDefault("inputSchema", Map.of("type", "object"));
        final ToolDefinition definition;
        try {
            definition = ToolDefinition.builder().name(name)
                    .description(String.valueOf(schema.getOrDefault("description", "")))
                    .inputSchema(MAPPER.writeValueAsString(input)).build();
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalArgumentException("tool input schema is not JSON serializable", ex);
        }
        return new ToolCallback() {
            @Override public ToolDefinition getToolDefinition() { return definition; }
            @Override public String call(String arguments) {
                throw new IllegalStateException("Spring AI internal tool execution is disabled");
            }
        };
    }

    /** Validation helper: returns a required non-blank schema field. */
    private String requiredText(Map<String, Object> schema, String field) {
        Object value = schema.get(field);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("tool schema " + field + " is required");
        }
        return String.valueOf(value);
    }

    /** Validation helper: reads an optional numeric model parameter. */
    private java.util.Optional<Number> optionalNumber(Map<String, Object> parameters, String name) {
        Object value = parameters.get(name);
        if (value == null) return java.util.Optional.empty();
        if (!(value instanceof Number number)) throw new IllegalArgumentException(name + " must be numeric");
        return java.util.Optional.of(number);
    }

    /** Validation helper: narrows a positive integral option without truncation or overflow. */
    private int positiveInt(String name, Number number) {
        long value = number.longValue();
        if (value <= 0 || value > Integer.MAX_VALUE || number.doubleValue() != value) {
            throw new IllegalArgumentException(name + " must be a positive integer");
        }
        return (int) value;
    }

    /** Validation helper: copies a non-empty list of strings. */
    private List<String> stringList(String name, Object value) {
        if (!(value instanceof List<?> values) || values.stream().anyMatch(item -> !(item instanceof String))) {
            throw new IllegalArgumentException(name + " must be a list of strings");
        }
        return values.stream().map(String.class::cast).toList();
    }

    /** Timeout policy: lets request metadata shorten the deployment maximum only. */
    private Duration requestTimeout(ModelRequest request) {
        Object value = request.metadata().get("timeoutMs");
        if (value == null) return timeout;
        if (!(value instanceof Number number)) throw new IllegalArgumentException("timeoutMs must be numeric");
        Duration requested = Duration.ofMillis(positiveInt("timeoutMs", number));
        return requested.compareTo(timeout) < 0 ? requested : timeout;
    }

    /** Constructor guard: enforces a finite positive deployment timeout. */
    private static Duration requirePositive(Duration value) {
        Objects.requireNonNull(value, "timeout is required");
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException("timeout must be positive");
        return value;
    }

/**
 * Normalization helper: appends one Foundry system segment to the single Spring AI system
 * instruction while preserving a clear boundary between prompt, tool, and RAG fragments.
 */
    private void appendSystem(StringBuilder system, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        if (!system.isEmpty()) {
            system.append("\n\n");
        }
        system.append(content);
    }

/**
 * Implements the toSpringMessage helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    private org.springframework.ai.chat.messages.Message toSpringMessage(Message message) {
        String content = message.content() == null ? "" : message.content();
        return switch (message.role()) {
            case SYSTEM -> new SystemMessage(content);
            case USER -> new UserMessage(content);
            case ASSISTANT -> new AssistantMessage(content);
            case TOOL -> ToolResponseMessage.builder()
                    .responses(List.of(new ToolResponseMessage.ToolResponse(
                            message.toolCallId() == null ? "" : message.toolCallId(),
                            // Spring AI's ToolResponse needs a name; foundry's Message doesn't carry
                            // one separately, so the adapter uses the tool-call id as both.
                            message.toolCallId() == null ? "" : message.toolCallId(),
                            content)))
                    .build();
        };
    }

/**
 * Factory method: creates the fromResponse result while keeping caller-facing defaults and validation in one place.
 */
    private ModelResponse fromResponse(ChatResponse response) {
        Generation generation = response.getResult();
        AssistantMessage assistant = generation == null ? null : generation.getOutput();
        String content = assistant == null ? "" : Objects.requireNonNullElse(assistant.getText(), "");
        return toModelResponse(content, finishReasonOf(generation), toolCallsFrom(assistant), usageFrom(response));
    }

    /** Shared mapping: the one place a neutral response is built, for both the blocking and the streaming call. */
    private ModelResponse toModelResponse(String content, String finishReason, List<ModelToolCall> toolCalls, ModelUsage usage) {
        return new ModelResponse(Message.assistant(content), toolCalls, finishReason,
                outcomeForFinishReason(finishReason, !toolCalls.isEmpty()), usage, java.util.Map.of(),
                null, java.math.BigDecimal.ZERO);
    }

    /** Finish-reason accessor: empty when the generation carries none. */
    private static String finishReasonOf(Generation generation) {
        return generation == null || generation.getMetadata() == null
                ? "" : Objects.requireNonNullElse(generation.getMetadata().getFinishReason(), "");
    }

    /**
     * Streaming call: subscribes to {@code ChatModel.stream}, reports each chunk's text as an ordered delta, and aggregates the
     * authoritative response from the chunks — concatenated text, every tool call, the last finish reason and the last reported
     * usage — through the same mapping as {@link #complete}. The subscription is disposed on interruption or timeout, and failures
     * are sanitized exactly as for {@code complete}. A model without streaming support falls back to {@code complete}.
     */
    @Override
    public ModelResponse stream(ModelRequest request, ExecutionContext context, java.util.function.Consumer<ModelDelta> deltas) {
        Objects.requireNonNull(request, "request is required");
        Objects.requireNonNull(deltas, "delta consumer is required");
        Prompt prompt = toPrompt(request);
        Duration callTimeout = requestTimeout(request);
        reactor.core.publisher.Flux<ChatResponse> flux;
        try { flux = chatModel.stream(prompt); }
        catch (UnsupportedOperationException notStreaming) { return complete(request, context); }
        var text = new StringBuilder();
        var toolCalls = new ArrayList<ModelToolCall>();
        var finishReason = new java.util.concurrent.atomic.AtomicReference<String>("");
        var usage = new java.util.concurrent.atomic.AtomicReference<ModelUsage>(ModelUsage.zero());
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var index = new java.util.concurrent.atomic.AtomicInteger();
        var done = new java.util.concurrent.CountDownLatch(1);
        reactor.core.Disposable subscription = flux.subscribe(chunk -> {
            Generation generation = chunk.getResult();
            AssistantMessage assistant = generation == null ? null : generation.getOutput();
            String piece = assistant == null ? null : assistant.getText();
            if (piece != null && !piece.isEmpty()) {
                text.append(piece);
                deltas.accept(new ModelDelta(index.getAndIncrement(), piece));
            }
            toolCalls.addAll(toolCallsFrom(assistant));
            String reason = finishReasonOf(generation);
            if (!reason.isEmpty()) finishReason.set(reason);
            ModelUsage reported = usageFrom(chunk);
            if (!reported.equals(ModelUsage.zero())) usage.set(reported);
        }, error -> { failure.set(error); done.countDown(); }, done::countDown);
        try {
            if (!done.await(callTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                subscription.dispose();
                throw new IllegalStateException("Spring AI provider call timed out");
            }
        } catch (InterruptedException ex) {
            subscription.dispose();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Spring AI provider call interrupted");
        }
        if (failure.get() != null) throw new IllegalStateException("Spring AI provider call failed");
        return toModelResponse(text.toString(), finishReason.get(), List.copyOf(toolCalls), usage.get());
    }

    /**
     * Adapter mapping: normalizes Spring AI's provider-derived finish reason. Blank reasons remain
     * compatible with simple ChatModel implementations; unknown non-blank values fail closed.
     */
    static ModelTurnOutcome outcomeForFinishReason(String finishReason, boolean hasToolCalls) {
        return ModelTurnOutcome.fromLegacy(finishReason, hasToolCalls);
    }

    /**
     * Adapter: projects Spring AI native tool-call records into Foundry's neutral tool-call shape.
     * Invalid JSON arguments fail the provider boundary instead of being silently replaced.
     */
    private List<ModelToolCall> toolCallsFrom(AssistantMessage assistant) {
        if (assistant == null || !assistant.hasToolCalls()) {
            return List.of();
        }
        return assistant.getToolCalls().stream().map(call -> {
            try {
                java.util.Map<String, Object> arguments = call.arguments() == null || call.arguments().isBlank()
                        ? java.util.Map.of()
                        : MAPPER.readValue(call.arguments(), ARGUMENTS_TYPE);
                return new ModelToolCall(call.id(), call.name(), arguments);
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw new IllegalArgumentException("Spring AI returned invalid tool-call arguments", ex);
            }
        }).toList();
    }

/**
 * Implements the usageFrom helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    private ModelUsage usageFrom(ChatResponse response) {
        if (response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return ModelUsage.zero();
        }
        var usage = response.getMetadata().getUsage();
        Integer prompt = usage.getPromptTokens();
        Integer completion = usage.getCompletionTokens();
        return new ModelUsage(
                prompt == null ? 0 : prompt.longValue(),
                completion == null ? 0 : completion.longValue());
    }
}
