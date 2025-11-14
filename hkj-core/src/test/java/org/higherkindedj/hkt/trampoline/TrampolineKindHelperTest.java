// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trampoline;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.trampoline.TrampolineKindHelper.TRAMPOLINE;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TrampolineKindHelper}.
 *
 * <p>Verifies the widen/narrow conversion operations and helper factory methods.
 */
@DisplayName("TrampolineKindHelper Tests")
class TrampolineKindHelperTest extends TrampolineTestBase {

  @Nested
  @DisplayName("widen() Tests")
  class WidenTests {

    @Test
    @DisplayName("widen() converts Trampoline to Kind")
    void widenConvertsTrampolineToKind() {
      Trampoline<Integer> trampoline = Trampoline.done(42);
      Kind<TrampolineKind.Witness, Integer> kind = TRAMPOLINE.widen(trampoline);

      assertThat(kind).isNotNull();
      assertThat(kind).isInstanceOf(TrampolineKindHelper.TrampolineHolder.class);
    }

    @Test
    @DisplayName("widen() with null throws NullPointerException")
    void widenWithNullThrows() {
      assertThatThrownBy(() -> TRAMPOLINE.widen(null)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("narrow() Tests")
  class NarrowTests {

    @Test
    @DisplayName("narrow() converts Kind back to Trampoline")
    void narrowConvertsKindToTrampoline() {
      Trampoline<Integer> original = Trampoline.done(42);
      Kind<TrampolineKind.Witness, Integer> kind = TRAMPOLINE.widen(original);
      Trampoline<Integer> narrowed = TRAMPOLINE.narrow(kind);

      assertThat(narrowed).isEqualTo(original);
      assertThat(narrowed.run()).isEqualTo(42);
    }

    @Test
    @DisplayName("narrow() with null throws KindUnwrapException")
    void narrowWithNullThrows() {
      assertThatThrownBy(() -> TRAMPOLINE.narrow(null)).isInstanceOf(KindUnwrapException.class);
    }

    @Test
    @DisplayName("narrow() with invalid Kind throws KindUnwrapException")
    void narrowWithInvalidKindThrows() {
      Kind<TrampolineKind.Witness, Integer> invalidKind = new Kind<>() {};

      assertThatThrownBy(() -> TRAMPOLINE.narrow(invalidKind))
          .isInstanceOf(KindUnwrapException.class);
    }
  }

  @Nested
  @DisplayName("done() Helper Tests")
  class DoneHelperTests {

    @Test
    @DisplayName("done() helper creates a completed trampoline Kind")
    void doneHelperCreatesCompletedKind() {
      Kind<TrampolineKind.Witness, Integer> kind = TRAMPOLINE.done(42);

      Trampoline<Integer> trampoline = TRAMPOLINE.narrow(kind);
      assertThat(trampoline).isInstanceOf(Trampoline.Done.class);
      assertThat(trampoline.run()).isEqualTo(42);
    }

    @Test
    @DisplayName("done() helper accepts null values")
    void doneHelperAcceptsNull() {
      Kind<TrampolineKind.Witness, String> kind = TRAMPOLINE.done(null);

      Trampoline<String> trampoline = TRAMPOLINE.narrow(kind);
      assertThat(trampoline.run()).isNull();
    }
  }

  @Nested
  @DisplayName("Round-trip Tests")
  class RoundTripTests {

    @Test
    @DisplayName("widen then narrow returns equivalent trampoline")
    void widenThenNarrowReturnsEquivalent() {
      Trampoline<Integer> original = Trampoline.done(42);
      Trampoline<Integer> roundTrip = TRAMPOLINE.narrow(TRAMPOLINE.widen(original));

      assertThat(roundTrip).isEqualTo(original);
      assertThat(roundTrip.run()).isEqualTo(original.run());
    }

    @Test
    @DisplayName("Round-trip with deferred trampoline works correctly")
    void roundTripWithDeferredTrampoline() {
      Trampoline<Integer> original = Trampoline.defer(() -> Trampoline.done(42));
      Trampoline<Integer> roundTrip = TRAMPOLINE.narrow(TRAMPOLINE.widen(original));

      assertThat(roundTrip).isEqualTo(original);
      assertThat(roundTrip.run()).isEqualTo(original.run());
    }

    @Test
    @DisplayName("Round-trip with flatMap trampoline")
    void roundTripWithFlatMapTrampoline() {
      Trampoline<Integer> original = Trampoline.done(10).flatMap(x -> Trampoline.done(x * 2));
      Trampoline<Integer> roundTrip = TRAMPOLINE.narrow(TRAMPOLINE.widen(original));

      assertThat(roundTrip).isEqualTo(original);
      assertThat(roundTrip.run()).isEqualTo(original.run());
    }

    @Test
    @DisplayName("Multiple round-trips preserve value")
    void multipleRoundTripsPreserveValue() {
      Trampoline<Integer> original = Trampoline.done(42);

      // Multiple round-trips
      Trampoline<Integer> result = original;
      for (int i = 0; i < 10; i++) {
        result = TRAMPOLINE.narrow(TRAMPOLINE.widen(result));
      }

      assertThat(result.run()).isEqualTo(original.run());
    }
  }

  @Nested
  @DisplayName("Enum Singleton Tests")
  class EnumSingletonTests {

    @Test
    @DisplayName("TRAMPOLINE enum constant is accessible")
    void trampolineEnumConstantAccessible() {
      assertThat(TrampolineKindHelper.TRAMPOLINE).isNotNull();
      assertThat(TrampolineKindHelper.TRAMPOLINE).isSameAs(TRAMPOLINE);
    }

    @Test
    @DisplayName("Multiple references to TRAMPOLINE return same instance")
    void multipleReferencesReturnSameInstance() {
      TrampolineKindHelper helper1 = TrampolineKindHelper.TRAMPOLINE;
      TrampolineKindHelper helper2 = TrampolineKindHelper.TRAMPOLINE;

      assertThat(helper1).isSameAs(helper2);
    }

    @Test
    @DisplayName("TrampolineKindHelper is an enum")
    void trampolineKindHelperIsEnum() {
      assertThat(TRAMPOLINE).isInstanceOf(Enum.class);
      assertThat(TRAMPOLINE).isInstanceOf(TrampolineConverterOps.class);
    }
  }

  @Nested
  @DisplayName("TrampolineHolder Tests")
  class TrampolineHolderTests {

    @Test
    @DisplayName("TrampolineHolder wraps trampoline correctly")
    void trampolineHolderWrapsCorrectly() {
      Trampoline<Integer> trampoline = Trampoline.done(42);
      Kind<TrampolineKind.Witness, Integer> kind = TRAMPOLINE.widen(trampoline);

      // Verify it's a holder
      assertThat(kind).isInstanceOf(TrampolineKindHelper.TrampolineHolder.class);

      // Verify we can extract the original
      Trampoline<Integer> extracted = TRAMPOLINE.narrow(kind);
      assertThat(extracted).isEqualTo(trampoline);
    }

    @Test
    @DisplayName("TrampolineHolder implements TrampolineKind")
    void trampolineHolderImplementsKind() {
      Trampoline<Integer> trampoline = Trampoline.done(42);
      Kind<TrampolineKind.Witness, Integer> kind = TRAMPOLINE.widen(trampoline);

      assertThat(kind).isInstanceOf(TrampolineKind.class);
    }

    @Test
    @DisplayName("TrampolineHolder with different trampoline types")
    void trampolineHolderWithDifferentTypes() {
      // Test with Done
      Trampoline<Integer> done = Trampoline.done(1);
      Kind<TrampolineKind.Witness, Integer> doneKind = TRAMPOLINE.widen(done);
      assertThat(TRAMPOLINE.narrow(doneKind)).isEqualTo(done);

      // Test with More
      Trampoline<Integer> more = Trampoline.defer(() -> Trampoline.done(2));
      Kind<TrampolineKind.Witness, Integer> moreKind = TRAMPOLINE.widen(more);
      assertThat(TRAMPOLINE.narrow(moreKind)).isEqualTo(more);

      // Test with FlatMap
      Trampoline<Integer> flatMap = Trampoline.done(3).flatMap(x -> Trampoline.done(x * 2));
      Kind<TrampolineKind.Witness, Integer> flatMapKind = TRAMPOLINE.widen(flatMap);
      assertThat(TRAMPOLINE.narrow(flatMapKind)).isEqualTo(flatMap);
    }
  }

  @Nested
  @DisplayName("Additional Edge Cases")
  class AdditionalEdgeCases {

    @Test
    @DisplayName("widen and narrow with null value trampolines")
    void widenAndNarrowWithNullValues() {
      Trampoline<String> trampoline = Trampoline.done(null);
      Kind<TrampolineKind.Witness, String> kind = TRAMPOLINE.widen(trampoline);
      Trampoline<String> result = TRAMPOLINE.narrow(kind);

      assertThat(result.run()).isNull();
    }

    @Test
    @DisplayName("done helper with complex types")
    void doneHelperWithComplexTypes() {
      record TestRecord(String name, int value) {}

      TestRecord record = new TestRecord("test", 42);
      Kind<TrampolineKind.Witness, TestRecord> kind = TRAMPOLINE.done(record);

      Trampoline<TestRecord> trampoline = TRAMPOLINE.narrow(kind);
      assertThat(trampoline.run()).isEqualTo(record);
    }
  }
}
