# Helpers from tracing the campus

One-off scripts written while each building was traced from its evacuation plans (PE) into one
frame. They are kept so a floor can be traced again the same way, not maintained as tools: each
says at its top what it takes and what it prints, and each loads `../trace.py` for reading photos.
The ones that earn a place move into `trace.py` or `lines.py`.

| Script | What it does |
|---|---|
| `lot.py`, `face.py`, `scanface.py` | Fit the lines of a plan photo - the lot's streets, a building's outer faces - and print where they cross, to lay a photo in its building's frame |
| `pe_spec.py`, `bi_spec.py`, `cpc_spec.py`, `jaab_spec.py` | Write a `trace.py` spec in a building's frame from those crossings |
| `migrate.py` | Carry an old spec's points into a new frame, through the photo both are laid on |
| `pe_fit.py` | Put the edges of rectangular rooms on the edge of the plan's orange |
| `pe_check.py` | Score a trace against its photo: how much of the orange its rooms cover, and where they miss |
| `scan.py`, `obox.py`, `aud.py` | Outline the orange inside a box: an odd-shaped room, the auditorium |
| `profile.py`, `mag.py` | Profiles across a strip of the photo, to find a wall where the tracer did not |
| `overlaps.py` | Spaces of a floor whose shapes share area, before the snapshot load refuses them |
