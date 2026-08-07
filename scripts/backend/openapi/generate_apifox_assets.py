#!/usr/bin/env python3
"""Generate ApiFox-importable Postman collections and local environment."""

from __future__ import annotations

import argparse
import base64
import json
import random
import sys
from pathlib import Path
from typing import Any


COLLECTION_SCHEMA = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
ENV_SCHEMA = "https://schema.getpostman.com/json/collection/v2.1.0/environment.json"
ACCESS_TOKEN_VARIABLE = "{{accessToken}}"

SAMPLE_PNG_BASE64 = (
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB"
    "9Y9Z4m8AAAAASUVORK5CYII="
)

TOKEN_REQUIRED_SCRIPT = [
    'const accessToken = pm.environment.get("accessToken");',
    'if (!accessToken) {',
    '  throw new Error("缺少 accessToken，请先执行登录接口并确认后置操作已成功提取令牌。");',
    '}',
]


def parse_scalar_yaml(path: Path) -> dict[str, str]:
    """Read scalar values from the project's indentation-based application.yaml."""
    values: dict[str, str] = {}
    stack: list[tuple[int, str]] = []

    for raw_line in path.read_text(encoding="utf-8").splitlines():
        stripped = raw_line.strip()
        if not stripped or stripped.startswith("#") or stripped.startswith("- "):
            continue
        indent = len(raw_line) - len(raw_line.lstrip(" "))
        if ":" not in stripped:
            continue
        key, value = stripped.split(":", 1)
        key = key.strip()
        value = value.strip()

        while stack and indent <= stack[-1][0]:
            stack.pop()

        if not value:
            stack.append((indent, key))
            continue

        full_key = ".".join([item[1] for item in stack] + [key])
        if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
            value = value[1:-1]
        values[full_key] = value
    return values


def event(listen: str, lines: list[str]) -> dict[str, Any]:
    return {
        "listen": listen,
        "script": {
            "type": "text/javascript",
            "exec": lines,
        },
    }


def bearer_auth() -> dict[str, Any]:
    """Return the single authentication definition used by every protected request."""
    return {
        "type": "bearer",
        "bearer": [
            {
                "key": "token",
                "value": ACCESS_TOKEN_VARIABLE,
                "type": "string",
            }
        ],
    }


def success_tests(
    expected_status: int,
    extracts: dict[str, str] | None = None,
    extra: list[str] | None = None,
) -> list[str]:
    lines = [
        f'pm.test("HTTP {expected_status}", function () {{',
        f"  pm.response.to.have.status({expected_status});",
        "});",
        "const body = pm.response.json();",
        'pm.test("统一响应 success=true", function () {',
        "  pm.expect(body.success).to.eql(true);",
        "});",
    ]
    for variable, expression in (extracts or {}).items():
        lines.extend(
            [
                f'pm.expect({expression}, "{variable} 不应为空").to.exist;',
                f'pm.environment.set("{variable}", {expression});',
            ]
        )
    if extra:
        lines.extend(extra)
    return lines


def login_tests() -> list[str]:
    """Build login assertions and the accessToken post-operation extraction."""
    return success_tests(
        200,
        extra=[
            "const accessToken = body && body.data ? body.data.accessToken : null;",
            'pm.test("登录响应包含 accessToken", function () {',
            '  pm.expect(accessToken).to.be.a("string").and.not.empty;',
            "});",
            'pm.environment.set("accessToken", accessToken);',
            'pm.test("accessToken 已写入环境变量", function () {',
            '  pm.expect(pm.environment.get("accessToken")).to.eql(accessToken);',
            "});",
            'pm.test("管理员角色存在", function () {',
            "  pm.expect(body.data.user.roles).to.include('ADMIN');",
            "});",
        ],
    )


def raw_json(data: dict[str, Any]) -> dict[str, Any]:
    return {
        "mode": "raw",
        "raw": json.dumps(data, ensure_ascii=False, indent=2),
        "options": {"raw": {"language": "json"}},
    }


def item(
    name: str,
    method: str,
    path: str,
    *,
    body: dict[str, Any] | None = None,
    tests: list[str] | None = None,
    pre_request: list[str] | None = None,
    auth: str = "bearer",
    formdata: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    request: dict[str, Any] = {
        "method": method,
        "header": [],
        "url": {"raw": "{{baseUrl}}" + path, "host": ["{{baseUrl}}"], "path": []},
    }

    effective_pre_request = list(pre_request or [])
    if auth == "noauth":
        request["auth"] = {"type": "noauth"}
    elif auth == "bearer":
        request["auth"] = bearer_auth()
        effective_pre_request = TOKEN_REQUIRED_SCRIPT + effective_pre_request
    else:
        raise ValueError(f"不支持的认证类型：{auth}")

    if body is not None:
        request["header"].append({"key": "Content-Type", "value": "application/json"})
        request["body"] = raw_json(body)
    if formdata is not None:
        request["body"] = {"mode": "formdata", "formdata": formdata}

    events = []
    if effective_pre_request:
        events.append(event("prerequest", effective_pre_request))
    if tests:
        events.append(event("test", tests))

    result: dict[str, Any] = {"name": name, "request": request}
    if events:
        result["event"] = events
    return result


def build_collection(image_path: Path, include_upload: bool) -> dict[str, Any]:
    first_pre_request = [
        "const randomPart = Math.floor(Math.random() * 100000).toString().padStart(5, '0');",
        "pm.environment.set('runId', Date.now().toString() + '-' + randomPart);",
        "pm.environment.unset('accessToken');",
    ]

    items: list[dict[str, Any]] = [
        item(
            "01 健康检查",
            "GET",
            "/api/v1/system/health",
            auth="noauth",
            pre_request=first_pre_request,
            tests=success_tests(200),
        ),
        item(
            "02 管理员登录并提取 accessToken",
            "POST",
            "/api/v1/auth/login",
            auth="noauth",
            body={"username": "{{username}}", "password": "{{password}}"},
            tests=login_tests(),
        ),
        item(
            "03 创建验收小区并提取 communityId",
            "POST",
            "/api/v1/communities",
            body={
                "communityCode": "APIFOX-COM-{{runId}}",
                "communityName": "ApiFox 自动验收小区",
                "administrativeRegion": "湖南省株洲市天元区",
                "address": "湖南省株洲市天元区示范路1号",
                "constructionPeriod": "2000年",
                "householdCount": 120,
                "residentCount": 350,
                "status": "ACTIVE",
            },
            tests=success_tests(
                201,
                {
                    "communityId": "body.data.id",
                    "communityVersion": "body.data.version",
                },
            ),
        ),
        item(
            "04 创建验收楼栋并提取 buildingId",
            "POST",
            "/api/v1/buildings",
            body={
                "communityId": "{{communityId}}",
                "buildingCode": "APIFOX-BLD-{{runId}}",
                "buildingName": "ApiFox 自动验收1栋",
                "address": "湖南省株洲市天元区示范路1号",
                "constructionYear": 1998,
                "structureType": "砖混结构",
                "floorCount": 6,
                "householdCount": 24,
                "residentCount": 72,
                "status": "ACTIVE",
            },
            tests=success_tests(
                201,
                {
                    "buildingId": "body.data.id",
                    "buildingVersion": "body.data.version",
                },
            ),
        ),
        item(
            "05 预览地理编码并提取坐标",
            "POST",
            "/api/v1/map/geocoding/preview",
            body={"address": "湖南省株洲市天元区示范路1号", "city": "株洲市"},
            tests=success_tests(
                200,
                {
                    "longitude": "body.data.longitude",
                    "latitude": "body.data.latitude",
                    "formattedAddress": "body.data.formattedAddress",
                    "locationProvider": "body.data.provider",
                    "matchLevel": "body.data.matchLevel",
                },
            ),
        ),
        item(
            "06 保存小区坐标",
            "PUT",
            "/api/v1/communities/{{communityId}}/location",
            body={
                "longitude": "{{longitude}}",
                "latitude": "{{latitude}}",
                "formattedAddress": "{{formattedAddress}}",
                "provider": "{{locationProvider}}",
                "matchLevel": "{{matchLevel}}",
                "metadata": {"source": "APIFOX_AUTO_FLOW"},
            },
            tests=success_tests(200),
        ),
        item(
            "07 创建巡检任务并提取 taskId",
            "POST",
            "/api/v1/inspection-tasks",
            body={
                "buildingId": "{{buildingId}}",
                "inspectionType": "ROUTINE",
                "title": "ApiFox 自动现场安全巡检",
                "description": "由 ApiFox 可执行集合创建的第二阶段验收任务",
            },
            tests=success_tests(201, {"taskId": "body.data.taskId"}),
        ),
        item(
            "08 开始巡检任务",
            "POST",
            "/api/v1/inspection-tasks/{{taskId}}/start",
            tests=success_tests(
                200,
                extra=[
                    'pm.test("任务进入执行中", function () {',
                    "  pm.expect(body.data.status).to.eql('IN_PROGRESS');",
                    "});",
                ],
            ),
        ),
        item(
            "09 创建巡检记录并提取 recordId",
            "POST",
            "/api/v1/inspection-records",
            body={
                "taskId": "{{taskId}}",
                "inspectionPart": "外墙与公共区域",
                "issueType": "OTHER",
                "severity": "LOW",
                "summary": "外墙未发现明显贯穿裂缝，部分墙面存在轻微表层脱落。",
                "rectificationSuggestion": "建议物业进行局部修补并持续观察。",
                "formData": {"source": "APIFOX_AUTO_FLOW"},
            },
            tests=success_tests(201, {"recordId": "body.data.recordId"}),
        ),
    ]

    if include_upload:
        items.append(
            item(
                "10 上传巡检图片并提取 assetId",
                "POST",
                "/api/v1/assets/images",
                formdata=[
                    {"key": "file", "type": "file", "src": str(image_path.resolve())},
                    {"key": "businessType", "type": "text", "value": "INSPECTION_TASK"},
                    {"key": "businessId", "type": "text", "value": "{{taskId}}"},
                    {"key": "bindingRole", "type": "text", "value": "INSPECTION_PHOTO"},
                ],
                tests=success_tests(201, {"assetId": "body.data.assetId"}),
            )
        )

    step = 11 if include_upload else 10
    items.extend(
        [
            item(
                f"{step:02d} 完成巡检任务",
                "POST",
                "/api/v1/inspection-tasks/{{taskId}}/complete",
                tests=success_tests(
                    200,
                    extra=[
                        'pm.test("任务完成", function () {',
                        "  pm.expect(body.data.status).to.eql('COMPLETED');",
                        "});",
                    ],
                ),
            ),
            item(
                f"{step + 1:02d} 查询任务详情",
                "GET",
                "/api/v1/inspection-tasks/{{taskId}}",
                tests=success_tests(
                    200,
                    extra=[
                        'pm.test("任务编号与状态正确", function () {',
                        "  pm.expect(body.data.taskId).to.eql(pm.environment.get('taskId'));",
                        "  pm.expect(body.data.status).to.eql('COMPLETED');",
                        "});",
                    ],
                ),
            ),
            item(
                f"{step + 2:02d} 查询巡检记录",
                "GET",
                "/api/v1/inspection-records?taskId={{taskId}}",
                tests=success_tests(
                    200,
                    extra=[
                        'pm.test("至少存在一条巡检记录", function () {',
                        "  const records = Array.isArray(body.data) ? body.data : (body.data.content || body.data.items || []);",
                        "  pm.expect(records.length).to.be.above(0);",
                        "});",
                    ],
                ),
            ),
            item(
                f"{step + 3:02d} 查询小区地图点位",
                "GET",
                "/api/v1/map/communities",
                tests=success_tests(200),
            ),
        ]
    )

    if include_upload:
        items.append(
            item(
                f"{step + 4:02d} 查询任务图片",
                "GET",
                "/api/v1/assets?businessType=INSPECTION_TASK&businessId={{taskId}}",
                tests=success_tests(
                    200,
                    extra=[
                        'pm.test("至少存在一张任务图片", function () {',
                        "  const assets = Array.isArray(body.data) ? body.data : (body.data.content || body.data.items || []);",
                        "  pm.expect(assets.length).to.be.above(0);",
                        "});",
                    ],
                ),
            )
        )

    title = (
        "UrbanSafe Priority 全链路验收（含图片）"
        if include_upload
        else "UrbanSafe Priority 核心链路验收"
    )
    return {
        "info": {
            "_postman_id": f"urban-safe-priority-{random.randint(100000, 999999)}",
            "name": title,
            "description": (
                "导入 ApiFox 后选择“本地开发环境”，按顺序运行即可。"
                "登录接口不携带鉴权信息，成功后提取 data.accessToken；"
                "其余受保护接口统一使用 Bearer {{accessToken}}。"
            ),
            "schema": COLLECTION_SCHEMA,
        },
        "auth": bearer_auth(),
        "item": [{"name": "第二阶段自动验收", "item": items}],
    }


def build_environment(config: dict[str, str]) -> dict[str, Any]:
    username = config.get("urban-safe.auth.bootstrap-admin.username", "admin")
    password = config.get("urban-safe.auth.bootstrap-admin.password", "")
    enabled = config.get("urban-safe.auth.bootstrap-admin.enabled", "false").lower() == "true"
    port = config.get("server.port", "8888")

    if not enabled:
        print(
            "警告：bootstrap-admin.enabled 不是 true；请确认数据库中已存在管理员账号。",
            file=sys.stderr,
        )
    if not password:
        raise SystemExit(
            "application.yaml 中 urban-safe.auth.bootstrap-admin.password 为空，"
            "请填写管理员密码后重新导出，确保 ApiFox 导入后可直接登录。"
        )

    keys = [
        ("baseUrl", f"http://localhost:{port}"),
        ("username", username),
        ("password", password),
        ("accessToken", ""),
        ("runId", ""),
        ("communityId", ""),
        ("communityVersion", ""),
        ("buildingId", ""),
        ("buildingVersion", ""),
        ("longitude", ""),
        ("latitude", ""),
        ("formattedAddress", ""),
        ("locationProvider", ""),
        ("matchLevel", ""),
        ("taskId", ""),
        ("recordId", ""),
        ("assetId", ""),
    ]
    return {
        "id": "urban-safe-priority-local",
        "name": "UrbanSafe Priority 本地开发环境",
        "values": [
            {"key": key, "value": value, "enabled": True, "type": "default"}
            for key, value in keys
        ],
        "_postman_variable_scope": "environment",
        "_postman_exported_using": "UrbanSafe Priority ApiFox Exporter",
        "_postman_exported_at": "2026-07-14T00:00:00.000Z",
        "_schema": ENV_SCHEMA,
    }


def write_json(path: Path, data: dict[str, Any]) -> None:
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--application", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    args.output.mkdir(parents=True, exist_ok=True)
    image_path = args.output / "inspection-sample.png"
    image_path.write_bytes(base64.b64decode(SAMPLE_PNG_BASE64))

    config = parse_scalar_yaml(args.application)
    environment = build_environment(config)
    write_json(
        args.output / "urban-safe-priority-local.postman_environment.json",
        environment,
    )
    write_json(
        args.output / "urban-safe-priority-smoke.postman_collection.json",
        build_collection(image_path, include_upload=False),
    )
    write_json(
        args.output / "urban-safe-priority-full.postman_collection.json",
        build_collection(image_path, include_upload=True),
    )

    print(f"ApiFox import assets generated in {args.output.resolve()}")


if __name__ == "__main__":
    main()
