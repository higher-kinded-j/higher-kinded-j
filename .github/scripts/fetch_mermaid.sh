#!/usr/bin/env bash
#
# Fetch the pinned browser-side mermaid library into hkj-book/.
#
# The mdbook-mermaid PREPROCESSOR (pinned in the workflows, 0.14.1 for mdbook
# 0.4 protocol compatibility) only rewrites ```mermaid fences into
# <pre class="mermaid"> blocks; rendering happens client-side in
# mermaid.min.js. Pinning the library here keeps the renderer current
# independently of the preprocessor's (older) bundled copy.
#
# To upgrade: bump MERMAID_VERSION, run
#   curl -fsSL "https://cdn.jsdelivr.net/npm/mermaid@<version>/dist/mermaid.min.js" | sha256sum
# and update MERMAID_SHA256. The dist/mermaid.min.js build is the IIFE bundle
# that assigns globalThis.mermaid, which hkj-book/mermaid-init.js requires
# (the .mjs ESM builds do not work here).
set -euo pipefail

MERMAID_VERSION="11.17.0"
MERMAID_SHA256="8d8e0eec56d3a83b4b3c87f42050845546dee93ebe1875d2117c12e6947c0cb3"

DEST_DIR="$(cd "$(dirname "$0")/../../hkj-book" && pwd)"
DEST="${DEST_DIR}/mermaid.min.js"
URL="https://cdn.jsdelivr.net/npm/mermaid@${MERMAID_VERSION}/dist/mermaid.min.js"

checksum_ok() {
  echo "${MERMAID_SHA256}  $1" | sha256sum -c --status -
}

if [[ -f "${DEST}" ]] && checksum_ok "${DEST}"; then
  echo "mermaid ${MERMAID_VERSION} already present: ${DEST}"
  exit 0
fi

TMP="$(mktemp "${DEST_DIR}/mermaid.min.js.XXXXXX")"
trap 'rm -f "${TMP}"' EXIT

echo "Fetching mermaid ${MERMAID_VERSION} from ${URL}"
curl -fsSL --retry 3 -o "${TMP}" "${URL}"

if ! checksum_ok "${TMP}"; then
  echo "error: checksum mismatch for mermaid ${MERMAID_VERSION}; refusing to install" >&2
  exit 1
fi

mv "${TMP}" "${DEST}"
trap - EXIT
echo "Installed ${DEST}"
