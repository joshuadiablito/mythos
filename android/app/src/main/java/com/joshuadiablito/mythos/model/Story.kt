package com.joshuadiablito.mythos.model

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class VocabItem(
    /** The word as it appears in the story. */
    val word: String,
    /** Its meaning in English, in this story. */
    val gloss: String,
)

/** One sentence of a story and its English translation. */
data class SentencePair(val original: String, val translation: String)

data class Story(
    val id: String,
    val language: Language,
    val level: Level,
    /** Position in its level's catalog, from 1. */
    val number: Int,
    val title: String,
    /** The title in English. */
    val subtitle: String,
    val topic: String,
    /** The iOS SF Symbol name for the topic; see `ui/theme/Symbols.kt`. */
    val symbol: String,
    val paragraphs: List<String>,
    /** Each paragraph's sentences beside their English translations. Empty
     *  if the story has no translation, or one that doesn't line up. */
    val sentencePairs: List<List<SentencePair>>,
    val vocab: List<VocabItem>,
) {
    /** Paragraphs joined by a blank line: what is read aloud. */
    val text: String get() = paragraphs.joinToString("\n\n")

    val wordCount: Int get() = paragraphs.sumOf { p -> p.split(Regex("\\s+")).count { it.isNotEmpty() } }

    /** At a learner's pace of about 50 words a minute. */
    val readingMinutes: Int get() = maxOf(1, (wordCount / 50.0).roundToInt())

    /** The first sentence, for notification previews. */
    val opening: String
        get() {
            val first = paragraphs.firstOrNull() ?: ""
            val end = first.indexOfFirst { it in ".!?;" }
            return if (end < 0) first else first.substring(0, end + 1)
        }

    fun gloss(word: String): String? {
        val w = word.lowercase()
        return vocab.firstOrNull { it.word.lowercase() == w }?.gloss
    }

    companion object {
        /** Pairs each paragraph's sentences, as [SentenceSplitter] splits them, with
         *  `english`: per paragraph, one translation per sentence. */
        fun sentencePairs(paragraphs: List<String>, english: List<List<String>>, language: Language): List<List<SentencePair>> {
            if (english.size != paragraphs.size) return emptyList()
            val pairs = mutableListOf<List<SentencePair>>()
            for ((paragraph, translations) in paragraphs.zip(english)) {
                val sentences = SentenceSplitter.split(paragraph, language.code)
                if (sentences.size != translations.size) return emptyList()
                pairs.add(sentences.zip(translations) { o, t -> SentencePair(o, t) })
            }
            return pairs
        }
    }
}

/** The JSON shape of one bundled catalog file: one language at one level. */
@Serializable
data class CatalogFile(
    val language: Language,
    val level: Level,
    val stories: List<Record>,
) {
    @Serializable
    data class Record(
        val id: String,
        val title: String,
        val subtitle: String,
        val topic: String,
        val symbol: String,
        val paragraphs: List<String>,
        val english: List<List<String>>? = null,
        val vocab: List<VocabItem>,
    )

    val resolved: List<Story>
        get() = stories.mapIndexed { index, r ->
            Story(
                id = r.id, language = language, level = level, number = index + 1, title = r.title,
                subtitle = r.subtitle, topic = r.topic, symbol = r.symbol, paragraphs = r.paragraphs,
                sentencePairs = Story.sentencePairs(r.paragraphs, r.english ?: emptyList(), language),
                vocab = r.vocab,
            )
        }
}
