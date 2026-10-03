// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.effect.pathsource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.effect.pathsource.OutcomeKindHelper.OUTCOME;
import static org.higherkindedj.example.book.effect.pathsource.TracedKindHelper.TRACED;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.capability.Combinable;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What the book's Custom Paths with {@code @PathSource} page says of the Paths generated for {@link
 * Traced} and {@link Outcome}, beyond the values its included regions print: {@code
 * BookExampleOutputTest} holds {@link PathSourceBook}'s output comments to what {@code main}
 * prints, so they are not asserted again here.
 */
@DisplayName("the Custom Paths page's generated Paths")
class PathSourceBookTest {

  private static TracedPath<Integer> priced() {
    return TracedPath.of(Traced.of(1200, "priced the basket"), TracedMonad.INSTANCE);
  }

  @Nested
  @DisplayName("TracedPath, at the default capability")
  class TracedPathTest {

    @Test
    @DisplayName("zipWith takes any Combinable, and refuses one of another class at the call")
    void zipWithRefusesAnotherPathClass() {
      assertThatThrownBy(() -> priced().zipWith(Path.just(350), Integer::sum))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageStartingWith("Cannot zipWith non-TracedPath");
    }

    @Test
    @DisplayName("pure starts from the Monad's of, with an empty log")
    void pureStartsWithAnEmptyLog() {
      Traced<Integer> result = TRACED.narrow(TracedPath.pure(1200, TracedMonad.INSTANCE).run());

      assertThat(result).isEqualTo(new Traced<>(1200, List.of()));
    }

    @Test
    @DisplayName("peek runs at the call on an eager effect, and keeps the value")
    void peekRunsAtTheCallOnAnEagerEffect() {
      List<Integer> seen = new ArrayList<>();

      TracedPath<Integer> peeked = priced().peek(seen::add);

      assertThat(seen).containsExactly(1200);
      assertThat(TRACED.narrow(peeked.run())).isEqualTo(Traced.of(1200, "priced the basket"));
    }

    @Test
    @DisplayName("then runs the next step and keeps both logs")
    void thenKeepsBothLogs() {
      Traced<String> result =
          TRACED.narrow(
              priced()
                  .then(
                      () -> TracedPath.of(Traced.of("paid", "took payment"), TracedMonad.INSTANCE))
                  .run());

      assertThat(result)
          .isEqualTo(new Traced<>("paid", List.of("priced the basket", "took payment")));
    }

    @Test
    @DisplayName("run and runKind return the same Kind")
    void runAndRunKindAgree() {
      TracedPath<Integer> path = priced();

      assertThat(path.runKind()).isSameAs(path.run());
    }

    @Test
    @DisplayName("equals and hashCode follow the wrapped Kind")
    void equalsFollowsTheKind() {
      assertThat(priced()).hasSameHashCodeAs(priced());
      assertThat(priced())
          .isNotEqualTo(TracedPath.of(Traced.of(1200, "repriced"), TracedMonad.INSTANCE));
    }

    @Test
    @DisplayName("Path.generic takes the Path's Kind back into a GenericPath")
    void backIntoGenericPath() {
      TracedPath<Integer> priced = priced();

      assertThat(PathSourceBook.toGeneric(priced).runKind()).isSameAs(priced.run());
    }
  }

  @Nested
  @DisplayName("OutcomePath, at RECOVERABLE")
  class OutcomePathTest {

    private static OutcomePath<String> reservation() {
      return OutcomePath.of(
          PathSourceBook.reserve("SKU-42"), OutcomeMonad.INSTANCE, OutcomeMonad.INSTANCE);
    }

    @Test
    @DisplayName("recoverWith continues with the Path the function returns")
    void recoverWithContinuesWithAPath() {
      Outcome<String> result =
          OUTCOME.narrow(
              reservation()
                  .recoverWith(
                      problem ->
                          OutcomePath.of(
                              PathSourceBook.reserve("SKU-7"),
                              OutcomeMonad.INSTANCE,
                              OutcomeMonad.INSTANCE))
                  .run());

      assertThat(result).isEqualTo(new Outcome.Ok<>("reserved SKU-7"));
    }

    @Test
    @DisplayName("a failed step stops via, and recover leaves a success alone")
    void failureStopsViaAndRecoverLeavesSuccess() {
      Outcome<String> stopped =
          OUTCOME.narrow(
              reservation()
                  .via(
                      reserved ->
                          OutcomePath.pure("paid", OutcomeMonad.INSTANCE, OutcomeMonad.INSTANCE))
                  .run());
      Outcome<String> kept =
          OUTCOME.narrow(
              OutcomePath.pure("reserved SKU-7", OutcomeMonad.INSTANCE, OutcomeMonad.INSTANCE)
                  .recover(problem -> "unused")
                  .run());

      assertThat(stopped).isEqualTo(new Outcome.Failed<>(new Problem("SKU-42 is out of stock")));
      assertThat(kept).isEqualTo(new Outcome.Ok<>("reserved SKU-7"));
    }
  }

  @Nested
  @DisplayName("the generated classes' shape")
  class Shape {

    @Test
    @DisplayName("a generated Path is Combinable, and not Chainable")
    void combinableNotChainable() {
      assertThat(Combinable.class).isAssignableFrom(TracedPath.class);
      assertThat(Combinable.class).isAssignableFrom(OutcomePath.class);
      assertThat(Chainable.class.isAssignableFrom(TracedPath.class)).isFalse();
      assertThat(Chainable.class.isAssignableFrom(OutcomePath.class)).isFalse();
    }

    @Test
    @DisplayName("recovery methods are generated only at RECOVERABLE")
    void recoveryOnlyAtRecoverable() {
      assertThat(methodNames(TracedPath.class))
          .contains("map", "peek", "zipWith", "via", "then", "flatMap")
          .doesNotContain("recover", "recoverWith", "mapError");
      assertThat(methodNames(OutcomePath.class)).contains("recover", "recoverWith", "mapError");
    }

    private static List<String> methodNames(Class<?> type) {
      return Arrays.stream(type.getDeclaredMethods()).map(Method::getName).toList();
    }
  }

  @Nested
  @DisplayName("the kind helpers")
  class Helpers {

    @Test
    @DisplayName("narrow refuses a null Kind, as the library's own helpers do")
    void narrowRefusesNull() {
      assertThatThrownBy(() -> TRACED.narrow(null)).isInstanceOf(KindUnwrapException.class);
      assertThatThrownBy(() -> OUTCOME.narrow(null)).isInstanceOf(KindUnwrapException.class);
    }

    @Test
    @DisplayName("widen returns the value itself")
    void widenReturnsTheValue() {
      Traced<Integer> traced = Traced.of(1200, "priced the basket");
      Outcome<String> outcome = PathSourceBook.reserve("SKU-7");

      assertThat(TRACED.widen(traced)).isSameAs(traced);
      assertThat(OUTCOME.widen(outcome)).isSameAs(outcome);
    }
  }

  @Test
  @DisplayName("GenericPath's recover takes the error type its caller names, unchecked")
  void genericRecoverTakesTheNamedErrorType() {
    assertThatThrownBy(PathSourceBook::genericRecover)
        .isInstanceOf(ClassCastException.class)
        .hasMessageContaining(Problem.class.getName())
        .hasMessageContaining(String.class.getName());
  }
}
