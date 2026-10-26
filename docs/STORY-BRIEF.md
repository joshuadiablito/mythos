# Writing a Mythos story catalog

Mythos sends a learner one short story a day, in the language they're
learning, at their CEFR level. This is the brief for writing a level's
catalog: 15 stories in one JSON file.

## The file

`Mythos/Resources/Catalog/catalog-<language>-<level>.json`, for example
`catalog-fr-b1.json` or `catalog-pt-PT-a1.json`. Copy the shape of an
existing file such as `catalog-fr-a2.json` exactly:

```json
{
  "language": "fr",
  "level": "B1",
  "stories": [
    {
      "id": "fr-b1-01",
      "title": "Title in the language",
      "subtitle": "The title in English",
      "topic": "Travel",
      "symbol": "airplane",
      "paragraphs": ["First paragraph…", "Second paragraph…", "Third…"],
      "english": [["Sentence one.", "Sentence two."], ["…"], ["…"]],
      "vocab": [ { "word": "as it appears in the text", "gloss": "its meaning here" } ]
    }
  ]
}
```

- `id`: `<language>-<level in lower case>-NN`, numbered 01 to 15 in order.
- `title` in the language; `subtitle` is that title in English.
- `topic`: one of Family, Routine, Food & drink, Cooking, Shopping, Outdoors,
  Learning, Culture, Celebrations, Friends, Home, Health, Weather,
  Getting around, Everyday life, Neighbours, Animals, Free time, Sport, Work,
  Plans, Travel, Science, Society, Memory, Mystery, Environment, Nature,
  Technology, Media, History, Art, Ideas, City, Love, Money, Language. The
  topic picks the cover's colours.
- `symbol`: an SF Symbol name for the topic, such as `airplane`,
  `leaf.fill`, `building.columns.fill`, `paintpalette.fill`, `cpu`,
  `newspaper.fill`, `music.note`, `theatermasks.fill`, `globe.europe.africa.fill`,
  `hourglass`, `moon.stars.fill`, `camera.fill`, `book.fill`, `heart.fill`,
  `fork.knife`, `tram.fill`, `ferry.fill`, `mountain.2.fill`, `tree.fill`,
  `flame.fill`, `envelope.fill`, `briefcase.fill`, `cross.case.fill`,
  `lightbulb.fill`, `scroll.fill`, `key.fill`, `eurosign.circle.fill`,
  `film.fill`, `figure.run`, `bicycle`, `wineglass.fill`, `sailboat.fill`,
  `binoculars.fill`, `graduationcap.fill`, `person.3.fill`, `house.fill`,
  `cloud.rain.fill`, `sun.max.fill`, `pawprint.fill`, `fish.fill`. The
  validator checks that each name exists.
- `paragraphs`: the story.
- `english`: the translation. One array per paragraph, and in each, one
  English sentence for each sentence of that paragraph, in order, exactly as
  the app splits it (see below). Natural English, but close enough that a
  learner can match it phrase by phrase.
- `vocab`: the story's new words for its level. Each `word` must appear in
  the text exactly as written (inflected, case aside): `achète`, not
  `acheter`. A short phrase is fine (`il a fallu`). `gloss` is its meaning
  in this story, in English.

## Levels

| Level | Paragraphs | Words | Glossary | What it stretches |
| --- | --- | --- | --- | --- |
| A1 | 2 | 45–150 (aim 90–120) | 6–9 | Present tense, everyday words, short sentences |
| A2 | 2–3 | 60–210 (aim 120–170) | 7–10 | Past tenses, routines, small events |
| B1 | 3 | 160–260 | 8–11 | Narrative past, future, conditional, opinions, dialogue |
| B2 | 3–4 | 230–340 | 9–12 | Subjunctive, passive, reported speech, argument, nuance |
| C1 | 4 | 290–420 | 10–13 | Idiom, register, complex syntax, feature writing |
| C2 | 4–5 | 360–520 | 12–15 | Literary style, irony, rare words, wordplay |

Vary the form as the level rises: B1 mostly stories with dialogue; B2 adds
letters, interviews, opinion pieces; C1 features, essays and longer short
fiction; C2 literary short fiction, satirical columns, essays and prose
poems. Give each story a real arc or point: a turn, a reveal, an ending
that lands. Fifteen different topics and settings per level; avoid reusing
the A1/A2 plots (market, café, birthday, lost key…).

**Everything must be original writing.** Write new stories in the style a
level calls for; never reproduce, translate or closely paraphrase existing
literature, songs, articles or other copyrighted text, even at C2. Real
places, foods, festivals and public figures from history are fine as
setting.

Set stories where the language is spoken, with local names, places,
food and habits, and use that variety's spelling and grammar.

## How the app splits sentences

The English must line up with this, so write with it in mind:

- A sentence ends at `.` `!` `?` `…` (in Greek also `;`, the question mark),
  plus any closing quotation marks, when the next character starts a
  sentence: a capital letter, an opening quote, or a dash followed by a
  capital.
- Nothing inside quotation marks is split: `« Bonjour. Ça va ? »` is one
  sentence, and so is `Il dit : « Viens. »`. Use «», „“ (German) or “”.
- A dash followed by a lower-case word continues the sentence:
  `— Quanto é? — pergunta ela.` is one.
- So avoid abbreviations with a full stop before a capital (`Sr. Silva`,
  `Dr. Weber`, `St. Paul`), and initials (`J. Martin`): write the word out.

To see exactly how a file splits:

```sh
scripts/segment.sh catalog-fr-b1.json
```

## Checking

```sh
scripts/check-catalog.py Mythos/Resources/Catalog/catalog-fr-b1.json
```

Fix everything it reports, and run it again until it prints `OK`. It
checks the shape, lengths, topics, symbols, glossary words and the
sentence-by-sentence alignment, and when a translation doesn't line up it
prints the app's split of that story.
