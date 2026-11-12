package com.joshuadiablito.mythos.services

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.joshuadiablito.mythos.model.Language
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device translation into English, with Google's ML Kit: the Android
 * counterpart of iOS's Translation framework. The first use for a language
 * downloads its model (about 30 MB); after that it works offline, and the
 * text never leaves the phone.
 */
object OnDeviceTranslator {
    suspend fun toEnglish(text: String, language: Language): String {
        val source = TranslateLanguage.fromLanguageTag(language.baseCode)
            ?: throw IllegalArgumentException("No translation model for ${language.englishName}")
        val translator = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(TranslateLanguage.ENGLISH).build()
        )
        try {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            // A paragraph at a time, so the blank lines between them survive.
            return text.split("\n\n").map { if (it.isBlank()) it else translator.translate(it).await() }.joinToString("\n\n")
        } finally {
            translator.close()
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
}
