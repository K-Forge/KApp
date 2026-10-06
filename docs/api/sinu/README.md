# What KApp reads from SINU

SINU holds everything academic at Konrad Lorenz. **KApp reads it and never copies it:**

- it reads only the data of the person who signed in;
- it reads on each request, with a short cache;
- it stores nothing academic, and writes nothing back.

The only academic thing KApp keeps is each student's own plan of future semesters. That plan is
KApp's, not SINU's.

This folder says which data KApp needs and in what shape, so the university's IT office (Dirección
de TI) can choose how to deliver it: a web service, a database view or a nightly export. The field
names are KApp's. TI can deliver its own names, and the adapter maps them.

| File | What it is |
|---|---|
| [`example.json`](example.json) | An invented student in this shape. In phase 2 it becomes the test SINU's data |
| [`pacr42-example.pdf`](pacr42-example.pdf) | The same timetable, printed the way SINU prints a student's (report `PACR42_GWT`). Written by [`make-example-pdf.py`](make-example-pdf.py) |

Nothing in either file is real except the program, the pensum and the course codes of Ingeniería de
Sistemas 1015, which are published. A real student's report never goes in the repository. The one
this shape was taken from is kept outside git.

## Who the data belongs to

The person signs in with Microsoft (Entra ID), and KApp asks SINU for that person only. Two things
still have to be confirmed with TI:

- **How a Microsoft account matches a person in SINU.** The institutional email is the expected
  key.
- **What tells a student, a professor and a staff member apart.** This should be an attribute of
  the directory or of SINU.

## The five tables

### 1. Person and program (per person)

| Field | Example | Notes |
|---|---|---|
| `email` | `pepito.perez@konradlorenz.edu.co` | The key, from the Microsoft sign-in |
| `name` | `PEREZ PEREZ PEPITO` | As SINU writes it: uppercase, surnames first |
| `profile` | `STUDENT` | `STUDENT`, `PROFESSOR` or `STAFF` |
| `programCode`, `programName` | `506`, `Ingeniería de sistemas` | |
| `pensumCode`, `pensumName` | `1015`, `Pensum Ingenieria de sistemas vigente` | The pensum version the student follows |
| `currentLevel` | `5` | The report's *Nivel actual* |

KApp does not ask for document numbers, phone numbers or student codes. The report prints them,
but KApp has no use for them.

### 2. Timetable (per person and period)

One row per course and group, which SINU calls an *asignatura*. Each row has its weekly meetings,
and each meeting has the date ranges it is taught in.

| Field | Report | Example |
|---|---|---|
| `sectionCode` | `Cod.` | `3101` |
| `sinuCode`, `courseName` | `Asignatura` | `13013`, `ECUACIONES DIFERENCIALES` |
| `level`, `credits`, `totalHours` | `Nivel`, `Cred.`, `Hor.` | `5`, `3`, `64` |
| `group`, `subgroup` | `Grupo.`, `SubGrupo.` | `21`, `null` |
| `professor` | `Docente` | `DOCENTE EJEMPLO UNO` |
| `sede` | `Sede` | `Sede Principal` |
| `meetings[].dayOfWeek`, `startTime`, `endTime` | The grid's column, and the time at the foot of the cell | `MONDAY`, `07:00`, `08:30` |
| `meetings[].blocks` | Not printed: worked out from the times | `2` |
| `meetings[].periods[].from`, `to`, `room` | Each `dd/mm/yy - dd/mm/yy` and the `Aula.` under it | `2027-02-01`, `2027-03-15`, `402` |

The report follows these rules, and the example does too:

- **Blocks are 45 minutes.** 6:15 pm to 9:15 pm is four blocks. `totalHours` is the weekly blocks
  times 16: four blocks a week give 64.
- **Ranges are inclusive.** A range never overlaps another of the same meeting, and both of its
  ends fall on the meeting's weekday. A one-day range has `from` equal to `to`.
- **`Aula. -` means no classroom was assigned** for that range, and becomes `room: null`. It is a
  real state, not a missing value.
- **The room can change between ranges.** In the example, Diseño de Interfaces moves from 710 to
  711.
- **A course can meet on more than one day.** In the example, Ecuaciones Diferenciales meets on
  Monday and Wednesday.
- **In SINU every building is a sede.** A room number identifies a room only together with its sede:
  there is a 301 in more than one building. The portal keeps the table that maps each sede to a
  building on the map.
- **A professor gets the sections they teach,** in the same shape.

This is the `Section` of [`schedule.openapi.yaml`](../schedule.openapi.yaml) 2.0.0. KApp adds
`pensumItemCode`, `buildingCode` and `color`, and works out `startDate` and `endDate` from the
ranges.

### 3. Course records (per person)

One row for each item of the pensum that SINU has a record of.

| Field | Example | Notes |
|---|---|---|
| `sinuCode`, `name` | `31506`, `LÓGICA DIGITAL Y MICROPROCESADORES` | |
| `status` | `PERDIDA` | Exactly as SINU writes it |
| `period` | `20262` | When it was taken |
| `grade` | `24` | **Optional.** On the `0..50` scale. The semáforo works without it |

KApp maps each status to one of the semáforo's own. The spellings below are a guess until TI sends
SINU's list:

| SINU | Semáforo |
|---|---|
| `APROBADA` | `PASSED` |
| `EN CURSO` | `IN_PROGRESS` |
| `PERDIDA` | `FAILED` |
| `APLAZADA` | `POSTPONED`, which counts as not passed until TI says how SINU uses it |
| (no record) | `PENDING` |

An unknown status is kept as it came, in `sinuStatus`. The semáforo shows it rather than guessing.

### 4. Catalog (public)

Programs, and pensums with their items. Each item has its code, name, level, credits, weekly
hours, area, prerequisites, and whether it is an elective slot. Until SINU serves it,
[`docs/pensums/`](../../pensums/) is the backup: 23 plans transcribed from the published PDFs.

### 5. Elective bank (public, per period)

The courses offered in a period to fill elective slots.

| Field | Example | Notes |
|---|---|---|
| `sinuCode`, `name` | `59217`, `SEGURIDAD DE LA INFORMACIÓN` | |
| `credits`, `weeklyHours` | `3`, `3` | |
| `slots` | `["59096", "59098"]` | The elective slots it can fill. Empty means any |

## What one real report showed

These observations come from the report of 2 October 2026 for period 20262:

- **Every class said `Sede Principal`.**
- **Rooms changed between ranges, and some ranges had `Aula. -`.**
- **The elective was listed under the slot, not under the course:** `59098 ELECTIVA VI`. Which
  course fills the slot has to come from somewhere else. This is a question for TI.
- **Estadística Descriptiva is `17080` in SINU,** while the 1015 transcription says `17018`. The fix
  is in phase 2 (#55).
- **Taller de Emprendimiento has 2 credits in the report** and 3 in the transcription. This is to be
  checked against the pensum.

## Not asked

- No writes of any kind. Enrolment stays in SINU.
- Nothing financial: payments, debts or tuition.
- No document numbers, phone numbers or student codes.
- No data of anyone other than the person who signed in. KApp's administrators do not read it
  either.
- No averages. The grade of each course is optional.

## Questions for TI

1. What is SINU's list of course statuses, and what does each one mean? `APLAZADA` above all.
2. How is an elective recorded: under the slot, the course, or both?
3. How does a Microsoft account match a person in SINU?
4. What is the list of sedes, and which building is each one?
5. In what form can the data be delivered, and how fresh is it?
