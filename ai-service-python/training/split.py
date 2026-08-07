"""不依赖深度学习框架的数据分组划分逻辑。"""

from __future__ import annotations

import random
from collections.abc import Sequence


def split_group_names(
    group_names: Sequence[str],
    *,
    seed: int,
    train_ratio: float = 0.70,
    val_ratio: float = 0.15,
) -> tuple[set[str], set[str], set[str]]:
    """按 group 划分训练、验证和测试集合，并保证三者均非空。"""

    if (
        not 0.0 < train_ratio < 1.0
        or not 0.0 < val_ratio < 1.0
        or train_ratio + val_ratio >= 1.0
    ):
        raise ValueError("训练集和验证集比例不合法")

    names = sorted(set(group_names))
    if len(names) < 3:
        raise ValueError("至少需要 3 个独立 group 才能划分训练、验证和测试集")
    random.Random(seed).shuffle(names)

    total = len(names)
    train_count = min(total - 2, max(1, round(total * train_ratio)))
    remaining = total - train_count
    val_count = min(remaining - 1, max(1, round(total * val_ratio)))

    train_groups = set(names[:train_count])
    val_groups = set(names[train_count : train_count + val_count])
    test_groups = set(names[train_count + val_count :])
    if not train_groups or not val_groups or not test_groups:
        raise AssertionError("训练、验证和测试 group 必须全部非空")
    if train_groups & val_groups or train_groups & test_groups or val_groups & test_groups:
        raise AssertionError("训练、验证和测试 group 不得交叉")
    return train_groups, val_groups, test_groups
