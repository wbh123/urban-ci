def test_public_health_check(client):
    response = client.get("/api/ai/health")

    assert response.status_code == 200
    assert response.json()["status"] == "healthy"
    assert response.json()["service"] == "urban-safe-ai-service"


def test_internal_health_check(client):
    response = client.get("/internal/api/v1/ai/health")

    assert response.status_code == 200
    assert response.json()["status"] == "healthy"


def test_current_model_is_explicit_mock(client):
    response = client.get("/internal/api/v1/ai/models/current")

    assert response.status_code == 200
    body = response.json()
    # 模型信息必须明确标记为 MOCK，不得伪装为真实模型。
    assert body["mode"] == "MOCK"
    assert body["status"] == "MOCK"
    assert body["modelId"] == "AI-DEFECT-MOCK-001"
    assert body["version"] == "0.1.0"
    assert body["license"] == "PROJECT-INTERNAL-MOCK"
    assert "crack" in body["supportedDefects"]
