# KApp

## Mobile Application for Fundación Universitaria Konrad Lorenz

### Software Requirements Specification (SRS)

**Version:** 2.0
**Date:** 11 October 2026

> This Markdown version describes the product as decided on 2–5 October 2026. It will be rewritten as a `.docx` for
> the thesis; until then this file is the reference. The detailed, testable requirements are in
> [`REQUIREMENTS.md`](REQUIREMENTS.md); this document gives the product's shape, its actors and its limits.

---

## Table of Contents

- [1. Introduction](#1-introduction)
- [2. General Description](#2-general-description)
- [3. Functional Requirements](#3-functional-requirements)
- [4. Non-Functional Requirements](#4-non-functional-requirements)
- [5. Personal Data (Ley 1581 de 2012)](#5-personal-data-ley-1581-de-2012)
- [6. Version History](#6-version-history)

---

## 1. Introduction

### 1.1 Purpose

KApp is a mobile application, for Android and iOS, for the community of Fundación Universitaria Konrad Lorenz. It
helps a student find their way around the campus and their own academic day: where a class is, what their timetable
is, how far they are in their programme. It also gives the community a place to report a damaged room, hand in a lost
object or raise a suggestion.

KApp **complements SINU, the university's academic system, and never replaces it**. Everything academic is read from
SINU, read-only, and nothing academic is stored in KApp.

### 1.2 What KApp Is Not

These are deliberately out of scope, and no requirement in this document may reintroduce them:

- **Anything that writes to SINU**, or copies its data: grades, enrolment, academic records.
- **Editing grades.** A student may see their own grades if SINU provides them, read-only and optionally.
- **Enrolment** in courses or sections.
- **Payments** of any kind, including parking.
- **Personal documents**: digital ID cards, certificates, identity documents of members.
- **Chat** or private messaging between users.
- **Library loans**, which belong to the library's own system.
- **Administrators reading students' academic data.** The portal manages KApp's own data, not SINU's.

### 1.3 Scope

| Area | What the user gets |
|---|---|
| Sign-in | One Microsoft account for the apps and the portal |
| Timetable | The student's timetable, read from SINU; a professor's own sections |
| Semáforo | The student's progress through their pensum, read from SINU, plus personal plans they edit |
| Campus map | Buildings, floors drawn from the university's plans, spaces and search |
| Notifications | An in-app inbox and push notifications, with per-topic preferences |
| Community | Student voice, damage reports, lost and found, citizenship points |
| Visitors | A one-day pass, issued by reception, that opens the map and nothing else |
| Administration | A web portal for the staff who manage KApp's own data |
| Later | Wellbeing, events, clubs and recreation bookings, after a questionnaire to the offices |

### 1.4 Definitions

| Term | Meaning |
|---|---|
| **SINU** | The university's academic system. The source of every academic datum KApp shows |
| **Test SINU adapter** | A stand-in that serves invented academic data with SINU's shape. Used until the university opens SINU, and for the presentation if access never comes |
| **Entra ID** | Microsoft's identity service. Members sign in to KApp with their university Microsoft account |
| **Pensum** | The curriculum of a programme: its courses, levels and prerequisites |
| **Semáforo** | The view of a student's progress through their pensum, course by course, with a status for each |
| **Plan** | A student's own arrangement of the courses they intend to take. The only academic thing a student edits in KApp |
| **Sede** | What SINU calls a building. Room numbers repeat across sedes |
| **Block** | SINU's scheduling unit, 45 minutes. A SINU "hour" is one block |
| **Profile role** | What a member is: student, professor or administrative staff |
| **Permission** | What a member may manage in the portal: administration, reception, maintenance, moderation, wellbeing |
| **Visitor pass** | A one-day credential reception issues to a person without a university account |
| **Ley 1581** | Colombia's personal data protection law (Ley 1581 de 2012) |

### 1.5 References

- [`REQUIREMENTS.md`](REQUIREMENTS.md): the detailed requirements and their status.
- [`SINU-ALIGNMENT.md`](SINU-ALIGNMENT.md): how KApp reads SINU and what it must never store.
- [`api/CHANGELOG.md`](api/CHANGELOG.md): the API contracts and their versions.
- [`MICROSERVICES-IDEAS.md`](MICROSERVICES-IDEAS.md): the roadmap of new services.
- [`adr/`](adr/): the architecture decisions and their reasons.

---

## 2. General Description

### 2.1 Product Perspective

KApp has two native clients, Android (Kotlin) and iOS (Swift), and a web portal for administration. Behind them, a
set of independent services sits behind a single gateway, each owning its own data. It is deployed on Kubernetes.

KApp's data and SINU's data never mix:

- **KApp stores**: member profiles (name, picture, roles, active flag), the student's plans, the campus map, the
  community's content, notifications, and visitor passes.
- **KApp reads, and never stores**: timetables, the catalog, programmes, pensums, a student's level, and course
  statuses. These come from SINU, read-only, cached for minutes at most.

### 2.2 Actors

Every member has **one profile role**, taken from the university's directory when they sign in. Permissions are granted
on top by an administrator, and they only open the portal: the app's tabs follow the profile role.

| Actor | Role | What they can do |
|---|---|---|
| **Student** | `ROLE_STUDENT` | Their own timetable, semáforo and plans; the map; notifications; the community features |
| **Professor** | `ROLE_PROFESSOR` | Their own sections; the map; notifications; the community features |
| **Administrative staff** | `ROLE_STAFF` | The map. Also the default when the directory names no role |
| **Visitor** | `ROLE_GUEST` | The map, for one day, with a pass from reception. No account |

| Permission | Granted to manage |
|---|---|
| `ROLE_ADMIN` | Accounts, roles and permissions, and the campus map |
| `ROLE_RECEPTION` | Visitor passes, and lost-and-found hand-ins and claims |
| `ROLE_MAINTENANCE` | Damage reports: validating and closing them |
| `ROLE_MODERATION` | Student voice and comments: reviewing reports and hiding content |
| `ROLE_WELLBEING` | Citizenship points, and the wellbeing features once they exist |

### 2.3 Constraints

- **SINU is read-only and never copied.** Access to it is the university's IT director's decision.
- **Android and iOS** native clients; a web portal for the staff.
- **Personal data** is handled under Ley 1581 de 2012 (section 5).
- **The source code is public**, so no credential, real student datum or private document is ever committed.
- **Members never have a KApp password in production.** Password sign-in exists only for development and tests.

### 2.4 Assumptions and Dependencies

- **SINU access**, granted by the university's IT director. Until then, and if it never comes, the test SINU adapter
  serves invented data with SINU's shape.
- **An Entra ID application registration** from the university. Development uses a test tenant and a fake identity
  server.
- **Push to iOS** needs an Apple Developer account, whose funding is being sought. Android push uses Firebase.
- **Wellbeing, events, clubs and recreation bookings** depend on a questionnaire to the offices that run them.
- **Production hardware** is agreed with the university's IT director; the fallback is a single machine running
  Kubernetes.

### 2.5 Dates

The thesis proposal is handed in at the end of the 2026-2 term. The product is presented in **2027-2**.

---

## 3. Functional Requirements

Each requirement is detailed, with its acceptance criteria, in [`REQUIREMENTS.md`](REQUIREMENTS.md).

### 3.1 Identity and Access

1. Members sign in with their university **Microsoft account**, in the apps and in the portal.
2. KApp issues its own access tokens after verifying the Microsoft sign-in. Sessions renew with **rotating refresh
   tokens**: 30 days in the apps, 12 hours in the portal. Reusing a refresh token revokes the whole session.
3. Members can sign out, and suspending an account revokes all its sessions.
4. Only members with a permission can enter the portal.

### 3.2 Timetable

5. A student sees their timetable as SINU has it: course, section, day, time in blocks, sede and room.
6. A professor sees their own sections.
7. The timetable is **not edited in KApp**. A change is made in SINU and appears in KApp.

### 3.3 Semáforo and Plans

8. A student sees their pensum course by course, with SINU's status for each: passed, in progress, pending, failed or
   postponed (*aplazada*).
9. A student may see their grades, read-only, if SINU provides them.
10. A student arranges their own **plans**: which pending courses they intend to take, and when. KApp checks
    prerequisites. Plans are the only academic data a student writes.

### 3.4 Campus Map

11. Anyone signed in, and any visitor with a pass, sees the campus: buildings, their floors and the spaces on each.
12. Floors are drawn as polygons taken from the university's plans, not sketched by eye.
13. A user searches for a space by name or room number, and KApp tells the sedes apart, since room numbers repeat.
14. KApp says how to reach a floor: stairs, the main lift or another access.
15. The map is managed in the portal, which is its source of truth.

### 3.5 Notifications

16. Each member has an in-app **inbox** and receives **push notifications**.
17. A member chooses, per topic, what reaches them.
18. Every feature notifies through one service, so the inbox is complete and the preferences apply everywhere.

### 3.6 Community

19. **Student voice.** A student posts a complaint or a suggestion. It is not anonymous: the author chooses to show
    their name or their programme, and moderation always sees who wrote it. Posts may not name people. A word filter,
    reports from users and human moderation keep it civil. Members vote and comment, in 20 to 200 words. There are no
    official replies.
20. **Damage reports.** A member reports a damaged space or object with its place on the map, a photo and a short
    description. The same votes, comments and reports apply. Maintenance validates and closes them.
21. **Lost and found.** Reception keeps the objects. Handing an object in and claiming it both use one-time codes:
    reception generates the hand-in code, and the app generates the claim code once the server confirms the match.
    The history is never deleted. Photos of documents, IDs or cards are not allowed.
22. **Citizenship points.** Members earn points for contributing — handing in an object, a validated report, a
    well-received post, voting — and lose them when a report they filed is overturned. Points are visible to their
    owner and to the university, are not redeemable yet, and may go negative.

### 3.7 Visitor Pass

23. Reception issues a pass code. A visitor redeems it with an identity document and gets 24 hours of map-only
    access. No account is created.

### 3.8 Administration Portal

24. The portal is bilingual, Spanish first, and serves only members with a permission.
25. It manages accounts, roles and permissions; the campus map; visitor passes; lost and found; damage reports;
    moderation; and the citizenship points.
26. It never shows students' academic data.

### 3.9 Later, After the Office Questionnaire

27. Wellbeing services, events, clubs and recreation bookings. Their requirements are written once the offices that
    run them have said how they work today.

---

## 4. Non-Functional Requirements

1. **Platforms.** Native Android and iOS apps; a web portal for the staff.
2. **Security.** Every service validates every request's token itself, so bypassing the gateway gains nothing. No
   credential lives in the source code. Members have no password in production.
3. **Read, never copy.** Academic data is read from SINU and cached for minutes at most.
4. **Deployment.** Every service runs on Kubernetes, locally with kind and in production.
5. **Independent services.** One service per feature, each with its own data. What crosses services goes through a
   transactional outbox, so a failure in one never leaves another half updated.
6. **Accessibility.** Readable contrast and support for the system's text size.
7. **Photos** are resized and stripped of their location metadata before they are stored.

---

## 5. Personal Data (Ley 1581 de 2012)

KApp keeps as little personal data as it can, and says why it keeps each piece:

| Data | Why it is kept | How long |
|---|---|---|
| Member profile: name, picture, roles | To show who a member is in the community features | While the account exists |
| No identity document, phone or student code of members | — | Never stored |
| Visitor's identity document | Reception must know who entered with a pass | Deleted automatically after 30 days |
| Author of a voice post or report | Moderation must be able to act, since posts are not anonymous | With the post |
| Lost-and-found history | An object handed in and claimed must stay traceable | Never deleted |
| Photos in reports | To show the damage | With the report; resized, location metadata stripped |

Academic data is not in this table because KApp does not store it.

---

## 6. Version History

| Version | Date | Change |
|---|---|---|
| 1.0 | 2025 | First version, written by the 2025 team. It described a different product: grades, enrolment, a digital ID card, library loans, parking payments and a chat — in effect a replacement for SINU |
| 2.0 | 11 October 2026 | Rewritten to the product decided on 2–5 October 2026 (#49): SINU read-only, Microsoft sign-in, the campus map, notifications and the community features |

Version 1.0 carried a notice reserving its reproduction to its two authors, Brian Steven Vargas Clavijo and Santiago
Rocha Ramírez. The project's terms of use are now those in the repository's `LICENSE`. Version 1.0 remains readable in
the repository's history.
