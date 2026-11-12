package com.joshuadiablito.mythos.ui.lookup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joshuadiablito.mythos.Mythos
import com.joshuadiablito.mythos.dictionary.EnglishVerb
import com.joshuadiablito.mythos.dictionary.Entry
import com.joshuadiablito.mythos.dictionary.FormOf
import com.joshuadiablito.mythos.dictionary.InflectionBuilder
import com.joshuadiablito.mythos.dictionary.LookupResult
import com.joshuadiablito.mythos.dictionary.isPrecededPhrase
import com.joshuadiablito.mythos.dictionary.whoLines
import com.joshuadiablito.mythos.model.Language
import com.joshuadiablito.mythos.ui.components.ListenButton
import com.joshuadiablito.mythos.ui.components.SectionLabel
import com.joshuadiablito.mythos.ui.reader.TranslateSheet
import com.joshuadiablito.mythos.ui.theme.Theme
import com.joshuadiablito.mythos.ui.theme.card
import java.net.URLEncoder

/** A word to look up, and where it came from. */
data class WordLookup(
    val word: String,
    val language: Language,
    /** Its meaning in the story, if it's in the story's glossary. */
    val gloss: String? = null,
    val storyTitle: String? = null,
)

/** The sheet shown when a word is tapped. It keeps its own stack, so a lemma
 *  can be opened and come back from. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LookupSheet(lookup: WordLookup, onDismiss: () -> Unit) {
    var stack by remember(lookup) { mutableStateOf(listOf(lookup)) }
    val c = Theme.colors
    val progress = Mythos.progress
    val haptics = LocalHapticFeedback.current
    val current = stack.last()
    var shortMeaning by remember(current) { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = c.paper,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (stack.size > 1) {
                IconButton(onClick = { stack = stack.dropLast(1) }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            } else {
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Close") }
            }
            Spacer(Modifier.weight(1f))
            val saved = progress.isSaved(current.word, current.language)
            TextButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                progress.toggleSaved(current.word, current.language, current.gloss ?: shortMeaning, current.storyTitle)
            }) {
                Icon(if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (saved) "Saved" else "Save")
            }
        }
        AnimatedContent(current, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "lookup") { page ->
            LookupPage(page, onOpen = { stack = stack + it }, onResult = { shortMeaning = it.shortMeaning })
        }
    }
}

@Composable
private fun LookupPage(lookup: WordLookup, onOpen: (WordLookup) -> Unit, onResult: (LookupResult) -> Unit) {
    var result by remember(lookup) { mutableStateOf<LookupResult?>(null) }
    var error by remember(lookup) { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var translate by remember { mutableStateOf(false) }
    LaunchedEffect(lookup, attempt) {
        error = null
        runCatching { Mythos.dictionary.lookUp(lookup.word, lookup.language) }
            .onSuccess { result = it; onResult(it) }
            .onFailure { error = it.message ?: "The dictionary couldn't be reached." }
    }
    val c = Theme.colors
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(lookup.word, style = Theme.serif(36.sp, FontWeight.Bold), color = c.ink)
                result?.ipa?.let { SelectionContainer { Text(it, fontSize = 16.sp, color = c.secondary) } }
            }
            ListenButton(lookup.word, lookup.language, result?.audioURL)
        }
        lookup.gloss?.let { StoryGloss(it) }
        val r = result
        when {
            r != null && r.isEmpty -> NotFound(lookup.word) { translate = true }
            r != null -> DictionaryContent(r, lookup.language, lookup.gloss, onOpen)
            error != null -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.WifiOff, contentDescription = null, tint = c.secondary, modifier = Modifier.size(36.dp))
                Text("Couldn't reach the dictionary", fontWeight = FontWeight.SemiBold, color = c.ink)
                Text(error!!, fontSize = 14.sp, color = c.secondary)
                FilledTonalButton(onClick = { attempt++ }) { Text("Try again") }
            }
            else -> LoadingRows()
        }
    }
    if (translate) TranslateSheet(lookup.word, lookup.language, onDismiss = { translate = false })
}

/** The word's meaning in the story it was tapped in. */
@Composable
private fun StoryGloss(gloss: String) {
    val c = Theme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.warm.copy(alpha = 0.12f)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.AutoStories, contentDescription = null, tint = c.warm)
        Column {
            Text("In this story", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.secondary)
            Text(gloss, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = c.ink)
        }
    }
}

@Composable
private fun NotFound(word: String, onTranslate: () -> Unit) {
    val c = Theme.colors
    Column(Modifier.fillMaxWidth().card(padding = 16.dp, radius = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.MenuBook, contentDescription = null, tint = c.ink)
            Text("Not in the dictionary", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
        }
        Text("Wiktionary has no entry for “$word”. It may be a name, or a form it doesn't list.", fontSize = 15.sp, color = c.secondary)
        FilledTonalButton(onClick = onTranslate) {
            Icon(Icons.Rounded.Translate, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Translate on device")
        }
    }
}

@Composable
private fun LoadingRows() {
    val c = Theme.colors
    val pulse by rememberInfiniteTransition(label = "loading")
        .animateFloat(0.4f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")
    Column(Modifier.alpha(pulse).semantics { contentDescription = "Looking up" }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (w in listOf(220, 300, 260, 180)) Box(Modifier.width(w.dp).height(14.dp).clip(RoundedCornerShape(6.dp)).background(c.quaternary))
    }
}

@Composable
private fun DictionaryContent(result: LookupResult, language: Language, hint: String?, onOpen: (WordLookup) -> Unit) {
    val c = Theme.colors
    val formOf = remember(result) {
        // As on flashcards: a phrase with a pronoun before the verb ("se despierta") isn't a command.
        val preceded = result.isPrecededPhrase()
        result.entries.flatMap { it.formOf }.filter { !(preceded && "imperative" in it.tags) }.distinctBy { "${it.lemma}|${it.description}" }
    }
    val withMeanings = result.all.filter { it.senses.isNotEmpty() }
    val withForms = remember(result) {
        result.all.distinctBy { it.id }.mapNotNull { e -> InflectionBuilder.build(e, language).takeIf { !it.isEmpty }?.let { e to it } }
    }
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (formOf.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (f in formOf.take(4)) {
                val english = result.lemmas.firstOrNull { it.word == f.lemma }?.let { EnglishVerb.fromEntry(it, hint) }
                FormOfCard(f, result.query, language, english) { onOpen(WordLookup(f.lemma, language)) }
            }
        }
        if (withMeanings.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("Meaning")
            for (entry in withMeanings) MeaningCard(entry)
        }
        if (withForms.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("Forms")
            for ((entry, inflections) in withForms) FormsView(entry, inflections, result.query, language)
        }
        val page = result.all.firstOrNull()?.word?.let { URLEncoder.encode(it, "UTF-8").replace("+", "%20") } ?: ""
        val linkStyle = TextLinkStyles(SpanStyle(color = c.accent))
        Text(buildAnnotatedString {
            append("Definitions and forms from ")
            withLink(LinkAnnotation.Url("https://en.wiktionary.org/wiki/$page", linkStyle)) { append("Wiktionary") }
            append(" via ")
            withLink(LinkAnnotation.Url("https://kaikki.org", linkStyle)) { append("kaikki.org") }
            append(", CC BY-SA 4.0.")
        }, fontSize = 11.sp, color = c.tertiary)
    }
}

/** What a form is, said three ways: who it is ("il/elle achète"), what that
 *  means ("he/she buys"), and the grammar, which opens the lemma. */
@Composable
private fun FormOfCard(formOf: FormOf, query: String, language: Language, english: EnglishVerb?, onOpen: () -> Unit) {
    val c = Theme.colors
    val lines = whoLines(formOf, query, language, english)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.accent.copy(alpha = 0.07f)).clickable(onClick = onOpen).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (lines.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (line in lines) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (line.subject.isNotEmpty()) {
                    Text(line.subject, style = Theme.serif(17.sp), color = c.ink)
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = c.tertiary, modifier = Modifier.size(14.dp))
                }
                Text(line.english, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = c.accent)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(buildAnnotatedString {
                append(formOf.description.replaceFirstChar { it.uppercase() } + " of ")
                withStyle(SpanStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, color = c.ink)) { append(formOf.lemma) }
            }, fontSize = 15.sp, color = c.secondary, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Open ${formOf.lemma}", tint = c.tertiary)
        }
    }
}

@Composable
private fun MeaningCard(entry: Entry) {
    val c = Theme.colors
    var showAll by remember { mutableStateOf(false) }
    val limit = 4
    Column(Modifier.fillMaxWidth().card(padding = 16.dp, radius = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(entry.word, style = Theme.serif(17.sp, FontWeight.SemiBold), color = c.ink)
            Text(entry.partOfSpeech, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.accent,
                modifier = Modifier.background(c.accent.copy(alpha = 0.1f), CircleShape).padding(horizontal = 7.dp, vertical = 2.dp))
        }
        entry.head?.let { Text(it, fontSize = 12.sp, color = c.secondary) }
        val senses = if (showAll) entry.senses else entry.senses.take(limit)
        senses.forEachIndexed { i, sense ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${i + 1}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.tertiary, modifier = Modifier.padding(top = 2.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(sense.gloss, fontSize = 16.sp, color = c.ink)
                    sense.example?.let { ex ->
                        Text(ex.text, style = Theme.serif(15.sp).copy(fontStyle = FontStyle.Italic), color = c.secondary)
                        ex.translation?.let { Text(it, fontSize = 12.sp, color = c.tertiary) }
                    }
                }
            }
        }
        if (entry.senses.size > limit) {
            Text(if (showAll) "Fewer meanings" else "All ${entry.senses.size} meanings", fontSize = 15.sp, fontWeight = FontWeight.Medium,
                color = c.accent, modifier = Modifier.clickable { showAll = !showAll }.padding(vertical = 4.dp))
        }
    }
}
