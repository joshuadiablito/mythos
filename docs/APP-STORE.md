# Submitting Mythos to the App Store

## Will it be approved?

Very likely.

- **A complete app with real content**: 630 stories, a dictionary, read-aloud,
  flashcards and streaks. Apple mostly rejects apps that are thin, unfinished
  or a website in a wrapper (guidelines 4.2 and 2.1).
- **No account and no server**: no account deletion or reviewer demo login to
  provide. No in-app purchases, so no StoreKit review.
- **Local notifications only**, which Apple allows. Ask for permission at a
  sensible moment (onboarding), and make sure the app still works if the user
  says no.
- **Dictionary data is attributed**: `LookupSheet.swift` credits
  Wiktionary/kaikki.org under CC BY-SA 4.0, and Settings links to kaikki.org.
- **Third-party service**: kaikki.org is a free, volunteer-run site. Apple
  doesn't object to this. The risk is practical: if it's down while the
  reviewer taps a word, they see an error. Mention it in the review notes.

## Privacy manifest

`Mythos/PrivacyInfo.xcprivacy` declares why Mythos uses "required-reason"
APIs; without it the upload is rejected automatically (ITMS-91053). It uses
two:

| API | Where | Reason code |
| --- | --- | --- |
| UserDefaults | `AppSettings`, `ProgressStore`, … | `CA92.1` |
| File timestamps (modification dates) | `DictionaryCache.swift` | `C617.1` |

The manifest also says the app collects no data and does no tracking. If
new code uses another required-reason API (disk space, system boot time),
add it there.

## How to submit

1. **Join the Apple Developer Program** ($99/year) at developer.apple.com.
   The project already has team ID `JXFB483Z4C`; check that it's the paid
   membership, not a free Personal Team.
2. **Create the app record** in App Store Connect: My Apps → + → New App.
   - Bundle ID `com.joshuadiablito.mythos` (you may need to register it under
     Certificates, IDs & Profiles first).
   - The store name is "Mythos: Language stories". Under the icon on the
     home screen it stays "Mythos" (`CFBundleDisplayName`), because a longer
     name gets cut off there.
3. **Fill in the listing** from [`app-store/LISTING.md`](app-store/LISTING.md):
   text, keywords, URLs, categories, age rating, App Privacy answers, review
   notes and the 6.9" screenshots in `app-store/screenshots/`. The support
   and privacy pages are `docs/*.html`, served by GitHub Pages from `/docs`.
4. **Archive and upload.** In Xcode, set the destination to *Any iOS Device*,
   then Product → Archive → Distribute App → App Store Connect.
   - Mythos uses only standard HTTPS, so the build sets
     `ITSAppUsesNonExemptEncryption = NO` and Apple skips the
     export-compliance question.
5. **Test with TestFlight** on your own phone. Check notifications at real
   times and word lookups on cellular data.
6. **Submit for review.** In the review notes, explain that stories unlock
   one per day and how to open one from Stories/Today. Otherwise the reviewer
   may only ever see a single story. Review usually takes about a day.

## Also check

- **The iOS 26-only target** is fine, but it limits the audience to newer
  phones.
- **If the stories were AI-generated**, that's allowed. Proofread them,
  because reviewers and users notice mistakes in language-learning content.
