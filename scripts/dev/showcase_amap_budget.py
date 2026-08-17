#!/usr/bin/env python3
from __future__ import annotations


class AmapNetworkBudgetExceeded(RuntimeError):
    pass


class AmapNetworkBudget:
    """Counts only real AMap network requests.

    A limit of zero means unlimited. Callers must invoke ``consume`` only after
    a cache miss and immediately before the real HTTP request.
    """

    def __init__(self, limit: int) -> None:
        if limit < 0:
            raise ValueError("高德网络请求预算不能为负数")
        self.limit = limit
        self.used = 0

    @property
    def remaining(self) -> int:
        if self.limit == 0:
            return 0
        return max(0, self.limit - self.used)

    def consume(self, path: str) -> None:
        if self.limit > 0 and self.used >= self.limit:
            raise AmapNetworkBudgetExceeded(
                f"高德网络请求预算已耗尽：limit={self.limit}, used={self.used}, next={path}。"
                "请优先复用缓存；确认确有需要后再提高 SHOWCASE_AMAP_MAX_NETWORK_REQUESTS。"
            )
        self.used += 1
