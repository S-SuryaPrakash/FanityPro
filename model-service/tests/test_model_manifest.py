"""Supply-chain and domain-mapping tests for the pinned candidate manifest."""

from pathlib import Path
import sys

import pytest


MODEL_SERVICE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(MODEL_SERVICE_ROOT))

from app.model_manifest import load_eligible_candidate, validate_native_labels  # noqa: E402
from app.schemas import RiskCategory  # noqa: E402


MANIFEST = MODEL_SERVICE_ROOT / "evaluation" / "candidate-models.json"


def test_default_development_candidate_is_eligible_commit_pinned_and_complete():
    candidate = load_eligible_candidate(MANIFEST, "minilm-toxic-jigsaw")

    assert len(candidate.revision) == 40
    assert set(candidate.domain_label_mapping) == set(RiskCategory)


def test_rejected_candidate_cannot_be_loaded():
    with pytest.raises(ValueError, match="not eligible"):
        load_eligible_candidate(MANIFEST, "unbiased-toxic-roberta")


def test_loaded_model_must_expose_every_mapped_native_label():
    candidate = load_eligible_candidate(MANIFEST, "minilm-toxic-jigsaw")

    with pytest.raises(ValueError, match="missing mapped native labels"):
        validate_native_labels(candidate, {"toxic", "insult"})
