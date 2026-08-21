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

# tool_matches BIN VERSION: BIN exists, is executable, and its --version
# reports exactly VERSION as its version token (an optional leading 'v' is
# tolerated; a substring such as 0.4.510 against a 0.4.51 pin is not).
tool_matches() {
  [[ -x "$1" ]] || return 1
  local reported
  reported=$("$1" --version 2>/dev/null | grep -oE 'v?[0-9]+(\.[0-9]+)+' | head -n1) || return 1
  [[ "${reported#v}" == "$2" ]]
}

# tools_complete DIR: all four pinned binaries present in DIR at the pins.
tools_complete() {
  tool_matches "$1/mdbook" "$MDBOOK_VERSION" \
    && tool_matches "$1/mdbook-admonish" "$MDBOOK_ADMONISH_VERSION" \
    && tool_matches "$1/mdbook-alerts" "$MDBOOK_ALERTS_VERSION" \
    && tool_matches "$1/mdbook-mermaid" "$MDBOOK_MERMAID_VERSION"
}
