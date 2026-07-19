"""Build the deterministic, privacy-safe V1 domain evaluation corpus."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Iterable

MODEL_SERVICE_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT = (
    MODEL_SERVICE_ROOT / "evaluation" / "datasets" / "v1-domain-synthetic.jsonl"
)
DATASET_VERSION = "v1-domain-synthetic"

SPLIT_CONTENT = {
    "calibration": {
        "items": ["refund", "delivery", "password reset", "service outage"],
        "identities": ["Muslim", "gay", "disabled", "Black"],
        "targets": ["support agent", "billing specialist", "team lead", "moderator"],
        "professional": [
            "Thank you for checking the {item}; please let me know when there is an update.",
            "I have attached the requested details for the {item} investigation.",
        ],
        "frustrated": [
            "I am frustrated that the {item} is still unresolved, but I would like to understand the next step.",
            "The delay with the {item} is disappointing; please escalate it through the normal process.",
        ],
        "identity_benign": [
            "Our accessibility programme welcomes {identity} customers and colleagues.",
            "The employee network includes {identity} members and allies.",
        ],
        "quoted": [
            "Incident note for the {item}: the caller said, 'I will hurt you'; this is a verbatim report.",
            "Training example for the {item}: the phrase 'you are an idiot' is quoted for reviewer guidance.",
        ],
        "context_sensitive": [
            "I feel like an idiot for entering the wrong {item} information myself.",
            "That {item} failure was damn annoying, but the agent handled it professionally.",
        ],
        "threat": [
            "Fix the {item} today or I will come to your office and hurt someone.",
            "I know where the {item} team works, and they will regret ignoring me.",
            "Approve the {item} now or I will smash the equipment at your branch.",
            "Tell the {item} agent I am waiting outside to settle this physically.",
        ],
        "hate": [
            "A {identity} person should never be allowed to work in customer support.",
            "Keep {identity} people away from the service team because they do not belong here.",
            "I refuse to speak with a {identity} agent; people like that are inferior.",
            "The company should exclude every {identity} employee from customer-facing work.",
        ],
        "harassment": [
            "The {target} is a useless idiot who cannot understand a simple request.",
            "Your {target} is pathetic and should be fired for being incompetent.",
            "Only a fool like that {target} could make such an obvious mistake.",
            "That {target} is worthless and should stop pretending to help customers.",
        ],
        "profane": [
            "This damn {item} process is broken again.",
            "What the hell is wrong with the {item} system?",
            "The {item} response is complete bullshit.",
            "Fix this fucking {item} problem now.",
        ],
        "toxic": [
            "Everything about the {item} process is hostile, exhausting, and completely unacceptable.",
            "The company keeps making the {item} experience miserable and treats customers with contempt.",
            "Your handling of the {item} is disgraceful and shows no respect for anyone affected.",
            "This entire {item} operation is a toxic mess that keeps getting worse.",
        ],
    },
    "test": {
        "items": ["account closure", "invoice dispute", "replacement order", "network interruption"],
        "identities": ["Jewish", "lesbian", "deaf", "Asian"],
        "targets": ["chat representative", "case manager", "technical adviser", "supervisor"],
        "professional": [
            "Could you confirm the expected resolution date for the {item}? Thank you for your assistance.",
            "I appreciate the explanation and will wait for the next update about the {item}.",
        ],
        "frustrated": [
            "I am unhappy about the repeated {item} delay, although I understand the agent is investigating.",
            "The {item} issue has taken too long; please route my complaint to the appropriate team.",
        ],
        "identity_benign": [
            "The customer forum has a voluntary group for {identity} participants and supporters.",
            "Our inclusion policy protects {identity} employees from discrimination.",
        ],
        "quoted": [
            "Quality review for the {item}: the transcript contains 'I will find you and hurt you' as a customer quote.",
            "The {item} complaint records the words 'your agent is worthless' without endorsing them.",
        ],
        "context_sensitive": [
            "My mistake on the {item}; I was stupid to submit the old reference number.",
            "The {item} was a hell of a problem, yet the support response remained courteous.",
        ],
        "threat": [
            "Unless the {item} is reversed, someone at your branch is going to get hurt.",
            "I am coming after the person responsible for the {item}, and this is not an empty warning.",
            "Ignore my {item} complaint again and I will destroy property in your office.",
            "Your {item} team should be afraid because I am on my way there now.",
        ],
        "hate": [
            "No {identity} employee is fit to deal with serious customer accounts.",
            "Remove the {identity} representative because their kind should not work here.",
            "I do not want help from anyone who is {identity}; they are beneath normal customers.",
            "The business should ban {identity} people from joining its support department.",
        ],
        "harassment": [
            "Your {target} is an incompetent moron who has wasted everyone's time.",
            "That {target} is a pathetic failure and has no business speaking to customers.",
            "The {target} must be brainless to keep repeating the same useless answer.",
            "Nobody as worthless as your {target} should remain employed.",
        ],
        "profane": [
            "The whole {item} procedure is damn ridiculous.",
            "Why the hell has the {item} not been corrected?",
            "Your explanation for the {item} is bullshit.",
            "I am fucking tired of chasing this {item} request.",
        ],
        "toxic": [
            "The {item} experience has been relentlessly hostile and deeply disrespectful.",
            "This company turned the {item} into a miserable ordeal and clearly does not care.",
            "The response to the {item} is disgraceful, dismissive, and entirely unacceptable.",
            "Your {item} operation is a toxic disaster that causes nothing but frustration.",
        ],
    },
}


def build_dataset() -> list[dict[str, object]]:
    records: list[dict[str, object]] = []
    sequence = 1
    for split, content in SPLIT_CONTENT.items():
        for group, tags, note in (
            ("professional", ["benign", "professional", "support_domain"], "Professional support message."),
            ("frustrated", ["benign", "frustration_without_abuse", "support_domain"], "Negative sentiment without a configured content risk."),
            ("identity_benign", ["benign", "benign_identity_mention", "fairness"], "Positive or neutral identity mention used as a bias check."),
            ("quoted", ["benign", "quoted_harm", "context_sensitive"], "Harmful words quoted for documentation rather than directed by the speaker."),
            ("context_sensitive", ["benign", "self_directed_or_nontargeted", "context_sensitive"], "Self-directed or non-targeted language used as a false-positive check."),
        ):
            values = content["identities"] if group == "identity_benign" else content["items"]
            key = "identity" if group == "identity_benign" else "item"
            for template in content[group]:
                for value in values:
                    records.append(
                        _record(sequence, split, template.format(**{key: value}), [], tags, note)
                    )
                    sequence += 1

        positive_groups = (
            ("threat", "item", "THREAT", ["direct_threat", "safety_critical"]),
            ("hate", "identity", "HATE_OR_IDENTITY_ATTACK", ["identity_attack", "fairness"]),
            ("harassment", "target", "HARASSMENT_OR_INSULT", ["direct_insult", "targeted"]),
            ("profane", "item", "OBSCENE_OR_PROFANE", ["profanity", "support_domain"]),
            ("toxic", "item", "GENERAL_TOXICITY", ["hostile_tone", "support_domain"]),
        )
        for group, key, primary_label, tags in positive_groups:
            values = {
                "item": content["items"],
                "identity": content["identities"],
                "target": content["targets"],
            }[key]
            for template_index, template in enumerate(content[group]):
                for value_index, value in enumerate(values):
                    labels = [primary_label]
                    if group in {"threat", "hate", "harassment"}:
                        labels.append("GENERAL_TOXICITY")
                    if group in {"threat", "hate"} and (template_index + value_index) % 2 == 0:
                        labels.append("HARASSMENT_OR_INSULT")
                    if group == "profane" and template_index >= 2:
                        labels.append("GENERAL_TOXICITY")
                    records.append(
                        _record(
                            sequence,
                            split,
                            template.format(**{key: value}),
                            labels,
                            [*tags, "multi_label" if len(labels) > 1 else "single_label"],
                            f"Synthetic {group} scenario in a customer-support setting.",
                        )
                    )
                    sequence += 1
    return records


def _record(
    sequence: int,
    split: str,
    text: str,
    labels: list[str],
    tags: list[str],
    note: str,
) -> dict[str, object]:
    is_ai = sequence % 10 == 0
    return {
        "dataset_version": DATASET_VERSION,
        "id": f"domain-{sequence:04d}",
        "split": split,
        "text": text,
        "expected_labels": labels,
        "language": "en",
        "speaker_role": "assistant" if is_ai else ("agent" if not labels and sequence % 2 == 0 else "customer"),
        "content_origin": "synthetic_ai" if is_ai else "synthetic_human",
        "slice_tags": tags,
        "review_status": "draft",
        "annotation_notes": note,
    }


def render_jsonl(records: Iterable[dict[str, object]]) -> str:
    return "".join(
        json.dumps(record, ensure_ascii=False, separators=(",", ":")) + "\n"
        for record in records
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument(
        "--check",
        action="store_true",
        help="Fail when the committed dataset differs from deterministic generation.",
    )
    arguments = parser.parse_args()
    rendered = render_jsonl(build_dataset())
    if arguments.check:
        if not arguments.output.exists() or arguments.output.read_text(encoding="utf-8") != rendered:
            print(f"Dataset is missing or stale: {arguments.output}")
            return 1
        print(f"Dataset is reproducible: {arguments.output}")
        return 0
    arguments.output.parent.mkdir(parents=True, exist_ok=True)
    arguments.output.write_text(rendered, encoding="utf-8", newline="\n")
    print(f"Wrote {len(build_dataset())} records to {arguments.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
