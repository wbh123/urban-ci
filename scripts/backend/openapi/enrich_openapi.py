#!/usr/bin/env python3
"""Add ApiFox-oriented metadata, examples, and unified authentication to OpenAPI."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

HTTP_METHODS = {"get", "post", "put", "patch", "delete", "options", "head"}
PUBLIC_PATHS = {
    "/api/v1/system/health",
    "/api/v1/auth/login",
}
BEARER_SECURITY = [{"bearerAuth": []}]
BEARER_DESCRIPTION = (
    "JWT Bearer Token 认证。ApiFox 调试时，登录接口会把响应体 "
    "data.accessToken 提取到环境变量 accessToken，受保护接口统一发送 "
    "Authorization: Bearer {{accessToken}}。"
)
LOGIN_AUTH_NOTE = (
    "该接口无需鉴权。ApiFox 集合会在请求成功后执行后置操作，"
    "把响应体 data.accessToken 保存为环境变量 accessToken。"
)
PROTECTED_AUTH_NOTE = (
    "该接口需要 JWT Bearer Token。ApiFox 调试时统一使用登录后提取的 "
    "accessToken 环境变量，即 Authorization: Bearer {{accessToken}}。"
)

FOLDERS = [
    ("/api/v1/system", "系统"),
    ("/api/v1/auth", "认证"),
    ("/api/v1/audit", "审计"),
    ("/api/v1/building-evidence", "楼栋与证据"),
    ("/api/v1/buildings", "楼栋与证据"),
    ("/api/v1/communities", "小区与地图"),
    ("/api/v1/map", "小区与地图"),
    ("/api/v1/inspection", "巡检任务"),
    ("/api/v1/assets", "图片资源"),
]

SUMMARY_BY_OPERATION = {
    "getSystemHealth": "查询系统健康状态",
    "login": "管理员登录",
    "logout": "退出登录",
    "getCurrentUser": "查询当前用户",
    "getPhase2MapRuntimeConfig": "查询地图运行配置",
    "previewPhase2Geocoding": "预览地址地理编码",
    "listPhase2CommunityPoints": "查询小区地图点位",
    "getPhase2CommunityLocation": "查询小区坐标",
    "savePhase2CommunityLocation": "保存小区坐标",
    "listPhase2InspectionTasks": "查询巡检任务",
    "createPhase2InspectionTask": "创建巡检任务",
    "getPhase2InspectionTask": "查询巡检任务详情",
    "startPhase2InspectionTask": "开始巡检任务",
    "completePhase2InspectionTask": "完成巡检任务",
    "cancelPhase2InspectionTask": "取消巡检任务",
    "listPhase2InspectionRecords": "查询巡检记录",
    "createPhase2InspectionRecord": "创建巡检记录",
    "uploadPhase2InspectionImage": "上传巡检图片",
    "listPhase2Assets": "查询业务图片",
    "previewPhase2Asset": "获取图片预览地址",
    "readPhase2AssetContent": "读取图片内容",
}

REQUEST_EXAMPLES: dict[str, dict[str, Any]] = {
    "login": {"username": "admin", "password": "urban_safe_admin_password"},
    "previewPhase2Geocoding": {
        "address": "湖南省株洲市天元区示范路1号",
        "city": "株洲市",
    },
    "savePhase2CommunityLocation": {
        "longitude": 113.13396,
        "latitude": 27.82767,
        "formattedAddress": "湖南省株洲市天元区示范路1号",
        "provider": "MOCK",
        "matchLevel": "BUILDING",
        "metadata": {"source": "APIFOX"},
    },
    "createPhase2InspectionTask": {
        "buildingId": "11111111-1111-1111-1111-111111111111",
        "inspectionType": "ROUTINE",
        "title": "ApiFox 现场安全巡检",
        "description": "第二阶段验收任务",
    },
    "createPhase2InspectionRecord": {
        "taskId": "22222222-2222-2222-2222-222222222222",
        "inspectionPart": "外墙与公共区域",
        "issueType": "OTHER",
        "severity": "LOW",
        "summary": "外墙未发现明显贯穿裂缝。",
        "rectificationSuggestion": "建议物业持续观察。",
        "formData": {"source": "APIFOX"},
    },
}

RESPONSE_EXAMPLES: dict[str, Any] = {
    "login": {
        "success": True,
        "data": {
            "accessToken": "eyJ...sample",
            "tokenType": "Bearer",
            "expiresInSeconds": 7200,
            "user": {
                "id": "00000000-0000-0000-0000-000000000001",
                "username": "admin",
                "realName": "开发管理员",
                "roles": ["ADMIN"],
            },
        },
        "error": None,
    },
    "getPhase2MapRuntimeConfig": {
        "success": True,
        "data": {
            "mode": "MOCK",
            "provider": "AMAP",
            "defaultCenter": {"longitude": 113.13396, "latitude": 27.82767},
            "defaultZoom": 12,
        },
        "error": None,
    },
    "createPhase2InspectionTask": {
        "success": True,
        "data": {
            "taskId": "22222222-2222-2222-2222-222222222222",
            "taskCode": "INS-20260714-0001",
            "status": "PENDING",
        },
        "error": None,
    },
    "createPhase2InspectionRecord": {
        "success": True,
        "data": {
            "recordId": "33333333-3333-3333-3333-333333333333",
            "severity": "LOW",
            "summary": "外墙未发现明显贯穿裂缝。",
        },
        "error": None,
    },
    "uploadPhase2InspectionImage": {
        "success": True,
        "data": {
            "assetId": "44444444-4444-4444-4444-444444444444",
            "originalFilename": "inspection-sample.png",
        },
        "error": None,
    },
}


def folder_for(path: str) -> str:
    for prefix, folder in FOLDERS:
        if path.startswith(prefix):
            return folder
    return "其他"


def first_success_response(operation: dict[str, Any]) -> tuple[str, dict[str, Any]] | None:
    responses = operation.get("responses")
    if not isinstance(responses, dict):
        return None
    for status in ("200", "201", "202", "204", "302"):
        response = responses.get(status)
        if isinstance(response, dict):
            return status, response
    return None


def add_request_example(operation: dict[str, Any], operation_id: str) -> None:
    example = REQUEST_EXAMPLES.get(operation_id)
    if not example:
        return
    request_body = operation.get("requestBody")
    if not isinstance(request_body, dict):
        return
    content = request_body.get("content")
    if not isinstance(content, dict):
        return
    media = content.get("application/json")
    if isinstance(media, dict) and "example" not in media and "examples" not in media:
        media["example"] = example


def add_response_example(operation: dict[str, Any], operation_id: str) -> None:
    example = RESPONSE_EXAMPLES.get(operation_id)
    if example is None:
        return
    result = first_success_response(operation)
    if not result:
        return
    _, response = result
    content = response.setdefault("content", {})
    media = content.setdefault("application/json", {})
    media.setdefault("example", example)


def append_description_once(operation: dict[str, Any], note: str) -> None:
    description = str(operation.get("description") or "").strip()
    if note in description:
        return
    operation["description"] = f"{description}\n\n{note}".strip()


def configure_security(document: dict[str, Any]) -> None:
    components = document.setdefault("components", {})
    schemes = components.setdefault("securitySchemes", {})
    bearer = schemes.setdefault("bearerAuth", {})
    bearer.update(
        {
            "type": "http",
            "scheme": "bearer",
            "bearerFormat": "JWT",
            "description": BEARER_DESCRIPTION,
        }
    )
    document["security"] = BEARER_SECURITY


def enrich(document: dict[str, Any]) -> None:
    configure_security(document)

    document.setdefault("tags", [])
    existing_tags = {tag.get("name") for tag in document["tags"] if isinstance(tag, dict)}
    for folder in ["系统", "认证", "小区与地图", "楼栋与证据", "巡检任务", "图片资源", "审计", "其他"]:
        if folder not in existing_tags:
            document["tags"].append({"name": folder, "description": f"{folder}相关接口"})

    for path, path_item in document.get("paths", {}).items():
        if not isinstance(path_item, dict):
            continue
        folder = folder_for(path)
        for method, operation in path_item.items():
            if method.lower() not in HTTP_METHODS or not isinstance(operation, dict):
                continue

            operation_id = operation.get("operationId", f"{method}_{path}")
            summary = operation.get("summary") or SUMMARY_BY_OPERATION.get(operation_id, operation_id)
            operation["summary"] = summary
            operation.setdefault("description", f"{summary}。由 OpenAPI 契约生成，可在 ApiFox 中直接调试。")
            operation["x-apifox-folder"] = folder
            operation["x-apifox-status"] = "tested"
            operation["x-apifox-name"] = summary
            operation.setdefault("tags", [folder])

            if path in PUBLIC_PATHS:
                operation["security"] = []
                if path == "/api/v1/auth/login":
                    append_description_once(operation, LOGIN_AUTH_NOTE)
            else:
                operation["security"] = BEARER_SECURITY
                append_description_once(operation, PROTECTED_AUTH_NOTE)

            add_request_example(operation, operation_id)
            add_response_example(operation, operation_id)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("openapi", type=Path)
    args = parser.parse_args()

    document = json.loads(args.openapi.read_text(encoding="utf-8"))
    enrich(document)
    args.openapi.write_text(
        json.dumps(document, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Enriched OpenAPI written to {args.openapi.resolve()}")


if __name__ == "__main__":
    main()
