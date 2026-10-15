#!/usr/bin/env bash
#
# Run the book tooling in Docker, with the CI-pinned toolchain, so the only
# thing you install is Docker.
#
# Usage:
#   ./docker.sh serve [PORT]            # live preview at http://localhost:PORT (default 3000)
#   ./docker.sh preview [REF] [PORT]    # preview with changes marked against a tag or commit
#   ./docker.sh check                   # the book checks CI runs (same as ./check.sh)
#   ./docker.sh shell                   # a shell in the container, repository mounted at /work
#   ./docker.sh image                   # print the image reference for the current pins
#
# The first run fetches the toolchain image (a minute or so) or, if the image is
# not published, builds it locally (several minutes while Rust compiles the four
# tools). Later runs start at once. Files the container writes belong to you.
set -euo pipefail
cd "$(dirname "$0")"

# shellcheck source=toolchain-pins.sh
source ./toolchain-pins.sh

IMAGE_REPO="ghcr.io/higher-kinded-j/hkj-book-toolchain"

sha256() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum; else shasum -a 256; fi
}

# The tag changes exactly when the pins or the Dockerfile do.
IMAGE_TAG="$(cat toolchain-pins.sh docker/Dockerfile | sha256 | cut -c1-12)"
IMAGE="${IMAGE_REPO}:${IMAGE_TAG}"

if [[ "${1:-}" == "image" ]]; then
  echo "$IMAGE"
  exit 0
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "error: docker not found. Install Docker (https://docs.docker.com/get-docker/) or use ./serve.sh with a Rust toolchain." >&2
  exit 1
fi

ensure_image() {
  if docker image inspect "$IMAGE" >/dev/null 2>&1; then
    return
  fi
  echo "Fetching the book toolchain image ($IMAGE)..."
  if docker pull --quiet "$IMAGE" >/dev/null 2>&1; then
    return
  fi
  echo "Not published; building it locally (one-off, several minutes)..."
  docker build --tag "$IMAGE" --file docker/Dockerfile .
}

CMD="${1:-serve}"
[[ $# -gt 0 ]] && shift

REPO_ROOT="$(git rev-parse --show-toplevel)"
BOOK_DIR="/work/hkj-book"

run_args=(--rm --init --user "$(id -u):$(id -g)"
  --volume "${REPO_ROOT}:/work"
  --workdir "$BOOK_DIR"
  --env HOME=/tmp
  --env HKJ_BOOK_OPEN=0 --env HKJ_BOOK_HOST=0.0.0.0
  # The mount is owned by you, but git still wants it declared safe.
  --env GIT_CONFIG_COUNT=1 --env GIT_CONFIG_KEY_0=safe.directory --env GIT_CONFIG_VALUE_0=/work)
[[ -t 0 && -t 1 ]] && run_args+=(--tty --interactive)

# In a linked git worktree, .git is a file pointing at the main repository's
# git directory by absolute path; mount that path so git works inside.
if [[ -f "${REPO_ROOT}/.git" ]]; then
  common_dir="$(cd "$REPO_ROOT" && git rev-parse --path-format=absolute --git-common-dir)"
  run_args+=(--volume "${common_dir}:${common_dir}:ro")
fi

serve_port() { # PORT -> publishes it on loopback only, same port inside
  run_args+=(--publish "127.0.0.1:$1:$1")
  echo "Serving the book at http://localhost:$1 (Ctrl-C to stop)"
}

case "$CMD" in
  serve)
    PORT="${1:-3000}"
    ensure_image
    serve_port "$PORT"
    exec docker run "${run_args[@]}" "$IMAGE" ./serve.sh "$PORT"
    ;;
  preview)
    BASE="${1:-}"
    PORT="${2:-3000}"
    ensure_image
    serve_port "$PORT"
    exec docker run "${run_args[@]}" "$IMAGE" ./preview-changes.sh "$BASE" "$PORT"
    ;;
  check)
    ensure_image
    exec docker run "${run_args[@]}" "$IMAGE" ./check.sh
    ;;
  shell)
    ensure_image
    exec docker run "${run_args[@]}" "$IMAGE" bash
    ;;
  *)
    echo "usage: $0 {serve [PORT] | preview [REF] [PORT] | check | shell | image}" >&2
    exit 2
    ;;
esac
