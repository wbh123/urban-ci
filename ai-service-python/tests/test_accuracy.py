from app.accuracy import (
    AccuracyCandidate,
    LocatorCandidate,
    SemanticClassDecision,
    SemanticGateResult,
    allowed_classes,
    deduplicate_accuracy_candidates,
    fuse_cross_model_candidates,
)


def test_only_qwen_positive_classes_reach_locator_stage():
    gate = SemanticGateResult({
        "CRACK": SemanticClassDecision("CRACK", False, 0.92),
        "WATER_STAIN": SemanticClassDecision("WATER_STAIN", True, 0.81),
        "SPALLING": SemanticClassDecision("SPALLING", None, 0.48),
        "CORROSION": SemanticClassDecision("CORROSION", True, 0.42),
    })
    assert allowed_classes(gate) == {"WATER_STAIN"}


def test_uncertain_class_does_not_reach_locator_stage():
    gate = SemanticGateResult({
        "CRACK": SemanticClassDecision("CRACK", None, 0.48),
    })
    assert allowed_classes(gate) == set()


def test_specific_damage_class_suppresses_surface_damage_fallback():
    gate = SemanticGateResult({
        "CRACK": SemanticClassDecision("CRACK", True, 0.70),
        "SURFACE_DAMAGE": SemanticClassDecision("SURFACE_DAMAGE", True, 0.95),
    })
    assert allowed_classes(gate) == {"CRACK"}


def test_surface_damage_requires_stronger_semantic_score():
    below = SemanticGateResult({
        "SURFACE_DAMAGE": SemanticClassDecision("SURFACE_DAMAGE", True, 0.84),
    })
    assert allowed_classes(below) == set()

    accepted = SemanticGateResult({
        "SURFACE_DAMAGE": SemanticClassDecision("SURFACE_DAMAGE", True, 0.85),
    })
    assert allowed_classes(accepted) == {"SURFACE_DAMAGE"}


def test_qwen_and_two_locators_agree_is_high_and_keeps_grounding_geometry():
    gate = SemanticGateResult({
        "CRACK": SemanticClassDecision("CRACK", True, 0.86),
    })
    locators = [
        LocatorCandidate([100, 100, 300, 300], 0.41, "CRACK", "GROUNDING_DINO"),
        LocatorCandidate([110, 105, 295, 305], 0.78, "CRACK", "FLORENCE2"),
    ]
    result = fuse_cross_model_candidates(gate, locators, 1000, 1000)
    assert len(result) == 1
    assert result[0].trust_level == "HIGH"
    assert set(result[0].sources) == {"GROUNDING_DINO", "FLORENCE2"}
    assert "CROSS_MODEL_CONFIRMED" in result[0].trust_reasons
    assert result[0].box_xyxy == [100.0, 100.0, 300.0, 300.0]
    assert result[0].score == 0.41


def test_qwen_strong_present_and_single_locator_is_medium():
    gate = SemanticGateResult({
        "CRACK": SemanticClassDecision("CRACK", True, 0.84),
    })
    result = fuse_cross_model_candidates(
        gate,
        [LocatorCandidate([100, 100, 300, 300], 0.42, "CRACK", "GROUNDING_DINO")],
        1000,
        1000,
    )
    assert len(result) == 1
    assert result[0].trust_level == "MEDIUM"
    assert "SEMANTIC_GATE_CONFIRMED" in result[0].trust_reasons


def test_qwen_medium_present_requires_cross_model_confirmation():
    gate = SemanticGateResult({
        "CRACK": SemanticClassDecision("CRACK", True, 0.62),
    })
    single = fuse_cross_model_candidates(
        gate,
        [LocatorCandidate([100, 100, 300, 300], 0.72, "CRACK", "GROUNDING_DINO")],
        1000,
        1000,
    )
    assert single == []

    cross = fuse_cross_model_candidates(
        gate,
        [
            LocatorCandidate([100, 100, 300, 300], 0.43, "CRACK", "GROUNDING_DINO"),
            LocatorCandidate([105, 105, 295, 295], 0.75, "CRACK", "FLORENCE2"),
        ],
        1000,
        1000,
    )
    assert len(cross) == 1
    assert cross[0].trust_level == "MEDIUM"
    assert "CROSS_MODEL_CONFIRMED" in cross[0].trust_reasons


def test_uncertain_with_two_independent_locators_is_not_formal():
    gate = SemanticGateResult({
        "CRACK": SemanticClassDecision("CRACK", None, 0.45),
    })
    result = fuse_cross_model_candidates(
        gate,
        [
            LocatorCandidate([100, 100, 300, 300], 0.43, "CRACK", "GROUNDING_DINO"),
            LocatorCandidate([105, 105, 295, 295], 0.75, "CRACK", "FLORENCE2"),
        ],
        1000,
        1000,
    )
    assert result == []


def test_qwen_high_confidence_absent_rejects_locator_candidate():
    gate = SemanticGateResult({
        "CRACK": SemanticClassDecision("CRACK", False, 0.91),
    })
    result = fuse_cross_model_candidates(
        gate,
        [LocatorCandidate([100, 100, 300, 300], 0.85, "CRACK", "FLORENCE2")],
        1000,
        1000,
    )
    assert result == []


def test_single_locator_oversized_box_is_not_formal_even_with_strong_semantics():
    gate = SemanticGateResult({
        "CRACK": SemanticClassDecision("CRACK", True, 0.91),
    })
    result = fuse_cross_model_candidates(
        gate,
        [LocatorCandidate([0, 0, 900, 900], 0.52, "CRACK", "GROUNDING_DINO")],
        1000,
        1000,
    )
    assert result == []


def test_cross_model_oversized_box_is_kept_for_recall():
    gate = SemanticGateResult({
        "CRACK": SemanticClassDecision("CRACK", True, 0.91),
    })
    result = fuse_cross_model_candidates(
        gate,
        [
            LocatorCandidate([0, 0, 900, 900], 0.52, "CRACK", "GROUNDING_DINO"),
            LocatorCandidate([20, 20, 880, 880], 0.71, "CRACK", "FLORENCE2"),
        ],
        1000,
        1000,
    )
    assert len(result) == 1
    assert result[0].trust_level == "HIGH"


def test_surface_damage_never_accepts_single_locator():
    gate = SemanticGateResult({
        "SURFACE_DAMAGE": SemanticClassDecision("SURFACE_DAMAGE", True, 0.95),
    })
    result = fuse_cross_model_candidates(
        gate,
        [
            LocatorCandidate(
                [100, 100, 300, 300],
                0.82,
                "SURFACE_DAMAGE",
                "GROUNDING_DINO",
            ),
        ],
        1000,
        1000,
    )
    assert result == []


def test_surface_damage_cross_model_large_region_is_rejected():
    gate = SemanticGateResult({
        "SURFACE_DAMAGE": SemanticClassDecision("SURFACE_DAMAGE", True, 0.95),
    })
    result = fuse_cross_model_candidates(
        gate,
        [
            LocatorCandidate(
                [0, 0, 550, 550],
                0.62,
                "SURFACE_DAMAGE",
                "GROUNDING_DINO",
            ),
            LocatorCandidate(
                [10, 10, 545, 545],
                0.75,
                "SURFACE_DAMAGE",
                "FLORENCE2",
            ),
        ],
        1000,
        1000,
    )
    assert result == []


def test_surface_damage_cross_model_local_region_is_kept():
    gate = SemanticGateResult({
        "SURFACE_DAMAGE": SemanticClassDecision("SURFACE_DAMAGE", True, 0.95),
    })
    result = fuse_cross_model_candidates(
        gate,
        [
            LocatorCandidate(
                [100, 100, 500, 500],
                0.62,
                "SURFACE_DAMAGE",
                "GROUNDING_DINO",
            ),
            LocatorCandidate(
                [110, 110, 490, 490],
                0.75,
                "SURFACE_DAMAGE",
                "FLORENCE2",
            ),
        ],
        1000,
        1000,
    )
    assert len(result) == 1
    assert result[0].trust_level == "HIGH"
    assert set(result[0].sources) == {"GROUNDING_DINO", "FLORENCE2"}


def _formal(
    box,
    score=0.5,
    trust="MEDIUM",
    sources=("GROUNDING_DINO",),
    code="CRACK",
):
    return AccuracyCandidate(
        box_xyxy=list(box),
        score=score,
        class_code=code,
        sources=tuple(sources),
        semantic_confidence=0.95,
        trust_level=trust,
        trust_reasons=("SEMANTIC_GATE_CONFIRMED",),
    )


def test_instance_dedup_prefers_high_cross_model_candidate_over_contained_medium():
    high = _formal(
        [100, 100, 300, 300],
        score=0.42,
        trust="HIGH",
        sources=("FLORENCE2", "GROUNDING_DINO"),
    )
    duplicate = _formal([120, 120, 280, 280], score=0.91, trust="MEDIUM")
    result = deduplicate_accuracy_candidates([duplicate, high])
    assert result == [high]


def test_instance_dedup_keeps_distinct_adjacent_crack_segments():
    first = _formal([100, 100, 260, 180], score=0.61, trust="HIGH")
    second = _formal([230, 150, 390, 230], score=0.59, trust="HIGH")
    result = deduplicate_accuracy_candidates([first, second])
    assert len(result) == 2
    assert first in result
    assert second in result


def test_instance_dedup_never_merges_different_classes():
    crack = _formal([100, 100, 300, 300], score=0.7, trust="HIGH", code="CRACK")
    stain = _formal([105, 105, 295, 295], score=0.8, trust="HIGH", code="WATER_STAIN")
    result = deduplicate_accuracy_candidates([crack, stain])
    assert len(result) == 2
