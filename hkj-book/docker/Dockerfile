# Book toolchain image: the exact mdbook, preprocessors, Node and Python that CI
# uses, so a contributor needs only Docker. Versions come from toolchain-pins.sh,
# the single source of truth, so there is nothing to keep in step here.
#
# Build context is hkj-book/ (see Dockerfile.dockerignore: only the pins file is
# sent). Normally built through hkj-book/docker.sh, or by the dev container.

FROM rust:1-bookworm AS tools
SHELL ["/bin/bash", "-euo", "pipefail", "-c"]
COPY toolchain-pins.sh /tmp/toolchain-pins.sh
RUN source /tmp/toolchain-pins.sh \
 && cargo install mdbook --version "$MDBOOK_VERSION" --locked --root /opt/hkj-book-tools \
 && cargo install mdbook-admonish --version "$MDBOOK_ADMONISH_VERSION" --locked --root /opt/hkj-book-tools \
 && cargo install mdbook-alerts --version "$MDBOOK_ALERTS_VERSION" --locked --root /opt/hkj-book-tools \
 && cargo install mdbook-mermaid --version "$MDBOOK_MERMAID_VERSION" --locked --root /opt/hkj-book-tools

# Node 22 matches the book workflows; the Debian release matches the builder's
# glibc so the compiled binaries run unchanged.
FROM node:22-bookworm-slim
LABEL org.opencontainers.image.source="https://github.com/higher-kinded-j/higher-kinded-j" \
      org.opencontainers.image.description="Pinned mdbook toolchain for building and checking the Higher-Kinded-J book"
RUN apt-get update \
 && apt-get install -y --no-install-recommends git curl ca-certificates python3 \
 && rm -rf /var/lib/apt/lists/*
COPY --from=tools /opt/hkj-book-tools /opt/hkj-book-tools
# serve.sh and preview-changes.sh read the pinned binaries from here.
ENV HKJ_BOOK_TOOLS=/opt/hkj-book-tools \
    PATH=/opt/hkj-book-tools/bin:$PATH \
    PYTHONDONTWRITEBYTECODE=1
WORKDIR /work/hkj-book
