#!/usr/bin/env python3
"""Merges English translations into the story catalog.

Translation files hold, for each story, one English sentence per original
sentence, prefixed with its paragraph number, in the order scripts/segment.sh
prints them:

    ## fr-a1-01
    1| Hello!
    1| My name is Léa.

Every story's sentence count, per paragraph, must match the app's sentence
split exactly, so each translation sits under the right sentence.

    scripts/merge-translations.py translations/fr.txt [more.txt ...]
"""
import json, subprocess, sys
from collections import defaultdict
from pathlib import Path

CATALOG = Path("Mythos/Resources/Catalog")

def parse(lines):
    stories = {}
    current = None
    for line in lines:
        line = line.rstrip("\n")
        if line.startswith("## "):
            current = line[3:].strip()
            stories[current] = defaultdict(list)
        elif line.strip():
            p, text = line.split("| ", 1)
            stories[current][int(p)].append(text.strip())
    return {k: [v[p] for p in sorted(v)] for k, v in stories.items()}

def dump(catalog):
    """Writes the catalog in its usual shape: one glossary item per line."""
    out = ['{', f'  "language": {json.dumps(catalog["language"])},', f'  "level": {json.dumps(catalog["level"])},', '  "stories": [']
    for i, s in enumerate(catalog["stories"]):
        out.append("    {")
        for key in ["id", "title", "subtitle", "topic", "symbol"]:
            out.append(f'      "{key}": {json.dumps(s[key], ensure_ascii=False)},')
        out.append('      "paragraphs": [')
        out.append(",\n".join(f"        {json.dumps(p, ensure_ascii=False)}" for p in s["paragraphs"]))
        out.append("      ],")
        if "english" in s:
            out.append('      "english": [')
            out.append(",\n".join("        [\n" + ",\n".join(f"          {json.dumps(t, ensure_ascii=False)}" for t in para) + "\n        ]"
                                  for para in s["english"]))
            out.append("      ],")
        out.append('      "vocab": [')
        out.append(",\n".join(f'        {{ "word": {json.dumps(v["word"], ensure_ascii=False)}, "gloss": {json.dumps(v["gloss"], ensure_ascii=False)} }}'
                              for v in s["vocab"]))
        out.append("      ]")
        out.append("    }" + ("," if i < len(catalog["stories"]) - 1 else ""))
    out += ["  ]", "}", ""]
    return "\n".join(out)

segments = parse(subprocess.run(["scripts/segment.sh"], capture_output=True, text=True, check=True).stdout.splitlines())
translations = {}
for path in sys.argv[1:]:
    translations.update(parse(open(path, encoding="utf-8")))

errors = []
for story_id, english in translations.items():
    original = segments.get(story_id)
    if original is None:
        errors.append(f"{story_id}: no such story")
    elif [len(p) for p in original] != [len(p) for p in english]:
        errors.append(f"{story_id}: sentences per paragraph {[len(p) for p in original]}, translated {[len(p) for p in english]}")
if errors:
    sys.exit("\n".join(errors))

for file in sorted(CATALOG.glob("catalog-*.json")):
    catalog = json.loads(file.read_text(encoding="utf-8"))
    changed = False
    for story in catalog["stories"]:
        if story["id"] in translations:
            story["english"] = translations[story["id"]]
            changed = True
    if changed:
        file.write_text(dump(catalog), encoding="utf-8")
        print(f"Updated {file.name}")
