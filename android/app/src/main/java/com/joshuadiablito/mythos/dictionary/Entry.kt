package com.joshuadiablito.mythos.dictionary

// One part of speech of a word, from kaikki.org (English Wiktionary as JSON).
// Ported from the iOS app's Dictionary/Entry.swift.

data class WordForm(
    val form: String,
    val tags: List<String>,
    /** From the inflection table, rather than the entry's head line. */
    val fromTable: Boolean,
)

data class Sense(val gloss: String, val tags: List<String>, val example: Example?) {
    data class Example(val text: String, val translation: String?)
}

/** "first/third-person singular present indicative" of a lemma. */
data class FormOf(val lemma: String, val description: String, val tags: List<String>)

data class Entry(
    val word: String,
    val pos: String,
    /** The head line, e.g. "casa f (plural casas)". */
    val head: String? = null,
    val ipa: String? = null,
    val audioURL: String? = null,
    val senses: List<Sense> = emptyList(),
    val forms: List<WordForm> = emptyList(),
    val formOf: List<FormOf> = emptyList(),
) {
    val id: String get() = "$word|$pos"

    /** "verb", "noun", "adj" → a readable label. */
    val partOfSpeech: String
        get() = when (pos) {
            "adj" -> "adjective"
            "adv" -> "adverb"
            "prep" -> "preposition"
            "conj" -> "conjunction"
            "pron" -> "pronoun"
            "det" -> "determiner"
            "intj" -> "interjection"
            "num" -> "numeral"
            "name" -> "proper noun"
            else -> pos
        }
}

/** What the dictionary says about a looked-up word: its own entries (which may
 *  just say it's a form of something), and the entries for those lemmas. */
data class LookupResult(val query: String, val entries: List<Entry>, val lemmas: List<Entry>) {
    val all: List<Entry> get() = entries + lemmas

    val isEmpty: Boolean get() = all.all { it.senses.isEmpty() && it.formOf.isEmpty() }

    val ipa: String? get() = entries.firstNotNullOfOrNull { it.ipa } ?: lemmas.firstNotNullOfOrNull { it.ipa }

    /** A recording of the word itself; a lemma's recording is a different word. */
    val audioURL: String? get() = entries.firstNotNullOfOrNull { it.audioURL }

    /** A short meaning, to keep with a saved word. */
    val shortMeaning: String? get() = all.flatMap { it.senses }.firstOrNull()?.gloss
}
