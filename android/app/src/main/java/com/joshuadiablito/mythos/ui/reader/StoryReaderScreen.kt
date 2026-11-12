package com.joshuadiablito.mythos.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joshuadiablito.mythos.Mythos
import com.joshuadiablito.mythos.model.Story
import com.joshuadiablito.mythos.ui.components.IconLabel
import com.joshuadiablito.mythos.ui.lookup.LookupSheet
import com.joshuadiablito.mythos.ui.lookup.WordLookup
import com.joshuadiablito.mythos.ui.theme.StoryCover
import com.joshuadiablito.mythos.ui.theme.Theme
import com.joshuadiablito.mythos.ui.theme.card

private const val FONT_SIZE_KEY = "storyFontSize"
private val coverHeight = 430.dp

@Composable
fun StoryReaderScreen(story: Story, onBack: () -> Unit) {
    val progress = Mythos.progress
    val speaker = Mythos.speaker
    val settings = Mythos.settings
    val c = Theme.colors
    val haptics = LocalHapticFeedback.current

    var fontSize by remember { mutableFloatStateOf(Mythos.prefs.getString(FONT_SIZE_KEY)?.toFloatOrNull() ?: 21f) }
    var selected by remember { mutableStateOf<WordTap?>(null) }
    var lookup by remember { mutableStateOf<WordLookup?>(null) }
    var showTranslation by rememberSaveable { mutableStateOf(false) }
    var showCompletion by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberScrollState()

    val isRead = progress.isRead(story)
    val isSpeaking = speaker.isSpeaking(story.id)
    val vocab = remember(story) { story.vocab.map { it.word } }

    // The story is read as `story.text`: paragraphs joined by a blank line.
    val paragraphStarts = remember(story) { story.paragraphs.runningFold(0) { at, p -> at + p.length + 2 } }
    fun spokenRange(index: Int): IntRange? {
        val range = speaker.spokenRange?.takeIf { isSpeaking } ?: return null
        val start = paragraphStarts[index]
        val end = start + story.paragraphs[index].length
        if (range.last < start || range.first >= end) return null
        return (maxOf(range.first, start) - start)..(minOf(range.last, end - 1) - start)
    }

    fun toggleListening() = when {
        !isSpeaking -> speaker.speak(story.text, story.language, settings.speechRate, story.id)
        speaker.isPaused -> speaker.resume()
        else -> speaker.pause()
    }

    // Leaving the story stops it being read.
    DisposableEffect(story.id) { onDispose { if (speaker.isSpeaking(story.id)) speaker.stop() } }
    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize().background(c.paper)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            CoverHeader(story)
            Column(
                Modifier
                    .offset(y = (-28).dp)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(c.paper)
                    .padding(start = 22.dp, end = 22.dp, top = 30.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy((fontSize * 0.9f).dp)) {
                    story.paragraphs.forEachIndexed { index, paragraph ->
                        TappableParagraph(paragraph, index, story.language, vocab, selected, spokenRange(index), fontSize) { tap ->
                            selected = tap
                            lookup = WordLookup(tap.word, story.language, story.gloss(tap.word), story.title)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = c.secondary, modifier = Modifier.size(16.dp))
                    Text("Tap any word for its meaning and every form. Words with an orange underline are new in this story.",
                        fontSize = 13.sp, color = c.secondary)
                }

                TranslationSection(story)

                VocabularySection(story) { item -> lookup = WordLookup(item.word, story.language, item.gloss, story.title) }

                Button(
                    onClick = {
                        progress.toggleRead(story)
                        if (!isRead) {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            showCompletion = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isRead) c.green else c.accent),
                ) {
                    Icon(if (isRead) Icons.Rounded.Verified else Icons.Rounded.CheckCircleOutline, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isRead) "Finished · tap to mark unread" else "I've read it", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        TopBar(
            scrolledPastCover = scroll.value > with(LocalDensity.current) { (coverHeight - 90.dp).toPx() },
            fraction = if (scroll.maxValue > 0) scroll.value.toFloat() / scroll.maxValue else 0f,
            fontSize = fontSize,
            onFontSize = { fontSize = it; Mythos.prefs.putString(FONT_SIZE_KEY, it.toString()) },
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        BottomBar(
            isSpeaking = isSpeaking,
            isPaused = speaker.isPaused,
            onListen = ::toggleListening,
            onStop = speaker::stop,
            onTranslate = { showTranslation = true },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    lookup?.let { item ->
        LookupSheet(item, onDismiss = { lookup = null; selected = null })
    }
    if (showTranslation) TranslateSheet(story.text, story.language, onDismiss = { showTranslation = false })
    if (showCompletion) CompletionSheet(story, onDismiss = { showCompletion = false })
}

/** The story's cover across the top, with its title set on it. */
@Composable
private fun CoverHeader(story: Story) {
    Box(Modifier.fillMaxWidth().height(coverHeight).semantics(mergeDescendants = true) { heading() }) {
        StoryCover(story, Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.BottomStart).padding(start = 22.dp, end = 22.dp, bottom = 50.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                val meta = Color.White.copy(alpha = 0.85f)
                Text("${story.language.endonym} · ${story.level.code} · Story ${story.number} ·", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = meta)
                IconLabel(Icons.Rounded.Schedule, "${story.readingMinutes} min", meta, weight = FontWeight.SemiBold)
            }
            Text(story.title, style = Theme.serif(40.sp, FontWeight.Bold), color = Color.White, lineHeight = 44.sp)
            Text(story.subtitle, fontSize = 20.sp, color = Color.White.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun TopBar(
    scrolledPastCover: Boolean,
    fraction: Float,
    fontSize: Float,
    onFontSize: (Float) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val c = Theme.colors
    val background by animateColorAsState(if (scrolledPastCover) c.paper else c.paper.copy(alpha = 0f), label = "bar")
    val tint by animateColorAsState(if (scrolledPastCover) c.ink else Color.White, label = "tint")
    val chip = if (scrolledPastCover) Color.Transparent else Color.Black.copy(alpha = 0.22f)
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().background(background).statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.background(chip, CircleShape)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = tint)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { ReadingProgress(fraction) }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.background(chip, CircleShape)) {
                Icon(Icons.Rounded.FormatSize, contentDescription = "Text size", tint = tint)
            }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                for ((label, size) in listOf("Small" to 18f, "Medium" to 21f, "Large" to 24f, "Extra large" to 28f)) {
                    DropdownMenuItem(
                        text = { Text(label, fontWeight = if (size == fontSize) FontWeight.Bold else FontWeight.Normal) },
                        onClick = { onFontSize(size); menu = false },
                    )
                }
            }
        }
    }
}

/** How far through the story the reader has scrolled, as a slim bar. */
@Composable
private fun ReadingProgress(fraction: Float) {
    val c = Theme.colors
    val visible by animateFloatAsState(if (fraction > 0.04f) 1f else 0f, tween(200), label = "progress")
    Box(
        Modifier
            .width(96.dp)
            .height(5.dp)
            .clip(CircleShape)
            .background(c.ink.copy(alpha = 0.12f * visible))
            .semantics { contentDescription = "Reading progress ${(fraction * 100).toInt()} percent" },
    ) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(5.dp).clip(CircleShape).background(c.accent.copy(alpha = visible)))
    }
}

@Composable
private fun BottomBar(isSpeaking: Boolean, isPaused: Boolean, onListen: () -> Unit, onStop: () -> Unit, onTranslate: () -> Unit, modifier: Modifier) {
    val c = Theme.colors
    Column(modifier.fillMaxWidth().background(c.card)) {
        HorizontalDivider(color = c.quaternary)
        Row(Modifier.navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onListen) {
                Icon(if (!isSpeaking || isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (!isSpeaking) "Listen" else if (isPaused) "Resume" else "Pause", fontSize = 16.sp)
            }
            AnimatedVisibility(isSpeaking) {
                TextButton(onClick = onStop) {
                    Icon(Icons.Rounded.Stop, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Stop", fontSize = 16.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onTranslate) {
                Icon(Icons.Rounded.Translate, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Translate", fontSize = 16.sp)
            }
        }
    }
}

/** The story's glossary, as chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VocabularySection(story: Story, onSelect: (com.joshuadiablito.mythos.model.VocabItem) -> Unit) {
    val c = Theme.colors
    Column(Modifier.fillMaxWidth().card(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("New words", style = Theme.serif(20.sp, FontWeight.Bold), color = c.ink)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (item in story.vocab) {
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(c.warm.copy(alpha = 0.12f))
                        .border(1.dp, c.warm.copy(alpha = 0.3f), CircleShape)
                        .clickable { onSelect(item) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .semantics(mergeDescendants = true) { contentDescription = "${item.word}, ${item.gloss}" },
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.word, style = Theme.serif(15.sp, FontWeight.SemiBold), color = c.ink)
                    Text(item.gloss, fontSize = 12.sp, color = c.secondary)
                }
            }
        }
    }
}
