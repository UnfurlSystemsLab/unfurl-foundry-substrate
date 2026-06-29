package com.unfurl.foundry.substrate.model;

import java.util.Map;

/**
 * record for the Foundry AI substrate surface; documents the Message contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public record Message(
        MessageRole role,
        String content,
        String toolCallId,
        Map<String, Object> metadata
) {
/**
 * Constructs Message with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public Message {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

/**
 * Implements the system helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    public static Message system(String content) {
        return new Message(MessageRole.SYSTEM, content, null, Map.of());
    }

/**
 * Implements the user helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    public static Message user(String content) {
        return new Message(MessageRole.USER, content, null, Map.of());
    }

/**
 * Implements the assistant helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    public static Message assistant(String content) {
        return new Message(MessageRole.ASSISTANT, content, null, Map.of());
    }

/**
 * Implements the tool helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    public static Message tool(String toolCallId, String content) {
        return new Message(MessageRole.TOOL, content, toolCallId, Map.of());
    }
}
