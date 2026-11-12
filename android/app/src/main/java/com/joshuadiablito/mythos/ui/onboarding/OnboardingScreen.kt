package com.joshuadiablito.mythos.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.joshuadiablito.mythos.Mythos
import com.joshuadiablito.mythos.model.Language
import com.joshuadiablito.mythos.services.NotificationScheduler
import com.joshuadiablito.mythos.ui.components.LanguageBadge
import com.joshuadiablito.mythos.ui.components.LevelPicker
import com.joshuadiablito.mythos.ui.components.NotificationStatus
import com.joshuadiablito.mythos.ui.components.SlotPicker
import com.joshuadiablito.mythos.ui.components.TimePickerDialog
import com.joshuadiablito.mythos.ui.components.formatTime
import com.joshuadiablito.mythos.ui.components.rememberNotificationPermission
import com.joshuadiablito.mythos.ui.theme.AppBackground
import com.joshuadiablito.mythos.ui.theme.CoverPalette
import com.joshuadiablito.mythos.ui.theme.StoryCover
import com.joshuadiablito.mythos.ui.theme.Theme
import com.joshuadiablito.mythos.ui.theme.card
import kotlinx.coroutines.delay

private enum class Step { WELCOME, LANGUAGES, LEVELS, TIME }

/** Welcome → languages → levels → delivery time. */
@Composable
fun OnboardingScreen() {
    val settings = Mythos.settings
    val context = LocalContext.current
    val permission = rememberNotificationPermission()
    var step by rememberSaveable { mutableStateOf(Step.WELCOME) }
    val canContinue = step != Step.LANGUAGES || settings.courses.isNotEmpty()

    fun finish() {
        val done = {
            settings.setOnboarded(true)
            NotificationScheduler.reschedule(context)
        }
        if (settings.notificationsEnabled && permission.status != NotificationStatus.ALLOWED) {
            permission.request { granted ->
                settings.setNotificationsOn(granted)
                done()
            }
        } else done()
    }

    BackHandler(enabled = step != Step.WELCOME) { step = Step.entries[step.ordinal - 1] }

    AppBackground {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (step != Step.WELCOME) {
                LinearProgressIndicator(
                    progress = { step.ordinal / (Step.entries.size - 1f) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 12.dp),
                    drawStopIndicator = {},
                )
            }
            AnimatedContent(
                step, modifier = Modifier.weight(1f),
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    (slideInHorizontally { if (forward) it else -it } + fadeIn()) togetherWith
                        (slideOutHorizontally { if (forward) -it else it } + fadeOut())
                },
                label = "step",
            ) { s ->
                when (s) {
                    Step.WELCOME -> WelcomeStep()
                    Step.LANGUAGES -> LanguagesStep()
                    Step.LEVELS -> LevelsStep()
                    Step.TIME -> TimeStep()
                }
            }
            Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (step != Step.WELCOME) {
                    FilledTonalIconButton(onClick = { step = Step.entries[step.ordinal - 1] }, modifier = Modifier.size(54.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
                Button(
                    onClick = { if (step == Step.TIME) finish() else step = Step.entries[step.ordinal + 1] },
                    enabled = canContinue,
                    modifier = Modifier.weight(1f).height(54.dp),
                ) {
                    Text(when (step) { Step.WELCOME -> "Get started"; Step.TIME -> "Start reading"; else -> "Continue" },
                        fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun StepHeader(title: String, subtitle: String) {
    val c = Theme.colors
    Column(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = Theme.serif(34.sp, FontWeight.Bold), color = c.ink)
        Text(subtitle, fontSize = 17.sp, color = c.secondary)
    }
}

/** Three first stories, in three languages, fanned out like books on a table. */
@Composable
private fun WelcomeStep() {
    val c = Theme.colors
    val covers = listOf("fr-a1-04", "el-a1-03", "es-a2-01").mapNotNull { Mythos.catalog.story(it) }
    var greeting by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1800); greeting = (greeting + 1) % Language.entries.size } }
    val float by rememberInfiniteTransition(label = "float")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(3000), RepeatMode.Reverse), label = "float")

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(30.dp, Alignment.CenterVertically)) {
        Box(Modifier.fillMaxWidth().height(250.dp).padding(top = 20.dp), contentAlignment = Alignment.Center) {
            covers.forEachIndexed { index, story ->
                val front = index == 1
                Box(
                    Modifier
                        .zIndex(if (front) 1f else 0f)
                        .offset(x = ((index - 1) * 84).dp, y = (if (front) -14 else 10).dp + (if (front) -6 else 4).dp * float)
                        .graphicsLayer { transformOrigin = TransformOrigin(0.5f, 1f); rotationZ = (index - 1) * 11f }
                        .scale(if (front) 1f else 0.9f)
                        .size(150.dp, 200.dp)
                        .shadow(18.dp, RoundedCornerShape(20.dp), ambientColor = CoverPalette.forTopic(story.topic).deep,
                            spotColor = CoverPalette.forTopic(story.topic).deep)
                        .clip(RoundedCornerShape(20.dp)),
                ) {
                    StoryCover(story, Modifier.fillMaxSize(), symbolScale = 1.1f)
                    // Only the front cover carries a title; the others are half hidden.
                    if (front) Text(story.title, style = Theme.serif(15.sp, FontWeight.Bold), color = Color.White,
                        modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Mythos", style = Theme.serif(52.sp, FontWeight.Bold), color = c.ink)
            AnimatedContent(greeting, transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(600)) }, label = "greeting") { i ->
                Text(Language.entries[i].greeting, style = Theme.serif(24.sp).copy(fontStyle = FontStyle.Italic), color = c.accent)
            }
            Text("A short story every day, in the language you're learning, at your level. Tap any word for its meaning and every form it takes.",
                color = c.secondary, textAlign = TextAlign.Center, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp))
        }
    }
}

@Composable
private fun LanguagesStep() {
    val settings = Mythos.settings
    val c = Theme.colors
    val haptics = LocalHapticFeedback.current
    LazyVerticalGrid(
        GridCells.Fixed(2), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(2) }) { StepHeader("What are you learning?", "Choose one or more. Each gets its own story every day.") }
        items(Language.entries) { language ->
            val selected = settings.isLearning(language)
            Column(
                Modifier
                    .heightIn(min = 120.dp)
                    .card(padding = 0.dp, radius = 22.dp)
                    .border(BorderStroke(2.dp, if (selected) c.accent else Color.Transparent), RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(role = Role.Checkbox) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        settings.toggle(language)
                    }
                    .semantics { this.selected = selected }
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LanguageBadge(language, 40.dp)
                    Spacer(Modifier.weight(1f))
                    Icon(if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, contentDescription = null,
                        tint = if (selected) c.accent else c.secondary.copy(alpha = 0.5f))
                }
                Spacer(Modifier.weight(1f, fill = false))
                Column {
                    Text(language.endonym, style = Theme.serif(19.sp, FontWeight.SemiBold), color = c.ink)
                    Text(language.englishName, fontSize = 14.sp, color = c.secondary)
                }
            }
        }
        item(span = { GridItemSpan(2) }) { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun LevelsStep() {
    val settings = Mythos.settings
    val c = Theme.colors
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        StepHeader("Your level", "Stories are written for each level of the Common European Framework, from A1 to C2.")
        for (course in settings.courses) {
            Column(Modifier.fillMaxWidth().card(padding = 16.dp, radius = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LanguageBadge(course.language)
                    Text(course.language.endonym, style = Theme.serif(20.sp, FontWeight.SemiBold), color = c.ink)
                }
                LevelPicker(course.level) { settings.setLevel(it, course.id) }
            }
        }
    }
}

@Composable
private fun TimeStep() {
    val settings = Mythos.settings
    val c = Theme.colors
    val context = LocalContext.current
    var picking by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StepHeader("When should it arrive?", "A notification brings each day's story. It's scheduled on your phone; nothing is sent to a server.")
        SlotPicker()
        Row(Modifier.fillMaxWidth().card(padding = 16.dp, radius = 20.dp).clickable { picking = true },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Schedule, contentDescription = null, tint = c.ink)
            Text("Exact time", fontSize = 17.sp, color = c.ink, modifier = Modifier.weight(1f))
            Text(formatTime(context, settings.deliveryTime), fontSize = 17.sp, color = c.accent, fontWeight = FontWeight.Medium)
        }
        Row(Modifier.fillMaxWidth().card(padding = 16.dp, radius = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Daily notification", fontSize = 17.sp, color = c.ink, modifier = Modifier.weight(1f))
            Switch(settings.notificationsEnabled, settings::setNotificationsOn)
        }
    }
    if (picking) TimePickerDialog(settings.deliveryTime, onDismiss = { picking = false }) {
        settings.setTime(it.hour, it.minute)
        picking = false
    }
}
