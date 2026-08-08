package org.urbansafe.priority.spatial;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.model.api.SpatialApi;
import org.urbansafe.priority.spatial.controller.SpatialController;

/** R2 HTTP 层必须直接实现生成的 SpatialApi，避免契约与控制器漂移。 */
class SpatialControllerContractTest {

    @Test
    void controllerImplementsGeneratedSpatialApiAndAllOperations() {
        assertThat(SpatialApi.class).isAssignableFrom(SpatialController.class);

        Set<String> methods = Arrays.stream(SpatialController.class.getDeclaredMethods())
                .map(method -> method.getName())
                .collect(Collectors.toSet());

        assertThat(methods).contains(
                "querySpatialCommunities",
                "querySpatialBuildings",
                "getCommunityBoundary",
                "upsertCommunityBoundary",
                "verifyCommunityBoundary",
                "rejectCommunityBoundary",
                "getBuildingBoundary",
                "upsertBuildingBoundary",
                "verifyBuildingBoundary",
                "rejectBuildingBoundary");
    }
}
