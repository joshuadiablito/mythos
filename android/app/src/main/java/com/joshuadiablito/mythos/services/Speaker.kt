package com.joshuadiablito.mythos.services

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.joshuadiablito.mythos.model.Language

/**
 * Reads stories and words aloud with the phone's own voices, and plays
 * native-speaker recordings from Wiktionary when there are some.
 *
 * Android's TextToSpeech can't pause, and takes at most a few thousand
 * characters at a time, so a story is queued in pieces (a paragraph each),
 * pausing stops it, and resuming starts again from the word it stopped on.
 */
class Speaker(private val context: Context) {
    /** What is being spoken, so the button that started it can show a stop control. */
    var speakingID by mutableStateOf<String?>(null); private set
    var isPaused by mutableStateOf(false); private set
    /** The word being spoken, as a [start, end) range of the text passed to [speak]. */
    var spokenRange by mutableStateOf<IntRange?>(null); private set

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: (() -> Unit)? = null

    /** Bumped whenever speech is stopped, so callbacks from earlier speech are ignored. */
    @Volatile private var session = 0
    private var text = ""
    private var language: Language? = null
    private var rate = 0.42
    private var chunks: List<Pair<Int, String>> = emptyList()
    private var lastWordStart = 0
    private var player: MediaPlayer? = null

    private fun withEngine(block: () -> Unit) {
        if (ready) return block()
        pending = block
        if (tts == null) {
            tts = TextToSpeech(context) { status ->
                main.post {
                    if (status == TextToSpeech.SUCCESS) {
                        ready = true
                        tts?.setOnUtteranceProgressListener(listener)
                        tts?.setAudioAttributes(
                            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                        )
                        pending?.invoke()
                    } else {
                        toast("Text-to-speech isn't available on this phone.")
                        reset()
                    }
                    pending = null
                }
            }
        }
    }

    fun speak(text: String, language: Language, rate: Double = 0.42, id: String? = null) {
        stop()
        this.text = text
        this.language = language
        this.rate = rate
        speakingID = id ?: text
        val mySession = session
        withEngine {
            if (mySession != session) return@withEngine
            if (!configure(language)) {
                reset()
                return@withEngine
            }
            enqueue(from = 0)
        }
    }

    /** The best installed voice for the language; false if there's none. */
    private fun configure(language: Language): Boolean {
        val engine = tts ?: return false
        val locale = language.locale
        val voices: Set<Voice> = runCatching { engine.voices }.getOrNull() ?: emptySet()
        val candidates = voices.filter {
            it.locale.language == locale.language && it.locale.country.equals(locale.country, ignoreCase = true) &&
                !it.isNetworkConnectionRequired && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features
        }
        val best = candidates.maxByOrNull { it.quality }
        val ok = if (best != null) engine.setVoice(best) == TextToSpeech.SUCCESS
        else engine.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE
        if (!ok) {
            toast("This phone has no ${language.englishName} voice. Add one in Settings › Text-to-speech output.")
            return false
        }
        // iOS's natural rate is 0.5; Android's is 1.0.
        engine.setSpeechRate((rate / 0.5).toFloat())
        return true
    }

    private fun enqueue(from: Int) {
        val engine = tts ?: return
        session += 1
        chunks = chunk(text, from)
        if (chunks.isEmpty()) return finish()
        chunks.forEachIndexed { i, (_, piece) ->
            val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(piece, mode, Bundle(), "$session:$i")
        }
    }

    fun pause() {
        if (speakingID == null || isPaused || player != null) return
        isPaused = true
        session += 1
        tts?.stop()
    }

    fun resume() {
        if (!isPaused) return
        isPaused = false
        language?.let { if (configure(it)) enqueue(from = lastWordStart) }
    }

    /** Plays a recording, falling back to the phone's voice if it can't. */
    fun play(url: String?, fallback: String, language: Language) {
        if (url == null) return speak(fallback, language, 0.4)
        stop()
        speakingID = fallback
        val mySession = session
        val mp = MediaPlayer()
        player = mp
        runCatching {
            mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            mp.setDataSource(url)
            mp.setOnPreparedListener { if (mySession == session) it.start() }
            mp.setOnCompletionListener { if (mySession == session) reset() }
            mp.setOnErrorListener { _, _, _ ->
                // A recording that fails to load is read by the phone instead.
                if (mySession == session) speak(fallback, language, 0.4)
                true
            }
            mp.prepareAsync()
        }.onFailure { speak(fallback, language, 0.4) }
    }

    fun stop() {
        session += 1
        runCatching { tts?.stop() }
        releasePlayer()
        reset()
    }

    fun isSpeaking(id: String): Boolean = speakingID == id

    private fun releasePlayer() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }

    private fun reset() {
        releasePlayer()
        speakingID = null
        isPaused = false
        spokenRange = null
        lastWordStart = 0
    }

    private fun finish() = reset()

    private val listener = object : UtteranceProgressListener() {
        private fun parse(id: String?): Pair<Int, Int>? {
            val parts = id?.split(":") ?: return null
            return (parts.getOrNull(0)?.toIntOrNull() ?: return null) to (parts.getOrNull(1)?.toIntOrNull() ?: return null)
        }

        override fun onStart(utteranceId: String?) {}

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            val (s, i) = parse(utteranceId) ?: return
            main.post {
                if (s != session) return@post
                val offset = chunks.getOrNull(i)?.first ?: return@post
                lastWordStart = offset + start
                spokenRange = (offset + start) until (offset + end)
            }
        }

        override fun onDone(utteranceId: String?) {
            val (s, i) = parse(utteranceId) ?: return
            main.post { if (s == session && i == chunks.size - 1) finish() }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            val (s, _) = parse(utteranceId) ?: return
            main.post { if (s == session) finish() }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            @Suppress("DEPRECATION") onError(utteranceId)
        }
    }

    private fun toast(message: String) = main.post { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }

    companion object {
        private const val MAX_CHUNK = 3000

        /** `text` from `from`, in pieces the engine accepts, each with its offset in `text`:
         *  a paragraph each, and long paragraphs at sentence ends or spaces. */
        fun chunk(text: String, from: Int): List<Pair<Int, String>> {
            val out = mutableListOf<Pair<Int, String>>()
            var start = from.coerceIn(0, text.length)
            while (start < text.length) {
                // Skip the blank lines between paragraphs.
                while (start < text.length && text[start].isWhitespace()) start++
                if (start >= text.length) break
                var end = text.indexOf("\n\n", start).let { if (it < 0) text.length else it }
                if (end - start > MAX_CHUNK) {
                    val limit = start + MAX_CHUNK
                    val sentenceEnd = text.lastIndexOfAny(charArrayOf('.', '!', '?', ';'), limit - 1)
                    end = if (sentenceEnd > start) sentenceEnd + 1
                    else text.lastIndexOf(' ', limit).let { if (it > start) it else limit }
                }
                out.add(start to text.substring(start, end))
                start = end
            }
            return out
        }
    }
}
