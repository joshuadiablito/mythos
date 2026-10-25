import Combine
import SwiftUI

/// Welcome → languages → levels → delivery time.
struct OnboardingView: View {
    enum Step: Int, CaseIterable { case welcome, languages, levels, time }

    @Environment(AppSettings.self) private var settings
    @State private var step: Step = .welcome

    var body: some View {
        VStack(spacing: 0) {
            if step != .welcome {
                ProgressView(value: Double(step.rawValue), total: Double(Step.allCases.count - 1))
                    .padding(.horizontal, 24)
                    .padding(.top, 12)
            }
            Group {
                switch step {
                case .welcome: WelcomeStep()
                case .languages: LanguagesStep()
                case .levels: LevelsStep()
                case .time: TimeStep()
                }
            }
            .transition(.asymmetric(insertion: .move(edge: .trailing).combined(with: .opacity),
                                    removal: .move(edge: .leading).combined(with: .opacity)))
            .frame(maxHeight: .infinity, alignment: .top)

            footer
        }
        .appBackground()
        .animation(.smooth(duration: 0.4), value: step)
    }

    private var canContinue: Bool {
        step != .languages || !settings.courses.isEmpty
    }

    private var footer: some View {
        HStack(spacing: 12) {
            if step != .welcome {
                Button {
                    step = Step(rawValue: step.rawValue - 1) ?? .welcome
                } label: {
                    Image(systemName: "chevron.left")
                        .font(.body.weight(.semibold))
                        .frame(width: 30, height: 30)
                }
                .buttonStyle(.glass)
                .buttonBorderShape(.circle)
                .controlSize(.large)
                .accessibilityLabel("Back")
            }
            Button {
                advance()
            } label: {
                Text(step == .welcome ? "Get started" : step == .time ? "Start reading" : "Continue")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
            }
            .buttonStyle(.glassProminent)
            .controlSize(.large)
            .disabled(!canContinue)
        }
        .padding(.horizontal, 24)
        .padding(.bottom, 12)
    }

    private func advance() {
        guard let next = Step(rawValue: step.rawValue + 1) else {
            finish()
            return
        }
        step = next
    }

    private func finish() {
        Task {
            if settings.notificationsEnabled {
                settings.notificationsEnabled = await NotificationScheduler.requestAuthorization()
            }
            settings.hasOnboarded = true
            await NotificationScheduler.reschedule(settings: settings)
        }
    }
}

// MARK: - Steps

private struct StepHeader: View {
    let title: String
    let subtitle: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.system(.largeTitle, design: .serif, weight: .bold))
            Text(subtitle)
                .font(.body)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 24)
        .padding(.top, 28)
        .padding(.bottom, 20)
    }
}

private struct WelcomeStep: View {
    @State private var greeting = 0
    @State private var float = false
    private let timer = Timer.publish(every: 1.8, on: .main, in: .common).autoconnect()

    /// Three first stories, in three languages, fanned out like books on a table.
    private var covers: [Story] {
        ["fr-a1-04", "el-a1-03", "es-a2-01"].compactMap { Catalog.shared.story(id: $0) }
    }

    var body: some View {
        VStack(spacing: 30) {
            Spacer(minLength: 20)
            ZStack {
                ForEach(Array(covers.enumerated()), id: \.element.id) { index, story in
                    let angle = Double(index - 1) * 11
                    StoryCover(story: story, symbolScale: 1.1)
                        .frame(width: 150, height: 200)
                        .overlay(alignment: .bottomLeading) {
                            // Only the front cover carries a title; the others are half hidden.
                            if index == 1 {
                                Text(story.title)
                                    .font(.system(.subheadline, design: .serif, weight: .bold))
                                    .foregroundStyle(.white)
                                    .padding(12)
                            }
                        }
                        .clipShape(.rect(cornerRadius: 20, style: .continuous))
                        .shadow(color: CoverPalette.forTopic(story.topic).deep.opacity(0.35), radius: 18, y: 12)
                        .rotationEffect(.degrees(angle), anchor: .bottom)
                        .scaleEffect(index == 1 ? 1 : 0.9)
                        .offset(x: CGFloat(index - 1) * 84, y: index == 1 ? -14 : 10)
                        .offset(y: float ? (index == 1 ? -6 : 4) : 0)
                        .zIndex(index == 1 ? 1 : 0)
                }
            }
            .frame(height: 250)
            .animation(.easeInOut(duration: 3).repeatForever(autoreverses: true), value: float)
            .accessibilityHidden(true)

            VStack(spacing: 12) {
                Text("Mythos")
                    .font(.system(size: 52, weight: .bold, design: .serif))
                Text(Language.allCases[greeting].greeting)
                    .font(.system(.title2, design: .serif))
                    .italic()
                    .foregroundStyle(Theme.accent)
                    .contentTransition(.opacity)
                    .id(greeting)
                    .transition(.opacity)
                Text("A short story every day, in the language you're learning, at your level. Tap any word for its meaning and every form it takes.")
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 32)
                    .padding(.top, 4)
            }
            Spacer()
        }
        .onAppear { float = true }
        .onReceive(timer) { _ in
            withAnimation(.easeInOut(duration: 0.6)) { greeting = (greeting + 1) % Language.allCases.count }
        }
    }
}

private struct LanguagesStep: View {
    @Environment(AppSettings.self) private var settings

    var body: some View {
        ScrollView {
            StepHeader(title: "What are you learning?", subtitle: "Choose one or more. Each gets its own story every day.")
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 14), GridItem(.flexible(), spacing: 14)], spacing: 14) {
                ForEach(Language.allCases) { language in
                    LanguageTile(language: language, selected: settings.course(for: language) != nil) {
                        withAnimation(.snappy) { settings.toggle(language) }
                    }
                }
            }
            .padding(.horizontal, 24)
        }
        .scrollBounceBehavior(.basedOnSize)
    }
}

private struct LanguageTile: View {
    let language: Language
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    LanguageBadge(language: language, size: 40)
                    Spacer()
                    Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                        .font(.title3)
                        .foregroundStyle(selected ? Theme.accent : Color.secondary.opacity(0.5))
                        .contentTransition(.symbolEffect(.replace))
                }
                Spacer(minLength: 6)
                Text(language.endonym)
                    .font(.system(.title3, design: .serif, weight: .semibold))
                    .foregroundStyle(.primary)
                Text(language.name)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, minHeight: 120, alignment: .leading)
            .card(padding: 16, radius: 22)
            .overlay {
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .strokeBorder(selected ? Theme.accent : .clear, lineWidth: 2)
            }
        }
        .buttonStyle(.plain)
        .sensoryFeedback(.selection, trigger: selected)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

private struct LevelsStep: View {
    @Environment(AppSettings.self) private var settings

    var body: some View {
        ScrollView {
            StepHeader(title: "Your level", subtitle: "Stories are written for each level of the Common European Framework. A1 and A2 are ready now; more are on the way.")
            VStack(spacing: 18) {
                ForEach(settings.courses) { course in
                    VStack(alignment: .leading, spacing: 12) {
                        HStack(spacing: 10) {
                            LanguageBadge(language: course.language)
                            Text(course.language.endonym)
                                .font(.system(.title3, design: .serif, weight: .semibold))
                        }
                        LevelPicker(selection: Binding(
                            get: { course.level },
                            set: { settings.setLevel($0, for: course.language) }))
                    }
                    .card(padding: 16, radius: 24)
                }
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 24)
        }
    }
}

/// The six CEFR levels, unavailable ones dimmed with "Soon".
struct LevelPicker: View {
    @Binding var selection: Level

    var body: some View {
        VStack(spacing: 6) {
            ForEach(Level.allCases) { level in
                Button {
                    withAnimation(.snappy) { selection = level }
                } label: {
                    HStack(spacing: 12) {
                        LevelPill(level: level, prominent: selection == level)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(level.title).font(.subheadline.weight(.semibold))
                            Text(level.summary).font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        if !level.isAvailable {
                            Text("Soon")
                                .font(.caption2.weight(.semibold))
                                .foregroundStyle(.secondary)
                                .padding(.horizontal, 8).padding(.vertical, 3)
                                .background(.quaternary, in: .capsule)
                        } else if selection == level {
                            Image(systemName: "checkmark").foregroundStyle(Theme.accent).fontWeight(.semibold)
                        }
                    }
                    .padding(.vertical, 8)
                    .padding(.horizontal, 10)
                    .background(selection == level ? Theme.accent.opacity(0.08) : .clear, in: .rect(cornerRadius: 14))
                    .contentShape(.rect)
                }
                .buttonStyle(.plain)
                .disabled(!level.isAvailable)
                .opacity(level.isAvailable ? 1 : 0.5)
            }
        }
        .sensoryFeedback(.selection, trigger: selection)
    }
}

private struct TimeStep: View {
    @Environment(AppSettings.self) private var settings

    var body: some View {
        @Bindable var settings = settings
        ScrollView {
            StepHeader(title: "When should it arrive?", subtitle: "A notification brings each day's story. It's scheduled on your phone; nothing is sent to a server.")
            VStack(spacing: 14) {
                SlotPicker()
                HStack {
                    Label("Exact time", systemImage: "clock")
                    Spacer()
                    DatePicker("Exact time", selection: $settings.deliveryTime, displayedComponents: .hourAndMinute)
                        .labelsHidden()
                }
                .card(padding: 16, radius: 20)
                Toggle(isOn: $settings.notificationsEnabled) {
                    Label("Daily notification", systemImage: "bell.badge")
                }
                .card(padding: 16, radius: 20)
            }
            .padding(.horizontal, 24)
        }
    }
}

/// Morning / afternoon / evening, as three tiles.
struct SlotPicker: View {
    @Environment(AppSettings.self) private var settings

    var body: some View {
        HStack(spacing: 12) {
            ForEach(DeliverySlot.allCases) { slot in
                let selected = settings.slot == slot
                Button {
                    withAnimation(.snappy) { settings.setSlot(slot) }
                } label: {
                    VStack(spacing: 10) {
                        Image(systemName: slot.symbol)
                            .font(.title2)
                            .symbolRenderingMode(.multicolor)
                        Text(slot.title).font(.subheadline.weight(.semibold))
                        Text(selected ? settings.deliveryTime.formatted(date: .omitted, time: .shortened)
                                      : Calendar.current.date(from: slot.defaultTime)!.formatted(date: .omitted, time: .shortened))
                            .font(.caption.monospacedDigit())
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 18)
                    .background(Theme.card, in: .rect(cornerRadius: 20, style: .continuous))
                    .overlay {
                        RoundedRectangle(cornerRadius: 20, style: .continuous)
                            .strokeBorder(selected ? Theme.accent : .clear, lineWidth: 2)
                    }
                    .shadow(color: .black.opacity(0.05), radius: 12, y: 6)
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
        }
        .sensoryFeedback(.selection, trigger: settings.slot)
    }
}
