# Guía para Contribuir a KApp

## ¿Quién puede contribuir?

Este proyecto es parte de K-Forge y está restringido a miembros autorizados de la Fundación Universitaria Konrad Lorenz. Si formas parte del equipo de desarrollo, sigue estas pautas para contribuir al código.

---

## Convención para Commits

Para mantener un historial limpio y comprensible, seguimos la convención de **Conventional Commits** y usamos la herramienta **Git Glow** para hooks. Usa el siguiente formato para tus mensajes de commit:

```
type: short message in english
```

> El mensaje siempre debe estar en **inglés**, en **minúsculas**, y sin punto final.

---

### Tipos de Commits

| Tipo       | Descripción                                    |
| ---------- | ---------------------------------------------- |
| `feat`     | Nueva funcionalidad                            |
| `fix`      | Corrección de errores                          |
| `chore`    | Tareas de mantenimiento del proyecto           |
| `release`  | Preparación de una nueva versión               |
| `hotfix`   | Corrección urgente en producción               |
| `docs`     | Cambios en documentación                       |
| `refactor` | Refactorización de código sin cambiar comportamiento |
| `test`     | Agregar o modificar tests                      |

---

### Ejemplos Correctos

```
feat: add login screen
fix: resolve jwt token expiration bug
chore: update spring boot dependencies
docs: add branching guide to contributing
refactor: extract user validation logic
test: add integration tests for user service
release: prepare version 1.0.0
hotfix: fix cors config in gateway
```

### Ejemplos Incorrectos

```
update                          → No describe nada útil
[FEAT][UI] Agregar pantalla     → No usar corchetes ni español
Fix bug                         → Debe ser minúscula
cambios varios                  → Muy ambiguo, y no está en inglés
```

---

## Modelo de Ramas — Git Flow

Seguimos el modelo **Git Flow** para organizar el trabajo en ramas. Todas las ramas deben partir de `develop` (excepto `hotfix/*`, que parte de `main`).

### Diagrama de ramas

```mermaid
gitGraph
   commit id: "init"
   branch develop
   checkout develop
   commit id: "setup project"
   branch feature/login
   checkout feature/login
   commit id: "feat: add login screen"
   commit id: "feat: add jwt auth"
   checkout develop
   merge feature/login
   branch feature/courses
   checkout feature/courses
   commit id: "feat: add course list"
   checkout develop
   merge feature/courses
   branch chore/update-docs
   checkout chore/update-docs
   commit id: "docs: add api documentation"
   checkout develop
   merge chore/update-docs
   branch test/sprint-1
   checkout test/sprint-1
   commit id: "test: integration tests"
   commit id: "fix: resolve test failures"
   checkout develop
   merge test/sprint-1
   branch release/1.0.0
   checkout release/1.0.0
   commit id: "release: prepare v1.0.0"
   checkout main
   merge release/1.0.0 tag: "v1.0.0"
   checkout develop
   merge release/1.0.0
   checkout main
   branch hotfix/fix-cors
   checkout hotfix/fix-cors
   commit id: "hotfix: fix cors config"
   checkout main
   merge hotfix/fix-cors tag: "v1.0.1"
   checkout develop
   merge hotfix/fix-cors
```

---

### Tipos de Ramas

| Rama           | Propósito                                    | Nace de     | Se fusiona en         |
| -------------- | -------------------------------------------- | ----------- | --------------------- |
| `main`         | Código estable en producción                 | —           | —                     |
| `develop`      | Integración de funcionalidades en desarrollo | `main`      | `release/*`, `main`   |
| `feature/*`    | Desarrollo de nuevas funcionalidades         | `develop`   | `develop`             |
| `chore/*`      | Mantenimiento (docs, configs, dependencias)  | `develop`   | `develop`             |
| `bugfix/*`     | Corrección de bugs no urgentes en desarrollo | `develop`   | `develop`             |
| `test/*`       | Pruebas de integración o experimentación     | `develop`   | `develop`             |
| `hotfix/*`     | Correcciones urgentes en producción          | `main`      | `main`, `develop`     |
| `release/*`    | Preparación de una versión para producción   | `develop`   | `main`, `develop`     |

---

### Cómo crear ramas

```bash
# Desde develop, crear una feature
git checkout develop
git pull origin develop
git checkout -b feature/course-enrollment

# Mantenimiento (docs, configs, refactor de estructura)
git checkout develop
git pull origin develop
git checkout -b chore/update-dependencies

# Corrección de bug no urgente
git checkout develop
git pull origin develop
git checkout -b bugfix/fix-jwt-expiration

# Desde develop, crear una rama de test
git checkout develop
git checkout -b test/sprint-1

# Desde main, crear un hotfix
git checkout main
git pull origin main
git checkout -b hotfix/fix-cors-gateway

# Desde develop, crear un release
git checkout develop
git checkout -b release/1.0.0
```

---

### Convención de nombres para ramas

Usa **kebab-case** (minúsculas separadas por guiones) después del prefijo.

- `feature/student-dashboard`          (correcto)
- `feature/assignment-submission-api`  (correcto)
- `chore/update-spring-dependencies`   (correcto)
- `chore/add-api-documentation`        (correcto)
- `bugfix/fix-null-pointer-product`    (correcto)
- `hotfix/fix-cors-gateway`            (correcto)
- `release/1.2.0`                      (correcto)
- `test/sprint-3`                      (correcto)

- `feature/changes`                    (incorrecto — muy vago)
- `mi-rama`                            (incorrecto — falta prefijo)
- `feature/StudentDashboard`           (incorrecto — no usar camelCase)
- `feat/login`                         (incorrecto — usar feature, no feat)

---

### Flujo completo de trabajo — Ejemplo

```bash
# 1. Actualizar develop
git checkout develop
git pull origin develop

# 2. Crear feature
git checkout -b feature/course-enrollment

# 3. Trabajar y hacer commits
git add .
git commit -m "feat: add course enrollment endpoint"

git add .
git commit -m "feat: add enrollment screen"

# 4. Push de la rama
git push origin feature/course-enrollment

# 5. Crear Pull Request → develop
# Esperar code review y aprobación

# 6. Merge a develop (vía PR)
# 7. Eliminar la rama feature
git branch -d feature/course-enrollment
```

---

## Versionamiento

Seguimos un esquema inspirado en **SemVer** (Semantic Versioning) con formato `MAJOR.MINOR.PATCH`. Se omiten ceros innecesarios para mantenerlo limpio si es necesario, aunque se recomienda la estructura completa.

```
MAJOR.MINOR.PATCH
```

| Segmento | Cuándo incrementar                                         | Ejemplo               |
| -------- | ---------------------------------------------------------- | --------------------- |
| `MAJOR`  | Rediseño grande o cambio que rompe compatibilidad          | `1.x` → `2.0.0`       |
| `MINOR`  | Nueva funcionalidad compatible hacia atrás                 | `1.0.0` → `1.1.0`     |
| `PATCH`  | Correcciones de errores en producción (hotfix)             | `1.1.0` → `1.1.1`     |

### Versiones Pre-release

Para versiones que aún no son estables, se añade un sufijo con guión:

| Versión          | Rama                | Tag               | Significado                              |
| ---------------- | ------------------- | ----------------- | ---------------------------------------- |
| `1.0.0-alpha.1`  | `release/1.0.0-alpha` | `v1.0.0-alpha.1`  | En desarrollo, funcional pero incompleta |
| `1.0.0-beta.1`   | `release/1.0.0-beta`  | `v1.0.0-beta.1`   | Estable para pruebas                     |
| `1.0.0`          | `release/1.0.0`       | `v1.0.0`          | Versión final                            |

### Ciclo de vida de una versión

```mermaid
graph LR
    A[alpha] --> B[beta]
    B --> C[release candidate]
    C --> D[stable]
    D --> E[maintenance / patch]
```

```bash
# 1. Crear rama de release desde develop
git checkout develop
git checkout -b release/1.0.0-alpha

# 2. Commit de preparación
git commit -m "release: prepare v1.0.0-alpha"

# 3. Mergear a main y taggear
git checkout main
git merge release/1.0.0-alpha
git tag -a v1.0.0-alpha -m "release: v1.0.0-alpha"
git push origin main --tags

# 4. Mergear de vuelta a develop
git checkout develop
git merge release/1.0.0-alpha
```

---

## Estándares de Código

### Frontend (Angular / Web)

Actualmente en migración hacia Angular. Se recomienda el uso de **Prettier** y **EditorConfig** para mantener un estilo consistente. 

### Backend (Spring Boot)

El backend utiliza **Java 21** y **Spring Boot 3.2**. Se recomienda seguir las convenciones estándar de Java:

- Nombres de clases en **PascalCase**
- Nombres de métodos y variables en **camelCase**
- Constantes en **UPPER_SNAKE_CASE**
- Paquetes en **minúsculas**
- Indentación con 4 espacios (convención Java estándar)
- Uso de **Lombok** para reducir código repetitivo (getters, setters, constructores).

---

Para más información sobre Git Glow, consulta la documentación interna del equipo.

<!-- Los scripts de instalación de hooks se encuentran en la carpeta scripts/ y están diferenciados por plataforma: macos-git-glow.sh y windows-git-glow.ps1. -->
