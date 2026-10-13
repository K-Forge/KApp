# Microservice Roadmap and Ideas — KApp

This document has two parts. **The roadmap** lists the new services Brian decided to build on 3 October 2026, with
their status. **The idea bank** below it keeps every other proposal the team has made, so that nothing is lost and the
next decision has something to choose from. An idea is not a commitment; a roadmap entry is.

---

## Roadmap

| Service | Status | What it is |
|---|---|---|
| `notification-service` | **Required** | Inbox plus push: FCM, and APNs once an Apple Developer account exists. Per-topic preferences. Cross-cutting: every other service notifies through it |
| `points-service` | For the final delivery | Citizenship points, visible to their owner and to the university. Not redeemable yet, and a balance may go negative |
| `voice-service` | For the final delivery | Student voice: complaints and suggestions. Not anonymous — the author chooses to show their name or their programme, and moderation always sees who wrote it. No naming people. Word filter, reports from users and human moderation. Votes and comments of 20 to 200 words. No official replies |
| `report-service` | For the final delivery | Damage reports with a location on the map, a photo and a short description. The same votes, comments and reports as the voice. Maintenance validates them |
| `lost-and-found-service` | For the final delivery | Reception keeps the objects. Hand-in and claim with one-time codes that reception generates in KApp. The history is never deleted. No photos of documents, IDs or cards |
| `event-service`, `club-service`, `recreation-bookings` | After a questionnaire to the offices | Depend on how Bienestar, the clubs and reception work today, which is asked first |

**How they are built.** One service per feature. Everything that crosses services goes through a **transactional
outbox**: the change and the message are written in the same MongoDB transaction, consumers use idempotency keys, and
there is no message broker. A service that only needs to notify writes to its outbox, and `notification-service`
delivers.

**Where the work is tracked.** `notification-service` is backend phase 7 (#60). Points, voice, damage reports and lost
and found are backend phase 8 (#61). The office questionnaire comes before any issue for the last row.

**Personal data.** The voice posts carry a name, the lost-and-found history is kept, and the reports carry photos. All
three fall under Ley 1581, and their retention and access rules are written into `docs/REQUIREMENTS.md` before they are
built.

### Already built

Two ideas from the bank below became the current platform. They are listed here so nobody proposes them again:

| Idea | Service | Notes |
|---|---|---|
| Interactive Map | `map-service` | Buildings, floors drawn as polygons, spaces and search |
| Student Academic Traffic Light | `semaphore-service` | Reads the student's progress from SINU and never copies it; the student only edits their plans |

---

## Idea Bank

### How to propose an idea

1. **Add a row to the table** below, filling in every field.
2. **Place it by the Impact × Difficulty matrix**: first **high impact, low difficulty** (quick wins), then **high
   impact, high difficulty** (strategic projects), then **low/medium impact, low difficulty** (filler), and finally
   **low/medium impact, high difficulty** (avoid or postpone).
3. **Add your name or GitHub handle**, so the team knows who to ask about it.
4. **Open a pull request** titled `docs: add the <service> idea`, following `CONTRIBUTING.md`.

> **Tip:** if you are unsure about a field, such as the difficulty, make your best estimate. Brainstorming is for
> exploring, not for certainty.

> [!IMPORTANT]
> **Ordering rule:** ideas are always sorted by **descending impact** (High → Medium → Low), breaking ties by
> **ascending difficulty** (Low → Medium → High). Insert a new idea in its category; never append it at the end.

> [!NOTE]
> **Before building any of these, check the scope.** KApp **reads SINU and never copies it**, and it explicitly
> leaves out chat, payments, personal documents, enrolment and anything that writes to SINU (`docs/REQUIREMENTS.md`,
> `docs/SINU-ALIGNMENT.md`). Several ideas below fall in those areas — Per-Course Chat, Parking (payments), Digital ID
> Card (documents), Academic Procedures and Library loans (SINU's domain). They stay recorded as ideas, but building
> one means revisiting that decision first.

### Impact × Difficulty Matrix

|                        | Low Difficulty                              | Medium/High Difficulty                        |
| ---------------------- | ------------------------------------------- | --------------------------------------------- |
| **High Impact**        | **Quick wins** <br> _Do first_              | **Strategic projects** <br> _Plan ahead_      |
| **Medium/Low Impact**  | **Filler** <br> _Do when there is time_     | **Avoid or postpone** <br> _Low priority_     |

### Registered Ideas

<!-- IMPORTANT: NOTE FOR AI AGENTS AND CONTRIBUTORS:
  The rows of this table MUST ALWAYS be sorted by the Impact x Difficulty matrix:
    1. Quick wins — High Impact + Low Difficulty (first)
    2. Strategic projects — High Impact + Medium/High Difficulty
    3. Filler — Low/Medium Impact + Low Difficulty
    4. Avoid or postpone — Low/Medium Impact + Medium/High Difficulty (last)
  Within the same category, order is chronological (by date).
  When a new idea is added, insert it at the end of its category group.
  NEVER append a row at the end without respecting this order.
  An idea that is decided moves to the Roadmap section, and one that is built moves to "Already built".
-->

| Microservice                        | Slug / URL identifier         | Description                                                                                                                                                                          | Audience                                 | Impact | Difficulty | Proposed by                 | Date     |
| ----------------------------------- | ----------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------- | ------ | ---------- | --------------------------- | -------- |
| Wellbeing and Coordination Bookings | bienestar-appointment-service | Book appointments with student wellbeing services, academic coordination and psychology. Extensible shell so each department configures its own availability and booking flow.         | Students, professors, staff              | High   | Medium     | KApp Team                   | 26/02/25 |
| Restaurant Menu and Orders          | restaurant-service            | Check the university restaurant daily menu and place food orders from the app.                                                                                                         | Students, professors, staff              | High   | Medium     | KApp Team                   | 26/02/25 |
| Library                             | library-service               | Browse the catalog, review loan history, renew loans, and reserve books or join a waiting list.                                                                                        | Students, professors                     | High   | Medium     | KApp Team                   | 26/02/25 |
| Academic Procedures                 | academic-procedures-service   | Register and track academic procedures in real time (credit transfers, cancellations, certificates, grade reviews and others), centralizing administrative requests and improving process transparency. | Students, staff                          | High   | Medium     | @DIEGO-ALI                  | 13/03/26 |
| Per-Course Chat                     | chat-service                  | Messaging organized by course: professor ↔ group, student ↔ student, faculty ↔ academic members. Replaces informal channels (WhatsApp, e-mail).                                        | Students, professors, faculty            | High   | High       | KApp Team                   | 26/02/25 |
| Available Classrooms                | available-classrooms          | Real-time view of free classrooms and their availability per floor, to avoid interrupting ongoing classes.                                                                             | Students                                 | Medium | Low        | @DIEGO-ALI                  | 10/03/26 |
| Parking                             | parking-service               | Reserve parking spots, check real-time availability and pay for use of the university parking lot.                                                                                     | Students, professors, staff              | Medium | Medium     | KApp Team                   | 26/02/25 |
| Gym Management                      | gym-management                | Enroll in the university gym service, manage routines designed by trainers, and get nutrition recommendations aligned with each goal.                                                  | Students and trainers                    | Medium | Medium     | Zavithar_17                 | 12/03/26 |
| Digital ID Card                     | carnet-service                | Digital university ID card with a QR code bound to the student or professor identity, for access control and services.                                                                 | Students, professors                     | Low    | Medium     | KApp Team                   | 26/02/25 |

> **KApp Team:** Brian Steven Vargas Clavijo, Julián David Avila Cortes, Santiago Rocha Ramirez, Diego Ali Lares Rondon.

---

<!--
  ┌──────────────────────────────────────────────────────────────────┐
  │  Add your idea as a new row in the table, respecting the         │
  │  Impact x Difficulty matrix order.                               │
  │  Quick wins -> Strategic -> Filler -> Postpone                   │
  └──────────────────────────────────────────────────────────────────┘
-->
