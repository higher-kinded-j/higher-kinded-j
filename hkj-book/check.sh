#!/usr/bin/env bash
# Runs the book checks of CI's "Book Checks" workflow, in the same order, from anywhere. Unlike CI,
# which stops at the first failing check, every check runs here; as in CI, the readability report
# always runs. The Gradle gate (./gradlew :hkj-examples:test :hkj-examples:bookVerify) is separate.
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 1

status=0
node .github/scripts/book-heading-html-check.cjs || status=1
node .github/scripts/book-anchor-check.cjs || status=1

mermaid_check=.github/scripts/mermaid-check
if .github/scripts/fetch_mermaid.sh; then
  installed="$mermaid_check/node_modules/.package-lock.json"
  if [ ! -f "$installed" ] || [ "$mermaid_check/package-lock.json" -nt "$installed" ]; then
    (cd "$mermaid_check" && npm ci --no-audit --no-fund) || status=1
  fi
  if [ -f "$installed" ]; then
    node "$mermaid_check/check.cjs" || status=1
  else
    status=1
  fi
else
  status=1
fi

node .github/scripts/book-readability-check.cjs || status=1
exit "$status"
