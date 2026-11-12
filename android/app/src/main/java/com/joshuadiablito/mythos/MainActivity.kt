package com.joshuadiablito.mythos

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.joshuadiablito.mythos.services.NotificationScheduler
import com.joshuadiablito.mythos.ui.MythosApp
import com.joshuadiablito.mythos.ui.theme.MythosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent { MythosTheme { MythosApp() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** A tapped notification opens its story on Today. */
    private fun handle(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(NotificationScheduler.EXTRA_STORY)?.let { id ->
            Router.selectedTab = Router.Tab.TODAY
            Router.pendingStoryID = id
        }
        // Debug builds can open a tab directly, for screenshots: `adb shell am start -e tab library`.
        if (BuildConfig.DEBUG) intent.getStringExtra("tab")?.let { name ->
            Router.Tab.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { Router.selectedTab = it }
        }
    }

    override fun onResume() {
        super.onResume()
        // Settings may have changed, or the clock; set every course's next alarm again.
        if (Mythos.settings.hasOnboarded) NotificationScheduler.reschedule(this)
    }
}
