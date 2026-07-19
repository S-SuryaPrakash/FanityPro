"""Validate the versioned V1 evaluation dataset using only Python stdlib."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from evaluation_lib import load_jsonl, validate_dataset

MODEL_SERVICE_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DATASET = MODEL_SERVICE_ROOT / "evaluation" / "datasets" / "v1-seed.jsonl"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", type=Path, default=DEFAULT_DATASET)
    arguments = parser.parse_args()

    try:
        records = load_jsonl(arguments.dataset)
    except ValueError as exception:
        print(exception, file=sys.stderr)
        return 1

    errors = validate_dataset(records)
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1

    versions = {record["dataset_version"] for record in records}
    statuses = sorted({record["review_status"] for record in records})
    print(
        f"Valid dataset: version={versions.pop()}, examples={len(records)}, "
        f"review_statuses={','.join(statuses)}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
