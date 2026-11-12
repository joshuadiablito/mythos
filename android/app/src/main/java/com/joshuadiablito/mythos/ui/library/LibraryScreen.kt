package com.joshuadiablito.mythos.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joshuadiablito.mythos.Mythos
import com.joshuadiablito.mythos.model.Course
import com.joshuadiablito.mythos.model.Story
import com.joshuadiablito.mythos.ui.Navigator
import com.joshuadiablito.mythos.ui.components.IconLabel
import com.joshuadiablito.mythos.ui.components.formatTime
import com.joshuadiablito.mythos.ui.theme.AppBackground
import com.joshuadiablito.mythos.ui.theme.CoverPalette
import com.joshuadiablito.mythos.ui.theme.StoryCover
import com.joshuadiablito.mythos.ui.theme.Symbols
import com.joshuadiablito.mythos.ui.theme.Theme
import java.time.Instant

/** The stories that have arrived so far, as a shelf of covers, newest first.
 *  Stories still to come stay hidden: they arrive one a day, on Today. */
@Composable
fun LibraryScreen(navigator: Navigator) {
    val settings = Mythos.settings
    val progress = Mythos.progress
    val c = Theme.colors
    var courseID by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    val course = settings.course(courseID) ?: settings.courses.firstOrNull()
    val all = course?.let { Mythos.plan.arrived(it, Instant.now()) } ?: emptyList()
    val arrived = if (search.isBlank()) all else all.filter {
        listOf(it.title, it.subtitle, it.topic, it.text).any { field -> field.contains(search.trim(), ignoreCase = true) }
    }
    val full = GridItemSpan(2)

    AppBackground {
        LazyVerticalGrid(
            GridCells.Fixed(2),
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item(span = { full }) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Your stories", style = Theme.serif(34.sp, FontWeight.Bold), color = c.ink)
                    if (course != null) OutlinedTextField(
                        search, { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search your stories") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = c.card, focusedContainerColor = c.card, unfocusedBorderColor = Color.Transparent,
                        ),
                    )
                }
            }
            if (course == null) {
                item(span = { full }) { EmptyShelf() }
                return@LazyVerticalGrid
            }
            if (settings.courses.size > 1) item(span = { full }) {
                CourseChips(course.id) { courseID = it }
            }
            item(span = { full }) { ShelfHeader(course) }
            itemsIndexed(arrived, key = { _, s -> s.id }) { index, story ->
                CoverTile(story, progress.isRead(story), isNew = index == 0 && search.isBlank(),
                    onOpen = { navigator.openStory(story.id) }, onToggleRead = { progress.toggleRead(story) })
            }
            if (search.isBlank()) item { LockedTile(course) }
            if (arrived.isEmpty() && search.isNotBlank()) item(span = { full }) {
                Text("No stories match “$search”.", color = c.secondary, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 60.dp))
            }
        }
    }
}

@Composable
private fun EmptyShelf() {
    val c = Theme.colors
    Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Rounded.LibraryBooks, contentDescription = null, tint = c.tertiary, modifier = Modifier.size(48.dp))
        Text("No stories yet", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.ink)
        Text("Choose a language in Settings, and its first story arrives today.", color = c.secondary, textAlign = TextAlign.Center)
    }
}

/** The courses being learned, as chips: "Español", or "Español A1" and "Español A2". */
@Composable
private fun CourseChips(selection: String, onSelect: (String) -> Unit) {
    val settings = Mythos.settings
    val c = Theme.colors
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(settings.courses, key = { it.id }) { course ->
            val selected = course.id == selection
            FilterChip(
                selected = selected,
                onClick = { onSelect(course.id) },
                label = {
                    Text(if (settings.hasSeveralLevels(course.language)) "${course.language.endonym} ${course.level.code}" else course.language.endonym,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.accent, selectedLabelColor = Color.White, containerColor = c.card),
                border = null,
            )
        }
    }
}

/** How many of the stories that have arrived have been read, as a ring. */
@Composable
private fun ShelfHeader(course: Course) {
    val c = Theme.colors
    val arrived = Mythos.plan.arrived(course, Instant.now())
    val read = Mythos.progress.readCount(arrived)
    val total = Mythos.catalog.stories(course.language, course.level).size
    val fraction = if (arrived.isEmpty()) 0f else read.toFloat() / arrived.size
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(Modifier.size(66.dp).semantics(mergeDescendants = true) { contentDescription = "$read of ${arrived.size} stories read" },
            contentAlignment = Alignment.Center) {
            val track = c.ink.copy(alpha = 0.08f)
            val accent = c.accent
            Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                drawArc(track, 0f, 360f, false, style = Stroke(7.dp.toPx()))
                drawArc(accent, -90f, 360f * fraction, false, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$read", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.ink, lineHeight = 20.sp)
                Text("of ${arrived.size}", fontSize = 11.sp, color = c.secondary, lineHeight = 12.sp)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${course.language.endonym} · ${course.level.code}", style = Theme.serif(20.sp, FontWeight.Bold), color = c.ink)
            Text(if (arrived.size == 1) "Your first story has arrived" else "${arrived.size} of $total stories have arrived",
                fontSize = 15.sp, color = c.secondary)
        }
    }
}

/** The stories still to come, as one locked tile. Never their titles. */
@Composable
private fun LockedTile(course: Course) {
    val remaining = Mythos.plan.remaining(course, Instant.now())
    if (remaining <= 0) return
    val c = Theme.colors
    val context = LocalContext.current
    val dash = c.secondary.copy(alpha = 0.35f)
    Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .drawBehind {
                    drawRoundRect(dash, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 15f))),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()))
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = c.secondary)
            Text("$remaining", style = Theme.serif(40.sp, FontWeight.Bold), color = c.secondary)
            Text(if (remaining == 1) "story to come" else "stories to come", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.secondary)
        }
        Column(Modifier.padding(horizontal = 2.dp)) {
            Text("One a day", style = Theme.serif(17.sp, FontWeight.SemiBold), color = c.ink)
            Text("Next at ${formatTime(context, Mythos.settings.deliveryTime(course.id))} tomorrow", fontSize = 12.sp, color = c.secondary)
        }
    }
}

/** A story on the shelf: its cover, numbered, with its title beneath. Long-press to mark it read. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CoverTile(story: Story, isRead: Boolean, isNew: Boolean, onOpen: () -> Unit, onToggleRead: () -> Unit) {
    val c = Theme.colors
    var menu by rememberSaveable { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
            .semantics(mergeDescendants = true) { if (isRead) contentDescription = "${story.title}, read" },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .shadow(12.dp, shape, ambientColor = CoverPalette.forTopic(story.topic).deep, spotColor = CoverPalette.forTopic(story.topic).deep)
                .clip(shape),
        ) {
            StoryCover(story, Modifier.fillMaxSize().then(if (isRead) Modifier.desaturate() else Modifier), symbolScale = 1.1f)
            Text("%02d".format(story.number), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp).background(Color.Black.copy(alpha = 0.18f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 4.dp))
            if (isNew && !isRead) {
                Text("NEW", fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).background(c.warm.copy(alpha = 0.9f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp))
            } else if (isRead) {
                Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(26.dp).background(c.green.copy(alpha = 0.85f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }
            Box(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                IconLabel(Symbols.icon(story.symbol), story.topic, Color.White.copy(alpha = 0.9f), fontSize = 11, weight = FontWeight.SemiBold)
            }
        }
        Column(Modifier.padding(horizontal = 2.dp)) {
            Text(story.title, style = Theme.serif(17.sp, FontWeight.SemiBold), color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${story.subtitle} · ${story.readingMinutes} min", fontSize = 12.sp, color = c.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(if (isRead) "Mark as unread" else "Mark as read") }, onClick = { menu = false; onToggleRead() })
        }
    }
}

/** Read stories' covers fade a little, as on iOS. */
private fun Modifier.desaturate(): Modifier = drawWithContent {
    val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.75f) }) }
    drawContext.canvas.saveLayer(Rect(Offset.Zero, size), paint)
    drawContent()
    drawContext.canvas.restore()
}
