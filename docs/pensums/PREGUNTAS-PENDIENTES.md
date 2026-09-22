# Preguntas pendientes sobre los pensums

Todo lo que los PDFs publicados no responden, junto para llevarlo a una sola conversación con la
coordinación académica. Cada punto dice qué falta, qué hace KApp mientras tanto y por qué importa.

La lista de lo que sí quedó cargado está en [README.md](README.md).

---

## 1. Códigos institucionales de las materias

**Qué falta.** Solo cuatro planes imprimen los códigos de sus materias: Ingeniería de Sistemas
1015, Matemáticas 1017, Ingeniería Industrial 1020 y Psicología 2020. Los otros diecinueve
programas llegan como folletos, sin un solo código.

**Qué hace KApp mientras tanto.** Genera un código propio por materia (`MKT-101`, `EPF-204`) que
**nunca se le muestra al estudiante**. Sirve para identificar la materia dentro del sistema y nada
más. Los planes que dependen de esos códigos quedan en estado `DRAFT`.

**Por qué importa.** El día que lleguen los códigos reales hay que reemplazarlos, y el progreso de
cualquier estudiante que ya estuviera enganchado a un código inventado se tiene que reconciliar.
Entre menos tiempo pase, menos datos hay que arrastrar.

**Lo que ya sabemos y ayuda.** Las materias se comparten entre programas bajo un mismo código:

- **Inglés y Cátedras de Cultura** aparecen con el mismo código en los cuatro planes codificados
  (`71181`, `72081`, `73081`, `74081`, `75081`, `71221`, `76011`, `70012`, `71071`).
- **Los cálculos y las matemáticas básicas** se comparten dentro de la FMI: `11015` Precálculo,
  `12015` Cálculo I, `13015` Cálculo II, `14015` Cálculo III, `13013` Ecuaciones Diferenciales,
  `21012` Lógica Matemática, `63202` Álgebra Lineal, `23012` Matemáticas Discretas.
- **Programación** igual: `41025` Fundamentos, `31013` Técnicas I, `31014` Técnicas II.

Es decir que buena parte de las materias de los folletos probablemente **ya tiene código**, el
mismo que usa Ingeniería de Sistemas o Matemáticas. No se asignó ninguno por nuestra cuenta:
emparejar por nombre parecido es justo la clase de suposición que después nadie puede auditar.

**Qué pedir.** Un export de SINU con código, nombre, créditos, horas, área, nivel y prerrequisitos
por plan. Resuelve este punto y los dos siguientes de una vez.

---

## 2. Créditos por materia que los folletos no imprimen

**Qué falta.** Cinco programas no publican los créditos de cada materia:

| Programa                                          | Lo que imprime el folleto |
| ------------------------------------------------- | ------------------------- |
| Especialización en Neuropsicología Clínica        | solo el total: 33         |
| Maestría en Psicología del Consumidor             | solo el total: 52         |
| Maestría en IA Aplicada a Contextos Humanos       | solo el total: 34         |
| Maestría en Psicología Clínica                    | solo el total: 59         |
| Especialización en Psicología Forense y Criminal  | **ni total ni detalle**   |

Además, Administración en Seguridad y Salud en el Trabajo (virtual) deja la casilla de créditos
**en blanco** en cinco materias: Cultura II y las Electivas 1, 2, 3 y 4.

**Qué hace KApp mientras tanto.** Guarda 0 en esas materias y conserva el total que el documento
declara. El portal no muestra un 0 —que se leería como "esta materia no vale nada"— sino un guion,
y avisa arriba que el plan no publica créditos por materia.

**Por qué importa.** Sin créditos por materia no hay barra de avance ni conteo de créditos
aprobados: el semáforo de esos programas queda incompleto.

---

## 3. Horas semanales que los folletos no imprimen

**Qué falta.** Solo los cuatro planes codificados, Marketing y Negocios Internacionales publican
horas. Los otros diecisiete programas no traen ninguna.

**Qué hace KApp mientras tanto.** Guarda 0 y el portal lo muestra como guion, igual que arriba.

**Por qué importa.** Menos grave que los créditos: las horas son informativas. Pero sin ellas no se
puede validar la regla colombiana de crédito (1 crédito = 48 horas por semestre), que es
justamente la que nos dejó verificar que las cuatro mallas codificadas están bien transcritas.

---

## 4. Medias horas

**Qué pasa.** Cuatro prácticas imprimen media hora: las dos Prácticas Profesionales de Psicología
(4,5 horas) y las Prácticas Profesionales de Marketing y de Negocios Internacionales (1,5 horas).

**Qué hace KApp.** Ya las guarda tal como están impresas. El campo de horas acepta enteros y
medias, y nada más fino: un 4,3 se rechaza como error de transcripción. Marketing queda entonces
en 165,5 horas semanales y Negocios Internacionales en 159,5.

**Lo que esto resolvió.** Redondearlas era lo que hacía que Psicología sumara 175 horas contra las
174 que imprime su propio documento. Guardando la media, suma 174 exactas. Ver el punto 6.

**Qué confirmar.** Si esas medias horas son reales o una errata del documento. La aritmética dice
que son reales, porque son justo lo que hace cuadrar el plan con su total impreso.

---

## 5. Matemáticas: la malla codificada y el folleto no coinciden

Son el mismo plan publicado dos veces, y difieren en dos puntos:

| Materia                      | Malla codificada (2019-2) | Folleto | Qué se cargó       |
| ---------------------------- | ------------------------- | ------- | ------------------ |
| Práctica Profesional         | 24 horas                  | 22      | 24, la codificada  |
| Enseñanza de las Matemáticas | 2 créditos                | 3       | 2, la codificada   |

**Por qué se eligió la codificada.** Por las horas, porque es la que trae los códigos y toda la
transcripción salió de ahí. Por los créditos, porque el folleto **se contradice a sí mismo**: con
3 créditos su propio semestre VIII sumaría 17, y el mismo folleto imprime 16.

**Por qué importa.** Son dos créditos y dos horas en el plan de un programa entero, y hoy un
estudiante de Matemáticas ve 143 créditos y 197 horas donde el folleto dice 143 y 195. Mientras no
se confirme cuál manda, cualquier cuenta que hagamos sobre ese plan arrastra la diferencia.

**Qué preguntar.** Cuál de los dos documentos es el vigente, y si el folleto se va a corregir.

---

## 6. Psicología: el total general y los totales por semestre ~~no cuadran~~ ya cuadran

**Resuelto, y vale la pena dejar escrito por qué.** El plan imprime **174** horas presenciales
como total, mientras que sus totales por semestre (21, 21, 23, 21, 24, 20, 19, 15, 11) suman
**175**. La diferencia no era del documento sino nuestra: las dos prácticas de 4,5 horas estaban
redondeadas a 5.

Guardando la media, las materias suman 174 —el total impreso— y los semestres VIII y IX suman
14,5 y 10,5. Es decir que el documento redondea hacia arriba cada total de semestre para
imprimirlo, y de ahí sale su 175. **Los dos números impresos son correctos**, cada uno a su
manera.

Queda una sola cosa por confirmar, la del punto 4: que las medias horas sean reales.

---

## 7. Vigencia de los planes

Los PDFs están publicados en la página oficial, así que se toman como vigentes. Quedan dos con
fecha vieja —Ingeniería de Sistemas 2019-1 e Ingeniería Industrial 2021-1—, que según lo hablado
significa que no se han actualizado, no que estén derogados. **Confirmar** que un estudiante que
entra hoy a esos programas sigue el plan que ahí aparece.

---

## 8. Códigos de programa y de plan

Solo son reales el código de programa `506` (Ingeniería de Sistemas) y los números de plan 1015,
1017 y 1020. Todo lo demás —el código de programa de los otros veintidós y el código de plan de
Psicología y de los folletos— lo inventó KApp para poder identificarlos. Pedir los oficiales en el
mismo export de SINU.

---

## 9. Prerrequisitos entre electivas

Dos planes dibujan una cadena entre casillas electivas: los Énfasis I → II → III de Matemáticas, y
Área profesional I → II → Práctica profesional Área Electiva de Psicología. KApp no las guarda,
porque una casilla electiva no tiene código de materia y el modelo solo acepta prerrequisitos por
código. Es una limitación nuestra, no del documento: **confirmar si esas cadenas se exigen de
verdad**, porque si es así hay que ampliar el modelo.
