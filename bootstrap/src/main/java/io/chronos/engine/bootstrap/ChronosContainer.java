package io.chronos.engine.bootstrap;

import io.chronos.engine.adapter.network.VirtualThreadNetworkGateway;
import io.chronos.engine.adapter.persistence.VirtualThreadSnapshotAdapter;
import io.chronos.engine.adapter.presentation.HeadlessRenderAdapter;
import io.chronos.engine.adapter.presentation.MicrometerTelemetryAdapter;
import io.chronos.engine.application.cqrs.CommandBus;
import io.chronos.engine.application.event.LockFreeRingBufferEventBus;
import io.chronos.engine.application.loop.DeterministicGameLoop;
import io.chronos.engine.application.usecase.EngineLifecycleUseCase;
import io.chronos.engine.application.usecase.EngineLifecycleUseCase.ShutdownEngineCommand;
import io.chronos.engine.domain.ecs.World;
import io.chronos.engine.domain.port.NetworkTransportPort;
import io.chronos.engine.domain.port.SnapshotStorePort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pure Constructor-Injection Dependency Container wiring domain, application, and infrastructure
 * adapters together.
 */
public final class ChronosContainer implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(ChronosContainer.class);
  private static final int RING_BUFFER_CAPACITY = 1024;
  private static final int TARGET_UPS = 60;
  private static final int MAX_UPDATES_PER_FRAME = 5;

  private final ExecutorService virtualIoExecutor;
  private final LockFreeRingBufferEventBus eventBus;
  private final World world;
  private final CommandBus commandBus;
  private final EngineLifecycleUseCase lifecycleUseCase;
  private final HeadlessRenderAdapter renderAdapter;
  private final MicrometerTelemetryAdapter telemetryAdapter;
  private final SnapshotStorePort snapshotStorePort;
  private final NetworkTransportPort networkTransportPort;
  private final DeterministicGameLoop gameLoop;
  private final AtomicBoolean closed;

  public ChronosContainer(int maxEntities, Path snapshotDir) {
    Objects.requireNonNull(snapshotDir, "snapshotDir must not be null");
    this.virtualIoExecutor =
        Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("chronos-io-vt-", 0).factory());
    this.eventBus = new LockFreeRingBufferEventBus(RING_BUFFER_CAPACITY);
    this.world = new World(maxEntities, eventBus);
    this.commandBus = new CommandBus();
    this.lifecycleUseCase = new EngineLifecycleUseCase(eventBus);
    this.lifecycleUseCase.registerHandlers(this.commandBus);

    this.renderAdapter = new HeadlessRenderAdapter();
    this.telemetryAdapter = new MicrometerTelemetryAdapter(new SimpleMeterRegistry());
    this.snapshotStorePort = new VirtualThreadSnapshotAdapter(snapshotDir, virtualIoExecutor);
    this.networkTransportPort = new VirtualThreadNetworkGateway(virtualIoExecutor);

    this.gameLoop =
        new DeterministicGameLoop(
            world,
            commandBus,
            lifecycleUseCase,
            renderAdapter,
            telemetryAdapter,
            TARGET_UPS,
            MAX_UPDATES_PER_FRAME);
    this.closed = new AtomicBoolean(false);
  }

  public World world() {
    return world;
  }

  public CommandBus commandBus() {
    return commandBus;
  }

  public EngineLifecycleUseCase lifecycleUseCase() {
    return lifecycleUseCase;
  }

  public LockFreeRingBufferEventBus eventBus() {
    return eventBus;
  }

  public HeadlessRenderAdapter renderAdapter() {
    return renderAdapter;
  }

  public MicrometerTelemetryAdapter telemetryAdapter() {
    return telemetryAdapter;
  }

  public SnapshotStorePort snapshotStorePort() {
    return snapshotStorePort;
  }

  public NetworkTransportPort networkTransportPort() {
    return networkTransportPort;
  }

  public DeterministicGameLoop gameLoop() {
    return gameLoop;
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    LOG.info("Initiating graceful shutdown of ChronosContainer...");
    try {
      commandBus.dispatchImmediate(new ShutdownEngineCommand());
      world.shutdownSystems();
      snapshotStorePort.saveSnapshotAsync(lifecycleUseCase.currentTick(), world).join();
      eventBus.drainToSubscribers();
      lifecycleUseCase.markTerminated();
    } finally {
      virtualIoExecutor.close();
      LOG.info(
          "ChronosContainer terminated cleanly at state={}",
          lifecycleUseCase.currentState().label());
    }
  }
}
