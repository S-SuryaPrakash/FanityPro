# V1 realistic synthetic domain dataset

## Purpose

`datasets/v1-domain-synthetic.jsonl` is a deterministic, privacy-safe corpus for
testing V1 against customer-support and AI-assistant messages. It contains no
copied conversations, usernames, account numbers, or public-comment text. The
examples are synthetic scenarios informed by established toxicity research.

This is a **working smoke and regression dataset**, not production ground truth.
Every row remains `draft`; generated labels cannot replace independent human
annotation and adjudication.

## Composition

| Property | Value |
| --- | ---: |
| Total examples | 240 |
| Calibration examples | 120 |
| Test examples | 120 |
| Unique texts | 240 |
| Language | English |
| Human-like origin | 216 |
| AI-assistant origin | 24 |

Each split contains 40 hard-negative or benign messages and 16 examples for
each primary risk family. Multi-label cases intentionally overlap categories
where appropriate.

Covered slices include professional support language, frustration without
abuse, threats, identity attacks paired with benign identity mentions, targeted
harassment, profanity, general toxicity, quoted harm, self-directed language,
and both human-like and AI-assistant origins.

Calibration and test use separate templates, entities, support issues, and
targets. Validation also rejects duplicate normalised text.

## Research basis

- Civil Comments documents multi-label toxicity, obscenity, threat, insult,
  identity attack, and related scores:
  https://huggingface.co/datasets/google/civil_comments
- Jigsaw Toxic Comment Classification uses independent toxic, severe-toxic,
  obscene, threat, insult, and identity-hate labels:
  https://www.kaggle.com/c/jigsaw-toxic-comment-classification-challenge
- Google research shows that benign demographic terms can trigger unintended
  classifier bias, motivating paired benign identity slices:
  https://research.google/pubs/measuring-and-mitigating-unintended-bias-in-text-classification/
- Research on specialised rater pools finds meaningful disagreement in
  identity-related toxicity annotation, motivating independent review:
  https://research.google/pubs/is-your-toxicity-my-toxicity-exploring-the-impact-of-rater-identity-on-toxicity-annotation/
- Covert-toxicity research highlights context-sensitive annotation difficulty:
  https://research.google/pubs/capturing-covertly-toxic-speech-via-crowdsourcing/

Only schemas, category concepts, and reported failure modes informed generation.
No source examples were copied into this repository.

## Reproduction and validation

```bash
python scripts/build_domain_dataset.py
python scripts/build_domain_dataset.py --check
python scripts/validate_dataset.py --profile domain
```

The stricter release check is expected to fail while rows remain draft:

```bash
python scripts/validate_dataset.py --profile release
```

## Limitations and next step

- Template-generated language is less varied than real conversations.
- The corpus is English-only and intentionally contains no real customer data.
- Draft labels include subjective and context-dependent decisions.
- It cannot establish a production error rate or fairness claim.

For production evaluation, create a separate privacy-reviewed dataset from
authorised, de-identified domain samples. Two reviewers must label it
independently, an authorised reviewer must adjudicate disagreements, and the
frozen result must remain separate from model training data.
