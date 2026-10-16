package io.chronos.engine.domain.port;

import io.chronos.engine.domain.ecs.World;

/**
 * Outbound port for presentation/rendering adapters (e.g., Vulkan, LWJGL3, LibGDX, or Headless).
 */
@FunctionalInterface
public interface RenderOutputPort {

  /**
   * Renders or projects the current world state using the interpolation factor {@code alpha}.
   *
   * @param world read-only view of the ECS world
   * @param alpha interpolation factor in {@code [0.0, 1.0)} between previous and current tick
   */
  void renderFrame(World world, double alpha);
}
