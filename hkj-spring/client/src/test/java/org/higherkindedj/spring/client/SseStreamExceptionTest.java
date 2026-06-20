// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SseStreamException")
class SseStreamExceptionTest {

  @Test
  @DisplayName("a short detail is the message verbatim and is kept in detail()")
  void shortDetail() {
    SseStreamException ex = new SseStreamException("boom");

    assertThat(ex.getMessage()).isEqualTo("boom");
    assertThat(ex.detail()).isEqualTo("boom");
  }

  @Test
  @DisplayName("a long detail is truncated in the message but kept whole in detail()")
  void longDetailTruncated() {
    String detail = "x".repeat(1000);

    SseStreamException ex = new SseStreamException(detail);

    assertThat(ex.getMessage())
        .hasSizeLessThan(detail.length())
        .endsWith("(truncated; see detail())");
    assertThat(ex.detail()).isEqualTo(detail);
  }

  @Test
  @DisplayName("a null detail does not NPE: the message is a default and detail() is null")
  void nullDetail() {
    SseStreamException ex = new SseStreamException(null);

    assertThat(ex.getMessage()).isEqualTo("Unknown SSE stream error");
    assertThat(ex.detail()).isNull();
  }
}
