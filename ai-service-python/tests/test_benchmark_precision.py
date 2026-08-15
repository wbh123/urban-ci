from types import SimpleNamespace

from tools.benchmark_precision import build_report, serialize_rows, summarize_results


def det(code, x, y, w, h, trust=None, confidence=0.5):
    return SimpleNamespace(
        classCode=code,
        className=code,
        confidence=confidence,
        boundingBox=SimpleNamespace(
            x=x, y=y, width=w, height=h, coordinateType="NORMALIZED_XYWH"
        ),
        trustLevel=trust,
        trustReasons=["TEST_REASON"] if trust else [],
        diagnostics={"bboxAreaRatio": round(w * h, 4)},
    )


def test_summary_counts_large_boxes_near_full_cracks_and_trust_levels():
    rows = [
        {
            "file": "a.jpg",
            "durationMs": 1200,
            "detections": [det("CRACK", 0, 0, 0.95, 0.95, "LOW")],
        },
        {
            "file": "b.jpg",
            "durationMs": 1800,
            "detections": [
                det("WATER_STAIN", 0.1, 0.1, 0.2, 0.2, "HIGH")
            ],
        },
        {"file": "c.jpg", "durationMs": 1500, "detections": []},
    ]
    summary = summarize_results(rows)
    assert summary["totalDetections"] == 2
    assert summary["noDetectionImages"] == 1
    assert summary["largeBoxes"] == 1
    assert summary["nearFullCracks"] == 1
    assert summary["trustCounts"] == {
        "HIGH": 1,
        "MEDIUM": 0,
        "LOW": 1,
        "UNSET": 0,
    }
    assert summary["p50Ms"] == 1500
    assert summary["p95Ms"] == 1800


def test_report_without_ground_truth_does_not_claim_accuracy_metrics():
    report = build_report(
        model_version="1.1.0",
        image_count=3,
        fast_summary=summarize_results([]),
        precision_summary=summarize_results([]),
        ground_truth_available=False,
    )
    assert "GROUND_TRUTH_NOT_AVAILABLE" in report
    assert "Precision=" not in report
    assert "Recall=" not in report
    assert "F1=" not in report
    assert "Accuracy=" not in report


def test_serialize_rows_keeps_per_image_detection_trust_diagnostics():
    rows = [
        {
            "file": "a.jpg",
            "durationMs": 1234.5,
            "detections": [det("CRACK", 0.1, 0.2, 0.3, 0.4, "HIGH", 0.41)],
        }
    ]
    details = serialize_rows(rows)
    assert details[0]["file"] == "a.jpg"
    assert details[0]["detections"][0]["classCode"] == "CRACK"
    assert details[0]["detections"][0]["trustLevel"] == "HIGH"
    assert details[0]["detections"][0]["trustReasons"] == ["TEST_REASON"]
    assert details[0]["detections"][0]["diagnostics"]["bboxAreaRatio"] == 0.12
