#!/usr/bin/env bash
#
# Preview hkj-book change tracking locally against a baseline git ref.
#
# The change-tracking preprocessor marks content that is new/changed relative
# to a baseline (see .github/scripts/hkj_changes_preprocessor.py). This script
# wires that up for a local `mdbook serve` so maintainers can see the margin
# line + word underlines before anything is deployed.
#
# Usage:
#   ./preview-changes.sh [BASELINE_REF] [PORT]
#
#   BASELINE_REF   Tag or commit to diff the current working tree against.
#                  Defaults to the most recent tag (git describe).
#   PORT           Port for `mdbook serve`. Defaults to 3000.
#
# Examples:
#   ./preview-changes.sh                 # working tree vs latest tag
#   ./preview-changes.sh v0.4.7          # working tree vs v0.4.7
#   ./preview-changes.sh v0.4.6 3001     # ...on port 3001
#
# To preview what a *released* version added (tag vs previous tag):
#   git checkout v0.4.7
#   ./preview-changes.sh v0.4.6
#   git checkout -            # restore your branch afterwards
#
set -euo pipefail
cd "$(dirname "$0")"

BASE="${1:-}"
PORT="${2:-3000}"

if [[ -z "$BASE" ]]; then
  BASE="$(git describe --tags --abbrev=0 2>/dev/null || true)"
  if [[ -z "$BASE" ]]; then
    echo "error: no tags found; pass a baseline ref explicitly, e.g." >&2
    echo "       ./preview-changes.sh <tag-or-commit>" >&2
    exit 1
  fi
fi

if ! git rev-parse --verify --quiet "${BASE}^{commit}" >/dev/null; then
  echo "error: baseline ref '${BASE}' not found in this repository." >&2
  exit 1
fi

if ! command -v mdbook >/dev/null 2>&1; then
  echo "error: mdbook not found. Install with: cargo install mdbook --version 0.4.51 --locked" >&2
  exit 1
fi

# Refresh mdbook-admonish assets if the binary is available (safe to re-run).
if command -v mdbook-admonish >/dev/null 2>&1; then
  mdbook-admonish install . >/dev/null 2>&1 || true
fi

echo "Change-tracking preview:"
echo "  new:      current working tree"
echo "  baseline: ${BASE}"
echo "  serving:  http://localhost:${PORT}"
echo

export HKJ_CHANGES_BASE="${BASE}"
export HKJ_CHANGES_SINCE_LABEL="${BASE}"
exec mdbook serve --open --port "${PORT}"
