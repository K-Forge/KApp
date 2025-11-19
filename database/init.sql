-- Script de inicialización de base de datos para K-APP (PostgreSQL)
-- Versión Unificada: K-APP SRS + Diagramas de Draw.io + Jerarquía de Clases

-- ==============================================================================================
-- 1. ENUMS Y TIPOS DE DATOS
-- Definición de tipos enumerados para estandarizar valores en la base de datos.
-- ==============================================================================================
CREATE TYPE id_type_enum AS ENUM ('CC', 'TI', 'CE', 'PASAPORTE'); -- Tipos de identificación
CREATE TYPE employee_type_enum AS ENUM ('ACADEMICO', 'ADMINISTRATIVO', 'APOYO', 'BIENESTAR'); -- Categorías de empleados
CREATE TYPE contract_type_enum AS ENUM ('TIEMPO_COMPLETO', 'MEDIO_TIEMPO', 'TEMPORAL', 'INDEFINIDO'); -- Tipos de contrato
CREATE TYPE employee_status_enum AS ENUM ('ACTIVO', 'INACTIVO', 'SUSPENDIDO', 'LICENCIA'); -- Estado laboral
CREATE TYPE program_level_enum AS ENUM ('PREGRADO', 'POSGRADO', 'TECNOLOGIA', 'MAESTRIA', 'DOCTORADO', 'CURSOS_DIPLOMADOS', 'ESPECIALIZACION'); -- Niveles académicos
CREATE TYPE student_status_enum AS ENUM ('ACTIVO', 'INACTIVO', 'GRADUADO', 'SUSPENDIDO'); -- Estado del estudiante
CREATE TYPE course_status_enum AS ENUM ('INSCRITO', 'APROBADO', 'REPROBADO', 'CANCELADO'); -- Estado de una materia inscrita
CREATE TYPE service_type_enum AS ENUM ('BIENESTAR', 'PSICOLOGIA', 'TUTORIA', 'COORDINACION'); -- Tipos de servicios universitarios
CREATE TYPE event_category_enum AS ENUM ('CULTURAL', 'ACADEMICO', 'DEPORTIVO', 'INSTITUCIONAL'); -- Categorías de eventos
CREATE TYPE card_status_enum AS ENUM ('ACTIVO', 'INACTIVO', 'PERDIDO', 'VENCIDO'); -- Estado del carnet
CREATE TYPE employee_role_enum AS ENUM ( -- Roles específicos de empleados
    'PROFESOR_PLANTA', 'PROFESOR_CATEDRA', 'INVESTIGADOR', 'TUTOR', 
    'DIRECTOR', 'SECRETARIA_ACADEMICA', 'RECURSOS_HUMANOS', 'FINANZAS', 'ADMISIONES', 
    'BIBLIOTECARIO', 'SOPORTE_IT', 'MANTENIMIENTO', 'SEGURIDAD', 
    'PSICOLOGO', 'CULTURAL_DEPORTIVO', 'ADMINISTRADOR_SISTEMA'
);
CREATE TYPE guest_type_enum AS ENUM ('VISITANTE', 'COLABORADOR'); -- Tipos de invitados
CREATE TYPE semaphore_status_enum AS ENUM ('ACTIVO', 'INACTIVO', 'BORRADOR', 'OBSOLETO'); -- Estado del plan de estudios (semáforo)

-- Función para auditoría automática (actualiza updated_at)
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- ==============================================================================================
-- 1.1. SISTEMA DE AUDITORÍA (LOGS)
-- Tabla y función para registrar cambios manuales o críticos en la base de datos.
-- ==============================================================================================
CREATE TABLE audit_log (
    audit_id SERIAL PRIMARY KEY,
    table_name VARCHAR(50) NOT NULL,
    operation VARCHAR(10) NOT NULL, -- INSERT, UPDATE, DELETE
    old_data JSONB, -- Datos anteriores (solo en UPDATE/DELETE)
    new_data JSONB, -- Datos nuevos (solo en INSERT/UPDATE)
    changed_by VARCHAR(50) DEFAULT CURRENT_USER, -- Usuario de BD que hizo el cambio
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE OR REPLACE FUNCTION log_audit_event()
RETURNS TRIGGER AS $$
BEGIN
    IF (TG_OP = 'DELETE') THEN
        INSERT INTO audit_log (table_name, operation, old_data, changed_by)
        VALUES (TG_TABLE_NAME, TG_OP, row_to_json(OLD), CURRENT_USER);
        RETURN OLD;
    ELSIF (TG_OP = 'UPDATE') THEN
        INSERT INTO audit_log (table_name, operation, old_data, new_data, changed_by)
        VALUES (TG_TABLE_NAME, TG_OP, row_to_json(OLD), row_to_json(NEW), CURRENT_USER);
        RETURN NEW;
    ELSIF (TG_OP = 'INSERT') THEN
        INSERT INTO audit_log (table_name, operation, new_data, changed_by)
        VALUES (TG_TABLE_NAME, TG_OP, row_to_json(NEW), CURRENT_USER);
        RETURN NEW;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- ==============================================================================================
-- 2. IDENTIDAD Y PERSONA (BASE)
-- Tabla central que almacena la información personal de todos los usuarios.
-- ==============================================================================================
CREATE TABLE person (
    person_id SERIAL PRIMARY KEY, -- Identificador único interno
    identification_number INT NOT NULL UNIQUE, -- Número de documento de identidad
    identification_type id_type_enum NOT NULL, -- Tipo de documento (CC, TI, etc.)
    first_name VARCHAR(50) NOT NULL, -- Nombres
    last_name VARCHAR(50) NOT NULL, -- Apellidos
    phone VARCHAR(15), -- Número de contacto
    home_address VARCHAR(50), -- Dirección de residencia
    birth_date DATE, -- Fecha de nacimiento
    avatar TEXT, -- URL de la foto de perfil (S3, Cloudinary, etc.)
    
    -- Metadatos de auditoría
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- Fecha de creación del registro
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- Fecha de última actualización
    is_active BOOLEAN DEFAULT TRUE -- Indicador de borrado lógico
);

CREATE TRIGGER update_person_modtime BEFORE UPDATE ON person FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER audit_person_changes AFTER INSERT OR UPDATE OR DELETE ON person FOR EACH ROW EXECUTE FUNCTION log_audit_event();

-- ==============================================================================================
-- 3. MEMBRESÍA Y ACCESO
-- Gestión de cuentas de usuario, credenciales y tarjetas institucionales.
-- ==============================================================================================

-- Tabla Member: Usuarios con vínculo formal con la universidad (Estudiantes y Empleados)
CREATE TABLE member (
    member_id SERIAL PRIMARY KEY, -- Identificador de miembro
    person_id INT REFERENCES person(person_id) UNIQUE NOT NULL, -- Relación 1:1 con Persona
    university_code VARCHAR(20) UNIQUE NOT NULL, -- Código institucional único
    university_email VARCHAR(100) UNIQUE NOT NULL, -- Correo institucional
    password_hash VARCHAR(255) NOT NULL, -- Hash de la contraseña
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- Auditoría
);
CREATE TRIGGER update_member_modtime BEFORE UPDATE ON member FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER audit_member_changes AFTER INSERT OR UPDATE OR DELETE ON member FOR EACH ROW EXECUTE FUNCTION log_audit_event();

-- Tabla Institutional Card: Carnet digital o físico
CREATE TABLE institutional_card (
    institutional_card_id SERIAL PRIMARY KEY, -- ID del carnet
    member_id INT REFERENCES member(member_id) UNIQUE NOT NULL, -- Dueño del carnet
    status card_status_enum NOT NULL, -- Estado del carnet
    photo TEXT NOT NULL, -- Foto específica del carnet
    expedition_date DATE DEFAULT CURRENT_DATE, -- Fecha de expedición
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- Auditoría
);
CREATE TRIGGER update_card_modtime BEFORE UPDATE ON institutional_card FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER audit_card_changes AFTER INSERT OR UPDATE OR DELETE ON institutional_card FOR EACH ROW EXECUTE FUNCTION log_audit_event();

-- Tabla Guest: Usuarios externos con acceso temporal
CREATE TABLE guest (
    guest_id SERIAL PRIMARY KEY, -- ID de invitado
    person_id INT REFERENCES person(person_id) UNIQUE NOT NULL, -- Relación 1:1 con Persona
    guest_type guest_type_enum NOT NULL, -- Tipo de invitado
    personal_email VARCHAR(100) UNIQUE NOT NULL, -- Correo personal para contacto
    access_start_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- Inicio del permiso de acceso
    access_end_date TIMESTAMP, -- Fin del permiso de acceso
    reason VARCHAR(255), -- Motivo de la visita o acceso
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- Auditoría
);
CREATE TRIGGER update_guest_modtime BEFORE UPDATE ON guest FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER audit_guest_changes AFTER INSERT OR UPDATE OR DELETE ON guest FOR EACH ROW EXECUTE FUNCTION log_audit_event();

-- ==============================================================================================
-- 4. ROLES UNIVERSITARIOS (ESTUDIANTES Y EMPLEADOS)
-- Definición de los roles específicos que puede tener un miembro.
-- ==============================================================================================

-- Tabla Student: Información específica del rol de estudiante
CREATE TABLE student (
    student_id SERIAL PRIMARY KEY, -- ID de estudiante
    member_id INT REFERENCES member(member_id) UNIQUE NOT NULL, -- Relación 1:1 con Miembro
    student_code VARCHAR(20) UNIQUE NOT NULL, -- Código estudiantil (puede ser igual al university_code)
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- Auditoría
);
CREATE TRIGGER update_student_modtime BEFORE UPDATE ON student FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER audit_student_changes AFTER INSERT OR UPDATE OR DELETE ON student FOR EACH ROW EXECUTE FUNCTION log_audit_event();

-- Tabla Employee: Información específica del rol de empleado
CREATE TABLE employee (
    employee_id SERIAL PRIMARY KEY, -- ID de empleado
    member_id INT REFERENCES member(member_id) UNIQUE NOT NULL, -- Relación 1:1 con Miembro
    employee_code VARCHAR(20) UNIQUE NOT NULL, -- Código de empleado
    employee_type employee_type_enum NOT NULL, -- Tipo general (Académico, Admin, etc.)
    employee_role employee_role_enum NOT NULL, -- Cargo específico
    contract_type contract_type_enum NOT NULL, -- Tipo de vinculación laboral
    hire_date DATE NOT NULL, -- Fecha de contratación
    salary DECIMAL(15, 2) NOT NULL, -- Salario asignado
    status employee_status_enum NOT NULL, -- Estado actual del empleado
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- Auditoría
);
CREATE TRIGGER update_employee_modtime BEFORE UPDATE ON employee FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER audit_employee_changes AFTER INSERT OR UPDATE OR DELETE ON employee FOR EACH ROW EXECUTE FUNCTION log_audit_event();

-- ==============================================================================================
-- 5. ESTRUCTURA ACADÉMICA (CATÁLOGO)
-- Definición de facultades, programas, cursos y planes de estudio (Semáforos).
-- ==============================================================================================

-- Tabla Faculty: Facultades de la universidad
CREATE TABLE faculty (
    faculty_id SERIAL PRIMARY KEY, -- ID de facultad
    name VARCHAR(100) NOT NULL, -- Nombre de la facultad
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- Auditoría
);
CREATE TRIGGER update_faculty_modtime BEFORE UPDATE ON faculty FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER audit_faculty_changes AFTER INSERT OR UPDATE OR DELETE ON faculty FOR EACH ROW EXECUTE FUNCTION log_audit_event();

-- Tabla Program: Programas académicos (Carreras)
CREATE TABLE program (
    program_id SERIAL PRIMARY KEY, -- ID de programa
    faculty_id INT REFERENCES faculty(faculty_id) NOT NULL, -- Facultad a la que pertenece
    name VARCHAR(100) NOT NULL, -- Nombre del programa
    level program_level_enum NOT NULL, -- Nivel (Pregrado, Posgrado, etc.)
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- Auditoría
);
CREATE TRIGGER update_program_modtime BEFORE UPDATE ON program FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER audit_program_changes AFTER INSERT OR UPDATE OR DELETE ON program FOR EACH ROW EXECUTE FUNCTION log_audit_event();

-- Tabla Course: Banco de asignaturas disponibles
CREATE TABLE course (
    course_id SERIAL PRIMARY KEY, -- ID de curso
    program_id INT REFERENCES program(program_id) NOT NULL, -- Programa propietario del curso
    code VARCHAR(20) UNIQUE NOT NULL, -- Código de la asignatura (Ej: MAT101)
    name VARCHAR(100) NOT NULL, -- Nombre de la asignatura
    credits INT NOT NULL, -- Número de créditos académicos
    level INT NOT NULL, -- Nivel o semestre sugerido
    attempt INT NOT NULL DEFAULT 1, -- Número de intentos permitidos por defecto
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- Auditoría
);
CREATE TRIGGER update_course_modtime BEFORE UPDATE ON course FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER audit_course_changes AFTER INSERT OR UPDATE OR DELETE ON course FOR EACH ROW EXECUTE FUNCTION log_audit_event();

-- Tabla Semaphore: Planes de estudio (Mallas curriculares)
CREATE TABLE semaphore (
    semaphore_id SERIAL PRIMARY KEY, -- ID del semáforo
    program_id INT REFERENCES program(program_id) NOT NULL, -- Programa al que pertenece
    start_period VARCHAR(20) NOT NULL, -- Periodo de inicio de vigencia (Ej: '2020-1')
    end_period VARCHAR(20), -- Periodo fin de vigencia (NULL si es indefinido)
    total_credits INT NOT NULL, -- Total de créditos del plan
    status semaphore_status_enum NOT NULL DEFAULT 'BORRADOR', -- Estado del plan
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- Fecha de creación
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- Auditoría
);
CREATE TRIGGER update_semaphore_modtime BEFORE UPDATE ON semaphore FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER audit_semaphore_changes AFTER INSERT OR UPDATE OR DELETE ON semaphore FOR EACH ROW EXECUTE FUNCTION log_audit_event();

-- Tabla Semaphore Course: Relación de cursos dentro de un plan de estudios
CREATE TABLE semaphore_course (
    semaphore_course_id SERIAL PRIMARY KEY, -- ID de la relación
    semaphore_id INT REFERENCES semaphore(semaphore_id) NOT NULL, -- Plan de estudios
    course_id INT REFERENCES course(course_id) NOT NULL, -- Asignatura
    semester INT NOT NULL, -- Semestre en el que se ubica en la malla
    is_mandatory BOOLEAN DEFAULT TRUE, -- Si es obligatoria o electiva
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- Auditoría
    UNIQUE(semaphore_id, course_id) -- Evita duplicar cursos en el mismo plan
);
CREATE TRIGGER update_semaphore_course_modtime BEFORE UPDATE ON semaphore_course FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER audit_semaphore_course_changes AFTER INSERT OR UPDATE OR DELETE ON semaphore_course FOR EACH ROW EXECUTE FUNCTION log_audit_event();

-- ==============================================================================================
-- 6. GESTIÓN ACADÉMICA Y VIDA ESTUDIANTIL
-- Inscripciones, planeación, periodos, horarios y calificaciones.
-- ==============================================================================================

-- Tabla Student Program: Vinculación de un estudiante a un programa
CREATE TABLE student_program (
    student_program_id SERIAL PRIMARY KEY, -- ID de la vinculación
    student_id INT REFERENCES student(student_id) NOT NULL, -- Estudiante
    program_id INT REFERENCES program(program_id) NOT NULL, -- Programa cursado
    status student_status_enum NOT NULL, -- Estado en el programa (Activo, Graduado, etc.)
    current_semester INT NOT NULL, -- Semestre actual del estudiante
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- Auditoría
    UNIQUE(student_id, program_id) -- Un estudiante no puede estar dos veces activo en el mismo programa
);
CREATE TRIGGER update_student_program_modtime BEFORE UPDATE ON student_program FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER audit_student_program_changes AFTER INSERT OR UPDATE OR DELETE ON student_program FOR EACH ROW EXECUTE FUNCTION log_audit_event();

-- Tabla Student Semaphore: Planeación personalizada del estudiante
CREATE TABLE student_semaphore (
    student_semaphore_id SERIAL PRIMARY KEY, -- ID del plan personalizado
    student_id INT REFERENCES student(student_id) NOT NULL, -- Estudiante dueño del plan
    name VARCHAR(100) DEFAULT 'Mi Plan', -- Nombre del plan
    is_primary BOOLEAN DEFAULT FALSE, -- Indica si es el plan principal
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- Fecha de creación
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- Auditoría
);
CREATE TRIGGER update_student_semaphore_modtime BEFORE UPDATE ON student_semaphore FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER audit_student_semaphore_changes AFTER INSERT OR UPDATE OR DELETE ON student_semaphore FOR EACH ROW EXECUTE FUNCTION log_audit_event();

-- Tabla Student Semaphore Course: Cursos planeados por el estudiante
CREATE TABLE student_semaphore_course (
    student_semaphore_course_id SERIAL PRIMARY KEY, -- ID de la planificación del curso
    student_semaphore_id INT REFERENCES student_semaphore(student_semaphore_id) NOT NULL, -- Plan personalizado
    course_id INT REFERENCES course(course_id) NOT NULL, -- Curso planeado
    planned_semester INT NOT NULL, -- Semestre proyectado para ver la materia
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- Auditoría
    UNIQUE(student_semaphore_id, course_id) -- No planear la misma materia dos veces
);
CREATE TRIGGER update_student_semaphore_course_modtime BEFORE UPDATE ON student_semaphore_course FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER audit_student_semaphore_course_changes AFTER INSERT OR UPDATE OR DELETE ON student_semaphore_course FOR EACH ROW EXECUTE FUNCTION log_audit_event();

-- Tabla Academic Period: Periodos académicos (Semestres)
CREATE TABLE academic_period (
    academic_period_id SERIAL PRIMARY KEY, -- ID del periodo
    name VARCHAR(20) NOT NULL, -- Nombre del periodo (Ej: '2025-1')
    start_date DATE NOT NULL, -- Fecha de inicio
    end_date DATE NOT NULL, -- Fecha de finalización
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- Auditoría
    CONSTRAINT chk_period_dates CHECK (end_date > start_date) -- Validación de fechas
);
CREATE TRIGGER update_academic_period_modtime BEFORE UPDATE ON academic_period FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER audit_academic_period_changes AFTER INSERT OR UPDATE OR DELETE ON academic_period FOR EACH ROW EXECUTE FUNCTION log_audit_event();

-- Tabla Course Group: Oferta de grupos para un periodo
CREATE TABLE course_group (
    course_group_id SERIAL PRIMARY KEY, -- ID del grupo
    course_id INT REFERENCES course(course_id), -- Curso ofertado
    period_id INT REFERENCES academic_period(academic_period_id), -- Periodo académico
    professor_id INT REFERENCES employee(employee_id), -- Profesor asignado
    group_code VARCHAR(10) NOT NULL, -- Código del grupo (Ej: 'G1')
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- Auditoría
);
CREATE TRIGGER update_course_group_modtime BEFORE UPDATE ON course_group FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER audit_course_group_changes AFTER INSERT OR UPDATE OR DELETE ON course_group FOR EACH ROW EXECUTE FUNCTION log_audit_event();

-- Tabla Schedule: Horarios de clase
CREATE TABLE schedule (
    schedule_id SERIAL PRIMARY KEY, -- ID del horario
    group_id INT REFERENCES course_group(course_group_id), -- Grupo asociado
    day_of_week VARCHAR(15) NOT NULL, -- Día de la semana (Ej: 'MONDAY')
    start_time TIME NOT NULL, -- Hora de inicio
    end_time TIME NOT NULL, -- Hora de fin
    location VARCHAR(50) NOT NULL, -- Salón o ubicación
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- Auditoría
);
CREATE TRIGGER update_schedule_modtime BEFORE UPDATE ON schedule FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER audit_schedule_changes AFTER INSERT OR UPDATE OR DELETE ON schedule FOR EACH ROW EXECUTE FUNCTION log_audit_event();

-- Tabla Student Course: Inscripción de asignaturas (Matrícula)
CREATE TABLE student_course (
    student_course_id SERIAL PRIMARY KEY, -- ID de la inscripción
    student_id INT REFERENCES student(student_id) NOT NULL, -- Estudiante
    group_id INT REFERENCES course_group(course_group_id) NOT NULL, -- Grupo seleccionado
    status course_status_enum NOT NULL, -- Estado (Inscrito, Aprobado, etc.)
    final_grade INT CHECK (final_grade >= 0 AND final_grade <= 50), -- Nota definitiva (0 a 50)
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- Auditoría
);
CREATE TRIGGER update_student_course_modtime BEFORE UPDATE ON student_course FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER audit_student_course_changes AFTER INSERT OR UPDATE OR DELETE ON student_course FOR EACH ROW EXECUTE FUNCTION log_audit_event();

-- Función y Trigger para validar REGLA DE ORO: Un estudiante solo ve una materia una vez por periodo
CREATE OR REPLACE FUNCTION check_student_course_uniqueness()
RETURNS TRIGGER AS $$
DECLARE
    v_course_id INT;
    v_period_id INT;
BEGIN
    -- Obtener curso y periodo del grupo que se intenta inscribir
    SELECT course_id, period_id INTO v_course_id, v_period_id
    FROM course_group WHERE course_group_id = NEW.group_id;

    -- Verificar si ya existe otra inscripción para el mismo curso y periodo (excluyendo el propio registro en updates)
    IF EXISTS (
        SELECT 1 FROM student_course sc
        JOIN course_group cg ON sc.group_id = cg.course_group_id
        WHERE sc.student_id = NEW.student_id
        AND cg.course_id = v_course_id
        AND cg.period_id = v_period_id
        AND sc.student_course_id IS DISTINCT FROM NEW.student_course_id
    ) THEN
        RAISE EXCEPTION 'El estudiante ya tiene inscrita esta materia en este periodo académico.';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER validate_student_course_uniqueness
BEFORE INSERT OR UPDATE ON student_course
FOR EACH ROW EXECUTE FUNCTION check_student_course_uniqueness();

-- Tabla Monitoria: Gestión de monitores académicos
CREATE TABLE course_monitor (
    course_monitor_id SERIAL PRIMARY KEY,
    student_id INT REFERENCES student(student_id) NOT NULL,
    course_group_id INT REFERENCES course_group(course_group_id) NOT NULL, -- Grupo al que apoya
    approved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- Fecha de aprobación de la monitoría
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- Auditoría
);
CREATE TRIGGER update_course_monitor_modtime BEFORE UPDATE ON course_monitor FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER audit_monitor_changes AFTER INSERT OR UPDATE OR DELETE ON course_monitor FOR EACH ROW EXECUTE FUNCTION log_audit_event();

-- Función y Trigger para validar: Un estudiante solo puede ser monitor una vez por periodo
CREATE OR REPLACE FUNCTION check_monitor_uniqueness()
RETURNS TRIGGER AS $$
DECLARE
    v_period_id INT;
BEGIN
    -- Obtener periodo del grupo
    SELECT period_id INTO v_period_id
    FROM course_group WHERE course_group_id = NEW.course_group_id;

    -- Verificar si ya es monitor en este periodo
    IF EXISTS (
        SELECT 1 FROM course_monitor cm
        JOIN course_group cg ON cm.course_group_id = cg.course_group_id
        WHERE cm.student_id = NEW.student_id
        AND cg.period_id = v_period_id
        AND cm.course_monitor_id IS DISTINCT FROM NEW.course_monitor_id
    ) THEN
        RAISE EXCEPTION 'El estudiante ya es monitor en este periodo académico.';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER validate_monitor_uniqueness
BEFORE INSERT OR UPDATE ON course_monitor
FOR EACH ROW EXECUTE FUNCTION check_monitor_uniqueness();



/*
-- ==============================================================================================
-- 7. MÓDULOS ADICIONALES (FUTURA IMPLEMENTACIÓN)
-- Esta sección contiene módulos para Biblioteca, Eventos, Servicios y Chat.
-- Se mantiene comentada hasta su integración en fases posteriores del proyecto.
-- ==============================================================================================

-- Tabla Grade: Notas parciales (Cortes)
-- Se manejan típicamente dos cortes: Corte 1 (40%) y Corte 2 (60%)
CREATE TABLE grade (
    grade_id SERIAL PRIMARY KEY, -- ID de la nota
    student_course_id INT REFERENCES student_course(student_course_id), -- Curso inscrito asociado
    name VARCHAR(50) NOT NULL, -- Nombre de la nota (Ej: 'Corte 1', 'Corte 2')
    weight DECIMAL(5, 2) NOT NULL, -- Peso porcentual de la nota (Ej: 40.00, 60.00)
    value INT NOT NULL CHECK (value >= 0 AND value <= 50), -- Valor obtenido (0 a 50, sin decimales)
    absences_percentage INT NOT NULL DEFAULT 0 CHECK (absences_percentage >= 0 AND absences_percentage <= 100), -- Porcentaje de fallas acumuladas en este corte
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- Fecha de registro
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- Auditoría
);
CREATE TRIGGER update_grade_modtime BEFORE UPDATE ON grade FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER audit_grade_changes AFTER INSERT OR UPDATE OR DELETE ON grade FOR EACH ROW EXECUTE FUNCTION log_audit_event();

-- Biblioteca
CREATE TABLE library_book (
    library_book_id SERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    author VARCHAR(100) NOT NULL,
    isbn VARCHAR(20) UNIQUE,
    available_copies INT DEFAULT 1
);

CREATE TABLE library_loan (
    library_loan_id SERIAL PRIMARY KEY,
    user_id INT REFERENCES person(person_id), -- Cualquier persona puede prestar (si tiene permiso)
    book_id INT REFERENCES library_book(library_book_id),
    loan_date DATE DEFAULT CURRENT_DATE,
    due_date DATE NOT NULL,
    return_date DATE,
    status VARCHAR(20) DEFAULT 'ACTIVE'
);

CREATE TABLE library_reservation (
    library_reservation_id SERIAL PRIMARY KEY,
    user_id INT REFERENCES person(person_id),
    book_id INT REFERENCES library_book(library_book_id),
    reservation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) DEFAULT 'PENDING'
);

-- Eventos
CREATE TABLE event (
    event_id SERIAL PRIMARY KEY,
    title VARCHAR(150) NOT NULL,
    description TEXT,
    date_time TIMESTAMP NOT NULL,
    location VARCHAR(100),
    category event_category_enum NOT NULL, -- Usando ENUM
    organizer_id INT REFERENCES person(person_id),
    image_url VARCHAR(255)
);

CREATE TABLE event_registration (
    event_registration_id SERIAL PRIMARY KEY,
    event_id INT REFERENCES event(event_id),
    user_id INT REFERENCES person(person_id),
    registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(event_id, user_id)
);

-- Servicios Universitarios
CREATE TABLE appointment (
    appointment_id SERIAL PRIMARY KEY,
    student_id INT REFERENCES student(student_id), -- Solo estudiantes agendan citas? O Members?
    staff_id INT REFERENCES employee(employee_id), -- Atendido por un empleado
    service_type service_type_enum NOT NULL, -- Usando ENUM
    date_time TIMESTAMP NOT NULL,
    status VARCHAR(20) DEFAULT 'SCHEDULED',
    notes TEXT
);

CREATE TABLE parking_reservation (
    parking_reservation_id SERIAL PRIMARY KEY,
    user_id INT REFERENCES person(person_id), -- Cualquier persona con carro
    reservation_date DATE NOT NULL,
    plate_number VARCHAR(10) NOT NULL,
    status VARCHAR(20) DEFAULT 'CONFIRMED',
    payment_status VARCHAR(20) DEFAULT 'PENDING'
);

-- Comunicación (Chat)
CREATE TABLE chat_conversation (
    chat_conversation_id SERIAL PRIMARY KEY,
    is_group BOOLEAN DEFAULT FALSE,
    name VARCHAR(100)
);

CREATE TABLE chat_participant (
    conversation_id INT REFERENCES chat_conversation(chat_conversation_id),
    user_id INT REFERENCES person(person_id),
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (conversation_id, user_id)
);

CREATE TABLE chat_message (
    chat_message_id SERIAL PRIMARY KEY,
    conversation_id INT REFERENCES chat_conversation(chat_conversation_id),
    sender_id INT REFERENCES person(person_id),
    content TEXT,
    attachment_url VARCHAR(255),
    sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Notificaciones
CREATE TABLE notification (
    notification_id SERIAL PRIMARY KEY,
    user_id INT REFERENCES person(person_id),
    title VARCHAR(100) NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    type VARCHAR(50)
);
*/
