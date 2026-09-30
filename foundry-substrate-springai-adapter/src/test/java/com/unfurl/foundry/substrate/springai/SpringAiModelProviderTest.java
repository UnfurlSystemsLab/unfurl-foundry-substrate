package com.unfurl.foundry.substrate.springai;

import com.unfurl.foundry.substrate.model.Message;
import com.unfurl.foundry.substrate.model.MessageRole;
import com.unfurl.foundry.substrate.model.ModelRequest;
import com.unfurl.foundry.substrate.model.ModelResponse;
import com.unfurl.foundry.substrate.model.ModelTurnOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Round-trip tests against an in-process {@link ChatModel} stub. We do
 * not call any real LLM provider; the goal is to prove the adapter
 * faithfully maps foundry's neutral request/response onto Spring AI's
 * primitives and back.
 */
class SpringAiModelProviderTest {

    /** Proves neutral options and schemas become definition-only native Spring AI call options. */
    @Test
    void projectsBoundedOptionsAndNativeToolDefinitions() {
        AtomicReference<Prompt> captured = new AtomicReference<>();
        ChatModel chatModel = prompt -> {
            captured.set(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
        };
        ModelRequest request = new ModelRequest(List.of(Message.user("find it")), "logical-model",
                Map.of("temperature", 0.2, "topP", 0.9, "topK", 20, "maxTokens", 512,
                        "stopSequences", List.of("STOP")),
                List.of(Map.of("name", "lookup", "description", "Looks up a record",
                        "inputSchema", Map.of("type", "object", "required", List.of("id")))),
                Map.of());

        new SpringAiModelProvider(chatModel).complete(request, null);

        ToolCallingChatOptions options = (ToolCallingChatOptions) captured.get().getOptions();
        assertThat(options.getTemperature()).isEqualTo(0.2);
        assertThat(options.getTopP()).isEqualTo(0.9);
        assertThat(options.getTopK()).isEqualTo(20);
        assertThat(options.getMaxTokens()).isEqualTo(512);
        assertThat(options.getStopSequences()).containsExactly("STOP");
        assertThat(options.getInternalToolExecutionEnabled()).isFalse();
        assertThat(options.getToolCallbacks()).singleElement().satisfies(callback -> {
            assertThat(callback.getToolDefinition().name()).isEqualTo("lookup");
            assertThat(callback.getToolDefinition().inputSchema()).contains("required", "id");
            assertThatIllegalStateException().isThrownBy(() -> callback.call("{\"id\":1}"))
                    .withMessage("Spring AI internal tool execution is disabled");
        });
    }

    /** Fail-closed projection: vendor-specific or typoed parameters cannot leak through the bridge. */
    @Test
    void rejectsUnknownModelParameters() {
        ChatModel chatModel = prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
        ModelRequest request = new ModelRequest(List.of(Message.user("hi")), "model",
                Map.of("vendorMagic", true), List.of(), Map.of());

        assertThatIllegalArgumentException().isThrownBy(
                () -> new SpringAiModelProvider(chatModel).complete(request, null))
                .withMessageContaining("unsupported model parameters")
                .withMessageContaining("vendorMagic");
    }

    /** Timeout boundary: a request can shorten the adapter limit and receives a sanitized failure. */
    @Test
    void cancelsCallsAtRequestTimeoutWithoutLeakingProviderDetails() {
        ChatModel chatModel = prompt -> {
            try {
                Thread.sleep(Duration.ofSeconds(5));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage("late"))));
        };
        ModelRequest request = new ModelRequest(List.of(Message.user("secret prompt")), "model",
                Map.of(), List.of(), Map.of("timeoutMs", 25));

        assertThatIllegalStateException().isThrownBy(
                () -> new SpringAiModelProvider(chatModel, Duration.ofSeconds(1)).complete(request, null))
                .withMessage("Spring AI provider call timed out")
                .withMessageNotContaining("secret prompt");
    }

    @Test
    void mapsFoundryMessagesIntoSpringPromptPreservingRoleAndOrder() {
        AtomicReference<Prompt> captured = new AtomicReference<>();
        ChatModel chatModel = prompt -> {
            captured.set(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
        };
        SpringAiModelProvider provider = new SpringAiModelProvider(chatModel);

        ModelRequest request = new ModelRequest(
                List.of(
                        Message.system("you are concise"),
                        Message.user("hello")),
                "test-model",
                Map.of(),
                List.of(),
                Map.of());

        provider.complete(request, null);

        Prompt prompt = captured.get();
        assertThat(prompt).isNotNull();
        assertThat(prompt.getInstructions()).hasSize(2);
        assertThat(prompt.getInstructions().get(0)).isInstanceOf(SystemMessage.class);
        assertThat(prompt.getInstructions().get(0).getText()).isEqualTo("you are concise");
        assertThat(prompt.getInstructions().get(1)).isInstanceOf(UserMessage.class);
        assertThat(prompt.getInstructions().get(1).getText()).isEqualTo("hello");
    }

    @Test
    void collapsesMultipleSystemMessagesWithoutDroppingConversationMessages() {
        AtomicReference<Prompt> captured = new AtomicReference<>();
        ChatModel chatModel = prompt -> {
            captured.set(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
        };
        SpringAiModelProvider provider = new SpringAiModelProvider(chatModel);

        ModelRequest request = new ModelRequest(
                List.of(
                        Message.system("base instructions"),
                        Message.user("execute step two"),
                        Message.system("tool-call instructions"),
                        Message.assistant("planning"),
                        Message.system("rag context")),
                "test-model",
                Map.of(),
                List.of(),
                Map.of());

        provider.complete(request, null);

        Prompt prompt = captured.get();
        assertThat(prompt).isNotNull();
        assertThat(prompt.getInstructions()).hasSize(3);
        assertThat(prompt.getInstructions().get(0)).isInstanceOf(SystemMessage.class);
        assertThat(prompt.getInstructions().get(0).getText())
                .isEqualTo("base instructions\n\ntool-call instructions\n\nrag context");
        assertThat(prompt.getInstructions().get(1)).isInstanceOf(UserMessage.class);
        assertThat(prompt.getInstructions().get(1).getText()).isEqualTo("execute step two");
        assertThat(prompt.getInstructions().get(2)).isInstanceOf(AssistantMessage.class);
        assertThat(prompt.getInstructions().get(2).getText()).isEqualTo("planning");
    }

    @Test
    void projectsAssistantResponseIntoFoundryModelResponse() {
        ChatModel chatModel = prompt -> new ChatResponse(
                List.of(new Generation(new AssistantMessage("hi there"))),
                ChatResponseMetadata.builder()
                        .usage(new DefaultUsage(11, 22))
                        .build());
        SpringAiModelProvider provider = new SpringAiModelProvider(chatModel);

        ModelResponse response = provider.complete(
                new ModelRequest(
                        List.of(Message.user("hi")),
                        "test-model",
                        Map.of(),
                        List.of(),
                        Map.of()),
                null);

        assertThat(response.message().role()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(response.message().content()).isEqualTo("hi there");
        assertThat(response.usage().promptTokens()).isEqualTo(11);
        assertThat(response.usage().completionTokens()).isEqualTo(22);
        assertThat(response.usage().totalTokens()).isEqualTo(33);
        assertThat(response.outcome()).isEqualTo(ModelTurnOutcome.COMPLETED);
    }

    @Test
    void zeroUsageWhenChatResponseProvidesNoMetadata() {
        ChatModel chatModel = prompt -> new ChatResponse(
                List.of(new Generation(new AssistantMessage(""))));
        SpringAiModelProvider provider = new SpringAiModelProvider(chatModel);

        ModelResponse response = provider.complete(
                new ModelRequest(
                        List.of(Message.user("")),
                        "any-model",
                        Map.of(),
                        List.of(),
                        Map.of()),
                null);

        assertThat(response.usage().totalTokens()).isZero();
    }

    /** Adapter mapping: Spring AI native tool calls become neutral Foundry tool requests. */
    @Test
    void projectsSpringToolCallsIntoNeutralResponse() {
        AssistantMessage assistant = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call-1", "function", "lookup", "{\"id\":42}")))
                .build();
        ChatModel chatModel = prompt -> new ChatResponse(List.of(new Generation(assistant)));

        ModelResponse response = new SpringAiModelProvider(chatModel).complete(
                new ModelRequest(List.of(Message.user("lookup")), "test-model", Map.of(), List.of(), Map.of()),
                null);

        assertThat(response.outcome()).isEqualTo(ModelTurnOutcome.TOOL_REQUESTED);
        assertThat(response.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.id()).isEqualTo("call-1");
            assertThat(call.toolName()).isEqualTo("lookup");
            assertThat(call.arguments()).containsEntry("id", 42);
        });
    }

    /** Fail-closed mapping: a new non-empty Spring/provider reason is not assumed successful. */
    @Test
    void mapsUnknownFinishReasonToProviderError() {
        assertThat(SpringAiModelProvider.outcomeForFinishReason("NEW_REASON", false))
                .isEqualTo(ModelTurnOutcome.PROVIDER_ERROR);
        assertThat(SpringAiModelProvider.outcomeForFinishReason("TOOL_CALLS", true))
                .isEqualTo(ModelTurnOutcome.TOOL_REQUESTED);
    }
}
