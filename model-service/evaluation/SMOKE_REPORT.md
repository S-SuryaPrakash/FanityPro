# Module 4 smoke-evaluation report

Date: 2026-07-19

Status: **No production model selected.** The dataset is synthetic, contains
only 40 examples, and is still marked `draft`. Results below validate the
evaluation process and reveal candidate risks; they are not release evidence.

This report records the original `v1-seed` experiment. The later
`v1-domain-synthetic` corpus expands coverage to 240 messages; candidates must
be rerun on it, and its generated labels still require human adjudication.

## Supply-chain checks

| Candidate | Pinned revision | Outcome |
| --- | --- | --- |
| `unitary/toxic-bert` | `4d6c22e74ba2fdd26bc4f7238f50766b045a0d94` | Eligible; safetensors loaded and labels matched configuration |
| `unitary/unbiased-toxic-roberta` | `36295dd80b422dc49f40052021430dae76241adc` | Rejected; no safetensors weights at the pinned revision |
| `Koushim/bert-multilabel-jigsaw-toxic-classifier` | `d0ee7f441bb8f76269c858f12d1e396e2c3abb4b` | Rejected; generic config labels and an unexpected training-weight key prevent self-describing label correlation |
| `minuva/MiniLMv2-toxic-jigsaw` | `00eacca7ba7c09b1e82db508b03a901bf9cc89eb` | Eligible; safetensors loaded and labels matched configuration |

The rejected models were not loaded through an unsafe fallback and were not
used for metrics.

## Draft smoke results

Per-category thresholds were selected on the 20-example calibration split by
maximising F2 subject to 0.50 minimum precision when feasible. Those thresholds
were then frozen and used once on the separate 20-example test split.

| Model | Macro F1 | Micro F1 | Micro precision | Micro recall | Threat recall | Identity-attack recall | Mean CPU latency/message* |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `unitary/toxic-bert` | 0.343 | 0.435 | 0.714 | 0.312 | 0.667 | 0.000 | 26.62 ms |
| `minuva/MiniLMv2-toxic-jigsaw` | 0.410 | 0.444 | 0.545 | 0.375 | 0.667 | 0.500 | 10.19 ms |

\*Local Windows CPU smoke run, batch size 8, model-loading time excluded. This
is not a target-hardware benchmark.

Both candidates miss too many draft positive examples at these seed-calibrated
thresholds. The lightweight MiniLM candidate is faster, but it is distilled
from `toxic-bert`, so this is not independent evidence. Neither model is approved.

## Required next gate

1. Have two reviewers independently label every seed example and adjudicate it.
2. Resolve policy questions exposed by context-sensitive examples, especially
   positive profanity, quoted harm, and indirect threats.
3. Expand to a privacy-reviewed domain dataset with enough examples per category
   and slice to make rates meaningful.
4. Repeat calibration and untouched-test evaluation.
5. Set explicit minimum acceptance criteria, with high recall requirements for
   threats and manual review of identity-related false positives/negatives.
6. Select a model only if it passes quality, safe-loading, latency, license, and
   reproducibility gates; rejecting all candidates remains a valid outcome.

## Primary sources

- https://huggingface.co/unitary/toxic-bert
- https://huggingface.co/unitary/unbiased-toxic-roberta
- https://huggingface.co/minuva/MiniLMv2-toxic-jigsaw
- https://huggingface.co/Koushim/bert-multilabel-jigsaw-toxic-classifier
- https://huggingface.co/docs/transformers/main_classes/pipelines
- https://scikit-learn.org/stable/modules/model_evaluation.html
