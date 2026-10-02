# KApp dev server

A server anyone on the team can open the admin portal on, with the laptop closed, running what
was last pushed to one branch. It holds no data of its own: it uses the shared Atlas development
cluster, like every laptop, so what is saved there shows everywhere.

## How it stays current

1. **A push** that touches a service, the portal or the contracts runs
   [`dev-images.yml`](../../.github/workflows/dev-images.yml). It builds the images that changed,
   for amd64 and arm64, and publishes them to GHCR as
   `ghcr.io/k-forge/kapp-<service>:<branch>` (with `/` as `-`). It also tags them
   `sha-<commit>` so any build can be gone back to.
2. **The server** runs [`agent.sh`](agent.sh) every two minutes (`kapp-dev-agent.timer`). It pulls
   its branch's images and, when one changed, checks that it was built from a commit on that
   branch. Then it restarts what changed.

About ten minutes pass from push to the phone: most of it is the Maven build.

Nothing in CI reaches a server or holds a secret. The images come from this public repository
and carry nothing private. The database credentials are in the server's `/opt/kapp/.env` and
nowhere else.

## A new server

Any Linux host with Docker (and the compose plugin), curl, python3 and systemd, amd64 or arm64.

1. Copy this directory to it.
2. Write `/opt/kapp/.env`, mode 0600, from [`env.example`](env.example). The secrets are the
   ones in a laptop's `app/backend/microservices/.env`. Never print them, and never commit them.
3. Run `sudo ./install.sh`. It checks the `.env`, installs [`compose.yaml`](compose.yaml), the
   agent and its timer and the `kapp` command ([`kapp.sh`](kapp.sh)), and brings the services
   up.
4. Publish the portal (`127.0.0.1:4300`) and the gateway (`127.0.0.1:8080`) over HTTPS.
   - On a Tailscale host, set `KAPP_DEV_TAILSCALE_SERVE=1` and install does it with
     `tailscale serve`.
   - The portal calls the gateway at its own host name and port 8080, so publish both on one name.
   - `KAPP_DEV_URL` is that name, as the browser shows it.

Everything here listens on 127.0.0.1 and nothing is published with `ports:`: the host decides
what leaves it. The limits in `compose.yaml` suit a host that is lent for other things too. Every
JVM stays small, and the kernel and the scheduler put the services last. A host with room can
relax that in `.env`.

## Users and the semaphore, on demand

The map's five services always run. The **extras** - user-service and semaphore-service, which
the portal's *Users*, *Programs* and *Pensums* pages and registration need - are off until
somebody switches them on, on the host (an SSH session from a phone will do):

```bash
kapp extras on       # checks the host has room, pulls them, starts them, waits until they answer
kapp extras off      # stops them; the map, the portal and signing in keep running
kapp status          # what runs, what each takes, and how much the host has left
kapp stop            # the whole server down, and it stays down (the agent leaves it alone)
kapp start           # the map's five up again, and the extras if they were on
```

- `on` refuses when the host has under ~950 MB available: the two may take up to ~550 MB, and
  the rest is the margin the host keeps.
- While they are on, the agent keeps them on the branch's images like the rest. If the host's
  available memory falls under 300 MB it switches them off itself, and `kapp status` says when
  and why. The host's other work comes first.
- They need `MONGO_USER_URI` and `MONGO_SEMAPHORE_URI` in `.env`; without them they stay off.

## Running it

```bash
sudo systemctl start kapp-dev-agent        # look for new images now
journalctl -u kapp-dev-agent -n 20         # what it did
cd /opt/kapp && sudo docker compose ps
sudo touch /opt/kapp/.stopped && sudo docker compose -f /opt/kapp/compose.yaml stop   # stop, and stay stopped
sudo rm /opt/kapp/.stopped && sudo systemctl start kapp-dev-agent                     # start again
```

To follow another branch, change `KAPP_DEV_BRANCH` and `KAPP_DEV_TAG` in `.env`, then start the
agent. The branch needs one push after `dev-images.yml` reached it, or a manual run of the
workflow on it.

## What a push can and cannot do

- **What a push can do:** change the code the server runs, if the push is to the branch the
  server follows. The agent refuses an image built from anything else.
- **What it cannot do:** change what the containers may do on the host. That means the network,
  mounts, capabilities and limits, all set in `compose.yaml` and `.env`. The agent never touches
  either; `install.sh`, run by a person, applies a change to them. The JVMs run without Linux
  capabilities and none of the containers can gain privileges.
