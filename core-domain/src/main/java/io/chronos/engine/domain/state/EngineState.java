package io.chronos.engine.domain.state;

import java.time.Instant;
import java.util.Objects;

/** Type-safe sealed state machine representing the lifecycle of the game engine. */
public sealed interface EngineState
    permits EngineState.Uninitialized,
        EngineState.Initialized,
        EngineState.Running,
        EngineState.Paused,
        EngineState.ShuttingDown,
        EngineState.Terminated {

  String label();

  default boolean canTransitionTo(EngineState next) {
    Objects.requireNonNull(next, "next state must not be null");
    return switch (this) {
      case Uninitialized ignored -> next instanceof Initialized || next instanceof ShuttingDown;
      case Initialized ignored -> next instanceof Running || next instanceof ShuttingDown;
      case Running ignored -> next instanceof Paused || next instanceof ShuttingDown;
      case Paused ignored -> next instanceof Running || next instanceof ShuttingDown;
      case ShuttingDown ignored -> next instanceof Terminated;
      case Terminated ignored -> false;
    };
  }

  record Uninitialized() implements EngineState {
    @Override
    public String label() {
      return "UNINITIALIZED";
    }
  }

  record Initialized(Instant initializedAt) implements EngineState {
    public Initialized {
      Objects.requireNonNull(initializedAt, "initializedAt must not be null");
    }

    @Override
    public String label() {
      return "INITIALIZED";
    }
  }

  record Running(Instant startedAt, long startTick) implements EngineState {
    public Running {
      Objects.requireNonNull(startedAt, "startedAt must not be null");
    }

    @Override
    public String label() {
      return "RUNNING";
    }
  }

  record Paused(Instant pausedAt, long pausedTick, String reason) implements EngineState {
    public Paused {
      Objects.requireNonNull(pausedAt, "pausedAt must not be null");
      Objects.requireNonNull(reason, "reason must not be null");
    }

    @Override
    public String label() {
      return "PAUSED";
    }
  }

  record ShuttingDown(Instant initiatedAt, long finalTick) implements EngineState {
    public ShuttingDown {
      Objects.requireNonNull(initiatedAt, "initiatedAt must not be null");
    }

    @Override
    public String label() {
      return "SHUTTING_DOWN";
    }
  }

  record Terminated(Instant terminatedAt, long totalTicksExecuted) implements EngineState {
    public Terminated {
      Objects.requireNonNull(terminatedAt, "terminatedAt must not be null");
    }

    @Override
    public String label() {
      return "TERMINATED";
    }
  }
}
