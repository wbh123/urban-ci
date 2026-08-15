from app.precision import (
    Candidate,
    PrecisionInferenceOptions,
    TileRegion,
    fuse_candidates,
    generate_tiles,
    map_tile_box_to_image,
    needs_local_verification,
    score_trust,
)


def test_generate_2x2_tiles_cover_image_with_overlap():
    tiles = generate_tiles(1000, 800, rows=2, cols=2, overlap=0.18)
    assert len(tiles) == 4
    assert tiles[0].x0 == 0 and tiles[0].y0 == 0
    assert tiles[-1].x1 == 1000 and tiles[-1].y1 == 800
    assert tiles[0].x1 > tiles[1].x0
    assert tiles[0].y1 > tiles[2].y0


def test_map_tile_box_back_to_original_coordinates():
    tile = TileRegion(index=0, row=0, col=0, x0=400, y0=200, x1=900, y1=700)
    assert map_tile_box_to_image(
        [0, 0, 500, 500], tile, 500, 500, 1200, 900
    ) == [400.0, 200.0, 900.0, 700.0]


def test_fuse_candidates_merges_same_class_cross_scale_and_preserves_best_geometry():
    candidates = [
        Candidate(
            [100, 100, 300, 300], 0.33, "CRACK", "疑似裂缝",
            "wall crack", "FULL_IMAGE", "full"
        ),
        Candidate(
            [110, 105, 295, 295], 0.41, "CRACK", "疑似裂缝",
            "concrete crack", "TILE", "tile-1"
        ),
        Candidate(
            [100, 100, 300, 300], 0.80, "SPALLING", "疑似剥落",
            "concrete spalling", "TILE", "tile-1"
        ),
    ]
    fused = fuse_candidates(candidates, iou_threshold=0.5)
    assert len(fused) == 2
    crack = next(item for item in fused if item.class_code == "CRACK")
    assert crack.box_xyxy == [110.0, 105.0, 295.0, 295.0]
    assert crack.max_confidence == 0.41
    assert crack.prompt_votes == 2
    assert crack.scale_votes == 2
    assert set(crack.sources) == {"FULL_IMAGE", "TILE"}


def test_prompt_verify_is_independent_prompt_evidence_not_fake_scale_vote():
    candidates = [
        Candidate(
            [100, 100, 300, 300], 0.36, "CRACK", "疑似裂缝",
            "wall crack", "TILE", "tile-1"
        ),
        Candidate(
            [105, 105, 295, 295], 0.35, "CRACK", "疑似裂缝",
            "concrete crack", "PROMPT_VERIFY", "prompt-crack-1"
        ),
    ]
    fused = fuse_candidates(candidates)[0]
    assert fused.prompt_votes == 2
    assert fused.scale_votes == 1


def test_near_full_crack_requires_local_verification_and_cannot_be_high_without_it():
    options = PrecisionInferenceOptions()
    candidate = Candidate(
        [0, 0, 950, 950], 0.40, "CRACK", "疑似裂缝",
        "wall crack", "FULL_IMAGE", "full"
    )
    fused = fuse_candidates([candidate])[0]
    assessment = score_trust(fused, options, image_width=1000, image_height=1000)
    assert needs_local_verification(fused, options, 1000, 1000) is True
    assert assessment.level == "LOW"
    assert "LOW_TRUST_LARGE_BOX" in assessment.reasons


def test_cross_scale_multi_prompt_normal_candidate_can_be_high():
    options = PrecisionInferenceOptions()
    candidates = [
        Candidate(
            [100, 100, 300, 300], 0.36, "CRACK", "疑似裂缝",
            "wall crack", "FULL_IMAGE", "full"
        ),
        Candidate(
            [105, 105, 295, 295], 0.34, "CRACK", "疑似裂缝",
            "concrete crack", "TILE", "tile-1"
        ),
    ]
    fused = fuse_candidates(candidates)[0]
    assessment = score_trust(fused, options, image_width=1000, image_height=1000)
    assert assessment.level == "HIGH"
    assert "MULTI_PROMPT_CONFIRMED" in assessment.reasons
    assert "CROSS_SCALE_CONFIRMED" in assessment.reasons


def test_high_confidence_without_independent_confirmation_stays_low():
    options = PrecisionInferenceOptions()
    candidate = Candidate(
        [100, 100, 260, 260], 0.62, "SPALLING", "疑似剥落",
        "concrete spalling", "TILE", "tile-0"
    )
    fused = fuse_candidates([candidate])[0]
    assessment = score_trust(fused, options, image_width=1000, image_height=1000)
    assert assessment.level == "LOW"
    assert "INSUFFICIENT_INDEPENDENT_EVIDENCE" in assessment.reasons


def test_cross_scale_only_confirmation_is_not_enough_for_medium_anymore():
    options = PrecisionInferenceOptions()
    candidates = [
        Candidate(
            [100, 100, 260, 260], 0.44, "SPALLING", "疑似剥落",
            "concrete spalling", "FULL_IMAGE", "full"
        ),
        Candidate(
            [105, 105, 255, 255], 0.42, "SPALLING", "疑似剥落",
            "concrete spalling", "TILE", "tile-0"
        ),
    ]
    fused = fuse_candidates(candidates)[0]
    assessment = score_trust(fused, options, image_width=1000, image_height=1000)
    assert fused.prompt_votes == 1
    assert fused.scale_votes == 2
    assert assessment.level == "LOW"
    assert "INSUFFICIENT_INDEPENDENT_EVIDENCE" in assessment.reasons


def test_two_independent_evidence_signals_with_medium_confidence_are_medium():
    options = PrecisionInferenceOptions()
    candidates = [
        Candidate(
            [100, 100, 260, 260], 0.29, "SPALLING", "疑似剥落",
            "concrete spalling", "FULL_IMAGE", "full"
        ),
        Candidate(
            [105, 105, 255, 255], 0.28, "SPALLING", "疑似剥落",
            "damaged concrete surface", "TILE", "tile-0"
        ),
    ]
    fused = fuse_candidates(candidates)[0]
    assessment = score_trust(fused, options, image_width=1000, image_height=1000)
    assert fused.prompt_votes == 2
    assert fused.scale_votes == 2
    assert assessment.level == "MEDIUM"


def test_two_evidence_signals_below_medium_floor_are_low():
    options = PrecisionInferenceOptions()
    candidates = [
        Candidate(
            [100, 100, 260, 260], 0.27, "SPALLING", "疑似剥落",
            "concrete spalling", "FULL_IMAGE", "full"
        ),
        Candidate(
            [105, 105, 255, 255], 0.26, "SPALLING", "疑似剥落",
            "damaged concrete surface", "TILE", "tile-0"
        ),
    ]
    fused = fuse_candidates(candidates)[0]
    assessment = score_trust(fused, options, image_width=1000, image_height=1000)
    assert assessment.level == "LOW"
    assert "BELOW_MEDIUM_CONFIDENCE_FLOOR" in assessment.reasons
