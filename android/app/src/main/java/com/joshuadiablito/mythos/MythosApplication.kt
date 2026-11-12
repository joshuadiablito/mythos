package com.joshuadiablito.mythos

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.joshuadiablito.mythos.dictionary.DictionaryCache
import com.joshuadiablito.mythos.dictionary.DictionaryClient
import com.joshuadiablito.mythos.model.AppSettings
import com.joshuadiablito.mythos.model.Catalog
import com.joshuadiablito.mythos.model.DailyPlan
import com.joshuadiablito.mythos.model.KeyValueStore
import com.joshuadiablito.mythos.model.ProgressStore
import com.joshuadiablito.mythos.services.NotificationScheduler
import com.joshuadiablito.mythos.services.Speaker
import java.io.File

class MythosApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Mythos.init(this)
        NotificationScheduler.ensureChannel(this)
    }
}

/** The app's shared objects, made once when the process starts (before any
 *  activity or alarm receiver runs), as the iOS app's `.shared` singletons are. */
object Mythos {
    lateinit var catalog: Catalog; private set
    lateinit var settings: AppSettings; private set
    lateinit var progress: ProgressStore; private set
    lateinit var dictionary: DictionaryClient; private set
    lateinit var speaker: Speaker; private set
    lateinit var prefs: KeyValueStore; private set

    val plan: DailyPlan get() = DailyPlan(catalog)

    fun init(context: Context) {
        if (::catalog.isInitialized) return
        prefs = PrefsStore(context)
        catalog = Catalog.fromAssets(context.assets)
        settings = AppSettings(prefs)
        progress = ProgressStore(prefs)
        dictionary = DictionaryClient(DictionaryCache(File(context.noBackupFilesDir, "DictionaryCache")))
        speaker = Speaker(context.applicationContext)
    }
}

/** Where the app should go next: set when a notification is tapped. */
object Router {
    enum class Tab { TODAY, LIBRARY, WORDS, SETTINGS }

    var selectedTab by mutableStateOf(Tab.TODAY)
    /** A story to open, from a tapped notification. */
    var pendingStoryID by mutableStateOf<String?>(null)
}

private class PrefsStore(context: Context) : KeyValueStore {
    private val prefs = context.getSharedPreferences("mythos", Context.MODE_PRIVATE)
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
}
