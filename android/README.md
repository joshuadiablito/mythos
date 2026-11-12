# Mythos for Android

The Android version of Mythos: the same 630 stories, dictionary, read-aloud,
flashcards and daily notifications as the iPhone app, in Kotlin and Jetpack
Compose.

It reads the story catalog straight from `../Mythos/Resources/Catalog`, and its
tests read the kaikki.org fixtures from `../MythosTests/Fixtures`, so a story
added for iOS is in the Android app at its next build too.

## Building

Requires Android Studio (or JDK 17+ and the Android SDK with platform 37).

```sh
cd android
./gradlew testDebugUnitTest     # the tests, on the JVM: no phone needed
./gradlew installDebug          # onto a running emulator or a plugged-in phone
./gradlew assembleRelease       # app/build/outputs/apk/release/app-release.apk
```

Or open the `android` folder in Android Studio and press Run.

Release builds are signed with `android/release.keystore`, using the passwords
in `android/keystore.properties`. Neither is committed. **Back both up** (a
password manager is ideal): every update to an installed app must be signed
with the same key, so losing it means testers have to uninstall and start again.
Without `keystore.properties`, release builds come out unsigned.

Debug builds accept `-e tab library` (or `words`, `settings`) to open on a
tab, for screenshots: `adb shell am start -n com.joshuadiablito.mythos/.MainActivity -e tab library`.

## How it maps to the iOS app

| iOS | Android |
| --- | --- |
| SwiftUI, `@Observable` | Jetpack Compose, `mutableStateOf` |
| `Model/`, `Dictionary/` | `model/`, `dictionary/`: line-for-line ports, with the iOS tests ported to `app/src/test` |
| `UserDefaults` | SharedPreferences, as JSON |
| `UNCalendarNotificationTrigger`s, 60 days ahead | One `AlarmManager` alarm per course; when it fires it posts the day's story and sets the next. `RescheduleReceiver` sets them again after a restart or a clock change |
| `AVSpeechSynthesizer` | `TextToSpeech`, a paragraph at a time; pausing stops it and resuming starts again at the word it stopped on |
| `NLTokenizer` | `java.text.BreakIterator`, with elisions (j', l', dell') split off as on iOS |
| Translation framework | Google ML Kit, on device; each language's model (~30 MB) downloads the first time |
| SF Symbols | Material icons, mapped in `ui/theme/Symbols.kt`; a test fails if a story uses a symbol without one |
| Liquid Glass | Translucent capsules over covers, solid cards elsewhere |
| `MeshGradient` covers | Nine overlapping radial gradients, seeded by story ID as on iOS, so each story's cover is the same on both |

### Differences on Android

- **Notification timing.** Android 14 and later only let an app set
  on-the-minute alarms if the user allows it. Settings shows an *Arrive on the
  minute* row with an *Allow* button when it's off; without it, Android may
  deliver the story up to an hour late.
- **Notification permission** is asked for at the end of onboarding, as on iOS.
  If it's refused twice, Android only lets the user turn it on in Settings, and
  the Today banner sends them there.
- **Voices** depend on the phone's text-to-speech engine. If a language has no
  voice installed, Mythos says so; Google's engine can download one in
  *Settings › Accessibility › Text-to-speech output*.
- **Size.** About 31 MB, most of it ML Kit's translation library. The APK
  includes only the ARM builds of it, which is what phones use.

## Getting it to a tester

There's no TestFlight on Android, but there are three ways to do the same
thing, from quickest to most like TestFlight:

1. **Send the APK.** Build a release, send
   `app/build/outputs/apk/release/app-release.apk` by any means (email, Drive,
   a chat app). On the phone, open it and allow installing from that app when
   asked. To update, send a newer build (raise `versionCode` in
   `app/build.gradle.kts`) and they install it over the top.
2. **Firebase App Distribution** (free). Upload the APK in the Firebase
   console, add the tester's email, and they get an invitation and install
   through the Firebase App Tester app, which also tells them about new
   builds. The closest thing to TestFlight.
3. **Google Play internal testing.** Needs a Play Console account (a one-off
   $25). Upload an app bundle (`./gradlew bundleRelease`), add the tester's
   Google account, and they install from the Play Store through an opt-in link.
   This is also the path to publishing on Play.

Google has begun requiring apps installed outside Play to come from verified
developers, starting in Brazil, Indonesia, Singapore and Thailand from
September 2026, with the rest of the world planned for 2027. Routes 2 and 3 are
unaffected; for route 1, a tester in one of those countries would need the
developer verification done first, or would install with `adb`.
