// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ErrorStatusCodeMapper")
class ErrorStatusCodeMapperTest {

  // Test errors -- chosen to exercise every branch of tokenize() and the heuristic table.
  record UserNotFoundError(String id) {}

  record ValidationError(String msg) {}

  record MfaCodeInvalidError(String msg) {}

  record AuthorizationError(String msg) {}

  record ForbiddenAction(String msg) {}

  record AuthenticationError(String msg) {}

  record UnauthorizedAccess(String msg) {}

  record DuplicateError(String msg) {}

  /** Single-token name to exercise the no-uppercase-boundary branch of tokenize. */
  record nofound(String msg) {}

  /** Whole-name uppercase to exercise the "no boundary inserted" branch (HTTP, FOO). */
  record FOOError(String msg) {}

  /**
   * A token like "revalidation" must NOT match the "validation" rule under the new tokenised
   * heuristic — this is the regression check for §2.
   */
  record RevalidationError(String msg) {}

  @Nested
  @DisplayName("tokenize")
  class TokenizeTests {

    @Test
    @DisplayName("CamelCase splits at upper-case boundaries")
    void splitsCamelCase() {
      assertThat(ErrorStatusCodeMapper.tokenize("MfaNotFoundError"))
          .isEqualTo("-mfa-not-found-error-");
    }

    @Test
    @DisplayName("Single lowercase token gets bracketed")
    void singleLowercase() {
      assertThat(ErrorStatusCodeMapper.tokenize("nofound")).isEqualTo("-nofound-");
    }

    @Test
    @DisplayName("Whole-uppercase name does not insert spurious boundaries")
    void wholeUppercase() {
      assertThat(ErrorStatusCodeMapper.tokenize("FOOError")).isEqualTo("-fooerror-");
    }

    @Test
    @DisplayName("null input returns lone separator")
    void nullInput() {
      assertThat(ErrorStatusCodeMapper.tokenize(null)).isEqualTo("-");
    }

    @Test
    @DisplayName("Empty input returns lone separator")
    void emptyInput() {
      assertThat(ErrorStatusCodeMapper.tokenize("")).isEqualTo("-");
    }
  }

  @Nested
  @DisplayName("heuristicStatus")
  class HeuristicTests {

    @Test
    @DisplayName("Adjacent Not + Found tokens map to 404")
    void notFound() {
      assertThat(ErrorStatusCodeMapper.heuristicStatus("UserNotFoundError", 400)).isEqualTo(404);
    }

    @Test
    @DisplayName("Validation token maps to 400")
    void validation() {
      assertThat(ErrorStatusCodeMapper.heuristicStatus("ValidationError", 500)).isEqualTo(400);
    }

    @Test
    @DisplayName("Invalid token maps to 400")
    void invalid() {
      assertThat(ErrorStatusCodeMapper.heuristicStatus("MfaCodeInvalidError", 500)).isEqualTo(400);
    }

    @Test
    @DisplayName("Authorization token maps to 403")
    void authorization() {
      assertThat(ErrorStatusCodeMapper.heuristicStatus("AuthorizationError", 500)).isEqualTo(403);
    }

    @Test
    @DisplayName("Forbidden token maps to 403")
    void forbidden() {
      assertThat(ErrorStatusCodeMapper.heuristicStatus("ForbiddenAction", 500)).isEqualTo(403);
    }

    @Test
    @DisplayName("Authentication token maps to 401")
    void authentication() {
      assertThat(ErrorStatusCodeMapper.heuristicStatus("AuthenticationError", 500)).isEqualTo(401);
    }

    @Test
    @DisplayName("Unauthorized token maps to 401")
    void unauthorized() {
      assertThat(ErrorStatusCodeMapper.heuristicStatus("UnauthorizedAccess", 500)).isEqualTo(401);
    }

    @Test
    @DisplayName("Unmatched name returns the supplied default")
    void unmatched() {
      assertThat(ErrorStatusCodeMapper.heuristicStatus("DuplicateError", 418)).isEqualTo(418);
    }

    @Test
    @DisplayName("Revalidation does NOT match validation under tokenised matching")
    void revalidationDoesNotMatchValidation() {
      assertThat(ErrorStatusCodeMapper.heuristicStatus("RevalidationError", 418)).isEqualTo(418);
    }
  }

  @Nested
  @DisplayName("determineStatusCode (single-arg overload)")
  class SingleArgTests {

    @Test
    @DisplayName("Defers to heuristics when no map is supplied")
    void defersToHeuristics() {
      assertThat(ErrorStatusCodeMapper.determineStatusCode(new UserNotFoundError("x"), 500))
          .isEqualTo(404);
    }

    @Test
    @DisplayName("Falls back to defaultStatus when no heuristic matches")
    void fallsBack() {
      assertThat(ErrorStatusCodeMapper.determineStatusCode(new DuplicateError("x"), 418))
          .isEqualTo(418);
    }
  }

  @Nested
  @DisplayName("determineStatusCode (mappings overload)")
  class MapTests {

    @Test
    @DisplayName("Simple-name mapping wins over heuristic")
    void simpleNameMappingWins() {
      Map<String, Integer> map = Map.of("UserNotFoundError", 410);
      assertThat(ErrorStatusCodeMapper.determineStatusCode(new UserNotFoundError("x"), 500, map))
          .isEqualTo(410);
    }

    @Test
    @DisplayName("Fully-qualified-name mapping wins when simple name is absent")
    void fqnMappingWins() {
      Map<String, Integer> map = Map.of(UserNotFoundError.class.getName(), 451);
      assertThat(ErrorStatusCodeMapper.determineStatusCode(new UserNotFoundError("x"), 500, map))
          .isEqualTo(451);
    }

    @Test
    @DisplayName("Simple-name mapping is preferred over fully-qualified-name mapping")
    void simpleNamePreferredOverFqn() {
      Map<String, Integer> map = new HashMap<>();
      map.put("UserNotFoundError", 410);
      map.put(UserNotFoundError.class.getName(), 451);
      assertThat(ErrorStatusCodeMapper.determineStatusCode(new UserNotFoundError("x"), 500, map))
          .isEqualTo(410);
    }

    @Test
    @DisplayName("Empty map falls through to heuristics")
    void emptyMapFallsThrough() {
      assertThat(ErrorStatusCodeMapper.determineStatusCode(new ValidationError("x"), 500, Map.of()))
          .isEqualTo(400);
    }

    @Test
    @DisplayName("Null map is treated as empty")
    void nullMapTreatedAsEmpty() {
      assertThat(ErrorStatusCodeMapper.determineStatusCode(new ValidationError("x"), 500, null))
          .isEqualTo(400);
    }

    @Test
    @DisplayName("Map miss falls through to heuristics")
    void mapMissFallsThrough() {
      Map<String, Integer> map = Map.of("Other", 999);
      assertThat(ErrorStatusCodeMapper.determineStatusCode(new ValidationError("x"), 500, map))
          .isEqualTo(400);
    }

    @Test
    @DisplayName("Unmatched error with non-empty map still falls back to defaultStatus")
    void unmatchedFallsBack() {
      Map<String, Integer> map = Map.of("Other", 999);
      assertThat(ErrorStatusCodeMapper.determineStatusCode(new DuplicateError("x"), 418, map))
          .isEqualTo(418);
    }
  }

  @Nested
  @DisplayName("Constructor visibility")
  class ConstructorTests {

    @Test
    @DisplayName("Reflective construction throws to prevent instantiation of utility class")
    void cannotInstantiate() throws NoSuchMethodException {
      Constructor<ErrorStatusCodeMapper> ctor =
          ErrorStatusCodeMapper.class.getDeclaredConstructor();
      ctor.setAccessible(true);
      assertThatThrownBy(ctor::newInstance)
          .isInstanceOf(InvocationTargetException.class)
          .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
  }
}
