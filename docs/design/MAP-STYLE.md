# Indoor Map Style

> How every KApp client draws a floor: the Android app, the iOS app and the admin portal. The values live in
> [`map-style.json`](map-style.json); this page explains them. Parent issue: the indoor map renderer.

## The rule: clients paint the map, they do not transcribe it

A floor is data that map-service serves (`GET /api/map/buildings/{code}/floors/{floorCode}`, map 3.7.0). Every
client has **one generic renderer** that knows nothing about any particular building, floor or room. It draws
whatever the response holds, with the style in this folder. So a floor redrawn in the portal reaches the apps without
a release, and the Android and iOS maps look the same.

What follows from it:

- **No space, floor or building is ever named in client code.** If a screen needs to treat one differently, that is
  data: a type, a category or a field in the contract.
- **Colours and icons come from `category` and `typeCode`, never from `code`.** Categories are a closed list in the
  contract; types are added from the portal. A type with no entry in `typeIcons` simply has no icon, and a category
  a client does not know is drawn as `fallbackCategory`.
- **The server owns the geometry.** Clients do not regularize, simplify or fix shapes. If a shape looks wrong, the
  fix is in the data (map-service and the portal's floor editor), not in a client.
- **Clients cache by `ETag` and `version`**, and redraw only when a floor changes.

## Visual reference

Flat, clean 2D indoor maps in the style of Mappedin (Brian's choice, 2026-11-04), not 3D ones:

- the floor is white and reads as the walkable area;
- spaces are flat fills by category, with a thin white stroke so neighbours that share a wall stay apart;
- the building's outline is a darker line around everything;
- labels sit inside their space, with a halo;
- amenities (elevator, stairs, restroom, cafeteria and so on) carry a small round icon;
- a level switcher sits on top of the map, and a selected space is outlined in the institutional pink.

The category fills are the ones the portal's floor editor already uses (`CATEGORY_COLORS` in
`app/frontend/web-admin/src/app/features/data/spaces/space.model.ts`), so the portal and the apps agree.

## Drawing order

`layers` lists the order, bottom to top:

1. **`floor`:** the floor's `outline`, filled. Outside it is `background`.
2. **`spaces`:** each placed space's `shape`, filled by its category, with the `spaces` stroke.
3. **`corridors`:** the corridor polylines, as wide soft strokes.
4. **`doors`:** each door's stretch of the outline, drawn as a gap in the wall.
5. **`outline`:** the building's walls, on top of the spaces.
6. **`icons`:** at the label anchor, for types in `typeIcons`.
7. **`labels`:** at the label anchor, below the icon when there is one.
8. **`selection`:** the selected space, on top of everything.

## Labels

- **Text:** `doorCode`, else `name`. Never `code`; the contract explains why.
- **Anchor:** `labelPoint` (map 3.7.0): the point inside the shape farthest from its walls, computed by the server.
  Never the centre of `bounds`, which can fall outside an L-shaped space; use it only as a fallback when a server
  does not send `labelPoint` yet.
- **Fit:** a label wider than its space is hidden at that zoom, and appears when zooming in. When labels collide,
  the one earlier in `labels.priority` wins.
- **Size:** between `minSizeSp` and `maxSizeSp`, growing with zoom.

## Units and themes

- A floor is in **drawing units**, not metres, and they differ between floors. Scale the whole drawing to the
  screen; never compare two floors' numbers.
- `Dp` and `Sp` values are density-independent pixels, the same on Android and in iOS points.
- Every colour has a `light` and a `dark` value. Clients follow the system theme.

## Changing the style

`map-style.json` is the single source. The clients keep a copy in their assets, or generate constants from it at
build time, and each pull request that changes it also updates every client that copies it. Bump `version` when a
client has to change code to follow, not for a colour tweak.

## Later

- **Point-to-point routes:** the server computes the route and the client draws it with `route`. That needs walkways
  as spaces, openings that connect units to walkways, and vertical connections between levels (backend phase 5
  follow-ups).
- **The campus outside:** `GET /api/map/campuses/{campus}/ground` already serves the streets in WGS 84. If the
  outdoor map grows, a map engine such as MapLibre, which can read one style on Android, iOS and the web, is the
  option to evaluate.
