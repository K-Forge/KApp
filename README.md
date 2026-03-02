# K-APP

Plataforma universitaria de la **Fundación Universitaria Konrad Lorenz**, desarrollada por **K-Forge**.

> 🟡 En desarrollo

---

## Stack

| Capa       | Tecnología                                     |
|------------|-------------------------------------------------|
| Backend    | Java 21 · Spring Boot 3.2 · Spring Cloud        |
| Frontend   | Web (Angular) · Android (Kotlin) · iOS (Swift)   |
| Database   | PostgreSQL 15+                                   |
| Infra      | Eureka · API Gateway · Docker · Resilience4j     |
| Security   | Spring Security · JWT · BCrypt                    |

## Quick Start

```bash
# Microservicios (todos)
./scripts/start-microservices.sh

# Frontend web
./scripts/start-frontend.sh

# Estado de servicios
./scripts/start-microservices.sh status
```

## Estructura

```
K-APP/
├── backend/microservices/    # 6 microservicios Spring Boot
├── frontend/web/             # Web app
├── frontend/kotlin/          # Android (futuro)
├── frontend/swift/           # iOS (futuro)
├── database/                 # SQL schemas
├── docs/                     # Requerimientos, diseño, Docker
└── scripts/                  # Bash de inicio
```

## Documentación

| Documento                                    | Descripción                   |
|----------------------------------------------|-------------------------------|
| [Requerimientos](docs/REQUIREMENTS.md)       | Requisitos funcionales y NFR  |
| [Diseño](docs/DESIGN.md)                     | Arquitectura y decisiones     |
| [Docker](docs/DOCKER-GUIDE.md)               | Guía de containerización      |
| [Progreso](PROGRESS.md)                      | Estado de implementación      |
| [Contribuir](CONTRIBUTING.md)                | Guía de contribución          |

## Equipo

Desarrollado por [K-Forge](mailto:kforge.dev@gmail.com) · [Contributors](CONTRIBUTORS.md) · [License](LICENSE)