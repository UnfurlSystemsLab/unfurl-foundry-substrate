package com.unfurl.foundry.substrate.prompt;

import com.unfurl.foundry.substrate.model.MessageRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PromptAssemblerTest {
    private final PromptAssembler assembler = new PromptAssembler();

    @Test
    void rendersTemplateVariablesAndLeavesMissingVariablesBlank() {
        assertThat(assembler.render("Hello {{ name }} from {{place}}.", Map.of("name", "Ada")))
                .isEqualTo("Hello Ada from .");
    }

    @Test
    void assemblesOrderedPromptSegments() {
        PromptTemplate template = new PromptTemplate("prompt", "1",
                List.of(new PromptSegment(MessageRole.SYSTEM, "Be {{tone}}."),
                        new PromptSegment(MessageRole.USER, "{{question}}")),
                List.of("tone", "question"));

        var messages = assembler.assemble(template, Map.of("tone", "brief", "question", "Ready?"));

        assertThat(messages).extracting("role").containsExactly(MessageRole.SYSTEM, MessageRole.USER);
        assertThat(messages).extracting("content").containsExactly("Be brief.", "Ready?");
    }
}
