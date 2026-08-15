package org.urbansafe.priority.map.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.map.config.AmapProperties;

class R41BoundaryCandidateContractTest {

    @Test
    void exposesAnIndependentBoundaryCandidateFeatureFlag() {
        boolean exists = Arrays.stream(AmapProperties.class.getMethods())
                .anyMatch(method -> method.getName().equals("isBoundaryCandidateEnabled"));

        assertTrue(exists, "R4-1 must expose an independent AMap boundary-candidate switch");
    }

    @Test
    void enablesBoundaryCandidateByDefaultForCompetitionDemo() throws Exception {
        Path application = Path.of("../starter/src/main/resources/application.yaml");
        String source = Files.readString(application);
        assertTrue(source.contains("${URBAN_SAFE_AMAP_BOUNDARY_CANDIDATE_ENABLED:true}"),
                "competition demo baseline should enable boundary candidates unless explicitly overridden");
    }

    @Test
    void publishesBoundaryCandidatePreviewInTheCurrentArchiveContract() throws Exception {
        Path contract = Path.of("../model/src/main/resources/archive/openapi-archive.yaml");
        String source = Files.readString(contract);

        assertTrue(source.contains("/api/v1/map/community-boundary-candidates/preview:"),
                "candidate preview must live in the current archive OpenAPI");
        assertTrue(source.contains("operationId: previewCommunityBoundaryCandidate"));
        assertTrue(source.contains("CommunityBoundaryCandidateRequest:"));
        assertTrue(source.contains("CommunityBoundaryCandidatePreview:"));
    }
}