# How Mythos works

Diagrams of the app's structure and its main flows. For the feature list and
how to run it, see the [README](../README.md).

## The big picture

Everything runs on the phone. The only network calls go to kaikki.org for
dictionary lookups, and those are cached on disk.

```mermaid
flowchart TB
    subgraph Bundle["App bundle"]
        JSON["catalog-&lt;lang&gt;-&lt;level&gt;.json<br/>(12 files, 15 stories each)"]
    end

    subgraph App["MythosApp"]
        Root["RootView<br/>(TabView + onboarding cover)"]
        Router["Router.shared<br/>selectedTab, pendingStoryID"]
    end

    subgraph Model
        Catalog["Catalog.shared"]
        Plan["DailyPlan"]
        Settings["AppSettings<br/>(UserDefaults)"]
        Progress["ProgressStore<br/>(UserDefaults)"]
    end

    subgraph Services
        Notif["NotificationScheduler"]
        Speaker["Speaker.shared<br/>(AVSpeechSynthesizer / AVPlayer)"]
    end

    subgraph Dictionary
        Client["DictionaryClient.shared"]
        Cache["DictionaryCache<br/>(Application Support)"]
        Parser["KaikkiParser"]
        Infl["InflectionBuilder"]
    end

    Kaikki[("kaikki.org")]
    iOSNotif[["UNUserNotificationCenter"]]
    Translate[["Translation framework<br/>(on device)"]]

    JSON --> Catalog
    Catalog --> Plan
    Settings --> Plan
    Plan --> Notif
    Notif --> iOSNotif
    iOSNotif -- "tap" --> Router
    Router --> Root
    Root --> Views["Views"]
    Views --> Plan
    Views --> Progress
    Views --> Speaker
    Views --> Client
    Views --> Translate
    Client --> Cache
    Client --> Kaikki
    Client --> Parser
    Views --> Infl
```

`MythosApp` creates `AppSettings` and `ProgressStore` and passes them, with
`Router.shared` and `Speaker.shared`, down through the SwiftUI environment.

## Source layout

```mermaid
flowchart LR
    subgraph Mythos
        AppDir["App/<br/>MythosApp, Router, AppDelegate"]
        ModelDir["Model/<br/>Catalog, Story, DailyPlan,<br/>AppSettings, ProgressStore,<br/>Language, Level, SentenceSplitter"]
        DictDir["Dictionary/<br/>DictionaryClient, DictionaryCache,<br/>KaikkiParser, LookupCandidates,<br/>Entry, Inflections"]
        SvcDir["Services/<br/>NotificationScheduler, Speaker"]
        ViewsDir["Views/<br/>Today, Library, Words, Settings,<br/>Reader, Lookup, Onboarding, Components"]
        ThemeDir["Theme/"]
        Res["Resources/Catalog/*.json"]
    end
    Tests["MythosTests/<br/>Catalog, Dictionary, DictionaryCache<br/>+ kaikki fixtures"]
    Scripts["scripts/<br/>segment.sh/.swift, merge-translations.py,<br/>render-icon.swift"]

    ViewsDir --> ModelDir & DictDir & SvcDir & ThemeDir
    SvcDir --> ModelDir
    ModelDir --> Res
    Scripts --> Res
    Scripts -. "compiles with" .-> ModelDir
    Tests --> ModelDir & DictDir
```

## Screens

```mermaid
flowchart TD
    Launch(["Launch"]) --> Onboarded{"settings.hasOnboarded?"}
    Onboarded -- no --> Onboarding["OnboardingView<br/>pick languages, levels, time"]
    Onboarding --> Tabs
    Onboarded -- yes --> Tabs

    subgraph Tabs["RootView TabView"]
        Today["TodayView<br/>today's story per course, streak"]
        Library["LibraryView<br/>every story"]
        Words["WordsView<br/>saved words"]
        SettingsV["SettingsView<br/>courses, time, speech, cache"]
    end

    Today --> Reader["StoryReaderView"]
    Library --> Reader
    Reader -- "tap a word<br/>(TappableParagraph)" --> Lookup["LookupSheet"]
    Reader -- "vocabulary list" --> Lookup
    Words --> Lookup
    Lookup --> Forms["FormsView<br/>inflection tables"]
    Reader -- "Translate" --> TF[["Translation sheet"]]
    Lookup -- "no entry" --> TF
```

## Which story is today's

`DailyPlan` is a pure function of the catalog, the courses and the date, so a
notification scheduled weeks ahead names the same story the app shows on that
day.

```mermaid
flowchart LR
    Course["Course<br/>language, level, startedOn"] --> Days["days since startedOn"]
    Days --> Mod["index = days mod stories.count"]
    Cat["catalog.stories(language, level)"] --> Mod
    Mod --> Story["story(for:on:)"]

    Courses["all courses with stories"] --> Turn["course = courses[dayNumber mod count]"]
    Turn --> Featured["featured(in:on:)<br/>(the notification's story)"]
    Story --> Featured
```

`Today` shows `story(for:on:)` for every course. Notifications use
`featured(in:on:)`, so with several languages they take turns day by day.

## Daily notifications

```mermaid
sequenceDiagram
    autonumber
    participant App as MythosApp
    participant NS as NotificationScheduler
    participant Plan as DailyPlan
    participant UN as UNUserNotificationCenter
    participant AD as AppDelegate
    participant R as Router
    participant Today as TodayView

    App->>NS: scenePhase == .active → reschedule(settings)
    NS->>UN: remove pending "daily-*" requests
    alt notifications enabled and authorized
        loop next 60 days (iOS allows 64)
            NS->>Plan: featured(in: courses, on: day)
            Plan-->>NS: Story
            NS->>UN: add(calendar trigger at hour:minute,<br/>userInfo.storyID)
        end
    end

    Note over UN: Days later, at the chosen time
    UN-->>AD: didReceive(response)
    AD->>R: (main thread) selectedTab = .today<br/>pendingStoryID = id
    AD-->>UN: completionHandler()
    R-->>Today: observes pendingStoryID
    Today->>Today: push StoryReaderView
```

The Today banner asks for permission when it hasn't been asked yet
(`requestAuthorization`), or links to iOS Settings when it was refused.

## Looking a word up

```mermaid
sequenceDiagram
    autonumber
    participant Sheet as LookupSheet
    participant DC as DictionaryClient
    participant LC as LookupCandidates
    participant Cache as DictionaryCache
    participant K as kaikki.org
    participant P as KaikkiParser
    participant IB as InflectionBuilder

    Sheet->>DC: lookUp("Achète", in: .french)
    DC->>DC: in-memory result? → return it
    DC->>LC: candidates("Achète")
    LC-->>DC: ["Achète", "achète", …]<br/>(plus un-elided l'/d'/qu' forms)
    loop each candidate until one has entries
        DC->>Cache: read(url)
        alt .found(data)
            Cache-->>DC: JSONL
        else .notFound (under 30 days old)
            Cache-->>DC: no such word
        else miss
            DC->>K: GET
            alt 200
                K-->>DC: JSONL
                DC->>Cache: store(.found)
            else 404
                DC->>Cache: store(.notFound)
            else other
                DC-->>Sheet: throw DictionaryError.unavailable
            end
        end
        DC->>P: parse (off the main actor)
        P-->>DC: [Entry]
    end
    par up to 3 lemmas ("achète" → "acheter")
        DC->>DC: fetch(lemma) via the same cache path
    end
    DC-->>Sheet: LookupResult(entries, lemmas)
    Sheet->>IB: build(entry, language)
    IB-->>Sheet: Inflections → FormsView<br/>(tapped form highlighted)
```

The disk cache trims itself least recently used first, down to its budget
(`maxBytes`, 80 MB by default), in a background task when the client starts.

## Reading aloud

```mermaid
sequenceDiagram
    participant Reader as StoryReaderView
    participant S as Speaker
    participant Synth as AVSpeechSynthesizer
    participant TP as TappableParagraph

    Reader->>S: speak(story.text, language, rate, id: story.id)
    S->>Synth: speak(utterance in the language's voice)
    loop each word
        Synth-->>S: willSpeakRangeOfSpeechString(range)
        S-->>Reader: spokenRange (observed)
        Reader->>TP: range mapped into that paragraph
        TP->>TP: highlight the word
    end
    Synth-->>S: didFinish → speakingID = nil
```

`story.text` joins paragraphs with a blank line (two UTF-16 units), which is
how the reader maps the spoken range back to a paragraph. Single words and
table forms go through `Speaker.play(url, fallback:)`, which plays the
Wiktionary recording when there is one.

## Catalog content and translations

```mermaid
flowchart LR
    JSON["catalog-*.json"] -- "scripts/segment.sh [fr-a1]" --> Seg["sentences, one per line<br/>## fr-a1-01<br/>1| Bonjour !"]
    Splitter["Model/SentenceSplitter.swift"] -. "concatenated into" .-> Seg
    Seg --> Write["write English, line for line<br/>translations/fr.txt"]
    Write -- "scripts/merge-translations.py" --> Check{"sentence counts match<br/>per paragraph?"}
    Check -- yes --> JSON2["catalog-*.json<br/>with &quot;english&quot;: [[…]]"]
    Check -- no --> Err["error"]
    JSON2 --> Tests["CatalogTests<br/>15 per level, unique IDs,<br/>glossary words in text,<br/>SF Symbols exist"]
```

The scripts use the app's own `SentenceSplitter`, so the English sentences
line up with the sentences the app shows them beside: `TranslationSection`, in
the reader under the story, pairs each sentence with its English via
`Story.sentencePairs`.

## State that persists

```mermaid
classDiagram
    class AppSettings {
        courses: [Course]
        hour, minute
        notificationsEnabled
        hasOnboarded
        speechRate
        slot: DeliverySlot
    }
    class Course {
        language: Language
        level: Level
        startedOn: Date
    }
    class ProgressStore {
        read: [storyID: Date]
        savedWords: [SavedWord]
        streak(asOf)
    }
    class SavedWord {
        word, language
        meaning?
        storyTitle?
    }
    class DictionaryCache {
        sha256(url).jsonl
        sha256(url).404
        trim()
    }
    AppSettings "1" *-- "*" Course
    ProgressStore "1" *-- "*" SavedWord
    note for AppSettings "UserDefaults"
    note for ProgressStore "UserDefaults"
    note for DictionaryCache "Application Support/DictionaryCache"
```
