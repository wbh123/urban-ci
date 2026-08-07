"""裂缝分割训练与独立评估使用的指标。"""
from __future__ import annotations
from dataclasses import dataclass
import torch

@dataclass
class SegmentationTotals:
    intersection: float = 0.0
    prediction_pixels: float = 0.0
    target_pixels: float = 0.0
    union: float = 0.0
    positive_images: int = 0
    detected_positive_images: int = 0
    negative_images: int = 0
    false_positive_images: int = 0

    def update(self, probabilities: torch.Tensor, targets: torch.Tensor, *, threshold: float) -> None:
        predictions=probabilities>=threshold; truth=targets>=0.5
        self.intersection+=float((predictions&truth).sum().item())
        self.prediction_pixels+=float(predictions.sum().item()); self.target_pixels+=float(truth.sum().item())
        self.union+=float((predictions|truth).sum().item())
        for predicted_image,truth_image in zip(predictions.flatten(1),truth.flatten(1),strict=True):
            has_truth=bool(truth_image.any().item()); has_prediction=bool(predicted_image.any().item())
            if has_truth:
                self.positive_images+=1; self.detected_positive_images+=int(has_prediction)
            else:
                self.negative_images+=1; self.false_positive_images+=int(has_prediction)

    def compute(self)->dict[str,float]:
        denominator=self.prediction_pixels+self.target_pixels
        return {"pixelF1":2.0*self.intersection/denominator if denominator else 1.0,
                "iou":self.intersection/self.union if self.union else 1.0,
                "imageRecall":self.detected_positive_images/self.positive_images if self.positive_images else 1.0,
                "falsePositiveImageRate":self.false_positive_images/self.negative_images if self.negative_images else 0.0}

def dice_bce_loss(logits:torch.Tensor,targets:torch.Tensor)->torch.Tensor:
    bce=torch.nn.functional.binary_cross_entropy_with_logits(logits,targets)
    probabilities=torch.sigmoid(logits); dims=tuple(range(1,probabilities.ndim))
    intersection=(probabilities*targets).sum(dim=dims); denominator=probabilities.sum(dim=dims)+targets.sum(dim=dims)
    dice=(2.0*intersection+1.0)/(denominator+1.0)
    return 0.5*bce+0.5*(1.0-dice.mean())
