# 🛠️ Guía para Contribuir a KApp

## 🤝 ¿Quién puede contribuir?

Este proyecto es parte de K-Forge y está restringido a miembros autorizados.  
Si formas parte del equipo de desarrollo, sigue estas pautas para contribuir al código.

---

## 📌 Flujo de trabajo y convención de commits

Para mantener un historial limpio y comprensible, seguimos la convención **Git Glow** y el flujo de trabajo basado en ramas. Usa el siguiente formato para tus commits:

```
type: short message in english
```

> Todos los commits deben estar **en inglés**, en minúscula y sin corchetes.

---

### 🧾 Tipos de Commits

El _type_ indica la naturaleza del cambio realizado.

- **feat** — Nueva funcionalidad
- **fix** — Corrección de errores
- **chore** — Tareas de mantenimiento del proyecto
- **release** — Preparación de una nueva versión
- **hotfix** — Corrección urgente en producción
- **docs** — Cambios en documentación
- **refactor** — Reestructuración de código sin cambiar funcionalidad
- **test** — Agregar o modificar tests

---

### ✅ Ejemplos Correctos

- `feat: add login screen`
- `fix: resolve jwt token expiration bug`
- `chore: update spring boot dependencies`
- `docs: add branching guide to contributing`
- `refactor: extract user validation logic`
- `release: prepare version 1.0.0`

---

### ⛔ Ejemplos Incorrectos

- `update` → No describe nada útil
- `[FEAT][UI] Agregar pantalla` → No usar corchetes ni español
- `Fix bug` → Debe ser minúscula: `fix: ...`
- `cambios varios` → Muy ambiguo y en español

---

## 🌿 Ramas (Branching)

Seguimos **Git Flow**. Todas las ramas deben partir de `develop` (excepto `hotfix/*`, que parte de `main`).

### Diagrama de ramas

```mermaid
gitGraph
    commit id: "init"
    branch develop
    commit id: "setup project"
    branch feature/login
    commit id: "feat: add login screen"
    commit id: "feat: add jwt auth"
    checkout develop
    merge feature/login
    branch feature/courses
    commit id: "feat: add course list"
    checkout develop
    merge feature/courses
    branch test/sprint-1
    commit id: "test: integration tests"
    commit id: "fix: resolve test failures"
    checkout develop
    merge test/sprint-1
    branch release/1.0.0
    commit id: "release: prepare v1.0.0"
    checkout main
    merge release/1.0.0 tag: "v1.0.0"
    checkout develop
    merge release/1.0.0
    checkout main
    branch hotfix/fix-cors
    commit id: "hotfix: fix cors config"
    checkout main
    merge hotfix/fix-cors tag: "v1.0.1"
    checkout develop
    merge hotfix/fix-cors
```

### Tipos de ramas

| Rama        | Base      | Se mergea a          | Uso                              |
| ----------- | --------- | -------------------- | -------------------------------- |
| `main`      | —         | —                    | Producción estable               |
| `develop`   | `main`    | `main` (via release) | Integración de features          |
| `feature/*` | `develop` | `develop`            | Nueva funcionalidad              |
| `test/*`    | `develop` | `develop`            | Pruebas de integración / QA      |
| `hotfix/*`  | `main`    | `main` + `develop`   | Corrección urgente en producción |
| `release/*` | `develop` | `main` + `develop`   | Preparación de una versión       |

### Crear una rama

```bash
# Feature nueva
git checkout develop
git pull origin develop
git checkout -b feature/login-screen

# Hotfix urgente
git checkout main
git pull origin main
git checkout -b hotfix/fix-jwt-expiration

# Test / QA
git checkout develop
git pull origin develop
git checkout -b test/sprint-1

# Release
git checkout develop
git checkout -b release/1.2.0
```

### Nombrar ramas

Usa kebab-case descriptivo después del prefijo:

✅ **Correcto:**

- `feature/student-dashboard`
- `feature/assignment-submission-api`
- `hotfix/fix-cors-gateway`
- `test/sprint-3`
- `release/2.0.0`

⛔ **Incorrecto:**

- `feature/changes` → Muy vago
- `mi-rama` → Sin prefijo
- `feature/StudentDashboard` → No usar camelCase
- `feat/login` → Usar `feature`, no `feat`

### Flujo completo (ejemplo)

```bash
# 1. Crear la rama desde develop
git checkout develop
git pull origin develop
git checkout -b feature/course-enrollment

# 2. Hacer commits siguiendo la convención
git add .
git commit -m "feat: add course enrollment endpoint"

git add .
git commit -m "feat: add enrollment screen"

# 3. Push de la rama
git push origin feature/course-enrollment

# 4. Crear Pull Request en GitHub → develop
# 5. Tras aprobación, mergear y eliminar la rama
```

---

## 🏷️ Versionamiento

Usamos un esquema inspirado en **SemVer** con formato `MAJOR.MINOR` y `.PATCH` solo cuando es necesario. Se omiten ceros innecesarios para mantenerlo limpio.

```
MAJOR.MINOR.PATCH
```

- **MAJOR** — Rediseño grande o cambio que rompe compatibilidad
- **MINOR** — Nueva funcionalidad
- **PATCH** — Corrección de errores (solo aparece si hace falta)

### Versiones estables

| Versión | Rama          | Tag      | Significado             |
| ------- | ------------- | -------- | ----------------------- |
| `1.0`   | `release/1.0` | `v1.0`   | Primera versión estable |
| `1.1`   | `release/1.1` | `v1.1`   | Nueva feature           |
| `1.1.1` | `hotfix/*`    | `v1.1.1` | Hotfix sobre producción |
| `2.0`   | `release/2.0` | `v2.0`   | Rediseño grande         |

### Pre-releases (alpha, beta)

Para versiones que aún no son estables, se añade un sufijo con guión:

| Versión     | Rama                | Tag          | Significado                              |
| ----------- | ------------------- | ------------ | ---------------------------------------- |
| `1.0-alpha` | `release/1.0-alpha` | `v1.0-alpha` | En desarrollo, funcional pero incompleta |
| `1.0-beta`  | `release/1.0-beta`  | `v1.0-beta`  | Estable para pruebas                     |
| `1.0`       | `release/1.0`       | `v1.0`       | Versión final                            |

Si se necesitan múltiples iteraciones: `v1.0-alpha.2`, `v1.0-beta.3`, etc.

### Ciclo de vida de una versión

```
alpha → beta → release estable
```

```bash
# 1. Crear rama de release desde develop
git checkout develop
git checkout -b release/1.0-alpha

# 2. Commit de preparación
git commit -m "release: prepare v1.0-alpha"

# 3. Mergear a main y taggear
git checkout main
git merge release/1.0-alpha
git tag -a v1.0-alpha -m "release: v1.0-alpha"
git push origin main --tags

# 4. Mergear de vuelta a develop
git checkout develop
git merge release/1.0-alpha
```

### Cuándo incrementar cada número

| Cambio                             | Ejemplo                   | Incremento             |
| ---------------------------------- | ------------------------- | ---------------------- |
| Nuevo módulo, pantalla o API       | Agregar sistema de notas  | Minor: `1.0` → `1.1`   |
| Bug fix en producción              | Corregir error de login   | Patch: `1.1` → `1.1.1` |
| Rediseño de arquitectura           | Migrar a microservicios   | Major: `1.x` → `2.0`   |
| Múltiples features nuevas (sprint) | Dashboard + inscripciones | Minor: `1.1` → `1.2`   |

---

📚 Para más información sobre Git Glow, visita [GitHub - Git Glow](https://github.com/arthurdenner/git-glow) o consulta la documentación interna del equipo.

---

<!-- Los scripts de instalación de hooks se encuentran en la carpeta scripts/ y están diferenciados por plataforma: macos-git-glow.sh y windows-git-glow.ps1. -->
