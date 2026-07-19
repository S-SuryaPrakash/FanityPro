"""Application service that enforces limits and validates model correlation."""

from collections import Counter
from math import isfinite

from .runtime import RiskModelRuntime, RuntimePrediction
from .schemas import BatchClassificationRequest, RiskCategory
from .settings import ModelServiceSettings


class RequestLimitError(ValueError):
    """The submitted batch exceeds a configured V1 resource limit."""


class ModelNotReadyError(RuntimeError):
    """Inference cannot run until the pinned model is loaded."""


class ModelContractError(RuntimeError):
    """A model runtime response does not satisfy the internal correlation contract."""


class ClassificationApplicationService:
    """Coordinates one bounded inference request without applying business policy."""

    def __init__(self, runtime: RiskModelRuntime, settings: ModelServiceSettings) -> None:
        self._runtime = runtime
        self._settings = settings

    def classify(self, request: BatchClassificationRequest) -> list[RuntimePrediction]:
        if len(request.sequences) > self._settings.max_batch_size:
            raise RequestLimitError(
                f"A batch may contain at most {self._settings.max_batch_size} sequences."
            )

        oversized = [
            item.sequence_id
            for item in request.sequences
            if len(item.text) > self._settings.max_text_length
        ]
        if oversized:
            raise RequestLimitError(
                f"Every sequence must contain at most {self._settings.max_text_length} characters."
            )

        total_characters = sum(len(item.text) for item in request.sequences)
        if total_characters > self._settings.max_total_text_length:
            raise RequestLimitError(
                "The total text in this batch exceeds the configured character limit."
            )
        if not self._runtime.ready:
            raise ModelNotReadyError("The model service is not ready for inference.")

        predictions = self._runtime.predict(request.sequences)
        requested_ids = [item.sequence_id for item in request.sequences]
        returned_ids = [item.sequence_id for item in predictions]
        counts = Counter(returned_ids)
        if set(returned_ids) != set(requested_ids) or any(count != 1 for count in counts.values()):
            raise ModelContractError(
                "The model did not return exactly one prediction for every sequence ID."
            )

        expected_categories = set(RiskCategory)
        for prediction in predictions:
            if set(prediction.scores) != expected_categories:
                raise ModelContractError(
                    "Every prediction must contain all five domain risk scores."
                )
            if any(
                not isfinite(score) or score < 0.0 or score > 1.0
                for score in prediction.scores.values()
            ):
                raise ModelContractError(
                    "Every model score must be a finite probability between zero and one."
                )

        by_identifier = {prediction.sequence_id: prediction for prediction in predictions}
        return [by_identifier[identifier] for identifier in requested_ids]
