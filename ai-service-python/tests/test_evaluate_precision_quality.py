from tools.evaluate_precision_quality import evaluate_predictions


def test_evaluate_predictions_uses_only_reviewed_samples_and_computes_precision_priority_metrics():
    truth = {
        "images": [
            {"file": "a.jpg", "expected": ["CRACK"], "reviewStatus": "CONFIRMED"},
            {"file": "b.jpg", "expected": [], "reviewStatus": "CONFIRMED"},
            {"file": "c.jpg", "expected": ["WATER_STAIN"], "reviewStatus": "PENDING"},
        ]
    }
    rows = [
        {
            "file": "a.jpg",
            "detections": [
                {"classCode": "CRACK"},
                {"classCode": "WATER_STAIN"},
            ],
        },
        {"file": "b.jpg", "detections": []},
        {"file": "c.jpg", "detections": [{"classCode": "WATER_STAIN"}]},
    ]

    result = evaluate_predictions(truth, rows)

    assert result["reviewedSamples"] == 2
    assert result["tp"] == 1
    assert result["fp"] == 1
    assert result["fn"] == 0
    assert result["precision"] == 0.5
    assert result["recall"] == 1.0
    assert result["f1"] == 0.6667
    assert result["f0_5"] == 0.5556
    assert result["normalSamples"] == 1
    assert result["normalFalsePositiveImages"] == 0
    assert result["perClass"]["CRACK"] == {"expected": 1, "hit": 1, "miss": 0, "fp": 0}
    assert result["perClass"]["WATER_STAIN"]["fp"] == 1


def test_evaluate_predictions_returns_not_evaluated_when_everything_is_pending():
    truth = {
        "images": [
            {"file": "a.jpg", "expected": ["CRACK"], "reviewStatus": "PENDING"},
        ]
    }
    result = evaluate_predictions(truth, [{"file": "a.jpg", "detections": []}])
    assert result["reviewedSamples"] == 0
    assert result["status"] == "QUALITY_NOT_EVALUATED"
