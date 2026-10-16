package io.chronos.engine.application.event;

import io.chronos.engine.domain.event.DomainEventPublisher;
import io.chronos.engine.domain.event.GameEvent;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Consumer;

/**
 * Disruptor-style lock-free power-of-two ring buffer implementing {@link DomainEventPublisher}.
 *
 * <p>Pre-allocates a fixed ring buffer of slots masked via {@code index & (capacity - 1)} for O(1)
 * lock-free event publication and consumption without heap allocation in hot paths.
 */
public final class LockFreeRingBufferEventBus implements DomainEventPublisher {

  private final int capacity;
  private final int mask;
  private final AtomicReferenceArray<GameEvent> ring;
  private final AtomicLong writeSequence;
  private final AtomicLong readSequence;
  private final List<Consumer<GameEvent>> subscribers;

  public LockFreeRingBufferEventBus(int capacityPowerOfTwo) {
    if (capacityPowerOfTwo <= 0 || Integer.bitCount(capacityPowerOfTwo) != 1) {
      throw new IllegalArgumentException(
          "capacityPowerOfTwo must be a positive power of 2: " + capacityPowerOfTwo);
    }
    this.capacity = capacityPowerOfTwo;
    this.mask = capacityPowerOfTwo - 1;
    this.ring = new AtomicReferenceArray<>(capacityPowerOfTwo);
    this.writeSequence = new AtomicLong(0L);
    this.readSequence = new AtomicLong(0L);
    this.subscribers = new CopyOnWriteArrayList<>();
  }

  public void subscribe(Consumer<GameEvent> subscriber) {
    subscribers.add(Objects.requireNonNull(subscriber, "subscriber must not be null"));
  }

  @Override
  public void publish(GameEvent event) {
    Objects.requireNonNull(event, "event must not be null");
    long seq = writeSequence.getAndIncrement();
    int slot = (int) (seq & mask);
    ring.set(slot, event);
  }

  /**
   * Drains available events from the ring buffer and notifies all registered subscribers.
   *
   * @return number of events dispatched
   */
  public int drainToSubscribers() {
    int dispatched = 0;
    long currentWrite = writeSequence.get();
    long currentRead = readSequence.get();

    if (currentWrite - currentRead > capacity) {
      currentRead = currentWrite - capacity;
      readSequence.set(currentRead);
    }

    while (currentRead < currentWrite) {
      int slot = (int) (currentRead & mask);
      GameEvent event = ring.getAndSet(slot, null);
      if (event != null) {
        for (int i = 0, size = subscribers.size(); i < size; i++) {
          subscribers.get(i).accept(event);
        }
        dispatched++;
      }
      currentRead = readSequence.incrementAndGet();
    }
    return dispatched;
  }

  public int capacity() {
    return capacity;
  }
}
