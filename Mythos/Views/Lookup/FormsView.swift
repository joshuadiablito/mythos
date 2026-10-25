import SwiftUI

/// Every form of an entry: its tables, each collapsible, with the table holding
/// the tapped word open and that word highlighted.
struct FormsView: View {
    let entry: Entry
    let inflections: Inflections
    let query: String
    let language: Language

    @State private var expanded: Set<String> = []

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Text(entry.word).font(.system(.headline, design: .serif))
                Text(entry.partOfSpeech).font(.caption).foregroundStyle(.secondary)
            }
            ForEach(inflections.tables) { table in
                TableCard(table: table, query: query, language: language, isExpanded: Binding(
                    get: { expanded.contains(table.id) },
                    set: { open in
                        withAnimation(.snappy) {
                            if open { expanded.insert(table.id) } else { expanded.remove(table.id) }
                        }
                    }))
            }
            if !inflections.other.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(inflections.other, id: \.label) { other in
                        HStack(alignment: .firstTextBaseline) {
                            Text(other.label).font(.caption).foregroundStyle(.secondary)
                                .frame(width: 110, alignment: .leading)
                            FormList(forms: other.forms, query: query, language: language)
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .card(padding: 14, radius: 18)
            }
        }
        .onAppear {
            // Open the table with the tapped word, or the first one.
            let match = inflections.tables.first { $0.contains(query) } ?? inflections.tables.first
            if let match, expanded.isEmpty { expanded.insert(match.id) }
        }
    }
}

private struct TableCard: View {
    let table: InflectionTable
    let query: String
    let language: Language
    @Binding var isExpanded: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button {
                isExpanded.toggle()
            } label: {
                HStack {
                    Text(table.title).font(.subheadline.weight(.semibold))
                    if table.contains(query) {
                        Circle().fill(Theme.accent).frame(width: 6, height: 6)
                            .accessibilityLabel("Contains \(query)")
                    }
                    Spacer()
                    Image(systemName: "chevron.down")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .rotationEffect(.degrees(isExpanded ? 0 : -90))
                }
                .padding(14)
                .contentShape(.rect)
            }
            .buttonStyle(.plain)

            if isExpanded {
                Divider().padding(.horizontal, 14)
                ScrollView(.horizontal, showsIndicators: false) {
                    TableGrid(table: table, query: query, language: language)
                        .padding(14)
                }
            }
        }
        .background(Theme.card, in: .rect(cornerRadius: 18, style: .continuous))
        .shadow(color: .black.opacity(0.05), radius: 10, y: 4)
    }
}

private struct TableGrid: View {
    let table: InflectionTable
    let query: String
    let language: Language

    private var showsColumnHeaders: Bool { !(table.columns.count == 1 && table.columns[0].key.isEmpty) }
    private var showsRowHeaders: Bool { !(table.rows.count == 1 && table.rows[0].key.isEmpty) }

    var body: some View {
        Grid(alignment: .leading, horizontalSpacing: 18, verticalSpacing: 10) {
            if showsColumnHeaders {
                GridRow {
                    if showsRowHeaders { Text(table.rowHeading).gridCellAnchor(.leading) }
                    ForEach(table.columns, id: \.key) { Text($0.label) }
                }
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
            }
            ForEach(table.rows, id: \.key) { row in
                GridRow(alignment: .firstTextBaseline) {
                    if showsRowHeaders {
                        Text(row.label).font(.caption).foregroundStyle(.secondary)
                    }
                    ForEach(table.columns, id: \.key) { column in
                        if let cell = table.cells[row.key]?[column.key] {
                            HStack(alignment: .firstTextBaseline, spacing: cell.pronoun?.hasSuffix("’") == true ? 0 : 5) {
                                if let pronoun = cell.pronoun {
                                    Text(pronoun).font(.caption).foregroundStyle(.tertiary)
                                }
                                FormList(forms: cell.forms, query: query, language: language, speaksWith: cell.pronoun)
                            }
                        } else {
                            Text("—").foregroundStyle(.quaternary)
                        }
                    }
                }
            }
        }
    }
}

/// Forms separated by slashes; tap one to hear it. The looked-up word is highlighted.
private struct FormList: View {
    let forms: [String]
    let query: String
    let language: Language
    var speaksWith: String?
    @Environment(Speaker.self) private var speaker

    var body: some View {
        HStack(spacing: 4) {
            ForEach(Array(forms.enumerated()), id: \.offset) { i, form in
                if i > 0 { Text("/").foregroundStyle(.tertiary) }
                let match = form.lowercased() == query.lowercased()
                Button {
                    // "nous mangeons" sounds more natural than "mangeons" alone.
                    let pronoun = speaksWith?.components(separatedBy: "/").first
                    let spoken = pronoun.map { $0.hasSuffix("’") ? $0 + form : "\($0) \(form)" } ?? form
                    speaker.speak(spoken, language: language, rate: 0.4)
                } label: {
                    Text(form)
                        .font(.system(.body, design: .serif, weight: match ? .bold : .regular))
                        .foregroundStyle(match ? Theme.accent : Theme.ink)
                        .padding(.horizontal, match ? 6 : 0)
                        .padding(.vertical, match ? 2 : 0)
                        .background(match ? Theme.accent.opacity(0.14) : .clear, in: .rect(cornerRadius: 6))
                        .fixedSize()
                }
                .buttonStyle(.plain)
                .accessibilityHint("Plays the word")
            }
        }
    }
}
