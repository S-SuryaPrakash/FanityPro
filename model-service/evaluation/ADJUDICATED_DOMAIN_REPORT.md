# Re-evaluation against the adjudicated domain dataset

Date: 2026-08-17

Status: **Both eligible candidates remain rejected.** This report re-runs the
release-gate pipeline (`run_hf_evaluation.py` → `select_thresholds.py` →
`evaluate_predictions.py`) against `v1-domain-adjudicated.jsonl` now that it
has been through the full three-step review in `ANNOTATION_GUIDE.md`
(Reviewer 1 → Reviewer 2 → independent adjudicator; see
`ADJUDICATOR_REPORT.md`). Every prior number in `DOMAIN_SMOKE_REPORT.md`,
`REVIEWER_1_REPORT.md`, and `REVIEWER_2_CRITIQUE.md` was measured against
`v1-domain-synthetic.jsonl` — draft labels, not the adjudicated ones. This is
the first run of both eligible candidates against the finalized ground truth.

## Why re-run at all

The 32 labels changed by adjudication only ever touched `HARASSMENT_OR_INSULT`
(see `ADJUDICATOR_REPORT.md`). Every other category (`THREAT`,
`HATE_OR_IDENTITY_ATTACK`, `OBSCENE_OR_PROFANE`, `GENERAL_TOXICITY`) has
identical ground truth before and after adjudication. So this run answers two
separate questions:

1. Does the previously reported rejection still hold once measured against
   correctly adjudicated labels, or was it partly an artifact of draft-label
   noise?
2. `unitary/toxic-bert` was previously only smoke-tested on the 40-example
   `v1-seed` set, never on the full 240-example domain corpus. This is its
   first domain-scale result, giving a real two-candidate comparison.

## Run configuration

| Property | Value |
| --- | --- |
| Dataset | `v1-domain-adjudicated` (240 examples: 120 calibration / 120 test) |
| Candidates | `unitary/toxic-bert` (pinned rev `4d6c22e`), `minuva/MiniLMv2-toxic-jigsaw` (pinned rev `00eacca`) |
| Threshold objective | Maximise F2 with 0.50 minimum precision when feasible, calibration split only |
| Device | Local Windows CPU |

## Untouched-test results

| Metric | toxic-bert | MiniLM |
| --- | ---: | ---: |
| Macro F1 | 0.631 | 0.570 |
| Micro F1 | 0.698 | 0.680 |
| Mean latency/message | 59.25 ms | 3.59 ms |

| Category | toxic-bert P / R / F1 | MiniLM P / R / F1 |
| --- | ---: | ---: |
| Threat | 1.000 / 0.312 / 0.476 | 0.800 / 0.250 / 0.381 |
| Hate or identity attack | 0.818 / 0.562 / 0.667 | 0.615 / 0.500 / 0.552 |
| Harassment or insult | 0.467 / 0.438 / 0.452 | 0.522 / 0.375 / 0.436 |
| Obscene or profane | 0.750 / 0.750 / 0.750 | 1.000 / 0.500 / 0.667 |
| General toxicity | 0.693 / 0.972 / 0.809 | 0.696 / 0.986 / 0.816 |

Context-sensitivity slices (both candidates identical):

| Slice | False-positive rate |
| --- | ---: |
| Benign identity mention | 0.875 (7/8) |
| Quoted harm | 1.000 (8/8) |
| Self-directed / non-targeted | 1.000 (8/8) |

## Comparison against the pre-adjudication draft numbers

`THREAT`, `HATE_OR_IDENTITY_ATTACK`, `OBSCENE_OR_PROFANE`, and
`GENERAL_TOXICITY` scores for MiniLM are unchanged from `DOMAIN_SMOKE_REPORT.md`
(0.578 macro F1, 0.25 threat recall) — expected, since adjudication never
touched those categories' ground truth. `HARASSMENT_OR_INSULT` did move
(MiniLM: 0.565/0.406/0.473 draft → 0.522/0.375/0.436 adjudicated) because that
category's ground truth changed. **The rejection decision does not change
either way** — no metric crosses the acceptance bar in either direction.

## Assessment against the proposed acceptance criteria

Both reviewer reports proposed: threat recall ≥ 0.90, identity-attack
false-positive rate on benign mentions ≤ 0.10, quoted-harm false-positive
rate ≤ 0.10.

| Criterion | Bar | toxic-bert | MiniLM | Result |
| --- | --- | ---: | ---: | --- |
| Threat recall | ≥ 0.90 | 0.312 | 0.250 | **Fail, both** |
| Benign-identity FP rate | ≤ 0.10 | 0.875 | 0.875 | **Fail, both** |
| Quoted-harm FP rate | ≤ 0.10 | 1.000 | 1.000 | **Fail, both** |

## Conclusion

Re-running against properly adjudicated ground truth **confirms and
strengthens** the original recommendation rather than changing it. `toxic-bert`
is measurably better than MiniLM on this domain corpus (higher macro F1,
better threat recall, no threat false positives) — worth noting since MiniLM
was previously the only one tested at domain scale — but neither model clears
any of the three safety-critical bars, and both fail identically on the
context-sensitivity slices (quoting harm, self-directed language). This is
consistent with both reviewers' diagnosis: these Jigsaw-trained models lack
context awareness and cannot distinguish a threat/insult being reported or
quoted from one being directed by the speaker, which no amount of relabeling
the existing data fixes.

**No model is approved for production.** The next step remains what both
reviewer reports already recommended: fine-tune or select a context-aware
candidate against `v1-domain-adjudicated.jsonl`, then re-run this same
pipeline.

## Reproduction

```bash
python scripts/run_hf_evaluation.py --candidate <key> \
  --dataset evaluation/datasets/v1-domain-adjudicated.jsonl --split calibration \
  --output evaluation/results/<key>-adjudicated-calibration.jsonl
python scripts/select_thresholds.py --candidate-key <key> \
  --predictions evaluation/results/<key>-adjudicated-calibration.jsonl \
  --dataset evaluation/datasets/v1-domain-adjudicated.jsonl \
  --output evaluation/results/<key>-adjudicated-thresholds.json
python scripts/run_hf_evaluation.py --candidate <key> \
  --dataset evaluation/datasets/v1-domain-adjudicated.jsonl --split test \
  --output evaluation/results/<key>-adjudicated-test.jsonl
python scripts/evaluate_predictions.py \
  --dataset evaluation/datasets/v1-domain-adjudicated.jsonl \
  --predictions evaluation/results/<key>-adjudicated-test.jsonl \
  --thresholds evaluation/results/<key>-adjudicated-thresholds.json \
  --split test --output evaluation/results/<key>-adjudicated-test.metrics.json
```

Full metrics (per-category, per-slice, latency) are in
`results/toxic-bert-adjudicated-test.metrics.json` and
`results/minilm-toxic-jigsaw-adjudicated-test.metrics.json`.
