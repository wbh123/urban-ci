"""用于裂缝语义分割训练、Hugging Face 权重转换和 ONNX 导出的 U-Net。

运行时 FastAPI 只依赖导出的 ONNX 文件，不依赖本模块。该模块仅服务于本地模型准备。
"""

from __future__ import annotations

from dataclasses import dataclass

import torch
from torch import nn
import torch.nn.functional as F


@dataclass(frozen=True)
class UNetConfig:
    """可序列化的 U-Net 结构参数。"""

    in_channels: int = 3
    out_channels: int = 1
    depth: int = 4
    start_filters: int = 32
    dropout: float = 0.2
    bottleneck_dropout: float = 0.3


def _conv_block(in_channels: int, out_channels: int, dropout: float) -> nn.Sequential:
    return nn.Sequential(
        nn.Conv2d(in_channels, out_channels, kernel_size=3, padding=1),
        nn.BatchNorm2d(out_channels),
        nn.ReLU(inplace=True),
        nn.Dropout2d(p=dropout),
        nn.Conv2d(out_channels, out_channels, kernel_size=3, padding=1),
        nn.BatchNorm2d(out_channels),
        nn.ReLU(inplace=True),
        nn.Dropout2d(p=dropout),
    )


class ImprovedUNet(nn.Module):
    """带批归一化和跳跃连接的 U-Net，输出未经过 Sigmoid 的 logits。"""

    def __init__(self, config: UNetConfig | None = None) -> None:
        super().__init__()
        self.config = config or UNetConfig()
        if self.config.depth < 2:
            raise ValueError("depth 必须大于等于 2")
        if self.config.start_filters < 8:
            raise ValueError("start_filters 必须大于等于 8")

        self.encoders = nn.ModuleList()
        self.pools = nn.ModuleList()
        current_channels = self.config.in_channels
        for index in range(self.config.depth):
            output_channels = self.config.start_filters * (2**index)
            self.encoders.append(
                _conv_block(current_channels, output_channels, self.config.dropout)
            )
            self.pools.append(nn.MaxPool2d(kernel_size=2, stride=2))
            current_channels = output_channels

        bottleneck_channels = self.config.start_filters * (2**self.config.depth)
        self.bottleneck = _conv_block(
            current_channels,
            bottleneck_channels,
            self.config.bottleneck_dropout,
        )

        self.upconvs = nn.ModuleList()
        self.decoders = nn.ModuleList()
        decoder_input_channels = bottleneck_channels
        for index in range(self.config.depth - 1, -1, -1):
            output_channels = self.config.start_filters * (2**index)
            self.upconvs.append(
                nn.ConvTranspose2d(
                    decoder_input_channels,
                    output_channels,
                    kernel_size=2,
                    stride=2,
                )
            )
            self.decoders.append(
                _conv_block(output_channels * 2, output_channels, self.config.dropout)
            )
            decoder_input_channels = output_channels

        self.final_conv = nn.Conv2d(
            self.config.start_filters,
            self.config.out_channels,
            kernel_size=1,
        )

    def forward(self, images: torch.Tensor) -> torch.Tensor:
        encoder_outputs: list[torch.Tensor] = []
        features = images
        for encoder, pool in zip(self.encoders, self.pools, strict=True):
            features = encoder(features)
            encoder_outputs.append(features)
            features = pool(features)

        features = self.bottleneck(features)
        for index, (upconv, decoder) in enumerate(
            zip(self.upconvs, self.decoders, strict=True)
        ):
            features = upconv(features)
            skip = encoder_outputs[-(index + 1)]
            if features.shape[-2:] != skip.shape[-2:]:
                features = F.interpolate(
                    features,
                    size=skip.shape[-2:],
                    mode="bilinear",
                    align_corners=False,
                )
            features = torch.cat([features, skip], dim=1)
            features = decoder(features)
        return self.final_conv(features)


def normalize_state_dict(payload: object) -> dict[str, torch.Tensor]:
    """从常见检查点结构提取 state_dict，并去掉 DataParallel 前缀。"""

    if not isinstance(payload, dict):
        raise ValueError("检查点必须是 state_dict 或包含 state_dict 的字典")

    candidate = payload
    for key in ("state_dict", "model_state_dict", "modelStateDict", "model"):
        nested = payload.get(key)
        if isinstance(nested, dict):
            candidate = nested
            break

    normalized: dict[str, torch.Tensor] = {}
    for key, value in candidate.items():
        if not isinstance(key, str) or not isinstance(value, torch.Tensor):
            continue
        normalized[key.removeprefix("module.")] = value
    if not normalized:
        raise ValueError("检查点中没有可用的模型参数")
    return normalized
