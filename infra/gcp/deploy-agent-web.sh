#!/usr/bin/env bash
# Deploy Agent Web to Cloud Run (nginx proxies /api → Cloud Run backend).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PROJECT="${GCP_PROJECT:-project-4cd8e655-f1eb-4da9-bea}"
REGION="${GCP_REGION:-asia-southeast1}"
SERVICE="${CLOUD_RUN_AGENT_SERVICE:-vb-agent-web}"
BACKEND_SERVICE="${CLOUD_RUN_SERVICE:-vb-backend}"
REPO="${ARTIFACT_REPO:-vb}"
IMAGE="${REGION}-docker.pkg.dev/${PROJECT}/${REPO}/agent-web:latest"

echo "==> Project: $PROJECT  Region: $REGION  Service: $SERVICE"
gcloud config set project "$PROJECT"

BACKEND_URL="$(gcloud run services describe "$BACKEND_SERVICE" --region="$REGION" --format='value(status.url)')"
if [[ -z "$BACKEND_URL" ]]; then
  echo "Backend service $BACKEND_SERVICE not found. Deploy backend first."
  exit 1
fi
echo "==> Backend: $BACKEND_URL"

echo "==> Build & push Agent Web image"
gcloud builds submit "$ROOT/agent-web" --tag "$IMAGE"

echo "==> Deploy Cloud Run service"
gcloud run deploy "$SERVICE" \
  --image "$IMAGE" \
  --region "$REGION" \
  --allow-unauthenticated \
  --port 8080 \
  --memory 256Mi \
  --cpu 1 \
  --set-env-vars "BACKEND_URL=${BACKEND_URL}"

AGENT_URL="$(gcloud run services describe "$SERVICE" --region="$REGION" --format='value(status.url)')"
CORS="http://localhost:5173,https://*.run.app,${AGENT_URL}"
echo "==> Update backend CORS: $CORS"
gcloud run services update "$BACKEND_SERVICE" \
  --region="$REGION" \
  --update-env-vars "^@^VB_CORS_ALLOWED_ORIGINS=${CORS}"

echo ""
echo "Agent Web: $AGENT_URL"
echo "Backend:   $BACKEND_URL"
echo "Health:    ${BACKEND_URL}/api/v1/health"
