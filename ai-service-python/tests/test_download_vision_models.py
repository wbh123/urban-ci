from tools import download_vision_models as download


def test_accuracy_first_model_package_is_fixed():
    assert download.MODEL_ID == "AI-VISION-LOCAL-001"
    assert download.MODEL_VERSION == "1.1.0"
    assert download.STATUS == "CANDIDATE"
    assert download.DETECTOR_REPOSITORY == "IDEA-Research/grounding-dino-base"
    assert download.SEGMENTER_REPOSITORY == "facebook/sam2.1-hiera-base-plus"
    assert download.DETECTOR_REVISION == "12bdfa3120f3e7ec7b434d90674b3396eccf88eb"
    assert download.SEGMENTER_REVISION == "b7320756a13354e7530a63935656d35b2f91a290"
    assert len(download.DETECTOR_REVISION) == 40
    assert len(download.SEGMENTER_REVISION) == 40
    assert download.INPUT["maxLongSide"] == 1280


def test_direct_fallback_downloads_only_transformers_runtime_files():
    detector_files = set(download.DIRECT_FILE_SETS[download.DETECTOR_REPOSITORY])
    segmenter_files = set(download.DIRECT_FILE_SETS[download.SEGMENTER_REPOSITORY])

    assert "model.safetensors" in detector_files
    assert "config.json" in detector_files
    assert "preprocessor_config.json" in detector_files
    assert "tokenizer.json" in detector_files
    assert "tokenizer_config.json" in detector_files
    assert "special_tokens_map.json" in detector_files
    assert "vocab.txt" in detector_files
    assert "pytorch_model.bin" not in detector_files

    assert "model.safetensors" in segmenter_files
    assert "config.json" in segmenter_files
    assert "preprocessor_config.json" in segmenter_files
    assert "processor_config.json" in segmenter_files
    assert "video_preprocessor_config.json" in segmenter_files
    assert "sam2.1_hiera_base_plus.pt" not in segmenter_files


def test_pinned_safetensors_hashes_match_verified_official_weights():
    assert download.PINNED_WEIGHT_SHA256[(download.DETECTOR_REPOSITORY, download.DETECTOR_REVISION)] == (
        "5548f844c928c4b6f411fa8cbcc2bfa8dbbba437cb1d513975519f93c2a9ed21"
    )
    assert download.PINNED_WEIGHT_SHA256[(download.SEGMENTER_REPOSITORY, download.SEGMENTER_REVISION)] == (
        "2012733a0de5d03efd1bba550a2847c4551be9ef2e0d497c83074df66189f780"
    )
