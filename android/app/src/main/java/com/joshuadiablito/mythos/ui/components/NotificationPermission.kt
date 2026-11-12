package com.joshuadiablito.mythos.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.joshuadiablito.mythos.Mythos
import com.joshuadiablito.mythos.services.NotificationScheduler

enum class NotificationStatus { ALLOWED, NOT_ASKED, DENIED }

/** Whether Mythos may notify, and a way to ask: by the system prompt while
 *  Android still shows it, then by sending the learner to Settings. */
class NotificationPermission internal constructor(
    status: NotificationStatus,
    private val ask: (onResult: (Boolean) -> Unit) -> Unit,
    private val context: Context,
) {
    var status by mutableStateOf(status); internal set

    /** Shows the system prompt if it can; otherwise opens the app's notification settings. */
    fun request(onResult: (Boolean) -> Unit = {}) {
        if (status == NotificationStatus.NOT_ASKED) ask(onResult) else {
            openSettings()
            onResult(false)
        }
    }

    fun openSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private const val ASKED_KEY = "notifications.asked"

private fun Context.activity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private fun currentStatus(context: Context): NotificationStatus {
    if (NotificationScheduler.canPost(context)) return NotificationStatus.ALLOWED
    if (Build.VERSION.SDK_INT < 33) return NotificationStatus.DENIED
    val asked = Mythos.prefs.getString(ASKED_KEY) == "true"
    val rationale = context.activity()?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true
    // Android shows its prompt twice at most; after a second "Don't allow" only Settings can turn them on.
    return if (!asked || rationale) NotificationStatus.NOT_ASKED else NotificationStatus.DENIED
}

@Composable
fun rememberNotificationPermission(): NotificationPermission {
    val context = LocalContext.current
    var callback by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    lateinit var state: NotificationPermission
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Mythos.prefs.putString(ASKED_KEY, "true")
        state.status = currentStatus(context)
        callback?.invoke(granted)
        callback = null
    }
    state = remember {
        NotificationPermission(currentStatus(context), { onResult ->
            if (Build.VERSION.SDK_INT >= 33) {
                callback = onResult
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else onResult(NotificationScheduler.canPost(context))
        }, context)
    }
    // Coming back from Settings: look again.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state.status = currentStatus(context) }
    return state
}
