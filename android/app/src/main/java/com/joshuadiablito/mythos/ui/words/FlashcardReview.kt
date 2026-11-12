package com.joshuadiablito.mythos.ui.words

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joshuadiablito.mythos.Mythos
import com.joshuadiablito.mythos.dictionary.WhoLine
import com.joshuadiablito.mythos.dictionary.whoLines
import com.joshuadiablito.mythos.model.Language
import com.joshuadiablito.mythos.model.SavedWord
import com.joshuadiablito.mythos.ui.components.ListenButton
import com.joshuadiablito.mythos.ui.theme.CoverPalette
import com.joshuadiablito.mythos.ui.theme.MeshBackground
import com.joshuadiablito.mythos.ui.theme.Theme
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Saved words one at a time: the word, then a tap turns the card over for
 *  its meaning. "Again" sends it to the back of the pile; "Got it" puts it away.
 *  Swipe right for "Got it", left for "Again". */
@Composable
fun FlashcardReview(language: Language, onClose: () -> Unit) {
    val progress = Mythos.progress
    var words by remember { mutableStateOf(progress.savedWords.filter { it.language == language }.shuffled()) }
    val total = remember { words.size }
    var known by remember { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    // Who does what in each word, from the dictionary: "él/ella se despierta → he/she wakes up".
    val readings = remember { mutableStateMapOf<String, List<WhoLine>>() }
    val haptics = LocalHapticFeedback.current

    // Look every word up as the review opens (from the disk cache when it can),
    // so turning a card over never waits.
    LaunchedEffect(Unit) {
        for (word in words) {
            if (word.id in readings) continue
            runCatching { Mythos.dictionary.lookUp(word.word, word.language) }.getOrNull()?.let {
                readings[word.id] = it.whoLines(word.language, word.meaning)
            }
        }
    }
    BackHandler(onBack = onClose)

    fun answer(knewIt: Boolean) {
        val word = words.firstOrNull() ?: return
        words = words.drop(1) + if (knewIt) emptyList() else listOf(word)
        if (knewIt) known++
        flipped = false
    }

    Box(Modifier.fillMaxSize()) {
        MeshBackground(
            listOf(CoverPalette.aegean.deep, CoverPalette.lavender.deep, CoverPalette.aegean.mid, CoverPalette.dusk.mid, CoverPalette.lavender.mid),
            Modifier.fillMaxSize(), animated = true,
        )
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = onClose,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.White.copy(alpha = 0.2f), contentColor = Color.White)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
                Spacer(Modifier.weight(1f))
                Text("$known of $total", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.85f))
            }
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else known.toFloat() / total },
                modifier = Modifier.fillMaxWidth(),
                color = Color.White, trackColor = Color.White.copy(alpha = 0.25f), drawStopIndicator = {},
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                AnimatedContent(words.firstOrNull(), transitionSpec = { (scaleIn(initialScale = 0.9f) + fadeIn()) togetherWith fadeOut() },
                    label = "card", contentKey = { it?.id }) { word ->
                    if (word != null) {
                        Card(word, readings[word.id], flipped,
                            onFlip = {
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                flipped = !flipped
                                if (flipped) Mythos.speaker.play(null, word.word, word.language)
                            },
                            onSwipe = { answer(it) })
                    } else {
                        Finished(language, total, onClose)
                    }
                }
            }
            if (words.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = { answer(false) }, modifier = Modifier.weight(1f).height(54.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White.copy(alpha = 0.12f), contentColor = Color.White),
                    border = null) {
                    Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Again", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
                Button(onClick = { answer(true) }, modifier = Modifier.weight(1f).height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.3f), contentColor = Color.White)) {
                    Icon(Icons.Rounded.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Got it", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun Card(word: SavedWord, lines: List<WhoLine>?, flipped: Boolean, onFlip: () -> Unit, onSwipe: (Boolean) -> Unit) {
    val c = Theme.colors
    val rotation by animateFloatAsState(if (flipped) 180f else 0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow), label = "flip")
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val threshold = with(density) { 120.dp.toPx() }
    val shape = RoundedCornerShape(32.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(340.dp)
            .graphicsLayer {
                translationX = drag.value
                rotationZ = drag.value / 22 / density.density
                rotationY = rotation
                cameraDistance = 12 * density.density
            }
            .shadow(30.dp, shape)
            .background(c.card, shape)
            .clickable(role = Role.Button, onClick = onFlip)
            .semantics { contentDescription = if (flipped) "Meaning of ${word.word}. Tap to show the word." else "${word.word}. Tap to show the meaning." }
            .pointerInput(word.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(drag.value) > threshold) onSwipe(drag.value > 0)
                        else scope.launch { drag.animateTo(0f, spring()) }
                    },
                    onHorizontalDrag = { _, amount -> scope.launch { drag.snapTo(drag.value + amount) } },
                )
            }
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (rotation <= 90f) {
            // Front: the word, as it appeared in the story.
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(word.word, style = Theme.serif(44.sp, FontWeight.Bold), color = c.ink, textAlign = TextAlign.Center)
                word.storyTitle?.let { Text("from “$it”", fontSize = 12.sp, color = c.secondary) }
                Text("Tap to turn over", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c.tertiary, modifier = Modifier.padding(top = 18.dp))
            }
        } else {
            // Back: who is doing it and what it means, drawn the right way round.
            Column(Modifier.graphicsLayer { rotationY = 180f }, horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (!lines.isNullOrEmpty()) {
                    for (line in lines.take(3)) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (line.subject.isNotEmpty()) Text(line.subject, style = Theme.serif(20.sp), color = c.secondary)
                        Text(line.english, fontSize = if (lines.size > 1) 24.sp else 30.sp, fontWeight = FontWeight.SemiBold,
                            color = c.ink, textAlign = TextAlign.Center)
                    }
                    word.meaning?.let { Text("In the story: $it", fontSize = 15.sp, color = c.secondary, textAlign = TextAlign.Center) }
                } else {
                    Text(word.word, style = Theme.serif(22.sp, FontWeight.SemiBold), color = c.secondary)
                    Text(word.meaning ?: "No meaning saved", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = c.ink, textAlign = TextAlign.Center)
                }
                ListenButton(word.word, word.language)
            }
        }
    }
}

@Composable
private fun Finished(language: Language, total: Int, onClose: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(80.dp).background(Color.White.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Verified, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
        }
        Text(language.praise, style = Theme.serif(34.sp, FontWeight.Bold), color = Color.White)
        Text("You went through all $total words.", color = Color.White.copy(alpha = 0.85f))
        Button(onClick = onClose, modifier = Modifier.padding(top = 10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.3f), contentColor = Color.White)) {
            Text("Done", fontSize = 17.sp)
        }
    }
}
