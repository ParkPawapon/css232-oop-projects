package io.chronos.engine.domain.ecs;

import io.chronos.engine.domain.event.DomainEventPublisher;
import io.chronos.engine.domain.event.GameEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * High-performance, cache-friendly Entity-Component-System (ECS) registry.
 *
 * <p>Avoids GC thrashing in hot simulation loops by storing component compositions as 64-bit
 * bitmasks per entity slot and reusing contiguous component storage arrays.
 */
public final class World {

  public static final int MAX_COMPONENT_TYPES = 64;

  private final int maxEntities;
  private final DomainEventPublisher eventPublisher;

  private final int[] generations;
  private final boolean[] alive;
  private final long[] componentMasks;

  private final int[] freeIndices;
  private int freeHead;
  private int freeTail;
  private int freeCount;
  private int nextHighWaterSlot;
  private int activeEntityCount;

  private final Map<Class<? extends Component>, Integer> componentTypeIndices;
  private final Component[][] componentColumns;
  private int registeredComponentTypes;

  private final List<GameSystem> systems;
  private boolean systemsDirty;

  public World(int maxEntities, DomainEventPublisher eventPublisher) {
    if (maxEntities <= 0) {
      throw new IllegalArgumentException("maxEntities must be strictly positive: " + maxEntities);
    }
    this.maxEntities = maxEntities;
    this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");

    this.generations = new int[maxEntities];
    Arrays.fill(this.generations, 1);
    this.alive = new boolean[maxEntities];
    this.componentMasks = new long[maxEntities];

    this.freeIndices = new int[maxEntities];
    this.freeHead = 0;
    this.freeTail = 0;
    this.freeCount = 0;
    this.nextHighWaterSlot = 0;
    this.activeEntityCount = 0;

    this.componentTypeIndices = new IdentityHashMap<>(MAX_COMPONENT_TYPES);
    this.componentColumns = new Component[MAX_COMPONENT_TYPES][maxEntities];
    this.registeredComponentTypes = 0;

    this.systems = new ArrayList<>();
    this.systemsDirty = false;
  }

  /**
   * Allocates a new or recycled entity in O(1) time without heap allocation for slot management.
   *
   * @param currentTick current simulation tick for event metadata
   * @return unique {@link EntityId}
   */
  public EntityId createEntity(long currentTick) {
    final int slot;
    if (freeCount > 0) {
      slot = freeIndices[freeHead];
      freeHead = (freeHead + 1) % maxEntities;
      freeCount--;
    } else {
      if (nextHighWaterSlot >= maxEntities) {
        throw new IllegalStateException("World entity capacity exhausted: " + maxEntities);
      }
      slot = nextHighWaterSlot++;
    }

    alive[slot] = true;
    componentMasks[slot] = 0L;
    activeEntityCount++;

    EntityId entityId = EntityId.of(slot, generations[slot]);
    eventPublisher.publish(
        new GameEvent.EntitySpawned(UUID.randomUUID(), currentTick, Instant.now(), entityId));
    return entityId;
  }

  /**
   * Destroys an entity, nulls out all attached component references to prevent memory leaks, and
   * increments the slot generation so stale handles become invalid immediately.
   *
   * @param entityId target entity handle
   * @param currentTick current simulation tick
   * @return {@code true} if the entity was alive and destroyed; {@code false} otherwise
   */
  public boolean destroyEntity(EntityId entityId, long currentTick) {
    Objects.requireNonNull(entityId, "entityId must not be null");
    if (!isAlive(entityId)) {
      return false;
    }

    int slot = entityId.index();
    long mask = componentMasks[slot];

    while (mask != 0L) {
      int typeBit = Long.numberOfTrailingZeros(mask);
      componentColumns[typeBit][slot] = null;
      mask &= (mask - 1L);
    }

    componentMasks[slot] = 0L;
    alive[slot] = false;
    generations[slot] = generations[slot] == Integer.MAX_VALUE ? 1 : generations[slot] + 1;

    freeIndices[freeTail] = slot;
    freeTail = (freeTail + 1) % maxEntities;
    freeCount++;
    activeEntityCount--;

    eventPublisher.publish(
        new GameEvent.EntityDestroyed(UUID.randomUUID(), currentTick, Instant.now(), entityId));
    return true;
  }

  /**
   * Checks if the entity handle refers to a currently live entity of the matching generation.
   *
   * @param entityId entity handle
   * @return {@code true} if valid and alive
   */
  public boolean isAlive(EntityId entityId) {
    Objects.requireNonNull(entityId, "entityId must not be null");
    int slot = entityId.index();
    return slot >= 0
        && slot < nextHighWaterSlot
        && alive[slot]
        && generations[slot] == entityId.generation();
  }

  /**
   * Attaches or replaces a component on a live entity.
   *
   * @param entityId target entity
   * @param component non-null component record
   * @param <T> concrete component type
   */
  public <T extends Component> void addComponent(EntityId entityId, T component) {
    Objects.requireNonNull(entityId, "entityId must not be null");
    Objects.requireNonNull(component, "component must not be null");
    ensureAlive(entityId);

    int typeIndex = resolveOrRegisterComponentType(component.getClass());
    int slot = entityId.index();
    componentColumns[typeIndex][slot] = component;
    componentMasks[slot] |= (1L << typeIndex);
  }

  /**
   * Removes a component type from a live entity and clears the underlying array reference.
   *
   * @param entityId target entity
   * @param componentClass component class to remove
   * @param <T> component type
   * @return {@code true} if the component was present and removed
   */
  public <T extends Component> boolean removeComponent(EntityId entityId, Class<T> componentClass) {
    Objects.requireNonNull(entityId, "entityId must not be null");
    Objects.requireNonNull(componentClass, "componentClass must not be null");
    ensureAlive(entityId);

    Integer typeIndex = componentTypeIndices.get(componentClass);
    if (typeIndex == null) {
      return false;
    }

    int slot = entityId.index();
    long bit = 1L << typeIndex;
    if ((componentMasks[slot] & bit) == 0L) {
      return false;
    }

    componentColumns[typeIndex][slot] = null;
    componentMasks[slot] &= ~bit;
    return true;
  }

  /**
   * Retrieves an optional component instance for an entity.
   *
   * @param entityId target entity
   * @param componentClass requested component class
   * @param <T> component type
   * @return {@link Optional} containing the component if present
   */
  public <T extends Component> Optional<T> getComponent(
      EntityId entityId, Class<T> componentClass) {
    Objects.requireNonNull(entityId, "entityId must not be null");
    Objects.requireNonNull(componentClass, "componentClass must not be null");
    if (!isAlive(entityId)) {
      return Optional.empty();
    }

    Integer typeIndex = componentTypeIndices.get(componentClass);
    if (typeIndex == null) {
      return Optional.empty();
    }

    Component raw = componentColumns[typeIndex][entityId.index()];
    return Optional.ofNullable(componentClass.cast(raw));
  }

  /**
   * Fast, direct component lookup for hot loops where liveness and presence are guaranteed by
   * {@link #forEachMatching(Consumer, Class[])}.
   *
   * @param entityId target entity
   * @param componentClass requested component class
   * @param <T> component type
   * @return component instance
   */
  public <T extends Component> T requireComponent(EntityId entityId, Class<T> componentClass) {
    return getComponent(entityId, componentClass)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Missing required component "
                        + componentClass.getSimpleName()
                        + " on "
                        + entityId));
  }

  /**
   * Zero-allocation bitmask query over all entities possessing ALL specified component types.
   *
   * @param consumer callback invoked for each matching {@link EntityId}
   * @param requiredTypes component classes required on matching entities
   */
  @SafeVarargs
  public final void forEachMatching(
      Consumer<EntityId> consumer, Class<? extends Component>... requiredTypes) {
    Objects.requireNonNull(consumer, "consumer must not be null");
    Objects.requireNonNull(requiredTypes, "requiredTypes must not be null");

    long requiredMask = 0L;
    for (Class<? extends Component> type : requiredTypes) {
      Integer typeIndex = componentTypeIndices.get(Objects.requireNonNull(type));
      if (typeIndex == null) {
        return;
      }
      requiredMask |= (1L << typeIndex);
    }

    for (int slot = 0; slot < nextHighWaterSlot; slot++) {
      if (alive[slot] && (componentMasks[slot] & requiredMask) == requiredMask) {
        consumer.accept(EntityId.of(slot, generations[slot]));
      }
    }
  }

  /**
   * Registers a deterministic {@link GameSystem} and invokes its {@link GameSystem#onAttach(World)}
   * hook.
   *
   * @param system system to register
   */
  public void registerSystem(GameSystem system) {
    Objects.requireNonNull(system, "system must not be null");
    systems.add(system);
    systemsDirty = true;
    system.onAttach(this);
  }

  /**
   * Executes all registered systems in priority order for a single fixed simulation step.
   *
   * @param tick current tick number
   * @param fixedDeltaSeconds fixed delta time in seconds
   */
  public void stepSystems(long tick, double fixedDeltaSeconds) {
    if (systemsDirty) {
      systems.sort(Comparator.comparingInt(GameSystem::priority));
      systemsDirty = false;
    }
    for (int i = 0, size = systems.size(); i < size; i++) {
      systems.get(i).update(this, tick, fixedDeltaSeconds);
    }
  }

  /** Detaches all systems in reverse order during engine shutdown. */
  public void shutdownSystems() {
    for (int i = systems.size() - 1; i >= 0; i--) {
      systems.get(i).onDetach(this);
    }
  }

  public int activeEntityCount() {
    return activeEntityCount;
  }

  private void ensureAlive(EntityId entityId) {
    if (!isAlive(entityId)) {
      throw new IllegalArgumentException("Entity is not alive or handle is stale: " + entityId);
    }
  }

  private int resolveOrRegisterComponentType(Class<? extends Component> componentClass) {
    Integer existing = componentTypeIndices.get(componentClass);
    if (existing != null) {
      return existing;
    }
    if (registeredComponentTypes >= MAX_COMPONENT_TYPES) {
      throw new IllegalStateException(
          "Exceeded maximum distinct component types (" + MAX_COMPONENT_TYPES + ")");
    }
    int assigned = registeredComponentTypes++;
    componentTypeIndices.put(componentClass, assigned);
    return assigned;
  }
}
