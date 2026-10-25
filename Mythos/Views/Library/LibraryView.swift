import SwiftUI

/// Every story in the catalog, by language and level, to read ahead or re-read.
struct LibraryView: View {
    @Environment(AppSettings.self) private var settings
    @Environment(ProgressStore.self) private var progress
    @State private var language: Language?
    @State private var level: Level = .a1
    @State private var search = ""

    private let catalog = Catalog.shared

    private var current: Language { language ?? settings.courses.first?.language ?? .french }

    private var stories: [Story] {
        let all = catalog.stories(current, level)
        guard !search.isEmpty else { return all }
        return all.filter {
            $0.title.localizedStandardContains(search) || $0.subtitle.localizedStandardContains(search)
                || $0.topic.localizedStandardContains(search) || $0.text.localizedStandardContains(search)
        }
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Picker("Level", selection: $level) {
                        ForEach(Level.allCases.filter(\.isAvailable)) { Text($0.rawValue).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets(top: 4, leading: 0, bottom: 4, trailing: 0))
                } footer: {
                    let all = catalog.stories(current, level)
                    Text("\(progress.readCount(all)) of \(all.count) read · \(level.title)")
                }

                Section {
                    ForEach(stories) { story in
                        NavigationLink(value: story) {
                            LibraryRow(story: story, isRead: progress.isRead(story))
                        }
                        .swipeActions(edge: .leading) {
                            Button(progress.isRead(story) ? "Unread" : "Read", systemImage: progress.isRead(story) ? "circle" : "checkmark") {
                                progress.toggleRead(story)
                            }
                            .tint(Theme.accent)
                        }
                    }
                }
                .listRowBackground(Theme.card)
            }
            .overlay {
                if stories.isEmpty && !search.isEmpty { ContentUnavailableView.search(text: search) }
            }
            .appBackground()
            .navigationTitle("Library")
            .searchable(text: $search, prompt: "Titles, topics and words")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Picker("Language", selection: Binding(get: { current }, set: { language = $0 })) {
                            ForEach(Language.allCases) { l in
                                Text("\(l.endonym) · \(l.name)").tag(l)
                            }
                        }
                    } label: {
                        HStack(spacing: 6) {
                            Text(current.monogram).font(.system(.subheadline, design: .serif, weight: .semibold))
                            Image(systemName: "chevron.down").font(.caption2.weight(.bold))
                        }
                    }
                    .accessibilityLabel("Language: \(current.name)")
                }
            }
            .navigationDestination(for: Story.self) { StoryReaderView(story: $0) }
            .onAppear {
                if language == nil, let course = settings.courses.first {
                    level = course.level.isAvailable ? course.level : .a1
                }
            }
        }
    }
}

private struct LibraryRow: View {
    let story: Story
    let isRead: Bool

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: story.symbol)
                .font(.body)
                .foregroundStyle(isRead ? Color.secondary : Theme.accent)
                .frame(width: 40, height: 40)
                .background((isRead ? Color.secondary : Theme.accent).opacity(0.1), in: .rect(cornerRadius: 12, style: .continuous))
            VStack(alignment: .leading, spacing: 3) {
                Text(story.title)
                    .font(.system(.body, design: .serif, weight: .semibold))
                    .foregroundStyle(isRead ? .secondary : .primary)
                Text("\(story.subtitle) · \(story.readingMinutes) min")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            if isRead {
                Image(systemName: "checkmark.circle.fill").foregroundStyle(Theme.accent)
                    .accessibilityLabel("Read")
            }
        }
        .padding(.vertical, 4)
    }
}
