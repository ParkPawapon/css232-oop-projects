package io.chronos.engine.domain.event;

import io.chronos.engine.domain.ecs.EntityId;
import io.chronos.engine.domain.state.EngineState;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Sealed hierarchy of deterministic and lifecycle domain events emitted by the engine. */
public sealed interface GameEvent
    permits GameEvent.EntitySpawned,
        GameEvent.EntityDestroyed,
        GameEvent.EngineStateTransitioned,
        GameEvent.CustomDomainEvent {

  UUID eventId();

  long tick();

  Instant occurredAt();

  record EntitySpawned(UUID eventId, long tick, Instant occurredAt, EntityId entityId)
      implements GameEvent {
    public EntitySpawned {
      Objects.requireNonNull(eventId, "eventId must not be null");
      Objects.requireNonNull(occurredAt, "occurredAt must not be null");
      Objects.requireNonNull(entityId, "entityId must not be null");
    }
  }

  record EntityDestroyed(UUID eventId, long tick, Instant occurredAt, EntityId entityId)
      implements GameEvent {
    public EntityDestroyed {
      Objects.requireNonNull(eventId, "eventId must not be null");
      Objects.requireNonNull(occurredAt, "occurredAt must not be null");
      Objects.requireNonNull(entityId, "entityId must not be null");
    }
  }

  record EngineStateTransitioned(
      UUID eventId,
      long tick,
      Instant occurredAt,
      EngineState previousState,
      EngineState currentState)
      implements GameEvent {
    public EngineStateTransitioned {
      Objects.requireNonNull(eventId, "eventId must not be null");
      Objects.requireNonNull(occurredAt, "occurredAt must not be null");
      Objects.requireNonNull(previousState, "previousState must not be null");
      Objects.requireNonNull(currentState, "currentState must not be null");
    }
  }

  record CustomDomainEvent(
      UUID eventId, long tick, Instant occurredAt, String eventType, Object payload)
      implements GameEvent {
    public CustomDomainEvent {
      Objects.requireNonNull(eventId, "eventId must not be null");
      Objects.requireNonNull(occurredAt, "occurredAt must not be null");
      Objects.requireNonNull(eventType, "eventType must not be null");
      Objects.requireNonNull(payload, "payload must not be null");
    }
  }
}
