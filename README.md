<a id="top"></a>

<div align="center">
  <table style="border: none; background-color: transparent;">
    <tr style="border: none; background-color: transparent;">
      <td align="center" width="20%" style="border: none;">
        <!-- ESPACIO RESERVADO PARA LOGO OFICIAL K-FORGE -->
        <img src="./assets/KForge-Yellow-Logo.png" alt="K-Forge Oficial Logo" width="120" style="border-radius: 10px;" />
      </td>
      <td align="center" width="80%" style="border: none;">
        <!-- BANNER CIBERNETICO DEL PROYECTO -->
        <img src="./assets/project-banner.svg" alt="Banner del Proyecto" width="100%" />
      </td>
    </tr>
  </table>
</div>

<p align="center"><strong>Plataforma universitaria de la Fundacion Universitaria Konrad Lorenz para gestionar procesos academicos y administrativos.</strong></p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot 3.2"/>
  <img src="https://img.shields.io/badge/Spring%20Cloud-2023.0-6DB33F?style=for-the-badge&logo=spring&logoColor=white" alt="Spring Cloud"/>
  <img src="https://img.shields.io/badge/PostgreSQL-15+-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL"/>
  <img src="https://img.shields.io/badge/Docker-Ready-2496ED?style=for-the-badge&logo=docker&logoColor=white" alt="Docker"/>
  <img src="https://img.shields.io/badge/Estado-En%20desarrollo-EAB308?style=for-the-badge" alt="En desarrollo"/>
</p>

---

## Tabla de Contenidos

- [Descripcion](#descripcion)
- [Stack y arquitectura](#stack-y-arquitectura-resumen)
- [Microservicios](#microservicios)
- [Inicio rapido](#inicio-rapido)
- [Estructura del repositorio](#estructura-del-repositorio)
- [Documentacion](#documentacion)
- [Enlaces rapidos](#enlaces-rapidos)
- [Equipo](#equipo)
- [Licencia](#licencia)

---

## Descripcion

KApp implementa una arquitectura de microservicios en Spring Boot con autenticacion centralizada por JWT en el API Gateway. El backend esta operativo y el frontend web actual se mantiene en `HTML/CSS/JS`, con migracion progresiva a Angular.

## Stack y arquitectura (resumen)

| Capa            | Tecnologias principales                                                                |
| --------------- | -------------------------------------------------------------------------------------- |
| Backend         | Java 21, Spring Boot 3.2, Spring Cloud 2023.0.0                                        |
| Seguridad       | Spring Security, JWT (JJWT), BCrypt                                                    |
| Persistencia    | Spring Data JPA, Hibernate, PostgreSQL 15+                                             |
| Infraestructura | Eureka, Spring Cloud Gateway, OpenFeign, Resilience4j, Docker                          |
| Frontend        | Web (`HTML/CSS/JS`, migrando a Angular), Android (Kotlin, futuro), iOS (Swift, futuro) |

## Microservicios

Los servicios principales se encuentran en `app/backend/microservices/`.

| Servicio           | Puerto | Rol                                             |
| ------------------ | ------ | ----------------------------------------------- |
| Discovery Server   | 8761   | Registro y descubrimiento de servicios (Eureka) |
| API Gateway        | 8080   | Entrada unica, enrutamiento y validacion de JWT |
| Auth Service       | 8081   | Autenticacion y emision de tokens               |
| User Service       | 8082   | Gestion de usuarios y perfiles                  |
| Course Service     | 8083   | Gestion de cursos, grupos y matriculas          |
| Assignment Service | 8084   | Gestion de tareas, entregas y calificaciones    |
| Common Library     | —      | DTOs y excepciones compartidas                  |

## Inicio rapido

```bash
# Levantar microservicios
./scripts/start-microservices.sh

# Iniciar frontend web
./scripts/start-frontend.sh

# Consultar estado de microservicios
./scripts/start-microservices.sh status
```

## Estructura del repositorio

```text
KApp/
├── app/
│   ├── backend/
│   │   ├── microservices/   # Arquitectura principal (actual)
│   │   ├── kapp/            # Monolito legado (sin nuevas funcionalidades)
│   │   └── postman/         # Colecciones para pruebas API
│   ├── frontend/
│   │   ├── web/             # Frontend web actual
│   │   └── mobile/          # Kotlin (Android) y Swift (iOS)
│   └── database/            # Esquema y scripts SQL
├── docs/                    # Requerimientos, diseno y guias tecnicas
├── scripts/                 # Scripts de arranque
├── CONTRIBUTING.md
├── CONTRIBUTORS.md
└── LICENSE
```

## Documentacion

| Documento                            | Contenido                                    |
| ------------------------------------ | -------------------------------------------- |
| [SRS](docs/SRS.md)                   | Especificacion de requerimientos de software |
| [REQUIREMENTS](docs/REQUIREMENTS.md) | Requisitos funcionales y no funcionales      |
| [DESIGN](docs/DESIGN.md)             | Decisiones de arquitectura y diseno tecnico  |
| [DOCKER-GUIDE](docs/DOCKER-GUIDE.md) | Guia de despliegue y contenedores            |
| [PROGRESS](docs/PROGRESS.md)         | Estado de implementacion por modulos         |
| [K-COLORS](docs/K-COLORS.md)         | Guia de color de la marca                    |
| [CONTRIBUTING](CONTRIBUTING.md)      | Flujo de ramas, commits y contribucion       |

## Enlaces rapidos

| Recurso               | Enlace                                           |
| --------------------- | ------------------------------------------------ |
| Organizacion K-Forge  | [github.com/K-Forge](https://github.com/K-Forge) |
| Web oficial K-Forge   | [kforge.vercel.app](https://kforge.vercel.app)   |
| Guia de contribucion  | [CONTRIBUTING.md](CONTRIBUTING.md)               |
| Contribuyentes        | [CONTRIBUTORS.md](CONTRIBUTORS.md)               |
| Diseno y arquitectura | [docs/DESIGN.md](docs/DESIGN.md)                 |
| Contacto              | kforge.dev@gmail.com                             |

## Equipo

Proyecto desarrollado por el club [K-Forge](https://kforge.vercel.app) en la Fundacion Universitaria Konrad Lorenz.

- Integrantes y contribuyentes: [CONTRIBUTORS.md](CONTRIBUTORS.md)
- Contacto del equipo: `kforge.dev@gmail.com`

## Acceso y contribucion

Repositorio visible para consulta academica y tecnica.

El mantenimiento del codigo esta restringido a miembros autorizados de K-Forge y de la Fundacion Universitaria Konrad Lorenz. Para contribuir, sigue la guia de [CONTRIBUTING.md](CONTRIBUTING.md).

## Licencia

Proyecto bajo [Licencia de Uso Interno](LICENSE).

El uso, modificacion y distribucion estan restringidos segun los terminos definidos por el equipo KApp/K-Forge para la Fundacion Universitaria Konrad Lorenz.

---

<div align="center">
  <br>
  <a href="https://github.com/K-Forge">
    <img src="https://img.shields.io/badge/GitHub-K--Forge-181717?style=for-the-badge&logo=github&logoColor=white" alt="GitHub"/>
  </a>
  &nbsp;
  <a href="https://kforge.vercel.app">
    <img src="https://img.shields.io/badge/Web-kforge.vercel.app-EAB308?style=for-the-badge&logo=vercel&logoColor=white" alt="Web"/>
  </a>
  &nbsp;
  <a href="mailto:kforge.dev@gmail.com">
    <img src="https://img.shields.io/badge/Email-kforge.dev-EA4335?style=for-the-badge&logo=gmail&logoColor=white" alt="Email"/>
  </a>
  <br><br>
  <sub>Forjado por <a href="https://github.com/K-Forge"><strong>K-Forge</strong></a> — Club de desarrollo de la Konrad Lorenz</sub>
  <br><br>
  <a href="#top">
    <img src="https://img.shields.io/badge/%E2%96%B2_Volver_arriba-EAB308?style=flat-square" alt="Volver arriba"/>
  </a>
  <br><br>
  <img src="https://capsule-render.vercel.app/api?type=waving&height=100&color=0:000000,100:EAB308&section=footer" width="100%"/>
</div>
