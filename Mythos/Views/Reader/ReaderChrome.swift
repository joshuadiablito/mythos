import SwiftUI

/// The story's cover across the top of the reader, under the navigation bar,
/// with its title set on it. Pulling down stretches the cover, not the title.
struct CoverHeader: View {
    let story: Story
    private let height: CGFloat = 430

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            StoryCover(story: story)
                .frame(height: height)
                .visualEffect { content, proxy in
                    // Overscroll: grow from the bottom edge to fill the gap above.
                    let pull = max(0, proxy.frame(in: .scrollView(axis: .vertical)).minY)
                    return content.scaleEffect((height + pull) / height, anchor: .bottom)
                }

            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 6) {
                    Text("\(story.language.endonym) · \(story.level.rawValue) · Story \(story.number)")
                    Text("·")
                    Label("\(story.readingMinutes) min", systemImage: "clock")
                        .labelStyle(.compact)
                }
                .font(.caption.weight(.semibold))
                .foregroundStyle(.white.opacity(0.85))

                Text(story.title)
                    .font(.system(size: 40, weight: .bold, design: .serif))
                    .foregroundStyle(.white)
                    .shadow(color: .black.opacity(0.25), radius: 12, y: 4)
                    .fixedSize(horizontal: false, vertical: true)
                Text(story.subtitle)
                    .font(.title3)
                    .foregroundStyle(.white.opacity(0.85))
            }
            .padding(.horizontal, 22)
            .padding(.bottom, 26)
        }
        .frame(height: height)
        // The page's paper curls up over the bottom of the cover.
        .overlay(alignment: .bottom) {
            UnevenRoundedRectangle(topLeadingRadius: 28, topTrailingRadius: 28, style: .continuous)
                .fill(Theme.paper)
                .frame(height: 28)
                .offset(y: 27)
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }
}

/// How far through the story the reader has scrolled, as a slim bar.
struct ReadingProgress: View {
    let fraction: Double

    var body: some View {
        Capsule()
            .fill(.primary.opacity(0.12))
            .frame(width: 96, height: 5)
            .overlay(alignment: .leading) {
                Capsule()
                    .fill(Theme.accent.gradient)
                    .frame(width: 96 * fraction, height: 5)
            }
            .opacity(fraction > 0.04 ? 1 : 0)
            .animation(.easeOut(duration: 0.2), value: fraction > 0.04)
            .accessibilityElement()
            .accessibilityLabel("Reading progress")
            .accessibilityValue("\(Int(fraction * 100)) percent")
    }
}

/// Shown on finishing a story: the streak, and the story's new words to keep.
struct CompletionSheet: View {
    let story: Story
    @Environment(ProgressStore.self) private var progress
    @Environment(\.dismiss) private var dismiss
    @State private var appeared = false

    private var unsaved: [VocabItem] { story.vocab.filter { !progress.isSaved($0.word, in: story.language) } }

    var body: some View {
        let streak = progress.streak()
        VStack(spacing: 22) {
            ZStack {
                ForEach(0..<12, id: \.self) { i in
                    Image(systemName: ["sparkle", "star.fill", "circle.fill"][i % 3])
                        .font(.system(size: CGFloat(8 + (i * 5) % 10)))
                        .foregroundStyle([CoverPalette.forTopic(story.topic).accent, Theme.warm, Theme.accent][i % 3])
                        .offset(x: appeared ? cos(Double(i) / 12 * 2 * .pi) * 110 : 0,
                                y: appeared ? sin(Double(i) / 12 * 2 * .pi) * 80 : 0)
                        .opacity(appeared ? 0 : 1)
                        .animation(.easeOut(duration: 1.1).delay(0.15), value: appeared)
                }
                StoryCover(story: story, symbolScale: 0.9)
                    .frame(width: 112, height: 148)
                    .clipShape(.rect(cornerRadius: 18, style: .continuous))
                    .overlay(alignment: .bottomTrailing) {
                        Image(systemName: "checkmark.seal.fill")
                            .font(.system(size: 34))
                            .symbolRenderingMode(.palette)
                            .foregroundStyle(.white, Color(hex: 0x2E9E4F))
                            .offset(x: 12, y: 12)
                            .symbolEffect(.bounce, value: appeared)
                    }
                    .shadow(color: CoverPalette.forTopic(story.topic).deep.opacity(0.35), radius: 18, y: 10)
                    .scaleEffect(appeared ? 1 : 0.8)
                    .animation(.spring(duration: 0.5, bounce: 0.4), value: appeared)
            }
            .padding(.top, 28)

            VStack(spacing: 6) {
                Text(story.language.praise)
                    .font(.system(.largeTitle, design: .serif, weight: .bold))
                Text("You finished “\(story.title)”.")
                    .foregroundStyle(.secondary)
            }
            .multilineTextAlignment(.center)

            HStack(spacing: 12) {
                Stat(value: "\(streak)", label: streak == 1 ? "day in a row" : "days in a row", symbol: "flame.fill", tint: Color(hex: 0xF2545B))
                Stat(value: "\(story.vocab.count)", label: "new words", symbol: "sparkles", tint: Theme.warm)
                Stat(value: "\(story.wordCount)", label: "words read", symbol: "text.alignleft", tint: Theme.accent)
            }

            VStack(spacing: 10) {
                if !unsaved.isEmpty {
                    Button {
                        withAnimation(.snappy) { progress.saveAll(story.vocab, in: story.language, storyTitle: story.title) }
                    } label: {
                        Label("Save \(unsaved.count) new words to review", systemImage: "bookmark.fill")
                            .font(.headline)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 6)
                    }
                    .buttonStyle(.glassProminent)
                } else {
                    Label("Words saved to your list", systemImage: "checkmark")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color(hex: 0x2E9E4F))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                }
                Button("Done") { dismiss() }
                    .buttonStyle(.glass)
                    .controlSize(.large)
            }
            .controlSize(.large)
            .sensoryFeedback(.success, trigger: unsaved.isEmpty)

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 24)
        .presentationDetents([.height(600), .large])
        .presentationDragIndicator(.visible)
        .onAppear { appeared = true }
    }
}

private struct Stat: View {
    let value: String
    let label: String
    let symbol: String
    let tint: Color

    var body: some View {
        VStack(spacing: 4) {
            Image(systemName: symbol)
                .foregroundStyle(tint)
            Text(value)
                .font(.system(.title2, design: .rounded, weight: .bold))
            Text(label)
                .font(.caption2)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .background(.primary.opacity(0.05), in: .rect(cornerRadius: 18, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}

extension Language {
    /// "Well done!" in the language.
    var praise: String {
        switch self {
        case .french: "Bravo !"
        case .spanish: "¡Muy bien!"
        case .german: "Super gemacht!"
        case .italian: "Bravissimo!"
        case .portuguese: "Muito bem!"
        case .greek: "Μπράβο!"
        }
    }
}
