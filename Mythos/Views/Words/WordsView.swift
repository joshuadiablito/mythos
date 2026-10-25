import SwiftUI

/// Words saved from lookups, by language.
struct WordsView: View {
    @Environment(ProgressStore.self) private var progress
    @State private var lookup: WordLookup?

    private var byLanguage: [(Language, [SavedWord])] {
        Language.allCases.compactMap { language in
            let words = progress.savedWords.filter { $0.language == language }
            return words.isEmpty ? nil : (language, words)
        }
    }

    var body: some View {
        NavigationStack {
            List {
                ForEach(byLanguage, id: \.0) { language, words in
                    Section {
                        ForEach(words) { word in
                            Button {
                                lookup = WordLookup(word: word.word, language: word.language, gloss: word.meaning, storyTitle: word.storyTitle)
                            } label: {
                                WordRow(word: word)
                            }
                            .buttonStyle(.plain)
                        }
                        .onDelete { offsets in progress.remove(offsets.map { words[$0] }) }
                    } header: {
                        HStack(spacing: 8) {
                            LanguageBadge(language: language, size: 24)
                            Text(language.endonym)
                        }
                    }
                    .listRowBackground(Theme.card)
                }
            }
            .overlay {
                if progress.savedWords.isEmpty {
                    ContentUnavailableView {
                        Label("No saved words yet", systemImage: "bookmark")
                    } description: {
                        Text("Tap a word in a story, then \(Image(systemName: "bookmark")) Save to keep it here.")
                    }
                }
            }
            .appBackground()
            .navigationTitle("Words")
            .sheet(item: $lookup) { LookupSheet(lookup: $0) }
        }
    }
}

private struct WordRow: View {
    let word: SavedWord

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            VStack(alignment: .leading, spacing: 3) {
                Text(word.word).font(.system(.body, design: .serif, weight: .semibold))
                if let meaning = word.meaning {
                    Text(meaning).font(.subheadline).foregroundStyle(.secondary).lineLimit(2)
                }
            }
            Spacer()
            ListenButton(text: word.word, language: word.language)
                .controlSize(.small)
        }
        .padding(.vertical, 2)
        .contentShape(.rect)
    }
}
