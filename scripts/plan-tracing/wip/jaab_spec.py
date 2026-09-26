# bi_spec.py <floor> <photo> '<corners json: tl tr br bl of the building's outer faces, as the floor
# is drawn: west wall left, the top of the north block up>' : a trace.py spec in Bienestar's frame.
import json, sys
floor, photo, c = sys.argv[1], sys.argv[2], json.loads(sys.argv[3])
PW, PH, M = 1420, 600, 15
spec = {"photo": photo, "resample": 2800,
        "corners": [c['tl'], c['tr'], c['br'], c['bl']], "plaque": [PW, PH],
        "crop": [-M / PW, -M / PH, (PW + M) / PW, (PH + M) / PH], "scale": 1.0, 
        "floor": {"building": "JAAB", "code": floor, "spaces": {}}}
json.dump(spec, open(f'jaab-pe-{floor.lower()}.spec.json', 'w'), indent=1)
