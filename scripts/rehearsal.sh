#!/usr/bin/env bash
# End-to-end API rehearsal for Virtual Branch POC (no browser required).
set -euo pipefail

BASE_URL="${VB_BACKEND_URL:-http://localhost:8081}"
PASS=0
FAIL=0

pass() { echo "  ✓ $1"; PASS=$((PASS + 1)); }
fail() { echo "  ✗ $1"; FAIL=$((FAIL + 1)); }

echo "=== Virtual Branch POC Rehearsal ==="
echo "Backend: $BASE_URL"
echo ""

# --- Infra ---
echo "[1/6] Infrastructure"
if docker exec vb-redis redis-cli ping 2>/dev/null | grep -q PONG; then
  pass "Redis PONG"
else
  fail "Redis not responding"
fi

if docker exec vb-postgres psql -U virtual_branch -d virtual_branch -tAc "SELECT 1" 2>/dev/null | grep -q 1; then
  pass "PostgreSQL OK"
else
  fail "PostgreSQL not responding"
fi

if curl -sf http://localhost:7880 >/dev/null 2>&1; then
  pass "LiveKit :7880"
else
  fail "LiveKit not on :7880"
fi

if curl -sf http://localhost:9000/minio/health/live >/dev/null 2>&1; then
  pass "MinIO :9000"
else
  fail "MinIO not on :9000"
fi
echo ""

# --- Health ---
echo "[2/6] Backend health"
HEALTH=$(curl -sf "$BASE_URL/api/v1/health" 2>/dev/null || echo "")
if echo "$HEALTH" | grep -q '"status":"UP"'; then
  pass "GET /api/v1/health -> UP"
else
  fail "GET /api/v1/health (got: ${HEALTH:-connection refused})"
fi
echo ""

# --- Session + tokens ---
echo "[3/6] Session & tokens"
SESSION=$(curl -sf -X POST "$BASE_URL/api/v1/sessions" -H 'Content-Type: application/json')
SESSION_ID=$(echo "$SESSION" | python3 -c "import sys,json; print(json.load(sys.stdin)['sessionId'])" 2>/dev/null || echo "")
if [[ -n "$SESSION_ID" ]]; then
  pass "Create session -> $SESSION_ID"
else
  fail "Create session"
  echo "Aborting — backend unavailable at $BASE_URL"
  exit 1
fi

AGENT_TOKEN=$(curl -sf -X POST "$BASE_URL/api/v1/sessions/$SESSION_ID/token" \
  -H 'Content-Type: application/json' \
  -d '{"identity":"rehearsal-agent","name":"Agent","role":"AGENT"}')
if echo "$AGENT_TOKEN" | grep -q participantToken; then
  pass "Agent token issued (ws: $(echo "$AGENT_TOKEN" | python3 -c "import sys,json; print(json.load(sys.stdin)['serverUrl'])"))"
else
  fail "Agent token"
fi

CUSTOMER_TOKEN=$(curl -sf -X POST "$BASE_URL/api/v1/sessions/$SESSION_ID/token" \
  -H 'Content-Type: application/json' \
  -d '{"identity":"rehearsal-customer","name":"Customer","role":"CUSTOMER"}')
if echo "$CUSTOMER_TOKEN" | grep -q participantToken; then
  pass "Customer token issued"
else
  fail "Customer token"
fi

# Security: token response must not contain api secret
if echo "$AGENT_TOKEN$CUSTOMER_TOKEN" | grep -qi "virtual_branch_poc_dev_secret"; then
  fail "API secret leaked in token response"
else
  pass "No API secret in token response"
fi
echo ""

# --- PDF upload + consent gate ---
echo "[4/6] Document & Doc Collab consent gate"
PDF_FILE="/tmp/vb-rehearsal-test.pdf"
printf '%%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n3 0 obj<</Type/Page/MediaBox[0 0 612 792]/Parent 2 0 R>>endobj\nxref\n0 4\n0000000000 65535 f\n0000000009 00000 n\n0000000058 00000 n\n0000000115 00000 n\ntrailer<</Size 4/Root 1 0 R>>\nstartxref\n190\n%%%%EOF' > "$PDF_FILE"

DOC=$(curl -sf -X POST "$BASE_URL/api/v1/sessions/$SESSION_ID/documents" -F "file=@$PDF_FILE;type=application/pdf")
DOC_ID=$(echo "$DOC" | python3 -c "import sys,json; print(json.load(sys.stdin)['documentId'])" 2>/dev/null || echo "")
if [[ -n "$DOC_ID" ]]; then
  pass "PDF uploaded -> $DOC_ID"
else
  fail "PDF upload"
fi

if echo "$DOC" | grep -q readUrl; then
  pass "Agent gets presigned readUrl on upload"
else
  fail "No readUrl on upload response"
fi

COLLAB=$(curl -sf -X POST "$BASE_URL/api/v1/sessions/$SESSION_ID/doc-collabs" \
  -H 'Content-Type: application/json' \
  -d "{\"documentId\":\"$DOC_ID\"}")
COLLAB_ID=$(echo "$COLLAB" | python3 -c "import sys,json; print(json.load(sys.stdin)['collabId'])" 2>/dev/null || echo "")
if [[ -n "$COLLAB_ID" ]]; then
  pass "Doc collab requested -> $COLLAB_ID"
else
  fail "Doc collab request"
fi

HTTP_BEFORE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/v1/doc-collabs/$COLLAB_ID/document-url")
if [[ "$HTTP_BEFORE" == "403" ]]; then
  pass "Document URL blocked before consent (403)"
else
  fail "Consent gate: expected 403, got $HTTP_BEFORE"
fi

CONSENT=$(curl -sf -X POST "$BASE_URL/api/v1/doc-collabs/$COLLAB_ID/consent" \
  -H 'Content-Type: application/json' \
  -d '{"decision":"ACCEPT"}')
if echo "$CONSENT" | grep -q '"status":"ACTIVE"'; then
  pass "Customer consent ACCEPT -> ACTIVE"
else
  fail "Consent accept"
fi

DOC_URL=$(curl -sf "$BASE_URL/api/v1/doc-collabs/$COLLAB_ID/document-url")
if echo "$DOC_URL" | grep -q readUrl; then
  pass "Document URL available after consent"
else
  fail "Document URL after consent"
fi

curl -sf -X POST "$BASE_URL/api/v1/doc-collabs/$COLLAB_ID/end" -H 'Content-Type: application/json' >/dev/null
pass "Doc collab ended"
rm -f "$PDF_FILE"
echo ""

# --- DB security check ---
echo "[5/6] DB persistence (no signed URLs stored)"
SIGNED_IN_DB=$(docker exec vb-postgres psql -U virtual_branch -d virtual_branch -tAc \
  "SELECT COUNT(*) FROM vb_document WHERE object_key LIKE '%X-Amz%' OR object_key LIKE '%http%';" 2>/dev/null || echo "err")
if [[ "$SIGNED_IN_DB" == "0" ]]; then
  pass "vb_document stores object_key only (no URLs)"
else
  fail "Unexpected URL-like data in vb_document ($SIGNED_IN_DB rows)"
fi

OBJ_KEY=$(docker exec vb-postgres psql -U virtual_branch -d virtual_branch -tAc \
  "SELECT object_key FROM vb_document WHERE id='$DOC_ID';" 2>/dev/null || echo "")
if [[ "$OBJ_KEY" == documents/* ]]; then
  pass "Object key pattern: $OBJ_KEY"
else
  fail "Object key pattern (got: $OBJ_KEY)"
fi
echo ""

# --- Recording API (smoke — MP4 needs LiveKit room + Egress) ---
echo "[6/6] Recording API smoke"
REC=$(curl -sf -X POST "$BASE_URL/api/v1/sessions/$SESSION_ID/recordings" -H 'Content-Type: application/json' 2>/dev/null || echo "")
REC_ID=$(echo "$REC" | python3 -c "import sys,json; print(json.load(sys.stdin).get('recordingId',''))" 2>/dev/null || echo "")
if [[ -n "$REC_ID" ]]; then
  pass "Recording start requested -> $REC_ID"
  STOP=$(curl -sf -X POST "$BASE_URL/api/v1/recordings/$REC_ID/stop" -H 'Content-Type: application/json' 2>/dev/null || echo "")
  if echo "$STOP" | grep -q recordingId; then
    pass "Recording stop API OK"
  else
    fail "Recording stop API"
  fi
else
  fail "Recording start (Egress may be unavailable — check egress logs)"
fi

curl -sf -X POST "$BASE_URL/api/v1/sessions/$SESSION_ID/end" -H 'Content-Type: application/json' >/dev/null
pass "Session ended"
echo ""

echo "=== Results: $PASS passed, $FAIL failed ==="
if [[ "$FAIL" -gt 0 ]]; then
  echo ""
  echo "Browser demo (manual):"
  echo "  Agent:    http://localhost:5173/agent"
  echo "  Customer: http://localhost:5173/customer-test"
  echo "  Session:  $SESSION_ID (already ended — create new in Agent UI)"
  exit 1
fi

echo ""
echo "All API checks passed."
echo ""
echo "Browser demo — open two tabs:"
echo "  Agent:    http://localhost:5173/agent"
echo "  Customer: http://localhost:5173/customer-test"
exit 0
