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

def parse(lines, name):
    stories = {}
    current = None
    for number, line in enumerate(lines, 1):
        line = line.rstrip("\n")
        if line.startswith("## "):
            current = line[3:].strip()
            stories[current] = defaultdict(list)
        elif line.strip():
            p, sep, text = line.partition("| ")
            if current is None or not sep or not p.strip().isdigit():
                sys.exit(f"{name}:{number}: expected '## <story id>' or '<paragraph>| <sentence>', got {line!r}")
            stories[current][int(p)].append(text.strip())
    return {k: [v[p] for p in sorted(v)] for k, v in stories.items()}

def value(key, v):
    """One story field, laid out as the catalog files have it."""
    j = lambda x: json.dumps(x, ensure_ascii=False)
    if key == "paragraphs":
        return "[\n" + ",\n".join(f"        {j(p)}" for p in v) + "\n      ]"
    if key == "english":
        return "[\n" + ",\n".join("        [\n" + ",\n".join(f"          {j(t)}" for t in para) + "\n        ]" for para in v) + "\n      ]"
    if key == "vocab":
        return "[\n" + ",\n".join(f'        {{ "word": {j(i["word"])}, "gloss": {j(i["gloss"])} }}' for i in v) + "\n      ]"
    return j(v)

def dump(catalog):
    """Writes the catalog in its usual shape: one glossary item per line.
    Every key is kept, so fields this script doesn't know about survive."""
    out = ["{"]
    for key, v in catalog.items():
        if key != "stories":
            out.append(f"  {json.dumps(key)}: {json.dumps(v, ensure_ascii=False)},")
    out.append('  "stories": [')
    for i, s in enumerate(catalog["stories"]):
        keys = [k for k in s if k != "english"]
        if "english" in s:
            # A new translation goes after the paragraphs it translates.
            keys.insert(keys.index("paragraphs") + 1 if "paragraphs" in keys else len(keys), "english")
        fields = [f"      {json.dumps(k)}: {value(k, s[k])}" for k in keys]
        out.append("    {\n" + ",\n".join(fields) + "\n    }" + ("," if i < len(catalog["stories"]) - 1 else ""))
    out += ["  ]", "}", ""]
    return "\n".join(out)

segments = parse(subprocess.run(["scripts/segment.sh"], capture_output=True, text=True, check=True).stdout.splitlines(), "segment.sh")
translations = {}
for path in sys.argv[1:]:
    with open(path, encoding="utf-8") as f:
        translations.update(parse(f, path))

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
