// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import static org.assertj.core.api.Assertions.assertThat;

import org.higherkindedj.hkt.Unit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@DisplayName("JsonResponses")
class JsonResponsesTest {

  @Test
  @DisplayName("a success has a body unless its status is bodiless or it holds nothing to send")
  void hasSuccessBody() {
    int ok = HttpStatus.OK.value();

    assertThat(JsonResponses.hasSuccessBody(ok, "value")).isTrue();
    assertThat(JsonResponses.hasSuccessBody(ok, Unit.INSTANCE)).isFalse();
    assertThat(JsonResponses.hasSuccessBody(ok, null)).isFalse();
    assertThat(JsonResponses.hasSuccessBody(HttpStatus.NO_CONTENT.value(), "value")).isFalse();
  }
}
