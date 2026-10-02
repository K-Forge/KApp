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
| **RH · Edificio de Recursos Humanos** | 0 | ? | — | **Sin ubicar.** Quizá en las oficinas de la Tienda K |
| **CPC 2** | 0 | ? | — | **Sin visitar** |
| **TK · Tienda K** | 0 | ? | — | **Sin visitar.** La tienda de uniformes, en la esquina junto a la casa de Francisco de Paula Vélez, con oficinas al lado y en su segundo piso |

## Dónde queda cada edificio

Todos sobre el catastro de Bogotá (Mapa de Referencia de IDECA): la dirección da el predio, y el
predio, las partes del edificio con cuántos pisos tiene cada una. Así quedan en el mapa del campus,
con su forma real, aunque nadie haya dibujado sus pisos.

| Edificio | Dirección | Predio | Pisos según el catastro | Cómo se ubicó |
|---|---|---|---|---|
| EC | Cra. 9 Bis # 62-43 | 008213024019 | 8 y 2 sótanos | La dirección de la universidad |
| RH | — | — | — | Sin ubicar. La web pone a Talento Humano en la Cra. 9A # 62-12, que es la casa de Francisco de Paula Vélez (lote 004), patrimonio y no una oficina |
| TK | Cra. 9A # 62-02 | 008213024015 | 2 | Google Maps, en la esquina con la Calle 62 |
| EA | Cra. 9A # 62-27 | 008213015038 | 4 | Brian: al frente de la casa de Francisco de Paula Vélez |
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

- **Ningún edificio pisa el andén.** El margen de cada piso, y los salones dentro de él, quedan
  dentro de la manzana o en su borde. El catastro mapea desde arriba, y su parte del ala norte del
  EC llega hasta el sardinel de la Calle 63; el andén pasa por debajo. Por eso el portal corta el
  margen y las partes del catastro donde pasa un andén, en todos los pisos.
- **Los PE no están a escala.** En el EC dibujan la torre un 17 % más angosta y el ala sur un 15 %.
  El ala norte y los espacios entre las alas salen bien.
  - `scripts/plan-tracing/register.py` estira cada tramo a su medida, con paredes que los planos
    y el catastro comparten.
  - `specs/ec-register.json` dice cuáles son esas paredes y por qué se eligieron.
  - Los ocho pisos del EC ya están corregidos.
- **Con cinta, el P1 y el P2 del EC resultan proporcionados.** El levantamiento de la manzana
  (`docs/map/survey/`) midió la pared de la Calle 63 a 6 m del sardinel, no sobre él, y el fondo
  del edificio 3 m más corto que el lote. Puestos sobre esas paredes, los PE de los dos primeros
  pisos caen solos: la pared interior del ala norte y el final de la conexión norte quedan a menos
  de 0,3 m de donde los pone la cinta.
  - `specs/ec-surveyed.json` tiene las paredes medidas de esos dos pisos, y `traced/` los pisos
    como salieron del trazado, de donde `register.py` vuelve a partir cada vez que el edificio se
    mide de nuevo.
  - El auditorio va por sus cuatro esquinas: el frente sobre el muro azul, desde la columna verde
    hasta el vecino, y el fondo sobre el del ala sur. Rodea la taquilla y el tramo gris.
  - La escalera norte queda al frente del ala norte, sobre la Carrera 9 Bis. La escalera principal
    queda tan honda como los ascensores, como la dibujan esos dos planos.
  - **Falta:** del P3 al P8 siguen sobre el catastro, con la escalera norte donde él ponía el ala.
- **El auditorio, como Brian lo marcó a mano** sobre su PE (2 de octubre; la foto
  `EC-AU-P1-PE.HEIC` lleva sus trazos). `scripts/plan-tracing/strokes.py` lee cada contorno que
  dibujó, color por color, y `specs/ec-au-p1-strokes.json` dice cuál es cuál.
  - De norte a sur: el **lobby** (toda la conexión sur; el PE no lo dibuja), un **muro grueso** con
    el botiquín pegado y una sola puerta, la **taquilla** en ese muro sobre la calle, la **antesala
    norte**, la sala con su **tarima**, **audiovisuales**, la **antesala sur** con la escalera y la
    puerta del tramo gris, y **utilería** al fondo. Los "S.I." son espacios sin identificar: pueden
    ser vacíos o columnas.
  - **En el P2** solo se repiten dos bloques de sillas; el resto de la sala sube los dos pisos.
  - **La esquina junto a la taquilla es un muro en ángulo**, no una esquina cuadrada: va del final
    de la taquilla al comienzo del muro azul. Las medidas 26 y 74 son sus dos catetos.
  - El PE del auditorio no se estira: se gira 1,7° y se agranda 7 % para que su frente caiga sobre
    el muro azul medido. Así el fondo queda entre 0,5 y 1,3 m antes del lindero que el catastro le
    da al ala sur; el patio cubierto puede ser ese tanto más ancho. La medida 83 lo resuelve.
  - Dónde queda cada puerta del lobby en su muro es como él la dibujó, no medido.
- **Atrás, sobre la Carrera 9A, solo salen el ala norte y la torre.** La conexión norte queda
  detrás de unas materas elevadas cuyo frente sigue el de las alas: por eso la cinta dio una línea
  recta. Street View (2023), desde tres puntos de la calle, pone su muro a 1,5 m del frente de la
  torre; Brian lo dejó a mano a 2,1 m, casi en línea con el muro del cuerpo de un piso. La medida 75
  del levantamiento lo confirma en sitio.
  - **De la entrada del parqueadero a la salida también es jardín** (Brian, 2 de octubre). Detrás
    de las materas está el muro del cuerpo de un piso, con baranda encima, en línea con el frente
    de la caseta. La medida 50 (5,48 m) es la esquina de las materas y la 70 (7,38 m) es el muro:
    el jardín tiene 1,9 m de fondo, y la caseta queda alineada con el edificio.
  - **El paso junto a la casa no se ha medido.** Las medidas 76 a 82 del levantamiento se toman
    adentro, entrando por la reja: los lados del cuerpo de un piso, el muro del edificio alto, el
    ancho del paso en dos puntos y el muro del fondo.
  - Los pisos altos no vuelan sobre el andén de la Carrera 9A. Las partes "desde el piso 2" que
    llegaban 3 m más afuera venían del contorno dibujado sobre la foto aérea, que inclina los
    techos altos, y se quitaron.
- **Los pisos altos del EC, según Brian.** El catastro se quedaba corto y ya está corregido en la
  huella:
  - **Ala norte:** aulas hasta el P5; el P6 es terraza.
  - **Ala sur:** el auditorio ocupa P1–P2, hay aulas y oficinas de P3 a P5, y en el P6 está la
    terraza sur.
  - **Ala central:** los ocho pisos. En el P6, el pasillo de oficinas que lleva a la terraza sur, y
    en el P7 una terraza pequeña encima de él, están en la conexión sur (ver más abajo).
- **Los PE de P6 a P8 dibujan la torre donde abajo está el ala norte.** Se movieron para que sus
  escaleras y ascensores queden encima de los de P3–P5. La silueta del ala sur que pintan en el
  P7 y el P8 se quitó, porque el ala termina en el P6.
- **Mientras no lleguen los planos de CAD, los dibujos siguen al edificio.** Cada salón queda a un
  muro (0,3 m) dentro del margen de su piso, y cada terraza dentro del margen del piso de abajo.
  - Los ascensores, la escalera principal y la de emergencia quedan donde las ponen P3–P5, un
    poco más adentro para caber en la torre en P7 y P8, donde sube sola.
  - La escalera norte queda donde la pone el P2.
  - Las aulas del ala norte de P3–P5 llegan hasta el andén de la Calle 63.
- **En el P3 no hay tramoya**, según Brian. Esa zona del ala sur tiene salones: el bloque quedó
  "Sin identificar".
- **El P1 no es a cielo abierto donde el catastro no registra partes.** El ala central tiene techo
  hasta la Carrera 9A; es a cielo abierto el paso junto a la casa de Francisco de Paula Vélez.
  Esas partes van en la huella sin predio.
- **El EC es rectangular; lo diagonal es la aleta de la Carrera 9 Bis.** Brian lo dibujó sobre
  Google Maps, Apple Maps y en un esquema (28 de septiembre de 2026). Se cruzó con el catastro y la
  ortofoto de 2014 de IDECA, que está nítida; la de 2017 inclina los techos altos varios metros.
  - La retícula del dibujo (128,13°) es la misma del catastro. Todas las construcciones del EC son
    ortogonales, menos una.
  - Esa una es una cuña de "6 pisos" frente a la Carrera 9 Bis: 105 m², 31,6 m de lado diagonal y
    7,7 m de ancho junto al ala norte, hasta cero en el ala sur. En la ortofoto es una plaza con
    gente y árboles, la aleta. La huella la tomó como ala central, y de P2 a P6 hay unos 22 m² de
    aulas encima de ella por piso.
  - La huella deja la cuña con 0 pisos; así `place.py` no la vuelve a subir. La fachada que queda
    es ortogonal, con retrocesos: el ala norte llega a la esquina y la torre queda atrás, con la
    plaza enfrente (las fotos de dron y el render lo muestran).
  - La casa de Francisco de Paula Vélez (lote 004, placas Carrera 9A # 62-12 a 62-22) es
    patrimonio. La universidad le puso un techo blanco y una placa con su logo; el cuadrado que se
    ve en las fotos es la casa con su jardín cerrado. No es una oficina, así que el RH no está ahí:
    queda sin ubicar. Brian cree que Talento Humano puede estar en las oficinas de la Tienda K, al
    lado o en su segundo piso. El lote de la esquina es solo de la Tienda K.
  - El vecino que no es de la universidad es el lote 001, Calle 62 # 9-46 a 9-60, de 5 pisos, más
    bajo que el ala sur. El ala sur llega hasta él, como dice el catastro.
  - La casa, su jardín y el vecino son las estructuras del campus (lo que hay en la manzana y no es
    de la universidad), y se corrigen en el editor de manzanas del portal.
  - El catastro dibuja el ala norte hasta el sardinel de la Calle 63, porque arriba vuela sobre el
    andén. La huella la corta en el borde interior del andén (x = 103 del dibujo), recta con la
    retícula, para que ningún dibujo de la manzana la muestre sobre el andén.
- **Las alas y sus conexiones, según Brian** (fotos de dron, 28 de septiembre de 2026). Primero fue
  el ala central; después vinieron las alas y las conexiones, y cada conexión es de su ala.
  - **Ala central:** los salones sin sufijo, la recepción de dos pisos (en el P2 no hay nada encima),
    los ascensores, la mitad de la biblioteca, la cafetería 1 y la presidencia en el P8.
  - **Ala norte:** las aulas con sufijo N, la cafetería 2, parte de la biblioteca del P2 y la terraza
    norte del P6, sobre toda el ala.
  - **Conexión norte**, del ala norte: la franja de dos pisos entre el ala norte y la torre. Tiene
    la cocina en el P1 y el resto de la biblioteca en el P2. Desde el P3 es un jardín a cielo
    abierto, con un pasillo en cada extremo (Carrera 9 Bis y Carrera 9A) y unas oficinas. Los
    pasillos van en la huella sin predio, de 2,6 m de fondo; eso se supuso.
  - **Conexión sur**, del ala sur: el bloque entre la torre y el auditorio. En el P1 es la entrada
    del público al auditorio y en el P2 un pasillo; de P3 a P6 tiene aulas y oficinas, y en el P7 la
    terraza pequeña. El catastro deja 0,5 m entre ella y el ala sur; esa ranura se llenó. También
    es suyo el pasillo largo de salida a la Carrera 9A, entre la torre y la casa, a cielo abierto
    sobre el sótano.
  - **Ala sur:** en P1 y P2, el auditorio, con la forma de su PE. De P3 a P6 sobresale un poco de
    él, con aulas con sufijo S, oficinas y parte de la terraza sur.
  - **Detrás del auditorio**, entre él y la casa, hay un patio cubierto con techo corredizo y mesas
    que va de la torre al edificio del vecino. Es de un piso y del EC, y va de pared a pared: del
    auditorio a la casa. Se dibujó rectangular, de 2,7 m, aunque el catastro sigue el lindero
    diagonal con la casa y le daba al auditorio muros diagonales. Su ancho y la pared de la casa se
    ajustan en el editor de manzanas.
- **Los salones son rectangulares.** `register.py` cuadra toda pared que el ajuste corta. Las
  esquinas en chaflán que dejó el trazado se completan si hay espacio, o se escalonan hacia
  adentro. Solo el auditorio conserva la forma de su PE.

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

- [ ] **Las medidas de afuera, con la hoja de levantamiento del portal** (*Survey*,
  `/data/survey`).
  - Son 63 distancias alrededor de la manzana, en el orden en que se camina, desde la esquina de
    la Calle 63 con la Cra 9 Bis.
  - Hay de dos clases. Las naranjas van del muro al borde de la calle, derecho, y dicen dónde está
    cada volumen. Las azules van a lo largo del muro o a través de una puerta, y dicen cuánto
    mide.
  - Hay una por cada fachada de las alas y sus conexiones, y por las puertas: la recepción, la
    salida a la Cra 9A y la entrada del auditorio.
  - El ala sur se mide cada 5 m, porque el catastro la ve desde arriba.
  - Con esas medidas se redibujan los contornos, en vez de moverlos a mano en el editor de
    manzana.
- [ ] Orientación con brújula.
- [ ] **Ala norte, P1–P5**, y su escalera hacia la terraza norte del P6.
- [ ] **Ala sur, P3–P6**, y por dónde se entra desde el ala central.
- [ ] El formato de los números de puerta. Si "S-301 / S-304" es un rango.
- [ ] El P7: si la puerta dice `7-01` o `701`. Ya se sabe que son 13 aulas.
- [ ] El P1: dónde quedan Admisiones, Tesorería y el Call Center.
- [ ] **Dónde están el núcleo y las escaleras.** Los PE no concuerdan, y el mapa usa la posición
  de P3–P5.
  - El P1 y el P2 ponen el núcleo central 3 m más cerca de la Calle 63; el P1, además, 5 m más atrás
    de la Carrera 9 Bis.
  - Medir la distancia de la fachada de la Calle 63 a la puerta de los ascensores centrales, en el
    P1 y en el P3.
- [ ] **La escalera norte:** si pasa junto al hueco que el catastro deja en el ala norte, cerca de
  la Carrera 9 Bis, o si ese hueco es parte de ella.
- [ ] **Los huecos del ala norte sobre el P1:** si son patios de luz. El catastro no registra piso
  encima de ellos, y el mapa quitó las cajas que los PE dibujan ahí.
- [ ] **El extremo de la torre sobre la Carrera 9A:** si de P2 a P7 llega hasta la fachada, como
  dibujan los PE, o termina 3 m antes, como registra el catastro.
- [ ] **P6:** dónde quedan las oficinas del ala sur dentro de la terraza sur, y si la terraza norte
  ocupa toda el ala, como se dibujó.
- [ ] El sótano: si lo usan peatones.
- [ ] **Los pasillos de la conexión norte**, de P3 a P5: su fondo (se supusieron 2,6 m) y dónde
  quedan las oficinas.
- [ ] **El patio cubierto detrás del auditorio:** su ancho hasta la pared de la casa (se supusieron
  2,7 m) y por dónde se entra.

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

- [ ] **Primera visita.** Dónde está Talento Humano (el RH): quizá en las oficinas de la Tienda K,
  al lado o en su segundo piso; las placas del EA también listan dependencias de Recursos Humanos.

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
