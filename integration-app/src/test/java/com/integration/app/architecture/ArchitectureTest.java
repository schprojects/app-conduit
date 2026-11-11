package com.integration.app.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Enforces the architecture rules of the specs on production code (tests and the dev/test profile are excluded):
 * applications are called only through their client abstraction layer, and the framework stays reusable.
 */
class ArchitectureTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.integration");

    @Test
    void onlyTheClientLayerUsesHttpClients() {
        noClasses().that().resideOutsideOfPackage("com.integration.app.appb..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.web.client..", "org.springframework.http.client..", "java.net.http..",
                        "org.apache.camel.component.http..", "org.apache.camel.component.rest..")
                .because("applications are called only through their client abstraction layer")
                .check(PRODUCTION);
    }

    @Test
    void onlyTheBusinessServiceUsesTheClient() {
        noClasses().that().resideOutsideOfPackage("com.integration.app.appb..")
                .and().doNotHaveSimpleName("SomeDataService")
                .should().dependOnClassesThat().haveSimpleNameStartingWith("AppBClient")
                .orShould().dependOnClassesThat().haveSimpleNameStartingWith("RestAppBClient")
                .because("routes and controllers call Business Services, never the client layer directly")
                .check(PRODUCTION);
    }

    @Test
    void frameworkDoesNotDependOnApplicationCode() {
        noClasses().that().resideInAPackage("com.integration.framework..")
                .should().dependOnClassesThat().resideInAPackage("com.integration.app..")
                .because("the integration framework must be reusable for other applications")
                .check(PRODUCTION);
    }

    @Test
    void routesDoNotUseHttpOrRestComponentsToCallApplications() throws IOException {
        Pattern httpEndpoint = Pattern.compile("\"(rest|rest-api|http|https|netty-http|undertow|vertx-http):");
        List<String> violations;
        try (Stream<Path> sources = Files.walk(Path.of("src/main/java"))) {
            violations = sources.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("/appb/"))
                    .filter(p -> {
                        try {
                            return httpEndpoint.matcher(Files.readString(p)).find();
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .map(Path::toString).toList();
        }
        assertEquals(List.of(), violations, "production routes must not call applications via rest:/http:");
    }
}
