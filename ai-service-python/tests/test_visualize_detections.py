from types import SimpleNamespace

from PIL import Image

from tools.visualize_detections import render_annotated_image, save_annotated_image


def detection(*, segmentation=None):
    return SimpleNamespace(
        classCode="CRACK",
        confidence=0.91,
        trustLevel="HIGH",
        boundingBox=SimpleNamespace(x=0.2, y=0.2, width=0.6, height=0.6),
        segmentation=segmentation,
    )


def test_polygon_segmentation_is_used_as_irregular_highlight():
    image = Image.new("RGB", (100, 100), "white")
    segmentation = SimpleNamespace(
        type="POLYGON",
        points=[[0.30, 0.30], [0.70, 0.35], [0.55, 0.70], [0.35, 0.60]],
    )

    rendered = render_annotated_image(image, [detection(segmentation=segmentation)], "PRECISION")

    # 多边形内部应发生半透明着色；远离标注的角落保持原图。
    assert rendered.getpixel((50, 50)) != (255, 255, 255)
    assert rendered.getpixel((95, 95)) == (255, 255, 255)


def test_missing_polygon_falls_back_to_bounding_box():
    image = Image.new("RGB", (100, 100), "white")

    rendered = render_annotated_image(image, [detection(segmentation=None)], "FAST")

    # 归一化框左上角约为 (20, 20)，应绘制轮廓。
    assert rendered.getpixel((20, 20)) != (255, 255, 255)


def test_save_annotated_image_creates_parent_directories(tmp_path):
    source = tmp_path / "input.jpg"
    output = tmp_path / "nested" / "precision" / "input.jpg"
    Image.new("RGB", (80, 60), "white").save(source)

    save_annotated_image(source, [detection(segmentation=None)], output, "PRECISION")

    assert output.is_file()
    with Image.open(output) as saved:
        assert saved.size == (80, 60)
