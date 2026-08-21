#!/usr/bin/env bash
# Deploy Virtual Branch backend to Cloud Run (GCP + LiveKit Cloud + GCS).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PROJECT="${GCP_PROJECT:-project-4cd8e655-f1eb-4da9-bea}"
REGION="${GCP_REGION:-asia-southeast1}"
SERVICE="${CLOUD_RUN_SERVICE:-vb-backend}"
REPO="${ARTIFACT_REPO:-vb}"
IMAGE="${REGION}-docker.pkg.dev/${PROJECT}/${REPO}/backend:latest"
SQL_INSTANCE="${PROJECT}:${REGION}:vb-poc-db"
BUCKET="${VB_STORAGE_BUCKET:-project-4cd8e655-vb-poc}"
if [[ "$BUCKET" == "virtual-branch" ]]; then
  echo "WARN: VB_STORAGE_BUCKET=virtual-branch looks like local MinIO; using project-4cd8e655-vb-poc"
  BUCKET="project-4cd8e655-vb-poc"
fi

ENV_FILE="${ROOT}/infra/.env"
if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE — copy from infra/.env.example and fill LiveKit + storage values."
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

: "${LIVEKIT_API_KEY:?Set LIVEKIT_API_KEY in infra/.env}"
: "${LIVEKIT_API_SECRET:?Set LIVEKIT_API_SECRET in infra/.env}"
: "${VB_LIVEKIT_API_URL:?Set VB_LIVEKIT_API_URL in infra/.env}"
: "${VB_LIVEKIT_WS_URL:?Set VB_LIVEKIT_WS_URL in infra/.env}"

echo "==> Project: $PROJECT  Region: $REGION  Service: $SERVICE"
gcloud config set project "$PROJECT"

echo "==> Grant Cloud Run / Cloud Build IAM (idempotent)"
PROJECT_NUMBER="$(gcloud projects describe "$PROJECT" --format='value(projectNumber)')"
RUN_SA="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"
BUILD_SA="${PROJECT_NUMBER}@cloudbuild.gserviceaccount.com"
for SA in "$RUN_SA" "$BUILD_SA"; do
  gcloud projects add-iam-policy-binding "$PROJECT" --member="serviceAccount:${SA}" --role="roles/storage.admin" --quiet >/dev/null || true
  gcloud projects add-iam-policy-binding "$PROJECT" --member="serviceAccount:${SA}" --role="roles/artifactregistry.writer" --quiet >/dev/null || true
done
gcloud projects add-iam-policy-binding "$PROJECT" --member="serviceAccount:${RUN_SA}" --role="roles/secretmanager.secretAccessor" --quiet >/dev/null || true
gcloud projects add-iam-policy-binding "$PROJECT" --member="serviceAccount:${RUN_SA}" --role="roles/cloudsql.client" --quiet >/dev/null || true

echo "==> Grant Cloud Run default SA access to GCS bucket gs://$BUCKET"
gsutil iam ch "serviceAccount:${RUN_SA}:objectAdmin" "gs://${BUCKET}" 2>/dev/null || true

echo "==> Ensure Secret Manager entries"
# Only create/update secrets when explicitly requested — avoids overwriting
# Cloud HMAC/R2 secrets with local MinIO values from infra/.env.
UPDATE_SECRETS="${UPDATE_SECRETS:-false}"

ensure_secret() {
  local name="$1"
  local value="$2"
  if [[ -z "$value" ]]; then
    return 0
  fi
  if ! gcloud secrets describe "$name" --project="$PROJECT" >/dev/null 2>&1; then
    echo -n "$value" | gcloud secrets create "$name" --data-file=-
  elif [[ "$UPDATE_SECRETS" == "true" ]]; then
    echo -n "$value" | gcloud secrets versions add "$name" --data-file=-
  else
    echo "Keep existing secret $name (set UPDATE_SECRETS=true to overwrite)"
  fi
}

ensure_secret vb-livekit-secret "${LIVEKIT_API_SECRET:-}"
ensure_secret vb-storage-secret "${STORAGE_SECRET_KEY:-}"
if [[ -n "${VB_EGRESS_SECRET_KEY:-}" ]]; then
  echo "==> Ensure R2 egress secret"
  ensure_secret vb-egress-secret "$VB_EGRESS_SECRET_KEY"
else
  echo "WARN: VB_EGRESS_SECRET_KEY not set — LiveKit Cloud recording will fail until R2 is configured."
fi

echo "==> Build & push container image"
gcloud builds submit "$ROOT/virtual-branch-backend" --tag "$IMAGE"

CORS="${VB_CORS_ALLOWED_ORIGINS:-http://localhost:5173,https://*.run.app}"
ENV_VARS="SPRING_PROFILES_ACTIVE=prod"
ENV_VARS+="@SPRING_DATASOURCE_URL=jdbc:postgresql:///virtual_branch?cloudSqlInstance=${SQL_INSTANCE}&socketFactory=com.google.cloud.sql.postgres.SocketFactory"
ENV_VARS+="@SPRING_DATASOURCE_USERNAME=virtual_branch"
ENV_VARS+="@LIVEKIT_API_KEY=${LIVEKIT_API_KEY}"
ENV_VARS+="@VB_LIVEKIT_API_URL=${VB_LIVEKIT_API_URL}"
ENV_VARS+="@VB_LIVEKIT_WS_URL=${VB_LIVEKIT_WS_URL}"
ENV_VARS+="@VB_STORAGE_PROVIDER=gcs"
ENV_VARS+="@VB_STORAGE_BUCKET=${BUCKET}"
ENV_VARS+="@VB_STORAGE_ENDPOINT=https://storage.googleapis.com"
ENV_VARS+="@VB_STORAGE_FORCE_PATH_STYLE=true"
ENV_VARS+="@GOOGLE_CLOUD_PROJECT=${PROJECT}"
ENV_VARS+="@VB_CORS_ALLOWED_ORIGINS=${CORS}"

SECRETS="SPRING_DATASOURCE_PASSWORD=vb-db-password:latest,LIVEKIT_API_SECRET=vb-livekit-secret:latest"

if [[ -n "${STORAGE_ACCESS_KEY:-}" ]]; then
  ENV_VARS+="@STORAGE_ACCESS_KEY=${STORAGE_ACCESS_KEY}"
fi
if gcloud secrets describe vb-storage-secret --project="$PROJECT" >/dev/null 2>&1; then
  SECRETS+=",STORAGE_SECRET_KEY=vb-storage-secret:latest"
fi

if [[ -n "${VB_EGRESS_BUCKET:-}" ]]; then
  ENV_VARS+="@VB_EGRESS_BUCKET=${VB_EGRESS_BUCKET}"
fi
if [[ -n "${VB_EGRESS_ENDPOINT:-}" ]]; then
  ENV_VARS+="@VB_EGRESS_ENDPOINT=${VB_EGRESS_ENDPOINT}"
fi
if [[ -n "${VB_EGRESS_ACCESS_KEY:-}" ]]; then
  ENV_VARS+="@VB_EGRESS_ACCESS_KEY=${VB_EGRESS_ACCESS_KEY}"
fi
if [[ -n "${VB_EGRESS_REGION:-}" ]]; then
  ENV_VARS+="@VB_EGRESS_REGION=${VB_EGRESS_REGION}"
fi
if [[ -n "${VB_EGRESS_FORCE_PATH_STYLE:-}" ]]; then
  ENV_VARS+="@VB_EGRESS_FORCE_PATH_STYLE=${VB_EGRESS_FORCE_PATH_STYLE}"
fi
if gcloud secrets describe vb-egress-secret --project="$PROJECT" >/dev/null 2>&1; then
  SECRETS+=",VB_EGRESS_SECRET_KEY=vb-egress-secret:latest"
fi

SA_JSON="${ROOT}/infra/gcp/vb-storage-sa.json"
if [[ -f "$SA_JSON" ]] && [[ -s "$SA_JSON" ]]; then
  echo "==> Upload GCS SA JSON to Secret Manager"
  if ! gcloud secrets describe vb-gcs-sa-json --project="$PROJECT" >/dev/null 2>&1; then
    gcloud secrets create vb-gcs-sa-json --data-file="$SA_JSON"
  else
    gcloud secrets versions add vb-gcs-sa-json --data-file="$SA_JSON"
  fi
  SECRETS+=",VB_GCS_SA_JSON=vb-gcs-sa-json:latest"
fi

echo "==> Deploy Cloud Run service"
gcloud run deploy "$SERVICE" \
  --image "$IMAGE" \
  --region "$REGION" \
  --allow-unauthenticated \
  --add-cloudsql-instances "$SQL_INSTANCE" \
  --memory 512Mi \
  --cpu 1 \
  --set-env-vars "^@^${ENV_VARS}" \
  --set-secrets "$SECRETS"

URL="$(gcloud run services describe "$SERVICE" --region="$REGION" --format='value(status.url)')"
echo ""
echo "Deployed: $URL"
echo "Health:   ${URL}/api/v1/health"
echo ""
echo "Next: ./infra/gcp/deploy-agent-web.sh"
