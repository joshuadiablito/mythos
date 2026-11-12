package com.joshuadiablito.mythos.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.joshuadiablito.mythos.model.Story
import kotlin.math.max
import kotlin.math.sin

/** The colours of a story's cover, chosen by its topic. Same palettes as iOS. */
data class CoverPalette(val deep: Color, val mid: Color, val light: Color, val accent: Color) {
    companion object {
        private fun CoverPalette(deep: Long, mid: Long, light: Long, accent: Long) =
            CoverPalette(hex(deep), hex(mid), hex(light), hex(accent))

        val aegean = CoverPalette(0x14307F, 0x2E5BD8, 0x8FB8FF, 0x5FD3E8)
        val dawn = CoverPalette(0x34265F, 0xD9648C, 0xFFC9A8, 0x7AA7FF)
        val terracotta = CoverPalette(0x7A2E1E, 0xC9583A, 0xF2B279, 0xE8C15A)
        val olive = CoverPalette(0x2F4A26, 0x6E8F45, 0xD4DE9B, 0xE7B94C)
        val lavender = CoverPalette(0x2F2461, 0x6C5BC4, 0xC6B8FF, 0xF08FC0)
        val rose = CoverPalette(0x7C1E45, 0xD2477A, 0xFFB8CE, 0xFF9F6E)
        val peach = CoverPalette(0x9A3F2C, 0xE97D5B, 0xFFD6B8, 0xF7B267)
        val mint = CoverPalette(0x0D4F55, 0x229A94, 0xA6E8D2, 0xF4D35E)
        val sky = CoverPalette(0x284C74, 0x5E92C6, 0xD7E7F5, 0xF2C14E)
        val transit = CoverPalette(0x053F5C, 0x1B7F8E, 0xF5C451, 0xE4572E)
        val dusk = CoverPalette(0x1C1A40, 0x5B3E8A, 0xF29E6D, 0xFFD08A)
        val citrus = CoverPalette(0xA44A00, 0xF2A20D, 0xFFE89A, 0xFF6B4A)
        val pitch = CoverPalette(0x0E4A24, 0x2E9E4F, 0xB6EFC3, 0xF7F06D)
        val ink = CoverPalette(0x141A2E, 0x3F4C78, 0xAAB6D6, 0x7EA2FF)

        fun forTopic(topic: String): CoverPalette = when (topic) {
            "Family" -> peach
            "Routine", "Plans" -> dawn
            "Food & drink", "Cooking" -> terracotta
            "Shopping", "Outdoors" -> olive
            "Learning", "Culture" -> lavender
            "Celebrations", "Friends" -> rose
            "Home", "Health" -> mint
            "Weather" -> sky
            "Getting around" -> transit
            "Everyday life", "Neighbours" -> dusk
            "Animals", "Free time" -> citrus
            "Sport" -> pitch
            "Work" -> ink
            "Travel", "Science" -> aegean
            "Society", "Memory", "Mystery" -> dusk
            "Environment", "Nature" -> olive
            "Technology", "Media" -> ink
            "History" -> terracotta
            "Art", "Ideas" -> lavender
            "City" -> transit
            "Love" -> rose
            "Money" -> citrus
            "Language" -> dawn
            else -> aegean
        }
    }
}

/** A stable number from the story's ID (the same as iOS's), so its cover never changes. */
fun coverSeed(id: String): Int {
    var acc = 5381L
    id.codePoints().forEach { acc = (acc * 33 + it) and 0x7FFFFFFF }
    return acc.toInt()
}

/**
 * A story's cover: a soft mesh of its topic's colours, arranged differently
 * for every story, with its icon drawn large across it. `animated` lets the
 * colours drift slowly, for the cover on Today.
 */
@Composable
fun StoryCover(story: Story, modifier: Modifier = Modifier, animated: Boolean = false, symbolScale: Float = 1.05f) {
    val palette = CoverPalette.forTopic(story.topic)
    val seed = coverSeed(story.id)
    val time by produceState(0.0, animated) {
        if (!animated) return@produceState
        val start = withFrameMillis { it }
        while (true) withFrameMillis { value = (it - start) / 1000.0 }
    }
    BoxWithConstraints(modifier.clearAndSetSemantics { }) {
        Canvas(Modifier.fillMaxSize()) { drawMesh(palette, seed, if (animated) time else 0.0, animated) }
        val width = maxWidth
        val height = maxHeight
        val iconSize = minOf(width, height) * symbolScale
        Icon(
            Symbols.icon(story.symbol), contentDescription = null, tint = Color.White.copy(alpha = 0.16f),
            modifier = Modifier
                .size(iconSize)
                .offset(
                    x = width * (if (seed % 2 == 0) 0.78f else 0.26f) - iconSize / 2,
                    y = height * 0.34f - iconSize / 2,
                )
                .rotate((seed % 21 - 10).toFloat()),
        )
        // A soft vignette at the bottom, so white text on top of it stays readable.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.3f)))
        }
    }
}

/** Nine colours at nine points, each blended out from where it sits. */
private fun DrawScope.drawMesh(p: CoverPalette, seed: Int, time: Double, animated: Boolean) {
    val ring = listOf(p.deep, p.mid, p.light, p.accent, p.mid, p.deep, p.light, p.mid)
    val shift = seed % ring.size
    val outer = (0 until 8).map { ring[(it + shift) % ring.size] }
    val drift = if (animated) 0.08 else 0.0
    val s = (seed % 100) / 100.0
    fun wobble(phase: Double) = (drift * sin(time * 0.35 + phase * 6.28)).toFloat()
    val center = if (seed % 3 == 0) p.accent else p.mid
    // Corners first, then edges, then the centre, so the middle of each edge and the centre win near themselves.
    val points = listOf(
        Triple(0f, 0f, outer[0]), Triple(1f, 0f, outer[2]), Triple(0f, 1f, outer[6]), Triple(1f, 1f, outer[4]),
        Triple(0.5f + wobble(s), 0f, outer[1]), Triple(0f, 0.5f + wobble(s + 0.3), outer[7]),
        Triple(1f, 0.5f + wobble(s + 0.6), outer[3]), Triple(0.5f + wobble(s + 0.9), 1f, outer[5]),
        Triple((0.35 + s * 0.3).toFloat() + wobble(s + 0.5), (0.45 + (1 - s) * 0.2).toFloat() + wobble(s + 0.8), center),
    )
    drawRect(p.mid)
    val radius = max(size.width, size.height) * 0.72f
    for ((x, y, color) in points) {
        drawRect(Brush.radialGradient(
            0f to color, 0.45f to color.copy(alpha = 0.75f), 1f to color.copy(alpha = 0f),
            center = Offset(x * size.width, y * size.height), radius = radius,
        ))
    }
}

/** A cover-coloured background without a story: for the review screen and banners. */
@Composable
fun MeshBackground(colors: List<Color>, modifier: Modifier = Modifier, animated: Boolean = false) {
    val time by produceState(0.0, animated) {
        if (!animated) return@produceState
        val start = withFrameMillis { it }
        while (true) withFrameMillis { value = (it - start) / 1000.0 }
    }
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(colors.first())
            val t = (sin(time * 0.3) * 0.12).toFloat()
            val spots = listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f, 0.5f + t to 0.5f - t)
            val radius = max(size.width, size.height) * 0.8f
            colors.zip(spots).forEach { (color, at) ->
                drawRect(Brush.radialGradient(0f to color, 1f to color.copy(alpha = 0f),
                    center = Offset(at.first * size.width, at.second * size.height), radius = radius))
            }
        }
    }
}
