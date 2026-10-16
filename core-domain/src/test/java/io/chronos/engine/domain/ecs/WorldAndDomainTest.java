package io.chronos.engine.domain.ecs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.chronos.engine.domain.event.GameEvent;
import io.chronos.engine.domain.state.EngineState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class WorldAndDomainTest {

  record PositionComponent(double x, double y) implements Component {}

  record VelocityComponent(double vx, double vy) implements Component {}

  record HealthComponent(int hp) implements Component {}

  @Test
  void entityIdValidationAndComparisonWorks() {
    EntityId id1 = EntityId.of(0, 1);
    EntityId id2 = EntityId.of(5, 2);

    assertThat(id1.index()).isZero();
    assertThat(id1.generation()).isEqualTo(1);
    assertThat(id1.compareTo(id2)).isLessThan(0);
    assertThat(id1.toString()).isEqualTo("EntityId[idx=0,gen=1]");

    assertThatThrownBy(() -> EntityId.of(-1, 1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> EntityId.of(0, 0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new EntityId(0L)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new EntityId((1L << 32) | 0xFFFF_FFFFL))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void worldManagesEntitiesComponentsSlotRecyclingAndSystems() {
    List<GameEvent> events = new ArrayList<>();
    World world = new World(2, events::add);

    assertThatThrownBy(() -> new World(0, events::add))
        .isInstanceOf(IllegalArgumentException.class);

    EntityId first = world.createEntity(1L);
    EntityId second = world.createEntity(1L);
    assertThat(world.activeEntityCount()).isEqualTo(2);
    assertThatThrownBy(() -> world.createEntity(1L)).isInstanceOf(IllegalStateException.class);

    world.addComponent(first, new PositionComponent(10.0, 20.0));
    world.addComponent(first, new VelocityComponent(1.0, 2.0));
    world.addComponent(second, new PositionComponent(5.0, 5.0));

    assertThat(world.requireComponent(first, PositionComponent.class))
        .isEqualTo(new PositionComponent(10.0, 20.0));
    assertThat(world.getComponent(second, VelocityComponent.class)).isEmpty();
    assertThat(world.getComponent(second, HealthComponent.class)).isEmpty();
    assertThatThrownBy(() -> world.requireComponent(second, VelocityComponent.class))
        .isInstanceOf(IllegalStateException.class);

    List<EntityId> movingEntities = new ArrayList<>();
    world.forEachMatching(movingEntities::add, PositionComponent.class, VelocityComponent.class);
    assertThat(movingEntities).containsExactly(first);

    List<EntityId> unindexedMatch = new ArrayList<>();
    world.forEachMatching(unindexedMatch::add, HealthComponent.class);
    assertThat(unindexedMatch).isEmpty();

    assertThat(world.removeComponent(first, VelocityComponent.class)).isTrue();
    assertThat(world.removeComponent(first, VelocityComponent.class)).isFalse();
    assertThat(world.removeComponent(first, HealthComponent.class)).isFalse();

    assertThat(world.destroyEntity(first, 2L)).isTrue();
    assertThat(world.destroyEntity(first, 2L)).isFalse();
    assertThat(world.isAlive(first)).isFalse();
    assertThat(world.getComponent(first, PositionComponent.class)).isEmpty();
    assertThatThrownBy(() -> world.addComponent(first, new PositionComponent(0, 0)))
        .isInstanceOf(IllegalArgumentException.class);

    EntityId recycled = world.createEntity(3L);
    assertThat(recycled.index()).isEqualTo(first.index());
    assertThat(recycled.generation()).isEqualTo(first.generation() + 1);
    assertThat(world.isAlive(EntityId.of(99, 1))).isFalse();

    List<String> order = new ArrayList<>();
    GameSystem lateSystem =
        new GameSystem() {
          @Override
          public int priority() {
            return 20;
          }

          @Override
          public void update(World w, long tick, double dt) {
            order.add("late-" + tick);
          }
        };
    GameSystem defaultSystem = (w, tick, dt) -> order.add("default-" + tick);

    world.registerSystem(lateSystem);
    world.registerSystem(defaultSystem);
    world.stepSystems(10L, 1.0 / 60.0);
    world.stepSystems(11L, 1.0 / 60.0);
    world.shutdownSystems();

    assertThat(order).containsExactly("default-10", "late-10", "default-11", "late-11");
    assertThat(events).hasSize(4);
  }

  @Test
  void engineStateMachineAndEventsValidateTransitions() {
    Instant now = Instant.now();
    EngineState uninit = new EngineState.Uninitialized();
    EngineState init = new EngineState.Initialized(now);
    EngineState running = new EngineState.Running(now, 0L);
    EngineState paused = new EngineState.Paused(now, 5L, "user-pause");
    EngineState shuttingDown = new EngineState.ShuttingDown(now, 10L);
    EngineState terminated = new EngineState.Terminated(now, 10L);

    assertThat(uninit.label()).isEqualTo("UNINITIALIZED");
    assertThat(init.label()).isEqualTo("INITIALIZED");
    assertThat(running.label()).isEqualTo("RUNNING");
    assertThat(paused.label()).isEqualTo("PAUSED");
    assertThat(shuttingDown.label()).isEqualTo("SHUTTING_DOWN");
    assertThat(terminated.label()).isEqualTo("TERMINATED");

    assertThat(uninit.canTransitionTo(init)).isTrue();
    assertThat(uninit.canTransitionTo(shuttingDown)).isTrue();
    assertThat(uninit.canTransitionTo(running)).isFalse();

    assertThat(init.canTransitionTo(running)).isTrue();
    assertThat(init.canTransitionTo(shuttingDown)).isTrue();
    assertThat(init.canTransitionTo(paused)).isFalse();

    assertThat(running.canTransitionTo(paused)).isTrue();
    assertThat(running.canTransitionTo(shuttingDown)).isTrue();
    assertThat(running.canTransitionTo(init)).isFalse();

    assertThat(paused.canTransitionTo(running)).isTrue();
    assertThat(paused.canTransitionTo(shuttingDown)).isTrue();
    assertThat(paused.canTransitionTo(init)).isFalse();

    assertThat(shuttingDown.canTransitionTo(terminated)).isTrue();
    assertThat(shuttingDown.canTransitionTo(running)).isFalse();
    assertThat(terminated.canTransitionTo(uninit)).isFalse();

    GameEvent.CustomDomainEvent custom =
        new GameEvent.CustomDomainEvent(UUID.randomUUID(), 1L, now, "TEST", "payload");
    assertThat(custom.eventType()).isEqualTo("TEST");
    assertThat(custom.tick()).isEqualTo(1L);
    assertThat(custom.occurredAt()).isEqualTo(now);
    assertThat(custom.eventId()).isNotNull();

    GameEvent.EngineStateTransitioned transition =
        new GameEvent.EngineStateTransitioned(UUID.randomUUID(), 1L, now, uninit, init);
    assertThat(transition.previousState()).isEqualTo(uninit);
    assertThat(transition.currentState()).isEqualTo(init);
  }
}
