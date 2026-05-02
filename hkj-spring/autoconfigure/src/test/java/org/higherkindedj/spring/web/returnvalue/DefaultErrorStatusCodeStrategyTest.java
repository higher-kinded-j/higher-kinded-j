// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DefaultErrorStatusCodeStrategy")
class DefaultErrorStatusCodeStrategyTest {

  record UserNotFoundError(String id) {}

  record DuplicateError(String id) {}

  @Test
  @DisplayName("null mappings argument is treated as empty")
  void nullMappingsTreatedAsEmpty() {
    DefaultErrorStatusCodeStrategy strategy = new DefaultErrorStatusCodeStrategy(null);
    assertThat(strategy.mappings()).isEmpty();
    assertThat(strategy.statusCodeFor(new DuplicateError("x"), 418)).isEqualTo(418);
  }

  @Test
  @DisplayName("Empty mappings falls through to heuristics")
  void emptyFallsThroughToHeuristics() {
    DefaultErrorStatusCodeStrategy strategy = new DefaultErrorStatusCodeStrategy(Map.of());
    assertThat(strategy.statusCodeFor(new UserNotFoundError("x"), 500)).isEqualTo(404);
  }

  @Test
  @DisplayName("Mapping overrides heuristic")
  void mappingOverridesHeuristic() {
    DefaultErrorStatusCodeStrategy strategy =
        new DefaultErrorStatusCodeStrategy(Map.of("UserNotFoundError", 410));
    assertThat(strategy.statusCodeFor(new UserNotFoundError("x"), 500)).isEqualTo(410);
  }

  @Test
  @DisplayName("Constructor takes a defensive copy")
  void defensiveCopy() {
    Map<String, Integer> mutable = new HashMap<>();
    mutable.put("UserNotFoundError", 410);
    DefaultErrorStatusCodeStrategy strategy = new DefaultErrorStatusCodeStrategy(mutable);

    mutable.put("UserNotFoundError", 999);
    mutable.put("AnotherError", 500);

    assertThat(strategy.statusCodeFor(new UserNotFoundError("x"), 500)).isEqualTo(410);
    assertThat(strategy.mappings()).containsOnlyKeys("UserNotFoundError");
  }

  @Test
  @DisplayName("mappings() view is immutable")
  void mappingsViewImmutable() {
    DefaultErrorStatusCodeStrategy strategy = new DefaultErrorStatusCodeStrategy(Map.of("X", 1));
    assertThatThrownBy(() -> strategy.mappings().put("Y", 2))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("Unmatched error returns supplied default")
  void unmatchedReturnsDefault() {
    DefaultErrorStatusCodeStrategy strategy = new DefaultErrorStatusCodeStrategy(Map.of("X", 1));
    assertThat(strategy.statusCodeFor(new DuplicateError("x"), 422)).isEqualTo(422);
  }
}
