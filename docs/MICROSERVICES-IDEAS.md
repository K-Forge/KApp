# 💡 Ideas de Microservicios — KApp

> **"Las mejores arquitecturas nacen de buenas ideas compartidas."**

Bienvenido al banco de ideas de microservicios de KApp. Este documento existe para que todo el equipo pueda proponer, documentar y discutir ideas de nuevos microservicios de forma estructurada, sin la presión de desarrollarlas todas de inmediato.

La meta es simple: **dejar una base sólida de ideas bien pensadas** que nos permita priorizar, planificar y construir el futuro de KApp con claridad. No importa si tu idea es pequeña o ambiciosa — si resuelve un problema real para la comunidad universitaria, merece estar aquí.

---

## 📋 Instrucciones de Uso

¿Tienes una idea? ¡Genial! Sigue estos pasos para agregarla:

1. **Agrega una nueva fila a la tabla** de la sección "Ideas Registradas", completando todos los campos.
2. **Ubica tu idea en la posición correcta** según la matriz Impacto × Dificultad: primero las de **alto impacto y baja dificultad** (Quick wins), luego las de **alto impacto y alta dificultad** (Proyectos estratégicos), después las de **bajo/medio impacto y baja dificultad** (Relleno), y al final las de **bajo/medio impacto y alta dificultad** (Evitar o posponer).
3. **Pon tu nombre o usuario de GitHub** para que el equipo sepa a quién contactar si quiere discutir la propuesta.
4. **Haz commit y abre un PR** con el título: `idea: Nombre del Microservicio`.

> **Tip:** Si no estás seguro sobre algún campo (como el nivel de dificultad), haz tu mejor estimación. La lluvia de ideas es para explorar, no para tener certezas absolutas.

> [!IMPORTANT]
> **Regla de ordenamiento:** Las ideas **siempre** deben estar ordenadas por **Impacto descendente** (Alto → Medio → Bajo) y como desempate por **Dificultad ascendente** (Bajo → Medio → Alto). Al agregar una nueva idea, insértala en la categoría correcta según la matriz Impacto × Dificultad, nunca al final del documento sin respetar el orden.

---

## 📊 Matriz Impacto × Dificultad

A continuación encontrarás las ideas propuestas por el equipo. Lee las existentes antes de agregar la tuya para evitar duplicados y, si ves algo que te gusta, ¡no dudes en comentarlo en el PR correspondiente!

|                        | Baja Dificultad                                | Media/Alta Dificultad                           |
| ---------------------- | ---------------------------------------------- | ----------------------------------------------- |
| **Alto Impacto**       | 🏆 **Quick wins** <br> _Hacer primero_         | 🎯 **Proyectos estratégicos** <br> _Planificar_ |
| **Medio/Bajo Impacto** | 🧩 **Relleno** <br> _Hacer cuando haya tiempo_ | ⏸️ **Evitar o posponer** <br> _Baja prioridad_  |

---

## 🚀 Ideas Registradas

<!-- ⚠️ NOTA PARA AGENTES DE IA Y CONTRIBUYENTES:
  Las filas de esta tabla SIEMPRE deben estar ordenadas por la matriz Impacto × Dificultad:
    1. 🏆 Quick wins — Alto Impacto + Baja Dificultad (primero)
    2. 🎯 Proyectos estratégicos — Alto Impacto + Media/Alta Dificultad
    3. 🧩 Relleno — Bajo/Medio Impacto + Baja Dificultad
    4. ⏸️ Evitar o posponer — Bajo/Medio Impacto + Media/Alta Dificultad (al final)
  Dentro de la misma categoría, el orden es cronológico (por fecha).
  Cuando se agregue una nueva idea, debe insertarse al final del grupo de su categoría.
  NUNCA agregar una fila al final sin respetar este orden.
-->

| Microservicio                     | Slug/Identificador URL        | Descripción                                                                                                                                                                                  | Público                                  | Impacto | Dificultad | Propuesto por               | Fecha    |
| --------------------------------- | ----------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------- | ------- | ---------- | --------------------------- | -------- |
| Reporte de Espacios Dañados       | report-service                | Denunciar espacios o recursos dañados (tableros, sillas, baños, proyectores) para que mantenimiento priorice y dé seguimiento a las reparaciones.                                            | Estudiantes, profesores, administrativos | Alto    | Bajo       | KApp Team                   | 26/02/25 |
| Mapa Interactivo                  | map-service                   | Buscar salones, aulas, edificios y espacios del campus mediante un mapa interactivo con orientación visual.                                                                                  | Estudiantes, profesores, invitados       | Alto    | Medio      | KApp Team                   | 26/02/25 |
| Citas de Bienestar y Coordinación | bienestar-appointment-service | Agendar citas con Bienestar Universitario, coordinación académica y psicología. Cascarón extensible para que cada dependencia configure su disponibilidad y flujo de agendamiento.           | Estudiantes, profesores, administrativos | Alto    | Medio      | KApp Team                   | 26/02/25 |
| Menú y Pedidos del Restaurante    | restaurant-service            | Consultar el menú del día del restaurante universitario y realizar pedidos o compras de comida desde la app.                                                                                 | Estudiantes, profesores, administrativos | Alto    | Medio      | KApp Team                   | 26/02/25 |
| Semáforo Estudiantil              | student-semaphore-service     | Consultar el semáforo académico de cada estudiante (promedio, créditos, alertas) e identificar estudiantes en riesgo para acompañamiento oportuno.                                           | Estudiantes, administradores             | Alto    | Medio      | KApp Team                   | 26/02/25 |
| Biblioteca                        | library-service               | Consultar catálogo, ver historial de préstamos, renovar préstamos y reservar libros o inscribirse en lista de espera.                                                                        | Estudiantes, profesores                  | Alto    | Medio      | KApp Team                   | 26/02/25 |
| Eventos y Actividades             | event-service                 | Centralizar eventos universitarios (culturales, deportivos, académicos), filtrar por categoría, inscribirse y agregar al calendario personal.                                                | Todos                                    | Alto    | Medio      | KApp Team                   | 26/02/25 |
| Notificaciones                    | notification-service          | Servicio transversal de notificaciones push en tiempo real. Centraliza alertas de todos los microservicios (horarios, mensajes, eventos, citas) con configuración personalizada por usuario. | Todos                                    | Alto    | Medio      | KApp Team                   | 26/02/25 |
| Gestión de Clubes                 | club-service                  | Crear, consultar y gestionar clubes estudiantiles: descubrir clubes, inscribirse, administrar miembros, publicar actividades y coordinar reuniones.                                          | Estudiantes, administradores             | Alto    | Medio      | Brian Steven Vargas Clavijo | 10/03/26 |
| Trámites Académicos | academic-procedures-service | Registrar y dar seguimiento en tiempo real a trámites académicos (homologaciones, cancelaciones, certificados, revisiones de nota, entre otros), centralizando solicitudes administrativas y mejorando la transparencia del proceso. | Estudiantes, administrativos | Alto | Medio | @DIEGO-ALI | 13/03/26 |
| Chat por Materia                  | chat-service                  | Mensajería organizada por materia: profesor ↔ grupo, estudiante ↔ estudiante, facultad ↔ miembros académicos. Reemplaza canales informales (WhatsApp, correo).                               | Estudiantes, profesores, facultad        | Alto    | Alto       | KApp Team                   | 26/02/25 |
| Objetos Perdidos                  | lost-and-found-service        | Registrar y buscar objetos perdidos en el campus. Quien encuentra un objeto lo reporta y quien lo perdió busca coincidencias para coordinar la devolución.                                   | Estudiantes, profesores, administrativos | Medio   | Bajo       | Brian Steven Vargas Clavijo | 10/03/26 |
| Salones Disponibles | available-classrooms | Consulta en tiempo real de aulas libres y su disponibilidad por piso para evitar interrupciones por clases. | Estudiantes | Medio | Bajo | @DIEGO-ALI | 10/03/26 |
| Reserva de espacios edificio Bienestar | recreation-bookings | Consulta de la dispoibilidad de los espacios de bienestar (mesas de Ping Pong, sala de danzas, mesas de billar, etc), agendamiento de espacios con cupos limitados y en los horarios especificos de atención. | Estudiantes | Medio | Bajo | Zavithar_17 | 12/03/26 |
| Carné Digital                     | carnet-service                | Carné universitario digital con código QR vinculado a la identidad del estudiante o profesor para identificación en accesos y servicios.                                                     | Estudiantes, profesores                  | Bajo    | Medio      | KApp Team                   | 26/02/25 |
| Parqueadero                       | parking-service               | Reservar espacios de parqueo, consultar disponibilidad en tiempo real y realizar pagos por uso del parqueadero universitario.                                                                | Estudiantes, profesores, administrativos | Medio   | Medio      | KApp Team                   | 26/02/25 |
| Gestion de Gimnasio | gym-management | Inscripción al servicio de gimnasio de la universidad, gestión de rutinas diseñadas por los profesores, sección de recomendaciones alimenticias para cumplimiento de objetivos. | Estudiantes y entrenadores | Medio | Medio | Zavithar_17 | 12/03/26 |


> **KApp Team:** Brian Steven Vargas Clavijo, Julián David Avila Cortes, Santiago Rocha Ramirez, Diego Ali Lares Rondon.

---

<!--
  ┌──────────────────────────────────────────────────────────────────┐
  │  ¡Agrega tu idea como nueva fila a la tabla, respetando el     │
  │  orden de la matriz Impacto × Dificultad!                      │
  │  🏆 Quick wins → 🎯 Estratégicos → 🧩 Relleno → ⏸️ Posponer   │
  └──────────────────────────────────────────────────────────────────┘
-->
