"""Apply Reviewer 2 corrections to the adjudicated dataset.

Reviewer 2 independently reviews Reviewer 1's adjudication and applies
corrections where the two reviewers disagree. The final dataset reflects
the adjudicated outcome after both reviews.
"""

import json
import sys
from pathlib import Path

DATASET_PATH = Path(__file__).resolve().parent.parent / "evaluation" / "datasets" / "v1-domain-adjudicated.jsonl"

# ── Reviewer 2 disagreements with Reviewer 1 ──────────────────────────
#
# 1. PERSON-TARGETED THREATS: Reviewer 1 removed HARASSMENT_OR_INSULT from
#    all 16 threat examples. Reviewer 2 disagrees for threats that target
#    people personally (intimidation IS demeaning), but agrees for threats
#    of property destruction only. The distinction:
#    - "I will come to your office and hurt someone" → targets people → demeaning
#    - "I will smash the equipment" → targets property → NOT demeaning
#    - "They will regret ignoring me" → targets people → demeaning
#    - "Settle this physically" → targets people → demeaning
#    - "Should be afraid" → targets people → demeaning
#    - "Coming after the person" → targets people → demeaning
#    - "Destroy property" → targets property → NOT demeaning
#
# 2. EXCLUDE/BAN: Reviewer 1 removed HARASSMENT_OR_INSULT from 4 examples
#    using "exclude" and "ban", reasoning these are "policy demands, not
#    personal demeaning language." Reviewer 2 disagrees: saying someone
#    should be excluded from work or banned from joining because of their
#    identity IS demeaning — it implies they are unfit. The exclusionary
#    demand is captured by HATE_OR_IDENTITY_ATTACK, but the implication of
#    unfitness is independently demeaning and supports HARASSMENT_OR_INSULT.

# Person-targeted threats where Reviewer 2 adds HARASSMENT_OR_INSULT back
# (Reviewer 1 removed it; Reviewer 2 disagrees for people-targeting threats)
PERSON_TARGETED_THREATS = {
    # "I will come to your office and hurt someone" — targets people
    "domain-0041", "domain-0042", "domain-0043", "domain-0044",
    # "They will regret ignoring me" — targets people
    "domain-0045", "domain-0046", "domain-0047", "domain-0048",
    # "Settle this physically" — targets people
    "domain-0053", "domain-0054", "domain-0055", "domain-0056",
    # "Should be afraid" — targets people
    "domain-0173", "domain-0174", "domain-0175", "domain-0176",
    # "Unless ... someone is going to get hurt" — targets people
    "domain-0161", "domain-0162", "domain-0163", "domain-0164",
    # "Coming after the person" — targets people
    "domain-0165", "domain-0166", "domain-0167", "domain-0168",
}

# Property-destruction threats where Reviewer 2 agrees with Reviewer 1
# (HARASSMENT_OR_INSULT correctly removed — property threats are not demeaning)
PROPERTY_THREATS = {
    # "I will smash the equipment"
    "domain-0049", "domain-0050", "domain-0051", "domain-0052",
    # "I will destroy property"
    "domain-0169", "domain-0170", "domain-0171", "domain-0172",
}

# Exclude/ban examples where Reviewer 2 adds HARASSMENT_OR_INSULT back
# (Reviewer 1 removed it; Reviewer 2 disagrees — exclusion implies unfitness)
EXCLUDE_BAN_REINSTATE = {
    "domain-0070", "domain-0072",  # calibration: "exclude"
    "domain-0190", "domain-0192",  # test: "ban"
}


def main() -> None:
    if not DATASET_PATH.exists():
        print(f"Dataset not found: {DATASET_PATH}", file=sys.stderr)
        sys.exit(1)

    records = []
    with DATASET_PATH.open("r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                records.append(json.loads(line))

    changes: list[dict] = []

    for record in records:
        rid = record["id"]
        labels = list(record["expected_labels"])
        notes = record["annotation_notes"]
        changed = False

        # Person-targeted threats: add HARASSMENT_OR_INSULT back
        if rid in PERSON_TARGETED_THREATS:
            if "HARASSMENT_OR_INSULT" not in labels:
                labels.append("HARASSMENT_OR_INSULT")
                labels.sort()
                record["expected_labels"] = labels
                notes = (
                    f"Reviewer 2: Disagree with R1 removal — "
                    f"person-targeted threat is demeaning beyond intimidation. "
                    f"Reinstated HARASSMENT_OR_INSULT. {notes}"
                )
                changed = True

        # Exclude/ban examples: add HARASSMENT_OR_INSULT back
        if rid in EXCLUDE_BAN_REINSTATE:
            if "HARASSMENT_OR_INSULT" not in labels:
                labels.append("HARASSMENT_OR_INSULT")
                labels.sort()
                record["expected_labels"] = labels
                notes = (
                    f"Reviewer 2: Disagree with R1 removal — "
                    f"excluding/banning someone from work because of their identity "
                    f"implies unfitness, which is demeaning. "
                    f"Reinstated HARASSMENT_OR_INSULT. {notes}"
                )
                changed = True

        if changed:
            record["annotation_notes"] = notes
            changes.append({
                "id": rid,
                "text": record["text"][:80],
                "change": "added HARASSMENT_OR_INSULT",
            })

    # Write the corrected dataset
    with DATASET_PATH.open("w", encoding="utf-8") as f:
        for record in records:
            f.write(json.dumps(record, ensure_ascii=False) + "\n")

    print(f"Applied {len(changes)} corrections to {DATASET_PATH}")
    print(f"Person-targeted threats: {len(PERSON_TARGETED_THREATS)}")
    print(f"Exclude/ban reinstated: {len(EXCLUDE_BAN_REINSTATE)}")
    print(f"Total changes: {len(changes)}")


if __name__ == "__main__":
    main()
