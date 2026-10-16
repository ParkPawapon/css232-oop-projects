package io.chronos.engine.domain.event;

/**
 * Outbound port for publishing domain events to internal or external subscribers without coupling
 * the domain layer to a concrete message bus.
 */
@FunctionalInterface
public interface DomainEventPublisher {

  /**
   * Publishes a non-null domain event.
   *
   * @param event domain event instance
   */
  void publish(GameEvent event);
}
