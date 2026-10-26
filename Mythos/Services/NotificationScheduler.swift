import Foundation
import UserNotifications

/// Schedules the daily story as local notifications, so no server is needed.
/// The catalog is in the app and the plan is deterministic, so each day's
/// notification can name its story weeks in advance. iOS allows 64 pending
/// notifications; we keep 60 days queued and top up whenever the app opens.
enum NotificationScheduler {
    nonisolated static let storyIDKey = "storyID"
    /// iOS keeps at most 64 pending notifications; this many are shared between
    /// the languages, one a day each, leaving room for a preview.
    private static let budget = 60
    private static let idPrefix = "daily-"

    static func authorizationStatus() async -> UNAuthorizationStatus {
        await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
    }

    /// Asks for permission if it hasn't been asked yet; whether notifications can be shown.
    static func ensureAuthorized() async -> Bool {
        switch await authorizationStatus() {
        case .authorized, .provisional, .ephemeral: true
        case .notDetermined: await requestAuthorization()
        default: false
        }
    }

    @discardableResult
    static func requestAuthorization() async -> Bool {
        (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    static func reschedule(settings: AppSettings, catalog: Catalog? = nil, now: Date = .now) async {
        let catalog = catalog ?? .shared
        let center = UNUserNotificationCenter.current()
        let pending = await center.pendingNotificationRequests().map(\.identifier).filter { $0.hasPrefix(idPrefix) }
        center.removePendingNotificationRequests(withIdentifiers: pending)

        guard settings.notificationsEnabled, !settings.courses.isEmpty else { return }
        let status = await authorizationStatus()
        guard status == .authorized || status == .provisional else { return }

        let calendar = Calendar.current
        let plan = DailyPlan(catalog: catalog, calendar: calendar)
        let today = calendar.startOfDay(for: now)
        // One notification a day per language, each at that language's time:
        // 60 days ahead for one language, 30 for two, and so on.
        let courses = settings.courses.filter { catalog.hasStories($0.language, $0.level) }
        guard !courses.isEmpty else { return }
        let daysAhead = max(7, budget / courses.count)
        for offset in 0..<daysAhead {
            guard let day = calendar.date(byAdding: .day, value: offset, to: today) else { continue }
            for course in courses {
                let time = settings.time(for: course)
                guard let fireDate = calendar.date(bySettingHour: time.hour, minute: time.minute, second: 0, of: day),
                      fireDate > now,
                      let story = plan.story(for: course, on: day) else { continue }
                let components = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: fireDate)
                let date = ISO8601DateFormatter.string(from: day, timeZone: calendar.timeZone, formatOptions: [.withFullDate])
                let request = UNNotificationRequest(
                    identifier: "\(idPrefix)\(course.language.rawValue)-\(date)",
                    content: content(for: story),
                    trigger: UNCalendarNotificationTrigger(dateMatching: components, repeats: false))
                try? await center.add(request)
            }
        }
    }

    /// "Bonjour! Your new story is here", with the story's title underneath and
    /// a hook that changes from day to day, so the notification is worth opening.
    static func content(for story: Story) -> UNMutableNotificationContent {
        let content = UNMutableNotificationContent()
        content.title = "\(story.language.greeting)! Your new story is here"
        content.subtitle = "\(story.title) · \(story.language.endonym) \(story.level.rawValue)"
        content.body = hook(for: story)
        content.sound = .default
        content.threadIdentifier = story.language.rawValue
        content.userInfo = [storyIDKey: story.id]
        return content
    }

    /// One of several invitations to read, chosen by the story so it's stable for the day.
    static func hook(for story: Story) -> String {
        let minutes = story.readingMinutes == 1 ? "a minute" : "\(story.readingMinutes) minutes"
        let hooks = [
            "A new story is ready for you to read today. \(minutes.prefix(1).uppercased() + minutes.dropFirst()), \(story.vocab.count) new words. Tap to start.",
            "“\(story.opening)” What happens next? Tap to find out.",
            "\(story.vocab.count) new words are hiding in today's story. Can you find them all?",
            "Your \(story.language.endonym) story for today takes just \(minutes). Read it now and keep your streak going.",
            "Today: \(story.subtitle.lowercased()). A short story, at your level, ready when you are.",
        ]
        return hooks[(story.number - 1) % hooks.count]
    }

    enum PreviewResult {
        case sent
        /// Notifications are turned off for Mythos.
        case blocked
        /// No course has a story to feature.
        case nothingToSend
    }

    /// Sends today's featured story in a few seconds, to see what it looks like.
    static func sendPreview(settings: AppSettings) async -> PreviewResult {
        guard await ensureAuthorized() else { return .blocked }
        guard let story = DailyPlan(catalog: .shared).featured(in: settings.courses, on: .now) else { return .nothingToSend }
        let request = UNNotificationRequest(identifier: "preview", content: content(for: story),
                                            trigger: UNTimeIntervalNotificationTrigger(timeInterval: 5, repeats: false))
        try? await UNUserNotificationCenter.current().add(request)
        return .sent
    }
}
