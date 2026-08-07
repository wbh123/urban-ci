from __future__ import annotations

import csv
import hashlib
import importlib.util
import json
import sys
from pathlib import Path
from zipfile import ZipFile

from PIL import Image


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "build_phase7_validation_dataset.py"
SPEC = importlib.util.spec_from_file_location("phase7_builder", SCRIPT_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


def _image(path: Path, color: tuple[int, int, int] = (120, 120, 120), size: tuple[int, int] = (96, 96)) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.new("RGB", size, color).save(path)


def _image_with_exif(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image = Image.new("RGB", (320, 320), (180, 120, 80))
    for y in range(320):
        for x in range(320):
            shade = 80 if ((x // 8) + (y // 8)) % 2 else 180
            image.putpixel((x, y), (shade, 120, 200 - shade // 2))
    exif = Image.Exif()
    exif[0x010E] = "sensitive description"
    image.save(path, exif=exif)


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _read_csv(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def test_dry_run_does_not_write_output_and_reports_candidates(tmp_path: Path) -> None:
    input_dir = tmp_path / "downloads"
    output_dir = input_dir / "phase7-validation"
    _image(input_dir / "dataset" / "crack" / "a.png")

    summary = MODULE.build_dataset(
        MODULE.BuildConfig(input_dir=input_dir, output_dir=output_dir, dry_run=True, target_count=30)
    )

    assert summary.candidate_image_count == 1
    assert summary.selected_count == 1
    assert summary.fixed_samples[0]["selected_for_fixed_diagnostic"] == "true"
    assert summary.fixed_samples[0]["output_relative_path"].startswith("fixed_diagnostic/obvious_defect/")
    assert not output_dir.exists()


def test_build_skips_output_directory_and_preserves_original_hashes(tmp_path: Path) -> None:
    input_dir = tmp_path / "downloads"
    output_dir = input_dir / "phase7-validation"
    original = input_dir / "dataset" / "crack" / "a.png"
    nested_output_original = output_dir / "fixed_diagnostic" / "obvious_defect" / "ignored.jpg"
    _image(original, (20, 30, 40))
    _image(nested_output_original, (200, 200, 200))
    before = _sha256(original)

    MODULE.build_dataset(MODULE.BuildConfig(input_dir=input_dir, output_dir=output_dir, force=True, target_count=30))

    rows = _read_csv(output_dir / "manifest.csv")
    assert len(rows) == 1
    assert rows[0]["source_relative_path"] == "dataset/crack/a.png"
    assert _sha256(original) == before


def test_exact_duplicates_and_near_duplicates_are_recorded_once(tmp_path: Path) -> None:
    input_dir = tmp_path / "downloads"
    output_dir = input_dir / "phase7-validation"
    first = input_dir / "dataset" / "crack" / "a.png"
    exact = input_dir / "dataset-copy" / "crack" / "a-copy.png"
    near = input_dir / "dataset-near" / "crack" / "a-near.png"
    _image(first, (100, 100, 100))
    exact.parent.mkdir(parents=True, exist_ok=True)
    exact.write_bytes(first.read_bytes())
    _image(near, (101, 100, 100))

    MODULE.build_dataset(
        MODULE.BuildConfig(
            input_dir=input_dir,
            output_dir=output_dir,
            force=True,
            target_count=30,
            near_duplicate_hamming_threshold=3,
        )
    )

    rows = _read_csv(output_dir / "manifest.csv")
    duplicates = _read_csv(output_dir / "duplicates.csv")
    reasons = {row["reason"] for row in duplicates}
    assert len(rows) == 1
    assert "EXACT_SHA256" in reasons
    assert "NEAR_DUPLICATE_PHASH_REVIEW" in reasons



def test_mask_and_label_directories_are_excluded_from_candidates(tmp_path: Path) -> None:
    input_dir = tmp_path / "downloads"
    output_dir = input_dir / "phase7-validation"
    _image(input_dir / "dataset" / "images" / "a.jpg", (10, 80, 120))
    _image(input_dir / "dataset" / "masks" / "a.png", (255, 255, 255))
    _image(input_dir / "dataset" / "images" / "a_lab.png", (255, 255, 255))

    MODULE.build_dataset(MODULE.BuildConfig(input_dir=input_dir, output_dir=output_dir, force=True, target_count=30))

    rows = _read_csv(output_dir / "manifest.csv")
    excluded = _read_csv(output_dir / "excluded" / "exclusion_manifest.csv")
    assert len(rows) == 1
    assert rows[0]["source_relative_path"] == "dataset/images/a.jpg"
    assert any(row["reason"] == "LABEL_OR_MASK_IMAGE" for row in excluded)

def test_corrupt_images_are_excluded_and_manifest_has_required_fields(tmp_path: Path) -> None:
    input_dir = tmp_path / "downloads"
    output_dir = input_dir / "phase7-validation"
    _image(input_dir / "dataset" / "crack" / "a.png")
    broken = input_dir / "dataset" / "crack" / "broken.jpg"
    broken.write_bytes(b"not an image")

    MODULE.build_dataset(MODULE.BuildConfig(input_dir=input_dir, output_dir=output_dir, force=True, target_count=30))

    rows = _read_csv(output_dir / "manifest.csv")
    excluded = _read_csv(output_dir / "excluded" / "exclusion_manifest.csv")
    assert MODULE.MANIFEST_FIELDS == list(rows[0].keys())
    assert len(rows) == 1
    assert excluded[0]["reason"] == "CORRUPT_IMAGE"
    assert excluded[0]["source_relative_path"] == "dataset/crack/broken.jpg"


def test_output_copy_preserves_exif_and_privacy_is_asserted_clean(tmp_path: Path) -> None:
    input_dir = tmp_path / "downloads"
    output_dir = input_dir / "phase7-validation"
    original = input_dir / "unknown-source" / "misc" / "with-exif.jpg"
    _image_with_exif(original)

    MODULE.build_dataset(MODULE.BuildConfig(input_dir=input_dir, output_dir=output_dir, force=True, target_count=30))

    row = _read_csv(output_dir / "manifest.csv")[0]
    output_image = output_dir / row["output_relative_path"]
    with Image.open(output_image) as image:
        assert image.getexif()
    assert row["secondary_label"] == "UNKNOWN"
    assert row["needs_manual_review"] == "true"
    assert row["privacy_status"] == "CLEAN"
    privacy_row = _read_csv(output_dir / "privacy_review.csv")[0]
    assert privacy_row["privacy_status"] == "CLEAN"
    assert privacy_row["review_reason"] == "USER_ASSERTED_NO_SENSITIVE_CONTENT"
    manual_queue = _read_csv(output_dir / "reports" / "manual_review_queue.csv")
    assert manual_queue[0]["sample_id"] == row["sample_id"]


def test_sample_id_selection_and_archive_sources_are_stable(tmp_path: Path) -> None:
    input_dir = tmp_path / "downloads"
    output_dir = input_dir / "phase7-validation"
    archive = input_dir / "archives" / "crack-set.zip"
    archive.parent.mkdir(parents=True)
    archive_image = tmp_path / "archive-image.jpg"
    _image(archive_image, (33, 44, 55))
    with ZipFile(archive, "w") as handle:
        handle.write(archive_image, "dataset/crack/z.jpg")
    _image(input_dir / "plain" / "shadow" / "negative.jpg", (77, 88, 99))

    MODULE.build_dataset(MODULE.BuildConfig(input_dir=input_dir, output_dir=output_dir, force=True, target_count=2))
    first_rows = _read_csv(output_dir / "manifest.csv")
    first_payload = json.loads((output_dir / "quality_report.json").read_text(encoding="utf-8"))
    MODULE.build_dataset(MODULE.BuildConfig(input_dir=input_dir, output_dir=output_dir, force=True, target_count=2))
    second_rows = _read_csv(output_dir / "manifest.csv")

    assert [row["sample_id"] for row in first_rows] == [row["sample_id"] for row in second_rows]
    assert [row["output_relative_path"] for row in first_rows] == [row["output_relative_path"] for row in second_rows]
    assert any(row["source_archive"] == "archives/crack-set.zip" for row in first_rows)
    assert first_payload["configuration"]["seed"] == 20260731

