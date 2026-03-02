// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

/**
 * Package-private marker attached as a suppressed exception by {@link VStream#mapTask} to carry the
 * remaining stream tail through a per-element VTask failure. This allows {@link VStream#recover} to
 * continue processing subsequent elements after recovering from a single element's error.
 *
 * <p>By using a suppressed exception rather than wrapping, the original exception type is preserved
 * for callers that do not use {@code recover()}.
 */
final class StreamTailMarker extends RuntimeException {

  private final VStream<?> remainingTail;

  StreamTailMarker(VStream<?> remainingTail) {
    super(null, null, /* enableSuppression= */ true, /* writableStackTrace= */ false);
    this.remainingTail = remainingTail;
  }

  VStream<?> remainingTail() {
    return remainingTail;
  }
}
