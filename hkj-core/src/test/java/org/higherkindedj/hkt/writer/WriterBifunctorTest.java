// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.writer.WriterKindHelper.WRITER;

import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Comprehensive test suite for {@link WriterBifunctor}. */
@DisplayName("WriterBifunctor Complete Test Suite")
class WriterBifunctorTest {

  private WriterBifunctor bifunctor;

  @BeforeEach
  void setUp() {
    bifunctor = WriterBifunctor.INSTANCE;
  }

  @Nested
  @DisplayName("Complete Type Class Test Suite")
  class CompleteTypeClassTestSuite {

    @Test
    @DisplayName("Run complete Bifunctor test pattern")
    void runCompleteBifunctorTestPattern() {
      Kind2<WriterKind2.Witness, String, Integer> validWriter =
          WRITER.widen2(new Writer<>("log", 42));
      Function<String, Integer> firstMapper = String::length;
      Function<Integer, String> secondMapper = n -> "Value:" + n;
      Function<Integer, String> compositionFirstMapper = i -> "#" + i;
      Function<String, String> compositionSecondMapper = s -> s + "!";
      BiPredicate<Kind2<WriterKind2.Witness, ?, ?>, Kind2<WriterKind2.Witness, ?, ?>>
          equalityChecker = (k1, k2) -> WRITER.narrow2(k1).equals(WRITER.narrow2(k2));

      TypeClassTest.<WriterKind2.Witness>bifunctor(WriterBifunctor.class)
          .<String, Integer>instance(bifunctor)
          .withKind2(validWriter)
          .withFirstMapper(firstMapper)
          .withSecondMapper(secondMapper)
          .withCompositionFirstMapper(compositionFirstMapper)
          .withCompositionSecondMapper(compositionSecondMapper)
          .withEqualityChecker(equalityChecker)
          .testAll();
    }
  }

  @Nested
  @DisplayName("Bifunctor Operation Tests")
  class BifunctorOperationTests {

    @Test
    @DisplayName("bimap() transforms both log and value")
    void bimapTransformsBothChannels() {
      Kind2<WriterKind2.Witness, String, Integer> writer =
          WRITER.widen2(new Writer<>("log entry", 42));

      Function<String, Integer> logMapper = String::length;
      Function<Integer, String> valueMapper = n -> "Value:" + n;

      Writer<Integer, String> transformed =
          WRITER.narrow2(bifunctor.bimap(logMapper, valueMapper, writer));

      assertThat(transformed).isEqualTo(new Writer<>(9, "Value:42"));
    }

    @Test
    @DisplayName("first() transforms only log")
    void firstTransformsOnlyLog() {
      Kind2<WriterKind2.Witness, String, Integer> writer =
          WRITER.widen2(new Writer<>("log entry", 42));

      Function<String, Integer> logMapper = String::length;

      Writer<Integer, Integer> transformed = WRITER.narrow2(bifunctor.first(logMapper, writer));

      assertThat(transformed).isEqualTo(new Writer<>(9, 42));
    }

    @Test
    @DisplayName("second() transforms only value")
    void secondTransformsOnlyValue() {
      Kind2<WriterKind2.Witness, String, Integer> writer =
          WRITER.widen2(new Writer<>("log entry", 42));

      Function<Integer, String> valueMapper = n -> "Value:" + n;

      Writer<String, String> transformed = WRITER.narrow2(bifunctor.second(valueMapper, writer));

      assertThat(transformed).isEqualTo(new Writer<>("log entry", "Value:42"));
    }
  }

  @Nested
  @DisplayName("Bifunctor Law Tests")
  class BifunctorLawTests {

    private final BiPredicate<Kind2<WriterKind2.Witness, ?, ?>, Kind2<WriterKind2.Witness, ?, ?>>
        equalityChecker = (k1, k2) -> WRITER.narrow2(k1).equals(WRITER.narrow2(k2));

    @Test
    @DisplayName("Identity Law: bimap(id, id, fab) == fab")
    void identityLaw() {
      Kind2<WriterKind2.Witness, String, Integer> writer =
          WRITER.widen2(new Writer<>("log entry", 42));

      Kind2<WriterKind2.Witness, String, Integer> result =
          bifunctor.bimap(Function.identity(), Function.identity(), writer);

      assertThat(equalityChecker.test(result, writer)).as("Identity law should hold").isTrue();
    }

    @Test
    @DisplayName("Composition Law")
    void compositionLaw() {
      Kind2<WriterKind2.Witness, String, Integer> writer =
          WRITER.widen2(new Writer<>("log entry", 42));

      Function<String, Integer> f1 = String::length;
      Function<Integer, String> f2 = i -> "#" + i;
      Function<Integer, String> g1 = n -> "Value:" + n;
      Function<String, String> g2 = s -> s + "!";

      // Left side
      Kind2<WriterKind2.Witness, String, String> leftSide =
          bifunctor.bimap(s -> f2.apply(f1.apply(s)), i -> g2.apply(g1.apply(i)), writer);

      // Right side
      Kind2<WriterKind2.Witness, Integer, String> intermediate = bifunctor.bimap(f1, g1, writer);
      Kind2<WriterKind2.Witness, String, String> rightSide = bifunctor.bimap(f2, g2, intermediate);

      assertThat(equalityChecker.test(leftSide, rightSide))
          .as("Composition law should hold")
          .isTrue();
    }
  }
}
