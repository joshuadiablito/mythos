# Mythos

A native iOS app for language learners: a short story every day, in the
language you're learning, at your level. Tap any word for its meaning and
every form it takes: conjugations, declensions, gender and number.

_μῦθος_ is Greek for "story". Mythos is the iPhone companion to
[Paradigma](../paradigma) (_παράδειγμα_, "pattern"), and uses the same
dictionary data and inflection tables.

- **French, Spanish, German, Italian, Brazilian Portuguese, European
  Portuguese and Greek**, 15 stories at every CEFR level from **A1** to
  **C2** (630 in all). Brazilian and European Portuguese are separate
  languages, with their own stories, spelling and voices.
- **A daily notification** for each language, at a time you choose for that
  language (Spanish at 7:00, Greek at 8:00). It's scheduled on the phone: no
  server, no account.
- **Tap a word** to see what it means in the story, who is doing it in English
  (_achète_ → "il/elle achète → he/she buys"), its dictionary meanings, and
  its full tables with the English under every form ("nous mangeons · we
  eat"), the tapped form highlighted.
- **Listen**: the story is read aloud by the phone's own voice, with each word
  highlighted as it's spoken. Pause, resume or stop it, or play it straight
  from the Today card. Words and table forms can be heard too.
- **English translation** under every story, sentence by sentence, bundled
  with the app. Tap a sentence to hear it; fold the section away to try the
  story on your own first.
- **Translate**: Apple's on-device Translation, for a word the dictionary
  doesn't have.
- **One story a day.** Stories aren't browsable in advance: **Stories** shows
  only the ones that have arrived, each with its own generated cover, and
  what's still to come stays locked.
- **Words** you save, reviewed as flipping **flashcards**, a reading streak
  with the week at a glance, and a "story complete" moment that offers to save
  the story's new words.

## Running it

Requires Xcode 26 and iOS 26 (it uses Liquid Glass).

```sh
open Mythos.xcodeproj        # then run the Mythos scheme on an iPhone simulator
```

From the command line:

```sh
xcodebuild test -project Mythos.xcodeproj -scheme Mythos \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro'
```

The project uses folder-synchronised groups, so new files under `Mythos/` or
`MythosTests/` are picked up without editing the project file.

### Debug launch arguments

For screenshots and quick checks, Debug builds accept:

| Argument | Effect |
| --- | --- |
| `-openStory fr-a1-04` | Opens a story, as tapping its notification would |
| `-lookupWord achète` | Then opens that word's lookup |
| `-lookupExpanded YES` | Opens the lookup sheet at full height |
| `-autoplay YES` | Starts reading the story aloud |
| `-scrollAnchor 0.55` | Opens the reader scrolled that far down |

## How it works

| Part | Where | Notes |
| --- | --- | --- |
| Story catalog | `Mythos/Resources/Catalog/catalog-<lang>-<level>.json` | Bundled with the app. Each story has a title, English subtitle, topic, SF Symbol, paragraphs, and a glossary of words _as written in the text_ |
| Daily plan | `Model/DailyPlan.swift` | Deterministic: a course shows its level's stories in order from the day it started, so notifications weeks ahead name the right story. Several languages take turns in notifications |
| Notifications | `Services/NotificationScheduler.swift` | Local `UNCalendarNotificationTrigger`s, 60 days ahead (iOS allows 64), topped up whenever the app becomes active. Tapping one opens its story |
| Dictionary | `Dictionary/` | [kaikki.org](https://kaikki.org) (Wiktionary as JSON): free and keyless. Ported from Paradigma's `kaikki.ts`, `word.ts` and `inflections.ts`. Responses are cached on disk (80 MB, not backed up), so a word costs one request at most |
| Speech | `Services/Speaker.swift` | `AVSpeechSynthesizer` with the language's voice; reports the word being spoken for highlighting. Wiktionary recordings play when there are some |
| Translation | `Translation` framework | On device, no network |
| Settings, progress | `Model/AppSettings.swift`, `Model/ProgressStore.swift` | `UserDefaults` |

### Design

One cool accent (Aegean blue, `#2E5BD8` / `#7EA2FF` in dark mode) for
actions, one warm one (apricot) for vocabulary, on a warm paper background
with soft glows that Liquid Glass picks up. Glass is used for controls and
bars, as iOS does; content sits on solid cards. Stories are set in a serif.
See `Theme/Theme.swift`. The icon is rendered by `scripts/render-icon.swift`.

### Adding stories

Add or edit a `catalog-<lang>-<level>.json` file. Each story's `english` holds
one English sentence per original sentence, per paragraph, split as
`Model/SentenceSplitter.swift` splits them (it keeps quoted dialogue and dash
speech tags together, and treats Greek `;` as a question mark). To translate a
story, run `scripts/segment.sh fr-a1` to see its sentences, write the English
in the same shape, and merge it with `scripts/merge-translations.py`, which
refuses translations that don't line up. The tests check that every
story's translation lines up with its sentences, every level has 15 stories, IDs are unique, every glossary word appears in its story
exactly as written, and every SF Symbol exists. To open a new level, also mark
it available in `Model/Level.swift`.

## Known limitations

- Wiktionary doesn't have a page for every inflected form, especially in Greek
  (_έφτιαχνε_). Those words show "Not in the dictionary" and offer on-device
  translation; their lemma can still be looked up from the glossary.
- Spanish tables show Spain's _tú_ and _vosotros_.
- Voice quality depends on the voices installed on the phone (Settings →
  Accessibility → Spoken Content → Voices). Enhanced voices sound much better.

Dictionary content is from Wiktionary via kaikki.org, CC BY-SA 4.0, and is
credited in every lookup.
