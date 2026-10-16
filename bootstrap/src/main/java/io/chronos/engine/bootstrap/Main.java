package io.chronos.engine.bootstrap;

import io.chronos.engine.domain.ecs.Component;
import io.chronos.engine.domain.ecs.EntityId;
import io.chronos.engine.domain.ecs.GameSystem;
import io.chronos.engine.domain.ecs.World;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Production entry point for the Chronos Enterprise Base Game Engine.
 *
 * <p>Initializes the dependency injection container, registers a JVM shutdown hook for
 * zero-data-loss termination, executes a deterministic headless verification tick, and shuts down
 * cleanly.
 */
public final class Main {

  private static final Logger LOG = LoggerFactory.getLogger(Main.class);
  private static final int DEFAULT_MAX_ENTITIES = 4096;

  /** Sample immutable position component record for headless verification. */
  public record TransformComponent(double x, double y, double z) implements Component {}

  /** Sample immutable velocity component record for headless verification. */
  public record VelocityComponent(double vx, double vy, double vz) implements Component {}

  /** Deterministic kinematics system updating entities with Transform + Velocity components. */
  public static final class KinematicsSystem implements GameSystem {

    @Override
    public int priority() {
      return 10;
    }

    @Override
    public void update(World world, long tick, double fixedDeltaSeconds) {
      world.forEachMatching(
          entityId -> {
            TransformComponent transform =
                world.requireComponent(entityId, TransformComponent.class);
            VelocityComponent velocity = world.requireComponent(entityId, VelocityComponent.class);
            TransformComponent updated =
                new TransformComponent(
                    transform.x() + velocity.vx() * fixedDeltaSeconds,
                    transform.y() + velocity.vy() * fixedDeltaSeconds,
                    transform.z() + velocity.vz() * fixedDeltaSeconds);
            world.addComponent(entityId, updated);
          },
          TransformComponent.class,
          VelocityComponent.class);
    }
  }

  private Main() {}

  public static void main(String[] args) {
    Path snapshotDir = Path.of(System.getProperty("java.io.tmpdir"), "chronos-snapshots");
    runHeadlessVerification(snapshotDir);
  }

  /**
   * Executes a full lifecycle verification run against the provided snapshot directory.
   *
   * @param snapshotDir directory where shutdown snapshots are persisted
   * @return updated transform of the probe entity after one deterministic tick
   */
  public static TransformComponent runHeadlessVerification(Path snapshotDir) {
    LOG.info("Bootstrapping Chronos Engine on Java {}", Runtime.version());
    ChronosContainer container = new ChronosContainer(DEFAULT_MAX_ENTITIES, snapshotDir);

    Thread shutdownHook =
        Thread.ofPlatform().name("chronos-shutdown-hook").unstarted(container::close);
    Runtime.getRuntime().addShutdownHook(shutdownHook);

    try (container) {
      AtomicInteger observedEvents = new AtomicInteger();
      container.eventBus().subscribe(event -> observedEvents.incrementAndGet());

      World world = container.world();
      world.registerSystem(new KinematicsSystem());

      EntityId probeEntity = world.createEntity(container.lifecycleUseCase().currentTick());
      world.addComponent(probeEntity, new TransformComponent(0.0, 0.0, 0.0));
      world.addComponent(probeEntity, new VelocityComponent(60.0, 120.0, -30.0));

      container.gameLoop().runTicksDeterministically(1);
      container.eventBus().drainToSubscribers();

      TransformComponent updated = world.requireComponent(probeEntity, TransformComponent.class);
      LOG.info(
          "Headless verification tick succeeded: tick={}, entity={}, transform={}, events={}",
          container.lifecycleUseCase().currentTick(),
          probeEntity,
          updated,
          observedEvents.get());
      return updated;
    } finally {
      try {
        Runtime.getRuntime().removeShutdownHook(shutdownHook);
      } catch (IllegalStateException ignored) {
        // JVM is already shutting down
      }
    }
  }
}
