package io.chronos.engine.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.chronos.engine.domain.ecs.World;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class VirtualThreadSnapshotAdapterTest {

  @Test
  void savesSnapshotAsynchronouslyOnVirtualThread(@TempDir Path tempDir) {
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      VirtualThreadSnapshotAdapter adapter = new VirtualThreadSnapshotAdapter(tempDir, executor);
      World world = new World(8, event -> {});
      world.createEntity(10L);

      adapter.saveSnapshotAsync(10L, world).join();

      Path expectedFile = tempDir.resolve("snapshot-tick-10.json");
      assertThat(Files.exists(expectedFile)).isTrue();
    }
  }
}
