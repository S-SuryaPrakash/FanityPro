"""FastAPI entry point for the internal V1 model-inference service."""

from collections.abc import Callable
from contextlib import asynccontextmanager
import re
from typing import Any
from uuid import uuid4

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from .runtime import HuggingFaceRiskRuntime, RiskModelRuntime
from .schemas import (
    BatchClassificationRequest,
    BatchClassificationResponse,
    LiveResponse,
    PredictionResponse,
    ProblemDetails,
    ReadyResponse,
    RiskScores,
)
from .service import (
    ClassificationApplicationService,
    ModelContractError,
    ModelNotReadyError,
    RequestLimitError,
)
from .settings import ModelServiceSettings


CORRELATION_ID_PATTERN = re.compile(r"^[A-Za-z0-9._:-]{1,128}$")


def _problem(
    request: Request,
    *,
    status: int,
    title: str,
    detail: str,
    error_code: str,
    errors: list[dict[str, Any]] | None = None,
) -> JSONResponse:
    body: dict[str, Any] = {
        "type": f"urn:fanitypro:problem:{error_code.lower().replace('_', '-')}",
        "title": title,
        "status": status,
        "detail": detail,
        "instance": request.url.path,
        "errorCode": error_code,
        "correlationId": request.state.correlation_id,
    }
    if errors:
        body["errors"] = errors
    return JSONResponse(body, status_code=status, media_type="application/problem+json")


def create_app(
    settings: ModelServiceSettings | None = None,
    runtime_factory: Callable[[], RiskModelRuntime] | None = None,
) -> FastAPI:
    """Create an app; runtime injection keeps API tests independent from model downloads."""

    effective_settings = settings or ModelServiceSettings()

    def default_runtime_factory() -> RiskModelRuntime:
        return HuggingFaceRiskRuntime(
            effective_settings.candidate_manifest,
            effective_settings.candidate_key,
            effective_settings.model_cache_directory,
            effective_settings.inference_batch_size,
        )

    factory = runtime_factory or default_runtime_factory

    @asynccontextmanager
    async def lifespan(application: FastAPI):
        application.state.runtime = None
        application.state.model_load_failed = False
        try:
            runtime = factory()
            runtime.load()
            application.state.runtime = runtime
        except Exception:
            # Do not expose dependency, filesystem, or provider details through health APIs.
            application.state.model_load_failed = True
        yield
        application.state.runtime = None

    application = FastAPI(
        title="Content Filter Model Service",
        summary="Internal multi-label risk evidence API",
        version="1.0.0-provisional",
        lifespan=lifespan,
        docs_url="/docs",
        redoc_url=None,
    )
    application.state.settings = effective_settings

    @application.middleware("http")
    async def correlation_id_middleware(request: Request, call_next):
        supplied = request.headers.get("X-Correlation-ID", "")
        request.state.correlation_id = (
            supplied if CORRELATION_ID_PATTERN.fullmatch(supplied) else str(uuid4())
        )
        response = await call_next(request)
        response.headers["X-Correlation-ID"] = request.state.correlation_id
        return response

    @application.exception_handler(RequestValidationError)
    async def validation_error_handler(
        request: Request, error: RequestValidationError
    ) -> JSONResponse:
        safe_errors = [
            {
                "location": [str(part) for part in issue["loc"]],
                "message": issue["msg"],
                "type": issue["type"],
            }
            for issue in error.errors()
        ]
        return _problem(
            request,
            status=422,
            title="Request validation failed",
            detail="The classification request does not satisfy the V1 API contract.",
            error_code="INVALID_REQUEST",
            errors=safe_errors,
        )

    @application.exception_handler(Exception)
    async def unexpected_error_handler(request: Request, error: Exception) -> JSONResponse:
        del error
        return _problem(
            request,
            status=500,
            title="Internal service error",
            detail="The model service could not complete the request.",
            error_code="INTERNAL_SERVICE_ERROR",
        )

    @application.get("/live", response_model=LiveResponse, tags=["Health"])
    def live() -> LiveResponse:
        return LiveResponse(service=effective_settings.service_name)

    @application.get(
        "/ready",
        response_model=ReadyResponse,
        responses={
            503: {
                "model": ProblemDetails,
                "description": "The pinned model is not ready.",
            }
        },
        tags=["Health"],
    )
    def ready(request: Request):
        runtime: RiskModelRuntime | None = request.app.state.runtime
        if runtime is None or not runtime.ready:
            return _problem(
                request,
                status=503,
                title="Model not ready",
                detail="The pinned model is not available for inference.",
                error_code="MODEL_NOT_READY",
            )
        return ReadyResponse(
            model=runtime.model_id,
            revision=runtime.revision,
            evaluationStatus=effective_settings.evaluation_status,
            approvedForProduction=effective_settings.approved_for_production,
        )

    @application.post(
        "/api/v1/classify/batch",
        response_model=BatchClassificationResponse,
        response_model_by_alias=True,
        responses={
            422: {
                "model": ProblemDetails,
                "description": "The request violates a V1 validation or resource limit.",
            },
            502: {
                "model": ProblemDetails,
                "description": "The model returned an invalid internal contract.",
            },
            503: {
                "model": ProblemDetails,
                "description": "The pinned model is not ready.",
            },
            500: {
                "model": ProblemDetails,
                "description": "An unexpected internal service error occurred.",
            },
        },
        tags=["Classification"],
    )
    def classify(request_body: BatchClassificationRequest, request: Request):
        runtime: RiskModelRuntime | None = request.app.state.runtime
        if runtime is None:
            return _problem(
                request,
                status=503,
                title="Model not ready",
                detail="The pinned model is not available for inference.",
                error_code="MODEL_NOT_READY",
            )
        service = ClassificationApplicationService(runtime, effective_settings)
        try:
            predictions = service.classify(request_body)
        except RequestLimitError as error:
            return _problem(
                request,
                status=422,
                title="Request limit exceeded",
                detail=str(error),
                error_code="REQUEST_LIMIT_EXCEEDED",
            )
        except ModelNotReadyError:
            return _problem(
                request,
                status=503,
                title="Model not ready",
                detail="The pinned model is not available for inference.",
                error_code="MODEL_NOT_READY",
            )
        except ModelContractError:
            return _problem(
                request,
                status=502,
                title="Invalid model response",
                detail="The model runtime returned evidence that could not be correlated safely.",
                error_code="INVALID_MODEL_RESPONSE",
            )
        except Exception:
            return _problem(
                request,
                status=503,
                title="Model inference unavailable",
                detail="The model could not complete this inference request.",
                error_code="MODEL_INFERENCE_FAILED",
            )

        return BatchClassificationResponse(
            model=runtime.model_id,
            revision=runtime.revision,
            evaluationStatus=effective_settings.evaluation_status,
            approvedForProduction=effective_settings.approved_for_production,
            predictions=[
                PredictionResponse(
                    sequenceId=prediction.sequence_id,
                    scores=RiskScores.model_validate(
                        {category.value: score for category, score in prediction.scores.items()}
                    ),
                    inputTruncated=prediction.input_truncated,
                )
                for prediction in predictions
            ],
        )

    return application


app = create_app()
