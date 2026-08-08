"""pytest 共享夹具与图片构造工具。"""

from __future__ import annotations

import hashlib
import io
import asyncio

import httpx
import pytest
from PIL import Image

from app.config import get_settings
from app.main import _applicability_provider, _orchestrator, app


MODEL_VERSION = "0.1.0"


class AsgiTestClient:
    """基于 httpx ASGITransport 的轻量同步测试客户端，避开 Starlette TestClient 线程池卡死。"""

    def __init__(self) -> None:
        # base_url 是 httpx 构造绝对请求 URL 的测试域名，不会发起真实网络访问。
        self._base_url = "http://testserver"

    def get(self, url: str, **kwargs) -> httpx.Response:
        """同步执行 GET 请求，兼容现有测试中的 client.get 调用。"""

        return self._request("GET", url, **kwargs)

    def post(self, url: str, **kwargs) -> httpx.Response:
        """同步执行 POST 请求，兼容现有测试中的 client.post 调用。"""

        return self._request("POST", url, **kwargs)

    def _request(self, method: str, url: str, **kwargs) -> httpx.Response:
        """在独立事件循环中直接调用 FastAPI ASGI 应用，避免经过真实端口和同步线程池。"""

        async def send_request() -> httpx.Response:
            """创建一次短生命周期 ASGI 客户端并返回完整响应对象。"""

            # transport 表示内存内 ASGI 传输层，请求直接进入 app，不经过网络套接字。
            transport = httpx.ASGITransport(app=app)
            # client 表示本次请求的异步 HTTP 客户端，退出上下文时释放 multipart 等资源。
            async with httpx.AsyncClient(transport=transport, base_url=self._base_url) as client:
                return await client.request(method, url, **kwargs)

        return asyncio.run(send_request())


@pytest.fixture
def client(monkeypatch):
    """接口测试固定使用显式 MOCK，避免读取开发机真实模型和图形处理器。"""

    monkeypatch.setenv("URBAN_SAFE_AI_REAL_MODEL_STATUS", "UNAVAILABLE")
    monkeypatch.setenv("URBAN_SAFE_AI_MODEL_CATALOG_PATH", "test-missing-catalog.json")
    # 接口测试默认关闭本地语义模型，稳定验证 UNCERTAIN fail-open，不读取开发机权重。
    monkeypatch.setenv("URBAN_SAFE_AI_APPLICABILITY_ENABLED", "false")
    # 单元测试只需覆盖大小超限分支；固定较小阈值可以避免 ASGI multipart 测试栈写入大临时文件。
    monkeypatch.setenv("URBAN_SAFE_AI_MAX_IMAGE_SIZE_BYTES", "4096")
    monkeypatch.delenv("AI_REAL_MODEL_STATUS", raising=False)
    monkeypatch.delenv("AI_MODEL_CATALOG_PATH", raising=False)
    monkeypatch.delenv("AI_APPLICABILITY_ENABLED", raising=False)
    monkeypatch.delenv("AI_MAX_IMAGE_SIZE_BYTES", raising=False)
    get_settings.cache_clear()
    _orchestrator.cache_clear()
    _applicability_provider.cache_clear()
    # 显式初始化一次编排器，替代 TestClient lifespan 对启动门禁的触发。
    _orchestrator()
    yield AsgiTestClient()
    _applicability_provider.cache_clear()
    _orchestrator.cache_clear()
    get_settings.cache_clear()


def make_image_bytes(fmt: str, size=(64, 64), pixel_value: int = 128) -> bytes:
    """构造一张纯色图片并返回指定格式的字节。"""

    color = (pixel_value % 256, (pixel_value * 3) % 256, (pixel_value * 7) % 256)
    img = Image.new("RGB", size, color)
    buf = io.BytesIO()
    img.save(buf, format=fmt)
    return buf.getvalue()


def seed_branch(image_bytes: bytes, version: str = MODEL_VERSION) -> int:
    """计算确定性 MOCK 适配器为该图片选择的分支。"""

    digest = hashlib.sha256(image_bytes + version.encode("utf-8")).hexdigest()
    return int(digest[0:2], 16) % 4


def find_image_for_branch(target: int, version: str = MODEL_VERSION) -> bytes:
    """搜索一张真实 PNG 图片，使其确定性分支等于目标值。"""

    for pixel_value in range(20000):
        image_bytes = make_image_bytes("PNG", (64, 64), pixel_value)
        if seed_branch(image_bytes, version) == target:
            return image_bytes
    raise RuntimeError("未能找到目标分支对应的图片")


@pytest.fixture
def jpeg_bytes() -> bytes:
    return make_image_bytes("JPEG")


@pytest.fixture
def png_bytes() -> bytes:
    return make_image_bytes("PNG")


@pytest.fixture
def webp_bytes() -> bytes:
    return make_image_bytes("WEBP")


@pytest.fixture
def tiny_png() -> bytes:
    return make_image_bytes("PNG", (16, 16))


@pytest.fixture
def corrupted_bytes() -> bytes:
    return b"\xff\xd8\xff\xe0not-a-real-image-bytes"


@pytest.fixture
def gif_bytes() -> bytes:
    return make_image_bytes("GIF")


@pytest.fixture
def applicable_one_image() -> bytes:
    return find_image_for_branch(0)


@pytest.fixture
def applicable_two_image() -> bytes:
    return find_image_for_branch(1)


@pytest.fixture
def no_defect_image() -> bytes:
    return find_image_for_branch(2)


@pytest.fixture
def not_applicable_image() -> bytes:
    return find_image_for_branch(3)


def metadata_json(request_id: str = "req-001", mode: str = "MOCK") -> str:
    import json

    return json.dumps(
        {
            "requestId": request_id,
            "mode": mode,
            "assetId": "asset-1",
            "filename": "inspection.jpg",
            "contentType": "image/jpeg",
            "sha256": "abc",
            "requestedModelId": "AI-DEFECT-MOCK-001",
        }
    )


def post_inference(client: AsgiTestClient, image_bytes: bytes, request_id: str = "req-001"):
    """按内部接口格式提交图片推理请求。"""

    return client.post(
        "/internal/api/v1/ai/inferences",
        files={"file": ("image.jpg", image_bytes, "image/jpeg")},
        data={"metadata": metadata_json(request_id)},
    )
