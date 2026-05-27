package com.unfurl.foundry.substrate.model;

import java.util.Map;

public record Message(
        MessageRole role,
        String content,
        String toolCallId,
        Map<String, Object> metadata
) {
    public Message {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static Message system(String content) {
        return new Message(MessageRole.SYSTEM, content, null, Map.of());
    }

    public static Message user(String content) {
        return new Message(MessageRole.USER, content, null, Map.of());
    }

    public static Message assistant(String content) {
        return new Message(MessageRole.ASSISTANT, content, null, Map.of());
    }

    public static Message tool(String toolCallId, String content) {
        return new Message(MessageRole.TOOL, content, toolCallId, Map.of());
    }
}
