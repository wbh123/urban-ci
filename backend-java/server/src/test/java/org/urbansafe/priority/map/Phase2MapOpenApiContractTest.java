package org.urbansafe.priority.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** 可视化建档所需地图发现与楼栋定位接口使用独立 archive OpenAPI 子契约。 */
class Phase2MapOpenApiContractTest {

    @Test
    void archiveContractDefinesPlaceSearchReverseGeocodingAndBuildingLocation() throws IOException {
        String yaml = read("/archive/openapi-archive.yaml");

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
                .contains("        coordinateSystem:\n          type: string\n          enum: [GCJ02, WGS84, BD09, UNKNOWN]")
                .contains("BuildingLocation:")
                .contains("BuildingLocationSuccessResponse:");
    }

    private String read(String resource) throws IOException {
        try (var stream = getClass().getResourceAsStream(resource)) {
            assertThat(stream)
                    .as("OpenAPI resource %s should exist", resource)
                    .isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
