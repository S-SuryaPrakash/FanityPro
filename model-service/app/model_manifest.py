"""Strict loading of the versioned Hugging Face candidate manifest."""

from dataclasses import dataclass
import json
from pathlib import Path
import re
from typing import Any

from .schemas import RiskCategory


PINNED_REVISION_PATTERN = re.compile(r"^[0-9a-f]{40}$")


@dataclass(frozen=True)
class ModelCandidate:
    """Security-relevant configuration for one pinned model candidate."""

    key: str
    model_id: str
    revision: str
    domain_label_mapping: dict[RiskCategory, tuple[str, ...]]


def load_eligible_candidate(path: Path, key: str) -> ModelCandidate:
    """Load one eligible, commit-pinned candidate or fail closed."""

    try:
        manifest: dict[str, Any] = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ValueError("The model candidate manifest is unavailable or invalid.") from error

    matches = [item for item in manifest.get("candidates", []) if item.get("key") == key]
    if len(matches) != 1:
        raise ValueError(f"Expected exactly one model candidate with key '{key}'.")

    selected = matches[0]
    if selected.get("evaluationStatus") != "eligible":
        raise ValueError(f"Model candidate '{key}' is not eligible for loading.")

    model_id = selected.get("modelId")
    revision = selected.get("revision")
    if not isinstance(model_id, str) or not model_id.strip():
        raise ValueError("The selected candidate has no model identifier.")
    if not isinstance(revision, str) or not PINNED_REVISION_PATTERN.fullmatch(revision):
        raise ValueError("The selected candidate must use a 40-character commit revision.")

    raw_mapping = selected.get("domainLabelMapping")
    if not isinstance(raw_mapping, dict) or set(raw_mapping) != {
        category.value for category in RiskCategory
    }:
        raise ValueError(
            "The candidate mapping must cover every domain risk category exactly once."
        )

    mapping: dict[RiskCategory, tuple[str, ...]] = {}
    for category in RiskCategory:
        labels = raw_mapping[category.value]
        if not isinstance(labels, list) or not labels or not all(
            isinstance(label, str) and label.strip() for label in labels
        ):
            raise ValueError(f"The mapping for {category.value} must contain native labels.")
        mapping[category] = tuple(label.strip().lower() for label in labels)

    return ModelCandidate(key, model_id.strip(), revision, mapping)


def validate_native_labels(candidate: ModelCandidate, native_labels: set[str]) -> None:
    """Verify that every configured native label really exists in the loaded model."""

    normalized = {label.strip().lower() for label in native_labels}
    missing = {
        label
        for labels in candidate.domain_label_mapping.values()
        for label in labels
        if label not in normalized
    }
    if missing:
        raise ValueError(
            "The loaded model is missing mapped native labels: "
            + ", ".join(sorted(missing))
        )
