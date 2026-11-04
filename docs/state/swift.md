# Swift State

> Owner: Swift. As of 2026-11-04. Built from Swift's report of 2026-10-29. Nothing has changed since: Swift is
> paused.

---

## Status

**Paused since 2026-10-13**, by the decision to finish Android first. Swift resumes when the Android app is done.

- **Branch:** `feature/mobile-swift` (at `a5031fc`). It is clean and has no pull request, and it is **105 commits
  behind `develop`**.
- **Built:**
  - against the old mocks, from before the 2.0 contracts;
  - password sign-in, Home, Timetable and Semáforo (both still write data), Profile with sign-out only, and Map as a
    placeholder;
  - 67 tests;
  - SwiftUI, iOS 17, Xcode 27.1.
- **The gap to the current contracts:**
  - #135 auth 1.0: Microsoft sign-in and session renewal;
  - #137 user 1.0: profiles without documents, roles as a list;
  - #133 schedule 2.0: a read-only timetable from SINU;
  - #131 semaphore 2.0: a read-only semáforo from SINU, and plans.
- **Git debt, Brian's decision:** commits `751a9c2` and `4adfe74` use the type `ios:` and carry a tool trailer.
  Both break CONTRIBUTING. Either rewrite them, or start a clean branch from `develop` and port the code.
- **Lost design:** the Semáforo redesign exists only as a private page Brian owns. The local HTML was deleted.
  Ask Brian for it before redrawing that screen.

---

## Plan to resume

1. Brian decides about the git debt. The likely path is a clean `feature/` branch from `develop`.
2. Follow the order Kotlin took: auth (#135), then user (#137), then schedule (#133), then semaphore (#131). One pull
   request per contract, each closing its `[Swift]` sub-issue by hand.
3. Reuse Kotlin's demo approach, a backend served from assets, so iOS can be shown without a server.
4. A cloud container has no Xcode, so iOS builds need Brian's Mac, or a macOS CI job that does not exist yet.
