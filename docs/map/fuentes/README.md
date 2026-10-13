# Map sources

The photos Brian took of the plans on the campus walls, which the map is drawn from. What each one
says is transcribed in [`../LEVANTAMIENTO.md`](../LEVANTAMIENTO.md) and its building documents.

## Folders and names

One folder per building, with the same names as [`../levantamiento/`](../levantamiento/): `ec`,
`cpc-1`, `jaab`, `bi`, `mu` and `ea`. A photo is `BUILDING-FLOOR-KIND.jpg`:

| Kind | What it is |
|---|---|
| `PE` | The evacuation plan (*plano para evacuación*). It is the most recent drawing of the floor's shape, but not to scale |
| `PI` | The information plaque (*placa informativa*) by the stairs: what is on each floor |
| `RS` | The sanitary route plan (*plano de ruta sanitaria*). It is older, but it draws the doors |

A photo somebody has drawn on keeps the name of the plan underneath and adds what the drawing
covers. For example, `ec/EC-AU-P1-PE.jpg` is the auditorium marked by hand over the P1 evacuation
plan, which `scripts/plan-tracing/strokes.py` reads.

## How they are made

[`scripts/plan-tracing/export-sources.py`](../../../scripts/plan-tracing/export-sources.py)
writes them from the originals in `~/Desktop/map`:

- each photo is turned upright;
- the long side is at most 2000 px, at about 400 KB;
- nothing is kept but the picture and its colour profile. A phone writes where the photo was
  taken (GPS), the device and the time into every photo, and none of that is kept.

Run it again when new photos arrive, and commit what changed.

## What is not here

- **The full-size originals.** They stay in `~/Desktop/map`, and the tracing scripts read them
  from there.
- **The views of the buildings,** with `VISTA` in their names. They are screenshots of Street View
  and of the university's own photos and renders, which are not ours to publish.
- **Anything with personal data.** Documents with a name, an ID number or a student code stay in
  `~/Desktop/KApp-privado/`, outside git.
