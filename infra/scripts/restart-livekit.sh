#!/usr/bin/env bash
# Refresh node_ip / egress ws_url and restart local LiveKit server.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
INFRA="$ROOT/infra"
IP="$("$INFRA/scripts/detect-host-ip.sh" || true)"

if [[ -z "${IP:-}" ]]; then
  echo "Could not detect LAN IP. Set livekit.yaml node_ip manually."
  exit 1
fi

echo "==> Host LAN IP: $IP"

sed -i.bak -E "s/^([[:space:]]*node_ip:).*/\\1 $IP/" "$INFRA/livekit.yaml"
rm -f "$INFRA/livekit.yaml.bak"
sed -i.bak -E "s|^ws_url:.*|ws_url: ws://$IP:7880|" "$INFRA/egress.yaml"
rm -f "$INFRA/egress.yaml.bak"

PIDS=$(lsof -t -iTCP:7880 -sTCP:LISTEN 2>/dev/null || true)
if [[ -n "${PIDS:-}" ]]; then
  echo "==> Stopping livekit-server ($PIDS)"
  kill $PIDS 2>/dev/null || true
  sleep 1
fi

echo "==> Starting livekit-server"
cd "$INFRA"
exec livekit-server --config livekit.yaml
