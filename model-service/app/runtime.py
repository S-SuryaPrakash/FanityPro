"""Hugging Face runtime isolated behind a small provider-neutral protocol."""

from dataclasses import dataclass
from pathlib import Path
from threading import Lock
from typing import Any, Protocol, Sequence

from .model_manifest import ModelCandidate, load_eligible_candidate, validate_native_labels
from .schemas import RiskCategory, SequenceInput


@dataclass(frozen=True)
class RuntimePrediction:
    """Raw provider-neutral evidence produced before HTTP serialization."""

    sequence_id: str
    scores: dict[RiskCategory, float]
    input_truncated: bool


class RiskModelRuntime(Protocol):
    """Replaceable model runtime used by production and lightweight contract tests."""

    @property
    def ready(self) -> bool: ...

    @property
    def model_id(self) -> str: ...

    @property
    def revision(self) -> str: ...

    def load(self) -> None: ...

    def predict(self, sequences: Sequence[SequenceInput]) -> list[RuntimePrediction]: ...


class HuggingFaceRiskRuntime:
    """Loads one commit-pinned safetensors model and performs batched CPU inference."""

    def __init__(
        self,
        candidate_manifest: Path,
        candidate_key: str,
        cache_directory: Path,
        inference_batch_size: int,
    ) -> None:
        self._candidate: ModelCandidate = load_eligible_candidate(
            candidate_manifest, candidate_key
        )
        self._cache_directory = cache_directory
        self._inference_batch_size = inference_batch_size
        self._model: Any | None = None
        self._tokenizer: Any | None = None
        self._native_labels: dict[int, str] = {}
        self._maximum_tokens = 512
        self._lock = Lock()

    @property
    def ready(self) -> bool:
        return self._model is not None and self._tokenizer is not None

    @property
    def model_id(self) -> str:
        return self._candidate.model_id

    @property
    def revision(self) -> str:
        return self._candidate.revision

    def load(self) -> None:
        """Download/load the pinned artifacts once during the application lifespan."""

        try:
            from transformers import AutoModelForSequenceClassification, AutoTokenizer
        except ImportError as error:
            raise RuntimeError(
                "Pinned model dependencies are not installed; install requirements.txt."
            ) from error

        self._cache_directory.mkdir(parents=True, exist_ok=True)
        tokenizer = AutoTokenizer.from_pretrained(
            self.model_id,
            revision=self.revision,
            cache_dir=self._cache_directory,
            trust_remote_code=False,
        )
        model = AutoModelForSequenceClassification.from_pretrained(
            self.model_id,
            revision=self.revision,
            cache_dir=self._cache_directory,
            trust_remote_code=False,
            use_safetensors=True,
        )
        native_labels = {
            int(index): str(label).strip().lower()
            for index, label in model.config.id2label.items()
        }
        validate_native_labels(self._candidate, set(native_labels.values()))

        configured_maximum = int(getattr(tokenizer, "model_max_length", 512))
        self._maximum_tokens = min(configured_maximum, 512)
        model.eval()
        self._native_labels = native_labels
        self._tokenizer = tokenizer
        self._model = model

    def predict(self, sequences: Sequence[SequenceInput]) -> list[RuntimePrediction]:
        if not self.ready:
            raise RuntimeError("The model runtime is not ready.")

        try:
            import torch
        except ImportError as error:
            raise RuntimeError("PyTorch is unavailable.") from error

        predictions: list[RuntimePrediction] = []
        with self._lock:
            for start in range(0, len(sequences), self._inference_batch_size):
                batch = sequences[start : start + self._inference_batch_size]
                texts = [item.text for item in batch]
                input_truncated = [
                    len(
                        self._tokenizer.encode(
                            text, add_special_tokens=True, truncation=False
                        )
                    )
                    > self._maximum_tokens
                    for text in texts
                ]
                encoded = self._tokenizer(
                    texts,
                    padding=True,
                    truncation=True,
                    max_length=self._maximum_tokens,
                    return_tensors="pt",
                )
                with torch.inference_mode():
                    logits = self._model(**encoded).logits
                    probabilities = torch.sigmoid(logits).cpu().tolist()

                for item, raw_scores, truncated in zip(
                    batch, probabilities, input_truncated, strict=True
                ):
                    native_scores = {
                        self._native_labels[index]: float(value)
                        for index, value in enumerate(raw_scores)
                    }
                    domain_scores = {
                        category: max(native_scores[label] for label in labels)
                        for category, labels in self._candidate.domain_label_mapping.items()
                    }
                    predictions.append(
                        RuntimePrediction(item.sequence_id, domain_scores, truncated)
                    )
        return predictions
