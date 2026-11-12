package com.joshuadiablito.mythos.ui.words

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joshuadiablito.mythos.Mythos
import com.joshuadiablito.mythos.model.Language
import com.joshuadiablito.mythos.model.SavedWord
import com.joshuadiablito.mythos.ui.Navigator
import com.joshuadiablito.mythos.ui.components.IconLabel
import com.joshuadiablito.mythos.ui.components.LanguageBadge
import com.joshuadiablito.mythos.ui.components.ListenButton
import com.joshuadiablito.mythos.ui.lookup.LookupSheet
import com.joshuadiablito.mythos.ui.lookup.WordLookup
import com.joshuadiablito.mythos.ui.theme.AppBackground
import com.joshuadiablito.mythos.ui.theme.CoverPalette
import com.joshuadiablito.mythos.ui.theme.MeshBackground
import com.joshuadiablito.mythos.ui.theme.Theme

/** Words saved from stories, by language, with a flashcard review. Swipe a word away to remove it. */
@Composable
fun WordsScreen(navigator: Navigator) {
    val progress = Mythos.progress
    val c = Theme.colors
    var lookup by remember { mutableStateOf<WordLookup?>(null) }
    val byLanguage = Language.entries.mapNotNull { language ->
        progress.savedWords.filter { it.language == language }.takeIf { it.isNotEmpty() }?.let { language to it }
    }
    AppBackground {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text("Words", style = Theme.serif(34.sp, FontWeight.Bold), color = c.ink, modifier = Modifier.padding(bottom = 8.dp)) }
            if (byLanguage.isEmpty()) item { EmptyWords() }
            for ((language, words) in byLanguage) {
                item(key = "header-${language.code}") {
                    Row(Modifier.padding(top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LanguageBadge(language, 24.dp)
                        Text(language.endonym, style = Theme.serif(17.sp, FontWeight.SemiBold), color = c.ink)
                    }
                }
                item(key = "review-${language.code}") { ReviewBanner(language, words.size) { navigator.review(language) } }
                items(words, key = { it.id }) { word ->
                    SwipeToRemove(onRemove = { progress.remove(listOf(word)) }) {
                        WordRow(word) { lookup = WordLookup(word.word, word.language, word.meaning, word.storyTitle) }
                    }
                }
            }
        }
    }
    lookup?.let { LookupSheet(it, onDismiss = { lookup = null }) }
}

@Composable
private fun EmptyWords() {
    val c = Theme.colors
    Column(Modifier.fillMaxWidth().padding(top = 100.dp, start = 12.dp, end = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Rounded.BookmarkBorder, contentDescription = null, tint = c.tertiary, modifier = Modifier.size(48.dp))
        Text("Your words will gather here", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.ink, textAlign = TextAlign.Center)
        Text("Tap a word in a story and save it, or save a story's new words when you finish it. Then review them as flashcards.",
            color = c.secondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SwipeToRemove(onRemove: () -> Unit, content: @Composable () -> Unit) {
    val c = Theme.colors
    val state = rememberSwipeToDismissBoxState(confirmValueChange = {
        if (it == SwipeToDismissBoxValue.EndToStart) { onRemove(); true } else false
    })
    SwipeToDismissBox(
        state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Color(0xFFD9443A)).padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd) {
                Icon(Icons.Rounded.Delete, contentDescription = "Remove", tint = Color.White)
            }
        },
    ) {
        Box(Modifier.clip(RoundedCornerShape(16.dp)).background(c.card)) { content() }
    }
}

/** "Review as flashcards", on a strip of colour. */
@Composable
private fun ReviewBanner(language: Language, count: Int, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "Review your ${language.englishName} words as flashcards, $count words" },
    ) {
        MeshBackground(
            listOf(CoverPalette.aegean.deep, CoverPalette.lavender.mid, CoverPalette.aegean.mid, CoverPalette.lavender.deep, CoverPalette.aegean.mid),
            Modifier.matchParentSize(),
        )
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Rounded.Style, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text("Review as flashcards", style = Theme.serif(17.sp, FontWeight.SemiBold), color = Color.White)
                Text(if (count == 1) "1 word" else "$count words", fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Color.White)
        }
    }
}

@Composable
private fun WordRow(word: SavedWord, onClick: () -> Unit) {
    val c = Theme.colors
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(word.word, style = Theme.serif(20.sp, FontWeight.SemiBold), color = c.ink)
            word.meaning?.let { Text(it, fontSize = 15.sp, color = c.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            word.storyTitle?.let { IconLabel(Icons.Rounded.Book, it, c.tertiary, size = 11.dp, fontSize = 11) }
        }
        ListenButton(word.word, word.language, size = 38.dp)
    }
}
