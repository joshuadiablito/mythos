package com.joshuadiablito.mythos.model

/**
 * Splits a paragraph into sentences, for showing each beside its translation.
 * A port of the iOS `SentenceSplitter`: the catalog's English translations are
 * written one per sentence *as that splitter splits them*, so this must agree
 * with it exactly. It never splits inside quotation marks, and a sentence ends
 * after . ! ? … (and Greek's question mark, ;) plus any closing quotes, when
 * what follows starts a new sentence.
 */
object SentenceSplitter {
    fun split(text: String, languageCode: String): List<String> {
        val chars = text.toCharArray()
        val terminators = mutableSetOf('.', '!', '?', '…')
        if (languageCode == "el") terminators.add(';')
        // German quotes „like this“; the others «like this» or “like this”.
        val opening = if (languageCode == "de") setOf('„', '«', '‹') else setOf('«', '“', '„', '‹')
        val closing = if (languageCode == "de") setOf('“', '»', '›') else setOf('»', '”', '›')

        // A dash starts a new line of dialogue, unless it introduces a speech
        // tag: "— Quanto é? — pergunta ela." is one sentence.
        fun startsSentence(index: Int): Boolean {
            val c = chars[index]
            if (c == '—' || c == '–') {
                for (j in index + 1 until chars.size) {
                    if (chars[j].isLetter()) return chars[j].isUpperCase()
                }
                return false
            }
            return c.isUpperCase() || c in opening || c == '¿' || c == '¡' || c == '"'
        }

        val sentences = mutableListOf<String>()
        var start = 0
        var depth = 0
        var i = 0
        while (i < chars.size) {
            val c = chars[i]
            if (c in opening) depth += 1
            else if (c in closing) depth = maxOf(0, depth - 1)

            if (c in terminators) {
                // Take repeated punctuation ("?!", "...") and closing quotes, with any spaces between ("? »").
                var end = i + 1
                var closingDepth = depth
                while (end < chars.size) {
                    val n = chars[end]
                    if (n in terminators) {
                        end += 1
                    } else if (n in closing) {
                        closingDepth = maxOf(0, closingDepth - 1); end += 1
                    } else if ((n == ' ' || n == ' ') && end + 1 < chars.size &&
                        (chars[end + 1] in closing || chars[end + 1] in terminators)
                    ) {
                        end += 1
                    } else {
                        break
                    }
                }
                var next = end
                while (next < chars.size && chars[next].isWhitespace()) next += 1
                if (closingDepth == 0 && (next == chars.size || (next > end && startsSentence(next)))) {
                    sentences.add(String(chars, start, end - start).trimSpaces())
                    depth = 0
                    start = next
                    i = next
                    continue
                }
                depth = closingDepth
                i = end
                continue
            }
            i += 1
        }
        val from = minOf(start, chars.size)
        val rest = String(chars, from, chars.size - from).trimSpaces()
        if (rest.isNotEmpty()) sentences.add(rest)
        return sentences
    }

    /** Swift's `.trimmingCharacters(in: .whitespaces)`: spaces and tabs, not newlines. */
    private fun String.trimSpaces(): String = trim { it == '\t' || Character.getType(it) == Character.SPACE_SEPARATOR.toInt() }
}
