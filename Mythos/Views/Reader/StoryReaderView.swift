import SwiftUI
import Translation

/// A word to look up, and where it came from.
struct WordLookup: Identifiable, Hashable {
    let word: String
    let language: Language
    /// Its meaning in the story, if it's in the story's glossary.
    var gloss: String?
    var storyTitle: String?

    var id: String { "\(language.rawValue)|\(word)" }
}

struct StoryReaderView: View {
    let story: Story

    @Environment(ProgressStore.self) private var progress
    @Environment(AppSettings.self) private var settings
    @Environment(Speaker.self) private var speaker
    @AppStorage("storyFontSize") private var fontSize: Double = 21
    @State private var selected: WordTap?
    @State private var lookup: WordLookup?
    @State private var showTranslation = false
    @State private var readFraction: Double = 0
    @State private var showCompletion = false

    private var vocab: [String] { story.vocab.map(\.word) }
    private var isRead: Bool { progress.isRead(story) }
    private var isSpeaking: Bool { speaker.isSpeaking(story.id) }

    /// The spoken word's range within one paragraph. The story is read as
    /// `story.text`: paragraphs joined by a blank line, two UTF-16 units.
    private func spokenRange(inParagraph index: Int) -> NSRange? {
        guard isSpeaking, let range = speaker.spokenRange else { return nil }
        let start = story.paragraphs.prefix(index).reduce(0) { $0 + ($1 as NSString).length + 2 }
        let length = (story.paragraphs[index] as NSString).length
        let local = NSIntersectionRange(range, NSRange(location: start, length: length))
        return local.length > 0 ? NSRange(location: local.location - start, length: local.length) : nil
    }

    private func toggleListening() {
        if !isSpeaking {
            speaker.speak(story.text, language: story.language, rate: settings.speechRate, id: story.id)
        } else if speaker.isPaused {
            speaker.resume()
        } else {
            speaker.pause()
        }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                CoverHeader(story: story)

                VStack(alignment: .leading, spacing: 28) {
                    VStack(alignment: .leading, spacing: fontSize * 0.9) {
                        ForEach(Array(story.paragraphs.enumerated()), id: \.offset) { index, paragraph in
                            TappableParagraph(text: paragraph, index: index, language: story.language, vocab: vocab,
                                              selected: selected, spoken: spokenRange(inParagraph: index),
                                              fontSize: fontSize) { tap in
                                selected = tap
                                lookup = WordLookup(word: tap.word, language: story.language,
                                                    gloss: story.gloss(for: tap.word), storyTitle: story.title)
                            }
                        }
                    }

                    Label("Tap any word for its meaning and every form. Words with an orange underline are new in this story.",
                          systemImage: "hand.tap")
                        .font(.footnote)
                        .foregroundStyle(.secondary)

                    TranslationSection(story: story)

                    VocabularySection(story: story) { item in
                        lookup = WordLookup(word: item.word, language: story.language, gloss: item.gloss, storyTitle: story.title)
                    }

                    finishButton
                }
                .padding(.horizontal, 22)
                .padding(.top, 28)
                .padding(.bottom, 40)
            }
        }
        .ignoresSafeArea(edges: .top)
        .onScrollGeometryChange(for: Double.self) { geometry in
            let scrollable = geometry.contentSize.height - geometry.containerSize.height
            return scrollable > 0 ? min(1, max(0, (geometry.contentOffset.y + geometry.contentInsets.top) / scrollable)) : 0
        } action: { _, fraction in
            readFraction = fraction
        }
        #if DEBUG
        // `-scrollAnchor 0.62` opens the reader scrolled down that far, for screenshots.
        .defaultScrollAnchor(UserDefaults.standard.object(forKey: "scrollAnchor").map { _ in
            UnitPoint(x: 0.5, y: UserDefaults.standard.double(forKey: "scrollAnchor"))
        })
        #endif
        .appBackground()
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        .toolbar { toolbar }
        .sheet(item: $lookup, onDismiss: { selected = nil }) { item in
            LookupSheet(lookup: item)
        }
        .translationPresentation(isPresented: $showTranslation, text: story.text)
        .onDisappear { if isSpeaking { speaker.stop() } }
        #if DEBUG
        .onAppear {
            // `-autoplay YES` starts reading aloud; `-lookupWord mange` opens a lookup. For screenshots.
            if UserDefaults.standard.bool(forKey: "autoplay"), !isSpeaking { toggleListening() }
            // `-showCompletion YES` opens the finished-story sheet.
            if UserDefaults.standard.bool(forKey: "showCompletion") { showCompletion = true }
            if let word = UserDefaults.standard.string(forKey: "lookupWord"), lookup == nil {
                lookup = WordLookup(word: word, language: story.language, gloss: story.gloss(for: word), storyTitle: story.title)
            }
        }
        #endif
    }

    private var finishButton: some View {
        Button {
            if isRead {
                withAnimation(.snappy) { progress.toggleRead(story) }
            } else {
                withAnimation(.snappy) { progress.toggleRead(story) }
                showCompletion = true
            }
        } label: {
            Label(isRead ? "Finished · tap to mark unread" : "I've read it", systemImage: isRead ? "checkmark.seal.fill" : "checkmark.circle")
                .font(.headline)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
                .contentTransition(.symbolEffect(.replace))
        }
        .buttonStyle(.glassProminent)
        .controlSize(.large)
        .tint(isRead ? Color(hex: 0x2E9E4F) : Theme.accent)
        .sensoryFeedback(.success, trigger: isRead) { _, new in new }
        .sheet(isPresented: $showCompletion) {
            CompletionSheet(story: story)
        }
    }

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
        ToolbarItem(placement: .principal) {
            ReadingProgress(fraction: readFraction)
        }
        ToolbarItem(placement: .topBarTrailing) {
            Menu {
                Picker("Text size", selection: $fontSize) {
                    Text("Small").tag(18.0)
                    Text("Medium").tag(21.0)
                    Text("Large").tag(24.0)
                    Text("Extra large").tag(28.0)
                }
            } label: {
                Image(systemName: "textformat.size")
            }
            .accessibilityLabel("Text size")
        }
        ToolbarItemGroup(placement: .bottomBar) {
            Button(action: toggleListening) {
                let title = !isSpeaking ? "Listen" : speaker.isPaused ? "Resume" : "Pause"
                let symbol = !isSpeaking || speaker.isPaused ? "play.fill" : "pause.fill"
                Label(title, systemImage: symbol)
                    .contentTransition(.symbolEffect(.replace))
            }
            .labelStyle(.titleAndIcon)
            .accessibilityHint("Reads the story aloud")

            if isSpeaking {
                Button("Stop", systemImage: "stop.fill") { speaker.stop() }
            }

            Spacer()

            Button {
                showTranslation = true
            } label: {
                Label("Translate", systemImage: "translate")
            }
            .labelStyle(.titleAndIcon)
        }
    }
}

/// The story's glossary, as chips.
private struct VocabularySection: View {
    let story: Story
    let onSelect: (VocabItem) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("New words")
                .font(.system(.title3, design: .serif, weight: .bold))
            FlowLayout(spacing: 8) {
                ForEach(story.vocab, id: \.word) { item in
                    Button {
                        onSelect(item)
                    } label: {
                        HStack(spacing: 6) {
                            Text(item.word)
                                .font(.system(.subheadline, design: .serif, weight: .semibold))
                                .foregroundStyle(Theme.ink)
                            Text(item.gloss)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(Theme.warm.opacity(0.12), in: .capsule)
                        .overlay(Capsule().strokeBorder(Theme.warm.opacity(0.3), lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("\(item.word), \(item.gloss)")
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
    }
}
