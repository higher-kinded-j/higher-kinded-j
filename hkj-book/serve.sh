#!/usr/bin/env bash
#
# Serve the book locally with the CI-pinned toolchain.
#
# All four book binaries (mdbook, mdbook-admonish, mdbook-alerts,
# mdbook-mermaid) are kept under hkj-book/.tools (gitignored) at exactly the
# versions CI pins (see toolchain-pins.sh for the versions and the why), so
# different global versions for other projects are untouched. The first run
# installs them via cargo; after that startup is instant.
#
# Usage:
#   ./serve.sh [PORT]     # defaults to 3000
set -euo pipefail
cd "$(dirname "$0")"

# shellcheck source=toolchain-pins.sh
source ./toolchain-pins.sh

PORT="${1:-3000}"
TOOLS="$PWD/.tools"

ensure_tool() { # crate version
  if ! tool_matches "$TOOLS/bin/$1" "$2"; then
    echo "Installing $1 $2 into $TOOLS (one-off)..."
    cargo install "$1" --version "$2" --locked --root "$TOOLS"
  fi
}
ensure_tool mdbook "$MDBOOK_VERSION"
ensure_tool mdbook-admonish "$MDBOOK_ADMONISH_VERSION"
ensure_tool mdbook-alerts "$MDBOOK_ALERTS_VERSION"
ensure_tool mdbook-mermaid "$MDBOOK_MERMAID_VERSION"

export PATH="$TOOLS/bin:$PATH"

# Refresh mdbook-admonish assets with the pinned version (safe to re-run;
# keeps book.toml's assets_version at the value CI expects). The book cannot
# render without these assets, so a failure here is fatal.
if ! admonish_output=$(mdbook-admonish install . 2>&1); then
  echo "$admonish_output" >&2
  echo "error: 'mdbook-admonish install .' failed; not serving a book with broken assets." >&2
  exit 1
fi

# Pinned browser-side mermaid; fall back to the preprocessor's bundled copy
# when offline (see preview-changes.sh for the same dance). If neither route
# produces mermaid.min.js, serving would 404 a script every page loads.
if ! ../.github/scripts/fetch_mermaid.sh >/dev/null 2>&1; then
  echo "warning: mermaid fetch failed; using mdbook-mermaid's bundled copy." >&2
  mdbook-mermaid install . >/dev/null 2>&1 || true
fi
if [[ ! -f mermaid.min.js ]]; then
  echo "error: no mermaid.min.js could be obtained (fetch and bundled fallback both failed)." >&2
  exit 1
fi

exec mdbook serve --open --port "$PORT"
