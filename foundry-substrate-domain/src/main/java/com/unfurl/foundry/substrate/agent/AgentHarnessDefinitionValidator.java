package com.unfurl.foundry.substrate.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validator: checks the portable harness shape and delegates wrapped-agent
 * topology validation to {@link AgentDefinitionValidator}.
 */
public final class AgentHarnessDefinitionValidator {
    private static final Pattern ID = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_-]*$");

    private final AgentDefinitionValidator agentValidator;

    /**
     * Constructs AgentHarnessDefinitionValidator with the standard agent
     * definition validator used by the embedded substrate runtime.
     */
    public AgentHarnessDefinitionValidator() {
        this(new AgentDefinitionValidator());
    }

    /**
     * Constructs AgentHarnessDefinitionValidator with an injected validator so
     * tests and hosts can share the same agent validation strategy.
     */
    public AgentHarnessDefinitionValidator(AgentDefinitionValidator agentValidator) {
        this.agentValidator = agentValidator == null ? new AgentDefinitionValidator() : agentValidator;
    }

    /**
     * Performs structural validation before a harness run is admitted into an
     * embedded or product runtime.
     */
    public void validate(AgentHarnessDefinition harness) {
        List<String> errors = new ArrayList<>();
        if (harness == null) {
            throw new IllegalArgumentException("Invalid agent harness definition: harness is required");
        }
        if (harness.id() == null || !ID.matcher(harness.id()).matches()) {
            errors.add("Invalid harness id: " + harness.id());
        }
        if (harness.version() == null || harness.version().isBlank()) {
            errors.add("Harness version is required");
        }
        if (harness.agent() == null) {
            errors.add("Harness '" + harness.id() + "' must wrap an agent definition");
        } else {
            try {
                agentValidator.validate(harness.agent());
            } catch (IllegalArgumentException ex) {
                errors.add(ex.getMessage());
            }
        }
        if (harness.loopPolicy() == null) {
            errors.add("Harness '" + harness.id() + "' must declare loopPolicy");
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid agent harness definition: " + String.join("; ", errors));
        }
    }
}
