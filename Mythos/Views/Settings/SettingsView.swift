import SwiftUI

struct SettingsView: View {
    @Environment(AppSettings.self) private var settings
    @State private var editing: Course?
    @State private var previewSent = false
    @State private var notificationsBlocked = false
    @State private var cacheBytes = 0
    @Environment(\.openURL) private var openURL

    var body: some View {
        @Bindable var settings = settings
        NavigationStack {
            Form {
                Section {
                    ForEach(settings.courses) { course in
                        Button {
                            editing = course
                        } label: {
                            HStack(spacing: 12) {
                                LanguageBadge(language: course.language, size: 30)
                                Text(course.language.endonym).foregroundStyle(.primary)
                                Spacer()
                                LevelPill(level: course.level)
                                Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(.tertiary)
                            }
                        }
                    }
                    .onDelete { offsets in
                        settings.courses.remove(atOffsets: offsets)
                    }
                    Menu {
                        // Every language, including ones already learned: Spanish A2 alongside A1.
                        ForEach(Language.allCases.filter { !settings.levelsToAdd(for: $0).isEmpty }) { language in
                            Menu("\(language.endonym) · \(language.name)") {
                                ForEach(settings.levelsToAdd(for: language)) { level in
                                    Button("\(level.rawValue) · \(level.title)") { settings.add(language, at: level) }
                                }
                            }
                        }
                    } label: {
                        Label("Add a language or level", systemImage: "plus")
                    }
                    .disabled(Language.allCases.allSatisfy { settings.levelsToAdd(for: $0).isEmpty })
                } header: {
                    Text("Learning")
                } footer: {
                    Text("Learn a language at two levels to get a story from each every day. Changing a level starts it from its first story.")
                }
                .listRowBackground(Theme.card)

                Section {
                    Toggle("Daily notification", isOn: $settings.notificationsEnabled)
                    if settings.notificationsEnabled {
                        SlotPicker()
                            .listRowBackground(Color.clear)
                            .listRowInsets(EdgeInsets())
                        DatePicker(settings.courses.count > 1 ? "Default time" : "Time",
                                   selection: $settings.deliveryTime, displayedComponents: .hourAndMinute)
                        if settings.courses.count > 1 {
                            ForEach(settings.courses) { course in
                                CourseTimeRow(course: course)
                            }
                        }
                        Button(previewSent ? "Arriving in 5 seconds…" : "Send a preview", systemImage: "bell.badge") {
                            Task {
                                switch await NotificationScheduler.sendPreview(settings: settings) {
                                case .sent: break
                                case .blocked:
                                    notificationsBlocked = true
                                    return
                                case .nothingToSend:
                                    return
                                }
                                // Queue the daily ones too, now that permission may just have been given.
                                await NotificationScheduler.reschedule(settings: settings)
                                previewSent = true
                                try? await Task.sleep(for: .seconds(6))
                                previewSent = false
                            }
                        }
                        .disabled(previewSent)
                    }
                } header: {
                    Text("Daily story")
                } footer: {
                    Text("Each course's story arrives at its own time: swipe one to put it back on the default. Notifications are scheduled on this iPhone, weeks ahead, and topped up each time you open Mythos.")
                }
                .listRowBackground(Theme.card)

                Section("Listening") {
                    VStack(alignment: .leading) {
                        Text("Reading speed")
                        Slider(value: $settings.speechRate, in: 0.3...0.55) {
                            Text("Reading speed")
                        } minimumValueLabel: {
                            Image(systemName: "tortoise")
                        } maximumValueLabel: {
                            Image(systemName: "hare")
                        }
                    }
                }
                .listRowBackground(Theme.card)

                Section("About") {
                    LabeledContent("Stories", value: "\(Catalog.shared.stories.count), from A1 to C2")
                    Link(destination: URL(string: "https://kaikki.org")!) {
                        LabeledContent("Dictionary", value: "Wiktionary via kaikki.org")
                    }
                    LabeledContent("Translation", value: "On device, by iOS")
                    HStack {
                        LabeledContent("Saved lookups", value: cacheBytes.formatted(.byteCount(style: .file)))
                        Button("Clear") {
                            Task {
                                await DictionaryClient.shared.clearCache()
                                cacheBytes = await DictionaryClient.shared.cacheSize()
                            }
                        }
                        .buttonStyle(.borderless)
                        .disabled(cacheBytes == 0)
                    }
                    LabeledContent("Version", value: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0")
                }
                .listRowBackground(Theme.card)
            }
            .appBackground()
            .navigationTitle("Settings")
            .task { cacheBytes = await DictionaryClient.shared.cacheSize() }
            .alert("Notifications are off for Mythos", isPresented: $notificationsBlocked) {
                Button("Open Settings") {
                    if let url = URL(string: UIApplication.openNotificationSettingsURLString) { openURL(url) }
                }
                Button("Not now", role: .cancel) {}
            } message: {
                Text("Turn on Allow Notifications in Settings to get each day's story.")
            }
            .sheet(item: $editing) { course in
                LevelSheet(courseID: course.id)
            }
        }
        .onChange(of: settings.courses) { reschedule() }
        .onChange(of: settings.hour) { reschedule() }
        .onChange(of: settings.minute) { reschedule() }
        .onChange(of: settings.notificationsEnabled) { _, enabled in
            Task {
                if enabled { await NotificationScheduler.requestAuthorization() }
                await NotificationScheduler.reschedule(settings: settings)
            }
        }
    }

    private func reschedule() {
        Task { await NotificationScheduler.reschedule(settings: settings) }
    }
}

/// One course's delivery time: the default until it's changed, then its own.
private struct CourseTimeRow: View {
    let course: Course
    @Environment(AppSettings.self) private var settings

    private var language: Language { course.language }

    var body: some View {
        let own = settings.hasOwnTime(course.id)
        DatePicker(selection: Binding(get: { settings.deliveryTime(for: course.id) },
                                      set: { settings.setDeliveryTime($0, for: course.id) }),
                   displayedComponents: .hourAndMinute) {
            HStack(spacing: 10) {
                LanguageBadge(language: language, size: 28)
                VStack(alignment: .leading, spacing: 1) {
                    Text(settings.hasSeveralLevels(language) ? "\(language.endonym) \(course.level.rawValue)" : language.endonym)
                    Text(own ? "Its own time" : "Same as default")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .swipeActions {
            if own {
                Button("Use default", systemImage: "arrow.uturn.backward") { settings.useSharedTime(for: course.id) }
                    .tint(Theme.accent)
            }
        }
        .accessibilityHint("Sets when the \(language.name) story arrives")
    }
}

private struct LevelSheet: View {
    let courseID: Course.ID
    @Environment(AppSettings.self) private var settings
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let course = settings.course(courseID)
        NavigationStack {
            ScrollView {
                LevelPicker(selection: Binding(
                    get: { course?.level ?? .a1 },
                    set: { settings.setLevel($0, for: courseID) }),
                    taken: Set(settings.courses.filter { $0.language == course?.language && $0.id != courseID }.map(\.level)))
                    .padding(20)
            }
            .navigationTitle(course?.language.endonym ?? "")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done", systemImage: "checkmark") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}
