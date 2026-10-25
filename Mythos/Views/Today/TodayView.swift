import SwiftUI
import UserNotifications

struct TodayView: View {
    @Environment(AppSettings.self) private var settings
    @Environment(ProgressStore.self) private var progress
    @Environment(Router.self) private var router
    @State private var path: [Story] = []
    @State private var notificationStatus: UNAuthorizationStatus = .authorized

    private let catalog = Catalog.shared
    private var plan: DailyPlan { DailyPlan(catalog: catalog) }

    var body: some View {
        NavigationStack(path: $path) {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    if settings.notificationsEnabled && (notificationStatus == .denied || notificationStatus == .notDetermined) {
                        NotificationsOffBanner(status: notificationStatus) {
                            Task {
                                await NotificationScheduler.requestAuthorization()
                                notificationStatus = await NotificationScheduler.authorizationStatus()
                                await NotificationScheduler.reschedule(settings: settings)
                            }
                        }
                    }
                    ForEach(settings.courses) { course in
                        if let story = plan.story(for: course, on: .now) {
                            NavigationLink(value: story) {
                                TodayStoryCard(story: story, isRead: progress.isRead(story))
                            }
                            .buttonStyle(.plain)
                        } else {
                            ComingSoonCard(course: course)
                        }
                    }
                    if !settings.courses.isEmpty {
                        UpNext(plan: plan)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 32)
            }
            .appBackground()
            .navigationTitle("Today")
            .navigationSubtitle(Date.now.formatted(.dateTime.weekday(.wide).day().month(.wide)))
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    StreakBadge(days: progress.streak())
                }
            }
            .navigationDestination(for: Story.self) { story in
                StoryReaderView(story: story)
            }
        }
        .task(id: settings.notificationsEnabled) {
            notificationStatus = await NotificationScheduler.authorizationStatus()
        }
        .onChange(of: router.pendingStoryID, initial: true) { _, id in
            guard let id, let story = catalog.story(id: id) else { return }
            path = [story]
            router.pendingStoryID = nil
        }
    }
}

private struct TodayStoryCard: View {
    let story: Story
    let isRead: Bool
    @Environment(Speaker.self) private var speaker
    @Environment(AppSettings.self) private var settings

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 10) {
                LanguageBadge(language: story.language)
                Text(story.language.endonym)
                    .font(.subheadline.weight(.semibold))
                LevelPill(level: story.level)
                Spacer()
                if isRead {
                    Label("Read", systemImage: "checkmark.circle.fill")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Theme.accent)
                }
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(story.title)
                    .font(.system(.title, design: .serif, weight: .bold))
                    .foregroundStyle(Theme.ink)
                Text(story.subtitle)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            Text(story.paragraphs.first ?? "")
                .font(Theme.storyFont(size: 17))
                .foregroundStyle(Theme.ink.opacity(0.85))
                .lineLimit(3)
                .lineSpacing(4)

            HStack {
                StoryMeta(story: story)
                Spacer()
                let speaking = speaker.isSpeaking(story.id)
                Button {
                    if speaking { speaker.stop() } else {
                        speaker.speak(story.text, language: story.language, rate: settings.speechRate, id: story.id)
                    }
                } label: {
                    Image(systemName: speaking ? "stop.fill" : "play.fill")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Theme.accent)
                        .frame(width: 34, height: 34)
                        .contentTransition(.symbolEffect(.replace))
                }
                .buttonStyle(.plain)
                .glassEffect(.regular.tint(Theme.accent.opacity(0.15)).interactive(), in: .circle)
                .accessibilityLabel(speaking ? "Stop reading" : "Read the story aloud")
                Image(systemName: "arrow.right")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Theme.accent)
                    .frame(width: 34, height: 34)
                    .glassEffect(.regular.tint(Theme.accent.opacity(0.15)).interactive(), in: .circle)
            }
        }
        .card()
        .contentShape(.rect)
        .accessibilityElement(children: .contain)
        .accessibilityHint("Opens the story")
    }
}

private struct ComingSoonCard: View {
    let course: Course

    var body: some View {
        HStack(spacing: 14) {
            LanguageBadge(language: course.language)
            VStack(alignment: .leading, spacing: 2) {
                Text("\(course.language.endonym) \(course.level.rawValue) is on its way")
                    .font(.headline)
                Text("Stories for \(course.level.title.lowercased()) learners are being written. Choose A1 or A2 in Settings to read now.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .card()
    }
}

/// Tomorrow's story, as a small teaser.
private struct UpNext: View {
    let plan: DailyPlan
    @Environment(AppSettings.self) private var settings

    var body: some View {
        let tomorrow = Calendar.current.date(byAdding: .day, value: 1, to: .now)!
        if let story = plan.featured(in: settings.courses, on: tomorrow) {
            VStack(alignment: .leading, spacing: 8) {
                Text("Tomorrow")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.secondary)
                    .textCase(.uppercase)
                HStack(spacing: 12) {
                    Image(systemName: story.symbol)
                        .font(.title3)
                        .foregroundStyle(Theme.warm)
                        .frame(width: 40, height: 40)
                        .background(Theme.warm.opacity(0.14), in: .rect(cornerRadius: 12))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(story.title).font(.system(.headline, design: .serif))
                        Text("\(story.language.endonym) · arrives \(settings.deliveryTime.formatted(date: .omitted, time: .shortened))")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .padding(.top, 8)
        }
    }
}

struct StreakBadge: View {
    let days: Int

    var body: some View {
        Label("\(days)", systemImage: "flame.fill")
            .labelStyle(.titleAndIcon)
            .font(.subheadline.weight(.semibold).monospacedDigit())
            .foregroundStyle(days > 0 ? Theme.warm : .secondary)
            .accessibilityLabel(days == 1 ? "1 day streak" : "\(days) day streak")
    }
}

private struct NotificationsOffBanner: View {
    let status: UNAuthorizationStatus
    /// Asks for permission, when it hasn't been asked yet.
    let onAllow: () -> Void
    @Environment(\.openURL) private var openURL

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "bell.slash.fill")
                .foregroundStyle(Theme.warm)
            VStack(alignment: .leading, spacing: 2) {
                Text("Notifications are off").font(.subheadline.weight(.semibold))
                Text("Turn them on to get each day's story.").font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Group {
                if status == .notDetermined {
                    Button("Turn on", action: onAllow)
                } else {
                    Button("Settings") {
                        if let url = URL(string: UIApplication.openNotificationSettingsURLString) { openURL(url) }
                    }
                }
            }
            .buttonStyle(.glass)
            .controlSize(.small)
        }
        .card(padding: 14, radius: 20)
    }
}
