package com.joshuadiablito.mythos

import com.joshuadiablito.mythos.model.Language
import com.joshuadiablito.mythos.services.Speaker
import com.joshuadiablito.mythos.ui.reader.tokenize
import com.joshuadiablito.mythos.ui.reader.vocabRanges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTest {
    @Test fun elisionsAreWordsOfTheirOwn() {
        // "habite" is the glossary word and what gets looked up, as on iOS: not "j'habite".
        val words = tokenize("J'ai vingt-cinq ans et j'habite à Lyon. L’école est là.", Language.FRENCH).map { it.word }
        // The words looked up. (How "L’" itself is split differs between the JVM's and Android's word breakers.)
        assertTrue(words.toString(), "habite" in words && "j'" in words && "école" in words && "j'habite" !in words)
        assertTrue(words.none { it.contains(" ") || it.all { c -> !c.isLetter() } })
    }

    @Test fun tokensKnowWhereTheyAre() {
        val text = "Με λένε Ελένη. Είμαι νοσηλεύτρια."
        for (t in tokenize(text, Language.GREEK)) assertEquals(t.word, text.substring(t.start, t.end))
    }

    @Test fun glossaryWordsMatchWholeWordsOnly() {
        // "lit" (bed) isn't in "lisait"; phrases are found too.
        assertTrue(vocabRanges("Il lisait au lit.", listOf("lit")).single().let { it == 13..15 })
        assertEquals(1, vocabRanges("Je vais faire les courses demain.", listOf("faire les courses")).size)
    }

    @Test fun speechIsQueuedAParagraphAtATimeFromWhereItStopped() {
        val text = "Bonjour.\n\nUn deux trois."
        assertEquals(listOf(0 to "Bonjour.", 10 to "Un deux trois."), Speaker.chunk(text, 0))
        // Resuming from "deux" starts there, with its offset in the whole text.
        assertEquals(listOf(13 to "deux trois."), Speaker.chunk(text, 13))
        // A very long paragraph is cut at a sentence end, and nothing is lost.
        val long = (1..400).joinToString(" ") { "Phrase numéro $it." }
        val pieces = Speaker.chunk(long, 0)
        assertTrue(pieces.size > 1 && pieces.all { it.second.length <= 3000 })
        assertEquals(long.replace(" ", ""), pieces.joinToString("") { it.second }.replace(" ", ""))
    }
}
