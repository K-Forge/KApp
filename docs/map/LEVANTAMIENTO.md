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
| [BI · Bienestar Institucional](levantamiento/bi.md) | 9 | P1–P4, terraza (P5) | PE y PI del ala occidental (bienestar) | **El ala oriental, de salones y laboratorios, a mano** |
| [MU · Casa Medio Universitario](levantamiento/mu.md) | 5 | P0, P1, P2 | PE y RS, sin PI | Se puede dibujar |
| [EA · Edificio Administrativo](levantamiento/ea.md) | 3 | P2–P4 | Solo PI | **Todo a mano** |
| **RH · Edificio de Recursos Humanos** | 0 | ? | — | **Sin visitar.** Casa de patrimonio, sin conexión interna con el EC |
| **CPC 2** | 0 | ? | — | **Sin visitar** |
| **TK · Tienda K** | 0 | ? | — | **Sin visitar.** La tienda de uniformes, en la esquina junto al RH |

## Dónde queda cada edificio

Todos sobre el catastro de Bogotá (Mapa de Referencia de IDECA): la dirección da el predio, y el
predio, las partes del edificio con cuántos pisos tiene cada una. Así quedan en el mapa del campus,
con su forma real, aunque nadie haya dibujado sus pisos.

| Edificio | Dirección | Predio | Pisos según el catastro | Cómo se ubicó |
|---|---|---|---|---|
| EC | Cra. 9 Bis # 62-43 | 008213024019 | 8 y 2 sótanos | La dirección de la universidad |
| RH | Cra. 9A # 62-12 | 008213024004 | 1 | Talento Humano, según la web; en Google Maps, "Casa de Francisco de Paula Vélez" |
| TK | Cra. 9A # 62-02 | 008213024015 | 2 | Google Maps, en la esquina con la Calle 62 |
| EA | Cra. 9A # 62-27 | 008213015038 | 4 | Brian: al frente del RH |
| BI | Cl. 62 # 9-81 a 9-93 | 008213025004, 005 y 006 | 4, y la terraza | Brian: al frente de la Tienda K. Al oriente, el lote de un piso con el seto de la foto de Street View |
| MU | Cl. 62 # 9-65 | 008213025008 | 3 | La página del Medio Universitario |
| CPC 1 | Cra. 9 # 61-38 | 008213027011 | 5 | La página del CPC. El catastro no trae aún la cafetería del P1 |
| CPC 2 | Cra. 8 # 64-42, piso 4 | 008214027016 | 7 y 2 sótanos | La página del CPC: el piso 4 del Edificio Corpocentro |
| JAAB | Cra. 10 # 64-65 | 008214013017 | 6 | Brian, y Google Maps |

- **Los alrededores de un edificio:** el bloque, los andenes y las calles vienen del mismo mapa de
  referencia, en `db/ground/`. `scripts/map-ground.py` los vuelve a cortar.
- **Cada edificio en su sitio:** `scripts/plan-tracing/place.py` ajusta el dibujo de sus planos a
  las partes del catastro. Al que no tiene dibujo le pone un marco sobre su predio.
- **Las alas del EC**, según su fachada: el ala norte son los cinco pisos sobre la Calle 63; el ala
  central, el núcleo de ocho pisos y su base; el ala sur, las partes de seis y cuatro pisos sobre
  el auditorio.
- **Las del BI**, según su fachada: el ala occidental (bienestar) ocupa los predios 004 y 005, y
  la oriental (Centro de Investigaciones 2), el 006. Desde la Calle 62, la oriental queda a la
  izquierda. El pin de Google Maps lo pone 60 m más al oriente, en el 9-43, y está mal.

## Lo que vale para todo el campus

### Qué fuente manda

Brian lo estableció en sitio, y las fotos lo confirman:

1. **PI**, para qué existe y cómo se llama. Es la más actual, aunque **no siempre está completa**.
2. **PE**, para la forma. En casi todos los edificios dibuja la silueta y no el uso: el
   "Auditorio" del EC aparece en los ocho pisos, y las aulas y salas de cómputo del JAAB ya tienen
   otros nombres.
3. **RS**, la más vieja. Pero donde existe (CPC 1, MU, ala central del EC) **es el plano más
   detallado**, y en el EC sus cantidades de salones coinciden con las placas.

### Los PE, el catastro y los andenes

- **El catastro mapea cada edificio desde arriba.** Donde los pisos altos vuelan sobre la acera,
  la parte llega hasta el sardinel y el andén pasa por debajo. Es el caso del ala norte del EC
  sobre la Calle 63, que va en columnas. En la calle, el edificio termina donde empieza el andén;
  arriba, puede volar. Por eso el editor corta las líneas rosadas por los andenes solo en los
  pisos de la calle.
- **Los PE no están a escala.** En el EC dibujan la torre un 17 % más angosta y el ala sur un 15 %.
  El ala norte y los espacios entre las alas salen bien.
  - `scripts/plan-tracing/register.py` estira cada tramo a su medida, con paredes que los planos
    y el catastro comparten.
  - `specs/ec-register.json` dice cuáles son esas paredes y por qué se eligieron.
  - El P1 ya está corregido; los demás pisos se corrigen uno a uno.
- **Los pisos altos del EC, según Brian.** El catastro se quedaba corto y ya está corregido en la
  huella:
  - **Ala norte:** aulas hasta el P5; el P6 es terraza.
  - **Ala sur:** el auditorio ocupa P1–P2, hay aulas y oficinas de P3 a P5, y en el P6 está la
    terraza sur.
  - **Ala central:** los ocho pisos. En el P6, el pasillo de oficinas que lleva a la terraza sur;
    en el P7, una terraza pequeña encima de ese pasillo.
- **Los PE de P6 a P8 dibujan la torre donde abajo está el ala norte.** Se movieron para que sus
  escaleras y ascensores queden encima de los de P3–P5. La silueta del ala sur que pintan en el
  P7 y el P8 se quitó, porque el ala termina en el P6.
- **El P1 no es a cielo abierto donde el catastro no registra partes.** El ala central tiene techo
  hasta la Carrera 9A; es a cielo abierto el paso junto a la casa de Francisco de Paula Vélez.
  Esas partes van en la huella sin predio.

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

**Lo que dicen las placas ya está cargado en el mapa.** Cada piso de estos seis edificios existe en
el editor de pisos, sin dibujar, con su inventario: lo que lista la PI y la circulación y los baños
que dibujan los PE y los RS. En sitio queda dibujar las cajas y decir cuál es cuál, borrar lo que ya
no exista y agregar lo que falte.

**EC**

- [ ] Orientación con brújula.
- [ ] **Ala norte, P1–P5**, y su escalera hacia la terraza norte del P6.
- [ ] **Ala sur, P3–P6**, y por dónde se entra desde el ala central.
- [ ] El formato de los números de puerta. Si "S-301 / S-304" es un rango.
- [ ] El P7: si la puerta dice `7-01` o `701`. Ya se sabe que son 13 aulas.
- [ ] El P1: dónde quedan Admisiones, Tesorería y el Call Center.
- [ ] **Si las escaleras y los ascensores quedan uno encima del otro.** Los PE no concuerdan:
  - El P1 y el P2 ponen el núcleo central 3 m más cerca de la Calle 63 que P3–P5.
  - El P1 lo pone además 5 m más atrás de la Carrera 9 Bis.
  - Medir la distancia de la fachada de la Calle 63 a la puerta de los ascensores centrales en el
    P1 y en el P3.
- [ ] **P3–P5, ala norte:** si las aulas llegan a la fachada de la Calle 63. Los PE las dejan 6 m
  adentro.
- [ ] **La escalera norte:** si pasa junto al hueco que el catastro deja en el ala norte, cerca de
  la Carrera 9 Bis, o si ese hueco es parte de ella.
- [ ] **El extremo de la torre sobre la Carrera 9A:** si de P2 a P7 llega hasta la fachada, como
  dibujan los PE, o termina 3 m antes, como registra el catastro.
- [ ] **P6:** dónde quedan las oficinas del ala sur dentro de la terraza sur, y la forma de la
  terraza norte.
- [ ] El sótano: si lo usan peatones.

**BI**

- [ ] **El ala oriental entera,** la de salones y laboratorios.
- [ ] Sus placas, si las tiene.
- [ ] Por dónde se cruza de un ala a la otra.

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

**RH, CPC 2 y Tienda K**

- [ ] **Primera visita.** En el RH, confirmar qué hay: la web ubica ahí a Talento Humano, pero
  las placas del EA también listan dependencias de Recursos Humanos.

**Antes de caminar**

- [ ] El recorrido 360° de la universidad (`webapps.konradlorenz.edu.co/tour/`) tiene escenas
  con nombre que sirven para reconocer espacios sin identificar:
  - **EC:** cafetería, biblioteca, piso STEM, Laboratorio de Redes Cisco, salas de cómputo,
    Servicios Tecnológicos, laboratorios de Workstation, de Desarrollo de Software, de Software y
    de Simulación y Videojuegos, Business Training Center, Cancillería, Oficina de Graduados,
    Cámara de Gesell, el museo de los pisos 3 a 5, el auditorio con su tramoya, y las terrazas
    norte, sur, del 7.º y del 8.º piso.
  - **JAAB:** el Laboratorio de Interactividad.
  - **BI:** la entrada al Centro de Bienestar y Deportes.
  - **CPC:** la terraza del último piso.

## Decisiones tomadas con Brian

1. **Se mapea todo, incluidos los espacios que no son para estudiantes:** planta eléctrica, centro
   de acopio, rack, cuartos de aseo. La app no es solo para estudiantes, docentes y visitantes:
   también servirá a administrativos y funcionarios. Qué ve cada perfil lo decide el cliente, no
   los datos.
2. **Alias en espacios y también en edificios.** "Sonia Fajardo Forero" y "Juan Alberto Aragón
   Bateman" son solo dos ejemplos: hay más lugares, e incluso edificios, que la gente conoce por
   otros nombres, a veces varios.
3. **La accesibilidad de cada zona se registra:** si se llega sin escaleras. El mezzanine del JAAB
   sin ascensor, la escalera exterior del MU y la terraza norte del EC son los casos conocidos.
   Mientras no se verifique, queda como desconocida, no como accesible.
4. **Seis categorías fijas y tipos que crecen:** docencia, atención, oficina, bienestar y social,
   servicio y circulación.
   - Lo que se crea desde el portal son **tipos**: "Cuarto de TI", "Sala de lactancia"…
   - Las categorías se quedan fijas porque las apps las usan para el color y el ícono. Una
     séptima es un cambio de contrato chico, pero se hace a propósito, no desde un formulario.
