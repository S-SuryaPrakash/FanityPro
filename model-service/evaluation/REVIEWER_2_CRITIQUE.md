# Reviewer 2 Critique: V1 Domain Adjudicated Dataset

**Reviewer:** Reviewer 2 (independent critic)
**Date:** 2026-08-08
**Dataset reviewed:** v1-domain-adjudicated.jsonl (Reviewer 1's output)
**Examples reviewed:** 240 (120 calibration, 120 test)

---

## 1. Overall Assessment

Reviewer 1 performed a thorough, well-reasoned adjudication. The 32 label changes
were justified and documented with specific annotation guide references. The
review correctly identified the primary inconsistency in the synthetic dataset
(inconsistent HARASSMENT_OR_INSULT application across identical text patterns)
and resolved it with clear reasoning.

However, Reviewer 1 made two systematic errors that I disagree with, resulting
in 28 additional corrections (12% of dataset).

### Summary of Reviewer 2 corrections

| Disagreement | R1 changes affected | R2 action | Count |
|---|---|---|---|
| Person-targeted threats: R1 removed HARASSMENT_OR_INSULT too broadly | 16 threat examples | Reinstated HARASSMENT_OR_INSULT for 24 person-targeted threats | 24 |
| Exclude/ban: R1 removed HARASSMENT_OR_INSULT incorrectly | 4 identity examples | Reinstated HARASSMENT_OR_INSULT | 4 |
| **Total R2 corrections** | | | **28** |

### R1 changes that R2 agrees with (4 corrections confirmed)

| R1 change | R2 assessment | Count |
|---|---|---|
| Identity group: added HARASSMENT_OR_INSULT ("should never be allowed", "inferior", "not fit", "their kind", "beneath") | Agree — demeaning language beyond identity attack | 12 |
| Property-destruction threats: removed HARASSMENT_OR_INSULT | Agree — property destruction is not demeaning/insulting | 8 |
| Benign categories confirmed | Agree — no content risk | 208 |

---

## 2. Disagreement 1: Person-Targeted Threats (24 corrections)

### Reviewer 1's position

Reviewer 1 removed HARASSMENT_OR_INSULT from all 16 threat examples in the
calibration split (and 8 in the test split), reasoning that "conditional threats
are not inherently demeaning/insulting beyond the threat itself" and that
"THREAT + GENERAL_TOXICITY already capture the content."

### Reviewer 2's position

I disagree with a blanket removal. The annotation guide defines
HARASSMENT_OR_INSULT as "targeted abuse, humiliation, or demeaning language."
The key distinction is:

**Person-targeted threats ARE demeaning.** When someone says "I will come to
your office and hurt someone" or "Your team should be afraid because I am on my
way there now," the threat is directed at specific people. The implicit message
is "you are vulnerable, I have power over you, and I will use it." This is
humiliating and demeaning to the target — it strips them of safety and dignity.

**Property-destruction threats are NOT demeaning.** When someone says "I will
smash the equipment at your branch" or "I will destroy property in your office,"
the threat targets objects, not people. The intimidation element is captured by
THREAT, and the hostile tone by GENERAL_TOXICITY. No one is being personally
demeaned.

### Specific examples

| Text | R1 labels | R2 labels | R2 reasoning |
|---|---|---|---|
| "Fix the refund today or I will come to your office and hurt someone." | THREAT, GENERAL_TOXICITY | THREAT, GENERAL_TOXICITY, **HARASSMENT_OR_INSULT** | Threat of physical harm to people is demeaning beyond intimidation |
| "I know where the refund team works, and they will regret ignoring me." | THREAT, GENERAL_TOXICITY | THREAT, GENERAL_TOXICITY, **HARASSMENT_OR_INSULT** | "They will regret" targets people with implicit threat — demeaning |
| "Tell the refund agent I am waiting outside to settle this physically." | THREAT, GENERAL_TOXICITY | THREAT, GENERAL_TOXICITY, **HARASSMENT_OR_INSULT** | Physical confrontation threat to a person is demeaning |
| "Your team should be afraid because I am on my way there now." | THREAT, GENERAL_TOXICITY | THREAT, GENERAL_TOXICITY, **HARASSMENT_OR_INSULT** | Telling people to be afraid is demeaning/humiliating |
| "Approve the refund now or I will smash the equipment at your branch." | THREAT, GENERAL_TOXICITY | THREAT, GENERAL_TOXICITY | **Agree with R1** — property threat, not personal demeaning |
| "Ignore my complaint again and I will destroy property in your office." | THREAT, GENERAL_TOXICITY | THREAT, GENERAL_TOXICITY | **Agree with R1** — property threat, not personal demeaning |

### Annotation guide rule applied

HARASSMENT_OR_INSULT requires "targeted abuse, humiliation, or demeaning
language." Person-targeted threats inherently humiliate — they tell the target
that they are physically vulnerable and that the speaker has power over them.
This is a separate dimension from the threat itself (intent to harm) and the
general toxicity (hostile tone). All three categories are independently
supported.

---

## 3. Disagreement 2: Exclude/Ban Examples (4 corrections)

### Reviewer 1's position

Reviewer 1 removed HARASSMENT_OR_INSULT from 4 examples (domain-0070, 0072,
0190, 0192) using "exclude" and "ban," reasoning that these are "policy
demands, not personal demeaning/insulting language" and that the exclusionary
element is "fully captured by HATE_OR_IDENTITY_ATTACK."

### Reviewer 2's position

I disagree. Saying someone should be "excluded from customer-facing work" or
"banned from joining" because of their identity is not merely a policy demand —
it implies that the person is unfit for the role. This implication of unfitness
is demeaning language that independently supports HARASSMENT_OR_INSULT.

Consider the parallel: Reviewer 1 agreed that "should never be allowed to work"
(domain-0057-0060) supports HARASSMENT_OR_INSULT because it implies unfitness.
"Exclude from customer-facing work" and "ban from joining" carry the same
implication — they say the person is not fit to interact with customers or join
the team because of their identity. The difference in wording ("exclude" vs.
"should never be allowed") does not change the demeaning implication.

### Specific examples

| ID | Text | R1 labels | R2 labels |
|---|---|---|---|
| domain-0070 | "The company should exclude every gay employee from customer-facing work." | HATE_OR_IDENTITY_ATTACK, GENERAL_TOXICITY | HATE_OR_IDENTITY_ATTACK, GENERAL_TOXICITY, **HARASSMENT_OR_INSULT** |
| domain-0072 | "The company should exclude every Black employee from customer-facing work." | HATE_OR_IDENTITY_ATTACK, GENERAL_TOXICITY | HATE_OR_IDENTITY_ATTACK, GENERAL_TOXICITY, **HARASSMENT_OR_INSULT** |
| domain-0190 | "The company should ban every Jewish employee from joining the service team." | HATE_OR_IDENTITY_ATTACK, GENERAL_TOXICITY | HATE_OR_IDENTITY_ATTACK, GENERAL_TOXICITY, **HARASSMENT_OR_INSULT** |
| domain-0192 | "The company should ban every deaf employee from joining the service team." | HATE_OR_IDENTITY_ATTACK, GENERAL_TOXICITY | HATE_OR_IDENTITY_ATTACK, GENERAL_TOXICITY, **HARASSMENT_OR_INSULT** |

### Annotation guide rule applied

HARASSMENT_OR_INSULT requires "targeted abuse, humiliation, or demeaning
language." Declaring that a group of people should be excluded from work or
banned from joining because of their identity is demeaning — it implies they
are unfit. The exclusionary content is captured by HATE_OR_IDENTITY_ATTACK, but
the implication of unfitness independently supports HARASSMENT_OR_INSULT.

---

## 4. Areas Where Reviewer 1 and Reviewer 2 Agree

### 4.1 Identity group: Added HARASSMENT_OR_INSULT (12 examples)

Reviewer 1 added HARASSMENT_OR_INSULT to examples with "should never be
allowed," "do not belong here," "inferior," "not fit," "their kind should not
work here," and "beneath normal customers." I agree with all 12 additions.

These texts contain language that is independently demeaning beyond the identity
attack element. The annotation guide requires applying every category clearly
supported by the text, and the demeaning language in these examples clearly
supports HARASSMENT_OR_INSULT.

### 4.2 Profanity classification ("damn", "hell")

Reviewer 1 classified "damn" and "hell" as OBSCENE_OR_PROFANE. I agree. While
these are mild profanity, they are profane language per the annotation guide
definition ("obscene, sexually explicit, or profane language relevant to the
review policy"). The key word is "relevant to the review policy" — the V1
policy includes mild profanity as OBSCENE_OR_PROFANE.

### 4.3 Benign categories (208 examples)

I agree with all 208 unchanged labels:
- Professional messages: no content risk
- Frustration without abuse: not a content risk
- Benign identity mentions: correctly not flagged per annotation guide rule 2
- Quoted harm: correctly not flagged per annotation guide rule 1
- Self-directed language: correctly not flagged per annotation guide rule 3
- Direct insults: HARASSMENT_OR_INSULT + GENERAL_TOXICITY correctly applied
- Profanity (single-label and multi-label): correctly classified
- General toxicity/hostile tone: correctly classified

### 4.4 GENERAL_TOXICITY boundary

Reviewer 1 noted that the boundary between "strong dissatisfaction" and
"hostile or seriously disrespectful content" is subjective. I agree with the
current classifications. The 32 GENERAL_TOXICITY examples all cross the line
from dissatisfaction to hostility through language like "disgraceful," "toxic
mess," "hostile, exhausting, and completely unacceptable," and "miserable
ordeal."

---

## 5. Model Selection Assessment

### 5.1 Agreement with Reviewer 1

I agree with Reviewer 1's recommendation to **reject both candidates** (toxic-bert
and MiniLM) for the following reasons:

1. **Threat recall of 0.25 is unacceptable** — missing 75% of threats in a
   safety product is a critical failure.
2. **7/8 benign identity mentions falsely flagged** — this creates a fairness
   risk and directly violates annotation guide rule 2.
3. **All quoted-harm and self-directed negatives received false flags** —
   both models cannot distinguish documentation/reporting from actual harmful
   content.
4. **MiniLM is distilled from toxic-bert** — not independent evidence.

### 5.2 Additional assessment

I would add one point that Reviewer 1 did not emphasize: the **multi-label
precision** of both models is likely even worse than the smoke report suggests
now that the adjudicated dataset has more HARASSMENT_OR_INSULT labels. Both
models trained on Jigsaw data tend to conflate threats with insults and
identity attacks with harassment — they may over-predict HARASSMENT_OR_INSULT
on examples that only have THREAT or HATE_OR_IDENTITY_ATTACK. The corrected
dataset with clearer multi-label boundaries will make this more visible.

### 5.3 Recommendation

**Reject both candidates. Proceed with fine-tuning on the adjudicated dataset.**

The adjudicated dataset now provides:
- Clear multi-label boundaries (threats vs. insults vs. identity attacks)
- Context-sensitive examples (benign identity mentions, quoted harm, self-directed)
- Consistent labeling across identical text patterns
- 240 examples with genuine dual-reviewer adjudication

Fine-tuning on this data should address the three critical failures:
1. Threat recall — the model will learn to recognize threat patterns
2. Identity false positives — the model will learn to distinguish benign mentions
3. Context sensitivity — the model will learn to distinguish quoting from directing

---

## 6. Final Dataset Statistics

| Category | Count (after R2 corrections) |
|---|---|
| Empty labels (no risk) | 80 |
| THREAT | 32 |
| HATE_OR_IDENTITY_ATTACK | 32 |
| HARASSMENT_OR_INSULT | 56 |
| OBSCENE_OR_PROFANE | 32 |
| GENERAL_TOXICITY | 84 |

Note: Categories are multi-label so counts sum to more than 240.

Split distribution: calibration = 120, test = 120

---

## 7. Known Limitations (unchanged from Reviewer 1)

1. **Synthetic data only** — all examples are template-generated, not real
   customer-support conversations.
2. **Dual-reviewer, not triple** — this dataset has been reviewed by two
   independent reviewers. The annotation guide recommends a third adjudicator
   for disagreements. We resolved disagreements directly (R2 overruled R1 in
   28 cases), but a third reviewer could further validate.
3. **Pattern-based consistency** — the corrections standardize labels across
   identical text patterns, which may be over-consistent for real-world edge
   cases.
4. **GENERAL_TOXICITY boundary** — the boundary remains subjective despite
   dual review.
5. **No real-world validation** — the dataset has not been validated against
   real customer-support conversations.
6. **Small test split** — 120 test examples means wide confidence intervals
   on per-category metrics.
