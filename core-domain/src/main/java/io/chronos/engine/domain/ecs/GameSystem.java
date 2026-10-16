package io.chronos.engine.domain.ecs;

/**
 * Contract for deterministic ECS processors that transform entity components during a fixed
 * simulation tick.
 */
public interface GameSystem {

  /**
   * Execution priority. Lower values execute earlier within the same tick.
   *
   * @return integer priority
   */
  default int priority() {
    return 0;
  }

  /**
   * Invoked once when the system is registered with the simulation loop.
   *
   * @param world active ECS world
   */
  default void onAttach(World world) {}

  /**
   * Executes one deterministic simulation step.
   *
   * @param world active ECS world
   * @param tick current monotonically increasing tick number
   * @param fixedDeltaSeconds fixed simulation step duration in seconds (e.g., 1/60s)
   */
  void update(World world, long tick, double fixedDeltaSeconds);

  /**
   * Invoked during graceful shutdown.
   *
   * @param world active ECS world
   */
  default void onDetach(World world) {}
}
