from types import SimpleNamespace

from tools.benchmark_accuracy import (
    _serialize_gate,
    build_report,
    serialize_rows,
    summarize_rows,
)


def det(code="CRACK", trust="HIGH", confidence=0.5, area=0.1):
    return {
        "sequence": 1,
        "classCode": code,
        "className": code,
        "confidence": confidence,
        "boundingBox": {
            "x": 0.1,
            "y": 0.1,
            "width": area,
            "height": area,
            "coordinateType": "NORMALIZED_XYWH",
        },
        "segmentation": None,
        "trustLevel": trust,
        "trustReasons": ["TEST"],
        "diagnostics": {},
    }


def test_summarize_rows_handles_dict_detections_and_class_counts():
    rows = [
        {"file": "a.jpg", "durationMs": 1000, "detections": [det("CRACK", "HIGH")]},
        {"file": "b.jpg", "durationMs": 2000, "detections": [det("WATER_STAIN", "MEDIUM")]},
        {"file": "c.jpg", "durationMs": 1500, "detections": []},
    ]
    summary = summarize_rows(rows)
    assert summary["totalDetections"] == 2
    assert summary["noDetectionImages"] == 1
    assert summary["classCounts"] == {"CRACK": 1, "WATER_STAIN": 1}
    assert summary["trustCounts"]["HIGH"] == 1
    assert summary["trustCounts"]["MEDIUM"] == 1
    assert summary["p50Ms"] == 1500.0


def test_accuracy_report_without_ground_truth_has_no_precision_recall_claims():
    empty = summarize_rows([])
    report = build_report(
        image_count=26,
        vision_version="1.1.0",
        precision_summary=empty,
        accuracy_summary=empty,
        accuracy_batch_wall_ms=12345.0,
        ground_truth_available=False,
    )
    assert "GROUND_TRUTH_NOT_AVAILABLE" in report
    assert "Precision=" not in report
    assert "Recall=" not in report
    assert "F1=" not in report
    assert "12.35" in report


def test_serialize_rows_preserves_accuracy_diagnostics():
    rows = [{"file": "a.jpg", "durationMs": 123.4, "detections": [det()]}]
    serialized = serialize_rows(rows)
    assert serialized[0]["file"] == "a.jpg"
    assert serialized[0]["detections"][0]["trustLevel"] == "HIGH"
    assert serialized[0]["detections"][0]["trustReasons"] == ["TEST"]


def test_serialize_gate_records_present_and_confidence():
    gate = SimpleNamespace(classes={
        "CRACK": SimpleNamespace(present=True, confidence=0.81234),
        "WATER_STAIN": SimpleNamespace(present=False, confidence=0.92345),
    })
    assert _serialize_gate(gate) == {
        "CRACK": {"present": True, "confidence": 0.8123},
        "WATER_STAIN": {"present": False, "confidence": 0.9234},
    }
