# Expanded domain-corpus smoke report

Date: 2026-07-19

Status: **Pipeline verified; no production model selected.** This run proves
that `v1-domain-synthetic` works through model inference, calibration-only
threshold selection, and untouched-test evaluation. Its labels remain draft.

## Run configuration

| Property | Value |
| --- | --- |
| Model | `minuva/MiniLMv2-toxic-jigsaw` |
| Revision | `00eacca7ba7c09b1e82db508b03a901bf9cc89eb` |
| Dataset | `v1-domain-synthetic` |
| Calibration size | 120 |
| Test size | 120 |
| Device | Local Windows CPU |
| Threshold objective | Maximise F2 with 0.50 minimum precision when feasible |

## Untouched-test results

| Metric | Result |
| --- | ---: |
| Macro F1 | 0.578 |
| Micro F1 | 0.686 |
| Micro precision | 0.689 |
| Micro recall | 0.684 |
| Mean inference latency/message | 4.11 ms |

| Category | Precision | Recall | F1 |
| --- | ---: | ---: | ---: |
| Threat | 0.800 | 0.250 | 0.381 |
| Hate or identity attack | 0.615 | 0.500 | 0.552 |
| Harassment or insult | 0.565 | 0.406 | 0.473 |
| Obscene or profane | 1.000 | 0.500 | 0.667 |
| General toxicity | 0.696 | 0.986 | 0.816 |

## Important findings

- Threat recall of 0.25 is unacceptable for a safety-critical production gate.
- Seven of eight benign identity mentions received at least one risk flag.
- All quoted-harm and self-directed/context-sensitive negatives received at
  least one flag at the selected thresholds.
- General-toxicity recall is high, but its false-positive rate is 0.646.

These findings demonstrate why overall accuracy or micro F1 is insufficient.
The application needs per-category thresholds, context handling, a manual-review
path, and human-reviewed domain evidence. The generated results under
`evaluation/results/` remain ignored because they can be reproduced.
