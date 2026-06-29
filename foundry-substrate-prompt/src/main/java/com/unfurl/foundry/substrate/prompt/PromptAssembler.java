package com.unfurl.foundry.substrate.prompt;

import com.unfurl.foundry.substrate.model.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure prompt/message assembly: renders a {@link PromptTemplate} against a flat variable
 * map into an ordered list of {@link Message}s. Variables use {@code {{name}}} syntax.
 *
 * <p>This is deliberately simple and side-effect free. Resolution of {@code $.x.y} data
 * references into the variable map is the engine's responsibility (via the resolver),
 * keeping this module dependent on domain only.
 */
public final class PromptAssembler {
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.]+)\\s*}}");

/**
 * Performs the assemble operation for this component, translating validated inputs into the domain result expected by callers.
 */
    public List<Message> assemble(PromptTemplate template, Map<String, Object> variables) {
        List<Message> messages = new ArrayList<>();
        for (PromptSegment segment : template.segments()) {
            String content = render(segment.template(), variables);
            messages.add(new Message(segment.role(), content, null, Map.of()));
        }
        return List.copyOf(messages);
    }

/**
 * Implements the render helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    public String render(String template, Map<String, Object> variables) {
        if (template == null) {
            return "";
        }
        Matcher matcher = VARIABLE.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            Object value = variables.get(matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
