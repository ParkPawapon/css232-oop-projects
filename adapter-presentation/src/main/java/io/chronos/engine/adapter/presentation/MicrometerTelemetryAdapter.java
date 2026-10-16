package io.chronos.engine.adapter.presentation;

import io.chronos.engine.domain.port.TelemetryPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Micrometer-backed telemetry adapter implementing {@link TelemetryPort} for tick-rate,
 * interpolation, and spiral-of-death observability.
 */
public final class MicrometerTelemetryAdapter implements TelemetryPort {

  private final Timer tickTimer;
  private final DistributionSummary alphaSummary;
  private final Counter spiralPanicCounter;

  public MicrometerTelemetryAdapter(MeterRegistry meterRegistry) {
    Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
    this.tickTimer =
        Timer.builder("chronos.engine.tick.duration")
            .description("Duration of deterministic simulation ticks")
            .register(meterRegistry);
    this.alphaSummary =
        DistributionSummary.builder("chronos.engine.render.alpha")
            .description("Render interpolation factor alpha")
            .register(meterRegistry);
    this.spiralPanicCounter =
        Counter.builder("chronos.engine.spiral.panic")
            .description("Count of spiral-of-death accumulator clamping events")
            .register(meterRegistry);
  }

  @Override
  public void recordTickDurationNanos(long tick, long durationNanos) {
    if (durationNanos >= 0L) {
      tickTimer.record(durationNanos, TimeUnit.NANOSECONDS);
    }
  }

  @Override
  public void recordFrameInterpolation(double alpha) {
    alphaSummary.record(alpha);
  }

  @Override
  public void recordSpiralOfDeathPanic(long tick, double droppedSeconds) {
    spiralPanicCounter.increment();
  }

  public long recordedTicks() {
    return tickTimer.count();
  }

  public double panicCount() {
    return spiralPanicCounter.count();
  }
}
