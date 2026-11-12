package com.joshuadiablito.mythos.services

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.joshuadiablito.mythos.MainActivity
import com.joshuadiablito.mythos.Mythos
import com.joshuadiablito.mythos.R
import com.joshuadiablito.mythos.model.Course
import com.joshuadiablito.mythos.model.Story
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Brings the daily story as a local notification, so no server is needed.
 * One alarm per course, set for its next delivery time; when it goes off,
 * the receiver posts that day's story and sets the alarm for the next day.
 * Alarms don't survive a restart, so [RescheduleReceiver] sets them again.
 */
object NotificationScheduler {
    const val CHANNEL = "daily-story"
    const val EXTRA_STORY = "storyID"
    private const val EXTRA_COURSE = "courseID"
    private const val SCHEDULED_KEY = "notifications.scheduled"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Daily story", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Each day's new story, at the time you chose"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Whether Mythos may show notifications. */
    fun canPost(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    /** The next time a course's story arrives: today, if that time is still to come, otherwise tomorrow. */
    fun nextFire(time: LocalTime, now: ZonedDateTime): ZonedDateTime {
        val today = now.toLocalDate().atTime(time).atZone(now.zone)
        return if (today.isAfter(now)) today else now.toLocalDate().plusDays(1).atTime(time).atZone(now.zone)
    }

    /** Sets one alarm per course, replacing any set before. Cheap: call it whenever settings change. */
    fun reschedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val settings = Mythos.settings
        // Cancel what was set last time, including courses since removed.
        val previous = Mythos.prefs.getString(SCHEDULED_KEY)?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        for (id in previous) alarms.cancel(alarmIntent(context, id))

        val courses = if (settings.notificationsEnabled && settings.hasOnboarded)
            settings.courses.filter { Mythos.catalog.hasStories(it.language, it.level) } else emptyList()
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        for (course in courses) {
            val at = nextFire(settings.time(course), now).toInstant().toEpochMilli()
            setAlarm(alarms, at, alarmIntent(context, course.id))
        }
        Mythos.prefs.putString(SCHEDULED_KEY, courses.joinToString(",") { it.id })
    }

    /** Whether alarms go off on the minute. Without this, Android may deliver one up to an hour late. */
    fun exactAlarmsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Android's page for letting Mythos set exact alarms. */
    fun exactAlarmSettings(context: Context): Intent =
        Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:${context.packageName}"))

    private fun setAlarm(alarms: AlarmManager, at: Long, intent: PendingIntent) {
        val exact = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
        if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
    }

    private fun alarmIntent(context: Context, courseID: String): PendingIntent {
        val intent = Intent(context, StoryAlarmReceiver::class.java)
            .setAction("com.joshuadiablito.mythos.DAILY.$courseID")
            .putExtra(EXTRA_COURSE, courseID)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** A course's alarm went off: post its story for today, then set tomorrow's. */
    internal fun deliver(context: Context, courseID: String) {
        val course: Course? = Mythos.settings.course(courseID)
        if (course != null && Mythos.settings.notificationsEnabled) {
            Mythos.plan.story(course, Instant.now())?.let { post(context, it, notificationID = courseID.hashCode()) }
        }
        reschedule(context)
    }

    fun post(context: Context, story: Story, notificationID: Int) {
        if (!canPost(context)) return
        val content = NotificationContent.forStory(story)
        val open = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_STORY, story.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tap = PendingIntent.getActivity(context, story.id.hashCode(), open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.accent))
            .setContentTitle(content.title)
            .setContentText(content.subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${content.subtitle}\n${content.body}"))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationID, notification)
        } catch (_: SecurityException) {
            // Permission was withdrawn between the check and the post.
        }
    }

    enum class PreviewResult { SENT, BLOCKED, NOTHING_TO_SEND }

    /** Sends today's featured story in a few seconds, to see what it looks like. */
    fun sendPreview(context: Context): PreviewResult {
        if (!canPost(context)) return PreviewResult.BLOCKED
        val story = Mythos.plan.featured(Mythos.settings.courses, Instant.now()) ?: return PreviewResult.NOTHING_TO_SEND
        val intent = Intent(context, StoryAlarmReceiver::class.java)
            .setAction("com.joshuadiablito.mythos.PREVIEW")
            .putExtra(EXTRA_STORY, story.id)
        val pending = PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        context.getSystemService(AlarmManager::class.java)
            .set(AlarmManager.ELAPSED_REALTIME_WAKEUP, android.os.SystemClock.elapsedRealtime() + 5_000, pending)
        return PreviewResult.SENT
    }

    internal fun courseIDFrom(intent: Intent): String? = intent.getStringExtra(EXTRA_COURSE)
}

/** A course's daily alarm, or a preview. */
class StoryAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Mythos.init(context)
        val previewID = intent.getStringExtra(NotificationScheduler.EXTRA_STORY)
        if (previewID != null) {
            Mythos.catalog.story(previewID)?.let { NotificationScheduler.post(context, it, notificationID = 1) }
            return
        }
        NotificationScheduler.courseIDFrom(intent)?.let { NotificationScheduler.deliver(context, it) }
    }
}

/** Sets the alarms again after a restart, an update, or a change of clock or time zone. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Mythos.init(context)
        NotificationScheduler.reschedule(context)
    }
}
