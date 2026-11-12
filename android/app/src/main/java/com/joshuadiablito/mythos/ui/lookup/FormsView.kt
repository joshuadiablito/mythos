package com.joshuadiablito.mythos.ui.lookup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joshuadiablito.mythos.Mythos
import com.joshuadiablito.mythos.dictionary.EnglishVerb
import com.joshuadiablito.mythos.dictionary.Entry
import com.joshuadiablito.mythos.dictionary.InflectionTable
import com.joshuadiablito.mythos.dictionary.Inflections
import com.joshuadiablito.mythos.model.Language
import com.joshuadiablito.mythos.ui.theme.Theme
import com.joshuadiablito.mythos.ui.theme.card

/** Every form of an entry: its tables, each collapsible, with the table holding
 *  the tapped word open and that word highlighted. */
@Composable
fun FormsView(entry: Entry, inflections: Inflections, query: String, language: Language) {
    val c = Theme.colors
    // The verb's meaning in English, to conjugate under each form.
    val english = remember(entry) { EnglishVerb.fromEntry(entry) }
    // Open the table with the tapped word, or the first one.
    var expanded by remember(inflections) {
        mutableStateOf(setOfNotNull((inflections.tables.firstOrNull { it.contains(query) } ?: inflections.tables.firstOrNull())?.id))
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(entry.word, style = Theme.serif(17.sp, FontWeight.SemiBold), color = c.ink)
            Text(entry.partOfSpeech, fontSize = 12.sp, color = c.secondary)
        }
        for (table in inflections.tables) {
            TableCard(table, query, language, english, table.id in expanded) {
                expanded = if (table.id in expanded) expanded - table.id else expanded + table.id
            }
        }
        if (inflections.other.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().card(padding = 14.dp, radius = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (other in inflections.other) {
                    // Label above, forms wrapping below: a list of participles can be long.
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(other.label, fontSize = 12.sp, color = c.secondary)
                            english?.phrase(other.label)?.let { Text("· $it", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c.accent) }
                        }
                        FormList(other.forms, query, language, wraps = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun TableCard(table: InflectionTable, query: String, language: Language, english: EnglishVerb?, isExpanded: Boolean, onToggle: () -> Unit) {
    val c = Theme.colors
    val shape = RoundedCornerShape(18.dp)
    val rotation by animateFloatAsState(if (isExpanded) 0f else -90f, label = "chevron")
    Column(Modifier.fillMaxWidth().shadow(4.dp, shape, ambientColor = Color.Black.copy(alpha = 0.05f)).background(c.card, shape)) {
        Row(Modifier.fillMaxWidth().clip(shape).clickable(onClick = onToggle).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(table.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
            if (table.contains(query)) {
                Box(Modifier.padding(start = 8.dp).size(6.dp).background(c.accent, CircleShape).semantics { contentDescription = "Contains $query" })
            }
            Box(Modifier.weight(1f))
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = c.secondary, modifier = Modifier.rotate(rotation))
        }
        AnimatedVisibility(isExpanded) {
            Column {
                HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = c.quaternary)
                Box(Modifier.horizontalScroll(rememberScrollState()).padding(14.dp)) {
                    TableGrid(table, query, language, english)
                }
            }
        }
    }
}

@Composable
private fun TableGrid(table: InflectionTable, query: String, language: Language, english: EnglishVerb?) {
    val c = Theme.colors
    val tense = if (table.isVerbTable) EnglishVerb.tense(table.id.split(" ").filter { it.isNotEmpty() }) else null
    val showsColumnHeaders = !(table.columns.size == 1 && table.columns[0].key.isEmpty())
    val showsRowHeaders = !(table.rows.size == 1 && table.rows[0].key.isEmpty())
    val columns = table.columns.size + if (showsRowHeaders) 1 else 0
    Grid(columns, horizontalSpacing = 18.dp, verticalSpacing = 10.dp) {
        if (showsColumnHeaders) {
            if (showsRowHeaders) Text(table.rowHeading, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.secondary)
            for (col in table.columns) Text(col.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.secondary)
        }
        for (row in table.rows) {
            if (showsRowHeaders) Text(row.label, fontSize = 12.sp, color = c.secondary, modifier = Modifier.padding(top = 3.dp))
            for (col in table.columns) {
                val cell = table.cells[row.key]?.get(col.key)
                if (cell == null) {
                    Text("—", color = c.quaternary)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(if (cell.pronoun?.endsWith("’") == true) 0.dp else 5.dp)) {
                            cell.pronoun?.let { Text(it, fontSize = 12.sp, color = c.tertiary) }
                            FormList(cell.forms, query, language, speaksWith = cell.pronoun)
                        }
                        // "we eat", under "nous mangeons": who is doing it, in English.
                        val phrase = if (english != null && tense != null) english.phrase(row.key, col.key, tense) else null
                        if (phrase != null) {
                            val match = cell.forms.any { it.lowercase() == query.lowercase() }
                            Text(phrase, fontSize = 12.sp, color = if (match) c.accent else c.secondary, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

/** A simple grid: children in row-major order, each column as wide as its widest cell. */
@Composable
private fun Grid(columns: Int, horizontalSpacing: androidx.compose.ui.unit.Dp, verticalSpacing: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Layout(content) { measurables, _ ->
        val placeables = measurables.map { it.measure(Constraints()) }
        val rows = placeables.chunked(columns)
        val widths = IntArray(columns)
        rows.forEach { row -> row.forEachIndexed { i, p -> widths[i] = maxOf(widths[i], p.width) } }
        val heights = rows.map { row -> row.maxOf { it.height } }
        val h = horizontalSpacing.roundToPx()
        val v = verticalSpacing.roundToPx()
        val width = widths.sum() + h * (columns - 1).coerceAtLeast(0)
        val height = heights.sum() + v * (rows.size - 1).coerceAtLeast(0)
        layout(width, height) {
            var y = 0
            rows.forEachIndexed { r, row ->
                var x = 0
                row.forEachIndexed { i, p ->
                    p.placeRelative(x, y)
                    x += widths[i] + h
                }
                y += heights[r] + v
            }
        }
    }
}

/** Forms separated by slashes; tap one to hear it. The looked-up word is highlighted.
 *  In a table cell they stay on one line (the table scrolls sideways); elsewhere they wrap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormList(forms: List<String>, query: String, language: Language, speaksWith: String? = null, wraps: Boolean = false) {
    val c = Theme.colors
    val items: @Composable () -> Unit = {
        forms.forEachIndexed { i, form ->
            if (i > 0) Text("/", color = c.tertiary)
            val match = form.lowercase() == query.lowercase()
            Text(
                form,
                style = Theme.serif(17.sp, if (match) FontWeight.Bold else FontWeight.Normal),
                color = if (match) c.accent else c.ink,
                softWrap = false,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (match) c.accent.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable {
                        // "nous mangeons" sounds more natural than "mangeons" alone.
                        val pronoun = speaksWith?.split("/")?.firstOrNull()
                        val spoken = pronoun?.let { if (it.endsWith("’")) it + form else "$it $form" } ?: form
                        Mythos.speaker.speak(spoken, language, 0.4)
                    }
                    .padding(horizontal = if (match) 6.dp else 0.dp, vertical = if (match) 2.dp else 0.dp),
            )
        }
    }
    if (wraps) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { items() }
    else Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) { items() }
}
