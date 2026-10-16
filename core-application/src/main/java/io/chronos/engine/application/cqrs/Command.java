package io.chronos.engine.application.cqrs;

/**
 * Marker interface for all application-layer CQRS commands.
 *
 * <p>Concrete commands should be implemented as immutable Java 21 {@code record} types.
 */
public interface Command {}
