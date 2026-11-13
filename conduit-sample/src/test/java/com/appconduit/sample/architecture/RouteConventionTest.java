package com.appconduit.sample.architecture;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Route architecture rules (transport URIs only in connectors, operation routes call capability endpoints only, no error handling or async steps in
 * operation routes), checked on the packaged route files (src/main/resources/camel).
 * ArchUnit cannot see YAML routes, so the files are parsed. Dev/test routes live under src/test/resources/camel
 * and are not checked.
 */
class RouteConventionTest {

    private static final Path CAMEL = Path.of("src/main/resources/camel");
    private static final Pattern TRANSPORT_URI = Pattern.compile("^(https?|netty-http|vertx-http|undertow|jetty|rest|rest-api):.*");
    private static final Pattern CAPABILITY = Pattern.compile("direct:([a-z0-9]+)\\.([A-Za-z0-9]+)");
    private static final Pattern PRIVATE_ROUTE = Pattern.compile("direct:([a-z0-9]+)\\._[A-Za-z0-9]+");
    private static final List<String> ASYNC_STEPS = List.of("threads", "seda", "wireTap", "parallelProcessing");

    private record RouteFile(Path path, Object content) {
        boolean inConnectors() {
            return CAMEL.relativize(path).startsWith("connectors");
        }

        boolean inOperations() {
            return CAMEL.relativize(path).startsWith("operations");
        }

        String system() {
            return CAMEL.relativize(path).getName(1).toString();
        }
    }

    private static List<RouteFile> routeFiles() throws IOException {
        try (Stream<Path> files = Files.walk(CAMEL)) {
            return files.filter(p -> p.toString().endsWith(".yaml"))
                    .map(p -> {
                        try {
                            return new RouteFile(p, new Yaml().load(Files.readString(p)));
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    }).toList();
        }
    }

    /** Every key and every string value found anywhere in the file, in document order. */
    private static void walk(Object node, List<String> keys, List<String> strings) {
        if (node instanceof Map<?, ?> map) {
            map.forEach((k, v) -> {
                keys.add(String.valueOf(k));
                walk(v, keys, strings);
            });
        } else if (node instanceof List<?> list) {
            list.forEach(v -> walk(v, keys, strings));
        } else if (node instanceof String s) {
            strings.add(s);
        }
    }

    private static List<String> keys(RouteFile file) {
        List<String> keys = new ArrayList<>();
        walk(file.content(), keys, new ArrayList<>());
        return keys;
    }

    private static List<String> strings(RouteFile file) {
        List<String> strings = new ArrayList<>();
        walk(file.content(), new ArrayList<>(), strings);
        return strings;
    }

    @Test
    void routeFilesExist() throws IOException {
        assertFalse(routeFiles().isEmpty());
    }

    @Test
    void transportUrisAndConnectorSettingsAppearOnlyInConnectors() throws IOException {
        List<String> violations = new ArrayList<>();
        for (RouteFile file : routeFiles()) {
            if (file.inConnectors() || file.path().getFileName().toString().equals("rest.yaml")) {
                continue;
            }
            strings(file).stream()
                    .filter(s -> TRANSPORT_URI.matcher(s).matches() || s.contains("{{integration.connectors."))
                    .forEach(s -> violations.add(file.path() + ": " + s));
        }
        assertEquals(List.of(), violations);
    }

    @Test
    void connectorCredentialsComeFromPlaceholders() throws IOException {
        List<String> violations = new ArrayList<>();
        for (RouteFile file : routeFiles()) {
            if (!file.inConnectors()) {
                continue;
            }
            collectCredentialLiterals(file.content(), file.path().toString(), violations);
        }
        assertEquals(List.of(), violations);
    }

    private static void collectCredentialLiterals(Object node, String where, List<String> violations) {
        if (node instanceof Map<?, ?> map) {
            map.forEach((k, v) -> {
                boolean credential = String.valueOf(k).matches("auth(Username|Password)|password|token");
                if (credential && !String.valueOf(v).contains("{{")) {
                    violations.add(where + ": " + k + " must come from a {{placeholder}}");
                }
                collectCredentialLiterals(v, where, violations);
            });
        } else if (node instanceof List<?> list) {
            list.forEach(v -> collectCredentialLiterals(v, where, violations));
        }
    }

    @Test
    void capabilityRoutesSetTargetAppAndLiveInTheirSystemsFolder() throws IOException {
        List<String> violations = new ArrayList<>();
        for (RouteFile file : routeFiles()) {
            if (!file.inConnectors()) {
                continue;
            }
            List<String> strings = strings(file);
            boolean entry = strings.stream().anyMatch(s -> CAPABILITY.matcher(s).matches());
            if (entry && !strings.contains("targetApp")) {
                violations.add(file.path() + ": a capability endpoint must set the property targetApp");
            }
            for (String s : strings) {
                var capability = CAPABILITY.matcher(s);
                var privateRoute = PRIVATE_ROUTE.matcher(s);
                String system = capability.matches() ? capability.group(1) : privateRoute.matches() ? privateRoute.group(1) : null;
                if (system != null && !system.equals(file.system())) {
                    violations.add(file.path() + ": " + s + " does not belong to the folder " + file.system());
                }
            }
        }
        assertEquals(List.of(), violations);
    }

    @Test
    void privateConnectorRoutesAreNotUsedOutsideTheirConnector() throws IOException {
        List<String> violations = new ArrayList<>();
        for (RouteFile file : routeFiles()) {
            if (file.inConnectors()) {
                continue;
            }
            strings(file).stream().filter(s -> PRIVATE_ROUTE.matcher(s).matches())
                    .forEach(s -> violations.add(file.path() + ": " + s));
        }
        assertEquals(List.of(), violations);
    }

    @Test
    void operationRoutesCallOnlyCapabilityEndpointsAndHaveNoErrorHandling() throws IOException {
        List<String> violations = new ArrayList<>();
        for (RouteFile file : routeFiles()) {
            if (!file.inOperations()) {
                continue;
            }
            keys(file).stream().filter(k -> List.of("onException", "errorHandler", "routeConfigurationId").contains(k))
                    .forEach(k -> violations.add(file.path() + ": operation routes must not define " + k));
            strings(file).stream().filter(s -> s.startsWith("direct:") || TRANSPORT_URI.matcher(s).matches())
                    .filter(s -> !CAPABILITY.matcher(s).matches() && !isOperationEndpoint(s))
                    .forEach(s -> violations.add(file.path() + ": " + s + " is not a capability endpoint"));
        }
        assertEquals(List.of(), violations);
    }

    /** An operation route's own entry endpoint: direct:<operationId>, a name without a dot. */
    private static boolean isOperationEndpoint(String uri) {
        return uri.matches("direct:[A-Za-z0-9]+");
    }

    @Test
    void noAsynchronousSteps() throws IOException {
        List<String> violations = new ArrayList<>();
        for (RouteFile file : routeFiles()) {
            keys(file).stream().filter(ASYNC_STEPS::contains)
                    .forEach(k -> violations.add(file.path() + ": " + k + " (routes run on the request thread, FR-12)"));
            strings(file).stream().filter(s -> s.startsWith("seda:") || s.startsWith("vm:"))
                    .forEach(s -> violations.add(file.path() + ": " + s));
        }
        assertEquals(List.of(), violations);
    }
}
