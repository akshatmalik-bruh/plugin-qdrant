package io.kestra.plugin.qdrant;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.qdrant.client.PointIdFactory;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.VectorsFactory;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Points;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class QueryTest extends QdrantTest {

    @BeforeEach
    void setUpCollection() throws Exception {
        if (!isQdrantAvailable()) return;
        var vectorParams = Collections.VectorParams.newBuilder()
            .setSize(DIMENSION)
            .setDistance(Collections.Distance.Cosine)
            .build();
        try (var client = qdrantClient()) {
            client.createCollectionAsync(collectionName, vectorParams).get();

            var p1 = Points.PointStruct.newBuilder()
                .setId(PointIdFactory.id(1L))
                .setVectors(VectorsFactory.vectors(List.of(0.05f, 0.61f, 0.76f, 0.74f)))
                .putPayload("city", ValueFactory.value("Berlin"))
                .build();
            var p2 = Points.PointStruct.newBuilder()
                .setId(PointIdFactory.id(2L))
                .setVectors(VectorsFactory.vectors(List.of(0.19f, 0.81f, 0.75f, 0.11f)))
                .putPayload("city", ValueFactory.value("Paris"))
                .build();
            client.upsertAsync(collectionName, List.of(p1, p2)).get();
        } catch (Exception ignored) {
        }
    }

    @Test
    void runQueryVector() throws Exception {
        var runContext = runContextFactory.of();

        var task = Query.builder()
            .host(Property.ofValue(host))
            .port(Property.ofValue(port))
            .collectionName(Property.ofValue(collectionName))
            .vector(Property.ofValue(List.of(0.05f, 0.61f, 0.76f, 0.74f)))
            .topK(Property.ofValue(2))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build();

        var output = task.run(runContext);

        assertThat(output, notNullValue());
        assertThat(output.getSize(), is(2L));
        assertThat(output.getRows(), notNullValue());
        assertThat(output.getRows().size(), is(2));
    }

    @Test
    void runQueryById() throws Exception {
        var runContext = runContextFactory.of();

        var task = Query.builder()
            .host(Property.ofValue(host))
            .port(Property.ofValue(port))
            .collectionName(Property.ofValue(collectionName))
            .vectorId(Property.ofValue(1))
            .topK(Property.ofValue(1))
            .fetchType(Property.ofValue(FetchType.FETCH_ONE))
            .build();

        var output = task.run(runContext);

        assertThat(output, notNullValue());
        assertThat(output.getSize(), is(1L));
        assertThat(output.getRow(), notNullValue());
    }

    @AfterEach
    void tearDown() {
        if (!isQdrantAvailable()) return;
        try (var client = qdrantClient()) {
            client.deleteCollectionAsync(collectionName).get();
        } catch (Exception ignored) {
        }
    }
}
