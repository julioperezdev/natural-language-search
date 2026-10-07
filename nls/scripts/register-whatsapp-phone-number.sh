#!/usr/bin/env bash

set -euo pipefail
umask 077

graph_api_version="${NLS_WHATSAPP_GRAPH_API_VERSION:-v25.0}"

if [[ ! "$graph_api_version" =~ ^v[0-9]+\.[0-9]+$ ]]; then
  printf 'NLS_WHATSAPP_GRAPH_API_VERSION debe tener formato vN.N.\n' >&2
  exit 1
fi

printf 'Phone Number ID: '
IFS= read -r phone_number_id
if [[ ! "$phone_number_id" =~ ^[0-9]+$ ]]; then
  printf 'El Phone Number ID debe contener solo dígitos.\n' >&2
  exit 1
fi

printf 'Access Token (entrada oculta): '
IFS= read -r -s access_token
printf '\n'
if [[ -z "$access_token" ]]; then
  printf 'El Access Token es obligatorio.\n' >&2
  exit 1
fi

printf 'Elegí el PIN nuevo de 6 dígitos (entrada oculta): '
IFS= read -r -s pin
printf '\n'
if [[ ! "$pin" =~ ^[0-9]{6}$ ]]; then
  printf 'El PIN debe tener exactamente 6 dígitos.\n' >&2
  exit 1
fi

printf 'Confirmá el PIN (entrada oculta): '
IFS= read -r -s pin_confirmation
printf '\n'
if [[ "$pin" != "$pin_confirmation" ]]; then
  printf 'Los PIN no coinciden; no se envió la solicitud.\n' >&2
  exit 1
fi

temporary_directory="$(mktemp -d)"
cleanup() {
  rm -rf "$temporary_directory"
  unset access_token pin pin_confirmation
}
trap cleanup EXIT

cat > "$temporary_directory/request.conf" <<EOF
url = "https://graph.facebook.com/${graph_api_version}/${phone_number_id}/register"
header = "Authorization: Bearer ${access_token}"
header = "Content-Type: application/json"
EOF

jq -n --arg pin "$pin" \
  '{messaging_product: "whatsapp", pin: $pin}' \
  > "$temporary_directory/body.json"

curl --config "$temporary_directory/request.conf" \
  --data-binary "@$temporary_directory/body.json" \
  --fail-with-body \
  --silent \
  --show-error
