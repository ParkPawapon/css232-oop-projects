package io.chronos.engine.domain.port;

import io.chronos.engine.domain.ecs.World;
import java.util.concurrent.CompletableFuture;

/** Outbound port for non-blocking persistence of world state snapshots. */
public interface SnapshotStorePort {

  CompletableFuture<Void> saveSnapshotAsync(long tick, World world);
}
