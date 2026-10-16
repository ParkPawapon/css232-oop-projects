package io.chronos.engine.application.cqrs;

/**
 * Handler responsible for executing a single concrete {@link Command} type.
 *
 * @param <C> concrete command type
 */
@FunctionalInterface
public interface CommandHandler<C extends Command> {

  void handle(C command);
}
