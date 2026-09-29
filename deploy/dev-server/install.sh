#!/usr/bin/env bash
# Installs, or updates, a KApp dev server on this host from this directory:
#
#   sudo ./install.sh
#
# Needs Docker with the compose plugin, curl, python3, systemd, and /opt/kapp/.env written first
# (env.example lists what goes in it). Puts compose.yaml and agent.sh in /opt/kapp, starts
# kapp-dev-agent.timer, and runs the agent once. Run it again after compose.yaml or the agent
# change: the agent itself never touches them.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
[[ $EUID -eq 0 ]] || { echo "run it as root" >&2; exit 1; }

env=/opt/kapp/.env
install -d -m 0755 /opt/kapp
[[ -f "$env" ]] || { echo "write $env first (mode 0600): see env.example" >&2; exit 1; }
chown root:root "$env"
chmod 0600 "$env"
missing=()
for key in KAPP_DEV_BRANCH KAPP_DEV_TAG KAPP_DEV_URL MONGO_AUTH_URI MONGO_MAP_URI \
           KAPP_JWT_PRIVATE_KEY KAPP_JWT_PUBLIC_KEY KAPP_INTERNAL_TOKEN; do
  grep -q "^$key=." "$env" || missing+=("$key")
done
(( ${#missing[@]} == 0 )) || { echo "missing in $env: ${missing[*]}" >&2; exit 1; }

install -m 0644 "$here/compose.yaml" /opt/kapp/compose.yaml
install -m 0755 "$here/agent.sh" /opt/kapp/agent.sh
install -m 0644 "$here/kapp-dev-agent.service" "$here/kapp-dev-agent.timer" /etc/systemd/system/
(cd /opt/kapp && docker compose config -q)

if [[ "$(sed -n 's/^KAPP_DEV_TAILSCALE_SERVE=//p' "$env" | tail -1)" == 1 ]]; then
  tailscale serve --bg --https=443 http://127.0.0.1:4300 >/dev/null
  tailscale serve --bg --https=8080 http://127.0.0.1:8080 >/dev/null
fi

systemctl daemon-reload
rm -f /opt/kapp/.stopped
# The first run through systemd, as the timer's are: one at a time, and in the journal.
systemctl start kapp-dev-agent.service
systemctl enable --now kapp-dev-agent.timer
echo "installed: $(cd /opt/kapp && docker compose ps --format '{{.Service}}={{.Status}}' | tr '\n' ' ')"
