# KApp · Estado de Implementación

> Última actualización: Febrero 2026

---

## Resumen

| Área               | Estado      | Progreso |
|--------------------|-------------|----------|
| Infraestructura    | ✅ Completa | 100%     |
| Backend Services   | ✅ Completa | 100%     |
| Frontend Web       | 🟡 Parcial  | 30%      |
| Frontend Android   | 🔲 Pendiente| 0%       |
| Frontend iOS       | 🔲 Pendiente| 0%       |
| DevOps             | 🟡 Parcial  | 40%      |
| Documentación      | 🟡 Parcial  | 50%      |

---

## Microservicios

| Servicio              | Puerto | Estado       | Notas                              |
|-----------------------|--------|--------------|------------------------------------|
| Discovery Server      | 8761   | ✅ Operativo | Eureka dashboard funcional          |
| API Gateway           | 8080   | ✅ Operativo | JWT validation + routing            |
| Auth Service          | 8081   | ✅ Operativo | Login + JWT generation              |
| User Service          | 8082   | ✅ Operativo | CRUD completo + endpoints internos  |
| Course Service        | 8083   | ✅ Operativo | Cursos, grupos, matrículas          |
| Assignment Service    | 8084   | ✅ Operativo | Tareas, entregas, calificaciones    |
| Common Library        | —      | ✅ Operativo | DTOs + excepciones compartidas      |

---

## Funcionalidades

### Autenticación
- [x] Login con email y password
- [x] JWT con roles (STUDENT, PROFESSOR, ADMIN)
- [x] Validación centralizada en Gateway
- [x] BCrypt para passwords
- [ ] Refresh tokens
- [ ] Logout / invalidación de tokens

### Usuarios
- [x] CRUD personas
- [x] CRUD miembros
- [x] CRUD estudiantes
- [x] CRUD empleados
- [x] Endpoints internos (Feign)
- [ ] Perfil de usuario editable

### Cursos
- [x] CRUD cursos y grupos
- [x] Matrícula de estudiantes
- [x] Cursos del estudiante
- [x] Cursos del profesor
- [x] Estudiantes por grupo
- [ ] Horarios
- [ ] Programas académicos detallados

### Tareas
- [x] Crear tareas (profesor)
- [x] Tareas pendientes (estudiante)
- [x] Entregar tareas
- [x] Calificar entregas
- [ ] Adjuntar archivos
- [ ] Notificaciones

### Infraestructura
- [x] Eureka Service Discovery
- [x] API Gateway (Spring Cloud)
- [x] Circuit Breaker (Resilience4j)
- [x] Docker Compose
- [x] Dockerfiles por servicio
- [ ] Config Server
- [ ] Distributed Tracing
- [ ] CI/CD Pipeline
- [ ] Rate Limiting

### Frontend
- [x] Login page
- [x] Dashboard
- [x] Vista de cursos
- [x] Vista de tareas
- [x] Vista de notas
- [x] Vista de horarios
- [ ] Migración a Angular
- [ ] App Android (Kotlin)
- [ ] App iOS (Swift)

### DevOps
- [x] Docker Compose orchestration
- [x] Bash scripts de inicio
- [x] Documentación técnica
- [ ] Kubernetes manifests
- [ ] CI/CD (GitHub Actions)
- [ ] Monitoring (Prometheus/Grafana)

---

## Próximos Pasos (Prioridad)

1. 🔴 **Config Server** — Centralizar configuración
2. 🔴 **Angular Web** — Migrar frontend a Angular
3. 🟡 **Refresh Tokens** — Mejorar flujo de auth
4. 🟡 **Rate Limiting** — Protección del Gateway
5. 🟢 **Kotlin App** — Implementar app Android
6. 🟢 **Swift App** — Implementar app iOS
7. 🟢 **CI/CD** — Pipeline de GitHub Actions

---

## Estructura del Proyecto

```
KApp/
├── backend/
│   ├── kapp/                   # Monolito (legacy)
│   ├── microservices/          # Arquitectura actual
│   │   ├── discovery-server/
│   │   ├── api-gateway/
│   │   ├── auth-service/
│   │   ├── user-service/
│   │   ├── course-service/
│   │   ├── assignment-service/
│   │   ├── common/
│   │   ├── docker-compose.yml
│   │   └── pom.xml
│   └── postman/                # Colecciones de testing
├── frontend/
│   ├── web/                    # Web (HTML/JS → Angular)
│   ├── kotlin/                 # Android (futuro)
│   └── swift/                  # iOS (futuro)
├── database/
│   ├── init.sql
│   ├── test_data.sql
│   └── delete_all_data.sql
├── docs/
│   ├── REQUIREMENTS.md
│   ├── DESIGN.md
│   └── DOCKER-GUIDE.md
├── scripts/
│   ├── start-backend.sh
│   ├── start-frontend.sh
│   └── start-microservices.sh
├── .ai-context.md
├── CONTRIBUTING.md
├── CONTRIBUTORS.md
├── LICENSE
├── PROGRESS.md                 # ← Este archivo
├── README.md
└── SECURITY.md
```

---

*Actualizar este archivo después de cada milestone completado.*
