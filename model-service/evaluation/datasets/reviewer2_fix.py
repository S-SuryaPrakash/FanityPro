#!/usr/bin/env python3
"""
Generate the corrected v1-domain-adjudicated.jsonl by applying Reviewer 2 changes
on top of Reviewer 1's adjudicated dataset.

Run from the project directory:
    python model-service/evaluation/datasets/reviewer2_fix.py
"""

import json
import os
from collections import Counter

# ── Paths ────────────────────────────────────────────────────────────────────
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
INPUT_FILE = os.path.join(SCRIPT_DIR, "v1-domain-adjudicated.jsonl")
OUTPUT_FILE = os.path.join(SCRIPT_DIR, "v1-domain-adjudicated.jsonl")  # overwrite in-place

# ── Records where HARASSMENT_OR_INSULT should be removed ─────────────────────
# domain-0057–0060: "should never be allowed to work" — exclusionary demand
# domain-0061–0064: "do not belong here" — exclusionary demand
REMOVE_HARASSMENT_IDS = {
    "domain-0057", "domain-0058", "domain-0059", "domain-0060",
    "domain-0061", "domain-0062", "domain-0063", "domain-0064",
}

# ── Annotation notes for changed records ─────────────────────────────────────
CHANGE_NOTES = {
    "domain-0057": "Reviewer 2: Disagree — 'should never be allowed to work' is an exclusionary demand, not demeaning language. Consistent with Reviewer 1's own reasoning for 'exclude'/'ban' examples. Changed from [HATE_OR_IDENTITY_ATTACK, GENERAL_TOXICITY, HARASSMENT_OR_INSULT] to [HATE_OR_IDENTITY_ATTACK, GENERAL_TOXICITY].",
    "domain-0058": "Reviewer 2: Disagree — 'should never be allowed to work' is an exclusionary demand, not demeaning language. Consistent with Reviewer 1's own reasoning for 'exclude'/'ban' examples. Changed from [HATE_OR_IDENTITY_ATTACK, GENERAL_TOXICITY, HARASSMENT_OR_INSULT] to [HATE_OR_IDENTITY_ATTACK, GENERAL_TOXICITY].",
    "domain-0059": "Reviewer 2: Disagree — 'should never be allowed to work' is an exclusionary demand, not demeaning language. Consistent with Reviewer 1's own reasoning for 'exclude'/'ban' examples. Changed from [HATE_OR_IDENTITY_ATTACK, GENERAL_TOXICITY, HARASSMENT_OR_INSULT] to [HATE_OR_IDENTITY_ATTACK, GENERAL_TOXICITY].",
    "domain-0060": "Reviewer 2: Disagree — 'should never be allowed to work' is an exclusionary demand, not demeaning language. Consistent with Reviewer 1's own reasoning for 'exclude'/'ban' examples. Changed from [HATE_OR_IDENTITY_ATTACK, GENERAL_TOXICITY, HARASSMENT_OR_INSULT] to [HATE_OR_IDENTITY_ATTACK, GENERAL_TOXICITY].",
    "domain-0061": "Reviewer 2: Disagree — 'do not belong here' is exclusionary, not demeaning language. Consistent with Reviewer 1's own reasoning for 'exclude'/'ban' examples. Changed from [HATE_OR_IDENTITY_ATTACK, GENERAL_TOXICITY, HARASSMENT_OR_INSULT] to [HATE_OR_IDENTITY_ATTACK, GENERAL_TOXICITY].",
    "domain-0062": "Reviewer 2: Disagree — 'do not belong here' is exclusionary, not demeaning language. Consistent with Reviewer 1's own reasoning for 'exclude'/'ban' examples. Changed from [HATE_OR_IDENTITY_ATTACK, GENERAL_TOXICITY, HARASSMENT_OR_INSULT] to [HATE_OR_IDENTITY_ATTACK, GENERAL_TOXICITY].",
    "domain-0063": "Reviewer 2: Disagree — 'do not belong here' is exclusionary, not demeaning language. Consistent with Reviewer 1's own reasoning for 'exclude'/'ban' examples. Changed from [HATE_OR_IDENTITY_ATTACK, GENERAL_TOXICITY, HARASSMENT_OR_INSULT] to [HATE_OR_IDENTITY_ATTACK, GENERAL_TOXICITY].",
    "domain-0064": "Reviewer 2: Disagree — 'do not belong here' is exclusionary, not demeaning language. Consistent with Reviewer 1's own reasoning for 'exclude'/'ban' examples. Changed from [HATE_OR_IDENTITY_ATTACK, GENERAL_TOXICITY, HARASSMENT_OR_INSULT] to [HATE_OR_IDENTITY_ATTACK, GENERAL_TOXICITY].",
}

# ── Reviewer 2 agreement notes by ID range ───────────────────────────────────
AGREEMENT_NOTES = {
    # Professional/benign
    (1, 8):   "Reviewer 2: Agree — professional message, no content risk.",
    (9, 16):  "Reviewer 2: Agree — frustration without abuse, no content risk per annotation guide.",
    # Frustration without abuse
    (121, 128): "Reviewer 2: Agree — professional message, no content risk.",
    (129, 136): "Reviewer 2: Agree — frustration without abuse, no content risk per annotation guide.",
    # Benign identity mention
    (17, 24):  "Reviewer 2: Agree — benign identity mention per annotation guide rule 2, no content risk.",
    (137, 144): "Reviewer 2: Agree — benign identity mention per annotation guide rule 2, no content risk.",
    # Quoted harm
    (25, 32):  "Reviewer 2: Agree — quoted harm for documentation per annotation guide rule 1, not directed by speaker.",
    (145, 152): "Reviewer 2: Agree — quoted harm for documentation per annotation guide rule 1, not directed by speaker.",
    # Self-directed
    (33, 40):  "Reviewer 2: Agree — self-directed/non-targeted language per annotation guide rule 3, no content risk.",
    (153, 160): "Reviewer 2: Agree — self-directed/non-targeted language per annotation guide rule 3, no content risk.",
    # Threats
    (41, 56):  "Reviewer 2: Agree — threats are intimidation captured by THREAT, not demeaning/insulting language. HARASSMENT_OR_INSULT does not apply.",
    (161, 176): "Reviewer 2: Agree — threats are intimidation captured by THREAT, not demeaning/insulting language. HARASSMENT_OR_INSULT does not apply.",
    # Identity attacks with "inferior"
    (65, 68):  "Reviewer 2: Agree — 'inferior' is explicitly demeaning language supporting HARASSMENT_OR_INSULT.",
    # Identity attacks with "exclude"
    (69, 72):  "Reviewer 2: Agree — 'exclude' is an exclusionary demand, not demeaning language. No HARASSMENT_OR_INSULT.",
    # Direct insults
    (73, 88):  "Reviewer 2: Agree — targeted demeaning language supports HARASSMENT_OR_INSULT and GENERAL_TOXICITY.",
    (193, 208): "Reviewer 2: Agree — targeted demeaning language supports HARASSMENT_OR_INSULT and GENERAL_TOXICITY.",
    # Profanity single-label
    (89, 96):  "Reviewer 2: Agree — OBSCENE_OR_PROFANE supported by profane language; no additional hostility beyond the profanity itself.",
    (209, 216): "Reviewer 2: Agree — OBSCENE_OR_PROFANE supported by profane language; no additional hostility beyond the profanity itself.",
    # Profanity multi-label
    (97, 104): "Reviewer 2: Agree — OBSCENE_OR_PROFANE supported by profane language; GENERAL_TOXICITY supported by hostile/dismissive tone beyond profanity alone.",
    (217, 224): "Reviewer 2: Agree — OBSCENE_OR_PROFANE supported by profane language; GENERAL_TOXICITY supported by hostile/dismissive tone beyond profanity alone.",
    # General toxicity
    (105, 120): "Reviewer 2: Agree — GENERAL_TOXICITY supported by hostile/seriously disrespectful tone.",
    (225, 240): "Reviewer 2: Agree — GENERAL_TOXICITY supported by hostile/seriously disrespectful tone.",
    # Identity attacks with "not fit"
    (177, 180): "Reviewer 2: Agree — 'not fit' implies unfitness, which is demeaning language supporting HARASSMENT_OR_INSULT.",
    # Identity attacks with "their kind"
    (181, 184): "Reviewer 2: Agree — 'their kind should not work here' is demeaning language supporting HARASSMENT_OR_INSULT.",
    # Identity attacks with "beneath"
    (185, 188): "Reviewer 2: Agree — 'beneath normal customers' is explicitly demeaning language supporting HARASSMENT_OR_INSULT.",
    # Identity attacks with "ban"
    (189, 192): "Reviewer 2: Agree — 'ban' is an exclusionary demand, not demeaning language. No HARASSMENT_OR_INSULT.",
}


def get_agreement_note(record_id: str) -> str:
    """Look up the Reviewer 2 agreement note for a record ID."""
    num = int(record_id.split("-")[1])
    for (lo, hi), note in AGREEMENT_NOTES.items():
        if lo <= num <= hi:
            return note
    raise ValueError(f"No agreement note defined for {record_id}")


def main():
    # Read all records
    with open(INPUT_FILE, "r", encoding="utf-8") as f:
        records = [json.loads(line) for line in f]

    print(f"Read {len(records)} records from {INPUT_FILE}")

    changed_count = 0
    unchanged_count = 0

    for rec in records:
        rec_id = rec["id"]
        rec["dataset_version"] = "v1-domain-adjudicated"
        rec["review_status"] = "adjudicated"

        if rec_id in REMOVE_HARASSMENT_IDS:
            # Remove HARASSMENT_OR_INSULT from expected_labels
            old_labels = list(rec["expected_labels"])
            rec["expected_labels"] = [l for l in rec["expected_labels"] if l != "HARASSMENT_OR_INSULT"]
            # Append Reviewer 2 disagreement note
            rec["annotation_notes"] = rec["annotation_notes"] + " " + CHANGE_NOTES[rec_id]
            changed_count += 1
        else:
            # Append Reviewer 2 agreement note
            note = get_agreement_note(rec_id)
            rec["annotation_notes"] = rec["annotation_notes"] + " " + note
            unchanged_count += 1

    # Write output
    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        for rec in records:
            f.write(json.dumps(rec, ensure_ascii=False) + "\n")

    print(f"Wrote {len(records)} records to {OUTPUT_FILE}")
    print(f"  Changed:   {changed_count}")
    print(f"  Unchanged: {unchanged_count}")

    # ── Statistics ────────────────────────────────────────────────────────────
    label_counter = Counter()
    label_set_counter = Counter()
    for rec in records:
        labels = rec["expected_labels"]
        label_counter.update(labels)
        label_set_counter[frozenset(labels)] += 1

    print("\n=== Final Dataset Statistics ===")
    print(f"Total records: {len(records)}")
    print(f"\nLabel counts:")
    for label, count in sorted(label_counter.items(), key=lambda x: -x[1]):
        print(f"  {label}: {count}")

    print(f"\nLabel combination counts:")
    for label_set, count in sorted(label_set_counter.items(), key=lambda x: -x[1]):
        labels_str = "[" + ", ".join(sorted(label_set)) + "]" if label_set else "[]"
        print(f"  {labels_str}: {count}")

    # Verify the 8 changed records
    print("\n=== Verification of Changed Records ===")
    for rec in records:
        if rec["id"] in REMOVE_HARASSMENT_IDS:
            has_harassment = "HARASSMENT_OR_INSULT" in rec["expected_labels"]
            print(f"  {rec['id']}: expected_labels={rec['expected_labels']}, "
                  f"HARASSMENT_OR_INSULT removed={not has_harassment}")


if __name__ == "__main__":
    main()
