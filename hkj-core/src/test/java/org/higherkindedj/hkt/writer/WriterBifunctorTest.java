// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.writer.WriterKindHelper.WRITER;

import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.laws.BifunctorLaws;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Test suite for {@link WriterBifunctor}. */
@DisplayName("WriterBifunctor")
class WriterBifunctorTest {

  private WriterBifunctor bifunctor;

  private final BiPredicate<Kind2<WriterKind2.Witness, ?, ?>, Kind2<WriterKind2.Witness, ?, ?>>
      equalityChecker = (k1, k2) -> WRITER.narrow2(k1).equals(WRITER.narrow2(k2));

  @BeforeEach
  void setUp() {
    bifunctor = WriterBifunctor.INSTANCE;
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("kind2Fixtures")
    void identity(String label, Kind2<WriterKind2.Witness, String, Integer> fab) {
      BifunctorLaws.assertIdentity(bifunctor, fab, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("kind2Fixtures")
    void composition(String label, Kind2<WriterKind2.Witness, String, Integer> fab) {
      Function<String, Integer> f1 = String::length;
      Function<Integer, String> f2 = i -> "#" + i;
      Function<Integer, String> g1 = n -> "Value:" + n;
      Function<String, String> g2 = s -> s + "!";
      BifunctorLaws.assertComposition(bifunctor, fab, f1, f2, g1, g2, equalityChecker);
    }

    @ParameterizedTest(name = "first-consistency holds on {0}")
    @MethodSource("kind2Fixtures")
    void firstConsistency(String label, Kind2<WriterKind2.Witness, String, Integer> fab) {
      Function<String, String> f = s -> s + "!";
      BifunctorLaws.assertFirstConsistency(bifunctor, fab, f, equalityChecker);
    }

    @ParameterizedTest(name = "second-consistency holds on {0}")
    @MethodSource("kind2Fixtures")
    void secondConsistency(String label, Kind2<WriterKind2.Witness, String, Integer> fab) {
      Function<Integer, String> g = n -> "Value:" + n;
      BifunctorLaws.assertSecondConsistency(bifunctor, fab, g, equalityChecker);
    }

    /** Writer is a product, so both channels (log and value) are always exercised. */
    static Stream<Arguments> kind2Fixtures() {
      return Stream.of(
          Arguments.of("Writer(\"log\", 42)", WRITER.widen2(new Writer<>("log", 42))),
          Arguments.of("Writer(\"\", 0)", WRITER.widen2(new Writer<>("", 0))));
    }
  }

  @Test
  @DisplayName("Bifunctor contract — operations, validations & exceptions (laws verified above)")
  void bifunctorContract() {
    Kind2<WriterKind2.Witness, String, Integer> writer = WRITER.widen2(new Writer<>("log", 42));

    TypeClassContract.<WriterKind2.Witness>bifunctor(WriterBifunctor.class)
        .<String, Integer>instance(bifunctor)
        .withKind2(writer)
        .withFirstMapper(String::length)
        .withSecondMapper(n -> "Value:" + n)
        .withFirstExceptionKind(writer)
        .withSecondExceptionKind(writer)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS, Category.EXCEPTIONS);
  }

  @Nested
  @DisplayName("Operations")
  class Operations {

    private final Kind2<WriterKind2.Witness, String, Integer> writer =
        WRITER.widen2(new Writer<>("log entry", 42));

    @Test
    @DisplayName("bimap() transforms both the log and the value")
    void bimapTransformsBothChannels() {
      Writer<Integer, String> transformed =
          WRITER.narrow2(bifunctor.bimap(String::length, n -> "Value:" + n, writer));
      assertThat(transformed).isEqualTo(new Writer<>(9, "Value:42"));
    }

    @Test
    @DisplayName("first() transforms only the log")
    void firstTransformsOnlyLog() {
      Writer<Integer, Integer> transformed =
          WRITER.narrow2(bifunctor.first(String::length, writer));
      assertThat(transformed).isEqualTo(new Writer<>(9, 42));
    }

    @Test
    @DisplayName("second() transforms only the value")
    void secondTransformsOnlyValue() {
      Writer<String, String> transformed =
          WRITER.narrow2(bifunctor.second(n -> "Value:" + n, writer));
      assertThat(transformed).isEqualTo(new Writer<>("log entry", "Value:42"));
    }
  }
}
