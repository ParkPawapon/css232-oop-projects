package io.chronos.engine.application.cqrs;

import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-safe CQRS Command Bus supporting both immediate dispatch and deterministic tick-aligned
 * queued execution.
 */
public final class CommandBus {

  private final Map<Class<? extends Command>, CommandHandler<? extends Command>> handlers;
  private final Queue<Command> pendingQueue;

  public CommandBus() {
    this.handlers = new ConcurrentHashMap<>();
    this.pendingQueue = new ConcurrentLinkedQueue<>();
  }

  /**
   * Registers a handler for a specific command type.
   *
   * @param commandType class token of the command
   * @param handler command handler implementation
   * @param <C> command type
   */
  public <C extends Command> void register(Class<C> commandType, CommandHandler<C> handler) {
    Objects.requireNonNull(commandType, "commandType must not be null");
    Objects.requireNonNull(handler, "handler must not be null");
    CommandHandler<?> previous = handlers.putIfAbsent(commandType, handler);
    if (previous != null) {
      throw new IllegalStateException(
          "Duplicate CommandHandler registered for " + commandType.getName());
    }
  }

  /**
   * Enqueues a command from any thread (e.g., Network Virtual Thread or UI Thread) to be drained
   * deterministically at the start of the next simulation tick.
   *
   * @param command non-null command
   */
  public void enqueue(Command command) {
    Objects.requireNonNull(command, "command must not be null");
    if (!handlers.containsKey(command.getClass())) {
      throw new IllegalArgumentException(
          "No CommandHandler registered for " + command.getClass().getName());
    }
    pendingQueue.add(command);
  }

  /**
   * Immediately dispatches a command on the calling thread.
   *
   * @param command non-null command
   * @param <C> command type
   */
  @SuppressWarnings("unchecked")
  public <C extends Command> void dispatchImmediate(C command) {
    Objects.requireNonNull(command, "command must not be null");
    CommandHandler<C> handler = (CommandHandler<C>) handlers.get(command.getClass());
    if (handler == null) {
      throw new IllegalArgumentException(
          "No CommandHandler registered for " + command.getClass().getName());
    }
    handler.handle(command);
  }

  /**
   * Drains up to {@code maxCommands} queued commands on the deterministic simulation thread.
   *
   * @param maxCommands upper bound on commands processed in a single tick
   * @return number of commands executed
   */
  public int drainQueuedCommands(int maxCommands) {
    if (maxCommands <= 0) {
      throw new IllegalArgumentException("maxCommands must be > 0: " + maxCommands);
    }
    int processed = 0;
    Command next;
    while (processed < maxCommands && (next = pendingQueue.poll()) != null) {
      dispatchImmediate(next);
      processed++;
    }
    return processed;
  }
}
