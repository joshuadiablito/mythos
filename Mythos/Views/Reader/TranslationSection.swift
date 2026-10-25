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
                                    SentencePair(original: pair.original, english: pair.english,
                                                 isSpeaking: speaker.isSpeaking("\(story.id)#\(p).\(s)")) {
                                        let id = "\(story.id)#\(p).\(s)"
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
}

private struct SentencePair: View {
    let original: String
    let english: String
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
                    Text(original)
                        .font(Theme.storyFont(size: 18))
                        .foregroundStyle(Theme.ink)
                    Text(english)
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
        .accessibilityLabel("\(original). \(english)")
        .accessibilityHint("Plays the sentence")
    }
}
