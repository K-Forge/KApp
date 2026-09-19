# Pensums

The 23 plans of study the university publishes, as KApp loads them.

The source is the set of 24 PDFs the university hands out (downloaded 2026-09-16). One of them,
`plan-estudios-matematicas-konrad.pdf`, is the brochure of the same plan as the coded Matemáticas
1017 grid, so it is used to check that grid rather than loaded twice.

| Path | What it is |
| --- | --- |
| `transcriptions/*.yaml` | One file per plan: exactly what its PDF prints, with notes on anything odd. **This is the source of truth.** |
| `catalog.yaml` | Which transcription becomes which program and pensum, under which codes and status. |
| `tools/extract_coded_grid.py` | Reads the Sistemas, Matemáticas and Industrial grids by geometry, arrows included. |
| `tools/extract_psicologia_2020.py` | The same for the Psicología 2020 grid, which has its own cell shape. |
| `tools/build_seed.py` | Turns the transcriptions into the seed JSON under `semaphore-service/src/main/resources/db/seed/pensums/`. |

`V006_SeedThePublishedPensums` loads that seed, and `PublishedPensumSeedTest` pins what it loads. In the admin portal, **Pensums → As printed** draws each one as areas × levels, which is how every plan below was checked against its PDF.

## Changing a plan

Edit the transcription (or `catalog.yaml`), never the generated JSON, then:

```bash
python3 -m pip install pyyaml
```

```bash
python3 docs/pensums/tools/build_seed.py
```

A change unit runs once. A database that already ran `V006` will not pick up a regenerated seed by
itself: ship the change as a new change unit, or update the pensum through the admin portal.

## How the PDFs were read

**Four plans print course codes** — Ingeniería de Sistemas 1015, Matemáticas 1017, Ingeniería
Industrial 1020 and Psicología 2020. They were extracted from the PDF's own text and vector layers,
not transcribed by eye, and every total each document prints is matched:

- **Sistemas and Matemáticas** print each cell as `HTD · code · HTI` over the credits. The Colombian
  credit is 48 hours a semester, so `credits × 3 = HTD + HTI` must hold in every cell; it does for all
  51 cells of 1015, and for 48 of 51 in 1017 (see the notes in its transcription).
- **Industrial** prints `H · code · C` and the totals per semester and overall (173 hours, 142 credits).
- **Psicología** prints the totals per area, per semester and overall (151 credits, 174 hours).
- **Prerequisites** are the drawn arrows, followed through their connector segments back to the box
  they leave from. Psicología draws plain lines, and the direction comes from the semesters. Every
  extracted link was then checked against an enlarged render of the drawing.

**The other nineteen are brochures.** They list course names by semester or cuatrimestre and, in
most cases, the credits. They were transcribed from the rendered pages, because their text layers
are incomplete. Tecnología en Desarrollo also draws its prerequisites; those were read from the
vector geometry like the grids.

## What the seed decides

| Decision | Why |
| --- | --- |
| `weeklyHours` is the printed contact hours (HTD, H, "Horas Presenciales"). | It is what the grids and brochures call hours, and what the Industrial and Psicología totals add up. |
| A printed 4,5 or 1,5 hours is stored as it is printed. | `weeklyHours` accepts a half and nothing finer. It affects four items: the two Psicología practices and the Marketing and Negocios Internacionales practices. Psicología then adds up to exactly the 174 it prints — its own printed semester totals say 175 because they round each half up (14,5 printed as 15, 10,5 as 11). |
| Missing credits or hours are stored as 0, and the pensum keeps the total the document prints. | Inventing per-course values would hide the gap; storing the printed total keeps it visible. |
| A course whose name says it is an elective is an elective slot. | A slot has no course code, so the printed code moves to `pensumItemCode` and `sinuCode`. |
| A prerequisite between two slots is not stored. | The model only accepts course codes as prerequisites. It drops Matemáticas' Énfasis I → II → III and Psicología's Área profesional I → II → Práctica profesional Área Electiva. |
| Printed typos in course names are corrected, and only those listed in `build_seed.py`. | *Internedio*, *Sofware*, *Aplicac Móviles* and the like would otherwise be on a student's screen. The transcriptions keep the printed text. |
| Brochure plans load as `DRAFT`. | Their course codes are placeholders; a student pinned to one would lose the pin when the real codes arrive. |

## What was loaded

| Pensum | Program | Level | Status | Items | Credits | Weekly hours | Prerequisites |
| --- | --- | --- | --- | ---: | ---: | ---: | ---: |
| `1015` | Ingeniería de Sistemas (`506`) | Pregrado | ACTIVE | 51 | 143 | 194 | 23 |
| `1017` | Matemáticas (`MATEMATICAS`) | Pregrado | ACTIVE | 51 | 143 | 197 vs 195 ⚠️ | 15 |
| `1020` | Ingeniería Industrial (`ING-INDUSTRIAL`) | Pregrado | ACTIVE | 51 | 142 | 173 | 21 |
| `PSI-2020` | Psicología (`PSICOLOGIA`) | Pregrado | ACTIVE | 54 | 151 | 174 | 21 |
| `TDS-2026` | Tecnología en Desarrollo (`TEC-DESARROLLO`) | Tecnología | DRAFT | 34 | 96 | — | 24 |
| `ADV-2026` | Administración de Empresas (virtual) (`ADM-EMPRESAS-VIRT`) | Pregrado | DRAFT | 50 | 147 | — | — |
| `SSTV-2026` | Administración en Seguridad y Salud en el Trabajo (virtual) (`ADM-SST-VIRTUAL`) | Pregrado | DRAFT | 53 | 138 | — | — |
| `MKT-2026` | Marketing (`MARKETING`) | Pregrado | DRAFT | 50 | 144 | 165,5 | — |
| `ANI-2026` | Administración de Negocios Internacionales (`NEGOCIOS-INTERNAC`) | Pregrado | DRAFT | 51 | 144 | 159,5 | — |
| `EADI-2026` | Especialización en Analítica de Datos e Inteligencia Artificial (`ESP-ANALITICA-IA`) | Especialización | DRAFT | 10 | 24 | — | — |
| `MCDI-2026` | Maestría en Ciencia de Datos e IA (`MAE-CIENCIA-DATOS`) | Maestría | DRAFT | 19 | 47 | — | — |
| `MIAC-2026` | Maestría en Inteligencia Artificial Aplicada a Contextos Humanos y Sociales (`MAE-IA-APLICADA`) | Maestría | DRAFT | 13 | 0 of 34 ⚠️ | — | — |
| `MPC-2026` | Maestría en Psicología del Consumidor (`MAE-PSI-CONSUMIDOR`) | Maestría | DRAFT | 18 | 0 of 52 ⚠️ | — | — |
| `EPC-2026` | Especialización en Psicología del Consumidor (`ESP-PSI-CONSUMIDOR`) | Especialización | DRAFT | 8 | 24 | — | — |
| `MPCL-2026` | Maestría en Psicología Clínica (`MAE-PSI-CLINICA`) | Maestría | DRAFT | 25 | 0 of 59 ⚠️ | — | — |
| `ENC-2026` | Especialización en Neuropsicología Clínica (`ESP-NEUROPSICOLOGIA`) | Especialización | DRAFT | 13 | 0 of 33 ⚠️ | — | — |
| `ECI-2026` | Especialización en Psicología Clínica Infantil, del Adolescente y la Familia (`ESP-CLINICA-INFANTIL`) | Especialización | DRAFT | 13 | 32 | — | — |
| `EPF-2026` | Especialización en Psicología Forense y Criminal (`ESP-PSI-FORENSE`) | Especialización | DRAFT | 12 | 0 ⚠️ | — | — |
| `ESX-2026` | Especialización en Sexualidad (`ESP-SEXUALIDAD`) | Especialización | DRAFT | 12 | 30 | — | — |
| `ECT-2026` | Especialización en Evaluación Clínica y Tratamiento de Trastornos Emocionales y Afectivos (`ESP-EVAL-CLINICA`) | Especialización | DRAFT | 13 | 32 | — | — |
| `EGTH-2026` | Especialización en Gerencia Estratégica del Talento Humano (`ESP-GER-TALENTO`) | Especialización | DRAFT | 13 | 26 | — | — |
| `EGSST-2026` | Especialización en Gerencia de la Seguridad y Salud en el Trabajo (`ESP-GER-SST`) | Especialización | DRAFT | 13 | 30 | — | — |
| `DPSI-2026` | Doctorado en Psicología (`DOC-PSICOLOGIA`) | Doctorado | DRAFT | 23 | 80 | — | — |

## What the PDFs cannot answer

These need the university — ideally a SINU export, which would settle most of them at once.
**[`PREGUNTAS-PENDIENTES.md`](PREGUNTAS-PENDIENTES.md)** is the same list written to be taken to a
meeting with the coordination office, in Spanish and with what each gap costs.

1. **Institutional codes.** Only program `506` and the plan numbers 1015, 1017 and 1020 are real.
   The other program codes, the pensum codes of Psicología and the brochures, and every brochure
   course code are placeholders. Many courses are shared across programs under one code (Cálculo I
   is 12015 in Sistemas, Matemáticas and Industrial; the English and Cultura courses repeat in all
   four grids), so several brochure courses probably have a code already. None was matched by name.
2. **Per-course credits** are not printed for Neuropsicología Clínica (33 in total), Maestría en
   Psicología del Consumidor (52), Maestría en IA Aplicada (34), Maestría en Psicología Clínica (59)
   and Psicología Forense (no total either). Administración en SST prints Cultura II and Electiva 1–4
   with the credit cell empty.
3. **Weekly hours** are printed only by the four grids, Marketing and Negocios Internacionales.
4. **Faculties.** The university has four — Facultad de Matemáticas e Ingenierías, Escuela de
   Negocios, Escuela de Posgrados and Facultad de Psicología — and none of the brochures prints
   one. Undergraduate programmes are assigned by discipline and every postgraduate programme to
   the Escuela de Posgrados.
5. **Half hours.** Four practices print 4,5 or 1,5 weekly hours and are stored that way. What is
   left to confirm is whether the halves are real or a slip of the document; the arithmetic says
   real, since keeping them is what makes Psicología add up to its own printed 174.
6. **Prerequisites between electives** are drawn in Matemáticas and Psicología and cannot be stored
   (see above).
7. **Matemáticas.** The coded grid (2019-2) and the brochure disagree on Práctica Profesional (24 vs 22
   hours) and on Enseñanza de las Matemáticas (2 vs 3 credits, where the brochure contradicts its own
   semester total). The grid is loaded; the brochure is the newer document.
8. **Maestría en Psicología Clínica** lists the courses of both emphases (adultos and infantil) in
   semesters I and II. They are loaded together; a student takes one emphasis.
9. **Vigencia.** Sistemas is dated 2019-1 and Industrial 2021-1. Whether they are still the plans new
   students enter under is not something the PDFs say.
