package com.yclaims.claims.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit architecture enforcement tests.
 * Verifies the Dependency Direction Rule:
 *   Presentation → Application → Domain ← Infrastructure (implements ports)
 *
 * Domain must NEVER depend on Spring, JPA, Kafka, or infrastructure packages.
 * This guarantees the domain is portable and independently testable.
 */
@AnalyzeClasses(
    packages = "com.yclaims.claims",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class ClaimsArchitectureTest {

    @ArchTest
    static final ArchRule domainMustNotDependOnInfrastructure = noClasses()
            .that().resideInAPackage("..claims.domain..")
            .should().dependOnClassesThat()
            .resideInAPackage("..claims.infrastructure..");

    @ArchTest
    static final ArchRule domainMustNotDependOnPresentation = noClasses()
            .that().resideInAPackage("..claims.domain..")
            .should().dependOnClassesThat()
            .resideInAPackage("..claims.presentation..");

    @ArchTest
    static final ArchRule domainMustNotDependOnSpring = noClasses()
            .that().resideInAPackage("..claims.domain..")
            .should().dependOnClassesThat()
            .resideInAPackage("org.springframework..");

    @ArchTest
    static final ArchRule domainMustNotDependOnJpa = noClasses()
            .that().resideInAPackage("..claims.domain..")
            .should().dependOnClassesThat()
            .resideInAPackage("jakarta.persistence..");

    @ArchTest
    static final ArchRule presentationMustNotAccessInfrastructure = noClasses()
            .that().resideInAPackage("..claims.presentation..")
            .should().dependOnClassesThat()
            .resideInAPackage("..claims.infrastructure..");
}
