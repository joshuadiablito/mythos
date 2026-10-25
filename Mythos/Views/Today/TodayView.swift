import SwiftUI
import UserNotifications

/// The front page: a greeting in the language being learned, today's story
/// as a large living cover (one per language, side by side), the week's
/// reading, and a glimpse of tomorrow.
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
                VStack(alignment: .leading, spacing: 26) {
                    Masthead(language: settings.courses.first?.language)
                        .padding(.horizontal, 24)

                    if settings.notificationsEnabled && (notificationStatus == .denied || notificationStatus == .notDetermined) {
                        NotificationsOffBanner(status: notificationStatus) {
                            Task {
                                await NotificationScheduler.requestAuthorization()
                                notificationStatus = await NotificationScheduler.authorizationStatus()
                                await NotificationScheduler.reschedule(settings: settings)
                            }
                        }
                        .padding(.horizontal, 20)
                    }

                    TodayCovers(courses: settings.courses, plan: plan) { path = [$0] }

                    WeekStrip()
                        .padding(.horizontal, 20)

                    if !settings.courses.isEmpty {
                        Tomorrow(plan: plan)
                            .padding(.horizontal, 20)
                    }
                }
                .padding(.top, 8)
                .padding(.bottom, 40)
            }
            .scrollIndicators(.hidden)
            .appBackground()
            .toolbar(.hidden, for: .navigationBar)
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

// MARK: - Masthead

/// "Καλησπέρα" over "Κυριακή 25 Οκτωβρίου": the day, in the language being learned.
private struct Masthead: View {
    let language: Language?

    var body: some View {
        TimelineView(.everyMinute) { context in
            VStack(alignment: .leading, spacing: 4) {
                Text((language ?? .french).longDate(context.date).localizedUppercase)
                    .font(.footnote.weight(.semibold))
                    .tracking(1.2)
                    .foregroundStyle(Theme.accent)
                Text(language?.greeting(at: context.date) ?? "Today")
                    .font(.system(size: 40, weight: .bold, design: .serif))
                    .foregroundStyle(Theme.ink)
                    .minimumScaleFactor(0.7)
                    .lineLimit(1)
            }
            .padding(.top, 12)
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isHeader)
        }
    }
}

// MARK: - Today's covers

/// One large cover per language, paging sideways when there are several.
private struct TodayCovers: View {
    let courses: [Course]
    let plan: DailyPlan
    let open: (Story) -> Void

    var body: some View {
        ScrollView(.horizontal) {
            LazyHStack(spacing: 14) {
                ForEach(courses) { course in
                    Group {
                        if let story = plan.story(for: course, on: .now) {
                            HeroCover(story: story, open: open)
                        } else {
                            ComingSoonCover(course: course)
                        }
                    }
                    // The content margins already narrow the container; with several
                    // languages, leave the next cover peeking in from the edge.
                    .containerRelativeFrame(.horizontal) { width, _ in courses.count > 1 ? width - 28 : width }
                }
            }
            .scrollTargetLayout()
        }
        .contentMargins(.horizontal, 20, for: .scrollContent)
        .scrollTargetBehavior(.viewAligned)
        .scrollIndicators(.hidden)
        .scrollDisabled(courses.count < 2)
    }
}

/// Today's story as a tall, slowly moving cover, with its title and a glass
/// panel of actions across the bottom.
private struct HeroCover: View {
    let story: Story
    let open: (Story) -> Void
    @Environment(ProgressStore.self) private var progress
    @Environment(Speaker.self) private var speaker
    @Environment(AppSettings.self) private var settings

    var body: some View {
        let isRead = progress.isRead(story)
        let speaking = speaker.isSpeaking(story.id)
        ZStack(alignment: .bottomLeading) {
            StoryCover(story: story, animated: true)

            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 8) {
                    Text("\(story.language.endonym) · \(story.level.rawValue)")
                        .font(.caption.weight(.bold))
                        .padding(.horizontal, 10).padding(.vertical, 6)
                        .glassEffect(.regular.tint(.white.opacity(0.08)), in: .capsule)
                    Spacer()
                    if isRead {
                        Label("Read", systemImage: "checkmark")
                            .font(.caption.weight(.bold))
                            .padding(.horizontal, 10).padding(.vertical, 6)
                            .glassEffect(.regular.tint(.green.opacity(0.35)), in: .capsule)
                            .transition(.scale.combined(with: .opacity))
                    }
                }
                .foregroundStyle(.white)

                Spacer(minLength: 60)

                Text("Today's story · \(story.topic)".localizedUppercase)
                    .font(.caption2.weight(.bold))
                    .tracking(1.4)
                    .foregroundStyle(.white.opacity(0.8))
                Text(story.title)
                    .font(.system(size: 38, weight: .bold, design: .serif))
                    .foregroundStyle(.white)
                    .shadow(color: .black.opacity(0.25), radius: 12, y: 4)
                    .minimumScaleFactor(0.6)
                    .lineLimit(2)
                    .padding(.top, 4)
                Text(story.subtitle)
                    .font(.title3)
                    .foregroundStyle(.white.opacity(0.85))
                    .padding(.top, 2)

                HStack(spacing: 10) {
                    Button { open(story) } label: {
                        Label(isRead ? "Read again" : "Start reading", systemImage: "book.fill")
                            .font(.headline)
                            .foregroundStyle(CoverPalette.forTopic(story.topic).deep)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 6)
                    }
                    .buttonStyle(.glassProminent)
                    .tint(.white)

                    Button {
                        if speaking { speaker.stop() } else {
                            speaker.speak(story.text, language: story.language, rate: settings.speechRate, id: story.id)
                        }
                    } label: {
                        Image(systemName: speaking ? "stop.fill" : "headphones")
                            .font(.headline)
                            .frame(width: 30, height: 30)
                            .contentTransition(.symbolEffect(.replace))
                    }
                    .buttonStyle(.glass)
                    .buttonBorderShape(.circle)
                    .accessibilityLabel(speaking ? "Stop listening" : "Listen to the story")
                }
                .controlSize(.large)
                .foregroundStyle(.white)
                .padding(.top, 18)

                HStack(spacing: 14) {
                    Label("\(story.readingMinutes) min", systemImage: "clock")
                    Label("\(story.vocab.count) new words", systemImage: "sparkles")
                }
                .labelStyle(.compact)
                .font(.caption.weight(.medium))
                .foregroundStyle(.white.opacity(0.8))
                .padding(.top, 12)
            }
            .padding(20)
        }
        .frame(height: 520)
        .clipShape(.rect(cornerRadius: 34, style: .continuous))
        .shadow(color: CoverPalette.forTopic(story.topic).deep.opacity(0.35), radius: 24, y: 14)
        .contentShape(.rect(cornerRadius: 34))
        .onTapGesture { open(story) }
        .animation(.snappy, value: isRead)
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Today's story: \(story.title), \(story.subtitle)")
    }
}

private struct ComingSoonCover: View {
    let course: Course

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Spacer()
            Image(systemName: "hourglass")
                .font(.largeTitle)
            Text("\(course.language.endonym) \(course.level.rawValue) is being written")
                .font(.system(.title2, design: .serif, weight: .bold))
            Text("Stories for \(course.level.title.lowercased()) learners are on their way. Choose A1 or A2 in Settings to read now.")
                .font(.subheadline)
                .opacity(0.85)
        }
        .foregroundStyle(.white)
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .frame(height: 520)
        .background(MeshGradient(width: 2, height: 2, points: [[0, 0], [1, 0], [0, 1], [1, 1]],
                                 colors: [Color(hex: 0x3F4C78), Color(hex: 0x141A2E), Color(hex: 0x2E5BD8), Color(hex: 0x141A2E)]))
        .clipShape(.rect(cornerRadius: 34, style: .continuous))
    }
}

// MARK: - The week

/// Seven days, a dot for each, filled on the days a story was finished.
private struct WeekStrip: View {
    @Environment(ProgressStore.self) private var progress

    var body: some View {
        let week = progress.week()
        let streak = progress.streak()
        let calendar = Calendar.current
        HStack(alignment: .center, spacing: 16) {
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 4) {
                    Image(systemName: "flame.fill")
                        .foregroundStyle(streak > 0 ? AnyShapeStyle(LinearGradient(colors: [Color(hex: 0xFFB347), Color(hex: 0xF2545B)], startPoint: .top, endPoint: .bottom)) : AnyShapeStyle(.tertiary))
                        .symbolEffect(.bounce, value: streak)
                    Text("\(streak)")
                        .font(.system(.title, design: .rounded, weight: .bold))
                        .contentTransition(.numericText())
                }
                Text(streak == 1 ? "day in a row" : "days in a row")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .frame(minWidth: 78, alignment: .leading)

            HStack(spacing: 0) {
                ForEach(week, id: \.day) { entry in
                    let isToday = calendar.isDateInToday(entry.day)
                    VStack(spacing: 6) {
                        Text(entry.day.formatted(.dateTime.weekday(.narrow)))
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(isToday ? Theme.accent : .secondary)
                        ZStack {
                            Circle()
                                .fill(entry.read ? AnyShapeStyle(Theme.accent.gradient) : AnyShapeStyle(Color.primary.opacity(0.06)))
                            if entry.read {
                                Image(systemName: "checkmark")
                                    .font(.caption2.weight(.heavy))
                                    .foregroundStyle(.white)
                            }
                        }
                        .frame(width: 26, height: 26)
                        .overlay {
                            if isToday && !entry.read {
                                Circle().strokeBorder(Theme.accent, style: StrokeStyle(lineWidth: 1.5, dash: [3, 3]))
                            }
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("\(entry.day.formatted(.dateTime.weekday(.wide))): \(entry.read ? "read" : "not read")")
                }
            }
        }
        .padding(18)
        .background(Theme.card, in: .rect(cornerRadius: 26, style: .continuous))
        .shadow(color: .black.opacity(0.05), radius: 16, y: 8)
    }
}

// MARK: - Tomorrow

/// Tomorrow's story, its cover blurred and its title kept back until it arrives.
private struct Tomorrow: View {
    let plan: DailyPlan
    @Environment(AppSettings.self) private var settings

    var body: some View {
        let tomorrow = Calendar.current.date(byAdding: .day, value: 1, to: .now)!
        if let story = plan.featured(in: settings.courses, on: tomorrow) {
            HStack(spacing: 16) {
                ZStack {
                    StoryCover(story: story, symbolScale: 0.9)
                        .blur(radius: 10)
                    Image(systemName: "lock.fill")
                        .font(.title3)
                        .foregroundStyle(.white)
                        .shadow(radius: 4)
                }
                .frame(width: 74, height: 96)
                .clipShape(.rect(cornerRadius: 14, style: .continuous))

                VStack(alignment: .leading, spacing: 4) {
                    Text("TOMORROW")
                        .font(.caption2.weight(.bold))
                        .tracking(1.2)
                        .foregroundStyle(.secondary)
                    // Just the topic: the story itself is a surprise until it arrives.
                    Text("A story about \(story.topic.lowercased())")
                        .font(.system(.title3, design: .serif, weight: .semibold))
                        .foregroundStyle(Theme.ink)
                    Text("\(story.language.endonym) · arrives at \(settings.deliveryTime.formatted(date: .omitted, time: .shortened))")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
            }
            .accessibilityElement(children: .combine)
        }
    }
}

// MARK: - Notifications

private struct NotificationsOffBanner: View {
    let status: UNAuthorizationStatus
    /// Asks for permission, when it hasn't been asked yet.
    let onAllow: () -> Void
    @Environment(\.openURL) private var openURL

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "bell.badge.fill")
                .font(.title3)
                .foregroundStyle(Theme.warm)
                .symbolEffect(.wiggle, options: .repeat(.periodic(delay: 4)))
            VStack(alignment: .leading, spacing: 2) {
                Text("Get each day's story").font(.subheadline.weight(.semibold))
                Text("A notification when it arrives, at the time you chose.").font(.caption).foregroundStyle(.secondary)
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
