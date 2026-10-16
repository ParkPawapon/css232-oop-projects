package io.chronos.engine.adapter.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

final class VirtualThreadNetworkGatewayTest {

  @Test
  void broadcastsStatePacketOnVirtualThread() {
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      VirtualThreadNetworkGateway gateway = new VirtualThreadNetworkGateway(executor);
      byte[] payload = "STATE_SYNC".getBytes(StandardCharsets.UTF_8);

      int bytesSent = gateway.broadcastStatePacketAsync(42L, payload).join();

      assertThat(bytesSent).isEqualTo(Long.BYTES + Integer.BYTES + payload.length);
      assertThat(gateway.totalBytesBroadcast()).isEqualTo(bytesSent);
    }
  }
}
