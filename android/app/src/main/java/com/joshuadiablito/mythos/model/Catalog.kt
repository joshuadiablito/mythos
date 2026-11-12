package com.joshuadiablito.mythos.model

import android.content.res.AssetManager
import android.util.Log
import kotlinx.serialization.json.Json

/**
 * Every story, bundled with the app as `catalog-<language>-<level>.json`
 * (the same files the iOS app ships). Nothing is downloaded.
 */
class Catalog(files: List<Pair<String, String>>) {
    val stories: List<Story>
    private val byID: Map<String, Story>
    private val byCourse: Map<String, List<Story>>

    init {
        val all = mutableListOf<Story>()
        val courses = mutableMapOf<String, List<Story>>()
        for ((name, json) in files.sortedBy { it.first }) {
            try {
                val file = JSON.decodeFromString(CatalogFile.serializer(), json)
                val resolved = file.resolved
                all += resolved
                courses[key(file.language, file.level)] = resolved
            } catch (e: Exception) {
                runCatching { Log.e("Catalog", "Couldn't read $name", e) }
            }
        }
        stories = all
        byID = all.associateBy { it.id }
        byCourse = courses
    }

    fun stories(language: Language, level: Level): List<Story> = byCourse[key(language, level)] ?: emptyList()

    fun story(id: String): Story? = byID[id]

    fun hasStories(language: Language, level: Level): Boolean = stories(language, level).isNotEmpty()

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }

        private fun key(language: Language, level: Level) = "${language.code}-${level.code}"

        fun fromAssets(assets: AssetManager): Catalog {
            val names = (assets.list("") ?: emptyArray()).filter { it.startsWith("catalog-") && it.endsWith(".json") }
            return Catalog(names.map { name -> name to assets.open(name).bufferedReader().use { it.readText() } })
        }
    }
}
