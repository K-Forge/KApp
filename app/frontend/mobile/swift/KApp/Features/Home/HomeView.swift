import SwiftUI

/// Inicio.
///
/// Everything on it that names another screen opens that screen: the avatar, the four shortcuts,
/// the two section links, "Cómo llegar", and the button on each empty state. Mirrors
/// `ui/home/HomeScreen.kt`.
struct HomeView: View {
    let model: HomeViewModel
    let open: (KAppDestination) -> Void

    var body: some View {
        BandScreen(
            title: model.student.firstName.isEmpty ? "Hola" : "Hola, \(model.student.firstName)",
            onRefresh: model.load,
            trailing: { avatar }
        ) {
            dayCard

            shortcuts
                .padding(.top, 18)

            SectionHeading(title: "Tu semestre", link: "Ver semáforo") { open(.semaphore) }
                .padding(.top, 14)
            semesterCard
                .padding(.top, 4)

            if case .classes(_, let later) = model.day, !later.isEmpty {
                SectionHeading(title: "Resto del día", link: "Ver horario") { open(.schedule) }
                    .padding(.top, 14)
                VStack(spacing: 10) {
                    ForEach(Array(later.enumerated()), id: \.offset) { _, item in
                        ClassRow(summary: item)
                    }
                }
                .padding(.top, 4)
            }
        }
    }

    private var avatar: some View {
        Button { open(.profile) } label: {
            Text(model.student.initials)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 34, height: 34)
                .background(Circle().fill(.white.opacity(0.18)))
                .overlay(Circle().stroke(.white.opacity(0.28), lineWidth: 1))
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Abrir tu perfil")
    }

    // MARK: - Day

    @ViewBuilder
    private var dayCard: some View {
        switch model.day {
        case .loading:
            VStack(alignment: .leading, spacing: 12) {
                SkeletonBar(width: 104, height: 11)
                SkeletonBar(width: 188, height: 20, radius: 7)
                SkeletonBar(width: 236, height: 13, color: KColor.borderSoft)
                SkeletonBar(width: 142, height: 44, color: KColor.borderSoft, radius: 14)
            }
            .padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .kCard(elevated: true)
            .accessibilityLabel("Cargando")

        case .noSchedule:
            NoticeCard(
                systemImage: "calendar.badge.plus",
                tint: KColor.subject,
                title: "Aún no armas tu horario",
                message: "Agrega tus materias y aparecerán aquí.",
                actionTitle: "Armar mi horario"
            ) { open(.schedule) }

        case .noClassesToday:
            NoticeCard(
                systemImage: "lightbulb",
                tint: KColor.brand,
                circle: KColor.inProgress,
                title: "Hoy no tienes clases",
                actionTitle: "Ver la semana",
                actionIsLink: true
            ) { open(.schedule) }

        case .doneForToday:
            NoticeCard(
                systemImage: "checkmark",
                tint: KColor.brand,
                circle: KColor.inProgress,
                title: "Terminaste las clases de hoy",
                actionTitle: "Ver la semana",
                actionIsLink: true
            ) { open(.schedule) }

        case .classes(let next, _):
            NextClassCard(summary: next) { open(.map) }

        case .failed(let error):
            FailureCard(error: error) {
                Task { await model.loadDay() }
            }
        }
    }

    // MARK: - Shortcuts

    private var shortcuts: some View {
        HStack(spacing: 12) {
            ForEach([KAppDestination.semaphore, .schedule, .map, .profile]) { destination in
                Button { open(destination) } label: {
                    VStack(spacing: 8) {
                        Image(systemName: destination.symbol)
                            .font(.system(size: 20))
                            .foregroundStyle(destination.colour)
                            .frame(maxWidth: .infinity)
                            .frame(height: 52)
                            .background(destination.colour.opacity(destination == .schedule ? 0.14 : 0.12))
                            .clipShape(RoundedRectangle(cornerRadius: KMetrics.tileRadius, style: .continuous))
                        Text(destination.title)
                            .font(KType.label)
                            .foregroundStyle(KColor.text)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
    }

    // MARK: - Semester

    @ViewBuilder
    private var semesterCard: some View {
        switch model.semester {
        case .loading:
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    SkeletonBar(width: 96, height: 14, radius: 7)
                    Spacer()
                    SkeletonBar(width: 54, height: 22, radius: 7)
                }
                SkeletonBar(width: nil, height: 10, color: KColor.borderSoft, radius: 5)
                SkeletonBar(width: 220, height: 11, color: KColor.borderSoft)
            }
            .padding(KMetrics.cardPadding)
            .kCard()

        case .ready(let progress):
            SemesterCard(progress: progress)

        case .failed(let error):
            FailureCard(error: error) {
                Task { await model.loadSemester() }
            }
        }
    }
}

// MARK: - Pieces

private struct NextClassCard: View {
    let summary: ClassSummary
    let onDirections: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            Color(apiHex: summary.colorHex).frame(width: 6)

            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Text("PRÓXIMA CLASE")
                        .font(KType.eyebrow)
                        .tracking(0.9)
                        .foregroundStyle(KColor.textSoft)
                    Spacer()
                    if let minutes = summary.startsInMinutes {
                        Text(ClassSummary.startsInLabel(minutes))
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(KColor.brand)
                            .padding(.horizontal, 9)
                            .padding(.vertical, 4)
                            .background(KColor.inProgress.opacity(0.32))
                            .clipShape(RoundedRectangle(cornerRadius: 9, style: .continuous))
                    }
                }
                .frame(minHeight: 22)

                Text(summary.courseName)
                    .font(KType.subject)
                    .foregroundStyle(KColor.text)
                    .padding(.top, 9)

                Text("\(summary.startTime) – \(summary.endTime) · \(summary.place)")
                    .font(.system(size: 14))
                    .foregroundStyle(KColor.textSoft)
                    .padding(.top, 3)

                PrimaryButton(title: "Cómo llegar", systemImage: "mappin.and.ellipse", fullWidth: false, action: onDirections)
                    .padding(.top, 14)
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .fixedSize(horizontal: false, vertical: true)
        .kCard(elevated: true)
    }
}

struct SemesterCard: View {
    let progress: SemesterProgress

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Semestre \(progress.level)")
                        .font(KType.rowTitle)
                        .foregroundStyle(KColor.text)
                    if let courses = progress.coursesInProgress {
                        Text(plural(courses, "materia en curso", "materias en curso"))
                            .font(KType.meta)
                            .foregroundStyle(KColor.textSoft)
                    }
                }
                Spacer()
                Text("\(Int(progress.percentComplete.rounded()))%")
                    .font(.system(size: 26, weight: .bold))
                    .foregroundStyle(KColor.brand)
            }

            ProgressBar(passed: progress.passedFraction, inProgress: progress.inProgressFraction)
                .padding(.top, 13)

            HStack(spacing: 14) {
                legend(KColor.passed, plural(progress.creditsPassed, "aprobado", "aprobados"))
                legend(KColor.inProgress, "\(progress.creditsInProgress) en curso")
                legend(KColor.pending, plural(progress.creditsRemaining, "restante", "restantes"))
            }
            .padding(.top, 13)
        }
        .padding(KMetrics.cardPadding)
        .kCard()
        .accessibilityElement(children: .combine)
    }

    private func legend(_ color: Color, _ text: String) -> some View {
        HStack(spacing: 6) {
            Circle().fill(color).frame(width: 8, height: 8)
            Text(text)
                .font(KType.label)
                .foregroundStyle(KColor.textSoft)
        }
    }
}

/// Green for credits passed, lime for credits in progress, grey for the rest.
struct ProgressBar: View {
    let passed: Double
    let inProgress: Double

    var body: some View {
        GeometryReader { geo in
            HStack(spacing: 0) {
                KColor.passed.frame(width: geo.size.width * min(max(passed, 0), 1))
                KColor.inProgress.frame(width: geo.size.width * min(max(inProgress, 0), 1 - min(max(passed, 0), 1)))
                KColor.pending
            }
        }
        .frame(height: 10)
        .clipShape(Capsule())
    }
}

struct ClassRow: View {
    let summary: ClassSummary

    var body: some View {
        HStack(spacing: 0) {
            Color(apiHex: summary.colorHex).frame(width: 4)

            HStack(spacing: 14) {
                VStack(alignment: .leading, spacing: 1) {
                    Text(summary.startTime)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(KColor.text)
                    Text(summary.endTime)
                        .font(KType.label)
                        .foregroundStyle(KColor.placeholder)
                }
                .frame(width: 42, alignment: .leading)

                VStack(alignment: .leading, spacing: 2) {
                    Text(summary.courseName)
                        .font(KType.rowTitle)
                        .foregroundStyle(KColor.text)
                        .lineLimit(2)
                    Text(summary.place)
                        .font(KType.label)
                        .foregroundStyle(KColor.textSoft)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
        }
        .frame(minHeight: KMetrics.rowHeight)
        .kCard(radius: KMetrics.rowRadius)
    }
}

/// "1 aprobado", "112 aprobados".
func plural(_ count: Int, _ one: String, _ other: String) -> String {
    "\(count) \(count == 1 ? one : other)"
}
