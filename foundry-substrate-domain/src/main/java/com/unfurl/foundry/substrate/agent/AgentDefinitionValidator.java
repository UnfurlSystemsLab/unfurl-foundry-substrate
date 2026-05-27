package com.unfurl.foundry.substrate.agent;

import com.unfurl.substrate.domain.EdgeDefinition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Structural validator for {@link AgentDefinition}, mirroring the substrate
 * WorkflowDefinitionValidator: unique phase ids, valid edge/dependency endpoints, and
 * no dependency cycles (conditional edges may still route on data).
 */
public final class AgentDefinitionValidator {
    private static final Pattern PHASE_ID = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_-]*$");

    public void validate(AgentDefinition agent) {
        List<String> errors = new ArrayList<>();
        Map<String, AgentPhase> phasesById = new HashMap<>();
        for (AgentPhase phase : agent.phases()) {
            if (phase.id() == null || !PHASE_ID.matcher(phase.id()).matches()) {
                errors.add("Invalid phase id: " + phase.id());
                continue;
            }
            if (phasesById.put(phase.id(), phase) != null) {
                errors.add("Duplicate phase id: " + phase.id());
            }
        }

        if (agent.phases().isEmpty()) {
            errors.add("Agent '" + agent.id() + "' must declare at least one phase");
        }

        validateDependencies(agent, phasesById.keySet(), errors);
        validateEdges(agent, phasesById.keySet(), errors);
        validateNoCycles(agent, phasesById.keySet(), errors);

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid agent definition: " + String.join("; ", errors));
        }
    }

    private void validateDependencies(AgentDefinition agent, Set<String> phaseIds, List<String> errors) {
        for (AgentPhase phase : agent.phases()) {
            for (String dependency : phase.dependencies()) {
                if (!phaseIds.contains(dependency)) {
                    errors.add("Phase '" + phase.id() + "' depends on unknown phase '" + dependency + "'");
                }
            }
        }
    }

    private void validateEdges(AgentDefinition agent, Set<String> phaseIds, List<String> errors) {
        for (EdgeDefinition edge : agent.edges()) {
            if (!phaseIds.contains(edge.from())) {
                errors.add("Edge references unknown from phase '" + edge.from() + "'");
            }
            if (!phaseIds.contains(edge.to())) {
                errors.add("Edge references unknown to phase '" + edge.to() + "'");
            }
        }
    }

    private void validateNoCycles(AgentDefinition agent, Set<String> phaseIds, List<String> errors) {
        Map<String, Integer> inDegree = phaseIds.stream().collect(Collectors.toMap(id -> id, id -> 0));
        Map<String, List<String>> adjacency = phaseIds.stream().collect(Collectors.toMap(id -> id, id -> new ArrayList<>()));
        for (AgentPhase phase : agent.phases()) {
            for (String dependency : phase.dependencies()) {
                addGraphEdge(dependency, phase.id(), phaseIds, adjacency, inDegree);
            }
        }
        for (EdgeDefinition edge : agent.edges()) {
            addGraphEdge(edge.from(), edge.to(), phaseIds, adjacency, inDegree);
        }

        ArrayDeque<String> ready = new ArrayDeque<>();
        inDegree.forEach((id, degree) -> {
            if (degree == 0) {
                ready.add(id);
            }
        });

        Set<String> processed = new HashSet<>();
        while (!ready.isEmpty()) {
            String phaseId = ready.removeFirst();
            processed.add(phaseId);
            for (String downstream : adjacency.getOrDefault(phaseId, List.of())) {
                int next = inDegree.computeIfPresent(downstream, (id, value) -> value - 1);
                if (next == 0) {
                    ready.add(downstream);
                }
            }
        }

        if (processed.size() != phaseIds.size()) {
            errors.add("Agent contains a phase dependency/edge cycle");
        }
    }

    private void addGraphEdge(String from, String to, Set<String> phaseIds, Map<String, List<String>> adjacency,
                              Map<String, Integer> inDegree) {
        if (!phaseIds.contains(from) || !phaseIds.contains(to)) {
            return;
        }
        adjacency.get(from).add(to);
        inDegree.computeIfPresent(to, (id, value) -> value + 1);
    }
}
