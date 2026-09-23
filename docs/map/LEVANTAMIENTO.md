# Levantamiento del campus

Lo que dicen las 68 fotos que tomó Brian de los planos de evacuación (**PE**), las placas
informativas (**PI**) y los planos de ruta sanitaria (**RS**), antes de diseñar nada.

La transcripción es fiel a cada fuente y **no está forzada a ningún modelo de datos**. El diseño
del mapa sale de aquí, después de revisarlo juntos.

- **Un documento por edificio** en [`levantamiento/`](levantamiento/): lo que dice cada fuente
  piso por piso, en qué se contradicen y qué falta.
- **Este documento:** el resumen, lo que vale para todo el campus y la **lista de lo que hay que ir
  a caminar**.

## Cobertura

| Edificio | Fotos | Pisos | Qué hay | Estado |
|---|---:|---|---|---|
| [EC · Edificio Central](levantamiento/ec.md) | 24 | Sótano, P1–P8 | PE, RS y PI en casi todos los pisos | Ala central completa. **Alas norte y sur, a mano** |
| [CPC 1 · Centro de Psicología Clínica 1](levantamiento/cpc-1.md) | 15 | P1–P5 | PE, PI y RS en todos | El más completo |
| [JAAB · Centro de Investigaciones Juan Alberto Aragón Bateman](levantamiento/jaab.md) | 12 | Sótano, P1, MEZZ, P2–P6 | PE de 5 niveles; PI de todos | **P1, P3 y sótano, a mano** |
| [BI · Bienestar Institucional](levantamiento/bi.md) | 9 | P1–P4, terraza (P5) | PE y PI del edificio original | **La expansión con aulas y laboratorios, a mano** |
| [MU · Casa Medio Universitario](levantamiento/mu.md) | 5 | P0, P1, P2 | PE y RS, sin PI | Se puede dibujar |
| [EA · Edificio Administrativo](levantamiento/ea.md) | 3 | P2–P4 | Solo PI | **Todo a mano** |
| **RH · Edificio de Recursos Humanos** | 0 | ? | — | **Sin visitar.** Casa de patrimonio, sin conexión interna con el EC |
| **CPC 2** | 0 | ? | — | **Sin visitar** |

## Lo que vale para todo el campus

### Qué fuente manda

Brian lo estableció en sitio, y las fotos lo confirman:

1. **PI**, para qué existe y cómo se llama. Es la más actual, aunque **no siempre está completa**.
2. **PE**, para la forma. En casi todos los edificios dibuja la silueta y no el uso: el
   "Auditorio" del EC aparece en los ocho pisos, y las aulas y salas de cómputo del JAAB ya tienen
   otros nombres.
3. **RS**, la más vieja. Pero donde existe (CPC 1, MU, ala central del EC) **es el plano más
   detallado**, y en el EC sus cantidades de salones coinciden con las placas.

### Cómo se identifica un espacio

Hay dos formas y conviven:

- **Por número de puerta:**
  - CPC 1: `101`–`114`, `201`–`211`, `301`–`313`.
  - EC: `301`–`309`, `503-S`… Las puertas del EC llevan el ala como sufijo, aunque las placas
    escriban `S-503`.
- **Por dependencia, sin número:** JAAB, BI, EA y MU. Placas como "Dirección de Revistas
  Científicas", "Gimnasio" o "Departamento de Nómina".

Consecuencia: **muchos espacios no tienen código de puerta.** El sistema necesita identificarlos
igual, y ese identificador no puede mostrarse, por la misma razón que los códigos de materia
inventados de los pensums.

**Tampoco se pueden copiar números de los RS.** Los del CPC 1 numeran los consultorios en secuencia
por todo el edificio (1 a 34), no por puerta.

### La orientación de los planos no es consistente

- **En el EC**, el norte de los PE queda a la izquierda, según las calles. El PE del P8 está girado
  respecto a los demás.
- **En el BI**, la Calle 62 cambia de lado entre el P1 y los demás pisos.
- **En el CPC 1**, el PE del P2 está en otra orientación que su RS.

El mapa tiene que fijar un norte por edificio y **alinear los pisos entre sí**. Si no, la escalera
del P3 aparece en otro lado que la del P2.

### Tres puntos de encuentro

Sirven como referencias del campus:

- **Plazoleta frente al Edificio Central**, sobre la Carrera 9 Bis: EC y CPC 1.
- **Plazoleta de Bomberos:** EC por el sur, BI y MU.
- **Plazoleta CAI Lourdes:** JAAB.

### Una misma dependencia, en varios edificios

- **ClinikLab** aparece en el CPC 1 (P3 y P4) y en el JAAB (P5).
- **"Sala de juntas"** y **"Oficinas de investigadores"** se repiten.

La búsqueda tiene que devolver todas las coincidencias, no la primera.

### Pisos raros

- **JAAB:** un mezzanine entre el P1 y el P2, que según el PE **no tiene ascensor**.
- **MU:** su nivel de entrada es el **P0**.
- **BI:** una terraza que el directorio numera **P5**.
- **Sótanos:** en el EC (parqueadero) y en el JAAB (archivo).
- **EC, P6:** una terraza a la que **solo se llega desde el piso de abajo** por otra escalera.

### Qué clases de espacio hay

Primera lista para el catálogo de tipos, agrupada en las categorías propuestas. Se ajusta en la
revisión:

| Categoría | Lo que aparece en las fotos |
|---|---|
| **Docencia** | Aula o salón · laboratorio · sala de cómputo (Aula de Sistemas, Sala MAC, Sala Cisco, "Cómputo") · auditorio · biblioteca · cámara de Gesell · tramoya |
| **Atención** | Ventanilla (Admisiones, Tesorería, Registro Académico, Call Center) · recepción · sala de espera · consultorio psicológico · consultorio médico · enfermería · fotocopiadora |
| **Oficina** | Oficina · dirección · decanatura · coordinación · sala de juntas · Sala de Fundadores · salón de eventos · archivo |
| **Bienestar y social** | Cafetería · café · *coffee break* · zona social · zona de esparcimiento o de comidas · gimnasio · salas de juego (billar, ping pong, futbolín, Xbox, juegos de mesa) · danza · música · karaoke · terraza |
| **Servicio** | Baño (hombres, mujeres, discapacidad) · cuarto de aseo o poceta · cuarto de TI o rack · planta eléctrica · centro de acopio de residuos · vestier · lockers · parqueadero |
| **Circulación** | Ascensor · banco de ascensores · escalera · escalera de emergencia · escalera exterior · rampa vehicular · pasillo · entrada o salida |

## Lo que hay que ir a caminar

Por edificio. El detalle de cada punto está en su documento.

**EC**

- [ ] Orientación con brújula.
- [ ] **Ala norte, P1–P5**, y su escalera hacia la terraza norte del P6.
- [ ] **Ala sur, P3–P6**, y por dónde se entra desde el ala central.
- [ ] El formato de los números de puerta. Si "S-301 / S-304" es un rango.
- [ ] El P7: si la puerta dice `7-01` o `701`. Ya se sabe que son 13 aulas.
- [ ] El P1: dónde quedan Admisiones, Tesorería y el Call Center.
- [ ] El sótano: si lo usan peatones.

**BI**

- [ ] **La expansión entera,** con su lado (occidente) y su nombre.
- [ ] Las placas de la expansión.
- [ ] Por dónde se cruza del edificio original a la expansión.

**JAAB**

- [ ] **P1, P3 y sótano, a mano.**
- [ ] Qué hay hoy en el mezzanine.
- [ ] En qué recinto queda cada dependencia del P4, P5 y P6.

**CPC 1**

- [ ] Los consultorios del P3: 13 en la placa, 9 en el RS.
- [ ] Las divisiones del P4.
- [ ] La sala de practicantes del P5.

**MU**

- [ ] Que el P2 existe y qué tiene.
- [ ] Los nombres de las oficinas del P1.

**EA**

- [ ] **Todo, a mano.** P1 incluido, y si hay más pisos.

**RH y CPC 2**

- [ ] **Primera visita.**

## Preguntas abiertas

1. **¿Importan los espacios que no son para estudiantes?** Planta eléctrica, centro de acopio,
   rack, cuartos de aseo. Existen, y a un administrativo le sirven, pero en la app de un estudiante
   son ruido. Probablemente se guardan todos y se filtran por categoría.
2. **Los nombres propios de los auditorios y la biblioteca** ("Sonia Fajardo Forero", "Juan Alberto
   Aragón Bateman") se pueden guardar como alias, para que la búsqueda los encuentre por el nombre
   que usa la gente.
3. **¿El mapa debe decir si un nivel es accesible sin escaleras?** El mezzanine del JAAB sin
   ascensor y la escalera exterior del MU son justo el caso en que importa.
