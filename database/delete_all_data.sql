-- ==============================================================================================
-- SCRIPT DE ELIMINACIÓN DE DATOS - PostgreSQL
-- Proyecto: KApp
-- Descripción: Elimina todos los datos de las tablas para permitir una reinserción limpia.
--              Utiliza TRUNCATE ... CASCADE para limpiar tablas dependientes automáticamente.
-- ==============================================================================================

-- Desactivar restricciones de claves foráneas temporalmente (opcional, pero TRUNCATE CASCADE lo maneja)
-- SET session_replication_role = 'replica';

-- Limpiar tablas usando TRUNCATE con CASCADE.
-- Se incluyen todas las tablas definidas en init.sql.
-- RESTART IDENTITY reinicia los contadores de las columnas SERIAL.

TRUNCATE TABLE 
    -- Tablas de Auditoría
    audit_log,
    
    -- Tablas de Tareas y Entregas (Dashboard)
    submission,
    assignment,

    -- Tablas de Gestión Académica y Vida Estudiantil
    course_monitor,
    student_course,
    schedule,
    course_group,
    academic_period,
    student_semaphore_course,
    student_semaphore,
    student_program,

    -- Tablas de Estructura Académica
    semaphore_course,
    semaphore,
    course,
    program,
    faculty,

    -- Tablas de Roles
    employee,
    student,

    -- Tablas de Membresía y Acceso
    guest,
    institutional_card,
    member,

    -- Tabla Base de Identidad
    person

RESTART IDENTITY CASCADE;

-- Reactivar restricciones (si se desactivaron)
-- SET session_replication_role = 'origin';

-- Mensaje de confirmación
DO $$ 
BEGIN 
    RAISE NOTICE 'Todos los datos han sido eliminados correctamente y los contadores reiniciados.'; 
END $$;
