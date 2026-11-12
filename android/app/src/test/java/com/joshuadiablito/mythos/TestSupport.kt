package com.joshuadiablito.mythos

import com.joshuadiablito.mythos.dictionary.Entry
import com.joshuadiablito.mythos.dictionary.KaikkiParser
import com.joshuadiablito.mythos.model.Catalog
import java.io.File

/** The real catalog, read from the iOS app's folder, which the Android app bundles too. */
val testCatalog: Catalog by lazy {
    // Gradle runs unit tests from the module directory, android/app.
    val dir = File("../../Mythos/Resources/Catalog")
    val files = dir.listFiles { f -> f.name.startsWith("catalog-") && f.name.endsWith(".json") }
        ?: error("No catalog at ${dir.absolutePath}")
    Catalog(files.map { it.name to it.readText() })
}

/** Real kaikki.org responses, shared with the iOS tests (MythosTests/Fixtures). */
fun fixtureText(name: String): String =
    (object {}).javaClass.classLoader!!.getResource("$name.jsonl")?.readText() ?: error("No fixture $name")

fun fixture(name: String): List<Entry> = KaikkiParser.parse(fixtureText(name))
