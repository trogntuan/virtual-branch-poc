#!/usr/bin/env bash
# Verify Cloudflare R2 credentials (LiveKit egress recording).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ENV_FILE="${ROOT}/infra/.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE"
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

: "${VB_EGRESS_BUCKET:?Set VB_EGRESS_BUCKET in infra/.env}"
: "${VB_EGRESS_ENDPOINT:?Set VB_EGRESS_ENDPOINT in infra/.env}"
: "${VB_EGRESS_ACCESS_KEY:?Set VB_EGRESS_ACCESS_KEY in infra/.env}"
: "${VB_EGRESS_SECRET_KEY:?Set VB_EGRESS_SECRET_KEY in infra/.env}"

if ! command -v aws >/dev/null 2>&1; then
  echo "ERROR: aws CLI required. Install: brew install awscli"
  exit 1
fi

TMP="$(mktemp)"
echo "r2-ok" > "$TMP"
KEY="_test/r2-verify-$(date +%s).txt"
REGION="${VB_EGRESS_REGION:-auto}"

echo "==> Test upload s3://${VB_EGRESS_BUCKET}/${KEY} via R2 endpoint"
if AWS_ACCESS_KEY_ID="$VB_EGRESS_ACCESS_KEY" \
   AWS_SECRET_ACCESS_KEY="$VB_EGRESS_SECRET_KEY" \
   aws s3 cp "$TMP" "s3://${VB_EGRESS_BUCKET}/${KEY}" \
     --endpoint-url "$VB_EGRESS_ENDPOINT" \
     --region "$REGION"; then
  AWS_ACCESS_KEY_ID="$VB_EGRESS_ACCESS_KEY" \
  AWS_SECRET_ACCESS_KEY="$VB_EGRESS_SECRET_KEY" \
  aws s3 rm "s3://${VB_EGRESS_BUCKET}/${KEY}" \
    --endpoint-url "$VB_EGRESS_ENDPOINT" \
    --region "$REGION" >/dev/null 2>&1 || true
  echo "OK: R2 credentials work — restart backend and test recording on a new call"
else
  echo "ERROR: R2 upload failed. Check bucket, API token (Object Read & Write), and account endpoint."
  echo "  Dashboard: https://dash.cloudflare.com → R2 → Manage R2 API Tokens"
  exit 1
fi
