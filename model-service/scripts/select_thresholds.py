"""Select per-label thresholds on the calibration split only."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

from evaluation_lib import RISK_LABELS, load_jsonl, validate_dataset

MODEL_SERVICE_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DATASET = MODEL_SERVICE_ROOT / "evaluation" / "datasets" / "v1-seed.jsonl"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--predictions", type=Path, required=True)
    parser.add_argument("--candidate-key", required=True)
    parser.add_argument("--dataset", type=Path, default=DEFAULT_DATASET)
    parser.add_argument("--minimum-precision", type=float, default=0.50)
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()

    if not 0.0 <= arguments.minimum_precision <= 1.0:
        parser.error("--minimum-precision must be between 0 and 1")

    dataset = load_jsonl(arguments.dataset)
    errors = validate_dataset(dataset)
    if errors:
        print("Dataset validation failed: " + "; ".join(errors), file=sys.stderr)
        return 1
    calibration = [row for row in dataset if row["split"] == "calibration"]
    prediction_rows = load_jsonl(arguments.predictions)
    predictions = {row.get("id"): row for row in prediction_rows}
    if set(predictions) != {row["id"] for row in calibration}:
        print("Predictions must exactly match the calibration split.", file=sys.stderr)
        return 1

    selections = {
        label: select_threshold(
            calibration, predictions, label, arguments.minimum_precision
        )
        for label in RISK_LABELS
    }
    output = {
        "thresholdVersion": f"{arguments.candidate_key}-v1-seed-calibrated",
        "approvedForProduction": False,
        "datasetVersion": calibration[0]["dataset_version"],
        "selectionSplit": "calibration",
        "selectionObjective": "maximise F2 subject to minimum precision when feasible",
        "minimumPrecision": arguments.minimum_precision,
        "warning": "Draft synthetic seed data; thresholds are smoke-test values only.",
        "thresholds": {
            label: selections[label]["threshold"] for label in RISK_LABELS
        },
        "calibrationMetrics": selections,
    }
    arguments.output.parent.mkdir(parents=True, exist_ok=True)
    arguments.output.write_text(
        json.dumps(output, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    print(json.dumps(output["thresholds"], indent=2, sort_keys=True))
    return 0


def select_threshold(
    rows: list[dict[str, Any]],
    predictions: dict[str, dict[str, Any]],
    label: str,
    minimum_precision: float,
) -> dict[str, float | int | bool]:
    candidates = sorted(
        {0.0, 1.0}
        | {
            float(predictions[row["id"]]["scores"].get(label, 0.0))
            for row in rows
        }
    )
    scored = [_score_threshold(rows, predictions, label, threshold) for threshold in candidates]
    feasible = [result for result in scored if result["precision"] >= minimum_precision]
    pool = feasible or scored
    selected = max(
        pool,
        key=lambda result: (
            result["f2"],
            result["recall"],
            result["precision"],
            result["threshold"],
        ),
    )
    return {**selected, "metMinimumPrecision": bool(feasible)}


def _score_threshold(
    rows: list[dict[str, Any]],
    predictions: dict[str, dict[str, Any]],
    label: str,
    threshold: float,
) -> dict[str, float | int]:
    tp = fp = fn = tn = 0
    for row in rows:
        actual = label in row["expected_labels"]
        predicted = float(predictions[row["id"]]["scores"].get(label, 0.0)) >= threshold
        if actual and predicted:
            tp += 1
        elif actual:
            fn += 1
        elif predicted:
            fp += 1
        else:
            tn += 1
    precision = tp / (tp + fp) if tp + fp else 0.0
    recall = tp / (tp + fn) if tp + fn else 0.0
    beta_squared = 4.0
    f2 = (
        (1 + beta_squared) * precision * recall
        / (beta_squared * precision + recall)
        if precision + recall
        else 0.0
    )
    return {
        "threshold": threshold,
        "truePositive": tp,
        "falsePositive": fp,
        "falseNegative": fn,
        "trueNegative": tn,
        "precision": precision,
        "recall": recall,
        "f2": f2,
    }


if __name__ == "__main__":
    raise SystemExit(main())
