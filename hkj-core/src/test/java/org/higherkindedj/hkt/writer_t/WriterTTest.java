// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Pair;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("WriterT Core Type Tests")
class WriterTTest {

  private static final Monoid<String> STRING_MONOID =
      new Monoid<>() {
        @Override
        public String empty() {
          return "";
        }

        @Override
        public String combine(String a, String b) {
          return a + b;
        }
      };

  private Monad<OptionalKind.Witness> outerMonad;

  private final Integer testValue = 42;
  private final String testOutput = "log entry";

  @BeforeEach
  void setUp() {
    outerMonad = OptionalMonad.INSTANCE;
  }

  private <A> Optional<Pair<A, String>> unwrapT(WriterT<OptionalKind.Witness, String, A> writerT) {
    Kind<OptionalKind.Witness, Pair<A, String>> outerKind = writerT.run();
    return OPTIONAL.narrow(outerKind);
  }

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethodTests {

    @Test
    @DisplayName("of should create WriterT with value and empty output")
    void of_createsWithEmptyOutput() {
      WriterT<OptionalKind.Witness, String, Integer> wt =
          WriterT.of(outerMonad, STRING_MONOID, testValue);
      Optional<Pair<Integer, String>> result = unwrapT(wt);
      assertThat(result).isPresent();
      assertThat(result.get().first()).isEqualTo(testValue);
      assertThat(result.get().second()).isEqualTo("");
    }

    @Test
    @DisplayName("writer should create WriterT with explicit value and output")
    void writer_createsWithValueAndOutput() {
      WriterT<OptionalKind.Witness, String, Integer> wt =
          WriterT.writer(outerMonad, testValue, testOutput);
      Optional<Pair<Integer, String>> result = unwrapT(wt);
      assertThat(result).isPresent();
      assertThat(result.get().first()).isEqualTo(testValue);
      assertThat(result.get().second()).isEqualTo(testOutput);
    }

    @Test
    @DisplayName("fromKind should wrap existing Kind<F, Pair<A, W>>")
    void fromKind_wrapsExisting() {
      Kind<OptionalKind.Witness, Pair<Integer, String>> wrapped =
          OPTIONAL.widen(Optional.of(Pair.of(testValue, testOutput)));
      WriterT<OptionalKind.Witness, String, Integer> wt = WriterT.fromKind(wrapped);
      Optional<Pair<Integer, String>> result = unwrapT(wt);
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(Pair.of(testValue, testOutput));
    }

    @Test
    @DisplayName("liftF should lift monadic value with empty output")
    void liftF_liftsWithEmptyOutput() {
      Kind<OptionalKind.Witness, Integer> fa = outerMonad.of(testValue);
      WriterT<OptionalKind.Witness, String, Integer> wt =
          WriterT.liftF(outerMonad, STRING_MONOID, fa);
      Optional<Pair<Integer, String>> result = unwrapT(wt);
      assertThat(result).isPresent();
      assertThat(result.get().first()).isEqualTo(testValue);
      assertThat(result.get().second()).isEqualTo("");
    }
  }

  @Nested
  @DisplayName("Object Methods")
  class ObjectMethodTests {

    @Test
    @DisplayName("equals should compare based on wrapped value")
    void equals_comparesWrappedValue() {
      Kind<OptionalKind.Witness, Pair<Integer, String>> wrapped1 =
          OPTIONAL.widen(Optional.of(Pair.of(testValue, testOutput)));
      Kind<OptionalKind.Witness, Pair<Integer, String>> wrapped2 =
          OPTIONAL.widen(Optional.of(Pair.of(testValue, testOutput)));
      Kind<OptionalKind.Witness, Pair<Integer, String>> wrapped3 =
          OPTIONAL.widen(Optional.of(Pair.of(99, "other")));

      WriterT<OptionalKind.Witness, String, Integer> wt1 = WriterT.fromKind(wrapped1);
      WriterT<OptionalKind.Witness, String, Integer> wt2 = WriterT.fromKind(wrapped2);
      WriterT<OptionalKind.Witness, String, Integer> wt3 = WriterT.fromKind(wrapped3);

      assertThat(wt1).isEqualTo(wt2);
      assertThat(wt1).isNotEqualTo(wt3);
      assertThat(wt1).isNotEqualTo(null);
    }

    @Test
    @DisplayName("hashCode should be consistent with equals")
    void hashCode_consistentWithEquals() {
      Kind<OptionalKind.Witness, Pair<Integer, String>> wrapped1 =
          OPTIONAL.widen(Optional.of(Pair.of(testValue, testOutput)));
      Kind<OptionalKind.Witness, Pair<Integer, String>> wrapped2 =
          OPTIONAL.widen(Optional.of(Pair.of(testValue, testOutput)));

      WriterT<OptionalKind.Witness, String, Integer> wt1 = WriterT.fromKind(wrapped1);
      WriterT<OptionalKind.Witness, String, Integer> wt2 = WriterT.fromKind(wrapped2);

      assertThat(wt1.hashCode()).isEqualTo(wt2.hashCode());
    }

    @Test
    @DisplayName("toString should represent the structure")
    void toString_representsStructure() {
      WriterT<OptionalKind.Witness, String, Integer> wt =
          WriterT.writer(outerMonad, testValue, testOutput);
      assertThat(wt.toString()).startsWith("WriterT[run=").endsWith("]");
    }
  }

  @Nested
  @DisplayName("mapT")
  class MapTTests {

    @Test
    @DisplayName("mapT with identity should return equivalent WriterT")
    void mapT_identity() {
      WriterT<OptionalKind.Witness, String, Integer> wt =
          WriterT.writer(outerMonad, testValue, testOutput);
      WriterT<OptionalKind.Witness, String, Integer> result = wt.mapT(Function.identity());
      Optional<Pair<Integer, String>> unwrapped = unwrapT(result);
      assertThat(unwrapped).isPresent();
      assertThat(unwrapped.get()).isEqualTo(Pair.of(testValue, testOutput));
    }

    @Test
    @DisplayName("mapT should transform outer monad to a different type")
    void mapT_crossMonad() {
      WriterT<OptionalKind.Witness, String, Integer> wt =
          WriterT.writer(outerMonad, testValue, testOutput);

      WriterT<IdKind.Witness, String, Integer> result =
          wt.mapT(
              optKind -> {
                Optional<Pair<Integer, String>> opt = OPTIONAL.narrow(optKind);
                return IdKindHelper.ID.widen(Id.of(opt.orElse(Pair.of(-1, "empty"))));
              });

      Id<Pair<Integer, String>> id = IdKindHelper.ID.narrow(result.run());
      assertThat(id.value()).isEqualTo(Pair.of(testValue, testOutput));
    }

    @Test
    @DisplayName("mapT should preserve accumulated output")
    void mapT_preservesOutput() {
      WriterT<OptionalKind.Witness, String, Integer> wt =
          WriterT.writer(outerMonad, testValue, "accumulated log");
      WriterT<OptionalKind.Witness, String, Integer> result = wt.mapT(Function.identity());
      Optional<Pair<Integer, String>> unwrapped = unwrapT(result);
      assertThat(unwrapped).isPresent();
      assertThat(unwrapped.get().second()).isEqualTo("accumulated log");
    }

    @Test
    @DisplayName("mapT should preserve empty outer monad")
    void mapT_preservesEmpty() {
      Kind<OptionalKind.Witness, Pair<Integer, String>> empty = OPTIONAL.widen(Optional.empty());
      WriterT<OptionalKind.Witness, String, Integer> wt = WriterT.fromKind(empty);
      WriterT<OptionalKind.Witness, String, Integer> result = wt.mapT(Function.identity());
      Optional<Pair<Integer, String>> unwrapped = unwrapT(result);
      assertThat(unwrapped).isEmpty();
    }

    @Test
    @DisplayName("mapT should reject null function")
    void mapT_rejectsNull() {
      WriterT<OptionalKind.Witness, String, Integer> wt =
          WriterT.writer(outerMonad, testValue, testOutput);
      assertThatThrownBy(() -> wt.mapT(null)).isInstanceOf(NullPointerException.class);
    }
  }
}
