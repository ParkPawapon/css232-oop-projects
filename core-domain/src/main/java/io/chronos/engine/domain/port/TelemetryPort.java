package io.chronos.engine.domain.port;

/** Outbound port for recording engine performance metrics and spiral-of-death panic warnings. */
public interface TelemetryPort {

  void recordTickDurationNanos(long tick, long durationNanos);

  void recordFrameInterpolation(double alpha);

  void recordSpiralOfDeathPanic(long tick, double droppedSeconds);
}
