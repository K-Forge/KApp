# Moving the development databases to Atlas

Step by step, in the order the Atlas interface asks for it. About twenty minutes.

The goal is one free cluster holding the same five databases the local stack has, with the same
rule: **one account per service, able to read and write exactly one database.** After this, the six
of us share one development database and nobody has to run MongoDB on their machine.

> **What I cannot do for you.** Every step below needs your Atlas account. I am not going to handle
> your credentials or an API key with permissions over your organisation, so the cluster and its
> users are yours to create. Everything on this side is already written and waiting for the
> connection strings — step 5 is the only place your work meets mine, and it is one command.

---

## 1. Account and project

1. Sign up or sign in at <https://cloud.mongodb.com>.
2. Create an organisation if you have none — call it **K-Forge**.
3. Inside it, create a project named **KApp Dev**.

One project per environment. When there is a production cluster it goes in its own project, so a
mistake made in development cannot reach it: users, network rules and alerts are all per project.

## 2. The cluster

1. **Create** → choose the **M0** tier. It is free and does not expire.
2. Provider **AWS**, region **N. Virginia (us-east-1)** — the lowest latency to Bogotá among the
   regions M0 offers. Take **São Paulo (sa-east-1)** instead if it appears in the list.
3. Name it **kapp-dev**.
4. Create. It takes three to five minutes to provision.

M0 is a three-node replica set, which is what the local container imitates. Transactions work the
same, so nothing in KApp behaves differently against it.

**Skip the "Quickstart" wizard's user and IP prompts** if it shows them; the next two steps do the
same thing properly.

## 3. The five database users

**Security → Database Access → Add New Database User**, five times.

For each one: **Password** authentication, then under *Database User Privileges* choose
**Specific Privileges** — not "Read and write to any database", which is exactly what we are
avoiding — and add one privilege:

| Username                | Role        | Database         | Collection    |
| ----------------------- | ----------- | ---------------- | ------------- |
| `kapp_auth_user`        | `readWrite` | `kapp_auth`      | *(leave blank)* |
| `kapp_user_user`        | `readWrite` | `kapp_user`      | *(leave blank)* |
| `kapp_semaphore_user`   | `readWrite` | `kapp_semaphore` | *(leave blank)* |
| `kapp_schedule_user`    | `readWrite` | `kapp_schedule`  | *(leave blank)* |
| `kapp_map_user`         | `readWrite` | `kapp_map`       | *(leave blank)* |

Use **Autogenerate Secure Password** and copy each one as you go — Atlas shows it once. You will
type them into step 5; do not put them in a chat, an issue or a commit.

You do not need to create the databases first. The first write creates them, and Mongock does that
on startup.

> **Atlas users live in `admin`, not in their own database.** This is the one place Atlas differs
> from our local container: every Atlas user authenticates against `admin` regardless of which
> database it can reach. So an Atlas connection string carries **no `authSource` parameter at all**.
> Leaving `authSource=kapp_auth` in there makes authentication fail with a confusing
> "Authentication failed" that looks like a wrong password. The isolation is unaffected — it comes
> from the privilege, not from where the user is stored.

## 4. Network access

**Security → Network Access → Add IP Address**.

For a development cluster holding disposable data, **Allow Access from Anywhere** (`0.0.0.0/0`) is
the practical choice: there are six of us, on home connections, campus Wi-Fi and phone tethering,
and dynamic addresses would mean somebody is locked out every week.

That is a decision made knowingly, not an oversight. What makes it acceptable is that the cluster
holds nothing real — no institutional records, no personal data, no production credentials — and
that the only way in is still a per-service password. It stops being acceptable the moment real
data lands there, and a production cluster must use an IP list or VPC peering instead.

## 5. Point the stack at it

Take each string from Atlas itself — *Connect → Drivers* on the cluster, with the user selected —
and paste it into `app/backend/microservices/.env`, single-quoted. Assembling one by hand is where
every mistake at the bottom of this document comes from, and two of the three fail with
`Authentication failed`, which reads exactly like a wrong password.

```
MONGO_MAP_URI='mongodb+srv://kapp_map_user:<password>@<cluster>.mongodb.net/kapp_map?retryWrites=true&w=majority&maxPoolSize=10'
```

Three things the copied string still needs checking for:

- **No `authSource`.** Atlas keeps every user in `admin` regardless of which database it can reach,
  so pinning it to the service's own database fails with a message that reads exactly like a wrong
  password. The isolation comes from the privilege, not from where the user is stored.
- **No `replicaSet`.** The SRV record carries it; passing it as well is an error.
- **The password is percent-encoded**, so an Atlas-generated one containing `@ : / ? # %` works
  instead of the string being parsed at the wrong character.

Passwords are typed, never passed as arguments — an argument would land in your shell history.

Your previous `.env` is saved as `.env.bak`.

## 6. Start the stack

```bash
cd app/backend/microservices
docker compose --profile academic --profile map --profile dev up -d
```

There is nothing else to start: this cluster is the only database the stack has
([ADR 0009](adr/0009-atlas-is-the-only-development-database.md)). If one of the five strings is
missing, Compose refuses to start and names it.

## 7. Check it worked

```bash
docker compose ps
docker compose logs --tail=30 auth-service | grep -i mongo
```

All services healthy, and no `MongoSecurityException`. Then create the team's accounts against the
cloud database, once, for everybody:

```bash
scripts/create-dev-accounts.sh
```

And confirm the seed data arrived — Mongock runs against Atlas the same way it runs locally:

```bash
curl -s http://localhost:8080/api/catalog/programs \
  -H "Authorization: Bearer <a token from /auth/login>" | head
```

## 8. Tell the others

Each teammate needs the same five `MONGO_*_URI` lines in their own `.env`. Two ways, and the second
is better:

**Send them the five lines** privately — one person, one message, never the group chat. Fastest, but
it puts five passwords in five chat histories.

**Or send them the cluster host and their own copy of the passwords**, and let them run the same
command you did:

```bash
../../../scripts/generate-dev-secrets.sh back > .env   # only if they have no .env yet
# paste the five MONGO_*_URI values into it, single-quoted
docker compose --profile academic --profile map --profile dev up -d
```

Either way they end up with a working backend without installing MongoDB. Nobody needs to run
`create-dev-accounts.sh` again — the accounts live in the shared cluster now, so you run it once in
step 7 and everyone signs in with the same four.

---

## What does not change

**The tests keep using Testcontainers.** They start their own MongoDB on the machine running them,
deliberately: pointing them at a shared cluster would make them slow and intermittently red, and one
person's run would wipe another's data halfway through someone else's. `./mvnw -B verify` needs
Docker and no Atlas.

**What did change: there is no local database any more.** Atlas is not an option beside a local
MongoDB, it is the only one, and the stack needs the network. That trade is written down in
[ADR 0009](adr/0009-atlas-is-the-only-development-database.md).

---

## If it does not connect

| What you see | What it is |
| --- | --- |
| `Authentication failed` | Almost always `authSource=` left in the string, or a password with `@` `:` `/` `?` `#` `%` that was not percent-encoded. If you copied the string from Atlas and only filled the password in, check that one. |
| `Timed out ... no primary` | Your IP is not on the Network Access list, or the entry is still "pending". |
| `not authorized on kapp_auth` | The user was created with a privilege on the wrong database, or with a built-in role instead of Specific Privileges. |
| `Unable to look up TXT record` | A network that blocks SRV DNS lookups — some campus and captive-portal Wi-Fi does. Use the non-SRV `mongodb://` string Atlas also offers. |
| Everything hangs on startup | The `cloud` profile was not used and Compose is waiting on a local `mongo` container that is not running. |
