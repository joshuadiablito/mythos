package com.joshuadiablito.mythos.ui.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joshuadiablito.mythos.Mythos
import com.joshuadiablito.mythos.model.Course
import com.joshuadiablito.mythos.model.Language
import com.joshuadiablito.mythos.model.Story
import com.joshuadiablito.mythos.ui.Navigator
import com.joshuadiablito.mythos.ui.components.IconLabel
import com.joshuadiablito.mythos.ui.components.NotificationStatus
import com.joshuadiablito.mythos.ui.components.formatTime
import com.joshuadiablito.mythos.ui.components.rememberNotificationPermission
import com.joshuadiablito.mythos.ui.theme.AppBackground
import com.joshuadiablito.mythos.ui.theme.CoverPalette
import com.joshuadiablito.mythos.ui.theme.StoryCover
import com.joshuadiablito.mythos.ui.theme.Theme
import com.joshuadiablito.mythos.ui.theme.card
import com.joshuadiablito.mythos.ui.theme.hex
import com.joshuadiablito.mythos.services.NotificationScheduler
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/** The front page: a greeting in the language being learned, today's story
 *  as a large living cover (one per course, side by side), the week's
 *  reading, and a glimpse of tomorrow. */
@Composable
fun TodayScreen(navigator: Navigator) {
    val settings = Mythos.settings
    val permission = rememberNotificationPermission()
    val context = LocalContext.current
    AppBackground {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            Masthead(settings.courses.firstOrNull()?.language, Modifier.padding(horizontal = 24.dp))

            if (settings.notificationsEnabled && permission.status != NotificationStatus.ALLOWED) {
                NotificationsOffBanner(permission.status, Modifier.padding(horizontal = 20.dp)) {
                    permission.request { NotificationScheduler.reschedule(context) }
                }
            }

            TodayCovers(settings.courses) { navigator.openStory(it.id) }

            WeekStrip(Modifier.padding(horizontal = 20.dp))

            if (settings.courses.isNotEmpty()) Tomorrow(Modifier.padding(horizontal = 20.dp))
        }
    }
}

/** "Καλησπέρα" over "ΚΥΡΙΑΚΉ 25 ΟΚΤΩΒΡΊΟΥ": the day, in the language being learned. */
@Composable
private fun Masthead(language: Language?, modifier: Modifier) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = LocalDateTime.now() } }
    val c = Theme.colors
    Column(modifier.padding(top = 12.dp).semantics(mergeDescendants = true) { heading() }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val lang = language ?: Language.FRENCH
        Text(lang.longDate(now).uppercase(lang.locale), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, color = c.accent)
        Text(language?.greeting(now) ?: "Today", style = Theme.serif(40.sp, FontWeight.Bold), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One large cover per course, paging sideways when there are several. */
@Composable
private fun TodayCovers(courses: List<Course>, open: (Story) -> Unit) {
    val pager = rememberPagerState { courses.size }
    // Vertical padding inside the pager, which clips its pages, so the covers' shadows show.
    HorizontalPager(
        pager,
        contentPadding = PaddingValues(start = 20.dp, end = if (courses.size > 1) 48.dp else 20.dp, top = 4.dp, bottom = 28.dp),
        pageSpacing = 14.dp,
        userScrollEnabled = courses.size > 1,
    ) { page ->
        val course = courses[page]
        val story = Mythos.plan.story(course, Instant.now())
        if (story != null) HeroCover(story, open) else ComingSoonCover(course)
    }
}

/** Today's story as a tall, slowly moving cover, with its title and actions across the bottom. */
@Composable
private fun HeroCover(story: Story, open: (Story) -> Unit) {
    val progress = Mythos.progress
    val speaker = Mythos.speaker
    val isRead = progress.isRead(story)
    val speaking = speaker.isSpeaking(story.id)
    val palette = CoverPalette.forTopic(story.topic)
    val shape = RoundedCornerShape(34.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(520.dp)
            .shadow(24.dp, shape, ambientColor = palette.deep, spotColor = palette.deep)
            .clip(shape)
            .clickable { open(story) }
            .semantics { contentDescription = "Today's story: ${story.title}, ${story.subtitle}" },
    ) {
        StoryCover(story, Modifier.fillMaxSize(), animated = true)
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FrostedLabel("${story.language.endonym} · ${story.level.code}")
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(isRead) {
                    FrostedLabel("Read", icon = Icons.Rounded.Check, tint = Color(0x5934C759))
                }
            }
            Spacer(Modifier.weight(1f))
            Text("TODAY'S STORY · ${story.topic.uppercase()}", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp, color = Color.White.copy(alpha = 0.8f))
            Text(story.title, style = Theme.serif(38.sp, FontWeight.Bold), color = Color.White, maxLines = 2,
                overflow = TextOverflow.Ellipsis, lineHeight = 42.sp, modifier = Modifier.padding(top = 4.dp))
            Text(story.subtitle, fontSize = 20.sp, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { open(story) },
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = palette.deep),
                ) {
                    Icon(Icons.Rounded.MenuBook, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isRead) "Read again" else "Start reading", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
                FilledTonalIconButton(
                    onClick = {
                        if (speaking) speaker.stop()
                        else speaker.speak(story.text, story.language, Mythos.settings.speechRate, story.id)
                    },
                    modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.White.copy(alpha = 0.22f), contentColor = Color.White),
                ) {
                    Icon(if (speaking) Icons.Rounded.Stop else Icons.Rounded.Headphones,
                        contentDescription = if (speaking) "Stop listening" else "Listen to the story")
                }
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconLabel(Icons.Rounded.Schedule, "${story.readingMinutes} min", Color.White.copy(alpha = 0.8f), weight = FontWeight.Medium)
                IconLabel(Icons.Rounded.AutoAwesome, "${story.vocab.size} new words", Color.White.copy(alpha = 0.8f), weight = FontWeight.Medium)
            }
        }
    }
}

/** A small label in a translucent capsule, for text over a cover. */
@Composable
fun FrostedLabel(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, tint: Color = Color.White.copy(alpha = 0.18f)) {
    Row(
        Modifier
            .background(tint, CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun ComingSoonCover(course: Course) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(520.dp)
            .clip(RoundedCornerShape(34.dp))
            .background(Brush.linearGradient(listOf(hex(0x3F4C78), hex(0x141A2E), hex(0x2E5BD8))))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
    ) {
        Icon(Icons.Rounded.HourglassEmpty, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
        Text("${course.language.endonym} ${course.level.code} is being written", style = Theme.serif(24.sp, FontWeight.Bold), color = Color.White)
        Text("Stories for ${course.level.title.lowercase()} learners are on their way.", fontSize = 15.sp, color = Color.White.copy(alpha = 0.85f))
    }
}

/** Seven days, a dot for each, filled on the days a story was finished. */
@Composable
private fun WeekStrip(modifier: Modifier) {
    val progress = Mythos.progress
    val c = Theme.colors
    val week = progress.week()
    val streak = progress.streak()
    val today = LocalDate.now()
    Row(modifier.fillMaxWidth().card(padding = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.width(78.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null,
                    tint = if (streak > 0) hex(0xF2545B) else c.tertiary, modifier = Modifier.size(26.dp))
                Text("$streak", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = c.ink)
            }
            Text(if (streak == 1) "day in a row" else "days in a row", fontSize = 12.sp, color = c.secondary)
        }
        Row(Modifier.weight(1f)) {
            for ((day, read) in week) {
                val isToday = day == today
                Column(
                    Modifier.weight(1f).semantics(mergeDescendants = true) {
                        contentDescription = "${day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}: ${if (read) "read" else "not read"}"
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold, color = if (isToday) c.accent else c.secondary)
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(if (read) c.accent else c.ink.copy(alpha = 0.06f))
                            .then(if (isToday && !read) Modifier.dashedCircle(c.accent) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (read) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

private fun Modifier.dashedCircle(color: Color) = drawBehind {
    drawCircle(color, radius = size.minDimension / 2 - 1.5f,
        style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))))
}

/** Tomorrow's story, its cover blurred and its title kept back until it arrives. */
@Composable
private fun Tomorrow(modifier: Modifier) {
    val settings = Mythos.settings
    val c = Theme.colors
    val context = LocalContext.current
    val tomorrow = Instant.now().plusSeconds(24 * 3600)
    val story = Mythos.plan.featured(settings.courses, tomorrow) ?: return
    Row(modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(74.dp, 96.dp).clip(RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            StoryCover(story, Modifier.fillMaxSize().blur(10.dp), symbolScale = 0.9f)
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color.White)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("TOMORROW", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = c.secondary)
            // Just the topic: the story itself is a surprise until it arrives.
            Text("A story about ${story.topic.lowercase()}", style = Theme.serif(20.sp, FontWeight.SemiBold), color = c.ink)
            val time = formatTime(context, settings.deliveryTime(settings.courseFor(story)?.id))
            Text("${story.language.endonym} · arrives at $time", fontSize = 12.sp, color = c.secondary)
        }
    }
}

@Composable
private fun NotificationsOffBanner(status: NotificationStatus, modifier: Modifier, onAllow: () -> Unit) {
    val c = Theme.colors
    Row(modifier.fillMaxWidth().card(padding = 14.dp, radius = 20.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = c.warm)
        Column(Modifier.weight(1f)) {
            Text("Get each day's story", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
            Text("A notification when it arrives, at the time you chose.", fontSize = 12.sp, color = c.secondary)
        }
        FilledTonalButton(onClick = onAllow) { Text(if (status == NotificationStatus.NOT_ASKED) "Turn on" else "Settings") }
    }
}
