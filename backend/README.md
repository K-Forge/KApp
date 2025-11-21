# K-APP Backend Structure

Este proyecto sigue una arquitectura en capas estándar de Spring Boot, organizada por módulos funcionales para facilitar la escalabilidad y el mantenimiento.

## Estructura de Paquetes

El código fuente se encuentra en `src/main/java/co/edu/konradlorenz/kapp`.

### 1. Capas Principales
*   **`entity`**: Clases JPA que representan las tablas de la base de datos.
*   **`repository`**: Interfaces que extienden `JpaRepository` para el acceso a datos.
*   **`service`**: Lógica de negocio de la aplicación.
*   **`controller`**: Controladores REST que manejan las peticiones HTTP.
*   **`dto`**: Objetos de Transferencia de Datos (Data Transfer Objects) para la comunicación API.

### 2. Módulos Funcionales
Dentro de cada capa, el código se organiza en los siguientes módulos:
*   **`user`**: Gestión de usuarios, roles y perfiles (Estudiantes, Profesores, Administrativos).
*   **`academic`**: Funcionalidades académicas como notas, horarios y cursos.
*   **`services`**: Servicios universitarios como reservas de espacios, parqueadero y citas de bienestar.
*   **`library`**: Gestión de biblioteca, préstamos y libros.
*   **`events`**: Gestión e inscripción a eventos universitarios.

### 3. Paquetes Transversales (Core)
*   **`config`**: Configuraciones globales del proyecto (Swagger, CORS, Beans).
*   **`security`**: Configuración de Spring Security, filtros JWT y autenticación.
*   **`exception`**: Manejo global de excepciones (`GlobalExceptionHandler`).
*   **`util`**: Clases utilitarias y helpers compartidos.
*   **`integration`**: Clientes para integración con sistemas externos (LMS, APIs de terceros).
*   **`websocket`**: Configuración y manejadores para comunicación en tiempo real (Chat).

## Diagrama de Árbol

```text
co.edu.konradlorenz.kapp
├── config
├── controller
│   ├── academic
│   ├── events
│   ├── library
│   ├── services
│   └── user
├── dto
│   ├── academic
│   ├── events
│   ├── library
│   ├── services
│   └── user
├── entity
│   ├── academic
│   ├── events
│   ├── library
│   ├── services
│   └── user
├── exception
├── integration
├── repository
│   ├── academic
│   ├── events
│   ├── library
│   ├── services
│   └── user
├── security
├── service
│   ├── academic
│   ├── events
│   ├── library
│   ├── services
│   └── user
├── util
└── websocket
```
