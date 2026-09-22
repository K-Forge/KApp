# Import templates

## `pensum-import-template.csv`

One row per pensum item. Everything before `pensumItemCode` describes the pensum and is
**repeated on every row of that pensum** — that is how a spreadsheet works, and the import
checks the repeated values agree with each other rather than letting the first row silently win.

One file may carry several pensums; group the rows by `pensumCode` however you like.

Post it as `multipart/form-data`:

```bash
curl -X POST 'http://localhost:8080/api/catalog/pensums/import?dryRun=true' \
  -H "Authorization: Bearer $TOKEN" \
  -F 'file=@docs/templates/pensum-import-template.csv'
```

**Always run it with `dryRun=true` first.** It validates the whole file and writes nothing.

| Column | Notes |
| --- | --- |
| `programCode` … `programLevel` | The program. `programLevel` is one of PREGRADO, POSGRADO, TECNOLOGIA, MAESTRIA, DOCTORADO, CURSOS_DIPLOMADOS, ESPECIALIZACION. |
| `pensumCode`, `reform` | Identify the plan. |
| `pensumStatus` | DRAFT, ACTIVE or OBSOLETE. Import as DRAFT and activate deliberately. |
| `declaredCredits`, `declaredHours` | What the official document says. **Checked, not trusted** — see below. `declaredHours` is the sum of **weekly** hours across the plan, not hours per semester: the Ingeniería de Sistemas grid adds up to 194. |
| `levels` | How many semesters the plan has. |
| `areaCode`, `areaName`, `areaColor` | The semáforo row this item belongs to. Name and colour need only be right on the first row that mentions the area. Area totals are **summed from the courses**, so there is nothing to transcribe. |
| `pensumItemCode` | Stable identifier of the square in the grid. Must be unique within the pensum. |
| `courseCode` | The institutional course code. **Leave empty for an elective slot** — a slot is not a course yet. |
| `courseName`, `courseLevel`, `credits`, `weeklyHours` | The item itself. Total hours are derived as `weeklyHours × 16`. `weeklyHours` may be a half — `4.5`, or `"4,5"` **with the quotes**, since an unquoted comma splits the cell in two. Nothing finer than a half is accepted. |
| `isElectiveSlot` | `true` or `false`. Also accepts `1`/`0`, `si`/`no`, `x`. |
| `prerequisites` | **Semicolon-separated**, not comma — a comma would need quoting and spreadsheets drop the quotes on a round trip. Each value must be a `courseCode` present in the same pensum. |
| `sinuCode` | Optional. Leave empty until the university's own export provides it. |

### The totals are checked

The import adds up the courses and compares against `declaredCredits` and `declaredHours`. If
they disagree, **nothing is written** and the report says both numbers.

`declaredHours` follows the same rule as the courses it is compared against: it may carry a half,
in either spelling. The four practices that print one are in
[`docs/pensums/PREGUNTAS-PENDIENTES.md`](../pensums/PREGUNTAS-PENDIENTES.md).

That check exists because it already caught something. The first Ingeniería de Sistemas seed,
reconstructed from a drawing, declared 142 credits and 194 weekly hours while its 48 courses added
up to **144 and 197**, and nobody noticed until the import was written. Finding that out at import
time is much cheaper than finding out when a student's semáforo does not add up.

The plans the university publishes are already loaded; see [`docs/pensums/`](../pensums/README.md).

Area credits and hours are not transcribed at all — they are summed from the courses of that
area, so there is one fewer number to get wrong.

### Nothing is written unless everything passes

Errors are reported against the line number of the file, so a failed import is a list of things
to fix in the spreadsheet. A partial import would leave the catalogue in a state nobody chose.
