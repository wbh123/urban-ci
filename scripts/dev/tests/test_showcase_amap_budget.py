#!/usr/bin/env python3
from __future__ import annotations

import sys
import unittest
from pathlib import Path

DEV_DIR = Path(__file__).resolve().parents[1]
if str(DEV_DIR) not in sys.path:
    sys.path.insert(0, str(DEV_DIR))

from showcase_amap_budget import AmapNetworkBudget, AmapNetworkBudgetExceeded  # noqa: E402


class AmapNetworkBudgetTest(unittest.TestCase):
    def test_zero_limit_is_unlimited(self) -> None:
        budget = AmapNetworkBudget(0)
        for _ in range(1000):
            budget.consume("/v5/place/text")
        self.assertEqual(1000, budget.used)
        self.assertEqual(0, budget.remaining)

    def test_positive_limit_stops_before_extra_network_call(self) -> None:
        budget = AmapNetworkBudget(2)
        budget.consume("/v5/place/text")
        budget.consume("/v3/geocode/regeo")
        with self.assertRaises(AmapNetworkBudgetExceeded):
            budget.consume("/v5/place/around")
        self.assertEqual(2, budget.used)
        self.assertEqual(0, budget.remaining)

    def test_remaining_never_becomes_negative(self) -> None:
        budget = AmapNetworkBudget(1)
        budget.consume("/v5/place/text")
        try:
            budget.consume("/v5/place/around")
        except AmapNetworkBudgetExceeded:
            pass
        self.assertEqual(0, budget.remaining)


if __name__ == "__main__":
    unittest.main()
