package io.chronos.engine.domain.ecs;

/**
 * Marker contract for all Entity-Component-System (ECS) data components.
 *
 * <p>Implementations MUST be immutable Java 21 {@code record} types containing pure data without
 * behavioral side effects or I/O references.
 */
public interface Component {}
