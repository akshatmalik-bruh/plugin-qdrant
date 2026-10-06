package io.kestra.plugin.qdrant;

import io.qdrant.client.grpc.Collections;

/**
 * Storage memory policy for point payload metadata in Qdrant (1.19+).
 */
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    title = "Payload memory policy",
    description = "Storage memory tier for metadata payload."
)
public enum PayloadMemory {
    @Schema(title = "Stored on disk (mmap)")
    COLD,

    @Schema(title = "Stored on disk with in-memory page cache")
    CACHED,

    @Schema(title = "Pinned in RAM")
    PINNED;
        };
    }
}
