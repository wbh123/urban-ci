from app.schemas import DetectionItem, InferenceMetadata, InferenceProfile


def test_old_metadata_defaults_to_fast_profile():
    metadata = InferenceMetadata.model_validate(
        {"requestId": "r1", "mode": "REAL"}
    )
    assert metadata.inferenceProfile == InferenceProfile.FAST


def test_precision_metadata_is_accepted():
    metadata = InferenceMetadata.model_validate(
        {
            "requestId": "r1",
            "mode": "REAL",
            "inferenceProfile": "PRECISION",
        }
    )
    assert metadata.inferenceProfile == InferenceProfile.PRECISION


def test_old_detection_json_remains_valid_without_trust_fields():
    item = DetectionItem.model_validate(
        {
            "sequence": 1,
            "classCode": "CRACK",
            "className": "疑似裂缝",
            "confidence": 0.4,
            "boundingBox": {
                "x": 0.1,
                "y": 0.1,
                "width": 0.2,
                "height": 0.2,
            },
        }
    )
    assert item.trustLevel is None
    assert item.trustReasons == []
    assert item.diagnostics == {}
