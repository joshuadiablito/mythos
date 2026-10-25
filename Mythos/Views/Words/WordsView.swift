import SwiftUI

/// Words saved from stories, by language, with a flashcard review.
struct WordsView: View {
    @Environment(ProgressStore.self) private var progress
    @State private var lookup: WordLookup?
    @State private var reviewing: Language?

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
                        ReviewBanner(language: language, count: words.count) { reviewing = language }
                            .listRowInsets(EdgeInsets(top: 4, leading: 0, bottom: 10, trailing: 0))
                            .listRowBackground(Color.clear)
                            .listRowSeparator(.hidden)
                        ForEach(words) { word in
                            Button {
                                lookup = WordLookup(word: word.word, language: word.language, gloss: word.meaning, storyTitle: word.storyTitle)
                            } label: {
                                WordRow(word: word)
                            }
                            .buttonStyle(.plain)
                            .listRowBackground(Theme.card)
                        }
                        .onDelete { offsets in progress.remove(offsets.map { words[$0] }) }
                    } header: {
                        HStack(spacing: 8) {
                            LanguageBadge(language: language, size: 24)
                            Text(language.endonym)
                                .font(.system(.headline, design: .serif))
                                .foregroundStyle(Theme.ink)
                                .textCase(nil)
                        }
                    }
                }
            }
            .overlay {
                if progress.savedWords.isEmpty {
                    ContentUnavailableView {
                        Label("Your words will gather here", systemImage: "bookmark")
                    } description: {
                        Text("Tap a word in a story and save it, or save a story's new words when you finish it. Then review them as flashcards.")
                    }
                }
            }
            .appBackground()
            .navigationTitle("Words")
            .sheet(item: $lookup) { LookupSheet(lookup: $0) }
            .fullScreenCover(item: $reviewing) { language in
                FlashcardReview(words: progress.savedWords.filter { $0.language == language }.shuffled(), language: language)
            }
            #if DEBUG
            .onAppear {
                // `-review el` saves a story's words if there are none, then opens the review. For screenshots.
                guard let code = UserDefaults.standard.string(forKey: "review"), let language = Language(rawValue: code) else { return }
                if !progress.savedWords.contains(where: { $0.language == language }),
                   let story = Catalog.shared.stories(language, .a1).first {
                    progress.saveAll(story.vocab, in: language, storyTitle: story.title)
                }
                if UserDefaults.standard.bool(forKey: "reviewOpen") { reviewing = language }
            }
            #endif
        }
    }
}

/// "Review 12 words", on a strip of colour.
private struct ReviewBanner: View {
    let language: Language
    let count: Int
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: "rectangle.on.rectangle.angled")
                    .font(.title2)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Review as flashcards")
                        .font(.system(.headline, design: .serif))
                    Text(count == 1 ? "1 word" : "\(count) words")
                        .font(.caption)
                        .opacity(0.85)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.subheadline.weight(.semibold))
            }
            .foregroundStyle(.white)
            .padding(16)
            .background(
                MeshGradient(width: 2, height: 2, points: [[0, 0], [1, 0], [0, 1], [1, 1]],
                             colors: [CoverPalette.lavender.mid, CoverPalette.aegean.mid, CoverPalette.aegean.deep, CoverPalette.lavender.deep]),
                in: .rect(cornerRadius: 20, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityHint("Shows your \(language.name) words one at a time")
    }
}

private struct WordRow: View {
    let word: SavedWord

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 3) {
                Text(word.word)
                    .font(.system(.title3, design: .serif, weight: .semibold))
                    .foregroundStyle(Theme.ink)
                if let meaning = word.meaning {
                    Text(meaning).font(.subheadline).foregroundStyle(.secondary).lineLimit(2)
                }
                if let story = word.storyTitle {
                    Label(story, systemImage: "book.closed")
                        .labelStyle(.compact)
                        .font(.caption2)
                        .foregroundStyle(.tertiary)
                }
            }
            Spacer()
            ListenButton(text: word.word, language: word.language)
                .controlSize(.small)
        }
        .padding(.vertical, 4)
        .contentShape(.rect)
    }
}

// MARK: - Flashcards

/// Saved words one at a time: the word, then a tap turns the card over for
/// its meaning. "Again" sends it to the back of the pile; "Got it" puts it away.
private struct FlashcardReview: View {
    @State var words: [SavedWord]
    let language: Language
    @Environment(\.dismiss) private var dismiss
    @Environment(Speaker.self) private var speaker
    @State private var flipped = false
    @State private var known = 0
    @State private var total: Int
    @State private var drag: CGSize = .zero

    init(words: [SavedWord], language: Language) {
        _words = State(initialValue: words)
        _total = State(initialValue: words.count)
        self.language = language
    }

    var body: some View {
        VStack(spacing: 24) {
            HStack {
                Button("Close", systemImage: "xmark") { dismiss() }
                    .labelStyle(.iconOnly)
                    .buttonStyle(.glass)
                    .buttonBorderShape(.circle)
                    .controlSize(.large)
                Spacer()
                Text("\(known) of \(total)")
                    .font(.subheadline.monospacedDigit().weight(.semibold))
                    .foregroundStyle(.white.opacity(0.85))
                    .contentTransition(.numericText())
            }

            ProgressView(value: Double(known), total: Double(max(total, 1)))
                .tint(.white)

            Spacer()

            if let word = words.first {
                card(for: word)
                    .id(word.id)
                    .transition(.asymmetric(insertion: .scale(scale: 0.9).combined(with: .opacity),
                                            removal: .move(edge: drag.width < 0 ? .leading : .trailing).combined(with: .opacity)))
            } else {
                VStack(spacing: 14) {
                    Image(systemName: "checkmark.seal.fill")
                        .font(.system(size: 64))
                        .symbolEffect(.bounce, value: known)
                    Text(language.praise)
                        .font(.system(.largeTitle, design: .serif, weight: .bold))
                    Text("You went through all \(total) words.")
                        .opacity(0.85)
                    Button("Done") { dismiss() }
                        .buttonStyle(.glassProminent)
                        .tint(.white.opacity(0.3))
                        .controlSize(.large)
                        .padding(.top, 10)
                }
                .foregroundStyle(.white)
                .transition(.scale.combined(with: .opacity))
            }

            Spacer()

            if words.first != nil {
                GlassEffectContainer(spacing: 16) {
                    HStack(spacing: 16) {
                        Button { answer(knewIt: false) } label: {
                            Label("Again", systemImage: "arrow.uturn.backward")
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 8)
                        }
                        .buttonStyle(.glass)
                        Button { answer(knewIt: true) } label: {
                            Label("Got it", systemImage: "checkmark")
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 8)
                        }
                        .buttonStyle(.glassProminent)
                        .tint(.white.opacity(0.3))
                    }
                }
                .font(.headline)
                .foregroundStyle(.white)
                .controlSize(.large)
            }
        }
        .padding(20)
        .background {
            TimelineView(.animation(minimumInterval: 1 / 20)) { context in
                let t = Float(sin(context.date.timeIntervalSinceReferenceDate * 0.3) * 0.12)
                MeshGradient(width: 3, height: 3,
                             points: [[0, 0], [0.5, 0], [1, 0], [0, 0.5], [0.5 + t, 0.5 - t], [1, 0.5], [0, 1], [0.5, 1], [1, 1]],
                             colors: [CoverPalette.lavender.deep, CoverPalette.aegean.mid, CoverPalette.aegean.deep,
                                      CoverPalette.lavender.mid, CoverPalette.aegean.deep, CoverPalette.dusk.mid,
                                      CoverPalette.aegean.deep, CoverPalette.lavender.deep, CoverPalette.dusk.deep])
            }
            .ignoresSafeArea()
        }
        .sensoryFeedback(.impact(weight: .light), trigger: flipped)
        .animation(.spring(duration: 0.45, bounce: 0.25), value: words.first?.id)
    }

    private func card(for word: SavedWord) -> some View {
        ZStack {
            // Front: the word, as it appeared in the story.
            VStack(spacing: 12) {
                Text(word.word)
                    .font(.system(size: 44, weight: .bold, design: .serif))
                    .multilineTextAlignment(.center)
                    .minimumScaleFactor(0.5)
                if let story = word.storyTitle {
                    Text("from “\(story)”")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Text("Tap to turn over")
                    .font(.caption.weight(.medium))
                    .foregroundStyle(.tertiary)
                    .padding(.top, 18)
            }
            .opacity(flipped ? 0 : 1)

            // Back: its meaning, upside-down until the card turns.
            VStack(spacing: 14) {
                Text(word.word)
                    .font(.system(.title2, design: .serif, weight: .semibold))
                    .foregroundStyle(.secondary)
                Text(word.meaning ?? "No meaning saved")
                    .font(.system(size: 30, weight: .semibold))
                    .multilineTextAlignment(.center)
                    .minimumScaleFactor(0.5)
                ListenButton(text: word.word, language: word.language)
            }
            .opacity(flipped ? 1 : 0)
            .rotation3DEffect(.degrees(180), axis: (x: 0, y: 1, z: 0))
        }
        .foregroundStyle(Theme.ink)
        .padding(28)
        .frame(maxWidth: .infinity)
        .frame(height: 340)
        .background(Theme.card, in: .rect(cornerRadius: 32, style: .continuous))
        .shadow(color: .black.opacity(0.25), radius: 30, y: 16)
        .rotation3DEffect(.degrees(flipped ? 180 : 0), axis: (x: 0, y: 1, z: 0), perspective: 0.6)
        .rotationEffect(.degrees(Double(drag.width) / 22))
        .offset(drag)
        .onTapGesture {
            withAnimation(.spring(duration: 0.5, bounce: 0.2)) { flipped.toggle() }
            if flipped { speaker.play(nil, fallback: word.word, language: word.language) }
        }
        .gesture(
            DragGesture()
                .onChanged { drag = $0.translation }
                .onEnded { value in
                    if abs(value.translation.width) > 120 { answer(knewIt: value.translation.width > 0) }
                    else { withAnimation(.spring) { drag = .zero } }
                })
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
        .accessibilityHint(flipped ? "Shows the word" : "Shows the meaning")
    }

    private func answer(knewIt: Bool) {
        guard let word = words.first else { return }
        withAnimation(.spring(duration: 0.45, bounce: 0.2)) {
            words.removeFirst()
            if knewIt { known += 1 } else { words.append(word) }
            flipped = false
        }
        drag = .zero
    }
}
