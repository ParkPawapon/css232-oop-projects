package io.chronos.engine.adapter.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.chronos.engine.domain.ecs.World;
import io.chronos.engine.domain.port.SnapshotStorePort;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persistence adapter implementing {@link SnapshotStorePort} using Java 21 Virtual Threads and
 * Jackson JSON serialization.
 */
public final class VirtualThreadSnapshotAdapter implements SnapshotStorePort {

  private static final Logger LOG = LoggerFactory.getLogger(VirtualThreadSnapshotAdapter.class);

  public record SnapshotEnvelope(long tick, int activeEntities, String savedAtIso) {}

  private final Path storageDirectory;
  private final ExecutorService virtualThreadExecutor;
  private final ObjectMapper objectMapper;

  public VirtualThreadSnapshotAdapter(
      Path storageDirectory, ExecutorService virtualThreadExecutor) {
    this.storageDirectory =
        Objects.requireNonNull(storageDirectory, "storageDirectory must not be null");
    this.virtualThreadExecutor =
        Objects.requireNonNull(virtualThreadExecutor, "virtualThreadExecutor must not be null");
    this.objectMapper = new ObjectMapper();
  }

  @Override
  public CompletableFuture<Void> saveSnapshotAsync(long tick, World world) {
    Objects.requireNonNull(world, "world must not be null");
    int activeEntities = world.activeEntityCount();
    SnapshotEnvelope envelope =
        new SnapshotEnvelope(tick, activeEntities, Instant.now().toString());

    return CompletableFuture.runAsync(() -> writeSnapshotToDisk(envelope), virtualThreadExecutor);
  }

  private void writeSnapshotToDisk(SnapshotEnvelope envelope) {
    try {
      Files.createDirectories(storageDirectory);
      Path snapshotFile = storageDirectory.resolve("snapshot-tick-" + envelope.tick() + ".json");
      byte[] bytes = objectMapper.writeValueAsBytes(envelope);
      Files.write(snapshotFile, bytes);
      LOG.info(
          "Saved snapshot at tick={} to {} on virtual={}",
          envelope.tick(),
          snapshotFile,
          Thread.currentThread().isVirtual());
    } catch (IOException e) {
      throw new IllegalStateException(
          "Failed to write world snapshot for tick " + envelope.tick(), e);
    }
  }
}
