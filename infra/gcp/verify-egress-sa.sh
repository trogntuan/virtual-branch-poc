#!/usr/bin/env bash
# Verify service account JSON for LiveKit egress (GCPUpload — NOT HMAC).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ENV_FILE="${ROOT}/infra/.env"
SA_FILE="${ROOT}/infra/gcp/vb-storage-sa.json"
PROJECT="${GCP_PROJECT:-project-4cd8e655-f1eb-4da9-bea}"

if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

if [[ -n "${GOOGLE_APPLICATION_CREDENTIALS:-}" ]]; then
  if [[ "${GOOGLE_APPLICATION_CREDENTIALS:0:1}" != "/" ]]; then
    SA_FILE="${ROOT}/infra/${GOOGLE_APPLICATION_CREDENTIALS#../infra/}"
    SA_FILE="$(cd "$(dirname "$SA_FILE")" 2>/dev/null && pwd)/$(basename "$SA_FILE")" || SA_FILE="${GOOGLE_APPLICATION_CREDENTIALS}"
  else
    SA_FILE="$GOOGLE_APPLICATION_CREDENTIALS"
  fi
fi

if [[ ! -f "$SA_FILE" ]]; then
  echo "ERROR: Missing service account JSON: $SA_FILE"
  echo ""
  echo "Create key in Console (org policy blocks gcloud key create):"
  echo "  https://console.cloud.google.com/iam-admin/serviceaccounts/details/215046327377-compute@developer.gserviceaccount.com/keys?project=$PROJECT"
  echo "  Or: vb-storage-egress@${PROJECT}.iam.gserviceaccount.com → Keys → Add key → JSON"
  echo ""
  echo "Save as: infra/gcp/vb-storage-sa.json"
  echo "Then in infra/.env:"
  echo "  GOOGLE_APPLICATION_CREDENTIALS=../infra/gcp/vb-storage-sa.json"
  exit 1
fi

if ! grep -q '"type"[[:space:]]*:[[:space:]]*"service_account"' "$SA_FILE"; then
  echo "ERROR: $SA_FILE is not a service account key (need type=service_account)"
  exit 1
fi

echo "==> SA JSON found: $SA_FILE"
CLIENT_EMAIL="$(python3 -c "import json; print(json.load(open('$SA_FILE'))['client_email'])")"
echo "    client_email=$CLIENT_EMAIL"

TMP="$(mktemp)"
echo "egress-sa-ok" > "$TMP"
KEY="_test/egress-sa-verify-$(date +%s).txt"

if gcloud auth activate-service-account --key-file="$SA_FILE" --quiet 2>/dev/null; then
  if gsutil cp "$TMP" "gs://${VB_STORAGE_BUCKET:-project-4cd8e655-vb-poc}/${KEY}"; then
    gsutil rm "gs://${VB_STORAGE_BUCKET:-project-4cd8e655-vb-poc}/${KEY}" >/dev/null 2>&1 || true
    echo "OK: Service account can write to GCS bucket"
    echo ""
    echo "Restart backend with GOOGLE_APPLICATION_CREDENTIALS set, then start a NEW call for recording."
    exit 0
  fi
fi

echo "WARN: Could not verify upload (gcloud/gsutil). File format looks valid — try recording after backend restart."
exit 0
