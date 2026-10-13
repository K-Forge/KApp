import SwiftUI

/// Semáforo: the pensum as columns of semesters, each course coloured by where the student is with
/// it. Tapping a course opens what can be done with it.
struct SemaphoreView: View {
    let model: SemaphoreViewModel

    @State private var selectedCardId: String?

    var body: some View {
        BandScreen(title: "Semáforo", onRefresh: model.load) {
            summaryCard

            if let board = model.board, board.addedCourseCount > 0 {
                InlineMessage(
                    kind: .info,
                    text: board.addedCourseCount == 1
                        ? "Tu plan de estudios cambió: se agregó 1 materia."
                        : "Tu plan de estudios cambió: se agregaron \(board.addedCourseCount) materias."
                )
                .padding(.top, 12)
            }

            if let notice = model.notice {
                InlineMessage(
                    kind: notice.kind == .success ? .success : .error,
                    text: notice.text,
                    onDismiss: { model.notice = nil }
                )
                .padding(.top, 12)
            }

            if let board = model.board {
                HStack {
                    Text("Mi plan de estudios")
                        .font(KType.section)
                        .foregroundStyle(KColor.text)
                    Spacer()
                    Text("desliza")
                        .font(KType.label)
                        .foregroundStyle(KColor.placeholder)
                    Image(systemName: "chevron.right")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(KColor.placeholder)
                }
                .padding(.top, 18)

                columns(board)
                    .padding(.top, 10)

                Text("Toca una materia para cambiar su estado o planearla en otro semestre.")
                    .font(KType.label)
                    .foregroundStyle(KColor.textSoft)
                    .padding(.top, 12)
            }
        }
        .sheet(item: Binding(
            get: { selectedCardId.flatMap { id in model.board?.card(id: id) } },
            set: { selectedCardId = $0?.id }
        )) { card in
            if let board = model.board {
                CourseDetailSheet(
                    card: card,
                    board: board,
                    currentPeriod: model.currentPeriod,
                    onSave: { status, grade, period in
                        await model.save(cardId: card.id, status: status, gradeText: grade, period: period)
                    },
                    onMove: { level in
                        await model.move(cardId: card.id, toLevel: level)
                    }
                )
                .presentationDetents([.large])
            }
        }
    }

    // MARK: - Summary

    @ViewBuilder
    private var summaryCard: some View {
        switch model.state {
        case .loading:
            VStack(alignment: .leading, spacing: 14) {
                SkeletonBar(width: 170, height: 16, radius: 7)
                SkeletonBar(width: nil, height: 10, color: KColor.borderSoft, radius: 5)
                SkeletonBar(width: 240, height: 11, color: KColor.borderSoft)
            }
            .padding(KMetrics.cardPadding)
            .kCard(elevated: true)

        case .failed(let error):
            FailureCard(error: error) { Task { await model.load() } }

        case .loaded:
            if let board = model.board {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(alignment: .bottom) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(board.pensum.programName)
                                .font(KType.rowTitle)
                                .foregroundStyle(KColor.text)
                            Text("Semestre \(model.summary?.currentLevel ?? board.progress.currentLevel) · pensum \(board.pensum.pensumCode)")
                                .font(KType.meta)
                                .foregroundStyle(KColor.textSoft)
                        }
                        Spacer()
                        if let summary = model.summary {
                            Text("\(Int(summary.percentComplete.rounded()))%")
                                .font(.system(size: 26, weight: .bold))
                                .foregroundStyle(KColor.brand)
                        }
                    }

                    if let summary = model.summary, summary.totalCredits > 0 {
                        ProgressBar(
                            passed: Double(summary.creditsPassed) / Double(summary.totalCredits),
                            inProgress: Double(summary.creditsInProgress) / Double(summary.totalCredits)
                        )
                        .padding(.top, 13)
                        Text("\(summary.creditsPassed) de \(summary.totalCredits) créditos aprobados")
                            .font(KType.label)
                            .foregroundStyle(KColor.textSoft)
                            .padding(.top, 8)
                    }

                    legend
                        .padding(.top, 12)
                }
                .padding(KMetrics.cardPadding)
                .kCard(elevated: true)
            }
        }
    }

    private var legend: some View {
        let states: [CardState] = [.passed, .inProgress, .available, .blocked, .failed]
        return LazyVGrid(columns: [GridItem(.adaptive(minimum: 96), alignment: .leading)], alignment: .leading, spacing: 6) {
            ForEach(states, id: \.label) { state in
                HStack(spacing: 6) {
                    RoundedRectangle(cornerRadius: 3)
                        .fill(CardStyle(state: state).background)
                        .overlay(RoundedRectangle(cornerRadius: 3).stroke(CardStyle(state: state).border ?? .clear, lineWidth: 1))
                        .frame(width: 12, height: 12)
                    Text(state.label)
                        .font(KType.label)
                        .foregroundStyle(KColor.textSoft)
                }
            }
        }
    }

    // MARK: - Grid

    private func columns(_ board: SemaphoreBoard) -> some View {
        let currentLevel = model.summary?.currentLevel ?? board.progress.currentLevel
        return ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(alignment: .top, spacing: 12) {
                    ForEach(board.levels, id: \.self) { level in
                        column(board, level: level, isCurrent: level == currentLevel)
                            .id(level)
                    }
                }
                .padding(.horizontal, KMetrics.screenPadding)
            }
            .padding(.horizontal, -KMetrics.screenPadding)
            .onAppear {
                // Centred rather than leading: the columns either side show partly, which says
                // "desliza" better than a column flush against the edge of the screen.
                proxy.scrollTo(currentLevel, anchor: .center)
            }
        }
    }

    private func column(_ board: SemaphoreBoard, level: Int, isCurrent: Bool) -> some View {
        let cards = board.cards(atLevel: level)
        return VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Text("SEMESTRE \(Self.roman(level))")
                    .font(KType.eyebrow)
                    .tracking(0.9)
                    .foregroundStyle(isCurrent ? KColor.brand : KColor.textSoft)
                if isCurrent {
                    Text("Actual")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(KColor.brand)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(KColor.inProgress.opacity(0.32))
                        .clipShape(Capsule())
                }
            }
            Text("\(board.credits(atLevel: level)) créditos")
                .font(KType.label)
                .foregroundStyle(KColor.placeholder)

            if cards.isEmpty {
                Text("Sin materias")
                    .font(KType.label)
                    .foregroundStyle(KColor.placeholder)
                    .frame(maxWidth: .infinity, minHeight: 60)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .stroke(KColor.border, style: StrokeStyle(lineWidth: 1, dash: [4]))
                    )
            } else {
                ForEach(cards) { card in
                    Button { selectedCardId = card.id } label: {
                        CourseCard(card: card)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .frame(width: 168, alignment: .topLeading)
    }

    static func roman(_ number: Int) -> String {
        let table: [(Int, String)] = [(10, "X"), (9, "IX"), (5, "V"), (4, "IV"), (1, "I")]
        var rest = number, result = ""
        for (value, symbol) in table {
            while rest >= value {
                result += symbol
                rest -= value
            }
        }
        return result
    }
}

/// The colours each state is painted in.
struct CardStyle {
    let background: Color
    let foreground: Color
    let border: Color?

    init(state: CardState) {
        switch state {
        case .passed:
            background = KColor.passed; foreground = .white; border = nil
        case .inProgress:
            // Purple on the K's green, never white.
            background = KColor.inProgress; foreground = KColor.brand; border = nil
        case .failed:
            background = KColor.error; foreground = .white; border = nil
        case .available:
            background = KColor.surface; foreground = KColor.text; border = KColor.subject
        case .blocked:
            background = KColor.background; foreground = KColor.textSoft; border = KColor.border
        }
    }
}

private struct CourseCard: View {
    let card: BoardCard

    var body: some View {
        let style = CardStyle(state: card.state)
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top, spacing: 4) {
                Text(card.displayName)
                    .font(.system(size: 13, weight: .semibold))
                    .multilineTextAlignment(.leading)
                    .lineLimit(3)
                    .strikethrough(card.isRemovedFromPensum)
                Spacer(minLength: 0)
                if card.state == .blocked {
                    Image(systemName: "lock.fill").font(.system(size: 10))
                } else if card.plannedLevel != nil {
                    Image(systemName: "arrow.left.arrow.right").font(.system(size: 10, weight: .semibold))
                }
            }
            HStack {
                Text(statusLine)
                    .font(.system(size: 11, weight: .medium))
                Spacer(minLength: 0)
                Text("\(card.course.credits) cr")
                    .font(.system(size: 11))
                    .opacity(0.8)
            }
        }
        .foregroundStyle(style.foreground)
        .padding(10)
        .frame(maxWidth: .infinity, minHeight: 72, alignment: .topLeading)
        .background(style.background)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(style.border ?? .clear, lineWidth: card.state == .available ? 1.5 : 1)
        )
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(card.displayName), \(statusLine), \(card.course.credits) créditos")
    }

    private var statusLine: String {
        if let grade = card.entry?.grade, card.state == .passed || card.state == .failed {
            return "\(card.state.label) · \(grade)"
        }
        return card.state.label
    }
}
