"""Dependency-free dataset validation and multilabel metric helpers."""

from __future__ import annotations

import json
from collections import Counter
from pathlib import Path
from typing import Any, Iterable

RISK_LABELS = (
    "THREAT",
    "HATE_OR_IDENTITY_ATTACK",
    "HARASSMENT_OR_INSULT",
    "OBSCENE_OR_PROFANE",
    "GENERAL_TOXICITY",
)
REQUIRED_FIELDS = {
    "dataset_version",
    "id",
    "split",
    "text",
    "expected_labels",
    "language",
    "speaker_role",
    "content_origin",
    "slice_tags",
    "review_status",
    "annotation_notes",
}
REQUIRED_DOMAIN_SLICES = (
    "benign_identity_mention",
    "quoted_harm",
    "context_sensitive",
    "direct_threat",
    "identity_attack",
    "direct_insult",
    "profanity",
    "hostile_tone",
    "multi_label",
)


def load_jsonl(path: Path) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as source:
        for line_number, line in enumerate(source, start=1):
            if not line.strip():
                continue
            try:
                value = json.loads(line)
            except json.JSONDecodeError as exception:
                raise ValueError(f"{path}:{line_number}: invalid JSON: {exception.msg}") from exception
            if not isinstance(value, dict):
                raise ValueError(f"{path}:{line_number}: each line must be a JSON object")
            records.append(value)
    return records


def validate_dataset(records: list[dict[str, Any]]) -> list[str]:
    errors: list[str] = []
    seen_ids: set[str] = set()
    seen_texts: set[str] = set()
    versions: set[str] = set()
    positive_coverage: dict[str, Counter[str]] = {
        "calibration": Counter(),
        "test": Counter(),
    }

    if not records:
        return ["dataset must contain at least one record"]

    for index, record in enumerate(records, start=1):
        prefix = f"record {index}"
        missing = REQUIRED_FIELDS - record.keys()
        if missing:
            errors.append(f"{prefix}: missing fields: {', '.join(sorted(missing))}")
            continue

        record_id = record["id"]
        if not isinstance(record_id, str) or not record_id.strip():
            errors.append(f"{prefix}: id must be a non-blank string")
        elif record_id in seen_ids:
            errors.append(f"{prefix}: duplicate id {record_id}")
        else:
            seen_ids.add(record_id)

        versions.add(record["dataset_version"])
        split = record["split"]
        if split not in positive_coverage:
            errors.append(f"{prefix}: split must be calibration or test")

        if not isinstance(record["text"], str) or not record["text"].strip():
            errors.append(f"{prefix}: text must be a non-blank string")
        else:
            normalised_text = " ".join(record["text"].lower().split())
            if normalised_text in seen_texts:
                errors.append(f"{prefix}: duplicate normalised text")
            else:
                seen_texts.add(normalised_text)
        if record["language"] != "en":
            errors.append(f"{prefix}: V1 evaluation supports English only")
        if record["review_status"] not in {"draft", "reviewed", "adjudicated"}:
            errors.append(f"{prefix}: invalid review_status")

        labels = record["expected_labels"]
        if not isinstance(labels, list) or len(labels) != len(set(labels)):
            errors.append(f"{prefix}: expected_labels must be a list without duplicates")
        else:
            unknown = set(labels) - set(RISK_LABELS)
            if unknown:
                errors.append(f"{prefix}: unknown labels: {', '.join(sorted(unknown))}")
            elif split in positive_coverage:
                positive_coverage[split].update(labels)

        tags = record["slice_tags"]
        if not isinstance(tags, list) or not tags or any(
            not isinstance(tag, str) or not tag.strip() for tag in tags
        ):
            errors.append(f"{prefix}: slice_tags must contain non-blank strings")

    if len(versions) != 1:
        errors.append("dataset must contain exactly one dataset_version")
    for split, coverage in positive_coverage.items():
        missing_labels = set(RISK_LABELS) - coverage.keys()
        if missing_labels:
            errors.append(
                f"{split} split has no positive example for: "
                + ", ".join(sorted(missing_labels))
            )
    return errors


def validate_domain_coverage(
    records: list[dict[str, Any]],
    minimum_examples_per_split: int = 100,
    minimum_positives_per_label: int = 12,
    minimum_examples_per_slice: int = 4,
) -> list[str]:
    """Check smoke-corpus coverage without claiming production readiness."""

    errors: list[str] = []
    for split in ("calibration", "test"):
        rows = [record for record in records if record.get("split") == split]
        if len(rows) < minimum_examples_per_split:
            errors.append(
                f"{split} split requires at least {minimum_examples_per_split} examples; "
                f"found {len(rows)}"
            )
        for label in RISK_LABELS:
            positive_count = sum(label in record.get("expected_labels", []) for record in rows)
            if positive_count < minimum_positives_per_label:
                errors.append(
                    f"{split} split requires at least {minimum_positives_per_label} "
                    f"positive examples for {label}; found {positive_count}"
                )
        for tag in REQUIRED_DOMAIN_SLICES:
            slice_count = sum(tag in record.get("slice_tags", []) for record in rows)
            if slice_count < minimum_examples_per_slice:
                errors.append(
                    f"{split} split requires at least {minimum_examples_per_slice} "
                    f"examples for slice {tag}; found {slice_count}"
                )
        origins = {record.get("content_origin") for record in rows}
        if not {"synthetic_human", "synthetic_ai"}.issubset(origins):
            errors.append(f"{split} split must cover synthetic human and AI origins")
    return errors


def validate_release_readiness(records: list[dict[str, Any]]) -> list[str]:
    """Enforce the human-annotation gate separately from structural validity."""

    errors = validate_domain_coverage(records)
    non_adjudicated = sum(
        record.get("review_status") != "adjudicated" for record in records
    )
    if non_adjudicated:
        errors.append(
            f"{non_adjudicated} examples are not independently reviewed and adjudicated"
        )
    return errors


def calculate_metrics(
    dataset: Iterable[dict[str, Any]],
    predictions: dict[str, dict[str, Any]],
    thresholds: dict[str, float],
) -> dict[str, Any]:
    rows = list(dataset)
    counts = {label: Counter() for label in RISK_LABELS}
    slice_counts: dict[str, Counter[str]] = {}
    latency_values: list[float] = []

    for row in rows:
        prediction = predictions[row["id"]]
        expected = set(row["expected_labels"])
        predicted = {
            label
            for label in RISK_LABELS
            if float(prediction["scores"].get(label, 0.0)) >= thresholds[label]
        }
        latency_values.append(float(prediction.get("latency_ms", 0.0)))

        for label in RISK_LABELS:
            actual_positive = label in expected
            predicted_positive = label in predicted
            outcome = _outcome(actual_positive, predicted_positive)
            counts[label][outcome] += 1

        actual_any = bool(expected)
        predicted_any = bool(predicted)
        for tag in row["slice_tags"]:
            slice_counts.setdefault(tag, Counter())[
                _outcome(actual_any, predicted_any)
            ] += 1

    per_category = {label: _metrics(value) for label, value in counts.items()}
    combined = Counter()
    for value in counts.values():
        combined.update(value)

    return {
        "exampleCount": len(rows),
        "perCategory": per_category,
        "microAverage": _metrics(combined),
        "macroAverage": {
            metric: _mean(per_category[label][metric] for label in RISK_LABELS)
            for metric in ("precision", "recall", "f1", "falsePositiveRate", "falseNegativeRate")
        },
        "sliceAnyFlag": {
            tag: _metrics(value) for tag, value in sorted(slice_counts.items())
        },
        "latencyMs": {
            "mean": _mean(latency_values),
            "max": max(latency_values, default=0.0),
        },
    }


def _outcome(actual_positive: bool, predicted_positive: bool) -> str:
    if actual_positive and predicted_positive:
        return "tp"
    if actual_positive:
        return "fn"
    if predicted_positive:
        return "fp"
    return "tn"


def _metrics(counts: Counter[str]) -> dict[str, float | int]:
    tp, fp, fn, tn = (counts[key] for key in ("tp", "fp", "fn", "tn"))
    precision = _divide(tp, tp + fp)
    recall = _divide(tp, tp + fn)
    return {
        "truePositive": tp,
        "falsePositive": fp,
        "falseNegative": fn,
        "trueNegative": tn,
        "precision": precision,
        "recall": recall,
        "f1": _divide(2 * precision * recall, precision + recall),
        "falsePositiveRate": _divide(fp, fp + tn),
        "falseNegativeRate": _divide(fn, fn + tp),
    }


def _divide(numerator: float, denominator: float) -> float:
    return numerator / denominator if denominator else 0.0


def _mean(values: Iterable[float]) -> float:
    collected = list(values)
    return sum(collected) / len(collected) if collected else 0.0
