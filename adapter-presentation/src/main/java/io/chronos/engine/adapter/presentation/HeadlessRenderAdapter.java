package io.chronos.engine.adapter.presentation;

import io.chronos.engine.domain.ecs.World;
import io.chronos.engine.domain.port.RenderOutputPort;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Headless presentation adapter implementing {@link RenderOutputPort} for dedicated servers and CI
 * verification pipelines.
 */
public final class HeadlessRenderAdapter implements RenderOutputPort {

  private static final Logger LOG = LoggerFactory.getLogger(HeadlessRenderAdapter.class);

  private final AtomicLong renderedFrames;
  private final DoubleAdder cumulativeAlpha;

  public HeadlessRenderAdapter() {
    this.renderedFrames = new AtomicLong(0L);
    this.cumulativeAlpha = new DoubleAdder();
  }

  @Override
  public void renderFrame(World world, double alpha) {
    Objects.requireNonNull(world, "world must not be null");
    if (alpha < 0.0 || alpha > 1.0) {
      throw new IllegalArgumentException("Interpolation alpha must be within [0.0, 1.0]: " + alpha);
    }
    long frameNumber = renderedFrames.incrementAndGet();
    cumulativeAlpha.add(alpha);
    LOG.debug(
        "Headless frame #{} rendered [entities={}, alpha={}]",
        frameNumber,
        world.activeEntityCount(),
        alpha);
  }

  public long renderedFrameCount() {
    return renderedFrames.get();
  }

  public double cumulativeAlpha() {
    return cumulativeAlpha.sum();
  }
}
