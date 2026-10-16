package io.chronos.engine.bootstrap;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.chronos.engine.bootstrap.Main.TransformComponent;
import io.chronos.engine.domain.ecs.Component;
import io.chronos.engine.domain.event.GameEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Full-classpath ArchUnit architecture verification and end-to-end bootstrap integration test. */
@AnalyzeClasses(
    packages = "io.chronos.engine",
    importOptions = ImportOption.DoNotIncludeTests.class)
final class ArchitectureAndBootstrapTest {

  @ArchTest
  static final ArchRule FULL_CLASSPATH_LAYERED_ARCHITECTURE =
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
          .mayNotBeAccessedByAnyLayer();

  @ArchTest
  static final ArchRule DOMAIN_HAS_NO_EXTERNAL_OR_OUTWARD_DEPENDENCIES =
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
              "io.netty..");

  @ArchTest
  static final ArchRule COMPONENTS_AND_EVENTS_ARE_RECORDS_OR_INTERFACES =
      classes()
          .that()
          .implement(Component.class)
          .or()
          .implement(GameEvent.class)
          .should()
          .beRecords()
          .orShould()
          .beInterfaces();

  @Test
  void bootstrapMainAndContainerExecuteCleanly(@TempDir Path tempDir) {
    TransformComponent result = Main.runHeadlessVerification(tempDir);

    assertThat(result.x()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
    assertThat(result.y()).isCloseTo(2.0, org.assertj.core.data.Offset.offset(1e-6));
    assertThat(result.z()).isCloseTo(-0.5, org.assertj.core.data.Offset.offset(1e-6));
    assertThat(Files.exists(tempDir.resolve("snapshot-tick-1.json"))).isTrue();

    ChronosContainer container = new ChronosContainer(16, tempDir);
    try (container) {
      assertThat(container.world()).isNotNull();
      assertThat(container.commandBus()).isNotNull();
      assertThat(container.renderAdapter()).isNotNull();
      assertThat(container.telemetryAdapter()).isNotNull();
      assertThat(container.snapshotStorePort()).isNotNull();
      assertThat(container.networkTransportPort()).isNotNull();
    }
    container.close();
    Main.main(new String[0]);
  }
}
