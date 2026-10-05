package io.kestra.plugin.qdrant;

import io.qdrant.client.grpc.Collections;

/**
 * Storage memory policy for point payload metadata in Qdrant (1.19+).
 */
public enum PayloadMemory {
    /**
     * Stored on disk (mmap), read on demand. Recommended for large payloads.
     */
    COLD,

    /**
     * Stored on disk with an in-memory page cache for hot reads (modern default).
     */
    CACHED,

    /**
     * Pinned in RAM for lowest latency access.
     */
    PINNED;

    public Collections.Memory toGrpc() {
        return switch (this) {
            case COLD -> Collections.Memory.Cold;
            case CACHED -> Collections.Memory.Cached;
            case PINNED -> Collections.Memory.Pinned;
        };
    }
}
