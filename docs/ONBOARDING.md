# Levantar KApp en tu máquina

Para Iván, Alejandro, Santiago y quien entre después. Unos diez minutos, y no tienes que
instalar MongoDB.

**Tú trabajas el front.** El backend lo levantas para tener contra qué programar, no para
modificarlo.

El backend corre en tu computador; **la base de datos vive en la nube y la compartimos los
seis**. Eso significa que ves los mismos datos que todos: si alguien carga un pensum, lo tienes
tú también.

---

## Lo que Brian te tiene que pasar, en privado

Dos cosas, y las manda **por mensaje directo — nunca al grupo**:

1. **Cinco líneas** que empiezan por `MONGO_` y terminan en `.mongodb.net/...`. Son las que
   conectan con la base compartida y llevan contraseñas dentro.
2. **Tu línea** del archivo de cuentas: tu correo `@kforge.dev` y tu contraseña. Ese correo no
   existe de verdad — es una identidad de desarrollo, y sólo sirve para entrar al portal.

Si te llegaron por el grupo, dile que las rote. Un mensaje en un chat queda en el historial de
todos para siempre.

---

## 1. Lo que necesitas instalado

| | Para qué | Cómo |
|---|---|---|
| **Docker Desktop** | Todo. Los servicios corren en contenedores | <https://docker.com/products/docker-desktop> |
| **Git** | Bajar el código | Viene con macOS; en Windows, <https://git-scm.com> |

**No necesitas Java, ni Maven, ni MongoDB.** Todo eso vive dentro de los contenedores.

Abre Docker Desktop y espera a que el icono de la ballena deje de moverse. Si no está corriendo,
nada de lo de abajo funciona.

## 2. Bajar el repositorio

```bash
git clone https://github.com/K-Forge/KApp.git
cd KApp
scripts/install-git-hooks.sh
```

El último comando activa un hook que revisa cada commit antes de guardarlo. Si el mensaje o la
rama no siguen las reglas, te dice qué está mal en vez de dejarlo pasar hasta la revisión.

**Lee [CONTRIBUTING.md](../CONTRIBUTING.md) antes de tu primera rama.** Lo importante:

- **Todo sale de `develop`.** Nunca trabajas en `main` ni commiteas directo en `develop`.
- Tu rama se llama `feature/<descripcion-en-kebab-case>`, o `bugfix/...` si corriges algo.
- Los commits van en inglés y minúsculas, con uno de ocho tipos: `feat: add login screen`.
- Tu trabajo entra por una Pull Request hacia `develop`, con una aprobación y el CI en verde.

```bash
git switch develop && git pull
git switch -c feature/<lo-que-vas-a-hacer>
```

Tú no vas a tocar el backend: lo levantas para tener contra qué programar.

## 3. Generar tus propios secretos

```bash
scripts/generate-dev-secrets.sh > app/backend/microservices/.env
```

Esto crea tu archivo de configuración con contraseñas generadas **en tu máquina**. Son tuyas y
sólo tuyas: no tienen que coincidir con las de nadie.

> Ese archivo, `.env`, nunca se sube a git. Ya está en el `.gitignore` y así se queda.

## 4. Apuntar a la base compartida

Abre `app/backend/microservices/.env` con cualquier editor. Busca estas cinco líneas —están
juntas, más o menos a mitad del archivo:

```
MONGO_AUTH_URI='mongodb://...'
MONGO_USER_URI='mongodb://...'
MONGO_SEMAPHORE_URI='mongodb://...'
MONGO_SCHEDULE_URI='mongodb://...'
MONGO_MAP_URI='mongodb://...'
```

**Reemplázalas por las cinco que te pasó Brian**, tal cual, con las comillas simples incluidas.
Las suyas empiezan por `mongodb+srv://` y llevan `.mongodb.net` en medio.

**No toques nada más del archivo.** Lo demás lo generó el script en tu máquina y es tuyo.

> **Las comillas simples importan.** Esas líneas llevan un `&`, y sin comillas cualquier script
> las lee mal — en silencio, dejando la variable vacía. Si copias y pegas la línea entera desde
> el mensaje de Brian, ya vienen puestas.

## 5. Arrancar

```bash
cd app/backend/microservices
docker compose --profile academic --profile map --profile dev up -d
```

La primera vez tarda: está descargando y construyendo siete imágenes. Diez o quince minutos, una
sola vez.

No hay base de datos que levantar: todos los servicios usan la de la nube. Si te falta alguna de
las cinco cadenas, Compose no arranca y te dice cuál.
`dev` añade el portal de administración.

Mira cómo va:

```bash
docker compose ps
```

Cuando las ocho líneas digan `healthy`, está listo. Si alguna dice `unhealthy` o desaparece, salta
a [Si algo falla](#si-algo-falla).

## 6. Comprobar que funciona

Abre <http://localhost:4300> y entra con el correo y la contraseña que te pasó Brian.

Si ves el portal con menú a la izquierda —Users, Buildings, Programs…— **ya está**. Estás viendo
la misma base que todos.

---

## Lo que puedes abrir

| Dirección | Qué es |
|---|---|
| <http://localhost:4300> | El portal de administración |
| <http://localhost:8080> | La API. Es la única puerta al backend |
| <http://localhost:8080/swagger-ui.html> | La documentación de la API, navegable |

Si estás construyendo pantallas del móvil y no quieres levantar el backend entero:

```bash
docker compose --profile mock up -d
```

Eso arranca cinco servidores que responden lo que dice el contrato, con datos de ejemplo, en los
puertos 4010 a 4014. Pesan casi nada y no tocan la base compartida.

---

## Tres cosas que NO debes hacer

**No corras `scripts/create-dev-accounts.sh`.** Las cuentas ya existen en la base compartida. Ese
script las borra y las vuelve a crear con contraseñas nuevas, y dejaría fuera a los otros cinco.
Sólo Brian, y sólo si hay que rehacerlas.

**No borres datos de la base compartida.** Es la misma para los seis, no tiene copia de
seguridad, y lo que borres se lo borras a todos. Si hay que hacerlo, avisa primero en el grupo.
Para apagar tus contenedores:

```bash
docker compose --profile full --profile dev down
```

**No subas tu `.env`.** Está ignorado por git, pero no lo fuerces.

---

## Trabajar sin internet

No se puede. La base de datos es la compartida en la nube y no hay una local a la que cambiarse:
sin conexión, el backend no arranca. Lo que sí funciona sin internet, una vez descargadas las
imágenes, son los mocks:

```bash
docker compose --profile mock up -d
```

Sirven las respuestas de ejemplo de los contratos, que es suficiente para seguir armando pantallas.
El porqué de esta decisión está en [ADR 0009](adr/0009-atlas-is-the-only-development-database.md).

---

## Si algo falla

### Algún servicio dice `unhealthy`, o desaparece de la lista

Mira qué dice:

```bash
docker compose logs --tail=40 auth-service
```

Cambia `auth-service` por el que falle: `user-service`, `semaphore-service`, `schedule-service`,
`map-service`.

### `bad auth : authentication failed`

Una de las cinco líneas quedó mal pegada. Compárala carácter por carácter con la que te mandaron
—suele ser una cortada a la mitad, o sin las comillas del final.

### `Timed out ... no primary` o `Unable to look up TXT record`

Tu red bloquea las consultas DNS que necesita la conexión. Pasa en algunas redes de campus y en
portales cautivos. Prueba con datos del móvil; si funciona, es eso. Dile a Brian y te pasa una
cadena alternativa.

### `Cannot connect to the Docker daemon`

Docker Desktop no está abierto. Ábrelo y espera a la ballena.

### El portal carga pero no entra

Comprueba que estás en <http://localhost:4300> y no en otro puerto, y que el correo es el de
desarrollo: `ivan@kforge.dev`, **no** tu dirección de la universidad. Esas cuentas no existen
como correo real; son identidades sólo para desarrollo.

### Todo va lentísimo y el ventilador suena

Estás levantando más de lo que necesitas. Si sólo trabajas en el mapa:

```bash
docker compose --profile full --profile dev down
docker compose --profile mock up -d
```

---

## Cuando cambies de rama o alguien suba código

```bash
git pull
cd app/backend/microservices
docker compose --profile academic --profile map --profile dev build
docker compose --profile academic --profile map --profile dev up -d
```

Sin el `build`, Docker arranca la imagen vieja y el servicio responde 404 en cosas que juras que
existen. Es el error más común del proyecto.

---

## Dónde seguir

- [`RUNBOOK.md`](RUNBOOK.md) — todos los comandos, perfiles y problemas conocidos
- [`docs/api/`](api/) — los contratos de la API. **Son la verdad**: si el código y el
  contrato no coinciden, el contrato manda
- [`PROGRESS.md`](PROGRESS.md) — qué está hecho y qué falta
