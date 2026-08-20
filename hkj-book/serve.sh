#!/usr/bin/env bash
#
# Serve the book locally with the CI-pinned toolchain.
#
# This book builds with mdbook 0.4.51 and mdbook-mermaid 0.14.1 (the versions
# CI pins); a globally installed mdbook 0.5.x cannot build it. Both binaries
# are kept under hkj-book/.tools (gitignored), so a different global mdbook
# for other projects is untouched. The first run installs them via cargo;
# after that startup is instant.
#
# Usage:
#   ./serve.sh [PORT]     # defaults to 3000
#
# mdbook-admonish and mdbook-alerts are taken from your PATH: their installed
# versions speak the 0.4 preprocessor protocol and work here as-is.
set -euo pipefail
cd "$(dirname "$0")"

PORT="${1:-3000}"
TOOLS="$PWD/.tools"
MDBOOK_VERSION="0.4.51"
MDBOOK_MERMAID_VERSION="0.14.1"

ensure_tool() { # crate version
  local bin="$TOOLS/bin/$1"
  if [[ ! -x "$bin" ]] || ! "$bin" --version 2>/dev/null | grep -qF "$2"; then
    echo "Installing $1 $2 into $TOOLS (one-off)..."
    cargo install "$1" --version "$2" --locked --root "$TOOLS"
  fi
}
ensure_tool mdbook "$MDBOOK_VERSION"
ensure_tool mdbook-mermaid "$MDBOOK_MERMAID_VERSION"

export PATH="$TOOLS/bin:$PATH"

# Refresh mdbook-admonish assets if the binary is available (safe to re-run).
if command -v mdbook-admonish >/dev/null 2>&1; then
  mdbook-admonish install . >/dev/null 2>&1 || true
fi

# Pinned browser-side mermaid; fall back to the preprocessor's bundled copy
# when offline (see preview-changes.sh for the same dance).
if ! ../.github/scripts/fetch_mermaid.sh >/dev/null 2>&1; then
  echo "warning: mermaid fetch failed; using mdbook-mermaid's bundled copy." >&2
  mdbook-mermaid install . >/dev/null 2>&1 || true
fi

exec mdbook serve --open --port "$PORT"
