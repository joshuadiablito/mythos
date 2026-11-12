package com.joshuadiablito.mythos.ui.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joshuadiablito.mythos.model.Language
import com.joshuadiablito.mythos.ui.theme.Theme
import java.text.BreakIterator

/** A word tapped in a story. */
data class WordTap(val word: String, val paragraph: Int, val token: Int)

/** One word of a paragraph, by its [start, end) offsets. */
data class Token(val start: Int, val end: Int, val word: String, val index: Int)

// An elided article or pronoun on the front of a word: French j', l', qu'; Italian dell', un'.
private val elision = Regex("^(\\p{L}{1,4}['’])(\\p{L}.*)$")

/** The paragraph's words, as the language breaks them. Punctuation and spaces
 *  aren't words, and an elision is a word of its own ("j'" and "habite"), as
 *  Apple's tokenizer has it, so "habite" is what gets looked up. */
fun tokenize(text: String, language: Language): List<Token> {
    val it = BreakIterator.getWordInstance(language.locale)
    it.setText(text)
    val tokens = mutableListOf<Token>()
    var start = it.first()
    var end = it.next()
    while (end != BreakIterator.DONE) {
        val word = text.substring(start, end)
        if (word.any { c -> c.isLetter() }) {
            val split = elision.find(word)
            if (split != null) {
                val cut = start + split.groupValues[1].length
                tokens.add(Token(start, cut, split.groupValues[1], tokens.size))
                tokens.add(Token(cut, end, split.groupValues[2], tokens.size))
            } else {
                tokens.add(Token(start, end, word, tokens.size))
            }
        }
        start = end
        end = it.next()
    }
    return tokens
}

/** Where glossary words and phrases ("faire les courses") occur in the text, as whole words. */
fun vocabRanges(text: String, vocab: List<String>): List<IntRange> {
    val lower = text.lowercase()
    return vocab.flatMap { phrase ->
        val p = phrase.lowercase()
        val found = mutableListOf<IntRange>()
        var from = 0
        while (p.isNotEmpty()) {
            val at = lower.indexOf(p, from)
            if (at < 0) break
            val end = at + p.length
            // Whole words only: "lit" (bed) isn't in "lisait".
            val before = if (at == 0) ' ' else text[at - 1]
            val after = if (end >= text.length) ' ' else text[end]
            if (!before.isLetter() && !after.isLetter()) found.add(at until end)
            from = end
        }
        found
    }
}

/**
 * A paragraph whose every word can be tapped. It stays a single Text, so it
 * wraps and scales like any text. Glossary words get a dotted apricot
 * underline; the tapped word is highlighted, and so is the word being read aloud.
 */
@Composable
fun TappableParagraph(
    text: String,
    index: Int,
    language: Language,
    vocab: List<String>,
    selected: WordTap?,
    spoken: IntRange?,
    fontSize: Float,
    onTap: (WordTap) -> Unit,
) {
    val c = Theme.colors
    val tokens = remember(text, language) { tokenize(text, language) }
    val underlined = remember(text, vocab) { vocabRanges(text, vocab) }
    val annotated = buildAnnotatedString {
        append(text)
        for (t in tokens) {
            if (selected?.paragraph == index && selected.token == t.index) {
                addStyle(SpanStyle(background = c.accent.copy(alpha = 0.22f)), t.start, t.end)
            } else if (spoken != null && t.start <= spoken.last && spoken.first < t.end) {
                addStyle(SpanStyle(background = c.warm.copy(alpha = 0.3f)), t.start, t.end)
            }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val underline = c.warm
    Text(
        annotated,
        style = Theme.storyFont(fontSize.sp).copy(lineHeight = (fontSize * 1.5f).sp),
        color = c.ink,
        onTextLayout = { layout = it },
        modifier = Modifier
            .pointerInput(tokens) {
                detectTapGestures { position ->
                    val l = layout ?: return@detectTapGestures
                    val offset = l.getOffsetForPosition(position)
                    // Only when the tap is on the word itself, not the margin beside a line.
                    val line = l.getLineForOffset(offset)
                    if (position.x < l.getLineLeft(line) || position.x > l.getLineRight(line)) return@detectTapGestures
                    tokens.firstOrNull { offset >= it.start && offset < it.end }
                        ?.let { onTap(WordTap(it.word, index, it.index)) }
                }
            }
            .drawBehind {
                val l = layout ?: return@drawBehind
                val dots = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx()))
                // A glossary phrase may wrap: underline its part of each line.
                for (range in underlined) {
                    val end = range.last + 1
                    for (line in l.getLineForOffset(range.first)..l.getLineForOffset(range.last)) {
                        val from = maxOf(range.first, l.getLineStart(line))
                        val to = minOf(end, l.getLineEnd(line, visibleEnd = true))
                        if (to <= from) continue
                        val y = l.getLineBaseline(line) + 4.dp.toPx()
                        val x0 = l.getHorizontalPosition(from, true)
                        val x1 = if (to == l.getLineEnd(line, visibleEnd = true)) l.getLineRight(line) else l.getHorizontalPosition(to, true)
                        drawLine(underline, Offset(x0, y), Offset(x1, y), strokeWidth = 1.5.dp.toPx(), pathEffect = dots)
                    }
                }
            },
    )
}
