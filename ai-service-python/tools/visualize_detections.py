"""将视觉推理结果绘制到图片副本上。

优先使用已有 SAM2 POLYGON 分割结果进行半透明不规则高亮；仅当没有有效多边形时
回退为轻量矩形框。该模块只依赖 Pillow，不执行任何额外模型推理。
"""
from __future__ import annotations

from pathlib import Path
from typing import Any, Iterable

from PIL import Image, ImageDraw, ImageFont

# (填充 RGB, 轮廓 RGB)。透明度在绘制时统一控制，避免增加额外样式依赖。
CLASS_COLORS: dict[str, tuple[tuple[int, int, int], tuple[int, int, int]]] = {
    "CRACK": ((255, 74, 74), (230, 38, 38)),
    "WATER_STAIN": ((36, 190, 210), (0, 142, 165)),
    "SPALLING": ((255, 178, 54), (224, 126, 0)),
    "EXPOSED_REBAR": ((171, 104, 255), (112, 54, 190)),
    "CORROSION": ((188, 104, 60), (132, 65, 30)),
    "SURFACE_DAMAGE": ((255, 132, 88), (214, 82, 39)),
}
DEFAULT_COLORS = ((255, 210, 64), (200, 145, 0))


def _get(value: Any, name: str, default: Any = None) -> Any:
    if value is None:
        return default
    if isinstance(value, dict):
        return value.get(name, default)
    return getattr(value, name, default)


def _enum_value(value: Any) -> Any:
    return getattr(value, "value", value)


def _bbox_pixels(detection: Any, width: int, height: int) -> tuple[int, int, int, int]:
    box = _get(detection, "boundingBox")
    x = float(_get(box, "x", 0.0))
    y = float(_get(box, "y", 0.0))
    w = float(_get(box, "width", 0.0))
    h = float(_get(box, "height", 0.0))
    x1 = max(0, min(width - 1, int(round(x * width))))
    y1 = max(0, min(height - 1, int(round(y * height))))
    x2 = max(x1 + 1, min(width, int(round((x + w) * width))))
    y2 = max(y1 + 1, min(height, int(round((y + h) * height))))
    return x1, y1, x2, y2


def _polygon_pixels(detection: Any, width: int, height: int) -> list[tuple[int, int]]:
    segmentation = _get(detection, "segmentation")
    if str(_enum_value(_get(segmentation, "type", ""))).upper() != "POLYGON":
        return []
    raw_points = _get(segmentation, "points", []) or []
    points: list[tuple[int, int]] = []
    for point in raw_points:
        if not isinstance(point, (list, tuple)) or len(point) < 2:
            continue
        try:
            x = max(0.0, min(1.0, float(point[0])))
            y = max(0.0, min(1.0, float(point[1])))
        except (TypeError, ValueError):
            continue
        points.append(
            (
                max(0, min(width - 1, int(round(x * (width - 1))))),
                max(0, min(height - 1, int(round(y * (height - 1))))),
            )
        )
    return points if len(points) >= 3 else []


def _label_text(detection: Any) -> str:
    code = str(_get(detection, "classCode", "UNKNOWN")).upper()
    confidence = float(_get(detection, "confidence", 0.0) or 0.0)
    trust = _enum_value(_get(detection, "trustLevel"))
    suffix = f" {str(trust).upper()}" if trust else ""
    return f"{code} {confidence:.2f}{suffix}"


def _draw_label(
    draw: ImageDraw.ImageDraw,
    xy: tuple[int, int],
    text: str,
    color: tuple[int, int, int],
    image_width: int,
) -> None:
    font = ImageFont.load_default()
    x, y = xy
    left, top, right, bottom = draw.textbbox((x, y), text, font=font)
    pad_x, pad_y = 4, 3
    label_width = (right - left) + pad_x * 2
    if x + label_width > image_width:
        x = max(0, image_width - label_width)
        left, top, right, bottom = draw.textbbox((x, y), text, font=font)
    draw.rectangle(
        (left - pad_x, top - pad_y, right + pad_x, bottom + pad_y),
        fill=(color[0], color[1], color[2], 215),
    )
    draw.text((x, y), text, font=font, fill=(255, 255, 255, 255))


def render_annotated_image(
    image: Image.Image,
    detections: Iterable[Any],
    mode_name: str,
) -> Image.Image:
    """返回带检测叠层的 RGB 图片，不修改原图对象。"""

    detection_list = list(detections)
    base = image.convert("RGBA")
    width, height = base.size
    overlay = Image.new("RGBA", base.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay, "RGBA")
    line_width = max(2, int(round(min(width, height) / 260.0)))

    for detection in detection_list:
        code = str(_get(detection, "classCode", "UNKNOWN")).upper()
        fill_rgb, outline_rgb = CLASS_COLORS.get(code, DEFAULT_COLORS)
        polygon = _polygon_pixels(detection, width, height)
        bbox = _bbox_pixels(detection, width, height)

        if polygon:
            # 宽而淡的外沿 + 半透明区域 + 细实线，视觉上更接近“病害区域高亮”。
            closed = polygon + [polygon[0]]
            draw.line(
                closed,
                fill=(outline_rgb[0], outline_rgb[1], outline_rgb[2], 80),
                width=line_width * 3,
                joint="curve",
            )
            draw.polygon(
                polygon,
                fill=(fill_rgb[0], fill_rgb[1], fill_rgb[2], 62),
            )
            draw.line(
                closed,
                fill=(outline_rgb[0], outline_rgb[1], outline_rgb[2], 220),
                width=line_width,
                joint="curve",
            )
            anchor = (min(p[0] for p in polygon), min(p[1] for p in polygon))
        else:
            # 没有 SAM2 多边形时才回退到矩形框，不做大面积填充。
            draw.rectangle(
                bbox,
                outline=(outline_rgb[0], outline_rgb[1], outline_rgb[2], 225),
                width=line_width,
            )
            anchor = (bbox[0], bbox[1])

        label_y = max(4, anchor[1] - 14)
        _draw_label(draw, (max(4, anchor[0]), label_y), _label_text(detection), outline_rgb, width)

    # 左上角只显示模式和数量，避免覆盖过多原图内容。
    header = f"{str(mode_name).upper()} | detections={len(detection_list)}"
    _draw_label(draw, (8, 8), header, (40, 40, 40), width)
    return Image.alpha_composite(base, overlay).convert("RGB")


def save_annotated_image(
    source_path: str | Path,
    detections: Iterable[Any],
    output_path: str | Path,
    mode_name: str,
) -> Path:
    """读取原图、绘制叠层并保存副本。"""

    source = Path(source_path)
    output = Path(output_path)
    output.parent.mkdir(parents=True, exist_ok=True)
    with Image.open(source) as image:
        rendered = render_annotated_image(image, detections, mode_name)
        suffix = output.suffix.lower()
        if suffix in {".jpg", ".jpeg"}:
            rendered.save(output, quality=92, subsampling=0)
        elif suffix == ".webp":
            rendered.save(output, quality=92)
        else:
            rendered.save(output)
    return output
