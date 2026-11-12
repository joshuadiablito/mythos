package com.joshuadiablito.mythos.ui.components

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joshuadiablito.mythos.Mythos
import com.joshuadiablito.mythos.model.DeliverySlot
import com.joshuadiablito.mythos.model.Language
import com.joshuadiablito.mythos.model.Level
import com.joshuadiablito.mythos.ui.theme.Theme
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date

/** A language's monogram in a tinted circle: "Fr", "Ελ". */
@Composable
fun LanguageBadge(language: Language, size: Dp = 34.dp) {
    val c = Theme.colors
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(c.accent.copy(alpha = 0.12f))
            .semantics { contentDescription = language.englishName },
        contentAlignment = Alignment.Center,
    ) {
        Text(language.monogram, style = Theme.serif((size.value * 0.4f).sp, FontWeight.SemiBold), color = c.accent)
    }
}

/** "A1", in a small capsule. */
@Composable
fun LevelPill(level: Level, prominent: Boolean = false) {
    val c = Theme.colors
    Text(
        level.code,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        color = if (prominent) Color.White else c.accent,
        modifier = Modifier
            .background(if (prominent) c.accent else c.accent.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** An icon and a few words, small: "⏱ 3 min". */
@Composable
fun IconLabel(icon: ImageVector, text: String, color: Color, size: Dp = 13.dp, fontSize: Int = 12, weight: FontWeight = FontWeight.Normal) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(size))
        Text(text, fontSize = fontSize.sp, color = color, fontWeight = weight, maxLines = 1)
    }
}

/** The six CEFR levels; levels the language is already learned at are dimmed. */
@Composable
fun LevelPicker(selection: Level, taken: Set<Level> = emptySet(), onSelect: (Level) -> Unit) {
    val c = Theme.colors
    val haptics = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (level in Level.entries) {
            val enabled = level.isAvailable && level !in taken
            val selected = selection == level
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) c.accent.copy(alpha = 0.08f) else Color.Transparent)
                    .clickable(enabled = enabled, role = Role.RadioButton) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(level)
                    }
                    .semantics { this.selected = selected }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.width(36.dp)) { LevelPill(level, prominent = selected) }
                Column(Modifier.weight(1f)) {
                    Text(level.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink.copy(alpha = if (enabled) 1f else 0.5f))
                    Text(level.summary, fontSize = 12.sp, color = c.secondary.copy(alpha = if (enabled) 0.62f else 0.35f))
                }
                when {
                    level in taken -> Text("Learning", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = c.secondary,
                        modifier = Modifier.background(c.quaternary, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp))
                    selected -> Icon(Icons.Rounded.Check, contentDescription = null, tint = c.accent)
                }
            }
        }
    }
}

/** Morning / afternoon / evening, as three tiles: card-coloured on the page, paper-coloured inside a card. */
@Composable
fun SlotPicker(insideCard: Boolean = false) {
    val settings = Mythos.settings
    val c = Theme.colors
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for (slot in DeliverySlot.entries) {
            val selected = settings.slot == slot
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (insideCard) c.paper else c.card)
                    .border(BorderStroke(2.dp, if (selected) c.accent else Color.Transparent), RoundedCornerShape(20.dp))
                    .clickable(role = Role.RadioButton) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        settings.setSlot(slot)
                    }
                    .semantics { this.selected = selected }
                    .padding(vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val (icon, tint) = when (slot) {
                    DeliverySlot.MORNING -> Icons.Rounded.WbTwilight to Color(0xFFF2A20D)
                    DeliverySlot.AFTERNOON -> Icons.Rounded.WbSunny to Color(0xFFF2A20D)
                    DeliverySlot.EVENING -> Icons.Rounded.Bedtime to Color(0xFF6C5BC4)
                }
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
                Text(slot.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                Text(
                    formatTime(context, if (selected) settings.deliveryTime else LocalTime.of(slot.hour, slot.minute)),
                    fontSize = 12.sp, color = c.secondary,
                )
            }
        }
    }
}

/** A time as the phone shows times: 7:30 PM, or 19:30. */
fun formatTime(context: Context, time: LocalTime): String {
    val instant = java.time.LocalDate.now().atTime(time).atZone(ZoneId.systemDefault()).toInstant()
    return DateFormat.getTimeFormat(context).format(Date.from(instant))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(initial.hour, initial.minute, DateFormat.is24HourFormat(context))
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state) },
    )
}

/** Hear a word: a Wiktionary recording when there is one, otherwise the phone's voice. */
@Composable
fun ListenButton(text: String, language: Language, recording: String? = null, size: Dp = 44.dp) {
    val speaker = Mythos.speaker
    val speaking = speaker.isSpeaking(text)
    val c = Theme.colors
    FilledTonalIconButton(
        onClick = { if (speaking) speaker.stop() else speaker.play(recording, text, language) },
        modifier = Modifier.size(size),
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = c.accent.copy(alpha = 0.12f), contentColor = c.accent),
    ) {
        AnimatedContent(speaking, label = "listen") { on ->
            Icon(
                if (on) Icons.Rounded.Stop else Icons.Rounded.VolumeUp,
                contentDescription = if (on) "Stop" else if (recording != null) "Listen to a recording" else "Listen",
            )
        }
    }
}

/** A small heading over a group of cards: "MEANING". */
@Composable
fun SectionLabel(title: String) {
    Text(title.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Theme.colors.secondary, letterSpacing = 0.6.sp)
}

@Composable
fun Gap(height: Dp) = Spacer(Modifier.size(height))
