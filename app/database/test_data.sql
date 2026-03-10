-- Script de Datos de Prueba para KApp
-- Contraseña para todos los usuarios: 123123
-- Hash BCrypt: $2b$12$8g50C8rO7r/hbTIXv90JuefGBWsv/WJjERcIgXRfs3S/zAv/Ok70e

BEGIN;

-- ==============================================================================================
-- 1. DATOS BÁSICOS (Facultad, Programa, Periodo)
-- ==============================================================================================

INSERT INTO faculty (name) VALUES ('Facultad de Matemáticas e Ingenierías');

INSERT INTO program (faculty_id, name, level) 
VALUES ((SELECT faculty_id FROM faculty WHERE name = 'Facultad de Matemáticas e Ingenierías'), 'Ingeniería de Sistemas', 'PREGRADO');

INSERT INTO academic_period (name, start_date, end_date) 
VALUES ('2025-1', '2025-02-01', '2025-06-01');

-- ==============================================================================================
-- 2. USUARIOS (Admin, Profesores, Estudiantes)
-- ==============================================================================================

DO $$
DECLARE
    v_person_id INT;
    v_member_id INT;
    v_program_id INT;
    i INT;
BEGIN
    -- Obtener ID del programa
    SELECT program_id INTO v_program_id FROM program WHERE name = 'Ingeniería de Sistemas';

    -- ------------------------------------------------------------------------------------------
    -- 2.1. ADMIN (1 Usuario)
    -- ------------------------------------------------------------------------------------------
    INSERT INTO person (identification_number, identification_type, first_name, last_name, phone, home_address, birth_date)
    VALUES (1000000, 'CC', 'Admin', 'Sistema', '3000000000', 'Calle Falsa 123', '1990-01-01')
    RETURNING person_id INTO v_person_id;

    INSERT INTO member (person_id, university_code, university_email, password_hash)
    VALUES (v_person_id, 'ADMIN001', 'admin@konradlorenz.edu.co', '$2b$12$8g50C8rO7r/hbTIXv90JuefGBWsv/WJjERcIgXRfs3S/zAv/Ok70e')
    RETURNING member_id INTO v_member_id;

    INSERT INTO employee (member_id, employee_code, employee_type, employee_role, contract_type, hire_date, salary, status)
    VALUES (v_member_id, 'EMP001', 'ADMINISTRATIVO', 'ADMINISTRADOR_SISTEMA', 'INDEFINIDO', '2020-01-01', 5000000, 'ACTIVO');

    -- ------------------------------------------------------------------------------------------
    -- 2.2. PROFESORES (5 Usuarios)
    -- ------------------------------------------------------------------------------------------
    FOR i IN 1..5 LOOP
        INSERT INTO person (identification_number, identification_type, first_name, last_name, phone, home_address, birth_date)
        VALUES (2000000 + i, 'CC', 'Profesor', 'Prueba ' || i, '3001112233', 'Calle Profe ' || i, '1985-05-05')
        RETURNING person_id INTO v_person_id;

        INSERT INTO member (person_id, university_code, university_email, password_hash)
        VALUES (v_person_id, 'PROF00' || i, 'profesor' || i || '@konradlorenz.edu.co', '$2b$12$8g50C8rO7r/hbTIXv90JuefGBWsv/WJjERcIgXRfs3S/zAv/Ok70e')
        RETURNING member_id INTO v_member_id;

        INSERT INTO employee (member_id, employee_code, employee_type, employee_role, contract_type, hire_date, salary, status)
        VALUES (v_member_id, 'EMP_P0' || i, 'ACADEMICO', 'PROFESOR_PLANTA', 'TIEMPO_COMPLETO', '2021-01-01', 4000000, 'ACTIVO');
    END LOOP;

    -- ------------------------------------------------------------------------------------------
    -- 2.3. ESTUDIANTES (100 Usuarios)
    -- ------------------------------------------------------------------------------------------
    FOR i IN 1..100 LOOP
        INSERT INTO person (identification_number, identification_type, first_name, last_name, phone, home_address, birth_date)
        VALUES (3000000 + i, 'TI', 'Estudiante', 'Numero ' || i, '3100000000', 'Carrera Estudiante ' || i, '2005-01-01')
        RETURNING person_id INTO v_person_id;

        INSERT INTO member (person_id, university_code, university_email, password_hash)
        VALUES (v_person_id, 'EST' || LPAD(i::text, 3, '0'), 'estudiante' || i || '@konradlorenz.edu.co', '$2b$12$8g50C8rO7r/hbTIXv90JuefGBWsv/WJjERcIgXRfs3S/zAv/Ok70e')
        RETURNING member_id INTO v_member_id;

        INSERT INTO student (member_id, student_code)
        VALUES (v_member_id, 'COD_EST_' || LPAD(i::text, 3, '0'));
    END LOOP;
END $$;

-- ==============================================================================================
-- 3. CURSOS (20 Cursos de Ingeniería de Sistemas)
-- ==============================================================================================
INSERT INTO course (program_id, code, name, credits, level) VALUES
((SELECT program_id FROM program WHERE name = 'Ingeniería de Sistemas'), 'ING101', 'Introducción a la Ingeniería', 2, 1),
((SELECT program_id FROM program WHERE name = 'Ingeniería de Sistemas'), 'MAT101', 'Matemáticas Básicas', 3, 1),
((SELECT program_id FROM program WHERE name = 'Ingeniería de Sistemas'), 'LOG101', 'Lógica de Programación', 3, 1),
((SELECT program_id FROM program WHERE name = 'Ingeniería de Sistemas'), 'CAL101', 'Cálculo Diferencial', 3, 2),
((SELECT program_id FROM program WHERE name = 'Ingeniería de Sistemas'), 'POO101', 'Programación Orientada a Objetos', 3, 2),
((SELECT program_id FROM program WHERE name = 'Ingeniería de Sistemas'), 'ALG101', 'Álgebra Lineal', 3, 2),
((SELECT program_id FROM program WHERE name = 'Ingeniería de Sistemas'), 'FIS101', 'Física Mecánica', 3, 3),
((SELECT program_id FROM program WHERE name = 'Ingeniería de Sistemas'), 'EST101', 'Estructuras de Datos', 3, 3),
((SELECT program_id FROM program WHERE name = 'Ingeniería de Sistemas'), 'CAL102', 'Cálculo Integral', 3, 3),
((SELECT program_id FROM program WHERE name = 'Ingeniería de Sistemas'), 'BD101',  'Bases de Datos', 3, 4),
((SELECT program_id FROM program WHERE name = 'Ingeniería de Sistemas'), 'SOFT1',  'Ingeniería de Software I', 3, 4),
((SELECT program_id FROM program WHERE name = 'Ingeniería de Sistemas'), 'RED101', 'Redes de Computadores', 3, 5),
((SELECT program_id FROM program WHERE name = 'Ingeniería de Sistemas'), 'SO101',  'Sistemas Operativos', 3, 5),
((SELECT program_id FROM program WHERE name = 'Ingeniería de Sistemas'), 'PROB1',  'Probabilidad y Estadística', 3, 5),
((SELECT program_id FROM program WHERE name = 'Ingeniería de Sistemas'), 'SOFT2',  'Ingeniería de Software II', 3, 6),
((SELECT program_id FROM program WHERE name = 'Ingeniería de Sistemas'), 'ARQ101', 'Arquitectura de Computadores', 3, 6),
((SELECT program_id FROM program WHERE name = 'Ingeniería de Sistemas'), 'IA101',  'Inteligencia Artificial', 3, 7),
((SELECT program_id FROM program WHERE name = 'Ingeniería de Sistemas'), 'WEB101', 'Desarrollo Web', 3, 7),
((SELECT program_id FROM program WHERE name = 'Ingeniería de Sistemas'), 'GER101', 'Gerencia de Proyectos', 3, 8),
((SELECT program_id FROM program WHERE name = 'Ingeniería de Sistemas'), 'ETI101', 'Ética Profesional', 2, 9);

-- ==============================================================================================
-- 4. GRUPOS Y ASIGNACIÓN DE PROFESORES
-- ==============================================================================================

DO $$
DECLARE
    v_course_record RECORD;
    v_period_id INT;
    v_prof_id INT;
    v_group_id INT;
    v_prof_counter INT := 1;
    v_groups_assigned_to_prof INT := 0;
    i INT;
BEGIN
    SELECT academic_period_id INTO v_period_id FROM academic_period WHERE name = '2025-1';

    -- Recorrer todos los cursos (20 cursos)
    FOR v_course_record IN SELECT course_id, code FROM course LOOP
        
        -- Crear 3 grupos por curso
        FOR i IN 1..3 LOOP
            
            -- Determinar profesor: Solo asignamos a los primeros 20 grupos (5 profes * 4 cursos)
            -- O asignamos cíclicamente para cubrir todo si se prefiere, pero el requerimiento dice "cada profe 4 cursos".
            -- Vamos a asignar estrictamente 4 grupos a cada uno de los 5 profesores.
            -- Total asignaciones = 20. Los grupos restantes quedarán sin profesor (NULL) o asignamos repetidos.
            -- Para que el dashboard se vea lleno, asignaremos cíclicamente a los 5 profesores en TODOS los grupos.
            -- Así cada profe tendrá 60 / 5 = 12 cursos.
            -- PERO el usuario dijo "cada profe debe tener 4 cursos".
            -- Ok, asignaré solo a los primeros 20 grupos un profesor, el resto NULL.
            
            v_prof_id := NULL;
            
            -- Lógica para asignar 4 cursos a cada uno de los 5 profesores
            -- Total asignaciones necesarias: 20.
            -- Vamos a usar una variable global para contar asignaciones totales o por profe.
            
            -- Simplemente asignamos a los primeros 20 grupos creados en el orden del loop.
            -- Como son 20 cursos * 3 grupos = 60 iteraciones totales.
            -- Asignaremos profesor si (row_number global <= 20).
            
            -- Mejor enfoque: Asignar profesor ID X a los grupos específicos para garantizar variedad.
            
        END LOOP;
    END LOOP;
END $$;

-- Reiniciamos la lógica de grupos para hacerlo más simple y directo
DELETE FROM course_group; -- Limpiar por si acaso (aunque es script nuevo)

DO $$
DECLARE
    v_period_id INT;
    v_course_id INT;
    v_prof_ids INT[];
    v_current_prof_idx INT := 1;
    v_groups_per_prof INT := 0;
    v_group_id INT;
    i INT;
    j INT;
BEGIN
    SELECT academic_period_id INTO v_period_id FROM academic_period WHERE name = '2025-1';
    
    -- Obtener IDs de los 5 profesores en un array
    SELECT ARRAY(SELECT employee_id FROM employee WHERE employee_role = 'PROFESOR_PLANTA' ORDER BY employee_id) INTO v_prof_ids;

    -- Crear grupos para los 20 cursos (3 grupos c/u) -> Total 60 grupos
    -- Asignar profesor solo hasta que cada uno tenga 4.
    
    FOR i IN 1..20 LOOP -- Iterar cursos por orden de inserción (IDs 1 a 20 aprox)
        -- Obtener ID del curso actual (asumiendo serial)
        -- Lo buscamos por offset para ser seguros
        SELECT course_id INTO v_course_id FROM course ORDER BY course_id LIMIT 1 OFFSET (i-1);

        FOR j IN 1..3 LOOP -- 3 Grupos por curso
            
            DECLARE 
                v_assigned_prof INT := NULL;
            BEGIN
                -- Si todavía hay profesores que necesitan cursos (5 profes * 4 cursos = 20 asignaciones)
                -- Y estamos en los primeros 20 grupos generados...
                -- Espera, si asignamos a los primeros 20 grupos, serán solo de los primeros ~7 cursos.
                -- Los profes solo verían cursos de 1er y 2do semestre.
                -- Mejor: Asignar profesor al Grupo 1 de cada curso, hasta agotar.
                
                IF j = 1 AND v_current_prof_idx <= 5 THEN
                    v_assigned_prof := v_prof_ids[v_current_prof_idx];
                    v_groups_per_prof := v_groups_per_prof + 1;
                    
                    IF v_groups_per_prof >= 4 THEN
                        v_current_prof_idx := v_current_prof_idx + 1;
                        v_groups_per_prof := 0;
                    END IF;
                END IF;

                INSERT INTO course_group (course_id, period_id, professor_id, group_code)
                VALUES (v_course_id, v_period_id, v_assigned_prof, 'G' || j);
            END;
            
        END LOOP;
    END LOOP;
    
    -- NOTA: Con la lógica anterior, asignamos 4 cursos a cada uno de los 5 profesores.
    -- Cubrimos 20 asignaciones. Como asignamos al "Grupo 1" de cada curso, cubrimos los cursos 1 al 20.
    -- ¡Perfecto! Cada curso tendrá al menos un grupo con profesor (el G1).
END $$;

-- ==============================================================================================
-- 5. INSCRIPCIONES (Matricular estudiantes)
-- ==============================================================================================

DO $$
DECLARE
    v_student_record RECORD;
    v_group_id INT;
    i INT;
BEGIN
    -- Recorrer cada estudiante (100)
    FOR v_student_record IN SELECT student_id FROM student LOOP
        
        -- Inscribir en 5 grupos aleatorios
        FOR i IN 1..5 LOOP
            -- Seleccionar un grupo aleatorio
            SELECT course_group_id INTO v_group_id FROM course_group ORDER BY RANDOM() LIMIT 1;
            
            -- Intentar insertar (ignorar duplicados de materia/periodo gracias al trigger o unique constraint)
            BEGIN
                INSERT INTO student_course (student_id, group_id, status)
                VALUES (v_student_record.student_id, v_group_id, 'INSCRITO');
            EXCEPTION WHEN OTHERS THEN
                -- Ignorar error si ya tiene la materia inscrita o choque de horario (si hubiera validación)
                -- Simplemente continuamos
            END;
        END LOOP;
    END LOOP;
END $$;

COMMIT;
