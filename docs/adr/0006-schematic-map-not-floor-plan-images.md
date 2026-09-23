# ADR 0006 — A schematic map drawn from data, not floor plan images

- **Status:** Accepted and implemented
- **Date:** 2026-09-05

## Context

`map-service` modelled a floor as a **photograph**: each floor carried `planImageUrl` plus the
image's pixel dimensions, and each space was pinned at an `x`/`y` percentage of it. The client drew
the image and placed pins on top.

That model needs architectural plans for five buildings. Obtaining them depends on the Dirección de
TI's calendar, on whoever holds the drawings, and on somebody exporting them at a usable
resolution — and `PROGRESS.md` had recorded it for weeks as **the longest-lead item on the project
and the one most likely to slip before November**.

Then the mobile mockups arrived, and they did not draw a photograph. They drew rooms as coloured
rectangles on a grid, corridors as coloured segments, and a card reading *"piso 4 · sube por el
ascensor central"*.

## Decision

A floor is **data**: `gridRows` × `gridColumns`, with each space occupying a rectangle of cells and
corridors tracing polylines through it. `planImageUrl` and the pin percentages are gone.

## Rationale

**It removes the dependency that was going to slip.** A schematic floor is captured by walking it
with a grid editor — counting squares, marking the lifts, tracing the corridors. That is an
afternoon of work the team can do itself, repeated across forty floors, without asking anyone's
permission or waiting for anyone's reply. It converts human latency into effort, which is the trade
worth making when the deadline is fixed.

**The mockups were already drawing it this way.** Implementing the image model would have meant
building something the design does not show.

**It makes three things expressible that the old model could not say at all:**

- **Wings as a field.** `301`, `301-N` and `301-S` are three different rooms on one floor. Search
  can filter on `wing`, and `baseCode` lets a student who types what they were told — without the
  wing — find all three. Parsing this out of the code at query time would break the first time a
  building names its wings anything else.
- **`accessVia`.** The code of the lift, staircase or entrance that serves a space. This is what
  produces *"sube por el ascensor central"*. It is stored, not derived from grid distance: whoever
  walks the floor knows which lift people actually use, and nearest-by-distance gives the wrong
  answer whenever a wall sits between them.
- **A basement at level −1**, where the rule that a room's first digit is its floor stops applying.

**Two rules become enforceable.** A space must fit inside its floor's grid, and two spaces may not
occupy the same cell. Neither fails visibly once stored — a space outside the grid does not render,
an overlapped one is drawn underneath — so both are refused at write time, in the editor and again
at the API.

## Alternatives considered

**Keep the image model and wait for the plans.** The status quo. Rejected: it kept the project's
riskiest dependency on the critical path for a rendering the mockups do not ask for.

**Support both, with the image as an optional background.** Tempting, and the compromise that looks
free. Rejected because it is not free: two coordinate systems, two editors, and the question "which
one is authoritative when they disagree?" answered nowhere. If real plans arrive later they can be
shown *behind* the grid without either model owning the coordinates.

**Derive `accessVia` from proximity instead of storing it.** Rejected above: confidently wrong
whenever a wall intervenes, and confidently wrong is worse than absent for directions.

## Consequences

- `V002_PlaceholderCampusSeed` could not be rewritten in place — Mongock had already recorded it as
  executed, so a rewritten body would have seeded nothing on exactly the machines needing the new
  data. It is emptied, and `V003_SchematicCampusSeed` removes what it wrote and seeds the new model,
  so an existing volume and a fresh one converge.
- The pin editor is deleted and replaced by `static/admin/grid-editor.html`, which enforces the same
  two rules so a mistake surfaces while somebody is still standing in the building.
  *Later:* that file was retired in favour of the floor editor in the admin portal
  (`/data/floors`), which saves a whole floor through the API instead of exporting JSON to paste.
- The admin portal's building and space forms had to be rewritten; until they were, editing either
  through the portal would have failed with a 400.
- **Still needed from a person:** one floor sketched or photographed, to model the first one and
  confirm the shape is right. The other thirty-nine are then captured with the editor.
