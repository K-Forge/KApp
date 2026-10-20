# Primera entrega · 23-10-2026

Imágenes para `PrimeraEntrega_KApp.pdf` (Desarrollo de Aplicaciones Móviles, solo frontend). El plan y las tareas están en el issue #158.

| Carpeta | Qué va | Quién | Issue |
|---|---|---|---|
| [`capturas/`](capturas/) | Capturas de la app real en modo demo, por pantalla y perfil | Santiago | #163 |
| [`wireframes/`](wireframes/) | Esqueletos en blanco y negro de las 7 pantallas | Iván | #164 |
| [`mockups/`](mockups/) | Diseño final de las 7 pantallas, a partir de la app real | Alejandro | #165 |
| [`mapa-base/`](mapa-base/) | Renders de los planos reales, base del mockup del Mapa | Backend | #162 |

## Reglas para todas las imágenes

- **PNG**, de menos de 1 MB cada una. Nombres en minúsculas, sin tildes ni espacios, con guiones: `inicio-estudiante.png`.
- **Solo datos de la variante demo**, que son inventados. Nunca nombres, códigos, correos ni capturas con datos reales de nadie: el repositorio es público.
- **Las 7 pantallas:** `login`, `inicio`, `semaforo`, `horario`, `perfil`, `invitacion` y `mapa`. **Los perfiles:** `estudiante`, `profesor` y `administrativo`.
- **Cada persona sube sus imágenes en una sola PR** a `develop`, desde una rama `chore/entrega-<lo-que-sube>`, con la etiqueta `area/docs`. Antes del primer commit, corre `scripts/install-git-hooks.sh`.
