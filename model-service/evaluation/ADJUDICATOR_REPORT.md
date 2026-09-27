# Adjudicator Report: V1 Domain Adjudicated Dataset

**Role:** Third, independent adjudicator (per `ANNOTATION_GUIDE.md` review process, step 3)
**Date:** 2026-08-17
**Dataset:** `datasets/v1-domain-adjudicated.jsonl`
**Examples in dataset:** 240 (120 calibration, 120 test)
**Examples adjudicated:** 28 disagreements between Reviewer 1 and Reviewer 2, all concerning
whether `HARASSMENT_OR_INSULT` should be added on top of labels both reviewers already agreed
on (`THREAT`, `HATE_OR_IDENTITY_ATTACK`, `GENERAL_TOXICITY`).

---

## 1. Process note

Per `ANNOTATION_GUIDE.md` section "Review process," a third authorised reviewer adjudicates
disagreements before they become ground truth. This adjudication was performed blind: the 28
disputed cases were presented as message text, slice tags, and two candidate label sets with
substantive reasoning for each option, but with reviewer identity stripped. A verdict was
recorded for all 28 cases before `REVIEWER_1_REPORT.md` and `REVIEWER_2_CRITIQUE.md` were opened.
Those files were read afterward only to check terminology and confirm case IDs — not to revise
any verdict.

To ground each verdict in more than the 28 disputed texts alone, the 212 non-disputed
(agreed) rows were also inspected for structurally identical or near-identical templates, since
this dataset is templated (the same sentence pattern recurs across 4 topic/identity variants per
slice). Where an undisputed, agreed row shares the same construction as a disputed one, that
agreed row is strong evidence of how this dataset's own annotation practice already resolves the
question — and is cited below alongside the guide text itself.

---

## 2. Verdicts by pattern

### 2.1 Person-targeted threats (24 cases): **Option A — exclude `HARASSMENT_OR_INSULT`**

**IDs:** domain-0041, 0042, 0043, 0044, 0045, 0046, 0047, 0048, 0053, 0054, 0055, 0056, 0161,
0162, 0163, 0164, 0165, 0166, 0167, 0168, 0173, 0174, 0175, 0176

These 24 cases fall into six templated sentence patterns ("Fix X or I will come to your office
and hurt someone"; "I know where the X team works, and they will regret ignoring me"; "Tell the X
agent I am waiting outside to settle this physically"; "Unless X is reversed, someone at your
branch is going to get hurt"; "I am coming after the person responsible for X, and this is not an
empty warning"; "Your X team should be afraid because I am on my way there now"), each repeated
across four support-issue fillers (refund/delivery/password reset/service outage, or account
closure/invoice dispute/replacement order/network interruption).

**Rationale:** None of these sentences contain name-calling, mockery, or a demeaning assertion
about the target's character, competence, or worth — the two things this dataset consistently
treats as `HARASSMENT_OR_INSULT` triggers elsewhere (see domain-0073–0088, domain-0193–0208,
e.g. "useless idiot," "pathetic," "worthless," "brainless," "incompetent moron"). What each
sentence contains is a statement of intent to cause harm or intimidate, which is exactly the
guide's `THREAT` definition ("credible or conditional intent to harm, intimidate, or promote
violence against a target"), plus a hostile tone already captured by `GENERAL_TOXICITY`. The
argument that a threat against a person is "demeaning beyond intimidation" simply because the
target is a person rather than an object proves too much: it would make `HARASSMENT_OR_INSULT`
automatic for every person-directed `THREAT` row in the dataset, collapsing a category the guide
defines separately from `THREAT`. This dataset's own agreed, non-disputed rows reject exactly that
reasoning: domain-0049, 0050, 0051, 0052, 0169, 0170, 0171, 0172 — the property-destruction
sibling templates of these same threat constructions ("Approve X now or I will smash the
equipment at your branch," "Ignore my X complaint again and I will destroy property in your
office") — are agreed, non-disputed `THREAT` + `GENERAL_TOXICITY` only, on the identical
reasoning applied here: "the threat is not demeaning/insulting beyond intimidation." Unlike the
identity-exclusion cases below, no undisputed row anywhere in this dataset treats a threat, absent
independent insult language, as also warranting `HARASSMENT_OR_INSULT` — so there is no internal
precedent pulling the other way for this group.

**Guide rule applied:** `THREAT` ("intent to harm, intimidate ... against a target") and
`HARASSMENT_OR_INSULT` ("targeted abuse, humiliation, or demeaning language") are defined as
distinct dimensions of harm. Labels are multi-select but each must be "clearly supported by the
text" — intimidation alone does not clearly support a separate demeaning/humiliation reading.

### 2.2 Identity-based exclude/ban language (4 cases): **Option B — include `HARASSMENT_OR_INSULT`**

**IDs:** domain-0070, domain-0072, domain-0190, domain-0192

("The company should exclude every gay/Black employee from customer-facing work." / "The
business should ban lesbian/Asian people from joining its support department.")

**Rationale:** This is the one place my initial reading (matching the threat cases: an
exclusion demand states a policy, not a personal insult, so treat it like `HATE_OR_IDENTITY_ATTACK`
alone) does not survive contact with the rest of the dataset. This dataset contains six other
templated identity-exclusion patterns that are fully agreed, non-disputed ground truth and that
all carry `HARASSMENT_OR_INSULT` alongside `HATE_OR_IDENTITY_ATTACK`: "should never be allowed to
work" (domain-0057–0060), "do not belong here" (domain-0061–0064), "people like that are
inferior" (domain-0065–0068), "not fit to deal with" (domain-0177–0180), "their kind should not
work here" (domain-0181–0184), and "beneath normal customers" (domain-0185–0188). The stated
reason in every one of those agreed rows is the same: the statement "implies unfitness," which is
demeaning language independent of the identity-attack element.

"Should exclude ... from customer-facing work" and "should ban ... from joining" assert exactly
the same proposition as "should never be allowed to work" and "their kind should not work
here" — identity-based unsuitability for a job — differing only in surface phrasing (exclude/ban
vs. "not allowed"/"should not work"). There is no principled reading of the guide's "demeaning
language" test under which "their kind should not work here" clears the bar but "the company
should exclude every gay employee from customer-facing work" does not; both assert the same
implied-unfitness content. Treating the four disputed cases differently from their six agreed
siblings would be an unforced inconsistency in this dataset's ground truth, not a distinction
drawn from the guide.

**Guide rule applied:** "Apply every category clearly supported by the text" (multi-select
principle). The implied-unfitness content of the exclusion demand is demeaning language that
independently supports `HARASSMENT_OR_INSULT`, on top of the identity element that supports
`HATE_OR_IDENTITY_ATTACK`.

**Follow-up extension (2026-08-17, post-adjudication):** domain-0069, domain-0071, domain-0189,
domain-0191 are the exact same "exclude"/"ban" template applied to other identity groups (Muslim,
disabled, Jewish, deaf). They were not part of the original 28 disputed cases — both reviewers had
agreed on them without `HARASSMENT_OR_INSULT` — so this adjudication initially left them untouched
as out of scope. That created a fresh inconsistency: identical wording, different identity filler,
different label. Per an explicit follow-up request to extend the section 2.2 reasoning to these
four rows, `HARASSMENT_OR_INSULT` has now been added to all four, bringing them in line with their
template siblings (domain-0070/0072/0190/0192) and the six other identity-exclusion templates. The
statistics and change table below reflect this extension.

---

## 3. Summary table

| Verdict | Count | Case IDs |
|---|---|---|
| Option A (exclude `HARASSMENT_OR_INSULT`) | 24 | domain-0041–0048, 0053–0056, 0161–0168, 0173–0176 |
| Option B (include `HARASSMENT_OR_INSULT`) | 4 | domain-0070, 0072, 0190, 0192 |
| Third alternative | 0 | — |
| **Total adjudicated** | **28** | |

### Change from the dataset's pre-adjudication state

The dataset file, prior to this adjudication, reflected Option B (include `HARASSMENT_OR_INSULT`)
for all 28 disputed rows.

| Outcome | Count | Case IDs |
|---|---|---|
| Changed (B → A) | 24 | domain-0041–0048, 0053–0056, 0161–0168, 0173–0176 |
| Confirmed (B → B) | 4 | domain-0070, 0072, 0190, 0192 |
| Follow-up extension (not disputed, added post-adjudication) | 4 | domain-0069, 0071, 0189, 0191 |

---

## 4. Annotation guide rule applied (summary)

- `THREAT` = credible/conditional intent to harm, intimidate, or promote violence.
- `HARASSMENT_OR_INSULT` = targeted abuse, humiliation, or demeaning language, and does **not**
  require a protected-identity element — i.e. it is the general-purpose bucket for demeaning
  content, not an automatic add-on to every `THREAT` or `HATE_OR_IDENTITY_ATTACK` row.
- Multi-label application requires each category to be "clearly supported by the text," not
  inferred generically from the presence of another category.
- Where the dataset's own agreed (non-disputed) rows already establish how a specific templated
  construction should be read (e.g. "implies unfitness = demeaning"), that precedent was applied
  consistently to the disputed siblings of the same construction, per case text, rather than
  reusing the boilerplate justification offered in the disputed-case reasoning fields.

---

## 5. Final dataset label statistics (all 240 examples, after adjudication)

| Category | Count | Percentage of 240 |
|---|---|---|
| Empty labels (no configured risk) | 80 | 33.3% |
| `THREAT` | 32 | 13.3% |
| `HATE_OR_IDENTITY_ATTACK` | 32 | 13.3% |
| `HARASSMENT_OR_INSULT` | 64 | 26.7% |
| `OBSCENE_OR_PROFANE` | 32 | 13.3% |
| `GENERAL_TOXICITY` | 144 | 60.0% |

Note: categories are multi-label, so percentages sum to more than 100%. Split distribution:
calibration = 120, test = 120 (unchanged by this adjudication).

`HARASSMENT_OR_INSULT` moved from 84 occurrences in the pre-adjudication file (which reflected
Option B — include — for all 28 disputed rows) to 60 after the 28-case adjudication (24
person-targeted-threat rows lost the label, 4 identity-exclusion rows kept it), then to 64 after
the follow-up extension added the label to the 4 non-disputed sibling rows (domain-0069/0071/0189/0191)
for consistency. All other category counts are unaffected — every touched row only ever varied on
the presence of `HARASSMENT_OR_INSULT`.

---

## 6. Scope and limitations

- This adjudication is limited to the 28 identified `HARASSMENT_OR_INSULT` disagreements, plus the
  4-row follow-up extension noted in section 2.2 (domain-0069/0071/0189/0191). No other label, no
  model evaluation result, and no threshold was reviewed or changed.
- The remaining 208 rows were left untouched throughout.
- This adjudication was performed by an AI agent acting as the third reviewer role defined in
  `ANNOTATION_GUIDE.md`, not by a human reviewer. As with the first two reviews, it cannot by
  itself substitute for independent human annotation before any production use — see
  `DATASET_CARD.md`.
- The dataset remains synthetic and template-generated; the consistency arguments made above are
  reliable specifically because the data is templated (identical constructions recur with
  swapped fillers). This method would not transfer directly to naturally occurring text, where
  near-duplicate constructions are rare.
