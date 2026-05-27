package com.unfurl.foundry.substrate.prompt;

import com.unfurl.foundry.substrate.model.MessageRole;

/** One ordered segment of a prompt template, rendered into a single message. */
public record PromptSegment(
        MessageRole role,
        String template
) {
}
