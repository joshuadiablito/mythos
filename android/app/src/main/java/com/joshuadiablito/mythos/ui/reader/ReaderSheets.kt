package com.joshuadiablito.mythos.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joshuadiablito.mythos.Mythos
import com.joshuadiablito.mythos.model.Language
import com.joshuadiablito.mythos.model.SentencePair
import com.joshuadiablito.mythos.model.Story
import com.joshuadiablito.mythos.services.OnDeviceTranslator
import com.joshuadiablito.mythos.ui.theme.CoverPalette
import com.joshuadiablito.mythos.ui.theme.StoryCover
import com.joshuadiablito.mythos.ui.theme.Theme
import com.joshuadiablito.mythos.ui.theme.card
import kotlin.math.cos
import kotlin.math.sin

private const val SHOW_TRANSLATION_KEY = "showTranslation"

/** The story again, sentence by sentence, each with its English underneath.
 *  Tap a sentence to hear it. It can be folded away, to try the story alone first. */
@Composable
fun TranslationSection(story: Story) {
    val pairs = story.sentencePairs
    if (pairs.isEmpty()) return
    val c = Theme.colors
    val speaker = Mythos.speaker
    var expanded by remember { mutableStateOf(Mythos.prefs.getString(SHOW_TRANSLATION_KEY) != "false") }
    Column(Modifier.fillMaxWidth().card()) {
        Row(
            Modifier.fillMaxWidth().clickable {
                expanded = !expanded
                Mythos.prefs.putString(SHOW_TRANSLATION_KEY, expanded.toString())
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = null, tint = c.accent)
            Spacer(Modifier.width(8.dp))
            Text("English translation", style = Theme.serif(20.sp, FontWeight.Bold), color = c.ink, modifier = Modifier.weight(1f))
            Text(if (expanded) "Hide" else "Show", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.accent)
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                pairs.forEachIndexed { p, paragraph ->
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        paragraph.forEachIndexed { s, pair ->
                            val id = "${story.id}#$p.$s"
                            SentencePairRow(pair, speaker.isSpeaking(id)) {
                                if (speaker.isSpeaking(id)) speaker.stop()
                                else speaker.speak(pair.original, story.language, Mythos.settings.speechRate, id)
                            }
                        }
                    }
                    if (p < pairs.size - 1) HorizontalDivider(color = c.quaternary)
                }
            }
        }
    }
}

@Composable
private fun SentencePairRow(pair: SentencePair, isSpeaking: Boolean, onTap: () -> Unit) {
    val c = Theme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSpeaking) c.warm.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(onClick = onTap)
            .semantics(mergeDescendants = true) { contentDescription = "${pair.original}. ${pair.translation}" }
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null,
            tint = if (isSpeaking) c.warm else c.secondary.copy(alpha = 0.5f), modifier = Modifier.padding(top = 4.dp).size(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(pair.original, style = Theme.storyFont(18.sp), color = c.ink)
            Text(pair.translation, fontSize = 15.sp, color = c.secondary)
        }
    }
}

/** Shown on finishing a story: the streak, and the story's new words to keep. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompletionSheet(story: Story, onDismiss: () -> Unit) {
    val progress = Mythos.progress
    val c = Theme.colors
    val palette = CoverPalette.forTopic(story.topic)
    val unsaved = story.vocab.filter { !progress.isSaved(it.word, story.language) }
    val streak = progress.streak()
    val burst = remember { Animatable(0f) }
    val pop = remember { Animatable(0.8f) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }
    LaunchedEffect(Unit) { burst.animateTo(1f, tween(1100, delayMillis = 150)) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.paper) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Box(Modifier.padding(top = 12.dp).height(170.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                // A burst of sparkles from behind the cover.
                for (i in 0 until 12) {
                    val angle = i / 12.0 * 2 * Math.PI
                    val icon = listOf(Icons.Rounded.AutoAwesome, Icons.Rounded.Star, Icons.Rounded.Star)[i % 3]
                    Icon(icon, contentDescription = null, tint = listOf(palette.accent, c.warm, c.accent)[i % 3],
                        modifier = Modifier
                            .offset((cos(angle) * 110 * burst.value).dp, (sin(angle) * 80 * burst.value).dp)
                            .size((8 + (i * 5) % 10).dp)
                            .alpha(1 - burst.value))
                }
                Box(Modifier.size(112.dp, 148.dp).scale(pop.value)) {
                    StoryCover(story, Modifier.fillMaxWidth().height(148.dp).clip(RoundedCornerShape(18.dp)), symbolScale = 0.9f)
                    Box(Modifier.align(Alignment.BottomEnd).offset(12.dp, 12.dp).size(38.dp).background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Verified, contentDescription = null, tint = c.green, modifier = Modifier.size(34.dp))
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(story.language.praise, style = Theme.serif(34.sp, FontWeight.Bold), color = c.ink)
                Text("You finished “${story.title}”.", color = c.secondary, textAlign = TextAlign.Center)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat("$streak", if (streak == 1) "day in a row" else "days in a row", Icons.Rounded.LocalFireDepartment, Color(0xFFF2545B))
                Stat("${story.vocab.size}", "new words", Icons.Rounded.AutoAwesome, c.warm)
                Stat("${story.wordCount}", "words read", Icons.Rounded.Notes, c.accent)
            }
            if (unsaved.isNotEmpty()) {
                Button(
                    onClick = { progress.saveAll(story.vocab, story.language, story.title) },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Icon(Icons.Rounded.Bookmark, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save ${unsaved.size} new words to review", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = c.green)
                    Spacer(Modifier.width(6.dp))
                    Text("Words saved to your list", fontWeight = FontWeight.SemiBold, color = c.green)
                }
            }
            FilledTonalButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) { Text("Done", fontSize = 16.sp) }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Stat(value: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    val c = Theme.colors
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(c.ink.copy(alpha = 0.05f)).padding(vertical = 14.dp)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.ink)
        Text(label, fontSize = 11.sp, color = c.secondary, textAlign = TextAlign.Center)
    }
}

/** Machine translation on the phone (ML Kit), for the whole story or one word. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateSheet(text: String, language: Language, onDismiss: () -> Unit) {
    val c = Theme.colors
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(text) {
        runCatching { OnDeviceTranslator.toEnglish(text, language) }
            .onSuccess { result = it }
            .onFailure { error = it.message ?: "Translation isn't available right now." }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.paper) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("${language.englishName} → English", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.secondary)
            Text(text, style = Theme.storyFont(17.sp), color = c.secondary)
            HorizontalDivider(color = c.quaternary)
            when {
                result != null -> Text(result!!, fontSize = 18.sp, color = c.ink)
                error != null -> Text(error!!, color = c.secondary)
                else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Translating on this phone. The first time, its ${language.englishName} model downloads (about 30 MB).",
                        fontSize = 14.sp, color = c.secondary)
                }
            }
            Text("Translated on device by Google ML Kit; the text doesn't leave your phone.", fontSize = 11.sp, color = c.tertiary)
        }
    }
}
