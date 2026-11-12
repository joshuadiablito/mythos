package com.joshuadiablito.mythos.dictionary

import java.io.File
import java.security.MessageDigest

/**
 * kaikki.org responses kept on disk, checked before every request. A word
 * looked up once never costs another request, and works offline.
 *
 * Kept in `noBackupFilesDir`, since every file can be downloaded again, and
 * not in the cache directory, which Android may clear; it trims itself
 * instead, least recently used first. "No such word" answers are remembered
 * too, for 30 days, since Wiktionary does add pages.
 */
class DictionaryCache(private val directory: File, private val maxBytes: Long = 80L shl 20) {
    sealed interface Hit {
        class Found(val data: ByteArray) : Hit {
            override fun equals(other: Any?) = other is Found && other.data.contentEquals(data)
            override fun hashCode() = data.contentHashCode()
        }
        data object NotFound : Hit
    }

    private val notFoundLifetime = 30L * 24 * 3600 * 1000

    init { directory.mkdirs() }

    private fun key(url: String): String =
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun foundFile(url: String) = File(directory, key(url) + ".jsonl")
    private fun notFoundFile(url: String) = File(directory, key(url) + ".404")

    @Synchronized
    fun read(url: String, now: Long = System.currentTimeMillis()): Hit? {
        val found = foundFile(url)
        if (found.exists()) {
            val data = runCatching { found.readBytes() }.getOrNull()
            if (data != null) {
                // Mark it recently used, for trimming.
                found.setLastModified(now)
                return Hit.Found(data)
            }
        }
        val missing = notFoundFile(url)
        if (missing.exists()) {
            if (now - missing.lastModified() < notFoundLifetime) return Hit.NotFound
            missing.delete()
        }
        return null
    }

    @Synchronized
    fun store(hit: Hit, url: String) {
        directory.mkdirs()
        when (hit) {
            is Hit.Found -> writeAtomically(foundFile(url), hit.data)
            Hit.NotFound -> writeAtomically(notFoundFile(url), ByteArray(0))
        }
    }

    private fun writeAtomically(file: File, data: ByteArray) {
        val temp = File(directory, file.name + ".tmp")
        runCatching {
            temp.writeBytes(data)
            if (!temp.renameTo(file)) { file.delete(); temp.renameTo(file) }
        }
    }

    /** Removes the least recently used entries until the cache fits its budget. */
    @Synchronized
    fun trim() {
        val files = directory.listFiles() ?: return
        var total = files.sumOf { it.length() }
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            total -= file.length()
            file.delete()
        }
    }

    @Synchronized
    fun removeAll() {
        directory.deleteRecursively()
        directory.mkdirs()
    }

    val sizeInBytes: Long
        @Synchronized get() = directory.listFiles()?.sumOf { it.length() } ?: 0
}
