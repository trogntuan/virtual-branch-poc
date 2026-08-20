#!/usr/bin/env bash
# Verify GCS HMAC credentials (backend PDF via gsutil path — NOT for LiveKit egress).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ENV_FILE="${ROOT}/infra/.env"
PROJECT="${GCP_PROJECT:-project-4cd8e655-f1eb-4da9-bea}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE"
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

: "${STORAGE_ACCESS_KEY:?Set STORAGE_ACCESS_KEY in infra/.env}"
: "${STORAGE_SECRET_KEY:?Set STORAGE_SECRET_KEY in infra/.env}"
: "${VB_STORAGE_BUCKET:?Set VB_STORAGE_BUCKET in infra/.env}"

TMP="$(mktemp)"
echo "hmac-ok" > "$TMP"
KEY="_test/hmac-verify-$(date +%s).txt"

echo "==> Test upload gs://${VB_STORAGE_BUCKET}/${KEY} via gsutil (HMAC)"
if BOTO_CONFIG=/dev/null gsutil \
  -o "Credentials:gs_access_key_id=${STORAGE_ACCESS_KEY}" \
  -o "Credentials:gs_secret_access_key=${STORAGE_SECRET_KEY}" \
  cp "$TMP" "gs://${VB_STORAGE_BUCKET}/${KEY}"; then
  echo "OK: HMAC credentials valid for GCS"
  BOTO_CONFIG=/dev/null gsutil \
    -o "Credentials:gs_access_key_id=${STORAGE_ACCESS_KEY}" \
    -o "Credentials:gs_secret_access_key=${STORAGE_SECRET_KEY}" \
    rm "gs://${VB_STORAGE_BUCKET}/${KEY}" >/dev/null 2>&1 || true
else
  echo "ERROR: HMAC upload failed — re-create key in Console:"
  echo "  https://console.cloud.google.com/storage/settings;tab=interoperability&project=$PROJECT"
  exit 1
fi

echo ""
echo "NOTE: LiveKit egress recording needs service account JSON, not HMAC."
echo "      Set GOOGLE_APPLICATION_CREDENTIALS=../infra/gcp/vb-storage-sa.json"
echo "      SA: vb-storage-egress@${PROJECT}.iam.gserviceaccount.com"
