package com.joshuadiablito.mythos.dictionary

object LookupCandidates {
    // Elided articles and pronouns: French l', d', qu', j'…; Italian dell', un'….
    private val elision = Regex("^(\\p{L}{1,4})'(\\p{L}.*)$")

    /** Spellings to try, most likely first. Lookups are case- and
     *  apostrophe-sensitive: "Mange" at the start of a sentence is "mange",
     *  German nouns are capitalised, and Wiktionary titles use a straight apostrophe. */
    fun candidates(text: String): List<String> {
        val word = text.trim().replace("’", "'").replace(Regex("\\s+"), " ")
        if (word.isEmpty()) return emptyList()
        val out = mutableListOf(word)
        elision.find(word)?.let { out.add(it.groupValues[2]) }
        for (w in out.toList()) {
            out.add(w.lowercase())
            out.add(w.take(1).uppercase() + w.drop(1))
        }
        return out.distinct()
    }

    /** A phrase's words, longest first, for looking it up by its main word:
     *  "se despierta" → "despierta". Single words have none. */
    fun mainWords(text: String): List<String> {
        val all = text.split(Regex("\\s+")).filter { it.isNotEmpty() }.map { it.trim(::isPunctuation) }
        if (all.size <= 1) return emptyList()
        // Clitics and articles ("se", "le", "a") are never the word to look up.
        val words = all.filter { it.codePointCount(0, it.length) > 2 }
        return words.withIndex()
            .sortedWith(compareByDescending<IndexedValue<String>> { it.value.codePointCount(0, it.value.length) }.thenBy { it.index })
            .map { it.value }
    }

    private fun isPunctuation(c: Char): Boolean = when (Character.getType(c).toByte()) {
        Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
        Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
        Character.OTHER_PUNCTUATION -> true
        else -> false
    }
}
