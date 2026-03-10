# 💡 Ideas de Microservicios — KApp

> **"Las mejores arquitecturas nacen de buenas ideas compartidas."**

Bienvenido al banco de ideas de microservicios de KApp. Este documento existe para que todo el equipo pueda proponer, documentar y discutir ideas de nuevos microservicios de forma estructurada, sin la presión de desarrollarlas todas de inmediato.

La meta es simple: **dejar una base sólida de ideas bien pensadas** que nos permita priorizar, planificar y construir el futuro de KApp con claridad. No importa si tu idea es pequeña o ambiciosa — si resuelve un problema real para la comunidad universitaria, merece estar aquí.

---

## 📋 Instrucciones de Uso

¿Tienes una idea? ¡Genial! Sigue estos pasos para agregarla:

1. **Agrega una nueva fila a la tabla** de la sección "Ideas Registradas", completando todos los campos.
2. **Ubica tu idea en la posición correcta** según su nivel de dificultad (primero Bajo, luego Medio, luego Alto).
3. **Pon tu nombre o usuario de GitHub** para que el equipo sepa a quién contactar si quiere discutir la propuesta.
4. **Haz commit y abre un PR** con el título: `idea: Nombre del Microservicio`.

> **Tip:** Si no estás seguro sobre algún campo (como el nivel de dificultad), haz tu mejor estimación. La lluvia de ideas es para explorar, no para tener certezas absolutas.

> [!IMPORTANT]
> **Regla de ordenamiento:** Las ideas **siempre** deben estar ordenadas por nivel de dificultad: **Bajo → Medio → Alto**. Al agregar una nueva idea, insértala al final del grupo de su dificultad correspondiente, nunca al final del documento sin importar el orden.

---

## 🚀 Ideas Registradas

A continuación encontrarás las ideas propuestas por el equipo. Lee las existentes antes de agregar la tuya para evitar duplicados y, si ves algo que te gusta, ¡no dudes en comentarlo en el PR correspondiente!

<!-- ⚠️ NOTA PARA AGENTES DE IA Y CONTRIBUYENTES:
  Las filas de esta tabla SIEMPRE deben estar ordenadas por Nivel de Dificultad:
    1. Bajo (primero)
    2. Medio (después)
    3. Alto (al final)
  Dentro del mismo nivel de dificultad, el orden es cronológico (por fecha).
  Cuando se agregue una nueva idea, debe insertarse al final del grupo de su dificultad.
  NUNCA agregar una fila al final sin respetar este orden.
-->

| #   | Microservicio                     | Slug                          | Descripción                                                                                                                                                                                  | Público                                  | Dificultad | Impacto | Propuesto por               | Fecha    |
| --- | --------------------------------- | ----------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------- | ---------- | ------- | --------------------------- | -------- |
| 1   | Reporte de Espacios Dañados       | report-service                | Denunciar espacios o recursos dañados (tableros, sillas, baños, proyectores) para que mantenimiento priorice y dé seguimiento a las reparaciones.                                            | Estudiantes, profesores, administrativos | Bajo       | Alto    | KApp Team                   | 26/02/25 |
| 2   | Objetos Perdidos                  | lost-and-found-service        | Registrar y buscar objetos perdidos en el campus. Quien encuentra un objeto lo reporta y quien lo perdió busca coincidencias para coordinar la devolución.                                   | Estudiantes, profesores, administrativos | Bajo       | Medio   | Brian Steven Vargas Clavijo | 10/03/26 |
| 3   | Mapa Interactivo                  | map-service                   | Buscar salones, aulas, edificios y espacios del campus mediante un mapa interactivo con orientación visual.                                                                                  | Estudiantes, profesores, invitados       | Medio      | Alto    | KApp Team                   | 26/02/25 |
| 4   | Gestión de Clubes                 | club-service                  | Crear, consultar y gestionar clubes estudiantiles: descubrir clubes, inscribirse, administrar miembros, publicar actividades y coordinar reuniones.                                          | Estudiantes, administradores             | Medio      | Alto    | Brian Steven Vargas Clavijo | 10/03/26 |
| 5   | Citas de Bienestar y Coordinación | bienestar-appointment-service | Agendar citas con Bienestar Universitario, coordinación académica y psicología. Cascarón extensible para que cada dependencia configure su disponibilidad y flujo de agendamiento.           | Estudiantes, profesores, administrativos | Medio      | Alto    | KApp Team                   | 26/02/25 |
| 6   | Menú y Pedidos del Restaurante    | restaurant-service            | Consultar el menú del día del restaurante universitario y realizar pedidos o compras de comida desde la app.                                                                                 | Estudiantes, profesores, administrativos | Medio      | Alto    | KApp Team                   | 26/02/25 |
| 7   | Semáforo Estudiantil              | student-semaphore-service     | Consultar el semáforo académico de cada estudiante (promedio, créditos, alertas) e identificar estudiantes en riesgo para acompañamiento oportuno.                                           | Estudiantes, administradores             | Medio      | Alto    | KApp Team                   | 26/02/25 |
| 8   | Biblioteca                        | library-service               | Consultar catálogo, ver historial de préstamos, renovar préstamos y reservar libros o inscribirse en lista de espera.                                                                        | Estudiantes, profesores                  | Medio      | Alto    | KApp Team                   | 26/02/25 |
| 9   | Eventos y Actividades             | event-service                 | Centralizar eventos universitarios (culturales, deportivos, académicos), filtrar por categoría, inscribirse y agregar al calendario personal.                                                | Todos                                    | Medio      | Alto    | KApp Team                   | 26/02/25 |
| 10  | Notificaciones                    | notification-service          | Servicio transversal de notificaciones push en tiempo real. Centraliza alertas de todos los microservicios (horarios, mensajes, eventos, citas) con configuración personalizada por usuario. | Todos                                    | Medio      | Alto    | KApp Team                   | 26/02/25 |
| 11  | Parqueadero                       | parking-service               | Reservar espacios de parqueo, consultar disponibilidad en tiempo real y realizar pagos por uso del parqueadero universitario.                                                                | Estudiantes, profesores, administrativos | Medio      | Medio   | KApp Team                   | 26/02/25 |
| 12  | Carné Digital                     | carnet-service                | Carné universitario digital con código QR vinculado a la identidad del estudiante o profesor para identificación en accesos y servicios.                                                     | Estudiantes, profesores                  | Medio      | Bajo    | KApp Team                   | 26/02/25 |
| 13  | Chat por Materia                  | chat-service                  | Mensajería organizada por materia: profesor ↔ grupo, estudiante ↔ estudiante, facultad ↔ miembros académicos. Reemplaza canales informales (WhatsApp, correo).                               | Estudiantes, profesores, facultad        | Alto       | Alto    | KApp Team                   | 26/02/25 |

> **KApp Team:** Brian Steven Vargas Clavijo, Julián David Avila Cortes, Santiago Rocha Ramirez, Diego Ali Lares Rondon.

---

<!--
  ┌─────────────────────────────────────────────┐
  │  ¡Agrega tu idea como nueva fila a la       │
  │  tabla, respetando el orden por dificultad!  │
  │  (Bajo → Medio → Alto)                      │
  └─────────────────────────────────────────────┘
-->
