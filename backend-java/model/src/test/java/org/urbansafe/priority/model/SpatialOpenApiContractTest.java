package org.urbansafe.priority.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** R2 空间接口必须先由独立 OpenAPI 契约定义，再生成 Java/TypeScript 契约。 */
class SpatialOpenApiContractTest {

    @Test
    void spatialContractDefinesBoundaryLifecycleAndBboxQueries() throws IOException {
        String yaml = read("/spatial/openapi-spatial.yaml");

        assertThat(yaml)
                .contains("/api/v1/spatial/communities:")
                .contains("/api/v1/spatial/buildings:")
                .contains("/api/v1/spatial/communities/{communityId}/boundary:")
                .contains("/api/v1/spatial/communities/{communityId}/boundary/verify:")
                .contains("/api/v1/spatial/communities/{communityId}/boundary/reject:")
                .contains("/api/v1/spatial/buildings/{buildingId}/boundary:")
                .contains("/api/v1/spatial/buildings/{buildingId}/boundary/verify:")
                .contains("/api/v1/spatial/buildings/{buildingId}/boundary/reject:")
                .contains("expectedVersion:")
                .contains("displayCoordinateSystem:")
                .contains("west:")
                .contains("south:")
                .contains("east:")
                .contains("north:")
                .contains("zoom:")
                .contains("FeatureCollection")
                .contains("VERIFIED");
    }

    private String read(String resource) throws IOException {
        try (var stream = getClass().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("OpenAPI resource not found: " + resource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
