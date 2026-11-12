package com.joshuadiablito.mythos.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Which story each day brings. Deterministic, so a notification scheduled
 * ahead names the same story the app shows on the day.
 */
class DailyPlan(val catalog: Catalog, val zone: ZoneId = ZoneId.systemDefault()) {
    /** Days from `start` to `date`, by calendar day. */
    private fun days(start: Instant, date: Instant): Long =
        ChronoUnit.DAYS.between(start.atZone(zone).toLocalDate(), date.atZone(zone).toLocalDate())

    /** A course's story for a day: its level's stories in order from the day
     *  the course started, starting again after the last. */
    fun story(course: Course, on: Instant): Story? {
        val stories = catalog.stories(course.language, course.level)
        if (stories.isEmpty()) return null
        val n = stories.size
        return stories[Math.floorMod(days(course.startedOnInstant, on), n.toLong()).toInt()]
    }

    /** The stories a course has brought by `date`, newest first: one a day from
     *  the day it started. Stories still to come are never listed. */
    fun arrived(course: Course, by: Instant): List<Story> {
        val stories = catalog.stories(course.language, course.level)
        val elapsed = days(course.startedOnInstant, by)
        if (elapsed < 0) return emptyList()
        return stories.take(minOf(stories.size.toLong(), elapsed + 1).toInt()).reversed()
    }

    /** How many of a course's stories are still to come. */
    fun remaining(course: Course, by: Instant): Int =
        catalog.stories(course.language, course.level).size - arrived(course, by).size

    /** The story a day features, when there are several courses: they take turns. */
    fun featured(courses: List<Course>, on: Instant): Story? {
        val playable = courses.filter { catalog.hasStories(it.language, it.level) }
        if (playable.isEmpty()) return null
        val day = ChronoUnit.DAYS.between(REFERENCE, on.atZone(zone).toLocalDate())
        val course = playable[Math.floorMod(day, playable.size.toLong()).toInt()]
        return story(course, on)
    }

    companion object {
        /** iOS's reference date, so both apps rotate the same way. */
        private val REFERENCE: LocalDate = LocalDate.of(2001, 1, 1)
    }
}
