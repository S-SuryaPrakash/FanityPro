from __future__ import annotations

import sys
import unittest
from pathlib import Path

MODEL_SERVICE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(MODEL_SERVICE_ROOT / "scripts"))

from evaluation_lib import RISK_LABELS, calculate_metrics, load_jsonl, validate_dataset
from run_hf_evaluation import load_candidate, validate_native_mapping
from select_thresholds import select_threshold


class DatasetValidationTests(unittest.TestCase):

    def test_repository_seed_dataset_is_valid(self) -> None:
        dataset = load_jsonl(
            MODEL_SERVICE_ROOT / "evaluation" / "datasets" / "v1-seed.jsonl"
        )

        self.assertEqual([], validate_dataset(dataset))
        self.assertEqual(40, len(dataset))
        self.assertTrue(all(row["review_status"] == "draft" for row in dataset))

    def test_duplicate_id_is_rejected(self) -> None:
        row = {
            "dataset_version": "test",
            "id": "same",
            "split": "calibration",
            "text": "example",
            "expected_labels": list(RISK_LABELS),
            "language": "en",
            "speaker_role": "customer",
            "content_origin": "synthetic_human",
            "slice_tags": ["test"],
            "review_status": "draft",
            "annotation_notes": "test",
        }

        errors = validate_dataset([row, dict(row, split="test")])

        self.assertTrue(any("duplicate id" in error for error in errors))


class MetricTests(unittest.TestCase):

    def test_metrics_count_true_and_false_predictions(self) -> None:
        dataset = [
            {"id": "one", "expected_labels": ["THREAT"], "slice_tags": ["direct"]},
            {"id": "two", "expected_labels": [], "slice_tags": ["benign"]},
        ]
        empty_scores = {label: 0.0 for label in RISK_LABELS}
        predictions = {
            "one": {"scores": {**empty_scores, "THREAT": 0.9}, "latency_ms": 4.0},
            "two": {"scores": {**empty_scores, "THREAT": 0.8}, "latency_ms": 6.0},
        }
        thresholds = {label: 0.5 for label in RISK_LABELS}

        result = calculate_metrics(dataset, predictions, thresholds)

        threat = result["perCategory"]["THREAT"]
        self.assertEqual(1, threat["truePositive"])
        self.assertEqual(1, threat["falsePositive"])
        self.assertEqual(0.5, threat["precision"])
        self.assertEqual(5.0, result["latencyMs"]["mean"])

    def test_threshold_selection_uses_calibration_labels(self) -> None:
        rows = [
            {"id": "positive", "expected_labels": ["THREAT"]},
            {"id": "negative", "expected_labels": []},
        ]
        predictions = {
            "positive": {"scores": {"THREAT": 0.7}},
            "negative": {"scores": {"THREAT": 0.2}},
        }

        selected = select_threshold(rows, predictions, "THREAT", 0.5)

        self.assertEqual(0.7, selected["threshold"])
        self.assertEqual(1.0, selected["precision"])
        self.assertEqual(1.0, selected["recall"])


class CandidateManifestTests(unittest.TestCase):

    def test_candidates_are_pinned_and_cover_domain_labels(self) -> None:
        manifest = MODEL_SERVICE_ROOT / "evaluation" / "candidate-models.json"
        candidate = load_candidate(manifest, "toxic-bert")

        self.assertEqual(40, len(candidate["revision"]))
        native_labels = {
            label
            for labels in candidate["domainLabelMapping"].values()
            for label in labels
        }
        validate_native_mapping(candidate, native_labels)


if __name__ == "__main__":
    unittest.main()
