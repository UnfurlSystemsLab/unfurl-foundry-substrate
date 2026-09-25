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
import com.unfurl.foundry.substrate.ports.ModelProvider;
import com.unfurl.substrate.policy.ExecutionContext;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
public final class SpringAiModelProvider implements ModelProvider {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<java.util.Map<String, Object>> ARGUMENTS_TYPE = new TypeReference<>() {
    };
    private final ChatModel chatModel;

/**
 * Constructs SpringAiModelProvider with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public SpringAiModelProvider(ChatModel chatModel) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel is required");
    }

/**
 * Performs the complete operation for this component, translating validated inputs into the domain result expected by callers.
 */
    @Override
    public ModelResponse complete(ModelRequest request, ExecutionContext context) {
        Prompt prompt = toPrompt(request);
        ChatResponse response = chatModel.call(prompt);
        return fromResponse(response);
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
        // ChatOptions are typically supplied by the host's ChatModel
        // bean (model name, temperature, etc.). We omit per-call options
        // so the host's defaults govern, and the request's parameters
        // map travels via metadata only.
        return new Prompt(springMessages);
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
        Message message = Message.assistant(content);
        String finishReason = generation == null || generation.getMetadata() == null
                ? ""
                : Objects.requireNonNullElse(generation.getMetadata().getFinishReason(), "");
        List<ModelToolCall> toolCalls = toolCallsFrom(assistant);
        ModelUsage usage = usageFrom(response);
        return new ModelResponse(message, toolCalls, finishReason,
                outcomeForFinishReason(finishReason, !toolCalls.isEmpty()), usage, java.util.Map.of(),
                null, java.math.BigDecimal.ZERO);
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
