#!/usr/bin/env bash
# Print the IP address LiveKit should advertise for local dev (Mac/Linux).
set -euo pipefail

if [[ "$(uname -s)" == "Darwin" ]]; then
  ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || true
elif command -v ip >/dev/null 2>&1; then
  ip route get 1.1.1.1 2>/dev/null | awk '{for (i=1;i<=NF;i++) if ($i=="src") {print $(i+1); exit}}'
fi
