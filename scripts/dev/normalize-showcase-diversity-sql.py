#!/usr/bin/env python3
from __future__ import annotations

import os
from pathlib import Path

SQL_FILE = Path(os.environ.get("SHOWCASE_SQL_FILE") or os.environ["SHOWCASE_DIVERSITY_SQL_FILE"])


def cast_make_interval_integer_arg(sql: str, unit: str) -> tuple[str, int]:
    """Ensure make_interval integer arguments receive PostgreSQL integer values.

    PostgreSQL make_interval() expects integer for years/months/weeks/days/hours/mins,
    while row_number() returns bigint. Showcase SQL intentionally uses row_number()
    heavily, so normalize generated SQL centrally instead of maintaining a fragile
    list of individual casts.
    """
    token = f"make_interval({unit} =>"
    output: list[str] = []
    cursor = 0
    count = 0

    while True:
        start = sql.find(token, cursor)
        if start < 0:
            output.append(sql[cursor:])
            break

        output.append(sql[cursor:start])
        arg_start = start + len(token)
        depth = 1  # opening parenthesis in make_interval(
        index = arg_start
        in_single_quote = False

        while index < len(sql):
            char = sql[index]
            if char == "'":
                if in_single_quote and index + 1 < len(sql) and sql[index + 1] == "'":
                    index += 2
                    continue
                in_single_quote = not in_single_quote
            elif not in_single_quote:
                if char == "(":
                    depth += 1
                elif char == ")":
                    depth -= 1
                    if depth == 0:
                        break
            index += 1

        if depth != 0:
            raise RuntimeError(f"无法解析生成 SQL 中的 make_interval({unit} => ...) 表达式")

        argument = sql[arg_start:index].strip()
        output.append(f"make_interval({unit} => (({argument})::int))")
        cursor = index + 1
        count += 1

    return "".join(output), count


def cast_make_interval_days(sql: str) -> tuple[str, int]:
    """Backward-compatible wrapper used by earlier showcase generation code."""
    return cast_make_interval_integer_arg(sql, "days")


def cast_make_interval_hours(sql: str) -> tuple[str, int]:
    return cast_make_interval_integer_arg(sql, "hours")


def normalize_resident_report_types(sql: str) -> tuple[str, int]:
    """Keep generated resident feedback aligned with the formal feedback API."""
    old = (
        "CASE s.rn%5 WHEN 0 THEN 'WALL_CRACK' WHEN 1 THEN 'WATER_LEAKAGE' "
        "WHEN 2 THEN 'SURFACE_FALLING' WHEN 3 THEN 'DEFORMATION' ELSE 'OTHER' END"
    )
    new = (
        "CASE s.rn%5 WHEN 0 THEN 'WALL_CRACK' WHEN 1 THEN 'WATER_LEAKAGE' "
        "WHEN 2 THEN 'SURFACE_FALLING' WHEN 3 THEN 'ILLEGAL_MODIFICATION' ELSE 'OTHER' END"
    )
    occurrences = sql.count(old)
    return sql.replace(old, new), occurrences


def main() -> None:
    sql = SQL_FILE.read_text(encoding="utf-8")
    sql, day_interval_count = cast_make_interval_days(sql)
    sql, hour_interval_count = cast_make_interval_hours(sql)
    sql, report_type_count = normalize_resident_report_types(sql)
    SQL_FILE.write_text(sql, encoding="utf-8")
    print(
        "展示 SQL 类型规范化完成："
        f"make_interval_days={day_interval_count}，"
        f"make_interval_hours={hour_interval_count}，"
        f"residentReportType={report_type_count}",
        flush=True,
    )


if __name__ == "__main__":
    main()
