package com.joshuadiablito.mythos.ui.settings

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.joshuadiablito.mythos.BuildConfig
import com.joshuadiablito.mythos.Mythos
import com.joshuadiablito.mythos.model.Course
import com.joshuadiablito.mythos.model.Language
import com.joshuadiablito.mythos.services.NotificationScheduler
import com.joshuadiablito.mythos.ui.components.LanguageBadge
import com.joshuadiablito.mythos.ui.components.NotificationStatus
import com.joshuadiablito.mythos.ui.components.LevelPicker
import com.joshuadiablito.mythos.ui.components.LevelPill
import com.joshuadiablito.mythos.ui.components.SectionLabel
import com.joshuadiablito.mythos.ui.components.SlotPicker
import com.joshuadiablito.mythos.ui.components.TimePickerDialog
import com.joshuadiablito.mythos.ui.components.formatTime
import com.joshuadiablito.mythos.ui.components.rememberNotificationPermission
import com.joshuadiablito.mythos.ui.theme.AppBackground
import com.joshuadiablito.mythos.ui.theme.Theme
import com.joshuadiablito.mythos.ui.theme.card
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val settings = Mythos.settings
    val c = Theme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val permission = rememberNotificationPermission()
    var editing by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf<String?>(null) } // a course ID, or "" for the default
    var previewSent by remember { mutableStateOf(false) }
    var blocked by remember { mutableStateOf(false) }
    var cacheBytes by remember { mutableLongStateOf(0L) }
    var exactAlarms by remember { mutableStateOf(NotificationScheduler.exactAlarmsAllowed(context)) }
    // Back from Android's settings: look again.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { exactAlarms = NotificationScheduler.exactAlarmsAllowed(context) }

    LaunchedEffect(Unit) { cacheBytes = Mythos.dictionary.cacheSize() }
    // Any change to what arrives, or when: set the alarms again.
    LaunchedEffect(settings.courses, settings.hour, settings.minute, settings.notificationsEnabled) {
        NotificationScheduler.reschedule(context)
    }

    AppBackground {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Settings", style = Theme.serif(34.sp, FontWeight.Bold), color = c.ink, modifier = Modifier.padding(bottom = 8.dp))

            Section("Learning", footer = "Learn a language at two levels to get a story from each every day. Changing a level starts it from its first story.") {
                for (course in settings.courses) {
                    Row(Modifier.fillMaxWidth().clickable { editing = course.id }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LanguageBadge(course.language, 30.dp)
                        Text(course.language.endonym, fontSize = 17.sp, color = c.ink, modifier = Modifier.weight(1f))
                        LevelPill(course.level)
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.tertiary)
                    }
                    HorizontalDivider(color = c.quaternary)
                }
                val canAdd = Language.entries.any { settings.levelsToAdd(it).isNotEmpty() }
                Row(Modifier.fillMaxWidth().clickable(enabled = canAdd) { adding = true }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = c.accent)
                    Text("Add a language or level", fontSize = 17.sp, color = c.accent)
                }
            }

            Section("Daily story", footer = "Each course's story can arrive at its own time. Notifications are scheduled on this phone; nothing is sent to a server.") {
                SwitchRow("Daily notification", settings.notificationsEnabled) { on ->
                    settings.setNotificationsOn(on)
                    if (on && permission.status != NotificationStatus.ALLOWED) permission.request()
                }
                if (settings.notificationsEnabled) {
                    Spacer(Modifier.padding(4.dp))
                    SlotPicker(insideCard = true)
                    TimeRow(if (settings.courses.size > 1) "Default time" else "Time", null, formatTime(context, settings.deliveryTime)) { pickingTime = "" }
                    if (settings.courses.size > 1) for (course in settings.courses) CourseTimeRow(course) { pickingTime = course.id }
                    if (!exactAlarms) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Arrive on the minute", fontSize = 17.sp, color = c.ink)
                                Text("Android may deliver the story up to an hour late unless Mythos may set alarms.",
                                    fontSize = 12.sp, color = c.secondary)
                            }
                            TextButton(onClick = { context.startActivity(NotificationScheduler.exactAlarmSettings(context)) }) { Text("Allow") }
                        }
                    }
                    TextButton(
                        enabled = !previewSent,
                        onClick = {
                            when (NotificationScheduler.sendPreview(context)) {
                                NotificationScheduler.PreviewResult.SENT -> scope.launch {
                                    previewSent = true
                                    delay(6000)
                                    previewSent = false
                                }
                                NotificationScheduler.PreviewResult.BLOCKED ->
                                    if (permission.status == NotificationStatus.NOT_ASKED) permission.request() else blocked = true
                                NotificationScheduler.PreviewResult.NOTHING_TO_SEND -> {}
                            }
                        },
                    ) {
                        Icon(Icons.Rounded.NotificationsActive, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (previewSent) "Arriving in 5 seconds…" else "Send a preview")
                    }
                }
            }

            Section("Listening") {
                Text("Reading speed", fontSize = 17.sp, color = c.ink)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Slower", fontSize = 13.sp, color = c.secondary)
                    Slider(settings.speechRate.toFloat(), { settings.setReadingSpeed(it.toDouble()) }, valueRange = 0.3f..0.55f, modifier = Modifier.weight(1f))
                    Text("Faster", fontSize = 13.sp, color = c.secondary)
                }
            }

            Section("About") {
                InfoRow("Stories", "${Mythos.catalog.stories.size}, from A1 to C2")
                InfoRow("Dictionary", "Wiktionary via kaikki.org", Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://kaikki.org")))
                })
                InfoRow("Translation", "On device, by ML Kit")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InfoRow("Saved lookups", Formatter.formatShortFileSize(context, cacheBytes), Modifier.weight(1f))
                    TextButton(enabled = cacheBytes > 0, onClick = {
                        scope.launch {
                            Mythos.dictionary.clearCache()
                            cacheBytes = Mythos.dictionary.cacheSize()
                        }
                    }) { Text("Clear") }
                }
                InfoRow("Version", BuildConfig.VERSION_NAME)
            }
        }
    }

    editing?.let { id -> LevelSheet(id) { editing = null } }
    if (adding) AddCourseDialog { adding = false }
    pickingTime?.let { id ->
        val initial = if (id.isEmpty()) settings.deliveryTime else settings.deliveryTime(id)
        TimePickerDialog(initial, onDismiss = { pickingTime = null }) { time ->
            if (id.isEmpty()) settings.setTime(time.hour, time.minute) else settings.setDeliveryTime(time, id)
            pickingTime = null
        }
    }
    if (blocked) AlertDialog(
        onDismissRequest = { blocked = false },
        title = { Text("Notifications are off for Mythos") },
        text = { Text("Turn on notifications for Mythos in Settings to get each day's story.") },
        confirmButton = { TextButton(onClick = { blocked = false; permission.openSettings() }) { Text("Open Settings") } },
        dismissButton = { TextButton(onClick = { blocked = false }) { Text("Not now") } },
    )
}

@Composable
private fun Section(title: String, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = Theme.colors
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(horizontal = 4.dp)) { SectionLabel(title) }
        Column(Modifier.fillMaxWidth().card(padding = 16.dp, radius = 20.dp), content = content)
        footer?.let { Text(it, fontSize = 13.sp, color = c.secondary, modifier = Modifier.padding(horizontal = 4.dp)) }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 17.sp, color = Theme.colors.ink, modifier = Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
private fun TimeRow(title: String, subtitle: String?, value: String, leading: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val c = Theme.colors
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, color = c.ink)
            subtitle?.let { Text(it, fontSize = 12.sp, color = c.secondary) }
        }
        Text(value, fontSize = 17.sp, color = c.accent, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 4.dp))
    }
}

/** One course's delivery time: the default until it's changed, then its own. */
@Composable
private fun CourseTimeRow(course: Course, onPick: () -> Unit) {
    val settings = Mythos.settings
    val context = LocalContext.current
    val own = settings.hasOwnTime(course.id)
    val name = if (settings.hasSeveralLevels(course.language)) "${course.language.endonym} ${course.level.code}" else course.language.endonym
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f)) {
            TimeRow(name, if (own) "Its own time" else "Same as default", formatTime(context, settings.time(course)),
                leading = { LanguageBadge(course.language, 28.dp) }, onClick = onPick)
        }
        if (own) TextButton(onClick = { settings.useSharedTime(course.id) }) { Text("Use default", fontSize = 13.sp) }
    }
}

@Composable
private fun InfoRow(title: String, value: String, modifier: Modifier = Modifier) {
    val c = Theme.colors
    Row(modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 17.sp, color = c.ink, modifier = Modifier.weight(1f))
        Text(value, fontSize = 15.sp, color = c.secondary)
    }
}

/** A course's level, and a way to stop learning it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LevelSheet(courseID: String, onDismiss: () -> Unit) {
    val settings = Mythos.settings
    val course = settings.course(courseID) ?: return onDismiss()
    val c = Theme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.paper) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(course.language.endonym, style = Theme.serif(24.sp, FontWeight.Bold), color = c.ink)
            LevelPicker(course.level, taken = settings.courses.filter { it.language == course.language && it.id != courseID }.map { it.level }.toSet()) {
                settings.setLevel(it, courseID)
            }
            OutlinedButton(onClick = { settings.remove(courseID); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                Text("Stop learning ${course.language.endonym} ${course.level.code}", color = Color(0xFFD9443A))
            }
        }
    }
}

/** Every language and the levels it could still be added at: Spanish A2 alongside A1. */
@Composable
private fun AddCourseDialog(onDismiss: () -> Unit) {
    val settings = Mythos.settings
    val c = Theme.colors
    var language by remember { mutableStateOf<Language?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(language?.let { "${it.endonym}: which level?" } ?: "Add a language") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val chosen = language
                if (chosen == null) {
                    for (l in Language.entries.filter { settings.levelsToAdd(it).isNotEmpty() }) {
                        Row(Modifier.fillMaxWidth().clickable { language = l }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LanguageBadge(l, 30.dp)
                            Column {
                                Text(l.endonym, fontSize = 17.sp, color = c.ink)
                                Text(l.englishName, fontSize = 13.sp, color = c.secondary)
                            }
                        }
                    }
                } else {
                    for (level in settings.levelsToAdd(chosen)) {
                        Row(Modifier.fillMaxWidth().clickable { settings.add(chosen, level); onDismiss() }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LevelPill(level)
                            Text(level.title, fontSize = 17.sp, color = c.ink)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { if (language != null) language = null else onDismiss() }) { Text(if (language != null) "Back" else "Cancel") }
        },
    )
}
