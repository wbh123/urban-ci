from __future__ import annotations

import argparse
import json
from pathlib import Path

from tools.model_pipeline import build_parser
from tools.pipeline_model import _write_candidate_manifest


def test_export_hf_defaults_match_official_inference_contract():
    args = build_parser().parse_args(
        [
            "export-hf",
            "--source-dir",
            "source",
            "--output",
            "output",
        ]
    )

    assert args.output_activation == "LOGITS"
    assert args.foreground_polarity == "LOW_PROBABILITY"
    assert args.interpolation == "LANCZOS"
    assert args.input_size == 256


def test_candidate_manifest_records_foreground_and_interpolation(tmp_path):
    output_dir = tmp_path / "package"
    output_dir.mkdir()
    (output_dir / "model.onnx").write_bytes(b"onnx")

    manifest_path = _write_candidate_manifest(
        output_dir=output_dir,
        model_id="AI-CRACK-OFFICIAL-001",
        model_name="Official Crack",
        version="1.0.1",
        source_type="HUGGING_FACE",
        source_repository="samir-mohamed/concrete-crack-segmentation",
        source_revision="55b4933b417822f8dda632cca19e391406d0bc7e",
        source_license="MIT",
        model_license="MIT",
        input_size=256,
        mask_threshold=0.5,
        min_component_pixels=16,
        output_activation="LOGITS",
        foreground_polarity="LOW_PROBABILITY",
        interpolation="LANCZOS",
    )

    payload = json.loads(manifest_path.read_text(encoding="utf-8"))
    assert payload["outputActivation"] == "LOGITS"
    assert payload["foregroundPolarity"] == "LOW_PROBABILITY"
    assert payload["input"]["interpolation"] == "LANCZOS"
