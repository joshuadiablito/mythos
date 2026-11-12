package com.joshuadiablito.mythos

import com.joshuadiablito.mythos.model.AppSettings
import com.joshuadiablito.mythos.model.Course
import com.joshuadiablito.mythos.model.DailyPlan
import com.joshuadiablito.mythos.model.Language
import com.joshuadiablito.mythos.model.Level
import com.joshuadiablito.mythos.model.MemoryStore
import com.joshuadiablito.mythos.model.ProgressStore
import com.joshuadiablito.mythos.model.SentenceSplitter
import com.joshuadiablito.mythos.services.NotificationContent
import com.joshuadiablito.mythos.ui.theme.Symbols
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class CatalogTest {
    private val catalog = testCatalog

    @Test fun everyLanguageHas15StoriesAtEveryLevel() {
        for (language in Language.entries) for (level in Level.entries) {
            assertEquals("${language.code} ${level.code}", 15, catalog.stories(language, level).size)
        }
        assertEquals(630, catalog.stories.size)
    }

    @Test fun idsAreUniqueAndNamedForTheirCourse() {
        assertEquals(catalog.stories.size, catalog.stories.map { it.id }.toSet().size)
        for (story in catalog.stories) {
            assertTrue(story.id, story.id.startsWith("${story.language.code}-${story.level.code.lowercase()}-"))
        }
    }

    @Test fun eachGlossaryWordAppearsInItsStory() {
        for (story in catalog.stories) {
            val text = story.text.lowercase()
            assertTrue("${story.id} has only ${story.vocab.size} words", story.vocab.size >= 6)
            for (item in story.vocab) assertTrue("${story.id}: “${item.word}” isn't in the text", text.contains(item.word.lowercase()))
        }
    }

    @Test fun everySymbolHasAnAndroidIcon() {
        val missing = catalog.stories.map { it.symbol }.toSet().filter { !Symbols.has(it) }
        assertTrue("No Material icon for $missing", missing.isEmpty())
    }

    @Test fun openingIsTheFirstSentence() {
        assertEquals("Bonjour !", catalog.story("fr-a1-01")!!.opening)
        assertEquals("Felix steht um sieben Uhr auf.", catalog.story("de-a1-02")!!.opening)
    }
}

class TranslationTest {
    @Test fun everyStoryHasAnEnglishTranslationSentenceForSentence() {
        for (story in testCatalog.stories) {
            assertTrue("${story.id}: translation missing or out of step with its sentences", story.sentencePairs.isNotEmpty())
            assertTrue(story.id, story.sentencePairs.flatten().all { it.translation.isNotBlank() })
        }
    }

    @Test fun dialogueStaysWithItsQuotationMarks() {
        assertEquals(
            listOf("Le serveur arrive.", "« Bonjour, madame. Qu'est-ce que vous désirez ? »", "Camille répond : « Un café. »"),
            SentenceSplitter.split("Le serveur arrive. « Bonjour, madame. Qu'est-ce que vous désirez ? » Camille répond : « Un café. »", "fr"),
        )
        assertEquals(
            listOf("„Was kostet das?“, fragt sie.", "„Neun Euro.“"),
            SentenceSplitter.split("„Was kostet das?“, fragt sie. „Neun Euro.“", "de"),
        )
    }

    @Test fun speechTagsAfterADashContinueTheSentence() {
        assertEquals(listOf("— Quanto é? — pergunta.", "— Quinze reais."), SentenceSplitter.split("— Quanto é? — pergunta. — Quinze reais.", "pt"))
    }

    @Test fun greekQuestionMarkEndsASentence() {
        assertEquals(
            listOf("Φοβήθηκε: πού το είχε αφήσει;", "Τηλεφώνησε στη γειτόνισσα."),
            SentenceSplitter.split("Φοβήθηκε: πού το είχε αφήσει; Τηλεφώνησε στη γειτόνισσα.", "el"),
        )
        assertEquals(1, SentenceSplitter.split("No le gusta el invierno; prefiere el verano.", "es").size)
    }
}

class DailyPlanTest {
    private val zone = ZoneId.of("Europe/London")
    private val plan = DailyPlan(testCatalog, zone)

    private fun at(y: Int, m: Int, d: Int, h: Int = 0) = LocalDateTime.of(y, m, d, h, 0).atZone(zone).toInstant()
    private fun Instant.plusDays(n: Long) = ZonedDateTime.ofInstant(this, zone).plusDays(n).toInstant()
    private fun course(language: Language, level: Level, start: Instant) = Course(language = language, level = level, startedOn = start.toEpochMilli())

    @Test fun coursesStartAtTheirFirstStoryAndWrapAround() {
        val start = at(2026, 10, 1, 9)
        val course = course(Language.FRENCH, Level.A1, start)
        assertEquals("fr-a1-01", plan.story(course, start)?.id)
        assertEquals("fr-a1-02", plan.story(course, start.plusDays(1).minusSeconds(8 * 3600))?.id)
        assertEquals("fr-a1-15", plan.story(course, start.plusDays(14))?.id)
        assertEquals("fr-a1-01", plan.story(course, start.plusDays(15))?.id)
    }

    @Test fun languagesTakeTurns() {
        val start = at(2026, 10, 1)
        val courses = listOf(course(Language.SPANISH, Level.A1, start), course(Language.GREEK, Level.A2, start))
        val languages = (0L until 4L).mapNotNull { plan.featured(courses, start.plusDays(it))?.language }
        assertEquals(4, languages.size)
        assertNotEquals(languages[0], languages[1])
        assertEquals(languages[0], languages[2])
    }

    @Test fun coursesWithoutStoriesAreSkipped() {
        val empty = DailyPlan(com.joshuadiablito.mythos.model.Catalog(emptyList()), zone)
        assertNull(empty.featured(listOf(course(Language.ITALIAN, Level.B2, Instant.now())), Instant.now()))
        assertTrue(empty.arrived(course(Language.GREEK, Level.C1, Instant.now()), Instant.now()).isEmpty())
    }

    @Test fun onlyStoriesThatHaveArrivedAreListedNewestFirst() {
        val start = at(2026, 10, 1, 9)
        val course = course(Language.GERMAN, Level.A2, start)
        assertEquals(listOf("de-a2-01"), plan.arrived(course, start).map { it.id })
        val fourth = start.plusDays(3)
        assertEquals(listOf("de-a2-04", "de-a2-03", "de-a2-02", "de-a2-01"), plan.arrived(course, fourth).map { it.id })
        assertEquals(11, plan.remaining(course, fourth))
        assertEquals(plan.story(course, fourth), plan.arrived(course, fourth).first())
    }

    @Test fun afterTheLastStoryEverythingHasArrived() {
        val start = at(2026, 1, 1)
        val course = course(Language.GREEK, Level.A1, start)
        assertEquals(15, plan.arrived(course, start.plusDays(40)).size)
        assertEquals(0, plan.remaining(course, start.plusDays(40)))
        assertTrue(plan.arrived(course, start.plusDays(-1)).isEmpty())
    }
}

class ProgressTest {
    private val zone = ZoneId.systemDefault()

    @Test fun streakCountsConsecutiveDaysAndSurvivesUntilTomorrow() {
        val progress = ProgressStore(MemoryStore(), zone)
        val stories = testCatalog.stories(Language.FRENCH, Level.A1)
        val today = Instant.now()
        fun day(n: Long) = ZonedDateTime.ofInstant(today, zone).plusDays(n).toInstant()
        assertEquals(0, progress.streak(today))
        progress.toggleRead(stories[0], day(-2))
        progress.toggleRead(stories[1], day(-1))
        assertEquals(2, progress.streak(today))
        progress.toggleRead(stories[2], today)
        assertEquals(3, progress.streak(today))
        assertEquals(0, progress.streak(day(2)))
    }

    @Test fun savingIsCaseInsensitivePerLanguage() {
        val progress = ProgressStore(MemoryStore(), zone)
        progress.toggleSaved("Maison", Language.FRENCH, "house", null)
        assertTrue(progress.isSaved("maison", Language.FRENCH))
        assertFalse(progress.isSaved("maison", Language.SPANISH))
        progress.toggleSaved("maison", Language.FRENCH, null, null)
        assertTrue(progress.savedWords.isEmpty())
    }

    @Test fun progressIsSavedAndReloaded() {
        val store = MemoryStore()
        val first = ProgressStore(store, zone)
        first.saveAll(testCatalog.story("es-a1-02")!!.vocab, Language.SPANISH, "Story")
        first.toggleRead(testCatalog.story("es-a1-02")!!)
        val second = ProgressStore(store, zone)
        assertEquals(first.savedWords, second.savedWords)
        assertTrue(second.isRead(testCatalog.story("es-a1-02")!!))
    }
}

class SettingsTest {
    @Test fun eachCourseCanHaveItsOwnTime() {
        val s = AppSettings(MemoryStore())
        s.setTime(8, 0)
        val spanish = s.add(Language.SPANISH, Level.A1)!!.id
        val greek = s.add(Language.GREEK, Level.A1)!!.id
        s.setDeliveryTime(java.time.LocalTime.of(7, 0), spanish)
        assertEquals(java.time.LocalTime.of(7, 0), s.time(s.course(spanish)!!))
        assertEquals(java.time.LocalTime.of(8, 0), s.time(s.course(greek)!!))
        assertTrue(s.hasOwnTime(spanish) && !s.hasOwnTime(greek))
        s.setTime(9, 0)
        assertEquals(java.time.LocalTime.of(9, 0), s.time(s.course(greek)!!))
        assertEquals(java.time.LocalTime.of(7, 0), s.time(s.course(spanish)!!))
        s.useSharedTime(spanish)
        assertEquals(java.time.LocalTime.of(9, 0), s.time(s.course(spanish)!!))
    }

    @Test fun aLanguageCanBeLearnedAtTwoLevelsButNotTheSameOneTwice() {
        val s = AppSettings(MemoryStore())
        val a1 = s.add(Language.SPANISH, Level.A1)!!
        val a2 = s.add(Language.SPANISH, Level.A2)!!
        assertNull(s.add(Language.SPANISH, Level.A1))
        assertEquals(listOf(Level.A1, Level.A2), s.courses.map { it.level })
        assertTrue(s.hasSeveralLevels(Language.SPANISH))
        assertTrue(Level.A1 !in s.levelsToAdd(Language.SPANISH) && Level.B1 in s.levelsToAdd(Language.SPANISH))
        s.setLevel(Level.A1, a2.id)
        assertEquals(Level.A2, s.course(a2.id)?.level)
        s.setLevel(Level.B1, a2.id)
        assertEquals(Level.B1, s.course(a2.id)?.level)
        assertEquals(Level.A1, s.course(a1.id)?.level)
    }

    @Test fun togglingALanguageOffRemovesEveryLevel() {
        val s = AppSettings(MemoryStore())
        s.add(Language.FRENCH, Level.A1)
        s.add(Language.FRENCH, Level.B2)
        s.add(Language.GREEK, Level.A1)
        s.toggle(Language.FRENCH)
        assertEquals(listOf(Language.GREEK), s.courses.map { it.language })
    }

    @Test fun settingsAreSavedAndReloaded() {
        val store = MemoryStore()
        val first = AppSettings(store)
        val course = first.add(Language.PORTUGAL, Level.C1)!!
        first.setTime(19, 30)
        first.setOnboarded(true)
        val second = AppSettings(store)
        assertEquals(listOf(course), second.courses)
        assertEquals(19, second.hour)
        assertTrue(second.hasOnboarded)
    }
}

class NotificationContentTest {
    @Test fun notificationsInviteTheReaderIn() {
        val content = NotificationContent.forStory(testCatalog.story("fr-a1-01")!!)
        assertEquals("Bonjour! Your new story is here", content.title)
        assertEquals("Je m'appelle Léa · Français A1", content.subtitle)
        assertTrue(content.body.startsWith("A new story is ready for you to read today."))
    }
}
