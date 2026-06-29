package com.unfurl.foundry.substrate.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureTest {
    private static final String ROOT = "com.unfurl.foundry.substrate";
    private static final Map<String, Set<String>> ALLOWED_MODULE_EDGES = Map.ofEntries(
            Map.entry("domain", Set.of("substrate-domain")),
            Map.entry("events", Set.of("substrate-events")),
            Map.entry("ports", Set.of("domain", "events", "substrate-domain", "substrate-ports")),
            Map.entry("prompt", Set.of("domain")),
            Map.entry("tools", Set.of("ports", "substrate-ports")),
            Map.entry("rag", Set.of("domain", "ports", "substrate-ports")),
            Map.entry("resolver", Set.of("domain", "substrate-resolver")),
            Map.entry("serialization", Set.of("domain")),
            Map.entry("offers", Set.of("domain", "ports", "dcp", "substrate-domain", "substrate-ports",
                    "substrate-composition-api")),
            Map.entry("testing", Set.of("domain", "ports", "tools", "rag", "substrate-ports")),
            Map.entry("engine", Set.of("domain", "events", "ports", "prompt", "tools", "rag", "resolver", "offers",
                    "substrate-domain", "substrate-ports"))
    );

    private final JavaClasses classes = new ClassFileImporter().importPaths(
            modulePath("domain"),
            modulePath("events"),
            modulePath("ports"),
            modulePath("prompt"),
            modulePath("tools"),
            modulePath("rag"),
            modulePath("resolver"),
            modulePath("serialization"),
            modulePath("offers"),
            modulePath("testing"),
            modulePath("engine")
    );

    @Test
    void productionCodeDoesNotUseSdkOrInfrastructureClients() {
        ArchRuleDefinition.noClasses()
                .that().resideInAPackage(ROOT + "..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.openai..",
                        "com.anthropic..",
                        "dev.langchain4j..",
                        "okhttp3..",
                        "retrofit2..",
                        "org.apache.http..",
                        "java.net.http..",
                        "org.springframework..",
                        "jakarta.ws.rs..",
                        "javax.ws.rs..",
                        "software.amazon.awssdk..",
                        "com.azure..",
                        "io.qdrant..",
                        "org.postgresql..")
                .check(classes);
    }

    @Test
    void productionCodeDoesNotImportHostProducts() {
        assertThat(disallowedDependencies("com.unfurl.flow", "com.unfurl.fabric"))
                .isEmpty();
        assertThat(classes.stream()
                .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
                .filter(dependency -> dependency.getTargetClass().getPackageName().startsWith("com.unfurl.foundry."))
                .filter(dependency -> !dependency.getTargetClass().getPackageName().startsWith(ROOT))
                .map(this::describe)
                .collect(Collectors.toList()))
                .isEmpty();
    }

    @Test
    void dcpDependenciesStayInOffers() {
        assertThat(classes.stream()
                .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
                .filter(dependency -> dependency.getTargetClass().getPackageName().startsWith("com.unfurl.dcp"))
                .filter(dependency -> !moduleOf(dependency.getOriginClass()).equals("offers"))
                .map(this::describe)
                .collect(Collectors.toList()))
                .isEmpty();
    }

    @Test
    void productionCodeDoesNotDependOnTestingFixtures() {
        assertThat(classes.stream()
                .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
                .filter(dependency -> dependency.getTargetClass().getPackageName().startsWith(ROOT + ".testing"))
                .filter(dependency -> !moduleOf(dependency.getOriginClass()).equals("testing"))
                .map(this::describe)
                .collect(Collectors.toList()))
                .isEmpty();
    }

    @Test
    void moduleEdgesMatchAllowedGraph() {
        Map<String, Set<String>> violations = new LinkedHashMap<>();
        for (JavaClass javaClass : classes) {
            String origin = moduleOf(javaClass);
            if (origin == null) {
                continue;
            }
            Set<String> invalidTargets = javaClass.getDirectDependenciesFromSelf().stream()
                    .map(Dependency::getTargetClass)
                    .map(this::moduleOf)
                    .filter(target -> target != null && !target.equals(origin))
                    .filter(target -> !ALLOWED_MODULE_EDGES.getOrDefault(origin, Set.of()).contains(target))
                    .collect(Collectors.toCollection(java.util.TreeSet::new));
            if (!invalidTargets.isEmpty()) {
                violations.put(javaClass.getName(), invalidTargets);
            }
        }

        assertThat(violations).isEmpty();
    }

    private java.util.List<String> disallowedDependencies(String... packagePrefixes) {
        return classes.stream()
                .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
                .filter(dependency -> {
                    String targetPackage = dependency.getTargetClass().getPackageName();
                    for (String prefix : packagePrefixes) {
                        if (targetPackage.startsWith(prefix)) {
                            return true;
                        }
                    }
                    return false;
                })
                .map(this::describe)
                .collect(Collectors.toList());
    }

    private String describe(Dependency dependency) {
        return dependency.getOriginClass().getName() + " -> " + dependency.getTargetClass().getName();
    }

    private String moduleOf(JavaClass javaClass) {
        String source = javaClass.getSource().map(location -> location.getUri().toString()).orElse("");
        for (String module : ALLOWED_MODULE_EDGES.keySet()) {
            if (source.contains("foundry-substrate-" + module)) {
                return module;
            }
        }
        String packageName = javaClass.getPackageName();
        if (!packageName.startsWith(ROOT + ".")) {
            return upstreamModule(packageName);
        }
        String remainder = packageName.substring((ROOT + ".").length());
        String top = remainder.split("\\.")[0];
        return switch (top) {
            case "agent", "embedding", "model", "runstate", "skill", "tool" -> "domain";
            case "events", "ports", "prompt", "tools", "rag", "resolver", "serialization", "offers", "testing", "engine" -> top;
            case "guardrail" -> "ports";
            default -> null;
        };
    }

    private String upstreamModule(String packageName) {
        if (packageName.startsWith("com.unfurl.substrate.domain")) {
            return "substrate-domain";
        }
        if (packageName.startsWith("com.unfurl.substrate.events")) {
            return "substrate-events";
        }
        if (packageName.startsWith("com.unfurl.substrate.ports") || packageName.startsWith("com.unfurl.substrate.policy")) {
            return "substrate-ports";
        }
        if (packageName.startsWith("com.unfurl.substrate.resolver")) {
            return "substrate-resolver";
        }
        if (packageName.startsWith("com.unfurl.substrate.composition")) {
            return "substrate-composition-api";
        }
        if (packageName.startsWith("com.unfurl.dcp")) {
            return "dcp";
        }
        return null;
    }

    private static Path modulePath(String module) {
        return Path.of("..", "foundry-substrate-" + module, "target", "classes");
    }
}
