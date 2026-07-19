"""Calculate auditable per-category and context-slice metrics."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from evaluation_lib import RISK_LABELS, calculate_metrics, load_jsonl, validate_dataset

MODEL_SERVICE_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DATASET = (
    MODEL_SERVICE_ROOT / "evaluation" / "datasets" / "v1-domain-synthetic.jsonl"
)
DEFAULT_THRESHOLDS = MODEL_SERVICE_ROOT / "evaluation" / "baseline-thresholds.json"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", type=Path, default=DEFAULT_DATASET)
    parser.add_argument("--predictions", type=Path, required=True)
    parser.add_argument("--thresholds", type=Path, default=DEFAULT_THRESHOLDS)
    parser.add_argument("--split", choices=("calibration", "test"), default="test")
    parser.add_argument("--output", type=Path)
    arguments = parser.parse_args()

    dataset = load_jsonl(arguments.dataset)
    errors = validate_dataset(dataset)
    if errors:
        print("Dataset validation failed: " + "; ".join(errors), file=sys.stderr)
        return 1
    selected = [row for row in dataset if row["split"] == arguments.split]

    prediction_rows = load_jsonl(arguments.predictions)
    predictions = {row.get("id"): row for row in prediction_rows}
    if None in predictions or len(predictions) != len(prediction_rows):
        print("Prediction IDs must be present and unique.", file=sys.stderr)
        return 1
    expected_ids = {row["id"] for row in selected}
    if set(predictions) != expected_ids:
        print("Prediction IDs must exactly match the selected dataset split.", file=sys.stderr)
        return 1

    threshold_document = json.loads(arguments.thresholds.read_text(encoding="utf-8"))
    thresholds = threshold_document["thresholds"]
    if set(thresholds) != set(RISK_LABELS) or any(
        not isinstance(value, (int, float)) or value < 0 or value > 1
        for value in thresholds.values()
    ):
        print("Thresholds must cover every risk label with values from 0 to 1.", file=sys.stderr)
        return 1

    metrics = calculate_metrics(selected, predictions, thresholds)
    report = {
        "datasetVersion": selected[0]["dataset_version"],
        "split": arguments.split,
        "thresholdVersion": threshold_document["thresholdVersion"],
        "approvedForProduction": threshold_document["approvedForProduction"],
        "modelId": prediction_rows[0].get("model_id"),
        "modelRevision": prediction_rows[0].get("model_revision"),
        **metrics,
    }
    rendered = json.dumps(report, indent=2, sort_keys=True)
    print(rendered)
    if arguments.output:
        arguments.output.parent.mkdir(parents=True, exist_ok=True)
        arguments.output.write_text(rendered + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
