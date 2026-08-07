#!/usr/bin/env python3
"""Normalize generated Postman files for direct import into ApiFox."""

from __future__ import annotations

import argparse
import json
import re
from copy import deepcopy
from pathlib import Path
from typing import Any, Iterator

PUBLIC_PATHS = {"/api/v1/system/health", "/api/v1/auth/login"}
PUBLIC_SUFFIXES = {
    "/system/health": "/api/v1/system/health",
    "/auth/login": "/api/v1/auth/login",
}
BEARER_AUTH = {
    "type": "bearer",
    "bearer": [{"key": "token", "value": "{{accessToken}}", "type": "string"}],
}
TOKEN_CHECK = [
    'const accessToken = pm.globals.get("accessToken");',
    'if (!accessToken) {',
    '  throw new Error("缺少 accessToken，请先执行登录接口。");',
    '}',
]
LOGIN_EXTRACT = [
    'const loginBody = pm.response.json();',
    'const accessToken = loginBody && loginBody.data ? loginBody.data.accessToken : null;',
    'pm.test("登录响应包含 accessToken", function () {',
    '  pm.expect(accessToken).to.be.a("string").and.not.empty;',
    '});',
    'pm.globals.set("accessToken", accessToken);',
]


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def save_json(path: Path, data: dict[str, Any]) -> None:
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def environment_values(path: Path) -> list[dict[str, str]]:
    environment = load_json(path)
    return [
        {"key": str(item["key"]), "value": str(item.get("value", "")), "type": "string"}
        for item in environment.get("values", [])
        if item.get("enabled", True) and item.get("key")
    ]


def iter_requests(items: list[dict[str, Any]]) -> Iterator[dict[str, Any]]:
    for item in items:
        if isinstance(item.get("item"), list):
            yield from iter_requests(item["item"])
        elif isinstance(item.get("request"), dict):
            yield item


def raw_request_path(item: dict[str, Any]) -> str:
    url = item["request"].get("url", "")
    if isinstance(url, str):
        raw = url
    else:
        raw = str(url.get("raw", ""))
        if not raw and isinstance(url.get("path"), list):
            raw = "/" + "/".join(str(segment) for segment in url["path"])

    raw = raw.split("?", 1)[0]
    marker = raw.find("/api/")
    if marker >= 0:
        return raw[marker:]

    raw = re.sub(r"^https?://[^/]+", "", raw)
    raw = re.sub(r"^\{\{[^}]+\}\}", "", raw)
    return "/" + raw.lstrip("/")


def request_path(item: dict[str, Any]) -> str:
    path = raw_request_path(item)
    if path in PUBLIC_PATHS:
        return path
    for suffix, canonical in PUBLIC_SUFFIXES.items():
        if path.endswith(suffix):
            return canonical
    return path


def event_lines(item: dict[str, Any], listen: str, create: bool = False) -> list[str]:
    events = item.setdefault("event", [])
    for event in events:
        if event.get("listen") == listen:
            return event.setdefault("script", {}).setdefault("exec", [])
    if not create:
        return []
    event = {"listen": listen, "script": {"type": "text/javascript", "exec": []}}
    events.append(event)
    return event["script"]["exec"]


def replace_environment_scope(item: dict[str, Any]) -> None:
    for event in item.get("event", []):
        script = event.get("script", {})
        script["exec"] = [
            str(line).replace("pm.environment.", "pm.globals.")
            for line in script.get("exec", [])
        ]


def ensure_login_extract(item: dict[str, Any]) -> None:
    lines = event_lines(item, "test", create=True)
    if 'pm.globals.set("accessToken"' not in "\n".join(lines):
        lines.extend(LOGIN_EXTRACT)


def ensure_token_check(item: dict[str, Any]) -> None:
    lines = event_lines(item, "prerequest", create=True)
    if 'pm.globals.get("accessToken")' not in "\n".join(lines):
        lines[:0] = TOKEN_CHECK


def normalize_request_url(item: dict[str, Any]) -> None:
    url = item["request"].get("url", "")
    if not isinstance(url, dict):
        return
    path = raw_request_path(item)
    if not path.startswith("/"):
        path = "/" + path
    url["raw"] = "{{baseUrl}}" + path
    url["host"] = ["{{baseUrl}}"]


def normalize_collection(collection: dict[str, Any], variables: list[dict[str, str]]) -> None:
    collection["variable"] = variables
    collection["auth"] = BEARER_AUTH
    for item in iter_requests(collection.get("item", [])):
        replace_environment_scope(item)
        path = request_path(item)
        normalize_request_url(item)
        if path in PUBLIC_PATHS:
            item["request"]["auth"] = {"type": "noauth"}
            if path == "/api/v1/auth/login":
                ensure_login_extract(item)
        else:
            item["request"]["auth"] = BEARER_AUTH
            ensure_token_check(item)


def normalize_openapi(document: dict[str, Any]) -> None:
    document.pop("security", None)
    methods = {"get", "post", "put", "patch", "delete", "options", "head"}
    for path, path_item in document.get("paths", {}).items():
        if not isinstance(path_item, dict):
            continue
        for method, operation in path_item.items():
            if method.lower() in methods and isinstance(operation, dict):
                operation["security"] = [] if path in PUBLIC_PATHS else [{"bearerAuth": []}]


def validate_collection(collection: dict[str, Any]) -> None:
    variables = {item["key"] for item in collection.get("variable", [])}
    required = {"baseUrl", "username", "password", "accessToken"}
    if not required.issubset(variables):
        raise ValueError(f"Collection 缺少项目变量：{sorted(required - variables)}")

    requests = list(iter_requests(collection.get("item", [])))
    by_path = {request_path(item): item for item in requests}
    discovered = sorted(by_path)
    for public in PUBLIC_PATHS:
        auth_type = by_path.get(public, {}).get("request", {}).get("auth", {}).get("type")
        if auth_type != "noauth":
            raise ValueError(f"公开接口未设置 noauth：{public}；已识别路径：{discovered}")

    for item in requests:
        path = request_path(item)
        if path in PUBLIC_PATHS:
            continue
        auth = item["request"].get("auth", {})
        bearer = auth.get("bearer", [])
        if auth.get("type") != "bearer" or not bearer or bearer[0].get("value") != "{{accessToken}}":
            raise ValueError(f"受保护接口未使用 Bearer {{{{accessToken}}}}：{path}")

    login_script = "\n".join(event_lines(by_path["/api/v1/auth/login"], "test"))
    if 'pm.globals.set("accessToken"' not in login_script:
        raise ValueError("登录接口缺少 accessToken 后置提取")
    if "pm.environment." in json.dumps(collection, ensure_ascii=False):
        raise ValueError("Collection 仍依赖 Environment 变量")


def normalize_directory(output: Path, openapi: Path) -> Path:
    variables = environment_values(output / "urban-safe-priority-local.postman_environment.json")
    smoke = output / "urban-safe-priority-smoke.postman_collection.json"
    full = output / "urban-safe-priority-full.postman_collection.json"
    all_api = output / "urban-safe-priority-all.postman_collection.json"

    normalized: dict[Path, dict[str, Any]] = {}
    for path in (smoke, full, all_api):
        collection = load_json(path)
        normalize_collection(collection, variables)
        validate_collection(collection)
        save_json(path, collection)
        normalized[path] = collection

    primary_collection = deepcopy(normalized[all_api])
    primary_collection.setdefault("info", {})["name"] = "UrbanSafe Priority 全接口与自动验收"
    primary_collection.setdefault("item", []).extend(deepcopy(normalized[full].get("item", [])))
    validate_collection(primary_collection)
    primary = output / "urban-safe-priority-apifox.postman_collection.json"
    save_json(primary, primary_collection)

    document = load_json(openapi)
    normalize_openapi(document)
    save_json(openapi, document)
    return primary


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--openapi", required=True, type=Path)
    args = parser.parse_args()
    primary = normalize_directory(args.output, args.openapi)
    print(f"ApiFox 单文件导入包已生成：{primary.resolve()}")


if __name__ == "__main__":
    main()
