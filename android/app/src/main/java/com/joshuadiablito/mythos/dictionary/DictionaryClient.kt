package com.joshuadiablito.mythos.dictionary

import com.joshuadiablito.mythos.model.Language
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class DictionaryUnavailable(val status: Int) :
    IOException("The dictionary isn't responding (kaikki.org returned $status). Try again in a moment.")

/** One HTTP GET: the status and body. */
typealias Fetcher = suspend (url: String) -> Pair<Int, ByteArray>

/**
 * Looks words up on kaikki.org: free and keyless. Every response, including
 * "no such word", is kept in a [DictionaryCache] that is checked first, so a
 * word looked up once is there offline and never costs a second request.
 */
class DictionaryClient(
    private val cache: DictionaryCache,
    private val fetcher: Fetcher = ::httpGet,
) {
    private val memory = ConcurrentHashMap<String, LookupResult>()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { cache.trim() }
    }

    /** Looks a word up under each plausible spelling until one is found, then
     *  fetches the lemmas it is a form of ("mange" → "manger"). */
    suspend fun lookUp(text: String, language: Language): LookupResult {
        val key = "${language.code}|$text"
        memory[key]?.let { return it }

        var entries: List<Entry> = emptyList()
        for (candidate in LookupCandidates.candidates(text)) {
            entries = fetch(candidate, language.dictionaryName)
            if (entries.isNotEmpty()) break
        }
        // Wiktionary has no page for most phrases ("se despierta", "a eu peur"):
        // try their words, longest first, and keep the phrase as the query.
        if (entries.isEmpty()) {
            outer@ for (word in LookupCandidates.mainWords(text)) {
                for (candidate in LookupCandidates.candidates(word)) {
                    entries = fetch(candidate, language.dictionaryName)
                    if (entries.isNotEmpty()) break@outer
                }
            }
        }
        val own = entries.map { it.word }.toSet()
        val lemmaWords = entries.flatMap { it.formOf }.map { it.lemma }.filter { it !in own }.distinct()
        // A lemma that fails to load loses its table, not the lookup.
        val lemmas = coroutineScope {
            lemmaWords.take(MAX_LEMMAS).map { word ->
                async { runCatching { fetch(word, language.dictionaryName) }.getOrDefault(emptyList()) }
            }.awaitAll().flatten()
        }

        val result = LookupResult(text, entries, lemmas)
        memory[key] = result
        return result
    }

    /** One spelling: from the cache if it's there, otherwise from kaikki.org,
     *  then cached. A 404 means "no such word", not an error, and is cached too. */
    private suspend fun fetch(word: String, language: String): List<Entry> = withContext(Dispatchers.IO) {
        val url = KaikkiParser.url(language, word)
        when (val hit = cache.read(url)) {
            is DictionaryCache.Hit.Found -> return@withContext parse(hit.data)
            DictionaryCache.Hit.NotFound -> return@withContext emptyList()
            null -> {}
        }
        val (status, data) = fetcher(url)
        if (status == 404) {
            cache.store(DictionaryCache.Hit.NotFound, url)
            return@withContext emptyList()
        }
        if (status !in 200..299) throw DictionaryUnavailable(status)
        cache.store(DictionaryCache.Hit.Found(data), url)
        parse(data)
    }

    private suspend fun parse(data: ByteArray): List<Entry> =
        withContext(Dispatchers.Default) { KaikkiParser.parse(String(data, Charsets.UTF_8)) }

    /** The size of the saved lookups on disk. */
    suspend fun cacheSize(): Long = withContext(Dispatchers.IO) { cache.sizeInBytes }

    /** Forgets every saved lookup, on disk and in memory. */
    suspend fun clearCache() {
        memory.clear()
        withContext(Dispatchers.IO) { cache.removeAll() }
    }

    companion object {
        private const val MAX_LEMMAS = 3

        suspend fun httpGet(url: String): Pair<Int, ByteArray> = withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.useCaches = false
                connection.setRequestProperty("User-Agent", "Mythos (Android)")
                val status = connection.responseCode
                val body = if (status in 200..299) connection.inputStream.use { it.readBytes() } else ByteArray(0)
                status to body
            } finally {
                connection.disconnect()
            }
        }
    }
}
