package com.unfurl.foundry.substrate.springai;

import com.unfurl.foundry.substrate.model.Message;
import com.unfurl.foundry.substrate.model.MessageRole;
import com.unfurl.foundry.substrate.model.ModelRequest;
import com.unfurl.foundry.substrate.model.ModelResponse;
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
 * Implements the toPrompt helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    private Prompt toPrompt(ModelRequest request) {
        List<org.springframework.ai.chat.messages.Message> springMessages = new ArrayList<>();
        for (Message message : request.messages()) {
            springMessages.add(toSpringMessage(message));
        }
        // ChatOptions are typically supplied by the host's ChatModel
        // bean (model name, temperature, etc.). We omit per-call options
        // so the host's defaults govern, and the request's parameters
        // map travels via metadata only.
        return new Prompt(springMessages);
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
            case TOOL -> new ToolResponseMessage(List.of(
                    new ToolResponseMessage.ToolResponse(
                            message.toolCallId() == null ? "" : message.toolCallId(),
                            // Spring AI's ToolResponse needs a name; foundry's
                            // Message doesn't carry one separately, so we use
                            // the tool-call id as both.
                            message.toolCallId() == null ? "" : message.toolCallId(),
                            content)));
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
        ModelUsage usage = usageFrom(response);
        return new ModelResponse(message, List.of(), finishReason, usage, java.util.Map.of());
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
