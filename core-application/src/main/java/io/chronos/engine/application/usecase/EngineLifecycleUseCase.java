package io.chronos.engine.application.usecase;

import io.chronos.engine.application.cqrs.Command;
import io.chronos.engine.application.cqrs.CommandBus;
import io.chronos.engine.domain.event.DomainEventPublisher;
import io.chronos.engine.domain.event.GameEvent;
import io.chronos.engine.domain.state.EngineState;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Orchestrates engine lifecycle transitions (Initialize, Start, Pause, Resume, Shutdown) with
 * atomic state validation and domain event emission.
 */
public final class EngineLifecycleUseCase {

  public sealed interface LifecycleCommand extends Command
      permits InitializeEngineCommand,
          StartEngineCommand,
          PauseEngineCommand,
          ResumeEngineCommand,
          ShutdownEngineCommand {}

  public record InitializeEngineCommand() implements LifecycleCommand {}

  public record StartEngineCommand() implements LifecycleCommand {}

  public record PauseEngineCommand(String reason) implements LifecycleCommand {
    public PauseEngineCommand {
      Objects.requireNonNull(reason, "reason must not be null");
    }
  }

  public record ResumeEngineCommand() implements LifecycleCommand {}

  public record ShutdownEngineCommand() implements LifecycleCommand {}

  private final AtomicReference<EngineState> stateRef;
  private final AtomicLong currentTickRef;
  private final DomainEventPublisher eventPublisher;

  public EngineLifecycleUseCase(DomainEventPublisher eventPublisher) {
    this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
    this.stateRef = new AtomicReference<>(new EngineState.Uninitialized());
    this.currentTickRef = new AtomicLong(0L);
  }

  /**
   * Wires all lifecycle commands into the provided {@link CommandBus}.
   *
   * @param commandBus target command bus
   */
  public void registerHandlers(CommandBus commandBus) {
    Objects.requireNonNull(commandBus, "commandBus must not be null");
    commandBus.register(InitializeEngineCommand.class, cmd -> initialize());
    commandBus.register(StartEngineCommand.class, cmd -> start());
    commandBus.register(PauseEngineCommand.class, cmd -> pause(cmd.reason()));
    commandBus.register(ResumeEngineCommand.class, cmd -> resume());
    commandBus.register(ShutdownEngineCommand.class, cmd -> initiateShutdown());
  }

  public void initialize() {
    transitionTo(new EngineState.Initialized(Instant.now()));
  }

  public void start() {
    transitionTo(new EngineState.Running(Instant.now(), currentTickRef.get()));
  }

  public void pause(String reason) {
    Objects.requireNonNull(reason, "reason must not be null");
    transitionTo(new EngineState.Paused(Instant.now(), currentTickRef.get(), reason));
  }

  public void resume() {
    transitionTo(new EngineState.Running(Instant.now(), currentTickRef.get()));
  }

  public void initiateShutdown() {
    EngineState current = stateRef.get();
    if (current instanceof EngineState.ShuttingDown || current instanceof EngineState.Terminated) {
      return;
    }
    transitionTo(new EngineState.ShuttingDown(Instant.now(), currentTickRef.get()));
  }

  public void markTerminated() {
    EngineState current = stateRef.get();
    if (current instanceof EngineState.Terminated) {
      return;
    }
    if (!(current instanceof EngineState.ShuttingDown)) {
      initiateShutdown();
    }
    transitionTo(new EngineState.Terminated(Instant.now(), currentTickRef.get()));
  }

  public EngineState currentState() {
    return stateRef.get();
  }

  public long currentTick() {
    return currentTickRef.get();
  }

  public long incrementAndGetTick() {
    return currentTickRef.incrementAndGet();
  }

  private void transitionTo(EngineState targetState) {
    while (true) {
      EngineState previous = stateRef.get();
      if (!previous.canTransitionTo(targetState)) {
        throw new IllegalStateException(
            "Invalid EngineState transition from "
                + previous.label()
                + " to "
                + targetState.label());
      }
      if (stateRef.compareAndSet(previous, targetState)) {
        eventPublisher.publish(
            new GameEvent.EngineStateTransitioned(
                UUID.randomUUID(), currentTickRef.get(), Instant.now(), previous, targetState));
        return;
      }
    }
  }
}
