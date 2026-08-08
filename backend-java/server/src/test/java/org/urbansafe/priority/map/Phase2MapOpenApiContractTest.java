package org.urbansafe.priority.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** 可视化建档所需地图发现与楼栋定位接口必须先进入第二阶段 OpenAPI 契约。 */
class Phase2MapOpenApiContractTest {

    @Test
    void phase2MapContractDefinesPlaceSearchReverseGeocodingAndBuildingLocation() throws IOException {
        String yaml = read("/phase2/openapi-phase2.yaml");

        assertThat(yaml)
                .contains("/api/v1/map/places/search:")
                .contains("/api/v1/map/reverse-geocoding/preview:")
                .contains("/api/v1/buildings/{buildingId}/location:")
                .contains("PlaceSearchRequest:")
                .contains("MapPlaceCandidate:")
                .contains("ReverseGeocodingRequest:")
                .contains("ReverseGeocodingResult:")
                .contains("nearestPoiId: { type: string, nullable: true }")
                .contains("nearestPoiName: { type: string, nullable: true }")
                .contains("BuildingLocationRequest:")
                .contains("BuildingLocation:")
                .contains("BuildingLocationSuccessResponse:");
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
