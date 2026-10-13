import SwiftUI

/// "Agregar materia": a course from the student's semáforo, a weekly slot, a room and a colour.
struct AddCourseSheet: View {
    @Bindable var model: AddCourseViewModel
    let onAdded: (AddedCourse) -> Void

    @Environment(\.dismiss) private var dismiss
    /// The room takes the number pad, which has no return key to put it away with.
    @FocusState private var editingText: Bool

    var body: some View {
        NavigationStack {
            Form {
                courseSection

                Section("Cuándo") {
                    Picker("Día", selection: $model.day) {
                        ForEach(DayOfWeek.allCases) { Text($0.name).tag($0) }
                    }
                    Picker("Empieza", selection: $model.startMinutes) {
                        ForEach(AddCourseViewModel.timeSlots, id: \.self) { Text(DisplayText.time(TimeOfDay.string(minutes: $0))).tag($0) }
                    }
                    Picker("Termina", selection: $model.endMinutes) {
                        ForEach(AddCourseViewModel.timeSlots, id: \.self) { Text(DisplayText.time(TimeOfDay.string(minutes: $0))).tag($0) }
                    }
                }

                Section {
                    TextField("Salón, como 302", text: $model.room)
                        .keyboardType(.numberPad)
                        .focused($editingText)
                    TextField("Grupo", text: $model.group)
                        .textInputAutocapitalization(.characters)
                        .focused($editingText)
                    TextField("Profesor (opcional)", text: $model.professor)
                        .textInputAutocapitalization(.words)
                        .focused($editingText)
                } header: {
                    Text("Dónde y con quién")
                } footer: {
                    Text("Si aún no sabes el salón, déjalo vacío. La clase se agrega para todo el semestre.")
                }

                Section("Color") {
                    HStack(spacing: 12) {
                        ForEach(KColor.courseChoices, id: \.hex) { choice in
                            Button {
                                model.colorHex = choice.hex
                            } label: {
                                Circle()
                                    .fill(Color(apiHex: choice.hex))
                                    .frame(width: 34, height: 34)
                                    .overlay(
                                        Circle().stroke(KColor.text, lineWidth: model.colorHex == choice.hex ? 3 : 0)
                                            .padding(-4)
                                    )
                                    .frame(width: 44, height: 44)
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(choice.name)
                            .accessibilityAddTraits(model.colorHex == choice.hex ? [.isSelected] : [])
                        }
                    }
                }

                if let message = model.saveError ?? (model.selectedCourse == nil ? nil : model.validationMessage) {
                    Section {
                        Label(message, systemImage: "exclamationmark.circle")
                            .foregroundStyle(KColor.error)
                            .font(.system(size: 14))
                    }
                }
            }
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle("Agregar materia")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("Listo") { editingText = false }
                        .fontWeight(.semibold)
                }
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if model.isSaving {
                        ProgressView()
                    } else {
                        Button("Agregar") {
                            Task {
                                if let added = await model.save() { onAdded(added) }
                            }
                        }
                        .fontWeight(.semibold)
                        .disabled(model.validationMessage != nil)
                    }
                }
            }
            .tint(KColor.action)
        }
        .task { await model.load() }
    }

    @ViewBuilder
    private var courseSection: some View {
        Section {
            switch model.candidates {
            case .loading:
                HStack {
                    ProgressView()
                    Text("Buscando las materias de tu plan…")
                        .foregroundStyle(KColor.textSoft)
                }
            case .failed(let error):
                VStack(alignment: .leading, spacing: 8) {
                    Text(error.userMessage).foregroundStyle(KColor.error)
                    Button("Reintentar") { Task { await model.load() } }
                }
            case .loaded(let list) where list.isEmpty:
                Text("No hay materias disponibles para agregar en tu semáforo.")
                    .foregroundStyle(KColor.textSoft)
            case .loaded(let list):
                ForEach(list) { candidate in
                    Button {
                        model.selectedCourseId = candidate.id
                    } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(candidate.displayName)
                                    .foregroundStyle(KColor.text)
                                Text("\(candidate.statusLabel) · Semestre \(candidate.course.level) · \(candidate.course.credits) créditos")
                                    .font(KType.label)
                                    .foregroundStyle(KColor.textSoft)
                            }
                            Spacer()
                            if model.selectedCourseId == candidate.id {
                                Image(systemName: "checkmark")
                                    .foregroundStyle(KColor.action)
                                    .fontWeight(.semibold)
                            }
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(model.selectedCourseId == candidate.id ? [.isSelected] : [])
                }
            }
        } header: {
            Text("Materia")
        } footer: {
            Text("Salen de tu semáforo: las que cursas, las que ya puedes ver y las que debes repetir.")
        }
    }
}
