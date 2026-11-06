# App Store listing

Everything App Store Connect asks for, ready to paste. Character counts are
within Apple's limits. Primary language: **English (U.K.)**.

## App Information

| Field | Value |
| --- | --- |
| Name (30) | `Mythos: Language stories` |
| Subtitle (30) | `Daily stories in 7 languages` |
| Bundle ID | `com.joshuadiablito.mythos` |
| Primary category | Education |
| Secondary category | Reference |
| Content rights | No, it doesn't contain, show or access third-party content that needs rights. The dictionary is CC BY-SA and credited in the app. |

## Version 1.1

### What's New in This Version (4000)

```
Learn a language at more than one level at once. Read Spanish A1 and A2 side by side, or A2 and B1: each level brings its own story every day, at its own time. Add a level in Settings → Add a language or level.

Flashcards now show who is doing the action: "él/ella se despierta → he/she wakes up", not just "wakes up".
```

The fields below carry over from 1.0 unless you change them.

### Promotional text (170)

```
A new story every day in the language you're learning. No account, no ads, no tracking and nothing to pay: your progress never leaves your phone.
```

### Description (4000)

```
Learn a language one short story at a time, privately.

Mythos has no account, no ads, no analytics and no subscription, and there is no Mythos server. The stories, translations, read-aloud and your progress all live on your phone.

Every day Mythos gives you a new story, written for your level, in the language you're learning. It takes a minute or two to read. Tap any word you don't know and Mythos tells you what it means in the story, who is doing it, and every form it takes.

SEVEN LANGUAGES, SIX LEVELS
French, Spanish, German, Italian, Brazilian Portuguese, European Portuguese and Greek, from beginner (A1) to proficient (C2). There are 630 original stories, 15 at every level of every language, and they're all in the app, so reading works offline.

TAP ANY WORD
• Its meaning in this story, and its dictionary meanings
• Who is doing it: "il/elle achète → he/she buys"
• Full conjugation and declension tables, with the English under every form
• Hear it pronounced

LISTEN AS YOU READ
The story is read aloud with each word highlighted as it's spoken. Slow it down while you're starting out.

AN ENGLISH TRANSLATION, SENTENCE BY SENTENCE
Fold it away to try the story on your own first, then check how you did.

A STORY A DAY
Choose a time for each language and Mythos lets you know when its story arrives. Keep a reading streak and see your week at a glance.

SAVE WORDS, REVIEW THEM AS FLASHCARDS
Save new words from any story and review them later, turning each card over to see who's doing it and what it means.

WHAT MYTHOS DOESN'T DO
• No account or sign-up
• No ads, analytics or tracking
• No subscriptions or in-app purchases
• No Mythos server. The only thing that leaves your phone is a word you look up, sent to the free Wiktionary dictionary at kaikki.org.

Dictionary data comes from Wiktionary through kaikki.org (CC BY-SA 4.0). Looking a word up needs an internet connection the first time.
```

### Keywords (100)

```
french,spanish,german,italian,portuguese,greek,vocabulary,reading,flashcards,conjugation,cefr,learn
```

Words already in the name and subtitle (language, stories, daily) count
anyway, so they're left out. So is "free": Apple rejects prices in the name,
subtitle, keywords and screenshots (guideline 2.3.7). The description and
promotional text can say it.

### Pricing and availability

Price: **Free** (USD 0.00), all countries and regions. No in-app purchases,
so leave that section empty.

### URLs

| Field | Value |
| --- | --- |
| Support URL | `https://joshuadiablito.github.io/mythos/support.html` |
| Marketing URL (optional) | `https://joshuadiablito.github.io/mythos/` |
| Privacy Policy URL | `https://joshuadiablito.github.io/mythos/privacy.html` |

These need GitHub Pages turned on: repo **Settings → Pages → Deploy from a
branch → `main`, `/docs`**. The pages are `docs/index.html`,
`docs/support.html` and `docs/privacy.html`.

### Copyright

```
2026 Joshua Anderson
```

### Screenshots

Two sets in `screenshots/`, the same seven screens in upload order:

| Folder | Size | App Store Connect slot |
| --- | --- | --- |
| `6.9-inch/` | 1320 × 2868 | iPhone 6.9" display |
| `6.3-inch/` | 1206 × 2622 | iPhone 6.1" or 6.3" display |

1. `01-lookup.jpg`: tapping *achète* in a French A1 story
2. `02-today.jpg`: today's stories and the reading streak
3. `03-listen.jpg`: a Greek story read aloud, dark mode
4. `04-lookup-full.jpg`: meanings and conjugation tables
5. `05-cards.jpg`: a flashcard showing who is doing it
6. `06-library.jpg`: the stories that have arrived
7. `07-onboarding.jpg`: the welcome screen

The app is iPhone-only, so there are no iPad screenshots.

To retake them, run the Debug build on the iPhone 17 Pro Max (6.9") or
iPhone 17 Pro (6.3") simulator with the launch arguments in the README,
plus `-notificationsAllowed YES` to hide the notification banner.

### Header and search-result artwork

In `artwork/`, rendered from `artwork.html` by `artwork/render.sh`:

| File | Size | Slot |
| --- | --- | --- |
| `header-3840x1646.jpg` | 3840 × 1646 | Header |
| `search-3840x2560.jpg` | 3840 × 2560 | Search results |

## Age rating

Answer **None** or **No** to every question: no violence, sexual content,
profanity, drugs, gambling, horror, medical content, user-generated content,
messaging, advertising, web access or parental controls. The result is
**4+**.

## App Privacy

**Data Not Collected.** The listing then shows Apple's "Data Not Collected"
label, the strongest sign of the app's privacy. Mythos has no account, analytics, ads or server.
Settings and progress stay on the device. Looking a word up fetches that
word's public dictionary page from kaikki.org, with nothing that identifies
the user, so it doesn't count as collection under Apple's definitions.

## App Review

| Field | Value |
| --- | --- |
| Sign-in required | No |
| Contact | Your name, phone and email (only Apple sees these) |

### Notes

```
1. SCREEN RECORDING
Attached: recorded on an iPhone running the latest iOS, starting from launching the app and following a typical day: onboarding, today's story, looking up words, listening, saving words, flashcards and settings. Mythos has no account registration or login, no user-generated content and no paid content, so none of those flows exist.

2. PURPOSE AND AUDIENCE
Mythos is for adults and teenagers learning French, Spanish, German, Italian, Portuguese (Brazil and Portugal) or Greek, from beginner (CEFR A1) to advanced (C2). Learners need reading material at their level, and most texts are either too hard or too dull. Mythos delivers one short story a day at the learner's chosen level, with a reminder at a time they pick. Any word can be tapped for its meaning and conjugation or declension tables, the story can be read aloud, every sentence has an English translation, and saved words become flashcards. I use it myself every day for Spanish and Greek at A1.

3. SETUP AND MAIN FEATURES
No login, credentials or sample files are needed. Everything is stored on the device.
1. Launch the app, choose a language and level in onboarding (Spanish A1 is a good start) and allow notifications.
2. Today shows the day's story. Tap "Start reading", then tap any underlined word for its meaning and tables. The play button reads the story aloud.
3. Tap the bookmark in a word's sheet to save it, then open Words > "Review as flashcards".
4. Settings: add another language, or another level of the same language, and set each one's reminder time.
Stories arrive one per day, so at first only one story per course is available. Adding courses in Settings gives more stories to try.

4. EXTERNAL SERVICES
- kaikki.org: a free, public, keyless dictionary dataset built from Wiktionary. When the user taps a word, the app requests that word's entry. Responses are cached on the device. No user information is sent.
- Wikimedia (upload.wikimedia.org): pronunciation recordings linked from those dictionary entries.
- Apple frameworks on the device: Translation (on-device translation), AVSpeechSynthesizer (reading aloud), local notifications.
There is no backend server, authentication, payment processing, analytics, advertising or AI service. The stories and translations are bundled in the app.

5. REGIONAL DIFFERENCES
None. The app works the same in every region. Translation and read-aloud use the languages and voices Apple provides on the device.

6. REGULATION AND THIRD-PARTY MATERIAL
Mythos does not operate in a regulated industry. The stories and their translations were written for Mythos. Dictionary content is from Wiktionary via kaikki.org under the CC BY-SA 4.0 licence, which allows this use, and it is credited with a link in every word lookup and in Settings.
```
