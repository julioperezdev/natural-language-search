#!/usr/bin/env bash

set -euo pipefail
umask 077

graph_api_version="${NLS_WHATSAPP_GRAPH_API_VERSION:-v25.0}"

if [[ ! "$graph_api_version" =~ ^v[0-9]+\.[0-9]+$ ]]; then
  printf 'NLS_WHATSAPP_GRAPH_API_VERSION debe tener formato vN.N.\n' >&2
  exit 1
fi

printf 'WhatsApp Business Account ID (WABA ID): '
IFS= read -r waba_id
if [[ ! "$waba_id" =~ ^[0-9]+$ ]]; then
  printf 'El WABA ID debe contener solo dígitos.\n' >&2
  exit 1
fi

printf 'Access Token (entrada oculta): '
IFS= read -r -s access_token
printf '\n'
if [[ -z "$access_token" ]]; then
  printf 'El Access Token es obligatorio.\n' >&2
  exit 1
fi

temporary_directory="$(mktemp -d)"
cleanup() {
  rm -rf "$temporary_directory"
  unset access_token
}
trap cleanup EXIT

graph_api_url="https://graph.facebook.com/${graph_api_version}/${waba_id}/subscribed_apps"
jq -n -r \
  --arg url "$graph_api_url" \
  --arg authorization "Authorization: Bearer ${access_token}" \
  '"url = " + ($url | tojson) + "\nrequest = \"POST\"\nheader = " + ($authorization | tojson)' \
  > "$temporary_directory/request.conf"

curl --config "$temporary_directory/request.conf" \
  --fail-with-body \
  --silent \
  --show-error
