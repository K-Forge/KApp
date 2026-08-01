# Microservice Ideas — KApp

> **"The best architectures grow out of good shared ideas."**

Welcome to the KApp microservice idea bank. This document exists so the whole team can propose, document and discuss
new microservice ideas in a structured way, without the pressure of building them all right away.

The goal is simple: **leave a solid base of well-thought-out ideas** that lets us prioritize, plan and build the
future of KApp with clarity. It does not matter whether your idea is small or ambitious — if it solves a real problem
for the university community, it belongs here.

---

## How to Use This Document

Got an idea? Follow these steps to add it:

1. **Add a new row to the table** in the "Registered Ideas" section, filling in every field.
2. **Place your idea in the right position** according to the Impact × Difficulty matrix: first **high impact, low
   difficulty** (quick wins), then **high impact, high difficulty** (strategic projects), then **low/medium impact,
   low difficulty** (filler), and finally **low/medium impact, high difficulty** (avoid or postpone).
3. **Add your name or GitHub handle** so the team knows who to contact to discuss the proposal.
4. **Commit and open a PR** titled: `idea: Microservice Name`.

> **Tip:** if you are unsure about a field (such as the difficulty level), make your best estimate. Brainstorming is
> for exploring, not for absolute certainty.

> [!IMPORTANT]
> **Ordering rule:** ideas must **always** be sorted by **descending impact** (High → Medium → Low), breaking ties by
> **ascending difficulty** (Low → Medium → High). When adding a new idea, insert it in the right category according
> to the Impact × Difficulty matrix; never append it at the end of the document ignoring the order.

---

## Impact × Difficulty Matrix

Below are the ideas proposed by the team. Read the existing ones before adding yours to avoid duplicates, and if you
like one of them, comment on the corresponding PR.

|                        | Low Difficulty                              | Medium/High Difficulty                        |
| ---------------------- | ------------------------------------------- | --------------------------------------------- |
| **High Impact**        | **Quick wins** <br> _Do first_              | **Strategic projects** <br> _Plan ahead_      |
| **Medium/Low Impact**  | **Filler** <br> _Do when there is time_     | **Avoid or postpone** <br> _Low priority_     |

---

## Registered Ideas

<!-- IMPORTANT: NOTE FOR AI AGENTS AND CONTRIBUTORS:
  The rows of this table MUST ALWAYS be sorted by the Impact x Difficulty matrix:
    1. Quick wins — High Impact + Low Difficulty (first)
    2. Strategic projects — High Impact + Medium/High Difficulty
    3. Filler — Low/Medium Impact + Low Difficulty
    4. Avoid or postpone — Low/Medium Impact + Medium/High Difficulty (last)
  Within the same category, order is chronological (by date).
  When a new idea is added, insert it at the end of its category group.
  NEVER append a row at the end without respecting this order.
-->

| Microservice                        | Slug / URL identifier         | Description                                                                                                                                                                          | Audience                                 | Impact | Difficulty | Proposed by                 | Date     |
| ----------------------------------- | ----------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------- | ------ | ---------- | --------------------------- | -------- |
| Damaged Facility Reports            | report-service                | Report damaged spaces or resources (boards, chairs, restrooms, projectors) so maintenance can prioritize repairs and track them.                                                       | Students, professors, staff              | High   | Low        | KApp Team                   | 26/02/25 |
| Interactive Map                     | map-service                   | Find classrooms, buildings and campus spaces through an interactive map with visual orientation.                                                                                       | Students, professors, visitors           | High   | Medium     | KApp Team                   | 26/02/25 |
| Wellbeing and Coordination Bookings | bienestar-appointment-service | Book appointments with student wellbeing services, academic coordination and psychology. Extensible shell so each department configures its own availability and booking flow.         | Students, professors, staff              | High   | Medium     | KApp Team                   | 26/02/25 |
| Restaurant Menu and Orders          | restaurant-service            | Check the university restaurant daily menu and place food orders from the app.                                                                                                         | Students, professors, staff              | High   | Medium     | KApp Team                   | 26/02/25 |
| Student Academic Traffic Light      | student-semaphore-service     | Check each student's academic status (average, credits, alerts) and identify at-risk students for timely support.                                                                      | Students, administrators                 | High   | Medium     | KApp Team                   | 26/02/25 |
| Library                             | library-service               | Browse the catalog, review loan history, renew loans, and reserve books or join a waiting list.                                                                                        | Students, professors                     | High   | Medium     | KApp Team                   | 26/02/25 |
| Events and Activities               | event-service                 | Centralize university events (cultural, sports, academic), filter by category, register and add them to a personal calendar.                                                           | Everyone                                 | High   | Medium     | KApp Team                   | 26/02/25 |
| Notifications                       | notification-service          | Cross-cutting real-time push notification service. Centralizes alerts from every microservice (schedules, messages, events, appointments) with per-user configuration.                 | Everyone                                 | High   | Medium     | KApp Team                   | 26/02/25 |
| Club Management                     | club-service                  | Create, browse and manage student clubs: discover clubs, join them, manage members, publish activities and coordinate meetings.                                                        | Students, administrators                 | High   | Medium     | Brian Steven Vargas Clavijo | 10/03/26 |
| Academic Procedures                 | academic-procedures-service   | Register and track academic procedures in real time (credit transfers, cancellations, certificates, grade reviews and others), centralizing administrative requests and improving process transparency. | Students, staff                          | High   | Medium     | @DIEGO-ALI                  | 13/03/26 |
| Per-Course Chat                     | chat-service                  | Messaging organized by course: professor ↔ group, student ↔ student, faculty ↔ academic members. Replaces informal channels (WhatsApp, e-mail).                                        | Students, professors, faculty            | High   | High       | KApp Team                   | 26/02/25 |
| Lost and Found                      | lost-and-found-service        | Register and search for items lost on campus. Whoever finds an item reports it, and whoever lost it looks for matches to arrange the return.                                           | Students, professors, staff              | Medium | Low        | Brian Steven Vargas Clavijo | 10/03/26 |
| Available Classrooms                | available-classrooms          | Real-time view of free classrooms and their availability per floor, to avoid interrupting ongoing classes.                                                                             | Students                                 | Medium | Low        | @DIEGO-ALI                  | 10/03/26 |
| Parking                             | parking-service               | Reserve parking spots, check real-time availability and pay for use of the university parking lot.                                                                                     | Students, professors, staff              | Medium | Medium     | KApp Team                   | 26/02/25 |
| Digital ID Card                     | carnet-service                | Digital university ID card with a QR code bound to the student or professor identity, for access control and services.                                                                 | Students, professors                     | Low    | Medium     | KApp Team                   | 26/02/25 |
| Wellbeing Building Space Booking    | recreation-bookings           | Check availability of wellbeing spaces (ping-pong tables, dance studio, pool tables and others) and book limited slots within their opening hours.                                     | Students                                 | Medium | Low        | Zavithar_17                 | 12/03/26 |
| Gym Management                      | gym-management                | Enroll in the university gym service, manage routines designed by trainers, and get nutrition recommendations aligned with each goal.                                                  | Students and trainers                    | Medium | Medium     | Zavithar_17                 | 12/03/26 |


> **KApp Team:** Brian Steven Vargas Clavijo, Julián David Avila Cortes, Santiago Rocha Ramirez, Diego Ali Lares Rondon.

---

<!--
  ┌──────────────────────────────────────────────────────────────────┐
  │  Add your idea as a new row in the table, respecting the         │
  │  Impact x Difficulty matrix order.                               │
  │  Quick wins -> Strategic -> Filler -> Postpone                   │
  └──────────────────────────────────────────────────────────────────┘
-->
