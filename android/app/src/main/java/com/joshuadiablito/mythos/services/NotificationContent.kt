package com.joshuadiablito.mythos.services

import com.joshuadiablito.mythos.model.Story

/** "Bonjour! Your new story is here", with the story's title underneath and
 *  a hook that changes from day to day, so the notification is worth opening. */
data class NotificationContent(val title: String, val subtitle: String, val body: String) {
    companion object {
        fun forStory(story: Story) = NotificationContent(
            title = "${story.language.greeting}! Your new story is here",
            subtitle = "${story.title} · ${story.language.endonym} ${story.level.code}",
            body = hook(story),
        )

        /** One of several invitations to read, chosen by the story so it's stable for the day. */
        fun hook(story: Story): String {
            val minutes = if (story.readingMinutes == 1) "a minute" else "${story.readingMinutes} minutes"
            val hooks = listOf(
                "A new story is ready for you to read today. ${minutes.replaceFirstChar { it.uppercase() }}, ${story.vocab.size} new words. Tap to start.",
                "“${story.opening}” What happens next? Tap to find out.",
                "${story.vocab.size} new words are hiding in today's story. Can you find them all?",
                "Your ${story.language.endonym} story for today takes just $minutes. Read it now and keep your streak going.",
                "Today: ${story.subtitle.lowercase()}. A short story, at your level, ready when you are.",
            )
            return hooks[(story.number - 1) % hooks.size]
        }
    }
}
