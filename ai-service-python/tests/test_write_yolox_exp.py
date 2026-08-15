from pathlib import Path

from tools.write_yolox_exp import write_yolox_exp


def test_write_yolox_exp_fixes_seven_classes_and_640_input(tmp_path: Path):
    target = tmp_path / "urban_safe_yolox_s.py"
    write_yolox_exp(
        target,
        dataset_root=tmp_path / "dataset",
        output_dir=tmp_path / "runs",
        max_epoch=80,
        eval_interval=5,
        num_workers=4,
        seed=42,
    )

    text = target.read_text(encoding="utf-8")
    assert "self.num_classes = 7" in text
    assert "self.input_size = (640, 640)" in text
    assert "self.test_size = (640, 640)" in text
    assert 'self.train_ann = "instances_train2017.json"' in text
    assert 'self.val_ann = "instances_val2017.json"' in text
    assert "self.max_epoch = 80" in text
    assert "self.eval_interval = 5" in text
