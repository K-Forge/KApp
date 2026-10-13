import SwiftUI

/// One course of the semáforo: what it is, what it needs, and the two things the student can change -
/// its status and the semester they plan it for.
struct CourseDetailSheet: View {
    let card: BoardCard
    let board: SemaphoreBoard
    let currentPeriod: String
    /// Returns the reason in words when the save did not go through.
    let onSave: (CourseStatus, String, String) async -> String?
    let onMove: (Int) async -> String?

    @Environment(\.dismiss) private var dismiss

    @State private var status: CourseStatus
    @State private var gradeText: String
    @State private var period: String
    @State private var plannedLevel: Int
    @State private var isSaving = false
    @State private var isMoving = false
    @State private var saveError: String?
    @State private var moveError: String?
    /// The grade and period take the number pad, which has no return key: without a way to put it
    /// away it sits over the save button.
    @FocusState private var editingField: Bool

    init(
        card: BoardCard,
        board: SemaphoreBoard,
        currentPeriod: String,
        onSave: @escaping (CourseStatus, String, String) async -> String?,
        onMove: @escaping (Int) async -> String?
    ) {
        self.card = card
        self.board = board
        self.currentPeriod = currentPeriod
        self.onSave = onSave
        self.onMove = onMove
        _status = State(initialValue: card.entry?.status ?? .pending)
        _gradeText = State(initialValue: card.entry?.grade.map(String.init) ?? "")
        _period = State(initialValue: card.entry?.period ?? currentPeriod)
        _plannedLevel = State(initialValue: card.effectiveLevel)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    header
                    prerequisites
                    statusSection
                    planSection
                }
                .padding(20)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(KColor.background)
            .navigationTitle("Materia")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cerrar") { dismiss() }
                }
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("Listo") { editingField = false }
                        .fontWeight(.semibold)
                }
            }
            .tint(KColor.action)
        }
    }

    // MARK: - What it is

    private var header: some View {
        let style = CardStyle(state: card.state)
        return VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Circle()
                    .fill(Color(apiHex: board.areaColor(card.course.area)))
                    .frame(width: 8, height: 8)
                Text(board.areaName(card.course.area))
                    .font(KType.label)
                    .foregroundStyle(KColor.textSoft)
            }
            Text(card.displayName)
                .font(.system(size: 22, weight: .bold))
                .foregroundStyle(KColor.text)
            Text(facts)
                .font(KType.meta)
                .foregroundStyle(KColor.textSoft)
            Text(card.state.label)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(style.foreground)
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(style.background)
                .clipShape(Capsule())
                .overlay(Capsule().stroke(style.border ?? .clear, lineWidth: 1))
            if card.isRemovedFromPensum {
                InlineMessage(kind: .info, text: "Esta materia ya no está en tu pensum. Su nota se conserva, pero no cuenta para tu avance.")
            }
        }
    }

    private var facts: String {
        var parts: [String] = []
        // `sinuCode`, never `code`: where the plan prints no code, KApp generates one, and a
        // student who quoted it to the registrar would be quoting something invented.
        if let code = card.course.sinuCode { parts.append("Código \(code)") }
        parts.append(plural(card.course.credits, "crédito", "créditos"))
        parts.append("\(DisplayText.hours(card.course.weeklyHours)) h por semana")
        parts.append("Semestre \(card.course.level)")
        return parts.joined(separator: " · ")
    }

    // MARK: - What it needs

    private var prerequisites: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Prerrequisitos").font(KType.section).foregroundStyle(KColor.text)
            if card.course.prerequisites.isEmpty {
                Text("No tiene prerrequisitos.")
                    .font(KType.body)
                    .foregroundStyle(KColor.textSoft)
            } else {
                VStack(spacing: 0) {
                    ForEach(card.course.prerequisites, id: \.self) { code in
                        let prerequisite = board.cards.first { $0.course.code == code }
                        let passed = prerequisite?.state == .passed
                        HStack(spacing: 10) {
                            Image(systemName: passed ? "checkmark.circle.fill" : "circle")
                                .foregroundStyle(passed ? KColor.passed : KColor.placeholder)
                            Text(prerequisite?.displayName ?? "Materia \(code)")
                                .font(KType.body)
                                .foregroundStyle(KColor.text)
                            Spacer()
                            Text(prerequisite?.state.label ?? "")
                                .font(KType.label)
                                .foregroundStyle(KColor.textSoft)
                        }
                        .padding(.vertical, 10)
                        .padding(.horizontal, 14)
                    }
                }
                .kCard(radius: KMetrics.rowRadius)
            }
        }
    }

    // MARK: - Status

    private var statusSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Estado").font(KType.section).foregroundStyle(KColor.text)

            Picker("Estado", selection: $status) {
                Text("Pendiente").tag(CourseStatus.pending)
                Text("En curso").tag(CourseStatus.inProgress)
                Text("Aprobada").tag(CourseStatus.passed)
                Text("Perdida").tag(CourseStatus.failed)
            }
            .pickerStyle(.segmented)
            .onChange(of: status) { saveError = nil }

            if status == .passed || status == .failed {
                labeledField("Nota final, de 0 a 50") {
                    TextField("42", text: $gradeText)
                        .keyboardType(.numberPad)
                        .focused($editingField)
                }
            }
            if status != .pending {
                labeledField("Periodo, como \(currentPeriod)") {
                    TextField(currentPeriod, text: $period)
                        .keyboardType(.numberPad)
                        .focused($editingField)
                }
            }

            if let saveError {
                Label(saveError, systemImage: "exclamationmark.circle")
                    .font(.system(size: 14))
                    .foregroundStyle(KColor.error)
            }

            PrimaryButton(title: "Guardar estado", isLoading: isSaving) {
                editingField = false
                Task {
                    isSaving = true
                    saveError = await onSave(status, gradeText, period)
                    isSaving = false
                    if saveError == nil { dismiss() }
                }
            }
            .disabled(isSaving)
        }
    }

    private func labeledField<Field: View>(_ label: String, @ViewBuilder field: () -> Field) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(KType.fieldLabel)
                .foregroundStyle(KColor.textSoft)
            field()
                .font(.system(size: 16))
                .padding(.horizontal, 14)
                .frame(height: KMetrics.fieldHeight)
                .background(
                    RoundedRectangle(cornerRadius: KMetrics.fieldRadius, style: .continuous)
                        .fill(KColor.surface)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: KMetrics.fieldRadius, style: .continuous)
                        .stroke(KColor.border, lineWidth: 1)
                )
        }
    }

    // MARK: - Plan

    private var planSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Planear").font(KType.section).foregroundStyle(KColor.text)
            Text("El semestre en que piensas verla. Es solo tu plan: no cambia lo que los prerrequisitos permiten.")
                .font(KType.meta)
                .foregroundStyle(KColor.textSoft)

            HStack {
                Text("Semestre")
                    .font(KType.body)
                    .foregroundStyle(KColor.text)
                Spacer()
                Picker("Semestre", selection: $plannedLevel) {
                    ForEach(board.levels, id: \.self) { level in
                        Text(level == card.course.level ? "\(level) (del pensum)" : "\(level)").tag(level)
                    }
                }
                .pickerStyle(.menu)
            }
            .padding(.horizontal, 14)
            .frame(minHeight: KMetrics.fieldHeight)
            .kCard(radius: KMetrics.fieldRadius)
            .onChange(of: plannedLevel) { moveError = nil }

            if let moveError {
                Label(moveError, systemImage: "exclamationmark.circle")
                    .font(.system(size: 14))
                    .foregroundStyle(KColor.error)
            }

            if plannedLevel != card.effectiveLevel {
                SecondaryButton(
                    title: isMoving ? "Moviendo…" : "Planear para el semestre \(plannedLevel)",
                    systemImage: "arrow.left.arrow.right"
                ) {
                    move(to: plannedLevel)
                }
                .disabled(isMoving)
            } else if card.plannedLevel != nil {
                SecondaryButton(
                    title: isMoving ? "Moviendo…" : "Devolver al semestre \(card.course.level)",
                    systemImage: "arrow.uturn.backward"
                ) {
                    move(to: card.course.level)
                }
                .disabled(isMoving)
            }
        }
    }

    private func move(to level: Int) {
        Task {
            isMoving = true
            moveError = await onMove(level)
            isMoving = false
            if moveError == nil { dismiss() }
        }
    }
}
