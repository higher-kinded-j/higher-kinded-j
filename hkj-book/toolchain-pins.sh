# Single source of truth for the CI-pinned book toolchain versions, sourced
# by serve.sh and preview-changes.sh. Keep in sync with the MDBOOK_* pins in
# .github/workflows/deploy-mdbook-versioned.yml (and its siblings).
#
# This book builds with mdbook 0.4.x: a global mdbook 0.5.x cannot parse the
# 0.4-protocol preprocessor output, and a newer global mdbook-admonish
# rewrites book.toml's assets_version, dirtying the working tree.

MDBOOK_VERSION="0.4.51"
MDBOOK_ADMONISH_VERSION="1.19.0"
MDBOOK_ALERTS_VERSION="0.7.0"
MDBOOK_MERMAID_VERSION="0.14.1"

# tool_matches BIN VERSION: BIN exists, is executable, and reports VERSION.
tool_matches() {
  [[ -x "$1" ]] && "$1" --version 2>/dev/null | grep -qF "$2"
}

# tools_complete DIR: all four pinned binaries present in DIR at the pins.
tools_complete() {
  tool_matches "$1/mdbook" "$MDBOOK_VERSION" \
    && tool_matches "$1/mdbook-admonish" "$MDBOOK_ADMONISH_VERSION" \
    && tool_matches "$1/mdbook-alerts" "$MDBOOK_ALERTS_VERSION" \
    && tool_matches "$1/mdbook-mermaid" "$MDBOOK_MERMAID_VERSION"
}
