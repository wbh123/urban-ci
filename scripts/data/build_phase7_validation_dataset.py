#!/usr/bin/env python3
"""Build the phase-7 fixed AI validation dataset from local downloads.

The script is intentionally offline-only. It copies source image bytes into
the output directory and never modifies source files under downloads/.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import random
import re
import shutil
import sys
import tarfile
import tempfile
import zipfile
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath
from typing import Any, Iterable
from xml.sax.saxutils import escape

from PIL import Image, ImageOps, UnidentifiedImageError


SEED = 20260731
IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".bmp", ".tif", ".tiff"}
ARCHIVE_EXTENSIONS = {".zip", ".tar", ".tgz", ".tar.gz"}
STRUCTURED_EXTENSIONS = {".csv", ".json", ".jsonl", ".yaml", ".yml", ".xlsx"}
LABEL_OR_MASK_DIRECTORY_KEYWORDS = {
    "annotation", "annotations", "bw", "gt", "label", "labels",
    "mask", "masks", "segmentation", "seg", "ground_truth",
}
PRIMARY_CATEGORIES = [
    "obvious_defect",
    "difficult_defect",
    "hard_negative",
    "low_quality",
    "not_applicable",
]
DEFAULT_CATEGORY_TARGETS = {
    "obvious_defect": 8,
    "difficult_defect": 7,
    "hard_negative": 7,
    "low_quality": 4,
    "not_applicable": 4,
}
SECONDARY_LABELS = [
    "CRACK",
    "SPALLING",
    "SEEPAGE",
    "EXPOSED_REBAR",
    "CORROSION",
    "HOLE",
    "JOINT",
    "STAIN",
    "SHADOW",
    "BRICK_JOINT",
    "BLUR",
    "OVEREXPOSED",
    "UNDEREXPOSED",
    "OCCLUDED",
    "OTHER",
    "UNKNOWN",
]
MANIFEST_FIELDS = [
    "sample_id",
    "output_relative_path",
    "source_relative_path",
    "source_archive",
    "source_dataset",
    "sha256",
    "perceptual_hash",
    "width",
    "height",
    "file_size_bytes",
    "primary_category",
    "secondary_label",
    "label_source",
    "label_confidence",
    "needs_manual_review",
    "privacy_status",
    "quality_flags",
    "blur_score",
    "brightness_mean",
    "overexposure_ratio",
    "underexposure_ratio",
    "selected_for_fixed_diagnostic",
    "split",
    "notes",
]


@dataclass(frozen=True)
class QualityThresholds:
    min_width: int = 256
    min_height: int = 256
    min_file_size_bytes: int = 1024
    min_blur_score: float = 50.0
    low_brightness_mean: float = 35.0
    high_brightness_mean: float = 220.0
    overexposure_pixel_threshold: int = 245
    underexposure_pixel_threshold: int = 10
    max_overexposure_ratio: float = 0.25
    max_underexposure_ratio: float = 0.25
    max_aspect_ratio: float = 4.0


@dataclass(frozen=True)
class BuildConfig:
    input_dir: Path
    output_dir: Path
    seed: int = SEED
    target_count: int = 30
    dry_run: bool = False
    force: bool = False
    config_path: Path | None = None
    near_duplicate_hamming_threshold: int = 6
    thresholds: QualityThresholds = field(default_factory=QualityThresholds)


@dataclass
class BuildSummary:
    candidate_image_count: int = 0
    unique_image_count: int = 0
    selected_count: int = 0
    duplicate_count: int = 0
    corrupt_count: int = 0
    excluded_count: int = 0
    privacy_review_count: int = 0
    category_counts: dict[str, int] = field(default_factory=dict)
    generated_files: list[str] = field(default_factory=list)
    fixed_samples: list[dict[str, str]] = field(default_factory=list)


@dataclass
class CandidateImage:
    local_path: Path
    source_relative_path: str
    source_archive: str
    source_dataset: str


@dataclass
class ImageRecord:
    candidate: CandidateImage
    sha256: str
    perceptual_hash: str
    width: int
    height: int
    file_size_bytes: int
    color_mode: str
    blur_score: float
    brightness_mean: float
    overexposure_ratio: float
    underexposure_ratio: float
    has_exif: bool
    has_transparency: bool
    quality_flags: list[str]
    primary_category: str
    secondary_label: str
    label_source: str
    label_confidence: float
    needs_manual_review: bool
    privacy_status: str
    notes: list[str]

    @property
    def sample_id(self) -> str:
        return "p7_" + self.sha256[:16]


@dataclass
class DuplicateRecord:
    kept_sample_id: str
    kept_source_relative_path: str
    duplicate_source_relative_path: str
    reason: str
    kept_sha256: str
    duplicate_sha256: str
    kept_perceptual_hash: str
    duplicate_perceptual_hash: str
    hamming_distance: str


@dataclass
class ExclusionRecord:
    source_relative_path: str
    source_archive: str
    reason: str
    detail: str


def build_dataset(config: BuildConfig) -> BuildSummary:
    input_dir = config.input_dir.expanduser().resolve()
    output_dir = config.output_dir.expanduser().resolve()
    if not input_dir.is_dir():
        raise NotADirectoryError(f"input directory does not exist: {input_dir}")
    if not _is_relative_to(output_dir, input_dir):
        raise ValueError("output directory must be inside input directory")

    temp_parent = Path("/tmp") if Path("/tmp").is_dir() else None
    with tempfile.TemporaryDirectory(prefix="phase7-validation-", dir=temp_parent) as temp_root:
        candidates, exclusions = discover_inputs(input_dir, output_dir, Path(temp_root))
        records = analyze_candidates(candidates, config.thresholds, exclusions)
        unique_records, duplicates = deduplicate_records(records, config.near_duplicate_hamming_threshold)
        selected = select_fixed_samples(unique_records, config.seed, config.target_count)
        summary = BuildSummary(
            candidate_image_count=len(records),
            unique_image_count=len(unique_records),
            selected_count=len(selected),
            duplicate_count=len(duplicates),
            corrupt_count=sum(1 for item in exclusions if item.reason == "CORRUPT_IMAGE"),
            excluded_count=len(exclusions),
            privacy_review_count=sum(1 for item in unique_records if item.privacy_status == "NEEDS_MANUAL_REVIEW"),
            category_counts=_category_counts(unique_records),
            fixed_samples=[_manifest_row(record, {item.sample_id for item in selected}, _expected_output_relative_path(record)) for record in selected],
        )
        if config.dry_run:
            return summary
        generated = write_dataset_outputs(config, unique_records, selected, duplicates, exclusions)
        summary.generated_files = generated
        return summary


def discover_inputs(input_dir: Path, output_dir: Path, temp_root: Path) -> tuple[list[CandidateImage], list[ExclusionRecord]]:
    candidates: list[CandidateImage] = []
    exclusions: list[ExclusionRecord] = []
    archive_index = 0
    for path in sorted(item for item in input_dir.rglob("*") if item.is_file()):
        if _is_relative_to(path.resolve(), output_dir):
            continue
        suffix = _normalized_suffix(path)
        source_relative = _relative(input_dir, path)
        if suffix in IMAGE_EXTENSIONS:
            if _looks_like_label_or_mask_image(source_relative):
                exclusions.append(ExclusionRecord(source_relative, "", "LABEL_OR_MASK_IMAGE", "directory semantic indicates annotation or mask"))
            else:
                candidates.append(CandidateImage(path, source_relative, "", _source_dataset(source_relative)))
        elif suffix in ARCHIVE_EXTENSIONS:
            archive_index += 1
            archive_dir = temp_root / f"archive_{archive_index:04d}"
            try:
                print(f"phase7 builder: extracting archive {source_relative}", file=sys.stderr)
                extracted = _extract_archive(path, archive_dir)
            except (tarfile.TarError, zipfile.BadZipFile, OSError) as exc:
                exclusions.append(ExclusionRecord(source_relative, "", "UNREADABLE_ARCHIVE", str(exc)))
                continue
            for extracted_path, inner_name in extracted:
                inner_suffix = _normalized_suffix(extracted_path)
                archive_source = f"{source_relative}!/{inner_name}"
                if inner_suffix in IMAGE_EXTENSIONS:
                    if _looks_like_label_or_mask_image(archive_source):
                        exclusions.append(ExclusionRecord(archive_source, source_relative, "LABEL_OR_MASK_IMAGE", "directory semantic indicates annotation or mask"))
                    else:
                        candidates.append(CandidateImage(
                            extracted_path,
                            archive_source,
                            source_relative,
                            _source_dataset(source_relative),
                        ))
                elif inner_suffix not in STRUCTURED_EXTENSIONS:
                    exclusions.append(ExclusionRecord(archive_source, source_relative, "UNSUPPORTED_FORMAT", inner_suffix))
        elif suffix in STRUCTURED_EXTENSIONS:
            continue
        else:
            exclusions.append(ExclusionRecord(source_relative, "", "UNSUPPORTED_FORMAT", suffix or "<no-extension>"))
    return candidates, exclusions


def analyze_candidates(
    candidates: Iterable[CandidateImage],
    thresholds: QualityThresholds,
    exclusions: list[ExclusionRecord],
) -> list[ImageRecord]:
    records: list[ImageRecord] = []
    for index, candidate in enumerate(candidates, start=1):
        if index == 1 or index % 1000 == 0:
            print(f"phase7 builder: analyzing image {index}", file=sys.stderr)
        try:
            records.append(_analyze_image(candidate, thresholds))
        except (UnidentifiedImageError, OSError, ValueError) as exc:
            exclusions.append(ExclusionRecord(
                candidate.source_relative_path,
                candidate.source_archive,
                "CORRUPT_IMAGE",
                str(exc),
            ))
    return records


def deduplicate_records(records: list[ImageRecord], threshold: int) -> tuple[list[ImageRecord], list[DuplicateRecord]]:
    unique: list[ImageRecord] = []
    duplicates: list[DuplicateRecord] = []
    by_sha: dict[str, ImageRecord] = {}
    near_buckets: dict[tuple[int, int, int, str], list[ImageRecord]] = {}
    for record in sorted(records, key=lambda item: (item.candidate.source_relative_path, item.sha256)):
        exact = by_sha.get(record.sha256)
        if exact is not None:
            duplicates.append(_duplicate_record(exact, record, "EXACT_SHA256", "0"))
            continue
        near = _find_near_duplicate(near_buckets.get(_near_bucket_key(record), []), record, threshold)
        if near is not None:
            distance = _hamming_hex(near.perceptual_hash, record.perceptual_hash)
            duplicates.append(_duplicate_record(near, record, "NEAR_DUPLICATE_PHASH_REVIEW", str(distance)))
            continue
        by_sha[record.sha256] = record
        near_buckets.setdefault(_near_bucket_key(record), []).append(record)
        unique.append(record)
    return unique, duplicates


def select_fixed_samples(records: list[ImageRecord], seed: int, target_count: int) -> list[ImageRecord]:
    if target_count <= 0:
        return []
    rng = random.Random(seed)
    by_category: dict[str, list[ImageRecord]] = {category: [] for category in PRIMARY_CATEGORIES}
    for record in records:
        by_category.setdefault(record.primary_category, []).append(record)
    for items in by_category.values():
        items.sort(key=lambda item: (
            item.candidate.source_dataset,
            item.width * item.height,
            item.sample_id,
            item.candidate.source_relative_path,
        ))
        rng.shuffle(items)

    selected: list[ImageRecord] = []
    quotas = _scaled_targets(target_count)
    for category in PRIMARY_CATEGORIES:
        count = min(quotas.get(category, 0), len(by_category.get(category, [])))
        selected.extend(by_category.get(category, [])[:count])
    if len(selected) < min(target_count, len(records)):
        selected_ids = {item.sample_id for item in selected}
        remaining = [item for item in records if item.sample_id not in selected_ids]
        remaining.sort(key=lambda item: (item.primary_category, item.sample_id))
        rng.shuffle(remaining)
        selected.extend(remaining[: min(target_count, len(records)) - len(selected)])
    selected.sort(key=lambda item: (PRIMARY_CATEGORIES.index(item.primary_category), item.sample_id))
    return selected


def write_dataset_outputs(
    config: BuildConfig,
    records: list[ImageRecord],
    selected: list[ImageRecord],
    duplicates: list[DuplicateRecord],
    exclusions: list[ExclusionRecord],
) -> list[str]:
    input_dir = config.input_dir.expanduser().resolve()
    output_dir = config.output_dir.expanduser().resolve()
    if output_dir.exists() and not config.force:
        raise FileExistsError(f"output directory already exists; use --force to rebuild: {output_dir}")
    if output_dir.exists():
        shutil.rmtree(output_dir)
    for category in PRIMARY_CATEGORIES:
        (output_dir / "fixed_diagnostic" / category).mkdir(parents=True, exist_ok=True)
    (output_dir / "excluded").mkdir(parents=True, exist_ok=True)
    (output_dir / "reports").mkdir(parents=True, exist_ok=True)

    selected_set = {record.sample_id for record in selected}
    output_paths: dict[str, str] = {}
    for record in selected:
        relative = Path(_expected_output_relative_path(record))
        _write_output_copy(record.candidate.local_path, output_dir / relative)
        output_paths[record.sample_id] = relative.as_posix()

    manifest_rows = [_manifest_row(record, selected_set, output_paths.get(record.sample_id, "")) for record in records]
    generated = [
        _write_csv(output_dir / "manifest.csv", MANIFEST_FIELDS, manifest_rows),
        _write_jsonl(output_dir / "manifest.jsonl", manifest_rows),
        _write_quality_report(output_dir / "quality_report.json", config, records, selected, duplicates, exclusions),
        _write_csv(output_dir / "duplicates.csv", [
            "kept_sample_id",
            "kept_source_relative_path",
            "duplicate_source_relative_path",
            "reason",
            "kept_sha256",
            "duplicate_sha256",
            "kept_perceptual_hash",
            "duplicate_perceptual_hash",
            "hamming_distance",
        ], [asdict(item) for item in duplicates]),
        _write_csv(output_dir / "privacy_review.csv", [
            "sample_id",
            "source_relative_path",
            "output_relative_path",
            "privacy_status",
            "review_reason",
        ], [_privacy_row(record, output_paths.get(record.sample_id, "")) for record in records]),
        _write_text(output_dir / "label_mapping.yaml", _label_mapping_yaml()),
        _write_json(output_dir / "quality_thresholds.json", asdict(config.thresholds)),
        _write_csv(output_dir / "excluded" / "exclusion_manifest.csv", [
            "source_relative_path",
            "source_archive",
            "reason",
            "detail",
        ], [asdict(item) for item in exclusions]),
        _write_csv(output_dir / "reports" / "class_distribution.csv", [
            "primary_category",
            "count",
            "selected_count",
        ], _class_distribution_rows(records, selected_set)),
        _write_csv(output_dir / "reports" / "source_distribution.csv", [
            "source_dataset",
            "count",
            "selected_count",
        ], _source_distribution_rows(records, selected_set)),
        _write_csv(output_dir / "reports" / "manual_review_queue.csv", MANIFEST_FIELDS, [
            row for row in manifest_rows if row["needs_manual_review"] == "true"
        ]),
        _write_text(output_dir / "README.md", _readme_text(config, records, selected, duplicates, exclusions)),
        _write_text(output_dir / "dataset_card.md", _dataset_card_text(config, records, selected, duplicates, exclusions)),
        _write_text(output_dir / "reports" / "build_report.md", _build_report_text(config, records, selected, duplicates, exclusions)),
        _write_labels_template(output_dir / "labels_template.xlsx", [row for row in manifest_rows if row["selected_for_fixed_diagnostic"] == "true"]),
    ]
    return sorted(_relative(output_dir, Path(path)) for path in generated)


def _analyze_image(candidate: CandidateImage, thresholds: QualityThresholds) -> ImageRecord:
    raw = candidate.local_path.read_bytes()
    sha256 = hashlib.sha256(raw).hexdigest()
    file_size = len(raw)
    with Image.open(candidate.local_path) as image:
        image.load()
        width, height = image.size
        mode = image.mode
        has_exif = bool(image.getexif())
        has_transparency = mode in {"RGBA", "LA"} or (mode == "P" and "transparency" in image.info)
        gray = ImageOps.grayscale(image)
        perceptual_hash = _dhash(gray)
        brightness_mean, over_ratio, under_ratio = _brightness_metrics(gray, thresholds)
        blur_score = _laplacian_variance(gray)

    quality_flags = _quality_flags(width, height, file_size, blur_score, brightness_mean, over_ratio, under_ratio, thresholds)
    label = _infer_label(candidate.source_relative_path, quality_flags)
    notes = ["privacy clean asserted by dataset steward; no automated desensitization performed"]
    if has_exif:
        notes.append("source image has EXIF; output copy preserves metadata")
    if has_transparency:
        notes.append("source image has transparency")
    return ImageRecord(
        candidate=candidate,
        sha256=sha256,
        perceptual_hash=perceptual_hash,
        width=width,
        height=height,
        file_size_bytes=file_size,
        color_mode=mode,
        blur_score=blur_score,
        brightness_mean=brightness_mean,
        overexposure_ratio=over_ratio,
        underexposure_ratio=under_ratio,
        has_exif=has_exif,
        has_transparency=has_transparency,
        quality_flags=quality_flags,
        primary_category=label["primary_category"],
        secondary_label=label["secondary_label"],
        label_source=label["label_source"],
        label_confidence=label["label_confidence"],
        needs_manual_review=label["needs_manual_review"],
        privacy_status="CLEAN",
        notes=notes,
    )


def _infer_label(source_relative_path: str, quality_flags: list[str]) -> dict[str, Any]:
    parts = [part.lower() for part in PurePosixPath(source_relative_path.split("!/", 1)[-1]).parent.parts]
    normalized = set(_normalize_token(part) for part in parts)
    path_text = "/".join(parts)
    if normalized & {"crack", "cracks", "cracked", "positive"}:
        return _label("obvious_defect", "CRACK", "directory_semantic", 0.7, False)
    if normalized & {"spalling", "spall"}:
        return _label("obvious_defect", "SPALLING", "directory_semantic", 0.7, False)
    if normalized & {"seepage", "leak", "leakage"}:
        return _label("obvious_defect", "SEEPAGE", "directory_semantic", 0.7, False)
    if "exposed" in normalized and "rebar" in normalized:
        return _label("obvious_defect", "EXPOSED_REBAR", "directory_semantic", 0.7, False)
    if normalized & {"corrosion", "rust"}:
        return _label("obvious_defect", "CORROSION", "directory_semantic", 0.7, False)
    if normalized & {"hole", "holes"}:
        return _label("obvious_defect", "HOLE", "directory_semantic", 0.7, False)
    if normalized & {"shadow", "shadows"}:
        return _label("hard_negative", "SHADOW", "directory_semantic", 0.7, False)
    if normalized & {"stain", "stains"}:
        return _label("hard_negative", "STAIN", "directory_semantic", 0.7, False)
    if normalized & {"joint", "joints"}:
        return _label("hard_negative", "JOINT", "directory_semantic", 0.7, False)
    if "brick" in normalized and "joint" in path_text:
        return _label("hard_negative", "BRICK_JOINT", "directory_semantic", 0.7, False)
    if normalized & {"normal", "negative", "noncrack", "non_crack", "non_cracked", "no_crack", "non-crack", "no-crack"}:
        return _label("hard_negative", "OTHER", "directory_semantic", 0.6, True)
    if "BLUR" in quality_flags:
        return _label("low_quality", "BLUR", "quality_rule", 0.6, True)
    if "OVEREXPOSED" in quality_flags:
        return _label("low_quality", "OVEREXPOSED", "quality_rule", 0.6, True)
    if "UNDEREXPOSED" in quality_flags:
        return _label("low_quality", "UNDEREXPOSED", "quality_rule", 0.6, True)
    if "TOO_SMALL" in quality_flags:
        return _label("low_quality", "OTHER", "quality_rule", 0.6, True)
    if normalized & {"person", "people", "vehicle", "vehicles", "document", "documents", "indoor"}:
        return _label("not_applicable", "OTHER", "directory_semantic", 0.7, True)
    return _label("not_applicable", "UNKNOWN", "unknown", 0.0, True)


def _label(primary: str, secondary: str, source: str, confidence: float, review: bool) -> dict[str, Any]:
    return {
        "primary_category": primary,
        "secondary_label": secondary,
        "label_source": source,
        "label_confidence": confidence,
        "needs_manual_review": review,
    }


def _quality_flags(
    width: int,
    height: int,
    file_size: int,
    blur_score: float,
    brightness_mean: float,
    over_ratio: float,
    under_ratio: float,
    thresholds: QualityThresholds,
) -> list[str]:
    flags: list[str] = []
    if width < thresholds.min_width or height < thresholds.min_height:
        flags.append("TOO_SMALL")
    if file_size < thresholds.min_file_size_bytes:
        flags.append("VERY_SMALL_FILE")
    aspect = max(width / height, height / width) if width and height else math.inf
    if aspect > thresholds.max_aspect_ratio:
        flags.append("EXTREME_ASPECT_RATIO")
    if blur_score < thresholds.min_blur_score:
        flags.append("BLUR")
    if brightness_mean < thresholds.low_brightness_mean or under_ratio > thresholds.max_underexposure_ratio:
        flags.append("UNDEREXPOSED")
    if brightness_mean > thresholds.high_brightness_mean or over_ratio > thresholds.max_overexposure_ratio:
        flags.append("OVEREXPOSED")
    return sorted(set(flags))


def _pixel_data(image: Image.Image) -> list[int]:
    if hasattr(image, "get_flattened_data"):
        return list(image.get_flattened_data())
    return list(image.getdata())


def _dhash(gray: Image.Image) -> str:
    small = gray.resize((9, 8), Image.Resampling.LANCZOS)
    pixels = _pixel_data(small)
    value = 0
    for row in range(8):
        for col in range(8):
            left = pixels[row * 9 + col]
            right = pixels[row * 9 + col + 1]
            value = (value << 1) | int(left > right)
    return f"{value:016x}"


def _brightness_metrics(gray: Image.Image, thresholds: QualityThresholds) -> tuple[float, float, float]:
    sample = gray.copy()
    sample.thumbnail((256, 256))
    pixels = _pixel_data(sample)
    if not pixels:
        return 0.0, 0.0, 0.0
    total = len(pixels)
    mean = sum(pixels) / total
    over = sum(1 for pixel in pixels if pixel >= thresholds.overexposure_pixel_threshold) / total
    under = sum(1 for pixel in pixels if pixel <= thresholds.underexposure_pixel_threshold) / total
    return mean, over, under


def _laplacian_variance(gray: Image.Image) -> float:
    sample = gray.copy()
    sample.thumbnail((64, 64))
    width, height = sample.size
    if width < 3 or height < 3:
        return 0.0
    pixels = _pixel_data(sample)
    values: list[float] = []
    for y in range(1, height - 1):
        for x in range(1, width - 1):
            center = pixels[y * width + x]
            lap = (
                4 * center
                - pixels[y * width + x - 1]
                - pixels[y * width + x + 1]
                - pixels[(y - 1) * width + x]
                - pixels[(y + 1) * width + x]
            )
            values.append(float(lap))
    if not values:
        return 0.0
    mean = sum(values) / len(values)
    return sum((value - mean) ** 2 for value in values) / len(values)


def _near_bucket_key(record: ImageRecord) -> tuple[int, int, int, str]:
    brightness_bucket = round(record.brightness_mean / 5.0)
    return (record.width, record.height, brightness_bucket, record.perceptual_hash[:4])


def _find_near_duplicate(records: list[ImageRecord], record: ImageRecord, threshold: int) -> ImageRecord | None:
    for existing in records:
        same_size = existing.width == record.width and existing.height == record.height
        similar_brightness = abs(existing.brightness_mean - record.brightness_mean) <= 5.0
        if same_size and similar_brightness and _hamming_hex(existing.perceptual_hash, record.perceptual_hash) <= threshold:
            return existing
    return None


def _hamming_hex(left: str, right: str) -> int:
    return (int(left, 16) ^ int(right, 16)).bit_count()


def _duplicate_record(kept: ImageRecord, duplicate: ImageRecord, reason: str, distance: str) -> DuplicateRecord:
    return DuplicateRecord(
        kept_sample_id=kept.sample_id,
        kept_source_relative_path=kept.candidate.source_relative_path,
        duplicate_source_relative_path=duplicate.candidate.source_relative_path,
        reason=reason,
        kept_sha256=kept.sha256,
        duplicate_sha256=duplicate.sha256,
        kept_perceptual_hash=kept.perceptual_hash,
        duplicate_perceptual_hash=duplicate.perceptual_hash,
        hamming_distance=distance,
    )


def _manifest_row(record: ImageRecord, selected: set[str] | list[ImageRecord], output_relative_path: str) -> dict[str, str]:
    selected_ids = {item.sample_id for item in selected} if isinstance(selected, list) else selected
    return {
        "sample_id": record.sample_id,
        "output_relative_path": output_relative_path,
        "source_relative_path": record.candidate.source_relative_path,
        "source_archive": record.candidate.source_archive,
        "source_dataset": record.candidate.source_dataset,
        "sha256": record.sha256,
        "perceptual_hash": record.perceptual_hash,
        "width": str(record.width),
        "height": str(record.height),
        "file_size_bytes": str(record.file_size_bytes),
        "primary_category": record.primary_category,
        "secondary_label": record.secondary_label,
        "label_source": record.label_source,
        "label_confidence": f"{record.label_confidence:.2f}",
        "needs_manual_review": _bool(record.needs_manual_review),
        "privacy_status": record.privacy_status,
        "quality_flags": ";".join(record.quality_flags),
        "blur_score": f"{record.blur_score:.4f}",
        "brightness_mean": f"{record.brightness_mean:.4f}",
        "overexposure_ratio": f"{record.overexposure_ratio:.6f}",
        "underexposure_ratio": f"{record.underexposure_ratio:.6f}",
        "selected_for_fixed_diagnostic": _bool(record.sample_id in selected_ids),
        "split": "fixed_diagnostic" if record.sample_id in selected_ids else "",
        "notes": "; ".join(record.notes),
    }


def _privacy_row(record: ImageRecord, output_relative_path: str) -> dict[str, str]:
    return {
        "sample_id": record.sample_id,
        "source_relative_path": record.candidate.source_relative_path,
        "output_relative_path": output_relative_path,
        "privacy_status": record.privacy_status,
        "review_reason": "USER_ASSERTED_NO_SENSITIVE_CONTENT",
    }


def _scaled_targets(target_count: int) -> dict[str, int]:
    if target_count >= sum(DEFAULT_CATEGORY_TARGETS.values()):
        return dict(DEFAULT_CATEGORY_TARGETS)
    categories = list(DEFAULT_CATEGORY_TARGETS)
    scaled: dict[str, int] = {}
    remaining = target_count
    total = sum(DEFAULT_CATEGORY_TARGETS.values())
    fractions: list[tuple[float, str]] = []
    for category, default in DEFAULT_CATEGORY_TARGETS.items():
        raw = target_count * default / total
        base = math.floor(raw)
        scaled[category] = base
        remaining -= base
        fractions.append((raw - base, category))
    for _, category in sorted(fractions, reverse=True):
        if remaining <= 0:
            break
        scaled[category] += 1
        remaining -= 1
    for category in categories:
        scaled.setdefault(category, 0)
    return scaled


def _extract_archive(path: Path, output_dir: Path) -> list[tuple[Path, str]]:
    output_dir.mkdir(parents=True, exist_ok=True)
    extracted: list[tuple[Path, str]] = []
    suffix = _normalized_suffix(path)
    if suffix == ".zip":
        with zipfile.ZipFile(path) as archive:
            for info in archive.infolist():
                if info.is_dir():
                    continue
                name = _safe_archive_member(info.filename)
                target = output_dir / name
                target.parent.mkdir(parents=True, exist_ok=True)
                with archive.open(info) as source, target.open("wb") as dest:
                    shutil.copyfileobj(source, dest)
                extracted.append((target, name))
    else:
        with tarfile.open(path) as archive:
            for member in archive.getmembers():
                if not member.isfile():
                    continue
                name = _safe_archive_member(member.name)
                target = output_dir / name
                target.parent.mkdir(parents=True, exist_ok=True)
                source = archive.extractfile(member)
                if source is None:
                    continue
                with source, target.open("wb") as dest:
                    shutil.copyfileobj(source, dest)
                extracted.append((target, name))
    return extracted


def _safe_archive_member(name: str) -> str:
    normalized = PurePosixPath(name.replace("\\", "/"))
    parts = [part for part in normalized.parts if part not in {"", "."}]
    if not parts or any(part == ".." for part in parts):
        raise ValueError(f"unsafe archive member path: {name}")
    return PurePosixPath(*parts).as_posix()


def _write_output_copy(source: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, destination)


def _safe_output_extension(suffix: str) -> str:
    suffix = suffix.lower()
    if suffix in {".jpg", ".jpeg", ".png", ".webp", ".bmp", ".tif", ".tiff"}:
        return ".jpg" if suffix == ".jpeg" else suffix
    return ".jpg"


def _expected_output_relative_path(record: ImageRecord) -> str:
    extension = _safe_output_extension(record.candidate.local_path.suffix)
    return (Path("fixed_diagnostic") / record.primary_category / f"{record.sample_id}{extension}").as_posix()


def _image_format_for_suffix(suffix: str) -> str:
    return {
        ".jpg": "JPEG",
        ".jpeg": "JPEG",
        ".png": "PNG",
        ".webp": "WEBP",
        ".bmp": "BMP",
        ".tif": "TIFF",
        ".tiff": "TIFF",
    }.get(suffix.lower(), "JPEG")


def _write_csv(path: Path, fieldnames: list[str], rows: list[dict[str, Any]]) -> str:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)
    return str(path)


def _write_json(path: Path, payload: Any) -> str:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return str(path)


def _write_jsonl(path: Path, rows: list[dict[str, Any]]) -> str:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")
    return str(path)


def _write_text(path: Path, text: str) -> str:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    return str(path)


def _write_quality_report(
    path: Path,
    config: BuildConfig,
    records: list[ImageRecord],
    selected: list[ImageRecord],
    duplicates: list[DuplicateRecord],
    exclusions: list[ExclusionRecord],
) -> str:
    payload = {
        "schemaVersion": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "configuration": {
            "seed": config.seed,
            "targetCount": config.target_count,
            "nearDuplicateHammingThreshold": config.near_duplicate_hamming_threshold,
            "thresholds": asdict(config.thresholds),
        },
        "counts": {
            "candidateImages": len(records),
            "uniqueImages": len(records),
            "selectedImages": len(selected),
            "duplicates": len(duplicates),
            "exclusions": len(exclusions),
            "privacyNeedsManualReview": sum(1 for item in records if item.privacy_status == "NEEDS_MANUAL_REVIEW"),
            "privacyAssertedClean": sum(1 for item in records if item.privacy_status == "CLEAN"),
        },
        "qualityFlagCounts": _quality_flag_counts(records),
        "categoryCounts": _category_counts(records),
    }
    return _write_json(path, payload)


def _write_labels_template(path: Path, manifest_rows: list[dict[str, str]]) -> str:
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("[Content_Types].xml", _xlsx_content_types())
        archive.writestr("_rels/.rels", _xlsx_root_rels())
        archive.writestr("xl/workbook.xml", _xlsx_workbook())
        archive.writestr("xl/_rels/workbook.xml.rels", _xlsx_workbook_rels())
        archive.writestr("xl/styles.xml", _xlsx_styles())
        archive.writestr("xl/worksheets/sheet1.xml", _xlsx_sheet("样本标签", [MANIFEST_FIELDS] + [[row[field] for field in MANIFEST_FIELDS] for row in manifest_rows], highlight_manual=True))
        archive.writestr("xl/worksheets/sheet2.xml", _xlsx_sheet("标签字典", _dictionary_rows(), highlight_manual=False))
        archive.writestr("xl/worksheets/sheet3.xml", _xlsx_sheet("操作说明", _instruction_rows(), highlight_manual=False))
    return str(path)


def _xlsx_content_types() -> str:
    return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""


def _xlsx_root_rels() -> str:
    return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""


def _xlsx_workbook() -> str:
    return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets>
<sheet name="样本标签" sheetId="1" r:id="rId1"/>
<sheet name="标签字典" sheetId="2" r:id="rId2"/>
<sheet name="操作说明" sheetId="3" r:id="rId3"/>
</sheets>
</workbook>"""


def _xlsx_workbook_rels() -> str:
    return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
<Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""


def _xlsx_styles() -> str:
    return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFFFF2CC"/><bgColor indexed="64"/></patternFill></fill></fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="0" fillId="2" borderId="0" xfId="0" applyFill="1"/></cellXfs>
</styleSheet>"""


def _xlsx_sheet(name: str, rows: list[list[str]], highlight_manual: bool) -> str:
    sheet_rows: list[str] = []
    for row_index, row in enumerate(rows, start=1):
        cells: list[str] = []
        manual = highlight_manual and row_index > 1 and (
            _cell_value(rows[0], row, "needs_manual_review") == "true"
            or _cell_value(rows[0], row, "privacy_status") == "NEEDS_MANUAL_REVIEW"
        )
        style = ' s="1"' if manual else ""
        for col_index, value in enumerate(row, start=1):
            ref = f"{_column_name(col_index)}{row_index}"
            cells.append(f'<c r="{ref}" t="inlineStr"{style}><is><t>{escape(str(value))}</t></is></c>')
        sheet_rows.append(f'<row r="{row_index}">{"".join(cells)}</row>')
    auto_filter = f'<autoFilter ref="A1:{_column_name(len(rows[0]))}{max(len(rows), 1)}"/>' if rows else ""
    validations = ""
    if name == "样本标签":
        validations = (
            '<dataValidations count="3">'
            '<dataValidation type="list" allowBlank="1" showErrorMessage="1" sqref="K2:K1048576"><formula1>"'
            + ",".join(PRIMARY_CATEGORIES)
            + '"</formula1></dataValidation>'
            '<dataValidation type="list" allowBlank="1" showErrorMessage="1" sqref="L2:L1048576"><formula1>"'
            + ",".join(SECONDARY_LABELS)
            + '"</formula1></dataValidation>'
            '<dataValidation type="list" allowBlank="1" showErrorMessage="1" sqref="P2:P1048576"><formula1>"CLEAN,NEEDS_MANUAL_REVIEW,EXCLUDED"</formula1></dataValidation>'
            '</dataValidations>'
        )
    return f"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>
<sheetData>{''.join(sheet_rows)}</sheetData>
{auto_filter}
{validations}
</worksheet>"""


def _cell_value(headers: list[str], row: list[str], field: str) -> str:
    try:
        return row[headers.index(field)]
    except (ValueError, IndexError):
        return ""


def _dictionary_rows() -> list[list[str]]:
    rows = [["字段", "取值", "说明"]]
    rows.extend(["primary_category", value, "固定诊断集一级类别"] for value in PRIMARY_CATEGORIES)
    rows.extend(["secondary_label", value, "二级标签或 UNKNOWN"] for value in SECONDARY_LABELS)
    rows.extend(["privacy_status", value, "隐私审核状态"] for value in ["CLEAN", "NEEDS_MANUAL_REVIEW", "EXCLUDED"])
    return rows


def _instruction_rows() -> list[list[str]]:
    return [
        ["说明"],
        ["仅在人工确认后把 UNKNOWN 或 needs_manual_review=true 修改为确定标签结论。"],
        ["不要把模型预测结果反向填写为真实标签。"],
        ["privacy_status=CLEAN 来自使用方对本地数据集无敏感内容的断言，不代表脚本做过自动隐私检测。"],
    ]


def _column_name(index: int) -> str:
    name = ""
    while index:
        index, remainder = divmod(index - 1, 26)
        name = chr(65 + remainder) + name
    return name


def _label_mapping_yaml() -> str:
    return """schema_version: 1
mapping_policy: >
  Only reliable directory semantics and objective quality rules are used.
  File names and model predictions are not used as ground-truth labels.
primary_categories:
  obvious_defect: 明显病害
  difficult_defect: 轻微、远距离、低对比度或复杂病害
  hard_negative: 阴影、污渍、砖缝、施工缝、装饰线、植物、电线等困难负样本
  low_quality: 模糊、过曝、欠曝、遮挡、分辨率过低、距离过远
  not_applicable: 与建筑表面病害初筛不适用或标签未知
directory_rules:
  crack: [obvious_defect, CRACK]
  positive: [obvious_defect, CRACK]
  spalling: [obvious_defect, SPALLING]
  seepage: [obvious_defect, SEEPAGE]
  exposed_rebar: [obvious_defect, EXPOSED_REBAR]
  corrosion: [obvious_defect, CORROSION]
  hole: [obvious_defect, HOLE]
  shadow: [hard_negative, SHADOW]
  stain: [hard_negative, STAIN]
  joint: [hard_negative, JOINT]
  brick_joint: [hard_negative, BRICK_JOINT]
  negative: [hard_negative, OTHER]
  non_cracked: [hard_negative, OTHER]
quality_rules:
  BLUR: [low_quality, BLUR]
  OVEREXPOSED: [low_quality, OVEREXPOSED]
  UNDEREXPOSED: [low_quality, UNDEREXPOSED]
  TOO_SMALL: [low_quality, OTHER]
unknown_policy:
  primary_category: not_applicable
  secondary_label: UNKNOWN
  needs_manual_review: true
privacy_policy:
  source_assertion: USER_ASSERTED_NO_SENSITIVE_CONTENT
  automatic_privacy_detection: false
  automatic_desensitization: false
  default_privacy_status: CLEAN
"""


def _readme_text(
    config: BuildConfig,
    records: list[ImageRecord],
    selected: list[ImageRecord],
    duplicates: list[DuplicateRecord],
    exclusions: list[ExclusionRecord],
) -> str:
    return f"""# Phase 7 Validation Dataset

This directory is generated from local `downloads/` resources for fixed
diagnostic validation of Dify, Spring AI, FastAPI, image applicability checks,
surface-defect triage, hard negatives, low-quality handling, structured output,
and manual review workflows.

- Seed: `{config.seed}`
- Target count: `{config.target_count}`
- Unique candidate images: `{len(records)}`
- Selected fixed diagnostic images: `{len(selected)}`
- Duplicates excluded: `{len(duplicates)}`
- Exclusions: `{len(exclusions)}`
- Privacy status: `CLEAN`, based on the dataset steward assertion that this
  local validation dataset contains no sensitive content.
- Automatic privacy detection: not performed.
- Automatic desensitization: not performed; fixed images are byte-for-byte copies
  of source candidates.

The generated files are ignored by Git through the repository-level
`downloads/` rule. Keep reviewing uncertain labels before treating them as
reference truth.
"""


def _dataset_card_text(
    config: BuildConfig,
    records: list[ImageRecord],
    selected: list[ImageRecord],
    duplicates: list[DuplicateRecord],
    exclusions: list[ExclusionRecord],
) -> str:
    return f"""# 第七阶段固定诊断集数据卡

## 用途

本数据集用于流程验证和结构化输出稳定性检查，不是训练集，也不能证明模型泛化能力。

## 构建边界

- 输入目录：`{config.input_dir}`
- 输出目录：`{config.output_dir}`
- 随机种子：`{config.seed}`
- 固定样本目标：`{config.target_count}`
- 不修改、移动、重命名或删除原始文件。
- 压缩包仅解压到临时目录。
- 输出副本按字节复制，保留源图片元数据。

## 统计

- 候选唯一图片：{len(records)}
- 固定诊断图片：{len(selected)}
- 重复排除：{len(duplicates)}
- 损坏或不支持排除：{len(exclusions)}
- 隐私状态为 CLEAN 的图片：{sum(1 for item in records if item.privacy_status == "CLEAN")}
- 自动隐私检测：未执行
- 自动脱敏处理：未执行

## 限制

`privacy_status=CLEAN` 来自使用方对本地验证集不含敏感内容的断言，不代表脚本执行过人脸、车牌、门牌或证件检测。标签只来自目录语义或客观质量规则，未知项保留为 `UNKNOWN` 并进入人工复核队列。

> 系统结果仅用于风险筛查与辅助决策，不作为正式房屋安全鉴定结论。对于高风险、低置信度或资料完整度不足的结果，应安排人工复核或第三方专业检测。
"""


def _build_report_text(
    config: BuildConfig,
    records: list[ImageRecord],
    selected: list[ImageRecord],
    duplicates: list[DuplicateRecord],
    exclusions: list[ExclusionRecord],
) -> str:
    gaps = []
    selected_counts = _category_counts(selected)
    for category, target in DEFAULT_CATEGORY_TARGETS.items():
        actual = selected_counts.get(category, 0)
        if actual < target:
            gaps.append(f"- `{category}` 缺口：目标 {target}，实际 {actual}")
    gap_text = "\n".join(gaps) if gaps else "- 固定诊断集达到默认类别建议数量。"
    fixed_list = "\n".join(
        f"- `{item.sample_id}` `{item.primary_category}` `{item.secondary_label}` `{item.candidate.source_relative_path}`"
        for item in selected
    )
    return f"""# 第七阶段固定诊断集构建报告

## 构建参数

- 输入目录：`{config.input_dir}`
- 输出目录：`{config.output_dir}`
- 随机种子：`{config.seed}`
- 目标数量：`{config.target_count}`
- 近重复阈值：`{config.near_duplicate_hamming_threshold}`

## 结果

- 去重后可用图片：{len(records)}
- 固定诊断集：{len(selected)}
- 重复排除：{len(duplicates)}
- 排除项：{len(exclusions)}
- 隐私状态 CLEAN：{sum(1 for item in records if item.privacy_status == "CLEAN")}
- 自动隐私检测：未执行
- 自动脱敏处理：未执行

## 类别缺口

{gap_text}

## 固定诊断集清单

{fixed_list if fixed_list else "- 无固定诊断样本。"}

## 人工事项

- `privacy_review.csv` 记录使用方无敏感内容断言和 CLEAN 状态。
- 人工修正 `labels_template.xlsx` 中 `UNKNOWN` 或 `needs_manual_review=true` 的标签。
"""


def _class_distribution_rows(records: list[ImageRecord], selected: set[str]) -> list[dict[str, str]]:
    rows = []
    for category in PRIMARY_CATEGORIES:
        category_records = [item for item in records if item.primary_category == category]
        rows.append({
            "primary_category": category,
            "count": str(len(category_records)),
            "selected_count": str(sum(1 for item in category_records if item.sample_id in selected)),
        })
    return rows


def _source_distribution_rows(records: list[ImageRecord], selected: set[str]) -> list[dict[str, str]]:
    datasets = sorted({item.candidate.source_dataset for item in records})
    return [
        {
            "source_dataset": dataset,
            "count": str(sum(1 for item in records if item.candidate.source_dataset == dataset)),
            "selected_count": str(sum(1 for item in records if item.candidate.source_dataset == dataset and item.sample_id in selected)),
        }
        for dataset in datasets
    ]


def _quality_flag_counts(records: list[ImageRecord]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for record in records:
        for flag in record.quality_flags:
            counts[flag] = counts.get(flag, 0) + 1
    return dict(sorted(counts.items()))


def _category_counts(records: list[ImageRecord]) -> dict[str, int]:
    return {category: sum(1 for item in records if item.primary_category == category) for category in PRIMARY_CATEGORIES}


def _looks_like_label_or_mask_image(source_relative_path: str) -> bool:
    inner = source_relative_path.split("!/", 1)[-1]
    relative = PurePosixPath(inner)
    parts = {_normalize_token(part) for part in relative.parent.parts}
    if parts & LABEL_OR_MASK_DIRECTORY_KEYWORDS:
        return True
    stem = _normalize_token(relative.stem)
    filename_tokens = set(stem.split("_"))
    if filename_tokens & {"mask", "masks", "label", "labels", "annotation", "annotations", "gt", "lab"}:
        return True
    return stem.endswith(("_mask", "_masks", "_label", "_labels", "_lab", "_gt"))


def _normalized_suffix(path: Path) -> str:
    name = path.name.lower()
    if name.endswith(".tar.gz"):
        return ".tar.gz"
    return path.suffix.lower()


def _relative(root: Path, path: Path) -> str:
    return path.resolve().relative_to(root.resolve()).as_posix()


def _source_dataset(source_relative_path: str) -> str:
    archive_path = source_relative_path.split("!/", 1)[0]
    parts = PurePosixPath(archive_path).parts
    if not parts:
        return "downloads"
    if len(parts) >= 2 and parts[0] in {"kaggle", "hf", "mendeley", "zenodo"}:
        return f"{parts[0]}/{parts[1]}"
    return parts[0]


def _is_relative_to(path: Path, parent: Path) -> bool:
    try:
        path.resolve().relative_to(parent.resolve())
        return True
    except ValueError:
        return False


def _normalize_token(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", value.lower()).strip("_")


def _bool(value: bool) -> str:
    return "true" if value else "false"


def _load_config(path: Path | None) -> dict[str, Any]:
    if path is None:
        return {}
    text = path.read_text(encoding="utf-8")
    if path.suffix.lower() == ".json":
        payload = json.loads(text)
        if not isinstance(payload, dict):
            raise ValueError("--config JSON must contain an object")
        return payload
    payload: dict[str, Any] = {}
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or ":" not in stripped:
            continue
        key, value = stripped.split(":", 1)
        payload[key.strip()] = _parse_scalar(value.strip())
    return payload


def _parse_scalar(value: str) -> Any:
    if value.lower() in {"true", "false"}:
        return value.lower() == "true"
    try:
        if "." in value:
            return float(value)
        return int(value)
    except ValueError:
        return value.strip("'\"")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build phase-7 fixed validation dataset from local downloads.")
    parser.add_argument("--input", type=Path, required=True, dest="input_dir")
    parser.add_argument("--output", type=Path, required=True, dest="output_dir")
    parser.add_argument("--seed", type=int, default=SEED)
    parser.add_argument("--target-count", type=int, default=30)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--config", type=Path, dest="config_path")
    return parser.parse_args()


def config_from_args(args: argparse.Namespace) -> BuildConfig:
    overrides = _load_config(args.config_path)
    threshold_overrides = overrides.get("thresholds", {}) if isinstance(overrides.get("thresholds"), dict) else {}
    flat_thresholds = {key: value for key, value in overrides.items() if key in QualityThresholds.__dataclass_fields__}
    thresholds = QualityThresholds(**{**flat_thresholds, **threshold_overrides})
    return BuildConfig(
        input_dir=args.input_dir,
        output_dir=args.output_dir,
        seed=args.seed,
        target_count=args.target_count,
        dry_run=args.dry_run,
        force=args.force,
        config_path=args.config_path,
        near_duplicate_hamming_threshold=int(overrides.get("near_duplicate_hamming_threshold", 6)),
        thresholds=thresholds,
    )


def main() -> int:
    args = parse_args()
    try:
        summary = build_dataset(config_from_args(args))
    except Exception as exc:
        print(f"phase7 validation dataset build failed: {exc}")
        return 1
    print(json.dumps(asdict(summary), ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

