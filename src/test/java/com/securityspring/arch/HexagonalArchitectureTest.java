package com.securityspring.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Garante as fronteiras da arquitetura hexagonal:
 *  - core (domain + ports + services) nunca depende de adapter ou infra
 *  - core.service nunca importa classes de adapter
 *  - adapter.in nunca depende de adapter.out (e vice-versa)
 */
class HexagonalArchitectureTest {

    static JavaClasses classes;

    @BeforeAll
    static void loadClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.securityspring");
    }

    @Test
    void core_domain_must_not_depend_on_adapters_or_infra() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..core.domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..adapter..", "..infra..");

        rule.check(classes);
    }

    @Test
    void core_ports_must_not_depend_on_adapters_or_infra() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..core.ports..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..adapter..", "..infra..");

        rule.check(classes);
    }

    @Test
    void core_services_must_not_depend_on_adapters_or_infra() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..core.service..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..adapter..", "..infra..");

        rule.check(classes);
    }

    @Test
    void adapter_in_must_not_depend_on_adapter_out() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..adapter.in..")
                .should().dependOnClassesThat()
                .resideInAPackage("..adapter.out..");

        rule.check(classes);
    }

    @Test
    void adapter_out_must_not_depend_on_adapter_in() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..adapter.out..")
                .should().dependOnClassesThat()
                .resideInAPackage("..adapter.in..");

        rule.check(classes);
    }

    @Test
    void adapter_in_must_not_depend_on_output_ports() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..adapter.in..")
                .should().dependOnClassesThat()
                .resideInAPackage("..core.ports.out..");

        rule.check(classes);
    }

    @Test
    void core_domain_and_ports_must_not_depend_on_spring() {
        // core/domain e core/ports não devem ter nenhuma dependência de Spring.
        noClasses()
                .that().resideInAnyPackage("..core.domain..", "..core.ports..")
                .should().dependOnClassesThat()
                .resideInAPackage("org.springframework..")
                .check(classes);
    }

    @Test
    void core_service_may_only_use_spring_transaction() {
        // Exceção consciente: @Transactional em core/service é aceito para manter
        // a demarcação transacional no nível do use-case, evitando transações
        // órfãs em operações multi-passo. Spring MVC, Data, Security e outros
        // módulos Spring continuam proibidos em core/.
        DescribedPredicate<JavaClass> springButNotTransaction =
                JavaClass.Predicates.resideInAPackage("org.springframework..")
                        .and(not(JavaClass.Predicates.resideInAPackage("org.springframework.transaction..")));

        noClasses()
                .that().resideInAPackage("..core.service..")
                .should().dependOnClassesThat(springButNotTransaction)
                .check(classes);
    }

    @Test
    void adapter_dtos_must_not_enter_core_service() {
        noClasses()
                .that().resideInAPackage("..core.service..")
                .should().dependOnClassesThat()
                .resideInAPackage("..adapter.in.dtos..")
                .check(classes);
    }

    @Test
    void services_must_only_implement_use_case_ports() {
        ArchRule rule = classes()
                .that().resideInAPackage("..core.service..")
                .and().areNotInterfaces()
                .and().areNotAnonymousClasses()
                .should().implement(
                        com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage("..core.ports.in..")
                                .or(com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage("..core.ports.out.."))
                );

        rule.check(classes);
    }
}
