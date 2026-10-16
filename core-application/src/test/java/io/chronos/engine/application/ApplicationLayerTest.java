package io.chronos.engine.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.chronos.engine.application.cqrs.Command;
import io.chronos.engine.application.cqrs.CommandBus;
import io.chronos.engine.application.event.LockFreeRingBufferEventBus;
import io.chronos.engine.application.loop.DeterministicGameLoop;
import io.chronos.engine.application.usecase.EngineLifecycleUseCase;
import io.chronos.engine.application.usecase.EngineLifecycleUseCase.InitializeEngineCommand;
import io.chronos.engine.application.usecase.EngineLifecycleUseCase.PauseEngineCommand;
import io.chronos.engine.application.usecase.EngineLifecycleUseCase.ResumeEngineCommand;
import io.chronos.engine.application.usecase.EngineLifecycleUseCase.ShutdownEngineCommand;
import io.chronos.engine.application.usecase.EngineLifecycleUseCase.StartEngineCommand;
import io.chronos.engine.domain.ecs.World;
import io.chronos.engine.domain.event.GameEvent;
import io.chronos.engine.domain.port.TelemetryPort;
import io.chronos.engine.domain.state.EngineState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;

final class ApplicationLayerTest {

  record UnregisteredCommand() implements Command {}

  @Test
  void commandBusAndLifecycleUseCaseHandleAllTransitions() {
    LockFreeRingBufferEventBus eventBus = new LockFreeRingBufferEventBus(16);
    List<GameEvent> receivedEvents = new ArrayList<>();
    eventBus.subscribe(receivedEvents::add);

    assertThat(eventBus.capacity()).isEqualTo(16);
    assertThatThrownBy(() -> new LockFreeRingBufferEventBus(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LockFreeRingBufferEventBus(15))
        .isInstanceOf(IllegalArgumentException.class);

    CommandBus commandBus = new CommandBus();
    EngineLifecycleUseCase lifecycle = new EngineLifecycleUseCase(eventBus);
    lifecycle.registerHandlers(commandBus);

    assertThatThrownBy(() -> commandBus.register(InitializeEngineCommand.class, cmd -> {}))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> commandBus.enqueue(new UnregisteredCommand()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> commandBus.dispatchImmediate(new UnregisteredCommand()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> commandBus.drainQueuedCommands(0))
        .isInstanceOf(IllegalArgumentException.class);

    commandBus.enqueue(new InitializeEngineCommand());
    commandBus.enqueue(new StartEngineCommand());
    commandBus.enqueue(new PauseEngineCommand("maintenance"));
    commandBus.enqueue(new ResumeEngineCommand());

    assertThat(commandBus.drainQueuedCommands(2)).isEqualTo(2);
    assertThat(commandBus.drainQueuedCommands(10)).isEqualTo(2);
    assertThat(lifecycle.currentState()).isInstanceOf(EngineState.Running.class);

    assertThatThrownBy(lifecycle::initialize).isInstanceOf(IllegalStateException.class);

    commandBus.dispatchImmediate(new ShutdownEngineCommand());
    lifecycle.initiateShutdown();
    lifecycle.markTerminated();
    lifecycle.initiateShutdown();
    lifecycle.markTerminated();
    assertThat(lifecycle.currentState()).isInstanceOf(EngineState.Terminated.class);

    EngineLifecycleUseCase directTerminateLifecycle = new EngineLifecycleUseCase(eventBus);
    directTerminateLifecycle.markTerminated();
    assertThat(directTerminateLifecycle.currentState()).isInstanceOf(EngineState.Terminated.class);

    int dispatchedEvents = eventBus.drainToSubscribers();
    assertThat(dispatchedEvents).isEqualTo(8);
    assertThat(receivedEvents).hasSize(8);

    for (int i = 0; i < 25; i++) {
      eventBus.publish(
          new GameEvent.CustomDomainEvent(UUID.randomUUID(), i, Instant.now(), "OVERFLOW", i));
    }
    assertThat(eventBus.drainToSubscribers()).isEqualTo(16);
  }

  @Test
  void deterministicGameLoopExecutesTicksPauseAndSpiralOfDeathProtection() {
    LockFreeRingBufferEventBus eventBus = new LockFreeRingBufferEventBus(64);
    World world = new World(16, eventBus);
    CommandBus commandBus = new CommandBus();
    EngineLifecycleUseCase lifecycle = new EngineLifecycleUseCase(eventBus);
    lifecycle.registerHandlers(commandBus);

    AtomicInteger ticksRecorded = new AtomicInteger();
    AtomicInteger spiralPanics = new AtomicInteger();
    AtomicInteger framesRendered = new AtomicInteger();

    TelemetryPort telemetry =
        new TelemetryPort() {
          @Override
          public void recordTickDurationNanos(long tick, long durationNanos) {
            ticksRecorded.incrementAndGet();
          }

          @Override
          public void recordFrameInterpolation(double alpha) {}

          @Override
          public void recordSpiralOfDeathPanic(long tick, double droppedSeconds) {
            spiralPanics.incrementAndGet();
          }
        };

    assertThatThrownBy(
            () ->
                new DeterministicGameLoop(
                    world, commandBus, lifecycle, (w, a) -> {}, telemetry, 0, 5))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new DeterministicGameLoop(
                    world, commandBus, lifecycle, (w, a) -> {}, telemetry, 2000, 5))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new DeterministicGameLoop(
                    world, commandBus, lifecycle, (w, a) -> {}, telemetry, 60, 0))
        .isInstanceOf(IllegalArgumentException.class);

    DeterministicGameLoop loop =
        new DeterministicGameLoop(
            world,
            commandBus,
            lifecycle,
            (w, alpha) -> {
              int frame = framesRendered.incrementAndGet();
              if (frame == 1) {
                // Induce >3ms delay at 1000 UPS (maxUpdatesPerFrame=1 => 1ms cap) to trigger spiral
                // clamp
                LockSupport.parkNanos(5_000_000L);
              } else if (frame == 2) {
                commandBus.enqueue(new PauseEngineCommand("test-pause"));
              } else if (frame == 3) {
                commandBus.enqueue(new ResumeEngineCommand());
                LockSupport.parkNanos(2_000_000L);
              } else if (frame >= 5) {
                commandBus.enqueue(new ShutdownEngineCommand());
              }
            },
            telemetry,
            1000,
            1);

    assertThatThrownBy(() -> loop.runTicksDeterministically(0))
        .isInstanceOf(IllegalArgumentException.class);

    // Verify starting from Initialized state
    lifecycle.initialize();
    loop.runTicksDeterministically(1);
    assertThat(lifecycle.currentTick()).isEqualTo(1L);

    // Run real-time loop through running, spiral clamp, pause, resume, and shutdown
    loop.runLoop();
    assertThat(lifecycle.currentState()).isInstanceOf(EngineState.Terminated.class);
    assertThat(ticksRecorded.get()).isGreaterThanOrEqualTo(2);
    assertThat(spiralPanics.get()).isGreaterThanOrEqualTo(1);

    // Verify starting from Uninitialized state
    EngineLifecycleUseCase uninitLifecycle = new EngineLifecycleUseCase(eventBus);
    DeterministicGameLoop uninitLoop =
        new DeterministicGameLoop(
            world, commandBus, uninitLifecycle, (w, a) -> {}, telemetry, 60, 5);
    uninitLoop.runTicksDeterministically(1);
    assertThat(uninitLifecycle.currentTick()).isEqualTo(1L);
  }
}
