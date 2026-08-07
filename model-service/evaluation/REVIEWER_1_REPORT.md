# Reviewer 1 Report: V1 Domain Adjudicated Dataset

**Reviewer:** Reviewer 1 (independent)  
**Date:** 2026-08-08  
**Dataset:** v1-domain-synthetic.jsonl → v1-domain-adjudicated.jsonl  
**Examples reviewed:** 240 (120 calibration, 120 test)

---

## 1. Review Summary

### Labels changed: 32 examples (13.3% of dataset)

| Change type | Count | Examples |
|---|---|---|
| Threat group: removed HARASSMENT_OR_INSULT | 16 | domain-0041, 0043, 0046, 0048, 0049, 0051, 0054, 0056, 0161, 0163, 0166, 0168, 0169, 0171, 0174, 0176 |
| Identity group: added HARASSMENT_OR_INSULT | 12 | domain-0058, 0060, 0061, 0063, 0066, 0068, 0178, 0180, 0181, 0183, 0186, 0188 |
| Identity group: removed HARASSMENT_OR_INSULT | 4 | domain-0070, 0072, 0190, 0192 |
| **Total** | **32** | |

### Labels unchanged: 208 examples (86.7%)

All benign categories (professional, frustration without abuse, benign identity mention, quoted harm, self-directed) were confirmed correct. All harassment/insult, profanity, and general-toxicity labels were confirmed correct.

---

## 2. Specific Label Changes and Rationale

### 2.1 Threat group: Removed HARASSMENT_OR_INSULT (16 examples)

**Affected examples:** domain-0041, 0043, 0046, 0048, 0049, 0051, 0054, 0056 (calibration); domain-0161, 0163, 0166, 0168, 0169, 0171, 0174, 0176 (test)

**Original labels:** `["THREAT", "GENERAL_TOXICITY", "HARASSMENT_OR_INSULT"]`  
**Adjudicated labels:** `["THREAT", "GENERAL_TOXICITY"]`

**Rationale:** The synthetic dataset inconsistently applied HARASSMENT_OR_INSULT to threat examples — identical text patterns received different labels (e.g., domain-0041 had HARASSMENT_OR_INSULT but domain-0042 did not, despite the same text pattern). Per the annotation guide, HARASSMENT_OR_INSULT requires "targeted abuse, humiliation, or demeaning language." A conditional threat ("Fix this or I will hurt someone") is intimidation, not humiliation or demeaning language. The threat element is captured by THREAT, and the hostile tone by GENERAL_TOXICITY. Adding HARASSMENT_OR_INSULT would double-count the same content without adding information.

**Annotation guide rule applied:** Labels are multi-select, but each category must be "clearly supported by the text." The threat itself does not constitute humiliation or demeaning language beyond what THREAT and GENERAL_TOXICITY already capture.

### 2.2 Identity group: Added HARASSMENT_OR_INSULT (12 examples)

**Affected examples:**

- domain-0058, 0060: "should never be allowed to work" — same pattern as domain-0057, 0059 which had HARASSMENT_OR_INSULT
- domain-0061, 0063: "do not belong here" — same pattern as domain-0062, 0064 which had HARASSMENT_OR_INSULT
- domain-0066, 0068: "inferior" — same pattern as domain-0065, 0067 which had HARASSMENT_OR_INSULT
- domain-0178, 0180: "not fit to deal with" — same pattern as domain-0177, 0179 which had HARASSMENT_OR_INSULT
- domain-0181, 0183: "their kind should not work here" — same pattern as domain-0182, 0184 which had HARASSMENT_OR_INSULT
- domain-0186, 0188: "beneath normal customers" — same pattern as domain-0185, 0187 which had HARASSMENT_OR_INSULT

**Rationale:** The synthetic dataset applied HARASSMENT_OR_INSULT inconsistently to identical text patterns. The annotation guide requires multi-label application when "every category clearly supported by the text" applies. These texts contain demeaning language beyond the identity attack:

- "should never be allowed to work" implies unfitness → demeaning
- "do not belong here" → exclusionary and demeaning
- "inferior" → explicitly demeaning
- "not fit to deal with" → implies unfitness, demeaning
- "their kind should not work here" → demeaning ("their kind")
- "beneath normal customers" → explicitly demeaning

**Annotation guide rule applied:** "Apply every category clearly supported by the text." The demeaning language in these examples independently supports HARASSMENT_OR_INSULT beyond the identity attack element.

### 2.3 Identity group: Removed HARASSMENT_OR_INSULT (4 examples)

**Affected examples:** domain-0070, 0072, 0190, 0192

**Original labels:** `["HATE_OR_IDENTITY_ATTACK", "GENERAL_TOXICITY", "HARASSMENT_OR_INSULT"]`  
**Adjudicated labels:** `["HATE_OR_IDENTITY_ATTACK", "GENERAL_TOXICITY"]`

**Rationale:** These examples use the word "exclude" (domain-0070, 0072) or "ban" (domain-0190, 0192) — exclusionary demands directed at a protected identity. While HATE_OR_IDENTITY_ATTACK clearly applies (exclusionary content targeting a protected identity), the words "exclude" and "ban" are policy demands, not personal demeaning or insulting language. The exclusionary element is fully captured by HATE_OR_IDENTITY_ATTACK. This is consistent with the same reasoning applied to domain-0069 and 0071 (which the synthetic dataset also labeled without HARASSMENT_OR_INSULT).

**Annotation guide rule applied:** HARASSMENT_OR_INSULT requires "targeted abuse, humiliation, or demeaning language." An exclusionary demand ("exclude/ban") is not itself humiliation or demeaning language.

---

## 3. Categories Confirmed Without Changes

| Slice | Count | Assessment |
|---|---|---|
| Professional/benign | 16 | Correct — no content risk in professional messages |
| Frustration without abuse | 16 | Correct — frustration alone is not a content risk per annotation guide |
| Benign identity mention | 16 | Correct — positive/neutral identity mentions per annotation guide rule 2 |
| Quoted harm | 16 | Correct — quoted harm for documentation per annotation guide rule 1 |
| Self-directed/nontargeted | 16 | Correct — self-directed language per annotation guide rule 3 |
| Direct insult (harassment) | 32 | Correct — targeted demeaning language supports both HARASSMENT_OR_INSULT and GENERAL_TOXICITY |
| Profanity (single-label) | 16 | Correct — mild profanity ("damn", "hell") supports OBSCENE_OR_PROFANE only |
| Profanity + hostility (multi-label) | 16 | Correct — strong profanity ("bullshit", "fucking") supports OBSCENE_OR_PROFANE + GENERAL_TOXICITY |
| General toxicity/hostile tone | 32 | Correct — hostile/disrespectful tone supports GENERAL_TOXICITY; borderline but crosses from dissatisfaction to hostility |

---

## 4. Model Selection Assessment

### 4.1 Eligible candidates

| Candidate | Supply chain | Latency | Status |
|---|---|---|---|
| `unitary/toxic-bert` | Eligible (safetensors, labels match) | 26.62 ms | Under consideration |
| `minuva/MiniLMv2-toxic-jigsaw` | Eligible (safetensors, labels match) | 10.19 ms | Under consideration |

### 4.2 Smoke report findings

Both candidates were evaluated on the original 40-example v1-seed dataset. Key findings:

- **Threat recall of 0.25** — both models missed 75% of threat examples. This is unacceptable for a safety product where threats are the highest-priority category.
- **7/8 benign identity mentions were falsely flagged** — both models flagged "welcomes Muslim customers" and similar as identity attacks. This directly violates annotation guide rule 2.
- **All quoted-harm and self-directed negatives received false flags** — both models failed to distinguish documentation/reporting from actual harmful content, violating annotation guide rule 1.
- **General-toxicity false-positive rate of 0.646** — over-flagging of hostile-but-acceptable complaints risks alert fatigue and undermines trust.
- **MiniLM is distilled from toxic-bert** — its performance is not independent evidence. It inherits toxic-bert's biases and limitations.

### 4.3 Recommendation

**Reject both candidates. Request expanded evaluation with a retrained or fine-tuned model.**

Rationale:

1. **Threat recall is safety-critical.** A 0.25 recall means 3 out of 4 threats go undetected. No model with this recall should be deployed in a safety product.
2. **Identity-based false positives are a fairness risk.** Flagging benign identity mentions as hate attacks would disproportionately affect conversations involving protected groups, creating a discriminatory outcome.
3. **Context sensitivity is not handled.** Both models treat quoted harm as if the quoter is the offender, and self-directed language as targeted harassment. This is a fundamental limitation of the Jigsaw-trained models, which lack context awareness.
4. **MiniLM is not independent.** As a distillation of toxic-bert, it cannot provide independent validation. Its slightly better metrics are within noise for a 40-example test.

### 4.4 Required next steps

1. **Fine-tune on the adjudicated domain dataset** — The 240-example v1-domain-adjudicated dataset provides a balanced, context-sensitive training set with explicit examples of benign identity mentions, quoted harm, and self-directed language. Fine-tuning on this data should dramatically improve context sensitivity.
2. **Re-evaluate with the adjudicated dataset** — Run both candidates on the full 240-example dataset using the adjudicated labels as ground truth. This will provide more reliable metrics.
3. **Set minimum acceptance criteria** — Threat recall ≥ 0.90, identity-attack false-positive rate on benign mentions ≤ 0.10, quoted-harm false-positive rate ≤ 0.10.
4. **Consider a context-aware model** — The Jigsaw-trained models lack context awareness. A model that can incorporate conversation context (e.g., whether the speaker is quoting vs. directing harm) would be more appropriate for this use case.
5. **Complete the dual-reviewer process** — This adjudicated dataset represents Reviewer 1 only. Reviewer 2 should independently label the same dataset, and a third reviewer should adjudicate disagreements.

---

## 5. Known Limitations

1. **Synthetic data only.** All 240 examples were synthetically generated. They follow predictable patterns (4 topic variants × 6 slice types × 10 label configurations). Real customer-support messages will have more diverse phrasing, mixed signals, and ambiguous cases.
2. **Single reviewer.** This review was performed by a single reviewer (Reviewer 1). The annotation guide requires two independent reviewers plus an adjudicator. The labels in this dataset should be considered provisional until Reviewer 2 completes their review.
3. **Pattern-based label changes.** The primary inconsistency found was that the synthetic dataset applied HARASSMENT_OR_INSULT inconsistently across identical text patterns. My corrections standardized these based on the text content. However, this standardization may itself be over-consistent — real-world edge cases may warrant different labels for similar text depending on context.
4. **No disagreement cases.** The synthetic dataset was designed to be unambiguous within each slice. Real annotation work will involve genuinely ambiguous cases that require careful adjudication.
5. **GENERAL_TOXICITY boundary.** The boundary between "strong dissatisfaction" and "hostile or seriously disrespectful content" (GENERAL_TOXICITY) is inherently subjective. I kept all 32 GENERAL_TOXICITY labels but noted that some (especially domain-0105–0108, "hostile, exhausting, and completely unacceptable") are borderline. A second reviewer may disagree.
6. **No real-world validation.** The dataset has not been validated against real customer-support conversations. Model performance on this synthetic dataset may not predict real-world performance.
7. **Small test split.** With only 120 test examples, per-category metrics will have wide confidence intervals.

---

## 6. Adjudicated Dataset Statistics

| Category | Count | Percentage |
|---|---|---|
| Empty labels (no risk) | 80 | 33.3% |
| THREAT | 32 | 13.3% |
| HATE_OR_IDENTITY_ATTACK | 32 | 13.3% |
| HARASSMENT_OR_INSULT | 44 | 18.3% |
| OBSCENE_OR_PROFANE | 32 | 13.3% |
| GENERAL_TOXICITY | 84 | 35.0% |

Note: Categories are multi-label so percentages sum to more than 100%.

Split distribution: calibration = 120, test = 120
