#!/usr/bin/env python3
"""Checks story catalog files before they ship. Run from the repo root:

    scripts/check-catalog.py Mythos/Resources/Catalog/catalog-fr-b1.json [...]
    scripts/check-catalog.py --all

For each file: its name matches its language and level; it has 15 stories
with sequential IDs; every story has a known topic and a real SF Symbol;
its length suits its level; every glossary word appears in the text as
written; and its English translation has one sentence per original
sentence, per paragraph, as the app's SentenceSplitter splits them.
Prints every problem and exits 1 if there are any.
"""
import json, re, subprocess, sys, tempfile
from pathlib import Path

CATALOG = Path("Mythos/Resources/Catalog")
LANGUAGES = {"fr", "es", "de", "it", "pt", "pt-PT", "el"}
LEVELS = ["A1", "A2", "B1", "B2", "C1", "C2"]

# Topics with a cover palette (Theme/StoryCover.swift). Any other topic gets the default.
TOPICS = {
    "Family", "Routine", "Food & drink", "Cooking", "Shopping", "Outdoors", "Learning", "Culture",
    "Celebrations", "Friends", "Home", "Health", "Weather", "Getting around", "Everyday life",
    "Neighbours", "Animals", "Free time", "Sport", "Work", "Plans", "Travel", "Science", "Society",
    "Memory", "Mystery", "Environment", "Nature", "Technology", "Media", "History", "Art", "Ideas",
    "City", "Love", "Money", "Language",
}

# Per level: paragraphs, words in the story, glossary entries (inclusive ranges).
SHAPE = {
    "A1": ((2, 2), (45, 150), (6, 9)),
    "A2": ((2, 3), (60, 210), (7, 10)),
    "B1": ((3, 3), (160, 260), (8, 11)),
    "B2": ((3, 4), (230, 340), (9, 12)),
    "C1": ((4, 4), (290, 420), (10, 13)),
    "C2": ((4, 5), (360, 520), (12, 15)),
}


def segments(file: Path) -> dict:
    """The app's own sentence split, via scripts/segment.sh."""
    out = subprocess.run(["scripts/segment.sh", file.name], capture_output=True, text=True)
    if out.returncode != 0:
        sys.exit(f"segment.sh failed:\n{out.stderr}")
    stories, current = {}, None
    for line in out.stdout.splitlines():
        if line.startswith("## "):
            current = line[3:].strip()
            stories[current] = {}
        elif line.strip():
            p, text = line.split("| ", 1)
            stories[current].setdefault(int(p), []).append(text)
    return {k: [v[p] for p in sorted(v)] for k, v in stories.items()}


def missing_symbols(symbols: set) -> set:
    """SF Symbols that don't exist, checked with AppKit (the same names as on iOS)."""
    script = "import AppKit\nfor name in CommandLine.arguments.dropFirst() where NSImage(systemSymbolName: name, accessibilityDescription: nil) == nil { print(name) }\n"
    with tempfile.NamedTemporaryFile("w", suffix=".swift", delete=False) as f:
        f.write(script)
    out = subprocess.run(["swift", f.name, *sorted(symbols)], capture_output=True, text=True)
    return set(out.stdout.split())


def check(file: Path) -> list:
    problems = []
    def fail(msg):
        problems.append(f"{file.name}: {msg}")

    m = re.fullmatch(r"catalog-(.+)-(a1|a2|b1|b2|c1|c2)\.json", file.name)
    if not m:
        return [f"{file.name}: name must be catalog-<language>-<level>.json"]
    lang, level = m.group(1), m.group(2).upper()
    try:
        data = json.loads(file.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        return [f"{file.name}: invalid JSON: {e}"]
    if lang not in LANGUAGES:
        fail(f"unknown language {lang!r}")
    if data.get("language") != lang or data.get("level") != level:
        fail(f"language/level inside ({data.get('language')}/{data.get('level')}) don't match the file name")

    stories = data.get("stories", [])
    if len(stories) != 15:
        fail(f"has {len(stories)} stories, not 15")
    (pmin, pmax), (wmin, wmax), (vmin, vmax) = SHAPE[level]
    seen_titles = set()
    split = segments(file)
    symbols = set()

    for i, s in enumerate(stories, 1):
        sid = s.get("id", f"#{i}")
        expected = f"{lang}-{level.lower()}-{i:02d}"
        if sid != expected:
            fail(f"story {i} has id {sid!r}, expected {expected!r}")
        for key in ["title", "subtitle", "topic", "symbol", "paragraphs", "english", "vocab"]:
            if not s.get(key):
                fail(f"{sid}: missing {key}")
        if not all(s.get(k) for k in ["paragraphs", "english", "vocab"]):
            continue
        if s["title"] in seen_titles:
            fail(f"{sid}: title {s['title']!r} used twice")
        seen_titles.add(s["title"])
        if s["topic"] not in TOPICS:
            fail(f"{sid}: topic {s['topic']!r} isn't one of: {', '.join(sorted(TOPICS))}")
        symbols.add(s["symbol"])

        paragraphs = s["paragraphs"]
        text = " ".join(paragraphs)
        words = len(text.split())
        if not pmin <= len(paragraphs) <= pmax:
            fail(f"{sid}: {len(paragraphs)} paragraphs; {level} needs {pmin}–{pmax}")
        if not wmin <= words <= wmax:
            fail(f"{sid}: {words} words; {level} needs {wmin}–{wmax}")

        vocab = s["vocab"]
        if not vmin <= len(vocab) <= vmax:
            fail(f"{sid}: {len(vocab)} glossary entries; {level} needs {vmin}–{vmax}")
        lower = text.lower()
        for v in vocab:
            if not v.get("word") or not v.get("gloss"):
                fail(f"{sid}: glossary entry missing word or gloss: {v}")
            elif v["word"].lower() not in lower:
                fail(f"{sid}: glossary word {v['word']!r} isn't in the text as written")
        if len({v.get('word', '').lower() for v in vocab}) != len(vocab):
            fail(f"{sid}: a glossary word is listed twice")

        english = s["english"]
        original = split.get(sid, [])
        if [len(p) for p in original] != [len(p) for p in english]:
            fail(f"{sid}: sentences per paragraph {[len(p) for p in original]}, translated {[len(p) for p in english]}. The app splits it as:\n"
                 + "\n".join(f"      {n + 1}| {t}" for n, para in enumerate(original) for t in para))
        if any(not t.strip() for para in english for t in para):
            fail(f"{sid}: an English sentence is empty")

    for name in sorted(missing_symbols(symbols)):
        fail(f"SF Symbol {name!r} doesn't exist")
    return problems


files = sorted(CATALOG.glob("catalog-*.json")) if sys.argv[1:] == ["--all"] else [Path(a) for a in sys.argv[1:]]
if not files:
    sys.exit(__doc__)
problems = [p for f in files for p in check(f)]
print("\n".join(problems) if problems else f"OK: {len(files)} file(s), {15 * len(files)} stories")
sys.exit(1 if problems else 0)
