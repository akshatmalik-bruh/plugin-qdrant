package io.kestra.plugin.qdrant;

import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Common;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
public class QdrantConnectionTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void testToPointIdValid() {
        Common.PointId numId = QdrantConnection.toPointId(12345);
        assertThat(numId.getNum(), is(12345L));

        String uuidStr = UUID.randomUUID().toString();
        Common.PointId uuidId = QdrantConnection.toPointId(uuidStr);
        assertThat(uuidId.getUuid(), is(uuidStr));
    }

    @Test
    void testToPointIdInvalidFailsFast() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> QdrantConnection.toPointId("doc_123")
        );
        assertThat(ex.getMessage(), containsString("Invalid point ID 'doc_123'"));
    }

    @Test
    void testToFilterRangeUnderMust() {
        Map<String, Object> filterMap = Map.of(
            "must", List.of(
                Map.of(
                    "key", "price",
                    "range", Map.of("gte", 100)
                )
            )
        );

        Common.Filter filter = QdrantConnection.toFilter(filterMap);
        assertThat(filter, notNullValue());
        assertThat(filter.getMustCount(), is(1));

        Common.Condition cond = filter.getMust(0);
        assertThat(cond.hasField(), is(true));
        assertThat(cond.getField().getKey(), is("price"));
        assertThat(cond.getField().hasRange(), is(true));
        assertThat(cond.getField().getRange().getGte(), is(100.0));
    }

    @Test
    void testToFilterTopLevelCombinedWithMust() {
        Map<String, Object> filterMap = Map.of(
            "must", List.of(
                Map.of(
                    "key", "price",
                    "range", Map.of("gte", 100)
                )
            ),
            "city", "Berlin"
        );

        Common.Filter filter = QdrantConnection.toFilter(filterMap);
        assertThat(filter, notNullValue());
        // Both the condition under must and the top-level city condition should be present
        assertThat(filter.getMustCount(), is(2));
    }

    @Test
    void testPayloadMemoryToGrpc() {
        assertThat(PayloadMemory.COLD.toGrpc(), is(Collections.Memory.Cold));
        assertThat(PayloadMemory.CACHED.toGrpc(), is(Collections.Memory.Cached));
        assertThat(PayloadMemory.PINNED.toGrpc(), is(Collections.Memory.Pinned));
    }

    @Test
    void testStreamingStoreFetchOutput() throws IOException {
        RunContext runContext = runContextFactory.of();
        List<String> items = List.of("alpha", "beta");

        var output = QdrantConnection.buildFetchOutput(
            runContext,
            FetchType.STORE,
            items,
            item -> Map.of("name", item)
        );

        assertThat(output, notNullValue());
        assertThat(output.getSize(), is(2L));
        assertThat(output.getUri(), notNullValue());
    }
}
