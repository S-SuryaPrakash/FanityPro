# Model evaluation

This directory contains the reproducible gate used before selecting a V1 model.
Model popularity and public leaderboard scores are not sufficient: the selected
model must perform acceptably on reviewed examples from the intended customer-
support and AI-assistant domain.

## Current state

- `datasets/v1-domain-synthetic.jsonl` is the default 240-message realistic
  synthetic smoke corpus. It is reproducible from `scripts/build_domain_dataset.py`
  and its coverage is documented in `DATASET_CARD.md`.
- `datasets/v1-seed.jsonl` is retained as the original 40-message tooling seed.
- Both datasets are synthetic, not production ground truth. Every row is marked
  `draft`; generated labels cannot be promoted without independent review.
- `candidate-models.json` pins the Hugging Face candidates and documents
  how their native labels map to the Java risk taxonomy.
- `baseline-thresholds.json` exists only to exercise the evaluation pipeline.
  Its values are not approved production thresholds.
- `DOMAIN_SMOKE_REPORT.md` records an end-to-end MiniLM proof run on the expanded
  corpus and the important failure modes it exposed.
- No model is selected yet.

## Dataset lifecycle

```text
Draft synthetic examples
        |
        v
Independent reviewer 1 + reviewer 2
        |
        v
Adjudicate disagreements
        |
        v
Freeze dataset version and checksum
        |
        v
Choose thresholds on calibration split
        |
        v
Compare once on untouched test split
        |
        v
Document release decision or reject all candidates
```

Do not tune thresholds against the test split. The expanded corpus tests the
tooling and hard slices, but a production decision still requires authorised,
privacy-reviewed and independently adjudicated domain data.

## Commands

Validate the dataset without installing ML dependencies:

```bash
python scripts/build_domain_dataset.py --check
python scripts/validate_dataset.py
python -m unittest discover -s tests -p "test_*.py"
```

`validate_dataset.py --profile release` deliberately fails on the current
corpus because its examples have not been independently adjudicated.

Install optional evaluation dependencies in a virtual environment:

```bash
python -m venv .venv
.venv/Scripts/python -m pip install -r requirements-evaluation.txt
```

Run a pinned candidate and calculate metrics:

```bash
python scripts/run_hf_evaluation.py --candidate toxic-bert --split calibration
python scripts/select_thresholds.py \
  --candidate-key toxic-bert \
  --predictions evaluation/results/toxic-bert-calibration.jsonl \
  --output evaluation/results/toxic-bert-thresholds.json
python scripts/run_hf_evaluation.py --candidate toxic-bert --split test
python scripts/evaluate_predictions.py \
  --predictions evaluation/results/toxic-bert-test.jsonl \
  --thresholds evaluation/results/toxic-bert-thresholds.json
```

Generated predictions and downloaded weights are ignored by Git.

## Release gate

A candidate may be selected only after:

1. Every evaluation example is `adjudicated`.
2. Each risk category and required context slice has adequate representation.
3. Thresholds are chosen using only the calibration split.
4. Per-category precision, recall, F1, false-positive rate, and false-negative
   rate are reported on the untouched test split.
5. False positives on benign identity mentions, quoted harmful content, humor,
   and self-directed language are reviewed manually.
6. Threat false negatives and latency fit the documented product requirements.
7. The model ID, immutable revision, dependency versions, dataset version,
   thresholds, hardware, and decision are recorded in a model-selection report.
