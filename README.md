# KApp

Plataforma universitaria de la **Fundación Universitaria Konrad Lorenz**, desarrollada por **K-Forge**.

> 🟡 En desarrollo

---

## Stack

| Capa     | Tecnología                                     |
| -------- | ---------------------------------------------- |
| Backend  | Java 21 · Spring Boot 3.2 · Spring Cloud       |
| Frontend | Web (Angular) · Android (Kotlin) · iOS (Swift) |
| Database | PostgreSQL 15+                                 |
| Infra    | Eureka · API Gateway · Docker · Resilience4j   |
| Security | Spring Security · JWT · BCrypt                 |

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
KApp/
├── app/
│   ├── backend/
│   │   ├── microservices/      # 6 microservicios Spring Boot
│   │   ├── kapp/               # Monolito legacy
│   │   └── postman/            # Colecciones Postman
│   ├── frontend/
│   │   ├── web/                # Web app (HTML/JS/CSS)
│   │   └── mobile/             # Android (Kotlin) · iOS (Swift)
│   └── database/               # SQL schemas
├── docs/                       # SRS, diseño, requerimientos, Docker
└── scripts/                    # Bash de inicio
```

## Documentación

| Documento                              | Descripción                      |
| -------------------------------------- | -------------------------------- |
| [SRS](docs/SRS.md)                     | Especificación de requerimientos |
| [Requerimientos](docs/REQUIREMENTS.md) | Requisitos funcionales y NFR     |
| [Diseño](docs/DESIGN.md)               | Arquitectura y decisiones        |
| [Colores](docs/K-COLORS.md)            | Paleta de colores de la marca    |
| [Docker](docs/DOCKER-GUIDE.md)         | Guía de containerización         |
| [Progreso](docs/PROGRESS.md)           | Estado de implementación         |
| [Contribuir](CONTRIBUTING.md)          | Guía de contribución             |

## Equipo

Desarrollado por [K-Forge](mailto:kforge.dev@gmail.com) · [Contributors](CONTRIBUTORS.md) · [License](LICENSE)
