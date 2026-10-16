package io.chronos.engine.application.loop;

import io.chronos.engine.application.cqrs.CommandBus;
import io.chronos.engine.application.usecase.EngineLifecycleUseCase;
import io.chronos.engine.domain.ecs.World;
import io.chronos.engine.domain.port.RenderOutputPort;
import io.chronos.engine.domain.port.TelemetryPort;
import io.chronos.engine.domain.state.EngineState;
import java.util.Objects;
import java.util.concurrent.locks.LockSupport;

/**
 * Deterministic Fixed-Timestep Game Loop Engine with render interpolation and spiral-of-death
 * protection.
 *
 * <p>Decouples deterministic simulation updates ({@code fixedDeltaSeconds}) from variable-rate
 * presentation frames via an interpolation factor {@code alpha in [0.0, 1.0)}.
 */
public final class DeterministicGameLoop {

  private static final double NANOS_PER_SECOND = 1_000_000_000.0;
  private static final long SLEEP_THRESHOLD_NANOS = 1_000_000L;
  private static final int DEFAULT_COMMAND_DRAIN_LIMIT = 4096;

  private final World world;
  private final CommandBus commandBus;
  private final EngineLifecycleUseCase lifecycleUseCase;
  private final RenderOutputPort renderPort;
  private final TelemetryPort telemetryPort;

  private final double fixedDeltaSeconds;
  private final long fixedDeltaNanos;
  private final double maxAccumulatedSeconds;

  public DeterministicGameLoop(
      World world,
      CommandBus commandBus,
      EngineLifecycleUseCase lifecycleUseCase,
      RenderOutputPort renderPort,
      TelemetryPort telemetryPort,
      int targetUpdatesPerSecond,
      int maxUpdatesPerFrame) {
    this.world = Objects.requireNonNull(world, "world must not be null");
    this.commandBus = Objects.requireNonNull(commandBus, "commandBus must not be null");
    this.lifecycleUseCase =
        Objects.requireNonNull(lifecycleUseCase, "lifecycleUseCase must not be null");
    this.renderPort = Objects.requireNonNull(renderPort, "renderPort must not be null");
    this.telemetryPort = Objects.requireNonNull(telemetryPort, "telemetryPort must not be null");

    if (targetUpdatesPerSecond <= 0 || targetUpdatesPerSecond > 1000) {
      throw new IllegalArgumentException(
          "targetUpdatesPerSecond must be in [1, 1000]: " + targetUpdatesPerSecond);
    }
    if (maxUpdatesPerFrame <= 0) {
      throw new IllegalArgumentException(
          "maxUpdatesPerFrame must be strictly positive: " + maxUpdatesPerFrame);
    }

    this.fixedDeltaSeconds = 1.0 / targetUpdatesPerSecond;
    this.fixedDeltaNanos = Math.round(NANOS_PER_SECOND / targetUpdatesPerSecond);
    this.maxAccumulatedSeconds = this.fixedDeltaSeconds * maxUpdatesPerFrame;
  }

  /**
   * Runs the continuous game loop until the lifecycle state transitions to {@link
   * EngineState.ShuttingDown} or {@link EngineState.Terminated}.
   */
  public void runLoop() {
    ensureReadyToRun();
    long previousTimeNanos = System.nanoTime();
    double accumulatorSeconds = 0.0;

    try {
      while (isLoopActive()) {
        long currentTimeNanos = System.nanoTime();
        long elapsedNanos = Math.max(0L, currentTimeNanos - previousTimeNanos);
        previousTimeNanos = currentTimeNanos;

        double frameDeltaSeconds = elapsedNanos / NANOS_PER_SECOND;
        accumulatorSeconds += frameDeltaSeconds;

        if (accumulatorSeconds > maxAccumulatedSeconds) {
          double droppedSeconds = accumulatorSeconds - maxAccumulatedSeconds;
          accumulatorSeconds = maxAccumulatedSeconds;
          telemetryPort.recordSpiralOfDeathPanic(lifecycleUseCase.currentTick(), droppedSeconds);
        }

        commandBus.drainQueuedCommands(DEFAULT_COMMAND_DRAIN_LIMIT);

        EngineState state = lifecycleUseCase.currentState();
        if (state instanceof EngineState.Running) {
          while (accumulatorSeconds >= fixedDeltaSeconds && isRunningState()) {
            executeSingleDeterministicTick();
            accumulatorSeconds -= fixedDeltaSeconds;
          }
        } else if (state instanceof EngineState.Paused) {
          accumulatorSeconds = 0.0;
        }

        double alpha = Math.clamp(accumulatorSeconds / fixedDeltaSeconds, 0.0, 1.0);
        telemetryPort.recordFrameInterpolation(alpha);
        renderPort.renderFrame(world, alpha);

        long frameWorkNanos = System.nanoTime() - currentTimeNanos;
        long remainingNanos = fixedDeltaNanos - frameWorkNanos;
        if (remainingNanos > SLEEP_THRESHOLD_NANOS) {
          LockSupport.parkNanos(remainingNanos / 2);
        } else {
          Thread.onSpinWait();
        }
      }
    } finally {
      world.shutdownSystems();
      lifecycleUseCase.markTerminated();
    }
  }

  /**
   * Executes a deterministic number of ticks synchronously without wall-clock pacing. Ideal for
   * headless verification, replay validation, and automated testing.
   *
   * @param tickCount number of ticks to execute
   */
  public void runTicksDeterministically(int tickCount) {
    if (tickCount <= 0) {
      throw new IllegalArgumentException("tickCount must be > 0: " + tickCount);
    }
    ensureReadyToRun();
    for (int i = 0; i < tickCount && isLoopActive(); i++) {
      commandBus.drainQueuedCommands(DEFAULT_COMMAND_DRAIN_LIMIT);
      if (isRunningState()) {
        executeSingleDeterministicTick();
        telemetryPort.recordFrameInterpolation(0.0);
        renderPort.renderFrame(world, 0.0);
      }
    }
  }

  private void executeSingleDeterministicTick() {
    long tickStartNanos = System.nanoTime();
    long tick = lifecycleUseCase.incrementAndGetTick();
    world.stepSystems(tick, fixedDeltaSeconds);
    long tickDurationNanos = System.nanoTime() - tickStartNanos;
    telemetryPort.recordTickDurationNanos(tick, tickDurationNanos);
  }

  private void ensureReadyToRun() {
    EngineState state = lifecycleUseCase.currentState();
    if (state instanceof EngineState.Uninitialized) {
      lifecycleUseCase.initialize();
      lifecycleUseCase.start();
    } else if (state instanceof EngineState.Initialized) {
      lifecycleUseCase.start();
    }
  }

  private boolean isRunningState() {
    return lifecycleUseCase.currentState() instanceof EngineState.Running;
  }

  private boolean isLoopActive() {
    EngineState state = lifecycleUseCase.currentState();
    return !(state instanceof EngineState.ShuttingDown || state instanceof EngineState.Terminated);
  }
}
