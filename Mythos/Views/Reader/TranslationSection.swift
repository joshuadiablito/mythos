import SwiftUI

/// The story again, sentence by sentence, each with its English translation
/// underneath. Tap a sentence to hear it. It can be folded away, for readers
/// who want to try the story on their own first.
struct TranslationSection: View {
    let story: Story
    @AppStorage("showTranslation") private var isExpanded = true
    @Environment(Speaker.self) private var speaker
    @Environment(AppSettings.self) private var settings

    var body: some View {
        let pairs = story.sentencePairs
        if !pairs.isEmpty {
            VStack(alignment: .leading, spacing: 0) {
                Button {
                    withAnimation(.snappy) { isExpanded.toggle() }
                } label: {
                    HStack {
                        Label("English translation", systemImage: "text.bubble")
                            .font(.system(.title3, design: .serif, weight: .bold))
                            .foregroundStyle(Theme.ink)
                        Spacer()
                        Text(isExpanded ? "Hide" : "Show")
                            .font(.subheadline.weight(.medium))
                            .foregroundStyle(Theme.accent)
                    }
                    .contentShape(.rect)
                }
                .buttonStyle(.plain)
                .accessibilityHint(isExpanded ? "Hides the translation" : "Shows the story sentence by sentence in English")

                if isExpanded {
                    VStack(alignment: .leading, spacing: 22) {
                        ForEach(Array(pairs.enumerated()), id: \.offset) { p, paragraph in
                            VStack(alignment: .leading, spacing: 14) {
                                ForEach(Array(paragraph.enumerated()), id: \.offset) { s, pair in
                                    let id = speechID(paragraph: p, sentence: s)
                                    SentencePairRow(pair: pair, isSpeaking: speaker.isSpeaking(id)) {
                                        if speaker.isSpeaking(id) { speaker.stop() } else {
                                            speaker.speak(pair.original, language: story.language, rate: settings.speechRate, id: id)
                                        }
                                    }
                                }
                            }
                            if p < pairs.count - 1 { Divider() }
                        }
                    }
                    .padding(.top, 18)
                    .transition(.opacity.combined(with: .move(edge: .top)))
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .card()
        }
    }

    /// Identifies one sentence to `Speaker`, apart from the whole story.
    private func speechID(paragraph: Int, sentence: Int) -> String {
        "\(story.id)#\(paragraph).\(sentence)"
    }
}

private struct SentencePairRow: View {
    let pair: SentencePair
    let isSpeaking: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(alignment: .firstTextBaseline, spacing: 10) {
                Image(systemName: isSpeaking ? "speaker.wave.2.fill" : "speaker.wave.1")
                    .font(.caption)
                    .foregroundStyle(isSpeaking ? Theme.warm : Color.secondary.opacity(0.6))
                    .frame(width: 16)
                    .contentTransition(.symbolEffect(.replace))
                VStack(alignment: .leading, spacing: 4) {
                    Text(pair.original)
                        .font(Theme.storyFont(size: 18))
                        .foregroundStyle(Theme.ink)
                    Text(pair.translation)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.vertical, 2)
            .background(isSpeaking ? Theme.warm.opacity(0.1) : .clear, in: .rect(cornerRadius: 8))
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(pair.original). \(pair.translation)")
        .accessibilityHint("Plays the sentence")
    }
}
