import SwiftUI

/// The stories that have arrived so far, as a shelf of covers, newest first.
/// Stories still to come stay hidden: they arrive one a day, on Today.
struct LibraryView: View {
    @Environment(AppSettings.self) private var settings
    @Environment(ProgressStore.self) private var progress
    @State private var courseID: Course.ID?
    @State private var search = ""

    private var plan: DailyPlan { DailyPlan(catalog: .shared) }

    private var course: Course? {
        courseID.flatMap(settings.course) ?? settings.courses.first
    }

    private var arrived: [Story] {
        guard let course else { return [] }
        let all = plan.arrived(for: course, by: .now)
        guard !search.isEmpty else { return all }
        return all.filter {
            $0.title.localizedStandardContains(search) || $0.subtitle.localizedStandardContains(search)
                || $0.topic.localizedStandardContains(search) || $0.text.localizedStandardContains(search)
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    if let course {
                        if settings.courses.count > 1 {
                            CourseChips(selection: Binding(get: { course.id }, set: { courseID = $0 }))
                        }

                        ShelfHeader(course: course, plan: plan)
                            .padding(.horizontal, 20)

                        LazyVGrid(columns: [GridItem(.flexible(), spacing: 16), GridItem(.flexible(), spacing: 16)], spacing: 26) {
                            ForEach(Array(arrived.enumerated()), id: \.element.id) { index, story in
                                NavigationLink(value: story) {
                                    CoverTile(story: story, isRead: progress.isRead(story), isNew: index == 0 && search.isEmpty)
                                }
                                .buttonStyle(.plain)
                                .contextMenu {
                                    Button(progress.isRead(story) ? "Mark as unread" : "Mark as read",
                                           systemImage: progress.isRead(story) ? "circle" : "checkmark.circle") {
                                        withAnimation(.snappy) { progress.toggleRead(story) }
                                    }
                                }
                            }
                            if search.isEmpty {
                                LockedTile(remaining: plan.remaining(for: course, by: .now), time: settings.deliveryTime(for: course.id))
                            }
                        }
                        .padding(.horizontal, 20)
                        .overlay {
                            if arrived.isEmpty && !search.isEmpty { ContentUnavailableView.search(text: search).padding(.top, 60) }
                        }
                    } else {
                        ContentUnavailableView("No stories yet", systemImage: "books.vertical",
                                               description: Text("Choose a language in Settings, and its first story arrives today."))
                            .padding(.top, 80)
                    }
                }
                .padding(.bottom, 32)
            }
            .scrollIndicators(.hidden)
            .appBackground()
            .navigationTitle("Your stories")
            .searchable(text: $search, prompt: "Search your stories")
            .navigationDestination(for: Story.self) { StoryReaderView(story: $0) }
        }
    }
}

/// The courses being learned, as glass chips: "Español", or "Español A1" and
/// "Español A2" when a language is learned at two levels.
private struct CourseChips: View {
    @Binding var selection: Course.ID
    @Environment(AppSettings.self) private var settings

    var body: some View {
        ScrollView(.horizontal) {
            GlassEffectContainer(spacing: 8) {
                HStack(spacing: 8) {
                    ForEach(settings.courses) { course in
                        let selected = course.id == selection
                        Button {
                            withAnimation(.snappy) { selection = course.id }
                        } label: {
                            Text(settings.hasSeveralLevels(course.language) ? "\(course.language.endonym) \(course.level.rawValue)" : course.language.endonym)
                                .font(.subheadline.weight(selected ? .semibold : .regular))
                                .foregroundStyle(selected ? .white : Theme.ink)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 8)
                        }
                        .buttonStyle(.plain)
                        .glassEffect(selected ? .regular.tint(Theme.accent).interactive() : .regular.interactive(), in: .capsule)
                        .accessibilityAddTraits(selected ? .isSelected : [])
                    }
                }
            }
        }
        .contentMargins(.horizontal, 20, for: .scrollContent)
        .scrollIndicators(.hidden)
        .sensoryFeedback(.selection, trigger: selection)
    }
}

/// How many of the stories that have arrived have been read, as a ring.
private struct ShelfHeader: View {
    let course: Course
    let plan: DailyPlan
    @Environment(ProgressStore.self) private var progress

    var body: some View {
        let arrived = plan.arrived(for: course, by: .now)
        let read = progress.readCount(arrived)
        let total = Catalog.shared.stories(course.language, course.level).count
        let fraction = arrived.isEmpty ? 0 : Double(read) / Double(arrived.count)
        HStack(spacing: 18) {
            ZStack {
                Circle().stroke(.primary.opacity(0.08), lineWidth: 7)
                Circle()
                    .trim(from: 0, to: fraction)
                    .stroke(Theme.accent.gradient, style: StrokeStyle(lineWidth: 7, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                    .animation(.snappy, value: fraction)
                VStack(spacing: -2) {
                    Text("\(read)")
                        .font(.system(.title3, design: .rounded, weight: .bold))
                        .contentTransition(.numericText())
                    Text("of \(arrived.count)")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
            .frame(width: 66, height: 66)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("\(read) of \(arrived.count) stories read")

            VStack(alignment: .leading, spacing: 4) {
                Text("\(course.language.endonym) · \(course.level.rawValue)")
                    .font(.system(.title3, design: .serif, weight: .bold))
                    .foregroundStyle(Theme.ink)
                Text(arrived.count == 1 ? "Your first story has arrived" : "\(arrived.count) of \(total) stories have arrived")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

/// The stories still to come, as one locked tile. Never their titles.
private struct LockedTile: View {
    let remaining: Int
    let time: Date

    var body: some View {
        if remaining > 0 {
            VStack(alignment: .leading, spacing: 10) {
                VStack(spacing: 10) {
                    Image(systemName: "lock.fill")
                        .font(.title2)
                    Text("\(remaining)")
                        .font(.system(size: 40, weight: .bold, design: .serif))
                    Text(remaining == 1 ? "story to come" : "stories to come")
                        .font(.caption.weight(.semibold))
                }
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .aspectRatio(3 / 4, contentMode: .fit)
                .background {
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .strokeBorder(.secondary.opacity(0.35), style: StrokeStyle(lineWidth: 1.5, dash: [6, 5]))
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text("One a day")
                        .font(.system(.headline, design: .serif))
                        .foregroundStyle(Theme.ink)
                    Text("Next at \(time.formatted(date: .omitted, time: .shortened)) tomorrow")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(.horizontal, 2)
            }
            .accessibilityElement(children: .combine)
        }
    }
}

/// A story on the shelf: its cover, numbered, with its title beneath.
private struct CoverTile: View {
    let story: Story
    let isRead: Bool
    var isNew = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            StoryCover(story: story, symbolScale: 1.1)
                .aspectRatio(3 / 4, contentMode: .fit)
                .overlay(alignment: .topLeading) {
                    Text(String(format: "%02d", story.number))
                        .font(.system(.caption, design: .rounded, weight: .bold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 8).padding(.vertical, 4)
                        .glassEffect(.regular.tint(.black.opacity(0.1)), in: .capsule)
                        .padding(10)
                }
                .overlay(alignment: .topTrailing) {
                    if isNew && !isRead {
                        Text("NEW")
                            .font(.caption2.weight(.heavy))
                            .tracking(1)
                            .foregroundStyle(.white)
                            .padding(.horizontal, 8).padding(.vertical, 4)
                            .glassEffect(.regular.tint(Theme.warm.opacity(0.8)), in: .capsule)
                            .padding(10)
                    } else if isRead {
                        Image(systemName: "checkmark")
                            .font(.caption.weight(.heavy))
                            .foregroundStyle(.white)
                            .frame(width: 26, height: 26)
                            .glassEffect(.regular.tint(Color(hex: 0x2E9E4F).opacity(0.8)), in: .circle)
                            .padding(10)
                            .transition(.scale.combined(with: .opacity))
                    }
                }
                .overlay(alignment: .bottomLeading) {
                    Label(story.topic, systemImage: story.symbol)
                        .labelStyle(.compact)
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(.white.opacity(0.9))
                        .padding(12)
                }
                .clipShape(.rect(cornerRadius: 20, style: .continuous))
                .shadow(color: CoverPalette.forTopic(story.topic).deep.opacity(0.28), radius: 12, y: 8)
                .saturation(isRead ? 0.75 : 1)

            VStack(alignment: .leading, spacing: 2) {
                Text(story.title)
                    .font(.system(.headline, design: .serif))
                    .foregroundStyle(Theme.ink)
                    .lineLimit(2)
                Text("\(story.subtitle) · \(story.readingMinutes) min")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            .padding(.horizontal, 2)
        }
        .contentShape(.rect)
        .accessibilityElement(children: .combine)
        .accessibilityValue(isRead ? "Read" : "")
    }
}
