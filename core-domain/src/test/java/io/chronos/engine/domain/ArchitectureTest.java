package io.chronos.engine.domain;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.chronos.engine.domain.ecs.Component;
import io.chronos.engine.domain.event.GameEvent;

/**
 * Automated ArchUnit verification suite enforcing Clean / Hexagonal Architecture boundaries and
 * domain purity.
 */
@AnalyzeClasses(
    packages = "io.chronos.engine",
    importOptions = ImportOption.DoNotIncludeTests.class)
public final class ArchitectureTest {

  @ArchTest
  static final ArchRule DOMAIN_MUST_HAVE_ZERO_OUTWARD_OR_FRAMEWORK_DEPENDENCIES =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..application..",
              "..adapter..",
              "..bootstrap..",
              "org.slf4j..",
              "ch.qos.logback..",
              "io.micrometer..",
              "com.fasterxml.jackson..",
              "com.esotericsoftware.kryo..",
              "io.netty..")
          .because(
              ":core-domain must depend exclusively on standard Java 21 SE APIs without framework coupling");

  @ArchTest
  static final ArchRule APPLICATION_MUST_NOT_DEPEND_ON_ADAPTERS_OR_BOOTSTRAP =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..adapter..", "..bootstrap..")
          .because(":core-application may only depend inward on :core-domain")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule LAYERED_HEXAGONAL_ARCHITECTURE_IS_RESPECTED =
      layeredArchitecture()
          .consideringOnlyDependenciesInLayers()
          .layer("Domain")
          .definedBy("..domain..")
          .layer("Application")
          .definedBy("..application..")
          .layer("Adapters")
          .definedBy("..adapter..")
          .layer("Bootstrap")
          .definedBy("..bootstrap..")
          .whereLayer("Domain")
          .mayOnlyBeAccessedByLayers("Application", "Adapters", "Bootstrap")
          .whereLayer("Application")
          .mayOnlyBeAccessedByLayers("Adapters", "Bootstrap")
          .whereLayer("Adapters")
          .mayOnlyBeAccessedByLayers("Bootstrap")
          .whereLayer("Bootstrap")
          .mayNotBeAccessedByAnyLayer()
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule ECS_COMPONENTS_AND_EVENTS_MUST_BE_IMMUTABLE_OR_INTERFACES =
      classes()
          .that()
          .implement(Component.class)
          .or()
          .implement(GameEvent.class)
          .should()
          .beRecords()
          .orShould()
          .beInterfaces()
          .because("All ECS Components and Domain Events must be immutable Java 21 records");

  @ArchTest
  static final ArchRule DOMAIN_CONCRETE_CLASSES_MUST_BE_FINAL =
      classes()
          .that()
          .resideInAPackage("..domain..")
          .and()
          .areNotInterfaces()
          .should()
          .haveModifier(JavaModifier.FINAL)
          .because("Concrete classes in :core-domain must be final or records");
}
