#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import random
import struct
import sys
import zlib
from pathlib import Path

WIDTH = 640
HEIGHT = 360
ISSUES = (
    ("CRACK", "crack"),
    ("WATER_LEAKAGE", "water-leakage"),
    ("SURFACE_FALLING", "surface-falling"),
    ("DEFORMATION", "deformation"),
)


def _chunk(kind: bytes, payload: bytes) -> bytes:
    body = kind + payload
    return struct.pack(">I", len(payload)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)


def encode_png(width: int, height: int, pixels: bytearray) -> bytes:
    stride = width * 3
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        start = y * stride
        raw.extend(pixels[start : start + stride])
    return (
        b"\x89PNG\r\n\x1a\n"
        + _chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
        + _chunk(b"IDAT", zlib.compress(bytes(raw), level=7))
        + _chunk(b"IEND", b"")
    )


def _set_pixel(pixels: bytearray, x: int, y: int, rgb: tuple[int, int, int]) -> None:
    if not (0 <= x < WIDTH and 0 <= y < HEIGHT):
        return
    index = (y * WIDTH + x) * 3
    pixels[index : index + 3] = bytes(rgb)


def _fill_rect(pixels: bytearray, x0: int, y0: int, x1: int, y1: int, rgb: tuple[int, int, int]) -> None:
    x0, x1 = sorted((max(0, x0), min(WIDTH - 1, x1)))
    y0, y1 = sorted((max(0, y0), min(HEIGHT - 1, y1)))
    row = bytes(rgb) * max(0, x1 - x0 + 1)
    for y in range(y0, y1 + 1):
        start = (y * WIDTH + x0) * 3
        pixels[start : start + len(row)] = row


def _line(
    pixels: bytearray,
    x0: int,
    y0: int,
    x1: int,
    y1: int,
    rgb: tuple[int, int, int],
    thickness: int = 1,
) -> None:
    dx = abs(x1 - x0)
    sx = 1 if x0 < x1 else -1
    dy = -abs(y1 - y0)
    sy = 1 if y0 < y1 else -1
    err = dx + dy
    while True:
        radius = max(0, thickness // 2)
        for oy in range(-radius, radius + 1):
            for ox in range(-radius, radius + 1):
                _set_pixel(pixels, x0 + ox, y0 + oy, rgb)
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def _base_wall(seed: int) -> bytearray:
    rng = random.Random(seed)
    base = 205 + seed % 18
    pixels = bytearray(WIDTH * HEIGHT * 3)
    for y in range(HEIGHT):
        shade = max(150, min(235, base - y // 24 + rng.randint(-2, 2)))
        row = bytes((shade, max(0, shade - 4), max(0, shade - 9))) * WIDTH
        start = y * WIDTH * 3
        pixels[start : start + len(row)] = row
    for x in (80, 210, 340, 470, 600):
        _line(pixels, x, 0, x, HEIGHT - 1, (155, 154, 150), 2)
    for y in (90, 180, 270):
        _line(pixels, 0, y, WIDTH - 1, y, (164, 162, 157), 2)
    for row in range(3):
        for col in range(5):
            x = 28 + col * 128
            y = 24 + row * 92
            _fill_rect(pixels, x, y, x + 44, y + 48, (116, 135, 143))
            _fill_rect(pixels, x + 4, y + 4, x + 40, y + 44, (150, 170, 176))
    return pixels


def _draw_crack(pixels: bytearray, rng: random.Random) -> None:
    x, y = rng.randint(230, 410), rng.randint(70, 120)
    for _ in range(10):
        nx = x + rng.randint(-28, 32)
        ny = y + rng.randint(15, 31)
        _line(pixels, x, y, nx, ny, (72, 65, 58), 3)
        if rng.random() < 0.45:
            _line(pixels, x, y, x + rng.randint(-30, 30), y + rng.randint(8, 24), (82, 73, 64), 2)
        x, y = nx, ny


def _draw_water(pixels: bytearray, rng: random.Random) -> None:
    cx, cy = rng.randint(240, 410), rng.randint(100, 180)
    for radius in range(75, 12, -6):
        rgb = (138 + radius // 8, 145 + radius // 10, 132 + radius // 12)
        for y in range(max(0, cy - radius), min(HEIGHT, cy + radius)):
            half = int((radius * radius - (y - cy) * (y - cy)) ** 0.5)
            x0 = max(0, cx - half)
            x1 = min(WIDTH - 1, cx + half)
            if (y + radius) % 3 == 0:
                _fill_rect(pixels, x0, y, x1, y, rgb)
    for offset in (-25, 0, 28):
        _line(pixels, cx + offset, cy + 40, cx + offset + rng.randint(-8, 8), cy + 120, (119, 127, 116), 3)


def _draw_falling(pixels: bytearray, rng: random.Random) -> None:
    cx, cy = rng.randint(220, 420), rng.randint(110, 220)
    points = [
        (cx - 85, cy - 38), (cx - 35, cy - 72), (cx + 45, cy - 55),
        (cx + 92, cy - 8), (cx + 55, cy + 62), (cx - 25, cy + 74), (cx - 88, cy + 28),
    ]
    for y in range(cy - 70, cy + 70):
        span = max(18, 85 - abs(y - cy) // 2)
        _fill_rect(pixels, cx - span, y, cx + span, y, (167, 151, 130))
    for p0, p1 in zip(points, points[1:] + points[:1]):
        _line(pixels, p0[0], p0[1], p1[0], p1[1], (95, 84, 72), 4)
    for _ in range(18):
        x = rng.randint(cx - 100, cx + 100)
        y = rng.randint(cy + 70, min(HEIGHT - 6, cy + 130))
        _fill_rect(pixels, x, y, x + rng.randint(3, 9), y + rng.randint(2, 7), (142, 126, 105))


def _draw_deformation(pixels: bytearray, rng: random.Random) -> None:
    start_x = rng.randint(170, 240)
    for index in range(8):
        y = 80 + index * 28
        bend = int(24 * ((index - 3.5) / 3.5) ** 2)
        _line(pixels, start_x + bend, y, start_x + 260 - bend, y + rng.randint(-4, 4), (112, 100, 88), 4)
    _line(pixels, start_x, 70, start_x + 32, 310, (103, 92, 82), 5)
    _line(pixels, start_x + 260, 70, start_x + 225, 310, (103, 92, 82), 5)


def render_issue(issue_code: str, variant: int) -> bytes:
    seed = int(hashlib.sha256(f"{issue_code}:{variant}:urban-safe".encode()).hexdigest()[:8], 16)
    rng = random.Random(seed)
    pixels = _base_wall(seed)
    if issue_code == "CRACK":
        _draw_crack(pixels, rng)
    elif issue_code == "WATER_LEAKAGE":
        _draw_water(pixels, rng)
    elif issue_code == "SURFACE_FALLING":
        _draw_falling(pixels, rng)
    elif issue_code == "DEFORMATION":
        _draw_deformation(pixels, rng)
    else:
        raise ValueError(f"未知演示病害类型：{issue_code}")
    return encode_png(WIDTH, HEIGHT, pixels)


def generate_assets(output_dir: Path, variants_per_issue: int = 4) -> dict[str, object]:
    output_dir.mkdir(parents=True, exist_ok=True)
    assets: list[dict[str, object]] = []
    for issue_code, slug in ISSUES:
        for variant in range(1, variants_per_issue + 1):
            filename = f"{slug}-{variant:02d}.png"
            data = render_issue(issue_code, variant)
            path = output_dir / filename
            path.write_bytes(data)
            assets.append({
                "issueType": issue_code,
                "variant": variant,
                "filename": filename,
                "objectKey": f"showcase/inspection/{filename}",
                "sha256": hashlib.sha256(data).hexdigest(),
                "size": len(data),
                "width": WIDTH,
                "height": HEIGHT,
                "contentType": "image/png",
                "syntheticImage": True,
            })
    manifest: dict[str, object] = {
        "schemaVersion": 1,
        "generator": "prepare-showcase-assets.py",
        "assets": assets,
    }
    (output_dir / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    return manifest


def main(argv: list[str]) -> int:
    output_dir = Path(argv[1]) if len(argv) > 1 else Path("data/showcase-assets/inspection")
    variants = int(argv[2]) if len(argv) > 2 else 4
    manifest = generate_assets(output_dir, variants)
    print(f"已生成 {len(manifest['assets'])} 张演示巡检 PNG：{output_dir}", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
