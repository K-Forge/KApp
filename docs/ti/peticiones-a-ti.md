---
title: Lo que KApp le pide a la Dirección de TI
subtitle: Inicio de sesión con Entra ID, lectura de SINU y un lugar donde correr. Octubre de 2026.
lang: es-CO
---

# Para qué es este documento

Reúne lo que KApp le pide a la Dirección de TI de la Fundación Universitaria Konrad Lorenz, lo que no le pide, por qué está planteado así y las preguntas técnicas que quedan abiertas.

# Qué es KApp hoy

KApp es la aplicación móvil de la comunidad Konrad Lorenz, para Android y iOS, con un portal web de administración. La construye el club K-Forge como trabajo de grado y se presenta en el segundo semestre de 2027.

- **El mapa del campus**: los edificios de la Sede Principal dibujados piso por piso a partir de los planos de evacuación y medidos en sitio, con búsqueda de salones e indicaciones para llegar.
- **El horario del día y de la semana** de cada estudiante o profesor, con su salón y el enlace al mapa.
- **El semáforo académico**: el pensum completo pintado por estado, y un plan personal para decidir qué ver en los semestres siguientes.
- **Funciones de comunidad** en camino: notificaciones, voz estudiantil (quejas y sugerencias), reportes de daños y objetos perdidos con recepción.

El backend son microservicios en Java y Spring Boot, con MongoDB, que se van a desplegar en Kubernetes. Hoy corre en un servidor de desarrollo y en los computadores del equipo.

**El principio que guía todo lo que sigue: KApp lee SINU y nunca lo copia.** El horario, los estados de las materias y el pensum se leen de SINU cuando el estudiante abre la app, no se guardan, y nada se escribe de vuelta. Lo único propio de KApp es el mapa, el plan personal de cada estudiante y las funciones de comunidad.

# Qué se le pide a TI

## 1. Inicio de sesión con la cuenta institucional (Entra ID)

Que estudiantes, profesores y administrativos entren a KApp con su cuenta `@konradlorenz.edu.co`, la misma de Microsoft 365. Para eso hace falta un **registro de aplicación** en el directorio de la universidad:

- Protocolo **OpenID Connect** (OIDC), con código de autorización y PKCE, que es el flujo que Microsoft recomienda para aplicaciones móviles.
- Permisos básicos: `openid`, `profile` y `email`. Nada de correo, archivos ni calendario.
- Tres plataformas en el mismo registro: Android, iOS y el portal web.
- **Tres roles de la aplicación** que digan si la persona es estudiante, profesor o administrativo: `Student`, `Professor` y `Staff`, asignados a los grupos del directorio que ya existan. Es lo más sencillo para TI y no exige tocar ningún atributo. KApp los usa para decidir qué mostrar; no dan permisos de administración: esos los asigna a mano un administrador de KApp.
- Ningún secreto de cliente: la app recibe un token de identidad de Microsoft y KApp lo verifica contra las claves públicas del directorio.

## Por qué OIDC y no SAML

La práctica institucional es integrar por SAML, porque es lo que usan Moodle y la mayoría de aplicaciones institucionales. KApp prefiere OIDC por tres razones:

1. **SAML necesita un navegador.** Fue pensado para iniciar sesión en aplicaciones web. En una app nativa obligaría a incrustar una vista web, que es peor para el usuario y menos segura, porque la persona no puede verificar dónde está escribiendo su contraseña (lo desaconseja el estándar RFC 8252).
2. **Una aserción SAML no sirve para llamar a una API.** Es un documento XML que afirma una identidad ante un sitio web. Para usarla contra la API de KApp habría que convertirla en otra cosa, con maquinaria que solo existe para salvar esa diferencia.
3. **Es lo que Microsoft recomienda para este caso.** Sus bibliotecas para móviles (MSAL) implementan OIDC con PKCE como camino documentado.

Además, con OIDC KApp deja de guardar contraseñas de miembros de la universidad, y cada inicio de sesión prueba que la cuenta sigue activa. Si la política institucional exigiera SAML, KApp lo implementaría; se plantea como pregunta, no como condición.

## 2. Lectura de SINU, solo de quien inició sesión

Que KApp pueda leer de SINU, **para la persona que inició sesión y para nadie más**, lo siguiente:

- Su programa, la versión de su pensum y su nivel actual.
- Su horario del periodo, en la forma del reporte `PACR42_GWT`: cada materia con su grupo, su docente, su sede, y cada rango de fechas con su aula y sus bloques de 45 minutos.
- El estado de cada materia de su pensum: aprobada, en curso, pendiente, perdida o aplazada, con **la lista de estados que usa SINU y lo que significa cada uno**.
- La nota, **solo si TI lo considera viable**. Es opcional: el semáforo funciona sin ella.
- El catálogo de programas y pensums con sus materias, créditos, niveles y prerrequisitos. Es información publicada.
- El banco de electivas de cada semestre: qué materias se ofrecen para llenar los cupos de electiva.

**La forma la decide TI**: un servicio web, una vista de base de datos o un archivo exportado cada noche. KApp ya tiene su modelo con la forma de SINU, así que cualquiera de las tres es un adaptador. Las tablas que se esperan, con sus campos y un ejemplo inventado, están en [`docs/api/sinu/README.md`](../api/sinu/README.md); qué lee KApp de SINU y qué no debe guardar nunca, en [`docs/SINU-ALIGNMENT.md`](../SINU-ALIGNMENT.md).

> Mientras no haya acceso, KApp funciona con un SINU de prueba con datos inventados de la misma forma. Si para 2027-2 no hubiera acceso, se presenta con él.

## 3. Un lugar donde correr KApp

Un servidor de la universidad, o un espacio en uno, para desplegar KApp en **Kubernetes**, que deja el despliegue repetible y fácil de mover. Lo que hace falta está en el anexo: como mínimo 4 vCPU, 8 GB de RAM y 60 GB de disco. También un nombre de dominio con certificado TLS, y acordar quién opera el servidor y cómo se respalda.

Si no fuera posible, el plan B para la presentación es correr el clúster en un computador del equipo.

## 4. Las cuentas para notificaciones

Las notificaciones push en iPhone y la distribución de la app por TestFlight exigen una cuenta de Apple Developer, que cuesta 99 dólares al año. La pregunta es si la universidad ya tiene una que KApp pueda usar, o si se puede financiar. Sin ella, iOS funciona con avisos dentro de la app. Android no la necesita, pero sus notificaciones pasan por Firebase, que pide un proyecto de Google: K-Forge puede crearlo, salvo que la universidad prefiera tenerlo bajo su propia cuenta.

# Qué no se pide

- **Ninguna escritura en SINU.** KApp no inscribe materias ni cambia nada; eso sigue en SINU, por los canales de la universidad.
- **Datos financieros**: pagos, deudas, matrículas.
- **Documentos de identidad, teléfonos ni códigos de estudiante.** KApp no los guarda.
- **Datos de otros estudiantes.** Cada persona ve lo suyo; ni los administradores de KApp leen la información académica de nadie.
- **Contraseñas.** Con Entra ID, KApp no maneja contraseñas de la comunidad.
- **Promedios.** La nota de cada materia es opcional y de solo lectura.

# Datos personales (Ley 1581 de 2012)

Conviene que TI sepa qué datos personales guarda KApp por su cuenta y con qué propósito:

| Dato | Para qué | Cuánto tiempo |
|---|---|---|
| Nombre y correo institucional | Identificar la cuenta. Llegan de Microsoft | Mientras exista la cuenta |
| Plan personal de materias | Planear los semestres siguientes | Mientras exista la cuenta |
| Pase de visitante: nombre y documento | Registro de quién estuvo en el edificio. Pendiente de lo que diga recepción | 30 días, se borra solo |
| Publicaciones de la voz estudiantil | Quejas y sugerencias. El autor decide si va con su nombre o con su programa | A definir con la universidad |
| Historial de objetos perdidos | Saber quién entregó y quién reclamó cada objeto | A definir con la universidad |
| Fotos de daños y de objetos | Ubicar el daño o reconocer el objeto. Sin metadatos de ubicación; nunca documentos ni tarjetas | A definir con la universidad |

Lo académico no aparece en esta tabla porque KApp no lo guarda: lo lee de SINU en cada consulta.

# Preguntas técnicas para TI

1. ¿OpenID Connect es aceptable para el registro en Entra ID, o la política institucional exige SAML?
2. ¿Qué atributo del directorio distingue a un estudiante, un profesor y un administrativo? Lo más sencillo para TI son tres roles de la aplicación (`Student`, `Professor`, `Staff`) asignados a grupos del directorio; no hace falta tocar ningún atributo.
3. ¿Una misma cuenta puede ser a la vez estudiante y profesor, por ejemplo un monitor o un egresado que dicta clase? ¿O cada papel tiene su propia cuenta, con su propio código?
4. ¿Qué sistema de identidad usa SINU, y cómo se relaciona una cuenta de Microsoft con el registro de una persona en SINU? ¿Por el correo institucional?
5. ¿En qué forma puede SINU entregar esos datos (servicio web, vista o archivo nocturno), y cada cuánto se actualizan?
6. En el horario, una electiva aparece como "Electiva N", con su profesor pero sin el nombre de la materia que se cursa. ¿SINU guarda en algún campo cuál materia del banco de electivas tomó el estudiante?
7. ¿Qué lineamientos institucionales debe seguir una aplicación como KApp?
8. ¿Hay un servidor o un espacio donde desplegar KApp en Kubernetes? ¿Quién lo opera y cómo se respalda?
9. ¿La universidad tiene una cuenta de Apple Developer que KApp pueda usar? ¿Y una cuenta de Google donde crear el proyecto de Firebase para las notificaciones de Android?
10. ¿La red del campus sale a internet por una sola dirección pública o por un proxy? KApp limita los intentos de inicio de sesión por dirección; si todo el campus comparte una, hay que saber qué encabezado trae la dirección real del celular.
11. ¿Hay una política de tratamiento de datos o un aviso de privacidad institucional que KApp deba adoptar?

# Anexo. Hardware para correr KApp en Kubernetes

|  | Mínimo | Óptimo |
|---|---|---|
| CPU | 4 vCPU | 8 vCPU en un nodo, o 3 nodos de 4 vCPU |
| RAM | 8 GB (KApp usa unos 4,5 GB) | 16 a 32 GB |
| Disco | 60 GB SSD | 200 GB SSD, con respaldos fuera del servidor |
| Base de datos | MongoDB Atlas, capa gratuita | MongoDB propio con réplica de 3 miembros, o Atlas de pago |
| Fotos | Almacenamiento en el mismo disco | Un volumen propio, con respaldo |
| Red | Una dirección y un certificado TLS | El dominio de la universidad, con TLS |

La estimación cuenta los servicios de KApp con sus límites de memoria actuales (entre 250 y 350 MB por servicio), el portal, el almacenamiento de fotos y los componentes de Kubernetes.
