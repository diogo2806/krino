#!/bin/sh
set -eu
API_URL_VALUE="${API_URL:-/api}"
cat > /usr/share/nginx/html/config.js <<EOF
window.__KRINO_CONFIG__ = { apiUrl: "${API_URL_VALUE}" };
EOF
