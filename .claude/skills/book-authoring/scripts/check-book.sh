#!/usr/bin/env bash
# Runs the book checks that CI's "Book Checks" workflow runs, in the same order, from anywhere in
# the repository. The Gradle gate (./gradlew :hkj-examples:bookVerify) is separate.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

node .github/scripts/book-heading-html-check.cjs
node .github/scripts/book-anchor-check.cjs

.github/scripts/fetch_mermaid.sh
if [ ! -d .github/scripts/mermaid-check/node_modules ]; then
  (cd .github/scripts/mermaid-check && npm ci --no-audit --no-fund)
fi
node .github/scripts/mermaid-check/check.cjs

node .github/scripts/book-readability-check.cjs
