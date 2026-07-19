"""Run a pinned Hugging Face candidate and emit provider-neutral scores."""

from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path
from typing import Any

from evaluation_lib import RISK_LABELS, load_jsonl, validate_dataset

MODEL_SERVICE_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DATASET = (
    MODEL_SERVICE_ROOT / "evaluation" / "datasets" / "v1-domain-synthetic.jsonl"
)
DEFAULT_MANIFEST = MODEL_SERVICE_ROOT / "evaluation" / "candidate-models.json"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--candidate", required=True)
    parser.add_argument("--dataset", type=Path, default=DEFAULT_DATASET)
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--split", choices=("calibration", "test"), default="test")
    parser.add_argument("--batch-size", type=int, default=8)
    parser.add_argument("--output", type=Path)
    arguments = parser.parse_args()

    if arguments.batch_size < 1:
        parser.error("--batch-size must be positive")

    dataset = load_jsonl(arguments.dataset)
    errors = validate_dataset(dataset)
    if errors:
        print("Dataset validation failed: " + "; ".join(errors), file=sys.stderr)
        return 1
    selected = [row for row in dataset if row["split"] == arguments.split]
    candidate = load_candidate(arguments.manifest, arguments.candidate)
    if candidate.get("evaluationStatus") != "eligible":
        print(
            "Candidate is not eligible: " + candidate.get("rejectionReason", "unspecified reason"),
            file=sys.stderr,
        )
        return 3

    try:
        import torch
        from transformers import AutoModelForSequenceClassification, AutoTokenizer
    except ImportError:
        print(
            "Install pinned evaluation dependencies with "
            "'python -m pip install -r requirements-evaluation.txt'.",
            file=sys.stderr,
        )
        return 2

    tokenizer = AutoTokenizer.from_pretrained(
        candidate["modelId"], revision=candidate["revision"]
    )
    model = AutoModelForSequenceClassification.from_pretrained(
        candidate["modelId"],
        revision=candidate["revision"],
        trust_remote_code=False,
        use_safetensors=True,
    )
    model.eval()
    maximum_length = min(int(getattr(tokenizer, "model_max_length", 512)), 512)
    native_labels = {
        int(index): str(label).lower() for index, label in model.config.id2label.items()
    }
    validate_native_mapping(candidate, set(native_labels.values()))

    predictions: list[dict[str, Any]] = []
    for start in range(0, len(selected), arguments.batch_size):
        batch = selected[start : start + arguments.batch_size]
        texts = [row["text"] for row in batch]
        input_truncated = [
            len(tokenizer.encode(text, add_special_tokens=True, truncation=False))
            > maximum_length
            for text in texts
        ]
        encoded = tokenizer(
            texts,
            padding=True,
            truncation=True,
            max_length=maximum_length,
            return_tensors="pt",
        )
        started = time.perf_counter()
        with torch.inference_mode():
            logits = model(**encoded).logits
            probabilities = torch.sigmoid(logits).cpu().tolist()
        per_item_latency_ms = (time.perf_counter() - started) * 1000 / len(batch)

        for row, raw_scores, truncated in zip(batch, probabilities, input_truncated):
            by_native_label = {
                native_labels[index]: float(value)
                for index, value in enumerate(raw_scores)
            }
            domain_scores = {
                domain_label: max(by_native_label[label] for label in native_names)
                for domain_label, native_names in candidate["domainLabelMapping"].items()
            }
            predictions.append(
                {
                    "id": row["id"],
                    "model_id": candidate["modelId"],
                    "model_revision": candidate["revision"],
                    "scores": domain_scores,
                    "input_truncated": truncated,
                    "latency_ms": per_item_latency_ms,
                }
            )

    output = arguments.output or (
        MODEL_SERVICE_ROOT
        / "evaluation"
        / "results"
        / f"{candidate['key']}-{arguments.split}.jsonl"
    )
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="\n") as target:
        for prediction in predictions:
            target.write(json.dumps(prediction, sort_keys=True) + "\n")
    print(f"Wrote {len(predictions)} predictions to {output}")
    return 0


def load_candidate(path: Path, key: str) -> dict[str, Any]:
    manifest = json.loads(path.read_text(encoding="utf-8"))
    matches = [candidate for candidate in manifest["candidates"] if candidate["key"] == key]
    if len(matches) != 1:
        raise ValueError(f"Unknown or duplicate candidate key: {key}")
    return matches[0]


def validate_native_mapping(candidate: dict[str, Any], native_labels: set[str]) -> None:
    mapping = candidate["domainLabelMapping"]
    if set(mapping) != set(RISK_LABELS):
        raise ValueError("Candidate mapping must cover every domain risk label.")
    missing = {
        label
        for labels in mapping.values()
        for label in labels
        if label not in native_labels
    }
    if missing:
        raise ValueError("Model is missing mapped native labels: " + ", ".join(sorted(missing)))


if __name__ == "__main__":
    raise SystemExit(main())
