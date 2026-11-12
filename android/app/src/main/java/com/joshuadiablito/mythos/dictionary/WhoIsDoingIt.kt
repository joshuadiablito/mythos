package com.joshuadiablito.mythos.dictionary

import com.joshuadiablito.mythos.model.Language

/** One way to read a verb form: who it is, in the language, and what that
 *  means in English. "il/elle achète" → "he/she buys". */
data class WhoLine(
    /** The form with its subject pronoun, or empty for a form without one (an infinitive). */
    val subject: String,
    val english: String,
)

/** The readings of one form-of sense: "je mange" and "il/elle mange" for
 *  "mange", each with its English. `form` is the word as it was looked up,
 *  clitics and all ("se despierta"). */
fun whoLines(formOf: FormOf, form: String, language: Language, verb: EnglishVerb?): List<WhoLine> {
    if (verb == null) return emptyList()
    val tense = EnglishVerb.tense(formOf.tags)
        ?: return verb.phrase(formOf.description)?.let { listOf(WhoLine("", it)) } ?: emptyList()
    val shown = form.lowercase()
    return EnglishVerb.subjects(formOf.tags).mapNotNull { (person, number) ->
        val phrase = verb.phrase(person, number, tense) ?: return@mapNotNull null
        val pronoun = if (tense == EnglishVerb.Tense.IMPERATIVE) null
        else InflectionBuilder.pronounFor(language, person, number, shown)
        val subject = pronoun?.let { if (it.endsWith("’")) it + shown else "$it $shown" } ?: shown
        WhoLine(subject, phrase)
    }
}

/** Whether a looked-up phrase has words before the verb ("se despierta"), so
 *  it can't be a command: those put their pronouns after it (¡despiértate!). */
fun LookupResult.isPrecededPhrase(): Boolean {
    val firstWord = query.split(Regex("\\s+")).firstOrNull { it.isNotEmpty() }?.lowercase()
    return query.contains(" ") && entries.any { it.word.lowercase() != firstWord }
}

/** Every reading of the looked-up word, for showing who does what without
 *  the full dictionary entry (on a flashcard). A verb's own infinitive reads
 *  "to wake up"; words that aren't verbs have none. `hint` is the word's
 *  gloss in its story ("wakes up"), to pick the English verb that matches it. */
fun LookupResult.whoLines(language: Language, hint: String? = null): List<WhoLine> {
    val lines = mutableListOf<WhoLine>()
    val preceded = isPrecededPhrase()
    for (formOf in entries.flatMap { it.formOf }) {
        if (preceded && "imperative" in formOf.tags) continue
        val verb = lemmas.firstOrNull { it.word == formOf.lemma }?.let { EnglishVerb.fromEntry(it, hint) }
        for (line in whoLines(formOf, query, language, verb)) if (line !in lines) lines.add(line)
    }
    if (lines.isEmpty()) {
        val verb = entries.firstNotNullOfOrNull { EnglishVerb.fromEntry(it, hint) }
        verb?.phrase("infinitive")?.let { lines.add(WhoLine("", it)) }
    }
    return lines
}
