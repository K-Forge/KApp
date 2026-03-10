# K-APP

## Aplicación Móvil de la Fundación Universitaria Konrad Lorenz

### Especificación de Requisitos de Software (SRS)

**Versión:** 1.0  
**Fecha:** 00/00/2025

---

> Queda prohibido cualquier tipo de explotación y, en particular, la reproducción, distribución, comunicación pública y/o transformación, total o parcial, por cualquier medio, de este documento sin el previo consentimiento expreso y por escrito de Brian Steven Vargas Clavijo, Santiago Rocha Ramírez, María Fernanda Vanegas Rey.

---

## Control de Versiones

| Versión | Causa del Cambio | Responsable | Fecha      |
| ------- | ---------------- | ----------- | ---------- |
| 1.0     | Versión inicial  |             | 00/00/2025 |

---

## Tabla de Contenido

- [1.1 Propósito](#11-propósito)
- [1.2 Objetivos](#12-objetivos)
  - [1.2.1 Objetivo General](#121-objetivo-general)
  - [1.2.2 Objetivos Específicos](#122-objetivos-específicos)
- [1.3 Alcance](#13-alcance)
- [1.4 Definiciones, Acrónimos y Abreviaturas](#14-definiciones-acrónimos-y-abreviaturas)
- [2.1 Perspectiva del Producto](#21-perspectiva-del-producto)
- [2.2 Funcionalidades del Producto (Resumen)](#22-funcionalidades-del-producto-resumen)
- [2.3 Características del Usuario](#23-características-del-usuario)
- [2.4 Restricciones Generales](#24-restricciones-generales)
- [2.5 Suposiciones y Dependencias](#25-suposiciones-y-dependencias)
- [3.1 Requerimientos Funcionales](#31-requerimientos-funcionales)
- [3.2 Requerimientos No Funcionales](#32-requerimientos-no-funcionales)
- [3.3 Roles y Permisos de Usuario](#33-roles-y-permisos-de-usuario)

---

## 1. Introducción

### 1.1 Propósito

K-APP es una aplicación móvil creada dentro del club de desarrollo **K-Forge**, orientada a estudiantes de la Fundación Universitaria Konrad Lorenz. Su propósito es centralizar en un solo lugar la información y los servicios más relevantes para la vida universitaria, brindando acceso rápido a funciones académicas, administrativas y de comunicación, todo desde un entorno intuitivo y seguro.

### 1.2 Objetivos

#### 1.2.1 Objetivo General

Desarrollar una aplicación móvil que simplifique la experiencia universitaria, integrando en un solo lugar herramientas académicas, administrativas, de comunicación y participación estudiantil.

#### 1.2.2 Objetivos Específicos

1. Facilitar la gestión académica mediante acceso ágil a horarios, calificaciones y notificaciones relevantes.
2. Mejorar la comunicación entre estudiantes y con la universidad mediante mensajería y avisos en tiempo real.
3. Centralizar servicios digitales como carné universitario, reservas de espacios y citas institucionales para simplificar trámites.
4. Fomentar el sentido de comunidad universitaria mediante la promoción e inscripción en eventos y actividades del campus.

### 1.3 Alcance

K-APP estará disponible como aplicación móvil para dispositivos Android e iOS, dirigida principalmente a los estudiantes de la Fundación Universitaria Konrad Lorenz, aunque también permitirá acceso limitado a profesores, personal administrativo e invitados.

La aplicación integrará en un solo lugar:

- **Funciones académicas:** consulta de horarios, calificaciones y notificaciones.
- **Servicios universitarios:** carné digital, reservas de espacios, citas de bienestar y tutorías.
- **Comunicación:** chat privado y grupal, así como avisos oficiales en tiempo real.
- **Información institucional:** mapa interactivo, calendario de eventos y redes sociales.

El desarrollo se centrará en ofrecer una **experiencia intuitiva, segura y personalizable**, garantizando que los estudiantes puedan gestionar su vida universitaria desde cualquier lugar. Se priorizará la integración con los sistemas existentes de la universidad para mantener la información actualizada sin duplicar procesos.

### 1.4 Definiciones, Acrónimos y Abreviaturas

| Término                         | Definición                                                                                                                                                                                  |
| ------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **K-APP**                       | Aplicación móvil creada para la Fundación Universitaria Konrad Lorenz con el fin de centralizar servicios y herramientas para estudiantes, profesores, personal administrativo e invitados. |
| **K-Forge**                     | Club estudiantil de desarrollo tecnológico encargado de diseñar y mantener K-APP y otros proyectos de software para la universidad.                                                         |
| **Credenciales universitarias** | Usuario y contraseña entregados por la universidad para identificar a cada miembro y permitir el acceso a sistemas internos.                                                                |
| **Carné digital**               | Versión electrónica del carné universitario disponible dentro de K-APP, que incluye información personal y un código QR para validación.                                                    |
| **Código QR**                   | Código gráfico que se escanea con la cámara del dispositivo para autenticar identidad o acceder a servicios.                                                                                |
| **Roles de usuario**            | Perfiles con diferentes niveles de acceso y permisos: Estudiante, Profesor, Administrador, Personal Administrativo e Invitado.                                                              |
| **Chat interno**                | Funcionalidad de mensajería dentro de la app para comunicación privada o grupal entre miembros de la comunidad universitaria.                                                               |
| **Servicios universitarios**    | Conjunto de funcionalidades que facilitan trámites o reservas: citas de bienestar, tutorías, parqueadero y atención académica.                                                              |
| **Eventos**                     | Actividades académicas, culturales, deportivas o institucionales organizadas por la universidad.                                                                                            |
| **Mapa interactivo**            | Herramienta visual que muestra edificios, salones y espacios relevantes del campus.                                                                                                         |
| **Notificaciones push**         | Avisos en tiempo real enviados al dispositivo móvil sobre eventos, cambios o mensajes importantes.                                                                                          |
| **LMS**                         | Learning Management System: plataforma digital para la gestión de aprendizaje (como Moodle o Blackboard).                                                                                   |
| **Integración de sistemas**     | Conexión entre K-APP y las plataformas ya existentes en la universidad para sincronizar datos.                                                                                              |
| **Autenticación segura**        | Proceso que verifica la identidad del usuario mediante contraseñas, códigos de verificación o autenticación de dos factores.                                                                |
| **Accesibilidad**               | Características de diseño para que la app sea usable por personas con discapacidades (texto ampliado, alto contraste, etc.).                                                                |
| **Personalización**             | Posibilidad de ajustar la apariencia o configuración de la app (colores, temas, tamaños de letra).                                                                                          |
| **API**                         | Application Programming Interface: conjunto de reglas y métodos que permiten que diferentes sistemas se comuniquen entre sí.                                                                |
| **App**                         | Aplicación diseñada para ejecutarse en dispositivos móviles.                                                                                                                                |

---

## 2. Descripción General

### 2.1 Perspectiva del Producto

K-APP es una aplicación móvil independiente que se integrará con los sistemas académicos y administrativos existentes de la Fundación Universitaria Konrad Lorenz. Funcionará como una plataforma centralizada para estudiantes, profesores, personal administrativo e invitados, permitiendo acceso a información y servicios sin necesidad de múltiples aplicaciones o portales web.

### 2.2 Funcionalidades del Producto (Resumen)

- Autenticación segura con credenciales universitarias o acceso limitado como invitado.
- Consulta de horarios, calificaciones e historial académico.
- Comunicación mediante chat privado y grupal con notificaciones en tiempo real.
- Carné universitario digital con código QR único y seguro.
- Acceso a servicios como reservas de espacios, citas de bienestar, tutorías y parqueadero.
- Información de eventos universitarios y registro directo desde la app.
- Mapa interactivo del campus y orientación guiada.
- Visualización de publicaciones y redes sociales oficiales.
- Centro de soporte integrado y canal de retroalimentación.
- Opciones de accesibilidad y personalización de la interfaz.

### 2.3 Características del Usuario

| Rol                         | Descripción                                                                                    |
| --------------------------- | ---------------------------------------------------------------------------------------------- |
| **Estudiantes**             | Usuarios principales con acceso completo a herramientas académicas y servicios universitarios. |
| **Profesores**              | Acceso a gestión de clases, comunicación con estudiantes y publicación de calificaciones.      |
| **Administradores**         | Control total para gestionar usuarios, eventos y configuraciones del sistema.                  |
| **Personal administrativo** | Acceso a gestión de citas, eventos y recursos sin modificar información académica.             |
| **Invitados**               | Acceso limitado a eventos y contenidos públicos.                                               |

### 2.4 Restricciones Generales

- La aplicación debe estar disponible para Android e iOS.
- El acceso completo requiere credenciales válidas de la universidad.
- Toda la información sensible debe manejarse con cifrado y cumplir con normativas de protección de datos.
- La integración con sistemas actuales depende de la disponibilidad de APIs proporcionadas por la universidad.

### 2.5 Suposiciones y Dependencias

- Los usuarios contarán con conexión a Internet para acceder a la mayoría de las funciones.
- La universidad proveerá las bases de datos y servicios necesarios para sincronizar información académica y administrativa.
- Las notificaciones en tiempo real requieren que los dispositivos permitan la recepción de avisos push.
- El éxito del despliegue depende de la correcta colaboración entre K-Forge, la universidad y proveedores de sistemas existentes.

---

## 3. Requerimientos Específicos

### 3.1 Requerimientos Funcionales

#### 3.1.1 Autenticación y Acceso

1. La aplicación debe permitir el inicio de sesión utilizando credenciales universitarias (código de estudiante/profesor y contraseña).
2. La aplicación debe permitir el acceso como invitado con funcionalidades limitadas a información pública.
3. La aplicación debe ofrecer la opción de recordar credenciales para agilizar futuros inicios de sesión.
4. La aplicación debe permitir la recuperación de contraseña mediante un proceso seguro de validación.

#### 3.1.2 Navegación General

5. La aplicación debe incluir un botón de inicio accesible desde cualquier pantalla para regresar a la página principal.
6. La aplicación debe contar con un menú de navegación estructurado para acceder rápidamente a todas las secciones principales.

#### 3.1.3 Comunicación

7. La aplicación debe permitir la comunicación entre estudiantes mediante un sistema de chat privado y grupal.
8. El sistema de chat debe soportar el envío de texto, imágenes, videos y archivos adjuntos.
9. La aplicación debe enviar notificaciones push en tiempo real para avisos importantes, mensajes nuevos y cambios relevantes.

#### 3.1.4 Gestión Académica

10. La aplicación debe permitir la visualización del horario académico actual.
11. La aplicación debe notificar al usuario sobre cambios o cancelaciones de clases.
12. La aplicación debe permitir la visualización de calificaciones del semestre en curso.
13. La aplicación debe permitir el acceso al historial de calificaciones de semestres anteriores.

#### 3.1.5 Carné Universitario Digital

14. La aplicación debe mostrar el carné universitario en formato digital.
15. El sistema debe generar un código QR único y seguro vinculado a la identidad del estudiante.

#### 3.1.6 Biblioteca

16. La aplicación debe mostrar el historial personal de préstamos de libros y sus fechas de devolución.
17. La aplicación debe permitir la renovación de préstamos directamente desde la app.
18. La aplicación debe permitir la reserva de libros disponibles o la inscripción en lista de espera.

#### 3.1.7 Servicios Universitarios

19. La aplicación debe permitir reservar espacios o citas en Bienestar Universitario.
20. La aplicación debe permitir gestionar la reserva y acceso al parqueadero universitario, incluyendo la posibilidad de pago.
21. La aplicación debe permitir reservar tutorías académicas.
22. La aplicación debe permitir agendar citas con coordinación académica.
23. La aplicación debe permitir agendar citas con el servicio de psicología.

#### 3.1.8 Eventos y Actividades

24. La aplicación debe mostrar los próximos eventos organizados por la universidad.
25. La aplicación debe permitir filtrar eventos por categoría (cultural, académico, deportivo, etc.).
26. La aplicación debe permitir inscribirse o registrarse directamente a eventos universitarios.
27. La aplicación debe permitir agregar eventos al calendario personal del usuario.

#### 3.1.9 Ubicación y Orientación

28. La aplicación debe mostrar un mapa interactivo del campus universitario.
29. La aplicación debe mostrar la ubicación de salones, edificios y espacios relevantes.
30. La aplicación debe ofrecer guías interactivas o videos para orientar al usuario dentro del campus.

#### 3.1.10 Redes Sociales

31. La aplicación debe redirigir a los usuarios a las redes sociales oficiales de la universidad.
32. La aplicación debe mostrar las publicaciones o actualizaciones recientes de la universidad dentro de la app.

### 3.2 Requerimientos No Funcionales

1. La aplicación debe estar disponible para dispositivos Android y iOS.
2. Toda la información sensible debe manejarse mediante cifrado y cumplir normativas de protección de datos.
3. La aplicación debe garantizar tiempos de respuesta menores a 2 segundos en condiciones normales de uso.
4. La aplicación debe ser accesible, incorporando opciones de alto contraste, texto ampliado y compatibilidad con lectores de pantalla.
5. La aplicación debe permitir personalización de la interfaz (temas, tamaños de fuente, colores).

### 3.3 Roles y Permisos de Usuario

#### 3.3.1 Estudiante

- Acceso completo a herramientas académicas, servicios universitarios, chat, carné digital, mapa interactivo y eventos.
- Puede reservar tutorías, espacios y citas institucionales.
- Recibe notificaciones personalizadas.

#### 3.3.2 Profesor

- Acceso a gestión de clases, horarios y calificaciones.
- Puede comunicarse con estudiantes mediante chat y notificaciones.
- Puede agendar tutorías académicas y crear eventos académicos.

#### 3.3.3 Administrador

- Control total sobre la aplicación: gestión de usuarios, roles, eventos y configuraciones.
- Acceso a reportes e historial de uso.
- Puede modificar información académica y administrativa.

#### 3.3.4 Personal Administrativo

- Acceso a la gestión de servicios y citas institucionales.
- Puede supervisar la disponibilidad de recursos y gestionar eventos generales.

#### 3.3.5 Invitado

- Acceso limitado a información pública, eventos abiertos y redes sociales oficiales.

---

> ⚠️ **TODO:** Las secciones 4 a 10 están siendo reformateadas y reestructuradas para mayor claridad.

---

3. Funcionalidades Principales
1. Autenticación y Acceso
   Inicio de sesión con credenciales universitarias: Permitir a los estudiantes acceder a la app usando su número de estudiante (código de estudiante) y contraseña asociada.
   Inicio de sesión como invitado: Acceso limitado a funcionalidades públicas de la universidad sin necesidad de autenticarse.
   Recordar los datos de inicio de sesión: Opción para guardar las credenciales del usuario para iniciar sesión automáticamente en futuros accesos.
   Recuperación de contraseña: Funcionalidad para recuperar la contraseña en caso de olvido mediante un proceso seguro de validación.
1. Navegación General
   Botón de inicio (Home): Acceso fácil y rápido al inicio de la app desde cualquier pantalla.
   Menú de navegación intuitivo: Un menú claro y bien estructurado para acceder rápidamente a todas las secciones de la aplicación (horarios, notas, servicios, etc.).
1. Comunicación
   Sistema de chat: Permitir la comunicación entre estudiantes mediante un chat privado o grupal.
   Notificaciones push: Enviar notificaciones en tiempo real para eventos importantes, mensajes nuevos o cambios en el horario académico.
1. Gestión Académica
   Visualización de horarios: Acceso fácil al horario académico actual, con la opción de ver detalles de cada clase, aula y profesor.
   Notificaciones de cambios en el horario: Alerta sobre cualquier cambio, cancelación o reprogramación de clases.
   Visualización de notas: Acceder a las calificaciones del semestre en curso, con la posibilidad de revisar el histórico de notas de semestres anteriores.
1. Carné Virtual
   Visualización del carné universitario digital: Mostrar el carné universitario de forma digital (incluyendo código QR vinculado a la identidad del estudiante).
   Generación de código QR: Crear un código QR único asociado a la identidad del estudiante para ser escaneado en diferentes servicios dentro de la universidad
1. Biblioteca
   Acceso al registro de la biblioteca: Ver el historial de préstamos y las fechas de devolución de los libros.
   Renovación de préstamos: Opción para renovar los préstamos de libros desde la app sin necesidad de ir físicamente a la biblioteca.
   Reserva de libros: Permitir la reserva de libros disponibles en la biblioteca (en caso de que estén prestados) o agregar libros a una lista de espera.
1. Servicios Universitarios
   Reserva de espacios en Bienestar Universitario: Agendar citas o reservar espacios en actividades o instalaciones de Bienestar Universitario.
   Gestión del parqueadero: Reservar espacios de parqueo, gestionar el acceso y, si es necesario, realizar pagos por el uso del parqueadero.
   Reserva de tutorías académicas: Agendar una cita para recibir apoyo académico con profesores o tutores.
   Citas con coordinación académica: Agendar una reunión con el personal de coordinación académica para resolver dudas o consultas.
   Citas con psicología: Reservar una cita con el servicio de psicología o bienestar emocional.
1. Eventos y Actividades
   Visualización de eventos: Acceso a un calendario de eventos organizados por la universidad, incluyendo actividades culturales, deportivas, académicas, etc.
   Filtro de eventos por categoría: Posibilidad de filtrar eventos por tipo (cultural, académico, deportivo, etc.).
   Agregar eventos al calendario personal: Permitir que los usuarios puedan agregar eventos al calendario de su dispositivo móvil.
   Inscripción a eventos: Inscribirse directamente en eventos organizados por la universidad (cuando sea necesario).
1. Ubicación y Orientación
   Mapa interactivo del campus: Visualizar un mapa de la universidad con la ubicación de edificios, aulas, bibliotecas, y otros servicios relevantes.
   Guía interactiva o video de orientación: Proveer guías o videos interactivos que expliquen cómo llegar a diferentes lugares dentro del campus.
   Ubicación en tiempo real: Función que permita ver la ubicación actual del usuario dentro del campus y orientarse en tiempo real.
1. Redes Sociales
   Enlace a redes sociales: Acceso directo a las redes sociales oficiales de la universidad (Facebook, Instagram, Twitter, etc.).
   Visualización de publicaciones recientes: Mostrar las últimas publicaciones o eventos destacados directamente dentro de la app (por ejemplo, noticias, actualizaciones de la universidad)
1. Soporte y Ayuda
   Centro de soporte dentro de la app: Un sistema de soporte que permita a los usuarios resolver dudas o problemas técnicos, ya sea mediante una base de conocimientos o contacto directo con el personal de soporte.
   Feedback y sugerencias: Permitir que los usuarios puedan enviar comentarios, sugerencias o reportar errores de manera fácil y directa.

1. Accesibilidad y Personalización
   Modo de accesibilidad: Incluir opciones de accesibilidad para personas con discapacidad (por ejemplo, opciones de aumento de texto, contraste, lector de pantalla).
   Personalización de la interfaz: Permitir que los usuarios personalicen la apariencia de la app (temas, tamaños de fuente, etc.) para mejorar la experiencia de uso.
1. Seguridad y Privacidad
   Autenticación segura: Implementar un sistema de autenticación robusto (como autenticación de dos factores) para proteger la cuenta del usuario.
   Protección de datos personales: Asegurar que los datos sensibles (como notas, horarios, información personal) estén cifrados y protegidos según las normativas de seguridad de datos.
   Historial de accesos y sesión segura: Ofrecer a los usuarios un registro de los dispositivos o sesiones activas, y permitirles cerrar sesión de manera remota.
1. Integraciones
   Integración con otros sistemas universitarios: Integrar la app con sistemas ya existentes en la universidad, como plataformas de aprendizaje, gestión de pagos, o sistemas de gestión académica, para mejorar la sincronización de la información.

1. Usuarios y Roles

1. Estudiante
   El Estudiante es el usuario principal de la aplicación y tiene acceso a la mayoría de las funcionalidades académicas y de servicios relacionados con su vida universitaria.
   Permisos:
   Autenticación:
   Inicio de sesión con credenciales universitarias (código de estudiante y contraseña).
   Acceso limitado como invitado (acceso a funcionalidades públicas).
   Gestión Académica:
   Ver su horario académico actual y los detalles de cada clase.
   Ver sus calificaciones del semestre en curso y el historial de notas.
   Biblioteca:
   Acceder al registro personal de la biblioteca (ver libros prestados y fechas de devolución).
   Renovar préstamos de libros desde la app.
   Reservar libros disponibles o agregar libros a una lista de espera.
   Servicios Universitarios:
   Reservar espacios en Bienestar Universitario, parqueadero, tutorías académicas, citas con coordinación académica y psicología.
   Eventos y Actividades:
   Ver los eventos organizados por la universidad, filtrarlos por categorías (cultural, académico, deportivo, etc.) y agregarlos al calendario personal.
   Inscribirse en eventos universitarios si es necesario.
   Carné Virtual:
   Visualizar su carné universitario digital y generar un código QR para identificarse en la universidad.
   Comunicación:
   Acceder a un sistema de chat para comunicarse con otros estudiantes.
   Recibir notificaciones push sobre eventos, cambios en el horario, mensajes, etc.
   Ubicación y Orientación:
   Ver el mapa interactivo del campus y las ubicaciones de los edificios y espacios relevantes.
   Acceder a guías interactivas o videos sobre cómo llegar a ciertos lugares dentro del campus.
   Redes Sociales:
   Acceder a las redes sociales oficiales de la universidad.

1. Profesor
   El Profesor tiene acceso a las funcionalidades relacionadas con la gestión académica, la comunicación con los estudiantes y algunos servicios administrativos.
   Permisos:
   Autenticación:
   Inicio de sesión con credenciales académicas (código de profesor y contraseña).
   Gestión Académica:
   Ver el horario académico de las clases que imparte.
   Visualizar la lista de estudiantes inscritos en sus clases.
   Ver las calificaciones de sus estudiantes y cargar nuevas calificaciones.
   Actualizar el horario de clases o realizar ajustes en caso de cambios.
   Comunicación:
   Acceder al sistema de chat para comunicarse con sus estudiantes.
   Enviar notificaciones a los estudiantes sobre cambios en las clases o eventos importantes.
   Servicios Universitarios:
   Agendar tutorías académicas con estudiantes.
   Eventos y Actividades:
   Crear o gestionar eventos académicos o culturales organizados por la universidad.
   Ver eventos relevantes para su área de trabajo (actividades académicas, reuniones, etc.).
   Redes Sociales:
   Acceder a las redes sociales oficiales de la universidad para mantenerse informado.

1. Administrador
   El Administrador es el usuario con acceso completo a la gestión y administración de la aplicación. Tiene la capacidad de gestionar la configuración, usuarios y supervisar todos los procesos de la universidad dentro de la app.
   Permisos:
   Gestión de Usuarios:
   Crear, editar y eliminar cuentas de estudiantes, profesores y personal administrativo.
   Gestionar roles y permisos de usuarios (asignar roles de administrador, profesor, etc.).
   Gestión Académica:
   Modificar horarios académicos de manera general.
   Ver y editar notas y calificaciones de todos los estudiantes en la universidad.
   Gestionar la asignación de recursos educativos, como aulas o equipos tecnológicos.
   Servicios Universitarios:
   Gestionar la reserva de espacios en Bienestar Universitario y parqueadero.
   Ver las citas y reservas realizadas por estudiantes y profesores.
   Administrar servicios de psicología, tutoría académica y coordinación académica.
   Eventos y Actividades:
   Crear, editar y gestionar eventos y actividades dentro de la universidad.
   Ver los detalles de la inscripción en eventos y controlar el acceso a los mismos.
   Comunicación:
   Enviar notificaciones a todos los usuarios sobre eventos importantes, actualizaciones de horarios, nuevos servicios, etc.
   Seguridad y Configuración:
   Configurar y administrar la seguridad de la aplicación (cifrado de datos, autenticación de dos factores, etc.).
   Realizar copias de seguridad y mantenimiento del sistema.
   Redes Sociales:
   Gestionar enlaces y contenido relacionado con las redes sociales oficiales de la universidad.
   Informes y Análisis:
   Acceder a informes detallados sobre el uso de la aplicación, la interacción de los estudiantes con los servicios y el rendimiento académico.

1. Personal Administrativo
   El Personal Administrativo tiene un rol intermedio, con acceso a ciertos servicios administrativos y de soporte, pero con permisos limitados en comparación con los administradores.
   Permisos:
   Gestión Académica:
   Ver los horarios y la lista de estudiantes inscritos en las clases, pero sin la capacidad de modificar calificaciones o horarios.
   Gestionar la asignación de recursos para actividades académicas (espacios, materiales, etc.).
   Servicios Universitarios:
   Gestionar las citas de los estudiantes para servicios como bienestar universitario, psicología, tutorías y coordinación académicas.
   Supervisar la disponibilidad del parqueadero y otras instalaciones.
   Eventos y Actividades:
   Crear o gestionar eventos a nivel administrativo, como actividades de bienestar o eventos académicos generales.
   Gestionar inscripciones y asistencia a eventos.
   Comunicación:
   Comunicarse con estudiantes y profesores a través de notificaciones o mensajes sobre eventos, citas, o actualizaciones importantes.
   Redes Sociales:
   Acceder a la información de las redes sociales de la universidad y apoyar la promoción de eventos.

1. Invitado (Usuario No Autenticado)
   El Invitado es un usuario que no tiene acceso completo a la app, pero puede visualizar ciertas funcionalidades de interés general, como eventos públicos, servicios básicos de información, o contenidos abiertos de la universidad.
   Permisos:
   Acceso Limitado:
   Ver eventos públicos organizados por la universidad.
   Consultar información general sobre la universidad, como ubicación, contacto, etc.
   Redes Sociales:
   Acceder a las redes sociales oficiales de la universidad sin necesidad de autenticación.

1. Plataforma y Tecnologías
   Especificar en qué plataformas funcionará la aplicación (web, móvil, escritorio) y qué tecnologías se utilizarán.
1. Diseño y Experiencia de Usuario (UI/UX)
   Describir la apariencia y la usabilidad de la aplicación.
1. Integraciones
   Especificar sistemas o servicios con los que la aplicación debe integrarse.
1. Seguridad
   Definir medidas de seguridad necesarias, como autenticación, cifrado y protección de datos.
1. Requerimientos No Funcionales
   Describir requisitos de rendimiento, escalabilidad, disponibilidad, entre otros.
1. Entrega y Cronograma
   Definir el plan de desarrollo y fechas de entrega de cada fase.

RECURSOS & FUENTES:
VIDEO: Paper Prototype Jave Móvil
