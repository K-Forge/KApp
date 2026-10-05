# cpc_spec.py <floor> <photo> '<corners json tl tr br bl>' : a trace.py spec in CPC 1's frame, laid by the
# outer faces of four walls every floor has, in floors 2, 4 and 5's orientation: the emergency
# stair's outer wall (left), the main stair's outer wall (top), the east end wall (right) and the
# bottom wall under the lift's block. Floors 1 and 3 hang turned half a turn: their corners are given
# already turned (tl is the photo's bottom-right).
import json, sys
floor, photo, c = sys.argv[1], sys.argv[2], json.loads(sys.argv[3])
PW, PH = 1060, 460
U0, V0, U1, V1 = -650, -90, 1100, 560
spec = {"photo": photo, "resample": 2800,
        "corners": [c['tl'], c['tr'], c['br'], c['bl']], "plaque": [PW, PH],
        "crop": [U0 / PW, V0 / PH, U1 / PW, V1 / PH], "scale": 1.0,
        "floor": {"building": "CPC1", "code": floor, "spaces": {}}}
json.dump(spec, open(f'cpc-pe-{floor.lower()}.spec.json', 'w'), indent=1)
