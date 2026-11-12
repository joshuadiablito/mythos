package com.joshuadiablito.mythos

import com.joshuadiablito.mythos.dictionary.DictionaryCache
import com.joshuadiablito.mythos.dictionary.DictionaryClient
import com.joshuadiablito.mythos.dictionary.EnglishVerb
import com.joshuadiablito.mythos.dictionary.EnglishVerb.Tense
import com.joshuadiablito.mythos.dictionary.Entry
import com.joshuadiablito.mythos.dictionary.FormOf
import com.joshuadiablito.mythos.dictionary.InflectionBuilder
import com.joshuadiablito.mythos.dictionary.KaikkiParser
import com.joshuadiablito.mythos.dictionary.LookupCandidates
import com.joshuadiablito.mythos.dictionary.LookupResult
import com.joshuadiablito.mythos.dictionary.Sense
import com.joshuadiablito.mythos.dictionary.WhoLine
import com.joshuadiablito.mythos.dictionary.WordForm
import com.joshuadiablito.mythos.dictionary.whoLines
import com.joshuadiablito.mythos.model.Language
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class KaikkiParserTest {
    @Test fun urlsAreSegmentedByFirstLetters() {
        assertEquals("https://kaikki.org/dictionary/French/meaning/m/ma/mange.jsonl", KaikkiParser.url("French", "mange"))
        assertEquals(
            "https://kaikki.org/dictionary/Greek/meaning/%CF%84/%CF%84%CF%81/%CF%84%CF%81%CF%8E%CF%89.jsonl",
            KaikkiParser.url("Greek", "τρώω"),
        )
        assertEquals("https://kaikki.org/dictionary/French/meaning/s/s'/s'habille.jsonl", KaikkiParser.url("French", "s'habille"))
    }

    @Test fun anInflectedFormPointsToItsLemma() {
        val entries = fixture("french-mange")
        assertTrue(entries.flatMap { it.formOf }.any { it.lemma == "manger" })
        assertFalse(entries.flatMap { it.formOf }.first().description.endsWith("of manger"))
    }

    @Test fun spanishPastIsAFormOfComer() {
        assertTrue(fixture("spanish-comia").flatMap { it.formOf }.any { it.lemma == "comer" })
    }

    @Test fun pronunciationRowsAreNotForms() {
        val forms = fixture("german-haus").flatMap { it.forms }.map { it.form }
        assertTrue(forms.isNotEmpty())
        assertTrue(forms.all { !it.contains("ʊ") && !it.contains("ˈ") })
    }

    @Test fun malformedLinesAreSkipped() {
        assertTrue(KaikkiParser.parse("not json\n{\"word\": \"x\"}\n").isEmpty())
    }
}

class InflectionTest {
    @Test fun germanNounDeclinesByCaseAndNumber() {
        val noun = fixture("german-haus").first { it.pos == "noun" }
        val table = InflectionBuilder.build(noun, Language.GERMAN).tables.first()
        assertEquals("Case", table.rowHeading)
        assertTrue(table.rows.any { it.key == "dative" })
        assertTrue(table.contains("Häuser"))
    }

    @Test fun greekVerbHasNamedTensesWithPronouns() {
        val verb = fixture("greek-troo").first { it.pos == "verb" && it.forms.isNotEmpty() }
        val inflections = InflectionBuilder.build(verb, Language.GREEK)
        val titles = inflections.tables.map { it.title }
        assertTrue(titles.toString(), "Active present" in titles)
        assertTrue(titles.toString(), "Active simple past (aorist)" in titles)
        assertTrue(titles.toString(), "Passive present" in titles)
        val present = inflections.tables.first { it.title == "Active present" }
        assertEquals("εγώ", present.cells["first-person"]?.get("singular")?.pronoun)
    }

    @Test fun spanishAdjectiveIncludesItsLemma() {
        val adjective = fixture("spanish-bonito").first { it.pos == "adj" }
        assertTrue(InflectionBuilder.build(adjective, Language.SPANISH).tables.any { it.contains("bonito") && it.contains("bonitas") })
    }

    @Test fun frenchJeElidesBeforeAVowel() {
        val entry = Entry(word = "aimer", pos = "verb", forms = listOf(
            WordForm("aime", listOf("first-person", "singular", "present", "indicative"), true),
            WordForm("aimons", listOf("first-person", "plural", "present", "indicative"), true),
        ))
        val table = InflectionBuilder.build(entry, Language.FRENCH).tables.first()
        assertEquals("j’", table.cells["first-person"]?.get("singular")?.pronoun)
        assertEquals("nous", table.cells["first-person"]?.get("plural")?.pronoun)
        assertEquals("Indicative present", table.title)
    }
}

class LookupCandidatesTest {
    @Test fun casesAndElisionsAreTried() {
        assertEquals(listOf("Mange", "mange"), LookupCandidates.candidates("Mange"))
        assertEquals(listOf("l'école", "école", "L'école", "École"), LookupCandidates.candidates("l’école"))
        assertTrue("Haus" in LookupCandidates.candidates("haus"))
        assertTrue(LookupCandidates.candidates("  ").isEmpty())
    }

    @Test fun aPhraseIsLookedUpByItsMainWord() {
        assertEquals(listOf("despierta"), LookupCandidates.mainWords("se despierta"))
        assertEquals(listOf("peur"), LookupCandidates.mainWords("a eu peur"))
        assertEquals(listOf("zieht", "sich"), LookupCandidates.mainWords("zieht sich an"))
        assertTrue(LookupCandidates.mainWords("despierta").isEmpty())
    }
}

class EnglishVerbTest {
    private fun verb(gloss: String) = EnglishVerb.fromGloss(gloss)!!

    @Test fun glossesBecomeVerbs() {
        assertEquals(EnglishVerb("eat", ""), EnglishVerb.fromGloss("to eat"))
        assertEquals(EnglishVerb("wake", "up"), EnglishVerb.fromGloss("to wake (someone) up"))
        assertEquals("for", EnglishVerb.fromGloss("(transitive) to look for, search")?.rest)
        assertNull(EnglishVerb.fromGloss("house"))
        assertNull(EnglishVerb.fromGloss("inflection of manger:"))
    }

    @Test fun whoIsDoingIt() {
        val eat = verb("to eat")
        assertEquals("he/she eats", eat.phrase("third-person", "singular", Tense.PRESENT))
        assertEquals("we eat", eat.phrase("first-person", "plural", Tense.PRESENT))
        assertEquals("they ate", eat.phrase("third-person", "plural", Tense.PAST))
        assertEquals("I was eating", eat.phrase("first-person", "singular", Tense.IMPERFECT))
        assertEquals("you all will eat", eat.phrase("second-person", "plural", Tense.FUTURE))
        assertEquals("he/she would eat", eat.phrase("third-person", "singular", Tense.CONDITIONAL))
        assertEquals("he/she has eaten", eat.phrase("third-person", "singular", Tense.PERFECT))
        assertEquals("eat!", eat.phrase("second-person", "singular", Tense.IMPERATIVE))
        assertEquals("let's eat!", eat.phrase("first-person", "plural", Tense.IMPERATIVE))
        assertEquals("(that) he/she eat", eat.phrase("third-person", "singular", Tense.PRESENT_SUBJUNCTIVE))
    }

    @Test fun englishMorphology() {
        val be = verb("to be called")
        assertEquals("I am called", be.phrase("first-person", "singular", Tense.PRESENT))
        assertEquals("they were called", be.phrase("third-person", "plural", Tense.PAST))
        assertEquals("I was stopping", verb("to stop").phrase("first-person", "singular", Tense.IMPERFECT))
        assertEquals("he/she tries", verb("to try").phrase("third-person", "singular", Tense.PRESENT))
        assertEquals("he/she watches", verb("to watch").phrase("third-person", "singular", Tense.PRESENT))
        assertEquals("I understood", verb("to understand").phrase("first-person", "singular", Tense.PAST))
        assertEquals("I knew", verb("to know").phrase("first-person", "singular", Tense.IMPERFECT))
        assertEquals("I was living", verb("to live").phrase("first-person", "singular", Tense.IMPERFECT))
    }

    @Test fun tagsBecomeTenses() {
        assertEquals(Tense.PRESENT, EnglishVerb.tense(listOf("first-person", "indicative", "present", "singular", "subjunctive", "third-person")))
        assertEquals(Tense.PAST, EnglishVerb.tense(listOf("indicative", "past", "perfective")))
        assertEquals(Tense.IMPERFECT, EnglishVerb.tense(listOf("imperfect", "imperfective", "indicative")))
        assertEquals(Tense.FUTURE_CONTINUOUS, EnglishVerb.tense(listOf("future", "imperfective", "indicative")))
        assertEquals(Tense.PAST, EnglishVerb.tense(listOf("indicative", "preterite")))
        assertEquals(Tense.PAST_SUBJUNCTIVE, EnglishVerb.tense(listOf("subjunctive", "imperfect-ra")))
        assertNull(EnglishVerb.tense(listOf("dependent", "perfective")))
        assertEquals(listOf("first-person", "third-person"), EnglishVerb.subjects(listOf("first-person", "third-person", "singular", "present")).map { it.first })
    }

    @Test fun spanishPastFromARealEntry() {
        val form = fixture("spanish-comia").flatMap { it.formOf }.first { it.lemma == "comer" }
        val tense = EnglishVerb.tense(form.tags)!!
        val phrases = EnglishVerb.subjects(form.tags).mapNotNull { verb("to eat").phrase(it.first, it.second, tense) }
        assertTrue(phrases.toString(), "he/she was eating" in phrases)
    }

    private fun despertar(vararg glosses: String) =
        Entry(word = "despertar", pos = "verb", senses = glosses.map { Sense(it, emptyList(), null) })

    @Test fun theStoryGlossPicksTheAlternative() {
        assertEquals(listOf("", "up"), EnglishVerb.alternatives("to wake, wake up").map { it.rest })
        assertEquals("up", EnglishVerb.fromEntry(despertar("to wake, wake up"), "wakes up")?.rest)
        assertEquals("", EnglishVerb.fromEntry(despertar("to wake, wake up"))?.rest)
        assertEquals("", EnglishVerb.fromEntry(despertar("to wake, wake up"), "rouses")?.rest)
        val senses = despertar("to awaken", "to wake up", "to wake")
        assertEquals("up", EnglishVerb.fromEntry(senses, "wakes up")?.rest)
        assertEquals("awaken", EnglishVerb.fromEntry(senses)?.base)
    }

    private val despiertaForm = Entry(word = "despierta", pos = "verb", formOf = listOf(
        FormOf("despertar", "third-person singular present indicative", listOf("third-person", "singular", "present", "indicative")),
        FormOf("despertar", "second-person singular imperative", listOf("second-person", "singular", "imperative")),
    ))

    @Test fun theReadingKeepsTheWholePhraseAndSaysWho() {
        val result = LookupResult("se despierta", listOf(despiertaForm), listOf(despertar("to wake up")))
        assertEquals(listOf(WhoLine("él/ella/usted se despierta", "he/she wakes up")), result.whoLines(Language.SPANISH))
    }

    @Test fun aPronounBeforeTheVerbRulesOutACommand() {
        val phrase = LookupResult("se despierta", listOf(despiertaForm), listOf(despertar("to wake, wake up")))
        assertEquals(listOf(WhoLine("él/ella/usted se despierta", "he/she wakes up")), phrase.whoLines(Language.SPANISH, "wakes up"))
        val word = LookupResult("despierta", listOf(despiertaForm), listOf(despertar("to wake, wake up")))
        assertTrue(WhoLine("despierta", "wake!") in word.whoLines(Language.SPANISH))
    }

    @Test fun anInfinitiveReadsAsOne() {
        val result = LookupResult("despertar", listOf(despertar("to wake up")), emptyList())
        assertEquals(listOf(WhoLine("", "to wake up")), result.whoLines(Language.SPANISH))
    }
}

class DictionaryCacheTest {
    private val directory = Files.createTempDirectory("cache").toFile().also { it.deleteOnExit() }

    @Test fun storesAndReadsResponsesAndMissingWords() {
        val cache = DictionaryCache(directory)
        val url = "https://kaikki.org/dictionary/French/meaning/m/ma/mange.jsonl"
        assertNull(cache.read(url))
        cache.store(DictionaryCache.Hit.Found("x".toByteArray()), url)
        assertEquals(DictionaryCache.Hit.Found("x".toByteArray()), cache.read(url))

        val missing = "https://kaikki.org/dictionary/French/meaning/z/zz/zzz.jsonl"
        cache.store(DictionaryCache.Hit.NotFound, missing)
        assertEquals(DictionaryCache.Hit.NotFound, cache.read(missing))
        // "No such word" is forgotten after 30 days, in case Wiktionary has added it.
        assertNull(cache.read(missing, System.currentTimeMillis() + 31L * 24 * 3600 * 1000))
    }

    @Test fun trimmingRemovesTheLeastRecentlyUsed() {
        val cache = DictionaryCache(directory, maxBytes = 150)
        val old = "https://kaikki.org/a.jsonl"
        val recent = "https://kaikki.org/b.jsonl"
        cache.store(DictionaryCache.Hit.Found(ByteArray(100) { 1 }), old)
        cache.store(DictionaryCache.Hit.Found(ByteArray(100) { 2 }), recent)
        cache.read(old, System.currentTimeMillis() - 3_600_000)
        cache.read(recent)
        cache.trim()
        assertNull(cache.read(old))
        assertNotNull(cache.read(recent))
    }

    @Test fun aRepeatLookupMakesNoRequests() = runTest {
        val requests = mutableListOf<String>()
        val responses = mapOf(
            "mange.jsonl" to fixtureText("french-mange").toByteArray(),
            "manger.jsonl" to """{"word": "manger", "pos": "verb", "senses": [{"glosses": ["to eat"]}]}""".toByteArray(),
        )
        val fetcher: suspend (String) -> Pair<Int, ByteArray> = { url ->
            val name = url.substringAfterLast('/')
            synchronized(requests) { requests.add(name) }
            responses[name]?.let { 200 to it } ?: (404 to ByteArray(0))
        }
        val cache = DictionaryCache(directory)
        val result = DictionaryClient(cache, fetcher).lookUp("Mange", Language.FRENCH)
        assertTrue(result.lemmas.any { it.word == "manger" })
        assertEquals(listOf("Mange.jsonl", "mange.jsonl", "manger.jsonl"), requests)

        // A new client (as after relaunching the app) answers entirely from disk.
        requests.clear()
        val again = DictionaryClient(cache, fetcher).lookUp("Mange", Language.FRENCH)
        assertTrue(requests.isEmpty())
        assertEquals(result.lemmas.map { it.word }, again.lemmas.map { it.word })
    }
}
