package io.chronos.engine.adapter.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.chronos.engine.domain.ecs.World;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

final class PresentationAdaptersTest {

  @Test
  void headlessRenderAndMicrometerTelemetryAdaptersRecordMetrics() {
    HeadlessRenderAdapter renderAdapter = new HeadlessRenderAdapter();
    World world = new World(4, event -> {});

    renderAdapter.renderFrame(world, 0.5);
    assertThat(renderAdapter.renderedFrameCount()).isEqualTo(1L);
    assertThat(renderAdapter.cumulativeAlpha()).isEqualTo(0.5);

    assertThatThrownBy(() -> renderAdapter.renderFrame(world, -0.1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> renderAdapter.renderFrame(world, 1.1))
        .isInstanceOf(IllegalArgumentException.class);

    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    MicrometerTelemetryAdapter telemetryAdapter = new MicrometerTelemetryAdapter(registry);
    telemetryAdapter.recordTickDurationNanos(1L, 1_500_000L);
    telemetryAdapter.recordTickDurationNanos(2L, -1L);
    telemetryAdapter.recordFrameInterpolation(0.25);
    telemetryAdapter.recordSpiralOfDeathPanic(2L, 0.1);

    assertThat(telemetryAdapter.recordedTicks()).isEqualTo(1L);
    assertThat(telemetryAdapter.panicCount()).isEqualTo(1.0);
  }
}
