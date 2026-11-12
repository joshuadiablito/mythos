package com.joshuadiablito.mythos.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

/** Saved strings: SharedPreferences in the app, a map in tests. */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

class MemoryStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()
    override fun getString(key: String) = map[key]
    override fun putString(key: String, value: String) { map[key] = value }
}

/**
 * A language being learned, at a level. `startedOn` anchors the daily
 * rotation, so changing level starts that level from its first story.
 * A language can be learned at several levels at once, but not twice at one.
 */
@Serializable
data class Course(
    /** Stable while the level changes, so a course can be edited in place. */
    val id: String = UUID.randomUUID().toString(),
    val language: Language,
    val level: Level,
    /** Milliseconds since 1970. */
    val startedOn: Long,
    /** When this course's story arrives, if not at the shared default time. */
    val hour: Int? = null,
    val minute: Int? = null,
) {
    val startedOnInstant: Instant get() = Instant.ofEpochMilli(startedOn)
}

/** When the day's story arrives. */
enum class DeliverySlot(val title: String, val hour: Int, val minute: Int) {
    MORNING("Morning", 8, 0),
    AFTERNOON("Afternoon", 13, 0),
    EVENING("Evening", 19, 30);

    companion object {
        /** The slot a time of day falls in. */
        fun containing(hour: Int): DeliverySlot = when {
            hour < 12 -> MORNING
            hour < 17 -> AFTERNOON
            else -> EVENING
        }
    }
}

/** The learner's choices, saved as JSON. Each change is saved at once. */
class AppSettings(private val store: KeyValueStore, private val now: () -> Instant = Instant::now) {
    @Serializable
    private data class Stored(
        val courses: List<Course> = emptyList(),
        val hour: Int = 8,
        val minute: Int = 0,
        val notificationsEnabled: Boolean = true,
        val hasOnboarded: Boolean = false,
        val speechRate: Double = 0.42,
    )

    private var loading = true

    var courses by mutableStateOf(listOf<Course>()); private set
    var hour by mutableStateOf(8); private set
    var minute by mutableStateOf(0); private set
    var notificationsEnabled by mutableStateOf(true); private set
    var hasOnboarded by mutableStateOf(false); private set
    /** Story read-aloud speed, 0.3 (slow) to 0.55 (natural), on iOS's scale. */
    var speechRate by mutableStateOf(0.42); private set

    init {
        val stored = store.getString(KEY)?.let { runCatching { JSON.decodeFromString(Stored.serializer(), it) }.getOrNull() }
        if (stored != null) {
            courses = stored.courses
            hour = stored.hour
            minute = stored.minute
            notificationsEnabled = stored.notificationsEnabled
            hasOnboarded = stored.hasOnboarded
            speechRate = stored.speechRate
        }
        loading = false
    }

    private fun save() {
        if (loading) return
        val stored = Stored(courses, hour, minute, notificationsEnabled, hasOnboarded, speechRate)
        store.putString(KEY, JSON.encodeToString(Stored.serializer(), stored))
    }

    fun setTime(hour: Int, minute: Int) { this.hour = hour; this.minute = minute; save() }
    fun setNotificationsOn(enabled: Boolean) { notificationsEnabled = enabled; save() }
    fun setOnboarded(done: Boolean) { hasOnboarded = done; save() }
    fun setReadingSpeed(rate: Double) { speechRate = rate; save() }
    private fun replaceCourses(value: List<Course>) { courses = value; save() }

    val slot: DeliverySlot get() = DeliverySlot.containing(hour)

    fun setSlot(slot: DeliverySlot) = setTime(slot.hour, slot.minute)

    val deliveryTime: LocalTime get() = LocalTime.of(hour, minute)

    fun course(id: String?): Course? = courses.firstOrNull { it.id == id }

    /** The course a story belongs to. */
    fun courseFor(story: Story): Course? = courses.firstOrNull { it.language == story.language && it.level == story.level }

    fun isLearning(language: Language): Boolean = courses.any { it.language == language }

    /** The levels a language could still be added at. */
    fun levelsToAdd(language: Language): List<Level> =
        Level.entries.filter { level -> level.isAvailable && courses.none { it.language == language && it.level == level } }

    /** When a course's story arrives: its own time, or the shared one. */
    fun time(course: Course): LocalTime = LocalTime.of(course.hour ?: hour, course.minute ?: minute)

    /** A course's delivery time; with no course, the shared time. */
    fun deliveryTime(id: String?): LocalTime = course(id)?.let(::time) ?: deliveryTime

    /** Gives one course its own delivery time. */
    fun setDeliveryTime(time: LocalTime, id: String) {
        replaceCourses(courses.map { if (it.id == id) it.copy(hour = time.hour, minute = time.minute) else it })
    }

    /** Whether a course arrives at a time of its own rather than the shared one. */
    fun hasOwnTime(id: String): Boolean = course(id)?.hour != null

    /** Puts a course back on the shared time. */
    fun useSharedTime(id: String) {
        replaceCourses(courses.map { if (it.id == id) it.copy(hour = null, minute = null) else it })
    }

    /** Starts or stops learning a language: at A1, or every level of it. */
    fun toggle(language: Language) {
        if (isLearning(language)) replaceCourses(courses.filter { it.language != language }) else add(language, Level.A1)
    }

    /** Learns a language at another level, unless it's already learned at that one. */
    fun add(language: Language, level: Level): Course? {
        if (courses.any { it.language == language && it.level == level }) return null
        val course = Course(language = language, level = level, startedOn = now().toEpochMilli())
        replaceCourses(courses + course)
        return course
    }

    fun remove(id: String) = replaceCourses(courses.filter { it.id != id })

    /** Changes a course's level, unless the language is already learned at that one. */
    fun setLevel(level: Level, id: String) {
        val course = course(id) ?: return
        if (course.level == level || courses.any { it.language == course.language && it.level == level }) return
        replaceCourses(courses.map { if (it.id == id) it.copy(level = level, startedOn = now().toEpochMilli()) else it })
    }

    /** Whether more than one course is in this language, so its level is needed to tell them apart. */
    fun hasSeveralLevels(language: Language): Boolean = courses.count { it.language == language } > 1

    companion object {
        private const val KEY = "settings.v1"
        private val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
