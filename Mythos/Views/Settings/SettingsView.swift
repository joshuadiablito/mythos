import SwiftUI

struct SettingsView: View {
    @Environment(AppSettings.self) private var settings
    @State private var editing: Language?
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
                            editing = course.language
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
                        ForEach(Language.allCases.filter { settings.course(for: $0) == nil }) { language in
                            Button("\(language.endonym) · \(language.name)") { settings.toggle(language) }
                        }
                    } label: {
                        Label("Add a language", systemImage: "plus")
                    }
                    .disabled(settings.courses.count == Language.allCases.count)
                } header: {
                    Text("Learning")
                } footer: {
                    Text("Changing a level starts it from its first story.")
                }
                .listRowBackground(Theme.card)

                Section {
                    Toggle("Daily notification", isOn: $settings.notificationsEnabled)
                    if settings.notificationsEnabled {
                        SlotPicker()
                            .listRowBackground(Color.clear)
                            .listRowInsets(EdgeInsets())
                        DatePicker("Time", selection: $settings.deliveryTime, displayedComponents: .hourAndMinute)
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
                    Text("Notifications are scheduled on this iPhone, 60 days ahead, and topped up each time you open Mythos. With more than one language, they take turns.")
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
                    LabeledContent("Stories", value: "\(Catalog.shared.stories.count), written for A1 and A2")
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
            .sheet(item: $editing) { language in
                LevelSheet(language: language)
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

private struct LevelSheet: View {
    let language: Language
    @Environment(AppSettings.self) private var settings
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                LevelPicker(selection: Binding(
                    get: { settings.course(for: language)?.level ?? .a1 },
                    set: { settings.setLevel($0, for: language) }))
                    .padding(20)
            }
            .navigationTitle(language.endonym)
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
