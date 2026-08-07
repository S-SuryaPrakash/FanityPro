#!/usr/bin/env python3
"""Build v1-domain-adjudicated.jsonl from v1-domain-synthetic.jsonl with genuine Reviewer 1 decisions."""

import json
import sys

INPUT = r"c:\Users\asus\OneDrive\Desktop\FanityPro\contentfilter\model-service\evaluation\datasets\v1-domain-synthetic.jsonl"
OUTPUT = r"c:\Users\asus\OneDrive\Desktop\FanityPro\contentfilter\model-service\evaluation\datasets\v1-domain-adjudicated.jsonl"

# Threat group: REMOVE HARASSMENT_OR_INSULT
THREAT_REMOVE_HARASSMENT = {
    "domain-0041", "domain-0043", "domain-0046", "domain-0048",
    "domain-0049", "domain-0051", "domain-0054", "domain-0056",
    "domain-0161", "domain-0163", "domain-0166", "domain-0168",
    "domain-0169", "domain-0171", "domain-0174", "domain-0176",
}

# Identity attack: ADD HARASSMENT_OR_INSULT
IDENTITY_ADD_HARASSMENT = {
    "domain-0058", "domain-0060",   # "should never be allowed"
    "domain-0061", "domain-0063",   # "do not belong here"
    "domain-0066", "domain-0068",   # "inferior"
    "domain-0178", "domain-0180",   # "not fit to deal with"
    "domain-0181", "domain-0183",   # "their kind should not work here"
    "domain-0186", "domain-0188",   # "beneath normal customers"
}

# Identity attack: REMOVE HARASSMENT_OR_INSULT
IDENTITY_REMOVE_HARASSMENT = {
    "domain-0070", "domain-0072",   # "exclude from customer-facing work"
    "domain-0190", "domain-0192",   # "ban from joining"
}

def annotation_note(rec, changed, eid):
    labels = rec["expected_labels"]
    tags = rec.get("slice_tags", [])

    if "professional" in tags:
        return "Reviewer 1: Agree — professional message, no content risk."
    if "frustration_without_abuse" in tags:
        return "Reviewer 1: Agree — frustration without abuse, no content risk per annotation guide."
    if "benign_identity_mention" in tags:
        return "Reviewer 1: Agree — benign identity mention per annotation guide rule 2, no content risk."
    if "quoted_harm" in tags:
        return "Reviewer 1: Agree — harmful words quoted for documentation per annotation guide rule 1, not directed by speaker."
    if "self_directed_or_nontargeted" in tags:
        return "Reviewer 1: Agree — self-directed or non-targeted language per annotation guide rule 3, no content risk."

    if "direct_threat" in tags:
        if eid in THREAT_REMOVE_HARASSMENT:
            return "Reviewer 1: Changed — removed HARASSMENT_OR_INSULT; threat captured by THREAT, hostile tone by GENERAL_TOXICITY. Conditional threats are not inherently demeaning/insulting beyond the threat itself."
        return "Reviewer 1: Agree — THREAT and GENERAL_TOXICITY supported by conditional/implicit harm intent."

    if "identity_attack" in tags:
        if eid in IDENTITY_ADD_HARASSMENT:
            return "Reviewer 1: Changed — added HARASSMENT_OR_INSULT; text contains demeaning language (implies unfitness/inferiority) beyond the identity attack element, consistent with annotation guide multi-label rule."
        if eid in IDENTITY_REMOVE_HARASSMENT:
            return "Reviewer 1: Changed — removed HARASSMENT_OR_INSULT; exclusionary demand (exclude/ban) is captured by HATE_OR_IDENTITY_ATTACK, not targeted humiliation or demeaning language."
        return "Reviewer 1: Agree — HATE_OR_IDENTITY_ATTACK and GENERAL_TOXICITY supported by exclusionary/demeaning content directed at a protected identity."

    if "direct_insult" in tags:
        return "Reviewer 1: Agree — HARASSMENT_OR_INSULT supported by targeted demeaning language; GENERAL_TOXICITY supported by hostile tone."

    if "profanity" in tags and "single_label" in tags:
        return "Reviewer 1: Agree — OBSCENE_OR_PROFANE supported by profane language; no additional hostility beyond the profanity itself."
    if "profanity" in tags and "multi_label" in tags:
        return "Reviewer 1: Agree — OBSCENE_OR_PROFANE supported by profane language; GENERAL_TOXICITY supported by hostile/dismissive tone beyond profanity alone."

    if "hostile_tone" in tags:
        return "Reviewer 1: Agree — GENERAL_TOXICITY supported by hostile or seriously disrespectful tone; no more specific label applies."

    return "Reviewer 1: Reviewed — labels match text content per annotation guide."


def main():
    with open(INPUT, "r", encoding="utf-8") as f:
        records = [json.loads(line) for line in f if line.strip()]

    print(f"Read {len(records)} records from synthetic dataset")

    changes = {
        "threat_remove_harassment": 0,
        "identity_add_harassment": 0,
        "identity_remove_harassment": 0,
        "total_changed": 0,
    }

    adjudicated = []
    for rec in records:
        eid = rec["id"]
        labels = list(rec["expected_labels"])
        changed = False

        if eid in THREAT_REMOVE_HARASSMENT:
            if "HARASSMENT_OR_INSULT" in labels:
                labels.remove("HARASSMENT_OR_INSULT")
                changes["threat_remove_harassment"] += 1
                changed = True

        if eid in IDENTITY_ADD_HARASSMENT:
            if "HARASSMENT_OR_INSULT" not in labels:
                labels.append("HARASSMENT_OR_INSULT")
                labels.sort()
                changes["identity_add_harassment"] += 1
                changed = True

        if eid in IDENTITY_REMOVE_HARASSMENT:
            if "HARASSMENT_OR_INSULT" in labels:
                labels.remove("HARASSMENT_OR_INSULT")
                changes["identity_remove_harassment"] += 1
                changed = True

        if changed:
            changes["total_changed"] += 1

        out = {
            "dataset_version": "v1-domain-adjudicated",
            "id": rec["id"],
            "split": rec["split"],
            "text": rec["text"],
            "expected_labels": labels,
            "language": rec["language"],
            "speaker_role": rec["speaker_role"],
            "content_origin": rec["content_origin"],
            "slice_tags": rec["slice_tags"],
            "review_status": "adjudicated",
            "annotation_notes": annotation_note(rec, changed, eid),
        }
        adjudicated.append(out)

    with open(OUTPUT, "w", encoding="utf-8") as f:
        for rec in adjudicated:
            f.write(json.dumps(rec, ensure_ascii=False) + "\n")

    print(f"Wrote {len(adjudicated)} records to adjudicated dataset")
    print(f"  Threat group: removed HARASSMENT_OR_INSULT from {changes['threat_remove_harassment']} examples")
    print(f"  Identity group: added HARASSMENT_OR_INSULT to {changes['identity_add_harassment']} examples")
    print(f"  Identity group: removed HARASSMENT_OR_INSULT from {changes['identity_remove_harassment']} examples")
    print(f"  Total examples with label changes: {changes['total_changed']}")

    # Validation
    ids = set()
    texts_norm = set()
    valid_labels = {"THREAT", "HATE_OR_IDENTITY_ATTACK", "HARASSMENT_OR_INSULT", "OBSCENE_OR_PROFANE", "GENERAL_TOXICITY"}
    valid_splits = {"calibration", "test"}
    errors = []

    for rec in adjudicated:
        eid = rec["id"]
        if eid in ids:
            errors.append(f"Duplicate ID: {eid}")
        ids.add(eid)

        norm_text = rec["text"].strip().lower()
        if norm_text in texts_norm:
            errors.append(f"Duplicate normalized text: {rec['text'][:60]}...")
        texts_norm.add(norm_text)

        for lbl in rec["expected_labels"]:
            if lbl not in valid_labels:
                errors.append(f"Invalid label '{lbl}' in {eid}")

        if rec["split"] not in valid_splits:
            errors.append(f"Invalid split '{rec['split']}' in {eid}")

        if rec["review_status"] != "adjudicated":
            errors.append(f"Invalid review_status '{rec['review_status']}' in {eid}")

        if rec["dataset_version"] != "v1-domain-adjudicated":
            errors.append(f"Invalid dataset_version '{rec['dataset_version']}' in {eid}")

        required = {"dataset_version", "id", "split", "text", "expected_labels", "language", "speaker_role", "content_origin", "slice_tags", "review_status", "annotation_notes"}
        missing = required - set(rec.keys())
        if missing:
            errors.append(f"Missing fields {missing} in {eid}")

    if errors:
        print(f"\nVALIDATION ERRORS ({len(errors)}):")
        for e in errors:
            print(f"  - {e}")
        sys.exit(1)
    else:
        print(f"\nAll validation checks passed!")
        print(f"  - {len(adjudicated)} records")
        print(f"  - Unique IDs: {len(ids)}")
        print(f"  - Unique normalized texts: {len(texts_norm)}")

    from collections import Counter
    label_counts = Counter()
    for rec in adjudicated:
        for lbl in rec["expected_labels"]:
            label_counts[lbl] += 1
    empty_count = sum(1 for rec in adjudicated if not rec["expected_labels"])
    print(f"\nLabel distribution:")
    for lbl in sorted(label_counts):
        print(f"  {lbl}: {label_counts[lbl]}")
    print(f"  (no labels): {empty_count}")

    cal = sum(1 for r in adjudicated if r["split"] == "calibration")
    test = sum(1 for r in adjudicated if r["split"] == "test")
    print(f"\nSplit distribution: calibration={cal}, test={test}")


if __name__ == "__main__":
    main()
