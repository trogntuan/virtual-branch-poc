#!/bin/sh
set -eu
: "${BACKEND_URL:?Set BACKEND_URL to the Cloud Run backend origin, e.g. https://vb-backend-xxx.run.app}"
BACKEND_HOST="$(printf '%s' "$BACKEND_URL" | sed -E 's|^https?://||; s|/.*||')"
sed \
  -e "s|BACKEND_URL_PLACEHOLDER|${BACKEND_URL}|g" \
  -e "s|BACKEND_HOST_PLACEHOLDER|${BACKEND_HOST}|g" \
  /etc/nginx/conf.d/default.conf.template > /etc/nginx/conf.d/default.conf
exec nginx -g 'daemon off;'
