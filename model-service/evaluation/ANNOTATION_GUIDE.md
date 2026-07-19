# V1 annotation guide

Annotate the complete message in context. Labels are multi-select: apply every
category clearly supported by the text. If evidence is genuinely ambiguous,
explain it in `annotation_notes`; do not force a label merely to balance data.

## Categories

- `THREAT`: credible or conditional intent to harm, intimidate, or promote
  violence against a target.
- `HATE_OR_IDENTITY_ATTACK`: demeaning, dehumanising, exclusionary, or harmful
  content directed at a protected identity or a person because of that identity.
- `HARASSMENT_OR_INSULT`: targeted abuse, humiliation, or demeaning language
  that does not require a protected-identity element.
- `OBSCENE_OR_PROFANE`: obscene, sexually explicit, or profane language relevant
  to the review policy.
- `GENERAL_TOXICITY`: hostile or seriously disrespectful content that is not
  better represented only by a more specific label.
- Empty `expected_labels`: no configured risk is supported by the reviewed
  context. This means “no automated flag,” not “guaranteed safe.”

`MANUAL_REVIEW` is not a ground-truth content label. It is a policy outcome for
uncertain, conflicting, or truncated model evidence.

## Context rules

- Quoting a harmful customer message for documentation is not automatically
  misconduct by the person quoting it.
- Benign mentions of identities must not be labelled as identity attacks.
- Self-directed or consensual humorous language requires context and must not be
  treated as targeted harassment automatically.
- Obfuscation, spacing, and punctuation do not remove harmful meaning.
- AI-generated replies and human messages use the same content definitions, but
  keep their origin in `slice_tags` for separate reporting.
- Do not include real customer text, names, account numbers, or other personal
  information in this repository.

## Review process

1. Reviewer 1 assigns labels without seeing Reviewer 2’s labels.
2. Reviewer 2 independently assigns labels.
3. A third authorised reviewer adjudicates disagreements.
4. Only adjudicated labels become evaluation ground truth.
5. Record a short reason for hard or ambiguous cases; never record personal data.
