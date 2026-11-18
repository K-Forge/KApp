# Running KApp on your own computer

Everything KApp needs, on one computer, with nothing of ours: no Atlas cluster, no Microsoft
tenant, no Tailscale and no secret from the team. It is how KApp is evaluated, and how it runs
offline.

- **The backend:** the API gateway and the five services (auth, user, semaphore, schedule, map),
  built from this source.
- **A local MongoDB:** one database and one account per service. The services' migrations create
  and fill them on their first start: the campus map, the pensums and the rest.
- **A fake sign-in:** a stand-in for Microsoft's, with three people in it.
- **The admin portal**, in the browser.
- **The Android app**, a debug build on the emulator.

The team itself works against the shared Atlas cluster ([ADR 0009](adr/0009-atlas-is-the-only-development-database.md));
this local database is the exception for evaluation ([ADR 0010](adr/0010-a-local-database-for-evaluation.md)).
The [runbook](RUNBOOK.md) is the team's guide.

---

## What you need

| | Version | What for |
|---|---|---|
| [Docker Desktop](https://www.docker.com/products/docker-desktop/) (or Docker Engine with the Compose plugin) | Compose 2.20 or later | The backend, the database and the portal |
| [Android Studio](https://developer.android.com/studio) | Recent, with an emulator | The app |
| An internet connection | | The first start only: it downloads the base images and the Maven and Gradle dependencies |

- **Memory:** give Docker at least 6 GB (Docker Desktop, *Settings > Resources*).
- **Ports:** 8080, 8090, 4300 and 27017 must be free on the computer. If one is taken, see
  [Troubleshooting](#troubleshooting).
- **Windows:** use either Git Bash, which comes with [Git for Windows](https://git-scm.com/download/win),
  or PowerShell. With PowerShell, nothing else is needed: the script generates the secrets inside a
  small Linux container.

---

## 1. Start the backend and the portal

From the repository's root folder, **one command**.

**macOS, Linux or Git Bash:**

```bash
scripts/run-locally.sh
```

**Windows PowerShell:**

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass   # only if PowerShell refuses to run scripts
.\scripts\run-locally.ps1
```

The first time, it:

1. writes `app/backend/microservices/.env`, with every secret **generated on your computer**:
   - the database's passwords;
   - the key that signs the sign-in tokens;
   - the token the services share.

   It is yours alone; never share it or commit it.
2. builds the six services from source (several minutes the first time) and starts them, with the
   database, the fake sign-in and the portal;
3. waits until the backend and the portal answer, and prints where everything is.

| | Address |
|---|---|
| API (the gateway) | http://localhost:8080 — from the Android emulator, http://10.0.2.2:8080 |
| Admin portal | http://localhost:4300 |
| Fake sign-in | http://localhost:8090/kapp |

Later starts reuse the same secrets and data and take under a minute.

| Command (`.sh` or `.ps1`) | What it does |
|---|---|
| `scripts/run-locally.sh` | Start everything |
| `scripts/run-locally.sh status` | What is running |
| `scripts/run-locally.sh logs` | Follow the logs |
| `scripts/run-locally.sh down` | Stop, keeping the data |
| `scripts/run-locally.sh reset` | Stop and delete the data and the secrets: the next start begins from scratch |

## 2. Who to sign in as

The sign-in is a fake one: no Microsoft account is involved. Press **Sign in with Microsoft**
(**Ingresar con Microsoft** in the app) and type one of these as the user name. There is no
password; leave the claims box empty.

| User name | Who it is | What it can do |
|---|---|---|
| `estudiante` | A student, Pepito Perez Gomez | In the app: home, semáforo (course record), timetable, map and profile. Their academic data is the university system's test student |
| `profesor` | A professor, Laura Marcela Gómez Restrepo | In the app: home, timetable, map and profile |
| `administrativo` | An administrative employee, Ana Ruiz Mejía | The admin portal, as its administrator |

Any other name signs in too, but as nobody in particular: with no role, it sees only the map and
its profile.

## 3. The admin portal

Open http://localhost:4300, press **Sign in with Microsoft** and type `administrativo`. Move around
with the menu on the left: reloading the page signs you out, and you sign in again.

For example, a visitor pass:

1. In the portal, *People and access > Visitor passes*, press **Issue a pass**. It shows a code of
   the form `KL-V-XXXX-XXXX`.
2. In the app, on the sign-in screen, press **Usa tu código de invitación**. Type the code, a
   document type and number, and a name, and press **Validar código**.
3. Back in the portal, *Show: Redeemed (the register)* lists the visit.

## 4. The Android app

1. Open `app/frontend/mobile/kotlin` in Android Studio and start an emulator.
2. Point the debug build at your backend. Either:
   - **with Gradle on the command line**, with the emulator running:

     ```bash
     cd app/frontend/mobile/kotlin
     ./gradlew :app:installDebug -Pkapp.api.baseUrl=http://10.0.2.2:8080/ -Pkapp.dev.oidcIssuer=http://10.0.2.2:8090/kapp
     ```

     On Windows, `gradlew.bat` in place of `./gradlew`. Gradle needs Java 17 or later and the
     Android SDK: opening the project once in Android Studio sets both up (it writes the SDK's
     location to `local.properties`); otherwise set `JAVA_HOME` and `ANDROID_HOME`.
   - **or in Android Studio:** add the two properties to `gradle.properties` in your user's Gradle
     folder (`~/.gradle/gradle.properties`; on Windows, `C:\Users\<you>\.gradle\gradle.properties`),
     choose the **debug** build variant (*Build > Select Build Variant*), and press Run.

     ```properties
     kapp.api.baseUrl=http://10.0.2.2:8080/
     kapp.dev.oidcIssuer=http://10.0.2.2:8090/kapp
     ```

3. In the app, press **Ingresar con Microsoft**. The fake sign-in page opens in the emulator's
   browser (close Chrome's welcome screens the first time); type `estudiante` or `profesor` and
   press **Sign-in**.

`10.0.2.2` is how the Android emulator reaches the computer it runs on. A physical phone needs the
computer's network address instead, in both properties and in the backend's settings: the
[Kotlin README](../app/frontend/mobile/kotlin/README.md#running-against-the-local-backend) explains it.

---

## What is in the database

The services' migrations create each database and load it on their first start:

- **the campus:** buildings, floors, spaces, and the map of the main campus;
- **the pensums:** the published programs and their courses.

The academic data, meaning the student's record, timetable and program, comes from a test version
of the university's system (SINU), built into the services: every student sees the same invented
student. Accounts, visitor passes and anything created in the portal are stored as they are made.

It all lives in two Docker volumes of the `kapp-local` project; `reset` deletes them. To look inside
with MongoDB Compass or `mongosh`, connect to `mongodb://localhost:27017/?directConnection=true` as
`kapp_root`, with the `MONGO_ROOT_PASSWORD` from your `.env`.

## Troubleshooting

- **A port is taken.** Add the free ports you want to `app/backend/microservices/.env`, then start
  again:

  ```properties
  KAPP_GATEWAY_PORT=8081
  KAPP_PORTAL_PORT=4301
  KAPP_FAKE_OIDC_PORT=8091
  KAPP_MONGO_PORT=27018
  ```

  The app's two properties change with them.
- **"Docker is not running."** Start Docker Desktop and wait for it to say it is running.
- **The backend does not answer after the first build.** `scripts/run-locally.sh logs` shows why. A
  service that runs out of memory restarts again and again: give Docker more memory.
- **"app/backend/microservices/.env exists and points at another database."** That `.env` is a team
  member's, for Atlas. Move it aside to run locally; the script never overwrites it.
- **On the emulator, the browser is slow or drops letters.** The backend and the emulator together
  need a lot of the computer. Type the user name slowly and check it before pressing **Sign-in**: a
  misspelt name signs in as nobody in particular, with only the map and the profile.
- **Everything is stuck, or you want a clean start:** `scripts/run-locally.sh reset`, then start again.
- **A ZIP of the code opened on macOS** with the `unzip` command can garble file names with accents
  (only under `docs/researches`). Finder opens it correctly.
