import SwiftUI
import Translation

/// The sheet shown when a word is tapped. Its own navigation stack, so a
/// lemma or another form can be opened and come back from.
struct LookupSheet: View {
    let lookup: WordLookup
    @Environment(\.dismiss) private var dismiss
    @State private var detent: PresentationDetent = {
        #if DEBUG
        if UserDefaults.standard.bool(forKey: "lookupExpanded") { return .large }
        #endif
        return .medium
    }()

    var body: some View {
        NavigationStack {
            LookupPage(lookup: lookup)
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        Button("Close", systemImage: "xmark") { dismiss() }
                    }
                }
                .navigationDestination(for: WordLookup.self) { LookupPage(lookup: $0) }
        }
        .presentationDetents([.medium, .large], selection: $detent)
        .presentationDragIndicator(.visible)
        .presentationBackgroundInteraction(.enabled(upThrough: .medium))
    }
}

private struct LookupPage: View {
    let lookup: WordLookup

    @Environment(ProgressStore.self) private var progress
    @Environment(Speaker.self) private var speaker
    @State private var result: LookupResult?
    @State private var error: String?
    @State private var attempt = 0
    @State private var showTranslation = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                header
                if let gloss = lookup.gloss {
                    StoryGloss(gloss: gloss)
                }
                if let result {
                    if result.isEmpty {
                        notFound
                    } else {
                        DictionaryContent(result: result, language: lookup.language)
                    }
                } else if let error {
                    ContentUnavailableView {
                        Label("Couldn't reach the dictionary", systemImage: "wifi.exclamationmark")
                    } description: {
                        Text(error)
                    } actions: {
                        Button("Try again") { attempt += 1 }.buttonStyle(.glass)
                    }
                } else {
                    LoadingRows()
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
        }
        .navigationTitle(lookup.word)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                let saved = progress.isSaved(lookup.word, in: lookup.language)
                Button(saved ? "Saved" : "Save", systemImage: saved ? "bookmark.fill" : "bookmark") {
                    progress.toggleSaved(lookup.word, in: lookup.language,
                                         meaning: lookup.gloss ?? result?.shortMeaning, storyTitle: lookup.storyTitle)
                }
                .contentTransition(.symbolEffect(.replace))
                .sensoryFeedback(.impact(weight: .light), trigger: saved)
            }
        }
        .translationPresentation(isPresented: $showTranslation, text: lookup.word)
        .task(id: attempt) {
            error = nil
            do {
                result = try await DictionaryClient.shared.lookUp(lookup.word, in: lookup.language)
            } catch {
                self.error = error.localizedDescription
            }
        }
    }

    private var header: some View {
        HStack(alignment: .firstTextBaseline) {
            VStack(alignment: .leading, spacing: 4) {
                Text(lookup.word)
                    .font(.system(size: 36, weight: .bold, design: .serif))
                    .foregroundStyle(Theme.ink)
                if let ipa = result?.ipa {
                    Text(ipa)
                        .font(.callout)
                        .foregroundStyle(.secondary)
                        .textSelection(.enabled)
                }
            }
            Spacer()
            ListenButton(text: lookup.word, language: lookup.language, recording: result?.audioURL)
        }
    }

    private var notFound: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("Not in the dictionary", systemImage: "character.book.closed")
                .font(.headline)
            Text("Wiktionary has no entry for “\(lookup.word)”. It may be a name, or a form it doesn't list.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Button("Translate on device", systemImage: "translate") { showTranslation = true }
                .buttonStyle(.glass)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(padding: 16, radius: 20)
    }
}

/// The word's meaning in the story it was tapped in.
private struct StoryGloss: View {
    let gloss: String

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "text.book.closed.fill")
                .foregroundStyle(Theme.warm)
            VStack(alignment: .leading, spacing: 1) {
                Text("In this story").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                Text(gloss).font(.body.weight(.medium))
            }
            Spacer()
        }
        .padding(14)
        .background(Theme.warm.opacity(0.12), in: .rect(cornerRadius: 16, style: .continuous))
    }
}

struct ListenButton: View {
    let text: String
    let language: Language
    var recording: URL?
    @Environment(Speaker.self) private var speaker

    var body: some View {
        let speaking = speaker.isSpeaking(text)
        Button {
            if speaking { speaker.stop() } else { speaker.play(recording, fallback: text, language: language) }
        } label: {
            Image(systemName: speaking ? "stop.fill" : "speaker.wave.2.fill")
                .font(.title3)
                .frame(width: 30, height: 30)
                .contentTransition(.symbolEffect(.replace))
        }
        .buttonStyle(.glass)
        .buttonBorderShape(.circle)
        .accessibilityLabel(speaking ? "Stop" : recording != nil ? "Listen to a recording" : "Listen")
    }
}

private struct LoadingRows: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            ForEach(0..<4, id: \.self) { i in
                RoundedRectangle(cornerRadius: 6).fill(.quaternary).frame(width: [220, 300, 260, 180][i], height: 14)
            }
        }
        .redacted(reason: .placeholder)
        .phaseAnimator([0.4, 1]) { view, phase in view.opacity(phase) }
        .accessibilityLabel("Looking up")
    }
}

// MARK: - Dictionary content

private struct DictionaryContent: View {
    let result: LookupResult
    let language: Language

    private var formOf: [FormOf] {
        var seen = Set<String>()
        return result.entries.flatMap(\.formOf).filter { seen.insert("\($0.lemma)|\($0.description)").inserted }
    }

    private var withMeanings: [Entry] { result.all.filter { !$0.senses.isEmpty } }

    private var withForms: [(Entry, Inflections)] {
        var seen = Set<String>()
        return result.all.compactMap { entry in
            guard seen.insert(entry.id).inserted else { return nil }
            let inflections = InflectionBuilder.build(entry, language: language)
            return inflections.isEmpty ? nil : (entry, inflections)
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 24) {
            if !formOf.isEmpty {
                VStack(alignment: .leading, spacing: 12) {
                    ForEach(formOf.prefix(4), id: \.self) { f in
                        NavigationLink(value: WordLookup(word: f.lemma, language: language)) {
                            FormOfCard(formOf: f, query: result.query, language: language,
                                       english: result.lemmas.first { $0.word == f.lemma }.flatMap(EnglishVerb.init(entry:)))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }

            if !withMeanings.isEmpty {
                Section("Meaning") {
                    ForEach(withMeanings) { MeaningCard(entry: $0) }
                }
            }

            if !withForms.isEmpty {
                Section("Forms") {
                    ForEach(withForms, id: \.0.id) { entry, inflections in
                        FormsView(entry: entry, inflections: inflections, query: result.query, language: language)
                    }
                }
            }

            Text("Definitions and forms from [Wiktionary](https://en.wiktionary.org/wiki/\(result.all.first?.word.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? "")) via [kaikki.org](https://kaikki.org), CC BY-SA 4.0.")
                .font(.caption2)
                .foregroundStyle(.tertiary)
        }
    }
}

/// What a form is, said three ways: who it is ("il/elle achète"), what that
/// means ("he/she buys"), and the grammar ("third-person singular present
/// indicative of acheter"), which opens the lemma.
private struct FormOfCard: View {
    let formOf: FormOf
    let query: String
    let language: Language
    let english: EnglishVerb?

    /// One line per subject the form can have: "je" and "il/elle" for "mange".
    private var lines: [(subject: String, english: String)] {
        guard let english, let tense = EnglishVerb.tense(for: formOf.tags) else {
            if let phrase = english?.phrase(forFormLabel: formOf.description) { return [("", phrase)] }
            return []
        }
        return EnglishVerb.subjects(for: formOf.tags).compactMap { person, number in
            guard let phrase = english.phrase(person: person, number: number, tense: tense) else { return nil }
            let pronoun = tense == .imperative ? nil
                : InflectionBuilder.pronounFor(language, row: person, column: number, form: query)
            let subject = pronoun.map { $0.hasSuffix("’") ? $0 + query.lowercased() : "\($0) \(query.lowercased())" } ?? query.lowercased()
            return (subject, phrase)
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if !lines.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    ForEach(lines, id: \.english) { line in
                        HStack(alignment: .firstTextBaseline, spacing: 8) {
                            if !line.subject.isEmpty {
                                Text(line.subject)
                                    .font(.system(.body, design: .serif))
                                    .foregroundStyle(Theme.ink)
                                Image(systemName: "arrow.right")
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(.tertiary)
                            }
                            Text(line.english)
                                .font(.system(.title3, weight: .semibold))
                                .foregroundStyle(Theme.accent)
                        }
                    }
                }
            }
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                let lemma = Text(formOf.lemma).font(.system(.subheadline, design: .serif, weight: .semibold)).foregroundStyle(Theme.ink)
                Text("\(formOf.description.prefix(1).uppercased() + formOf.description.dropFirst()) of \(lemma)")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.tertiary)
            }
            .multilineTextAlignment(.leading)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.accent.opacity(0.07), in: .rect(cornerRadius: 18, style: .continuous))
        .contentShape(.rect)
        .accessibilityElement(children: .combine)
        .accessibilityHint("Opens \(formOf.lemma)")
    }
}

private struct Section<Content: View>: View {
    let title: String
    @ViewBuilder let content: Content

    init(_ title: String, @ViewBuilder content: () -> Content) {
        self.title = title
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.footnote.weight(.semibold))
                .foregroundStyle(.secondary)
                .textCase(.uppercase)
            content
        }
    }
}

private struct MeaningCard: View {
    let entry: Entry
    @State private var showAll = false
    private let limit = 4

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(entry.word).font(.system(.headline, design: .serif))
                Text(entry.partOfSpeech)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Theme.accent)
                    .padding(.horizontal, 7).padding(.vertical, 2)
                    .background(Theme.accent.opacity(0.1), in: .capsule)
            }
            if let head = entry.head {
                Text(head).font(.caption).foregroundStyle(.secondary)
            }
            ForEach(Array(entry.senses.prefix(showAll ? .max : limit).enumerated()), id: \.offset) { i, sense in
                HStack(alignment: .firstTextBaseline, spacing: 10) {
                    Text("\(i + 1)").font(.caption.monospacedDigit().weight(.semibold)).foregroundStyle(.tertiary)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(sense.gloss)
                        if let example = sense.example {
                            Text(example.text).font(.system(.callout, design: .serif)).italic().foregroundStyle(.secondary)
                            if let translation = example.translation {
                                Text(translation).font(.caption).foregroundStyle(.tertiary)
                            }
                        }
                    }
                }
            }
            if entry.senses.count > limit {
                Button(showAll ? "Fewer meanings" : "All \(entry.senses.count) meanings") {
                    withAnimation(.snappy) { showAll.toggle() }
                }
                .font(.subheadline.weight(.medium))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(padding: 16, radius: 20)
    }
}
