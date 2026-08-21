#!/usr/bin/env bash
# Prepare LiveKit + Egress local configs (node_ip / ws_url) and start docker infra.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
INFRA="$ROOT/infra"
IP="$("$INFRA/scripts/detect-host-ip.sh" || true)"

if [[ -z "${IP:-}" ]]; then
  echo "Could not detect LAN IP. Set manually:"
  echo "  livekit.yaml node_ip + egress.yaml ws_url"
  exit 1
fi

echo "==> Host LAN IP: $IP"

# Update livekit.yaml node_ip
if grep -q '^[[:space:]]*node_ip:' "$INFRA/livekit.yaml"; then
  sed -i.bak -E "s/^([[:space:]]*node_ip:).*/\\1 $IP/" "$INFRA/livekit.yaml"
  rm -f "$INFRA/livekit.yaml.bak"
fi

# Update egress.yaml ws_url
if grep -q '^ws_url:' "$INFRA/egress.yaml"; then
  sed -i.bak -E "s|^ws_url:.*|ws_url: ws://$IP:7880|" "$INFRA/egress.yaml"
  rm -f "$INFRA/egress.yaml.bak"
fi

echo "==> Updated livekit.yaml node_ip and egress.yaml ws_url"
echo "==> docker compose up (postgres redis minio egress)"
cd "$INFRA"
docker compose up -d postgres redis minio minio-init egress

echo ""
echo "Next:"
echo "  1) livekit-server --config $INFRA/livekit.yaml"
echo "  2) cd virtual-branch-backend && set -a && source ../infra/.env && set +a && SERVER_PORT=8081 ./mvnw spring-boot:run"
echo "  3) cd agent-web && VB_BACKEND_URL=http://127.0.0.1:8081 npm run dev"
echo "  4) Open http://127.0.0.1:5173/infra-load-test"
