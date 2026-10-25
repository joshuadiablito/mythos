import Foundation
import UserNotifications

/// Schedules the daily story as local notifications, so no server is needed.
/// The catalog is in the app and the plan is deterministic, so each day's
/// notification can name its story weeks in advance. iOS allows 64 pending
/// notifications; we keep 60 days queued and top up whenever the app opens.
enum NotificationScheduler {
    nonisolated static let storyIDKey = "storyID"
    private static let daysAhead = 60
    private static let idPrefix = "daily-"

    static func authorizationStatus() async -> UNAuthorizationStatus {
        await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
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
        for offset in 0..<daysAhead {
            guard let day = calendar.date(byAdding: .day, value: offset, to: today),
                  let fireDate = calendar.date(bySettingHour: settings.hour, minute: settings.minute, second: 0, of: day),
                  fireDate > now,
                  let story = plan.featured(in: settings.courses, on: day) else { continue }

            let components = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: fireDate)
            let request = UNNotificationRequest(
                identifier: idPrefix + ISO8601DateFormatter.string(from: day, timeZone: calendar.timeZone, formatOptions: [.withFullDate]),
                content: content(for: story),
                trigger: UNCalendarNotificationTrigger(dateMatching: components, repeats: false))
            try? await center.add(request)
        }
    }

    static func content(for story: Story) -> UNMutableNotificationContent {
        let content = UNMutableNotificationContent()
        content.title = story.title
        content.subtitle = "\(story.language.endonym) · \(story.level.rawValue) · \(story.readingMinutes) min"
        content.body = story.opening
        content.sound = .default
        content.threadIdentifier = story.language.rawValue
        content.userInfo = [storyIDKey: story.id]
        return content
    }

    /// Sends today's featured story in a few seconds, to see what it looks like.
    static func sendPreview(settings: AppSettings) async {
        guard let story = DailyPlan(catalog: .shared).featured(in: settings.courses, on: .now) else { return }
        let request = UNNotificationRequest(identifier: "preview", content: content(for: story),
                                            trigger: UNTimeIntervalNotificationTrigger(timeInterval: 5, repeats: false))
        try? await UNUserNotificationCenter.current().add(request)
    }
}
