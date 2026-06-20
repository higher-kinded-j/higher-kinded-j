// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client;

import org.jspecify.annotations.Nullable;

/**
 * Raised while materialising a {@code VStreamPath} backed by a server-sent-event stream when the
 * server emits an {@code event: error} frame, or when a frame exceeds the size bound — the
 * client-side mirror of the server's {@code VStreamPathReturnValueHandler} error event.
 *
 * <p>The {@code detail} originates from the (untrusted) remote stream. To avoid dumping an
 * arbitrary remote payload into logs that print {@link #getMessage()}, the message is truncated;
 * the full detail is available via {@link #detail()}.
 */
public class SseStreamException extends RuntimeException {

  private static final long serialVersionUID = 1L;
  private static final int MAX_MESSAGE_CHARS = 512;
  private static final String NO_DETAIL = "Unknown SSE stream error";

  private final @Nullable String detail;

  /**
   * Creates a new SSE stream exception.
   *
   * @param detail the error detail carried by the {@code event: error} frame (or a size-bound
   *     message); may be {@code null}, since it originates from an untrusted remote stream
   */
  public SseStreamException(@Nullable String detail) {
    super(truncate(detail));
    this.detail = detail;
  }

  /**
   * The full, untruncated detail from the error frame.
   *
   * @return the detail
   */
  public @Nullable String detail() {
    return detail;
  }

  private static String truncate(@Nullable String detail) {
    if (detail == null) {
      return NO_DETAIL;
    }
    return detail.length() <= MAX_MESSAGE_CHARS
        ? detail
        : detail.substring(0, MAX_MESSAGE_CHARS) + "… (truncated; see detail())";
  }
}
