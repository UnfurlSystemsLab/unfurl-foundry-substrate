package com.unfurl.foundry.substrate.prompt;

import com.unfurl.foundry.substrate.model.MessageRole;

/** One ordered segment of a prompt template, rendered into a single message. */
/**
 * record for the Foundry AI substrate surface; documents the PromptSegment contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public record PromptSegment(
        MessageRole role,
        String template
) {
}
