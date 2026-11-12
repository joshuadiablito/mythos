package com.joshuadiablito.mythos.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID

/** A word the learner kept from a lookup. */
@Serializable
data class SavedWord(
    val id: String = UUID.randomUUID().toString(),
    val word: String,
    val language: Language,
    /** A short meaning, from the story's glossary or the dictionary. */
    val meaning: String? = null,
    /** Milliseconds since 1970. */
    val savedOn: Long,
    /** The story it was found in. */
    val storyTitle: String? = null,
)

/** Stories read and words saved. */
class ProgressStore(
    private val store: KeyValueStore,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val locale: Locale = Locale.getDefault(),
) {
    @Serializable
    private data class Stored(val read: Map<String, Long> = emptyMap(), val savedWords: List<SavedWord> = emptyList())

    /** Story ID → when it was finished (milliseconds since 1970). */
    var read by mutableStateOf(mapOf<String, Long>()); private set
    var savedWords by mutableStateOf(listOf<SavedWord>()); private set

    init {
        store.getString(KEY)?.let { runCatching { JSON.decodeFromString(Stored.serializer(), it) }.getOrNull() }?.let {
            read = it.read
            savedWords = it.savedWords
        }
    }

    private fun save() {
        store.putString(KEY, JSON.encodeToString(Stored.serializer(), Stored(read, savedWords)))
    }

    // Reading

    fun isRead(story: Story): Boolean = read.containsKey(story.id)

    fun toggleRead(story: Story, on: Instant = Instant.now()) {
        read = if (isRead(story)) read - story.id else read + (story.id to on.toEpochMilli())
        save()
    }

    fun readCount(stories: List<Story>): Int = stories.count(::isRead)

    private fun readDays(): Set<LocalDate> = read.values.map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.toSet()

    /** Consecutive days with a story finished, counting today or, if nothing
     *  is read yet today, ending yesterday (the streak isn't lost until tomorrow). */
    fun streak(asOf: Instant = Instant.now()): Int {
        val days = readDays()
        var day = asOf.atZone(zone).toLocalDate()
        if (day !in days) day = day.minusDays(1)
        var count = 0
        while (day in days) {
            count += 1
            day = day.minusDays(1)
        }
        return count
    }

    /** The days of the week containing `date`, each with whether a story was finished on it. */
    fun week(containing: Instant = Instant.now()): List<Pair<LocalDate, Boolean>> {
        val days = readDays()
        val first = WeekFields.of(locale).firstDayOfWeek
        val start = containing.atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(first))
        return (0L until 7L).map { start.plusDays(it).let { d -> d to (d in days) } }
    }

    // Words

    fun isSaved(word: String, language: Language): Boolean =
        savedWords.any { it.language == language && it.word.lowercase() == word.lowercase() }

    /** Saves each of a story's glossary words that isn't saved already. */
    fun saveAll(items: List<VocabItem>, language: Language, storyTitle: String) {
        var words = savedWords
        for (item in items.reversed()) {
            if (words.any { it.language == language && it.word.lowercase() == item.word.lowercase() }) continue
            words = listOf(SavedWord(word = item.word, language = language, meaning = item.gloss,
                savedOn = System.currentTimeMillis(), storyTitle = storyTitle)) + words
        }
        savedWords = words
        save()
    }

    fun toggleSaved(word: String, language: Language, meaning: String?, storyTitle: String?) {
        val i = savedWords.indexOfFirst { it.language == language && it.word.lowercase() == word.lowercase() }
        savedWords = if (i >= 0) savedWords.filterIndexed { j, _ -> j != i }
        else listOf(SavedWord(word = word, language = language, meaning = meaning,
            savedOn = System.currentTimeMillis(), storyTitle = storyTitle)) + savedWords
        save()
    }

    fun remove(words: List<SavedWord>) {
        val ids = words.map { it.id }.toSet()
        savedWords = savedWords.filter { it.id !in ids }
        save()
    }

    companion object {
        private const val KEY = "progress.v1"
        private val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
