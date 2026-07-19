"""Contract tests for the FastAPI boundary without downloading model weights."""

from pathlib import Path
import sys

from fastapi.testclient import TestClient


MODEL_SERVICE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(MODEL_SERVICE_ROOT))

from app.main import create_app  # noqa: E402
from app.runtime import RuntimePrediction  # noqa: E402
from app.schemas import RiskCategory  # noqa: E402
from app.settings import ModelServiceSettings  # noqa: E402


ALL_SCORES = {
    RiskCategory.THREAT: 0.01,
    RiskCategory.HATE_OR_IDENTITY_ATTACK: 0.02,
    RiskCategory.HARASSMENT_OR_INSULT: 0.03,
    RiskCategory.OBSCENE_OR_PROFANE: 0.04,
    RiskCategory.GENERAL_TOXICITY: 0.05,
}


class FakeRuntime:
    """Predictable test double that intentionally returns results in reverse order."""

    model_id = "example/test-model"
    revision = "1" * 40

    def __init__(
        self,
        *,
        become_ready: bool = True,
        invalid_contract: bool = False,
        invalid_scores: bool = False,
    ) -> None:
        self._ready = False
        self._become_ready = become_ready
        self._invalid_contract = invalid_contract
        self._invalid_scores = invalid_scores
        self.load_calls = 0

    @property
    def ready(self) -> bool:
        return self._ready

    def load(self) -> None:
        self.load_calls += 1
        self._ready = self._become_ready

    def predict(self, sequences):
        selected = list(reversed(sequences))
        if self._invalid_contract:
            selected = selected[:1]
        results = []
        for item in selected:
            scores = dict(ALL_SCORES)
            if self._invalid_scores:
                scores[RiskCategory.THREAT] = float("nan")
            results.append(RuntimePrediction(item.sequence_id, scores, False))
        return results


class FailingInferenceRuntime(FakeRuntime):
    """Ready runtime whose provider call fails after startup."""

    def predict(self, sequences):
        del sequences
        raise RuntimeError("provider unavailable")


def build_client(runtime: FakeRuntime, **settings_overrides):
    settings = ModelServiceSettings(**settings_overrides)
    app = create_app(settings, lambda: runtime)
    return TestClient(app), app


def valid_payload():
    return {
        "sequences": [
            {
                "sequenceId": "sheet-0-row-0",
                "text": "Thank you for contacting support.",
                "conversationId": "conversation-42",
                "speakerRole": "agent",
                "language": "en",
            },
            {
                "sequenceId": "sheet-0-row-1",
                "text": "This is a second sequence.",
            },
        ]
    }


def test_lifespan_loads_model_once_and_health_contracts_are_separate():
    runtime = FakeRuntime()
    client, _ = build_client(runtime)

    with client:
        live = client.get("/live")
        ready = client.get("/ready")
        ready_again = client.get("/ready")

    assert runtime.load_calls == 1
    assert live.status_code == 200
    assert live.json()["status"] == "LIVE"
    assert ready.status_code == 200
    assert ready.json() == {
        "status": "READY",
        "model": runtime.model_id,
        "revision": runtime.revision,
        "evaluationStatus": "PROVISIONAL",
        "approvedForProduction": False,
    }
    assert ready_again.status_code == 200


def test_unavailable_model_keeps_liveness_up_but_readiness_down():
    runtime = FakeRuntime(become_ready=False)
    client, _ = build_client(runtime)

    with client:
        assert client.get("/live").status_code == 200
        response = client.get("/ready")

    assert response.status_code == 503
    assert response.headers["content-type"].startswith("application/problem+json")
    assert response.json()["errorCode"] == "MODEL_NOT_READY"


def test_startup_failure_is_live_but_never_ready():
    settings = ModelServiceSettings()

    def failing_factory():
        raise RuntimeError("model load failed")

    app = create_app(settings, failing_factory)
    with TestClient(app) as client:
        live = client.get("/live")
        ready = client.get("/ready")

    assert live.status_code == 200
    assert ready.status_code == 503
    assert ready.json()["errorCode"] == "MODEL_NOT_READY"


def test_batch_returns_all_domain_scores_in_request_order_without_policy_decisions():
    runtime = FakeRuntime()
    client, _ = build_client(runtime)

    with client:
        response = client.post("/api/v1/classify/batch", json=valid_payload())

    assert response.status_code == 200
    body = response.json()
    assert body["model"] == runtime.model_id
    assert body["revision"] == runtime.revision
    assert body["evaluationStatus"] == "PROVISIONAL"
    assert body["approvedForProduction"] is False
    assert [item["sequenceId"] for item in body["predictions"]] == [
        "sheet-0-row-0",
        "sheet-0-row-1",
    ]
    assert set(body["predictions"][0]["scores"]) == {
        category.value for category in RiskCategory
    }
    assert "primaryCategory" not in body["predictions"][0]
    assert "severity" not in body["predictions"][0]


def test_duplicate_sequence_ids_return_safe_problem_details():
    runtime = FakeRuntime()
    client, _ = build_client(runtime)
    payload = valid_payload()
    payload["sequences"][1]["sequenceId"] = payload["sequences"][0]["sequenceId"]

    with client:
        response = client.post("/api/v1/classify/batch", json=payload)

    assert response.status_code == 422
    assert response.json()["errorCode"] == "INVALID_REQUEST"
    assert "Thank you" not in response.text


def test_configured_batch_limit_is_enforced_below_the_public_contract_maximum():
    runtime = FakeRuntime()
    client, _ = build_client(runtime, max_batch_size=1)

    with client:
        response = client.post("/api/v1/classify/batch", json=valid_payload())

    assert response.status_code == 422
    assert response.json()["errorCode"] == "REQUEST_LIMIT_EXCEEDED"


def test_unsupported_language_and_oversized_text_are_rejected():
    runtime = FakeRuntime()
    client, _ = build_client(runtime)
    unsupported = valid_payload()
    unsupported["sequences"] = [
        {"sequenceId": "one", "text": "Hola", "language": "es"}
    ]
    oversized = {
        "sequences": [{"sequenceId": "one", "text": "x" * 4_001}]
    }

    with client:
        unsupported_response = client.post(
            "/api/v1/classify/batch", json=unsupported
        )
        oversized_response = client.post("/api/v1/classify/batch", json=oversized)

    assert unsupported_response.status_code == 422
    assert oversized_response.status_code == 422
    assert unsupported_response.json()["errorCode"] == "INVALID_REQUEST"
    assert oversized_response.json()["errorCode"] == "INVALID_REQUEST"


def test_invalid_runtime_correlation_fails_closed():
    runtime = FakeRuntime(invalid_contract=True)
    client, _ = build_client(runtime)

    with client:
        response = client.post("/api/v1/classify/batch", json=valid_payload())

    assert response.status_code == 502
    assert response.json()["errorCode"] == "INVALID_MODEL_RESPONSE"


def test_non_finite_model_score_fails_closed():
    runtime = FakeRuntime(invalid_scores=True)
    client, _ = build_client(runtime)

    with client:
        response = client.post("/api/v1/classify/batch", json=valid_payload())

    assert response.status_code == 502
    assert response.json()["errorCode"] == "INVALID_MODEL_RESPONSE"


def test_provider_inference_failure_returns_structured_service_unavailable():
    runtime = FailingInferenceRuntime()
    client, _ = build_client(runtime)

    with client:
        response = client.post("/api/v1/classify/batch", json=valid_payload())

    assert response.status_code == 503
    assert response.json()["errorCode"] == "MODEL_INFERENCE_FAILED"


def test_correlation_id_is_preserved_and_openapi_documents_the_contract():
    runtime = FakeRuntime()
    client, _ = build_client(runtime)

    with client:
        live = client.get("/live", headers={"X-Correlation-ID": "request-123"})
        schema = client.get("/openapi.json").json()

    assert live.headers["X-Correlation-ID"] == "request-123"
    assert set(schema["paths"]) >= {
        "/live",
        "/ready",
        "/api/v1/classify/batch",
    }
