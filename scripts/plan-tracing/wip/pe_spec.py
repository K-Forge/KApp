# pe_spec.py <floor> '<lot corners json from lot.py>' [photo name]
# Writes ec-pe-<floor>.spec.json for trace.py: the floor's PE photo laid into the Edificio Central's
# frame by the four corners of the lot its streets draw - Calle 63 and Calle 62 plumb, Carrera 9A
# level, sized as the P3 plan draws them - so every floor traced lands in the same place.
import importlib.util, json, sys
spec = importlib.util.spec_from_file_location('t', '/Users/13rian/Development/3-K-Forge/KApp-worktrees/backend/scripts/plan-tracing/trace.py')
T = importlib.util.module_from_spec(spec); spec.loader.exec_module(T)

# The lot in the frame, from P3's photo: bottom 2090 wide, left side 2590 tall, right side 1879.
LOT = {'tl': (100, 110), 'tr': (2190, 821), 'br': (2190, 2700), 'bl': (100, 2700)}
PLAQUE = (2290, 2800)
CROP = (150, 350, 2250, 2620)          # the part kept: the building, with room round it

floor = sys.argv[1]
lot = json.loads(sys.argv[2])
photo = sys.argv[3] if len(sys.argv) > 3 and sys.argv[3] else None
# A plan that leaves out the lot's west line is laid by the building's west facade instead:
# frame x of that line given as the fourth argument, where it meets the top line and the bottom.
if len(sys.argv) > 4:
    X = float(sys.argv[4])
    LOT = dict(LOT, tl=(X, 110 + (X - 100) * 711 / 2090), bl=(X, 2700))
names = ['tl', 'tr', 'br', 'bl']
hm = T.homography([LOT[n] for n in names], [lot[n] for n in names])


def to_photo(u, v):
    z = hm[2][0] * u + hm[2][1] * v + hm[2][2]
    return [round((hm[0][0] * u + hm[0][1] * v + hm[0][2]) / z, 1), round((hm[1][0] * u + hm[1][1] * v + hm[1][2]) / z, 1)]


pw, ph = PLAQUE
out = {
    "photo": photo or f"Edificio Central/EC-{floor}-PE.jpeg",
    "resample": 5600,
    "lot": lot,
    "corners": [to_photo(0, 0), to_photo(pw, 0), to_photo(pw, ph), to_photo(0, ph)],
    "plaque": [pw, ph],
    "crop": [CROP[0] / pw, CROP[1] / ph, CROP[2] / pw, CROP[3] / ph],
    "scale": 1.0,
    "light": "balanced",
    "floor": {"building": "EC", "code": floor, "spaces": {}},
}
json.dump(out, open(f'ec-pe-{floor.lower()}.spec.json', 'w'), indent=1)
print(json.dumps(out["corners"]))
