"""Bundle the authoring skill as text. Spell checking belongs to the game's Codec."""

import argparse
import hashlib
import json
from pathlib import Path


def bundle(skill: Path) -> str:
    parts = [
        "You are authoring a Youkai's Homecoming danmaku spell.\n"
        "The player supplies operation, card_type, custom_request, and current_json.\n"
        "Return one complete JSON object only. All authoring references needed here\n"
        "are included below; this request has no filesystem or shell tools.\n"
        "The host runs the game's checker and may send checker_feedback for repair.\n"
        "Follow that feedback while preserving the player's request and working nodes.\n"
        "The runtime capability report after this guide lists the loaded types.\n"
        "Capability policies describe later use, not an approval gate for this draft.\n"
        "Completed drafts go to chat and the clipboard; they are not automatically saved."
    ]
    sources = ["references/authoring.md", "references/schema.md"]
    parts.append((skill / sources[0]).read_text(encoding="utf-8").strip())
    schema = (skill / sources[1]).read_text(encoding="utf-8")
    # The provider needs the field reference, not local source/storage/command paths.
    parts.append("# Field reference\n\n" + schema.split("## Top-Level Object", 1)[1]
                 .split("## Built-In Starter Templates", 1)[0].strip())
    examples = [
        ("minimal_spell", "Complete single-segment normal card"),
        ("reference_yh_ring", "Rotating ring and color provider"),
        ("reference_yh_mover", "Formula flight curve"),
        ("reference_yh_shooter", "Independent orbiting shooter"),
        ("reference_yh_hit_callbacks", "One-shot delay and callback clocks"),
        ("reference_yh_bounce", "Hit controls, bounce and hold"),
    ]
    parts.append("# Construction examples\n\n"
                 "Adapt the technique to the requested design and card setup. Use fresh IDs.\n"
                 "These examples demonstrate content, not certification or visual approval.")
    for name, title in examples:
        path = f"assets/{name}.json"
        sources.append(path)
        parts.append(f"## {title}\n\n```json\n"
                     + (skill / path).read_text(encoding="utf-8").strip() + "\n```")
    digest = hashlib.sha256()
    for path in sources:
        digest.update(path.encode("utf-8") + b"\0" + (skill / path).read_bytes())
    parts.append(f"<!-- Source: yh-danmakuspell-create; SHA-256: {digest.hexdigest()} -->")
    return "\n\n".join(parts) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("skill_directory", type=Path)
    args = parser.parse_args()
    output = Path(__file__).resolve().parents[1] / (
        "src/main/resources/data/youkaishomecoming/llm/spell_generation_system.txt")
    text = bundle(args.skill_directory)
    output.write_text(text, encoding="utf-8", newline="\n")
    print(json.dumps({"resource": str(output), "bytes": len(text.encode("utf-8")),
                      "sha256": hashlib.sha256(text.encode("utf-8")).hexdigest()}, indent=2))


if __name__ == "__main__":
    main()
