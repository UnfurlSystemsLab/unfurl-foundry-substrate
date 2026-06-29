package com.unfurl.foundry.substrate.tools;

import com.unfurl.foundry.substrate.ports.ToolExecutor;
import com.unfurl.foundry.substrate.ports.ToolRegistry;
import com.unfurl.substrate.policy.ExecutionContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory {@link ToolRegistry}. The substrate ships this reference registry;
 * YAML-directory loading, sandboxed handlers, and MCP sub-registries live in
 * {@code unfurl-foundry}.
 */
public final class DefaultToolRegistry implements ToolRegistry {
    private final Map<String, ToolExecutor> executorsByName = new LinkedHashMap<>();

/**
 * Performs the register operation for this component, translating validated inputs into the domain result expected by callers.
 */
    public void register(String toolName, ToolExecutor executor) {
        if (executorsByName.containsKey(toolName)) {
            throw new IllegalArgumentException("Duplicate tool: " + toolName);
        }
        executorsByName.put(toolName, executor);
    }

/**
 * Performs the hasTool operation for this component, translating validated inputs into the domain result expected by callers.
 */
    @Override
    public boolean hasTool(String toolName, ExecutionContext context) {
        return executorsByName.containsKey(toolName);
    }

/**
 * Performs the resolveTool operation for this component, translating validated inputs into the domain result expected by callers.
 */
    @Override
    public Optional<ToolExecutor> resolveTool(String toolName, ExecutionContext context) {
        return Optional.ofNullable(executorsByName.get(toolName));
    }
}
