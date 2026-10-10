#!/usr/bin/env python3
"""
Prints example.json's timetable the way SINU prints a student's (the PACR42_GWT report), so the
repository shows what the report looks like without anybody's real one.

    docs/api/sinu/make-example-pdf.py

Writes pacr42-example.pdf next to this file, through Google Chrome without a window; $CHROME
names another Chromium-based browser. Standard library only.
"""
import datetime
import html
import json
import os
import pathlib
import subprocess
import tempfile

HERE = pathlib.Path(__file__).resolve().parent
CHROME = os.environ.get("CHROME", "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")
DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]
HEADERS = ["LUNES", "MARTES", "MIERCOLES", "JUEVES", "VIERNES", "SABADO", "DOMINGO"]
MONTHS = ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre",
          "octubre", "noviembre", "diciembre"]


def clock(hhmm):
    """'18:15' as the report writes it: '6:15 pm'."""
    h, m = map(int, hhmm.split(":"))
    return f"{(h - 1) % 12 + 1}:{m:02d} {'am' if h < 12 else 'pm'}"


def short(iso):
    """'2027-02-01' as '01/02/27'."""
    return datetime.date.fromisoformat(iso).strftime("%d/%m/%y")


def cell(section, meeting, program):
    e = html.escape
    lines = [f"Cod. {e(section['sectionCode'])}", f"Prog. {e(program)}",
             f"<b>{e(section['sinuCode'])} {e(section['courseName'])}</b>",
             f"Grupo. {e(section['group'])}", f"SubGrupo. {e(section['subgroup'] or '')}", ""]
    for p in meeting["periods"]:
        lines += [f"{short(p['from'])} - {short(p['to'])}", f"Aula. {e(p['room'] or '-')}", ""]
    lines.append(f"{clock(meeting['startTime'])}-{clock(meeting['endTime'])}")
    return "<br>".join(lines)


def grid(sections, program):
    meetings = [(s, m) for s in sections for m in s["meetings"]]
    times = sorted({t for _, m in meetings for t in (m["startTime"], m["endTime"])})
    starts = {(m["dayOfWeek"], m["startTime"]): (s, m) for s, m in meetings}
    busy = set()
    rows = []
    for i, t in enumerate(times):
        tds = [f'<td class="hour">{clock(t)}</td>']
        for day in DAYS:
            if (day, t) in busy:
                continue
            if (day, t) in starts:
                s, m = starts[(day, t)]
                span = times.index(m["endTime"]) - i
                busy.update((day, x) for x in times[i:i + span])
                tds.append(f'<td class="class" rowspan="{span}">{cell(s, m, program)}</td>')
            else:
                tds.append("<td></td>")
        rows.append("<tr>" + "".join(tds) + "</tr>")
    head = "".join(f"<th>{h}</th>" for h in ["HORAS"] + HEADERS)
    return f'<table class="grid"><tr>{head}</tr>{"".join(rows)}</table>'


def detail(sections, person):
    head = ["Grupo", "SubGrupo", "Fec ini.", "Fec. fin", "Nivel", "Hor.", "Cred.", "Cód. programa",
            "Cód. pensum", "Docente", "Sede"]
    rows = []
    for s in sorted(sections, key=lambda s: s["sectionCode"]):
        froms = [p["from"] for m in s["meetings"] for p in m["periods"]]
        tos = [p["to"] for m in s["meetings"] for p in m["periods"]]
        values = [s["sectionCode"], f"{s['sinuCode']} {s['courseName']}", s["group"], s["subgroup"] or "",
                  short(min(froms)), short(max(tos)), s["level"], s["totalHours"], s["credits"],
                  person["programCode"], person["pensumCode"], s["professor"], s["sede"]]
        rows.append("<tr>" + "".join(f"<td>{html.escape(str(v))}</td>" for v in values) + "</tr>")
    return ('<table class="detail"><tr><th colspan="2">Asignatura</th>' + "".join(f"<th>{h}</th>" for h in head) + "</tr>"
            + "".join(rows) + "</table>")


def page(example, printed):
    person, timetable = example["person"], example["timetable"]
    sections = timetable["sections"]
    date = f"{printed.day:02d} de {MONTHS[printed.month - 1]} de {printed.year}"
    return f"""<!doctype html><html lang="es"><head><meta charset="utf-8">
<title>Horario del estudiante: ejemplo inventado para KApp</title><style>
@page {{ size: letter; margin: 10mm 12mm; }}
body {{ font-family: "Times New Roman", Times, serif; font-size: 9pt; color: #000; }}
.banner {{ border: 1.5pt solid #c00; color: #c00; font: bold 7.5pt Arial, sans-serif;
          text-align: center; padding: 3pt; margin-bottom: 8pt; }}
.top {{ display: flex; justify-content: space-between; }}
.top .mid {{ flex: 1; text-align: center; }}
.top .right {{ text-align: right; font-size: 8pt; }}
.who {{ display: flex; justify-content: space-between; margin: 10pt 0 8pt; font-size: 9.5pt; }}
.who b {{ font-family: Arial, sans-serif; }}
.who .code {{ font-size: 13pt; }}
table {{ border-collapse: collapse; width: 100%; }}
th, td {{ border: 0.5pt solid #999; vertical-align: top; }}
.grid th {{ font: 7.5pt Arial, sans-serif; padding: 3pt; }}
.grid td {{ font-size: 6.5pt; line-height: 1.1; padding: 1.5pt 3pt; width: 12%; height: 11pt; }}
.grid td.hour {{ font: 7.5pt Arial, sans-serif; text-align: center; vertical-align: middle; width: 8%; }}
h3 {{ font: bold 9pt Arial, sans-serif; margin: 10pt 0 6pt; }}
.prog {{ margin: 4pt 0; }} .prog b {{ font-family: Arial, sans-serif; display: inline-block; width: 60pt; }}
.detail th {{ font: 6.5pt Arial, sans-serif; padding: 1pt 2pt; }}
.detail td {{ font: 6.5pt Arial, sans-serif; padding: 1pt 2pt; }}
.note {{ color: #c00; font-size: 7.5pt; text-align: center; margin-top: 12pt; }}
</style></head><body>
<div class="banner">EJEMPLO INVENTADO PARA KAPP. No sale de SINU y no es un documento de la universidad:
la persona, los docentes, los códigos de clase y las aulas son ficticios.</div>
<div class="top"><div class="mid">FUNDACION UNIVERSITARIA KONRAD LORENZ<br>Horario del estudiante</div>
<div class="right">{date}<br>{printed:%H:%M:%S}<br>PACR42_GWT</div></div>
<div class="who"><div><b>Periodo:</b> &nbsp; {timetable['period']}<br>
<b>Estudiante:</b> 0000000000 &nbsp; {html.escape(person['name'])}</div>
<div style="text-align:right"><b>Nivel actual: {person['currentLevel']}</b><br>
<span class="code">Código: 000000000</span></div></div>
{grid(sections, person['programCode'])}
<h3>Detalle de las Materias:</h3>
<div class="prog"><b>Programa:</b> {person['programCode']} {html.escape(person['programName'])}</div>
<div class="prog"><b>Pensum:</b> {person['pensumCode']} {html.escape(person['pensumName'])}</div>
{detail(sections, person)}
<div class="note">NOTA: LA INFORMACIÓN PRESENTADA EN ESTA PÁGINA SOLO ES DE CONSULTA Y NO SE CONSIDERA UN
DOCUMENTO OFICIAL</div>
</body></html>"""


def main():
    example = json.loads((HERE / "example.json").read_text(encoding="utf-8"))
    printed = datetime.datetime(2027, 2, 15, 10, 30, 0)
    out = HERE / "pacr42-example.pdf"
    with tempfile.TemporaryDirectory() as tmp:
        source = pathlib.Path(tmp) / "pacr42-example.html"
        source.write_text(page(example, printed), encoding="utf-8")
        subprocess.run([CHROME, "--headless", "--disable-gpu", "--no-pdf-header-footer",
                        f"--print-to-pdf={out}", source.as_uri()],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    print(f"wrote {out.relative_to(HERE.parents[2])}")


if __name__ == "__main__":
    main()
