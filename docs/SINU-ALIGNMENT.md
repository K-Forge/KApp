# KApp beside SINU: what to drop before asking for access

An evaluation, not a decision. Written on 2 October 2026, after Registro Académico said that
everything academic lives in SINU and that access is the IT director's call.

**The position to hold:** SINU is the record. KApp reads from it, for the person signed in, and
writes only what SINU does not have. Anything in KApp that looks like a second copy of SINU, or
that collects data SINU already guards, makes the request to Dirección de TI harder to grant.

## 1. What KApp holds today

| Datum | Where it is today | Whose it is | Proposal |
|---|---|---|---|
| Password, e-mail verification, invitation codes, temporary passwords | `auth-service` | Entra ID | Goes when Entra ID arrives. Keep as the stated interim until then |
| Name, institutional e-mail, role | `auth-service`, `user-service` | Entra ID | Read from the token at sign-in |
| Identity document and phone of a member | `user-service` (`identification`, `phone`) | SINU | **Remove.** Nothing in the repository uses them |
| Student code, programme, pensum version, level | `user-service` (`academic`) | SINU | Read. An administrator types them today |
| Programmes and pensums: 23 plans, 650 items | `semaphore-service`, typed in from brochures; the portal creates and edits them | SINU | Read-only mirror. The portal shows them and imports them; it stops editing them |
| A final mark, 0 to 50, for each course | `semaphore-service` (`grade`), typed by the student | SINU | **Remove.** The semáforo needs the state, not the mark |
| Passed, in progress, pending or failed, for each course | `semaphore-service`, typed by the student | SINU | Read where TI allows it; typed by the student otherwise |
| The timetable: course, group, professor, rooms by date range | `schedule-service`, typed by the student, in SINU's own shape | SINU | Read |
| Any student's semáforo and timetable, for an administrator | `GET /api/semaphore/{userId}`, `GET /api/schedule/{userId}` | SINU | **Remove.** KApp's administrators have no reason to open a student's record |
| What a student plans to take in later semesters | `semaphore-service` (`plans`) | KApp | Keep |
| The campus: buildings, floors, rooms, routes | `map-service` | KApp | Keep |
| A visitor's identity document, for 30 days | `auth-service` (visitor passes) | KApp | Tell TI, or drop the document. See 3.5 |

## 2. What KApp covers that SINU does not

This is the whole pitch, and it is enough:

- **Finding a room.** The campus drawn floor by floor, and a class that opens on its classroom.
- **The timetable as a day**, not as a report: what is next, where, and in which room this week.
- **The semáforo as a picture**: the whole plan coloured by state, and what the prerequisites
  let a student take next.
- **Planning ahead**: laying the remaining courses over future semesters. SINU has no such thing.
- **A visitor's day pass**, if reception wants it.

None of these needs a mark, a balance or a document number.

## 3. What to remove

Ordered by how badly each reads to somebody deciding whether to open SINU to us.

### 3.1 The old story, still in the repository

`REQUIREMENTS.md` already declares all of this out of scope, so deleting it loses nothing that
`git` does not keep:

- **`docs/SRS.md`** promises viewing and *editing* every student's grades, professors uploading
  grades, a digital ID card, library loans, parking payments and a chat. It is the first document
  a reviewer would open, and it describes a replacement for SINU.
- **`app/database/`**: the legacy PostgreSQL schema, with `grade`, `student_course`,
  `library_loan`, `parking_reservation` and `chat_message`. Nothing reads it.
- **`app/frontend/web/`**: the old prototype, with `notas.html`, `inscribir.html` and `pqr.html`.
- **`course-service`** and **`assignment-service`**: frozen, out of the build.
- **`docs/MICROSERVICES-IDEAS.md`**: seventeen services nobody is building.

### 3.2 Marks

The semáforo stores a final mark for each course and validates it against the university's scale.
That is exactly the datum Registro Académico said would be hardest to obtain, and KApp does not
need it: a traffic light has colours. Dropping `grade` leaves the state, the period and the
elective a slot was resolved to.

### 3.3 Administrators reading a student's record

Two routes let `ROLE_ADMIN` read any student's semáforo and timetable. They exist for support, and
they turn KApp's portal into a second window onto academic records. Without them, a student's
academic data is visible to that student only.

### 3.4 Documents and phones of members

`REQUIREMENTS.md` says KApp stores no government-issued data except a visitor's document. The user
contract contradicts it: `UserProfile.identification` carries a member's document type and number,
and `phone` their number. No rule uses either, the portal does not show them and neither mobile
client refers to them.

### 3.5 The visitor's document

It is the one piece of personal data KApp collects on its own, under Ley 1581. TI has not been
told (S12 in `SECURITY-AUDIT.md`). Either it goes into the conversation with TI as its own item, or
the pass keeps the visitor's name and drops the document.

### 3.6 Editing the catalogue by hand

The portal creates programmes and edits pensums. With SINU as the source that is a second place
where a pensum can be wrong. The pages stay as viewers; the only write left is the import, from
whatever SINU exports.

### 3.7 Words that say more than KApp does

The timetable's entries are called `Enrollment` and live under `/enrollments`. In Spanish that is
*matrícula*, which is SINU's act, not KApp's. A class on a timetable is what it is. Worth renaming
at the next breaking version of the schedule contract, not before.

## 4. Course codes

**For telling courses apart inside KApp: no.** Within one plan no two courses share a name, and a
screen never needs a code to show one.

**As the link to SINU: yes, and only the real ones.** A name is not a key between systems. Our own
catalogue already spells the same course two ways under one code: `17080` is *Estadistica
Descriptiva* in one plan and *Estadística Descriptiva* in another; `56201` is *Metodologia de la
Investigacion* and *Metodología de Investigación*; `71181` appears under three spellings. SINU's
timetable report names a class by its code and group. The code is what joins a class on the
timetable to its item on the semáforo, and one programme's course to the same course in another.

**What is a bad idea is inventing them.** Only 4 of the 23 plans print the university's codes: 182
items. For the other 392 fixed courses KApp made up codes such as `MKT-101`, which look
institutional, must never be shown, and all change the day the real ones arrive. Each item ends up
with three identifiers: `code` (real or invented), `pensumItemCode` (always present) and `sinuCode`
(real only).

**Proposal:** one internal identifier, `pensumItemCode`, for addressing and prerequisites; one
optional `sinuCode`, filled only from SINU; `code` and the invented codes go. It is a breaking
change to the semáforo contract and touches the four mobile developers, so it travels with 3.2 and
3.3 in a single new version, announced once.

## 5. What to ask Dirección de TI for

Small, read-only, and stated with what is *not* being asked.

1. **Entra ID:** one application registration. OpenID Connect, authorization code with PKCE,
   scopes `openid profile email`. If the directory can say student or staff, that claim. See
   [ADR 0003](adr/0003-oidc-over-saml-for-mobile-authentication.md) for why not SAML on a phone.
2. **SINU, for the person signed in and nobody else:**
   - their programme and the version of their pensum;
   - that pensum: for each course its code, name, credits, level and prerequisites. This is
     published information;
   - their timetable for the current period, as report `PACR42_GWT` prints it;
   - for each course of their pensum, whether it is passed, in progress or pending.
3. **In whatever form SINU already offers:** a web service, a database view or a file exported
   overnight. KApp's model follows the report's shape, so any of the three is an adapter.

**Not requested:** marks, averages, financial state, payments, identity documents, anything about
another student, and any write.

## 6. Order

1. Now, with no access to anything: 3.1, then 3.4. Both are deletions.
2. One breaking version of the semáforo contract: 3.2, 3.3 and section 4 together. Agree the date
   with the mobile team first; November is close.
3. With Entra ID: the password side of `auth-service` goes.
4. With SINU: the catalogue and the timetable become reads, and 3.6 follows.
