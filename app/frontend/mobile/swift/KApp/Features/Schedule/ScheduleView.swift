import SwiftUI

/// Horario: the week, one day at a time, and the courses on it.
struct ScheduleView: View {
    let model: ScheduleViewModel
    let open: (KAppDestination) -> Void

    @State private var selectedClass: ClassOccurrence?
    @State private var addCourse: AddCourseViewModel?

    var body: some View {
        BandScreen(title: "Horario", onRefresh: model.load) {
            weekCard

            if let notice = model.notice {
                InlineMessage(
                    kind: notice.kind == .success ? .success : .error,
                    text: notice.text,
                    onDismiss: { model.notice = nil }
                )
                .padding(.top, 12)
            }

            content
                .padding(.top, 14)
        }
        .sheet(item: $selectedClass) { occurrence in
            ClassDetailSheet(
                occurrence: occurrence,
                day: model.selectedDay,
                isRemoving: model.removingEnrollmentId == occurrence.enrollmentId,
                onDirections: {
                    selectedClass = nil
                    open(.map)
                },
                onRemove: {
                    Task {
                        if await model.remove(occurrence) { selectedClass = nil }
                    }
                }
            )
            .presentationDetents([.medium, .large])
        }
        .sheet(item: $addCourse) { form in
            AddCourseSheet(model: form) { added in
                addCourse = nil
                model.courseAdded(added)
            }
        }
    }

    // MARK: - Week

    private var weekCard: some View {
        VStack(spacing: 14) {
            HStack(spacing: 4) {
                stepButton("chevron.left", label: "Semana anterior") {
                    Task { await model.showPreviousWeek() }
                }
                VStack(spacing: 2) {
                    Text("SEMANA")
                        .font(KType.eyebrow)
                        .tracking(0.9)
                        .foregroundStyle(KColor.textSoft)
                    Text(model.weekTitle)
                        .font(KType.rowTitle)
                        .foregroundStyle(KColor.text)
                }
                .frame(maxWidth: .infinity)
                stepButton("chevron.right", label: "Semana siguiente") {
                    Task { await model.showNextWeek() }
                }
            }
            .overlay(alignment: .trailing) {
                if model.isAwayFromThisWeek {
                    Button("Hoy") { Task { await model.showThisWeek() } }
                        .font(KType.link)
                        .tint(KColor.action)
                        .padding(.trailing, 48)
                }
            }

            HStack(spacing: 6) {
                ForEach(DayOfWeek.allCases) { day in
                    dayChip(day)
                }
            }
        }
        .padding(14)
        .kCard(elevated: true)
    }

    private func stepButton(_ symbol: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(KColor.action)
                .frame(width: 44, height: 44)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    private func dayChip(_ day: DayOfWeek) -> some View {
        let selected = day == model.selectedDay
        let today = model.isToday(day)
        let hasClasses = !model.classes(on: day).isEmpty

        return Button {
            model.selectedDay = day
        } label: {
            VStack(spacing: 3) {
                Text(day.initial)
                    .font(.system(size: 12, weight: .semibold))
                Text(model.date(of: day).map { "\($0.day)" } ?? " ")
                    .font(.system(size: 15, weight: selected || today ? .bold : .regular))
                Circle()
                    .fill(hasClasses ? (selected ? Color.white : KColor.action) : .clear)
                    .frame(width: 5, height: 5)
            }
            .foregroundStyle(selected ? Color.white : (today ? KColor.action : KColor.text))
            .frame(maxWidth: .infinity, minHeight: 58)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(selected ? KColor.action : KColor.background)
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(day.name)\(today ? ", hoy" : "")\(hasClasses ? ", con clases" : "")")
        .accessibilityAddTraits(selected ? [.isSelected] : [])
    }

    // MARK: - Day

    @ViewBuilder
    private var content: some View {
        switch model.state {
        case .loading:
            VStack(spacing: 10) {
                ForEach(0..<3, id: \.self) { _ in
                    HStack(spacing: 14) {
                        SkeletonBar(width: 42, height: 30, radius: 6)
                        VStack(alignment: .leading, spacing: 8) {
                            SkeletonBar(width: 180, height: 14)
                            SkeletonBar(width: 120, height: 11, color: KColor.borderSoft)
                        }
                        Spacer()
                    }
                    .padding(14)
                    .kCard(radius: KMetrics.rowRadius)
                }
            }

        case .noSchedule:
            NoticeCard(
                systemImage: "calendar.badge.plus",
                tint: KColor.subject,
                title: "Aún no armas tu horario",
                message: "Crea tu horario del periodo \(ScheduleViewModel.periodLabel(AcademicCalendar.period(containing: model.today))) y agrega tus materias.",
                actionTitle: "Armar mi horario",
                isLoading: model.isCreatingSchedule
            ) {
                Task { await model.createSchedule() }
            }

        case .failed(let error):
            FailureCard(error: error) { Task { await model.load() } }

        case .loaded:
            let classes = model.classes(on: model.selectedDay)

            SectionHeading(title: dayHeading)

            if classes.isEmpty {
                NoticeCard(
                    systemImage: "sun.max",
                    tint: KColor.brand,
                    circle: KColor.inProgress,
                    title: "No tienes clases este día"
                )
                .padding(.top, 4)
            } else {
                VStack(spacing: 10) {
                    ForEach(classes) { occurrence in
                        Button { selectedClass = occurrence } label: {
                            ScheduleClassRow(occurrence: occurrence)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.top, 4)
            }

            PrimaryButton(title: "Agregar materia", systemImage: "plus") {
                addCourse = model.makeAddCourseModel()
            }
            .padding(.top, 16)
        }
    }

    private var dayHeading: String {
        guard let date = model.date(of: model.selectedDay) else { return model.selectedDay.name }
        return "\(model.selectedDay.name) \(date.shortLabel)"
    }
}

/// One class on the day list: rail, times, name, place, professor.
private struct ScheduleClassRow: View {
    let occurrence: ClassOccurrence

    var body: some View {
        HStack(spacing: 0) {
            Color(apiHex: occurrence.color).frame(width: 4)

            HStack(alignment: .top, spacing: 14) {
                VStack(alignment: .leading, spacing: 1) {
                    Text(DisplayText.time(occurrence.startTime))
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(KColor.text)
                    Text(DisplayText.time(occurrence.endTime))
                        .font(KType.label)
                        .foregroundStyle(KColor.placeholder)
                }
                .frame(width: 42, alignment: .leading)

                VStack(alignment: .leading, spacing: 3) {
                    Text(DisplayText.title(occurrence.courseName))
                        .font(KType.rowTitle)
                        .foregroundStyle(KColor.text)
                        .multilineTextAlignment(.leading)
                    Text(DisplayText.place(room: occurrence.room, campus: occurrence.campus))
                        .font(KType.label)
                        .foregroundStyle(occurrence.room == nil ? KColor.error : KColor.textSoft)
                    Text(DisplayText.title(occurrence.professor))
                        .font(KType.label)
                        .foregroundStyle(KColor.placeholder)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(KColor.placeholder)
                    .padding(.top, 2)
            }
            .padding(14)
        }
        .kCard(radius: KMetrics.rowRadius)
        .contentShape(Rectangle())
    }
}

/// What a tapped class offers: how to get there, and taking the course off the timetable.
private struct ClassDetailSheet: View {
    let occurrence: ClassOccurrence
    let day: DayOfWeek
    let isRemoving: Bool
    let onDirections: () -> Void
    let onRemove: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var confirmingRemoval = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Capsule()
                    .fill(Color(apiHex: occurrence.color))
                    .frame(width: 36, height: 6)
                Spacer()
                Button("Cerrar") { dismiss() }
                    .font(KType.link)
                    .tint(KColor.action)
            }

            Text(DisplayText.title(occurrence.courseName))
                .font(.system(size: 22, weight: .bold))
                .foregroundStyle(KColor.text)
                .padding(.top, 12)

            VStack(alignment: .leading, spacing: 10) {
                detail("clock", "\(day.name), \(DisplayText.time(occurrence.startTime)) – \(DisplayText.time(occurrence.endTime))")
                detail("mappin.and.ellipse", DisplayText.place(room: occurrence.room, campus: occurrence.campus))
                detail("person", DisplayText.title(occurrence.professor))
                detail("number", "Código \(occurrence.courseCode)")
            }
            .padding(.top, 16)

            Spacer(minLength: 24)

            VStack(spacing: 10) {
                PrimaryButton(title: "Cómo llegar", systemImage: "mappin.and.ellipse", action: onDirections)
                SecondaryButton(title: isRemoving ? "Quitando…" : "Quitar del horario", systemImage: "trash", tint: KColor.error) {
                    confirmingRemoval = true
                }
                .disabled(isRemoving)
            }
        }
        .padding(24)
        .background(KColor.surface)
        .confirmationDialog(
            "¿Quitar \(DisplayText.title(occurrence.courseName)) de tu horario?",
            isPresented: $confirmingRemoval,
            titleVisibility: .visible
        ) {
            Button("Quitar del horario", role: .destructive, action: onRemove)
            Button("Cancelar", role: .cancel) {}
        } message: {
            Text("Se quita de todos los días en que la tienes, no solo de este.")
        }
    }

    private func detail(_ symbol: String, _ text: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: symbol)
                .foregroundStyle(KColor.textSoft)
                .frame(width: 20)
            Text(text)
                .font(KType.body)
                .foregroundStyle(KColor.text)
        }
    }
}
