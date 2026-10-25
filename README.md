# Mythos

A native iOS app for language learners: a short story every day, in the
language you're learning, at your level. Tap any word for its meaning and
every form it takes: conjugations, declensions, gender and number.

_μῦθος_ is Greek for "story". Mythos is the iPhone companion to
[Paradigma](../paradigma) (_παράδειγμα_, "pattern"), and uses the same
dictionary data and inflection tables.

- **French, Spanish, German, Italian, Portuguese (Brazil) and Greek**, 15
  stories each at **A1** and **A2** (180 in all). B1–C2 are shown as coming soon.
- **A daily notification** in the morning, afternoon or evening, at a time you
  choose. It's scheduled on the phone: no server, no account.
- **Tap a word** to see what it means in the story, its dictionary meanings,
  what form it is (_achète_ → "first/third-person singular present of
  _acheter_"), and its full tables, with the tapped form highlighted.
- **Listen**: the story is read aloud by the phone's own voice, with each word
  highlighted as it's spoken. Pause, resume or stop it, or play it straight
  from the Today card. Words and table forms can be heard too.
- **Translate**: Apple's on-device Translation, for the whole story or a word
  the dictionary doesn't have.
- **Library** of every story, a **Words** list of saved words, and a reading streak.

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

## How it works

| Part | Where | Notes |
| --- | --- | --- |
| Story catalog | `Mythos/Resources/Catalog/catalog-<lang>-<level>.json` | Bundled with the app. Each story has a title, English subtitle, topic, SF Symbol, paragraphs, and a glossary of words _as written in the text_ |
| Daily plan | `Model/DailyPlan.swift` | Deterministic: a course shows its level's stories in order from the day it started, so notifications weeks ahead name the right story. Several languages take turns in notifications |
| Notifications | `Services/NotificationScheduler.swift` | Local `UNCalendarNotificationTrigger`s, 60 days ahead (iOS allows 64), topped up whenever the app becomes active. Tapping one opens its story |
| Dictionary | `Dictionary/` | [kaikki.org](https://kaikki.org) (Wiktionary as JSON): free and keyless. Ported from Paradigma's `kaikki.ts`, `word.ts` and `inflections.ts`. Responses are cached on disk (100 MB), so a word costs one request at most |
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

Add or edit a `catalog-<lang>-<level>.json` file. The tests check that every
level has 15 stories, IDs are unique, every glossary word appears in its story
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
