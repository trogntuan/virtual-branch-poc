#!/usr/bin/env bash
# One-time GCS setup for Virtual Branch POC (local dev + LiveKit Cloud).
set -euo pipefail

PROJECT="${GCP_PROJECT:-project-4cd8e655-f1eb-4da9-bea}"
BUCKET="${VB_STORAGE_BUCKET:-project-4cd8e655-vb-poc}"
USER_EMAIL="${GCP_USER_EMAIL:-$(gcloud config get-value account 2>/dev/null)}"

echo "Project: $PROJECT"
echo "Bucket:  gs://$BUCKET"
echo "User:    $USER_EMAIL"

gcloud config set project "$PROJECT"

if ! gsutil ls -b "gs://$BUCKET" >/dev/null 2>&1; then
  echo "Creating bucket gs://$BUCKET ..."
  gsutil mb -l asia-southeast1 "gs://$BUCKET"
fi

echo "Applying CORS for Agent Web (localhost + Cloud Run) ..."
gsutil cors set "$(dirname "$0")/gcs-cors.json" "gs://$BUCKET"

if [[ -n "$USER_EMAIL" ]]; then
  echo "Granting objectAdmin to $USER_EMAIL ..."
  gsutil iam ch "user:${USER_EMAIL}:objectAdmin" "gs://$BUCKET"
fi

echo ""
echo "Done. Next steps:"
echo "  1. gcloud auth application-default login"
echo "  2. Add to infra/.env:"
echo "       VB_STORAGE_PROVIDER=gcs"
echo "       VB_STORAGE_BUCKET=$BUCKET"
echo "  3. For LiveKit Cloud recording, add GCS HMAC (Console → Storage → Interoperability):"
echo "       VB_STORAGE_ENDPOINT=https://storage.googleapis.com"
echo "       STORAGE_ACCESS_KEY=..."
echo "       STORAGE_SECRET_KEY=..."
echo "       VB_STORAGE_FORCE_PATH_STYLE=true"
echo "  4. Restart backend with: source infra/.env && SERVER_PORT=8081 ./mvnw spring-boot:run"
