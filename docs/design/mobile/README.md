# KApp · Diseño móvil

Maquetas de **Login** e **Inicio** para los dos clientes nativos. Estáticas: sirven para
escribir la vista mirándolas, no para navegar.

## Archivos

| Archivo | Qué es |
|---|---|
| `Main.dc.html` | Login · iOS (390×844) |
| `LoginAndroid.dc.html` | Login · Android (360×800) |
| `HomeIOS.dc.html` | Inicio · iOS (390×844) |
| `HomeAndroid.dc.html` | Inicio · Android (360×800) |
| `EstadosLogin.dc.html` | Enviando, 401, 403, sin conexión |
| `EstadosHome.dc.html` | Cargando, hoy sin clases, sin horario (404) |
| `Tokens.dc.html` | Paleta, semántica, tipografía, contraste |
| `canvas.json` | Posición de cada lámina y las notas al margen |

Cada lámina se abre sola en el navegador. Son estáticas y se editan a mano: son la fuente, y
`canvas.json` guarda dónde va cada una y las notas al margen.

## Reglas de color

Los siete colores salen de [`docs/K-COLORS.md`](../../K-COLORS.md). Tres niveles:

- **Morado `#522567`** — marca. La banda superior y el campo del login.
- **Rosa `#D51A65`** — acciones, y nada más. Botones, pestaña activa, enlaces.
- **Color de materia** — lo manda la API en `ClassOccurrence.color`. El cliente lo pinta,
  no lo elige.

Nunca blanco sobre el verde `#C9D329` (1.5:1). Sobre verde va morado.

## Pendientes de backend

1. `auth.openapi.yaml` no tiene refresh token y `expiresIn` es de una hora para todos. La
   maqueta deja la sesión del estudiante guardada: hace falta token largo o refresh para
   `ROLE_STUDENT`, dejando la expiración corta para `ROLE_ADMIN`.
2. Si `schedule-service` puede devolver `#D51A65` como color de una materia, el rosa deja de
   significar "tocable". Conviene acotar la paleta de materias a los seis colores no-rosa.
