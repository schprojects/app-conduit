package com.integration.app.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Java rules of architecture.md section 12 (the route rules are in RouteConventionTest), checked on production code
 * only: test sources and the dev/test routes are excluded.
 */
class ArchitectureTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.integration");

    @Test
    void onlyConnectorsUseHttpClientsOrVendorSdks() {
        noClasses().that().resideOutsideOfPackage("com.integration.app.connectors..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "java.net.http..", "org.springframework.web.client..", "org.springframework.http.client..",
                        "org.apache.hc..", "org.apache.camel.component.http..",
                        "com.vendor.sdk..")
                .because("a target system is reached only through its connector (FR-4)")
                .allowEmptyShould(true)
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
    void applicationHasNoControllers() {
        noClasses().that().resideInAPackage("com.integration.app..")
                .should().beAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .orShould().beAnnotatedWith("org.springframework.stereotype.Controller")
                .because("inbound operations come from the OpenAPI contract (FR-1, AC-17)")
                .allowEmptyShould(true)
                .check(PRODUCTION);
    }
}
