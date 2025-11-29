// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.function.Function;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Iso<S, A> Tests")
class IsoTest {

  // Test value types - wrapping primitives
  record UserId(long value) {}

  record Celsius(double value) {}

  record Fahrenheit(double value) {}

  record Point(int x, int y) {}

  record Pair<A, B>(A first, B second) {}

  private Iso<UserId, Long> userIdIso;
  private Iso<Celsius, Fahrenheit> temperatureIso;

  @BeforeEach
  void setUp() {
    // Simple wrapper iso
    userIdIso = Iso.of(UserId::value, UserId::new);

    // Temperature conversion iso
    temperatureIso =
        Iso.of(
            c -> new Fahrenheit(c.value() * 9 / 5 + 32),
            f -> new Celsius((f.value() - 32) * 5 / 9));
  }

  @Nested
  @DisplayName("Core Functionality")
  class CoreFunctionality {

    @Test
    @DisplayName("get should extract the target value")
    void get() {
      UserId userId = new UserId(42L);
      assertThat(userIdIso.get(userId)).isEqualTo(42L);
    }

    @Test
    @DisplayName("reverseGet should construct the source from target")
    void reverseGet() {
      UserId userId = userIdIso.reverseGet(42L);
      assertThat(userId.value()).isEqualTo(42L);
    }

    @Test
    @DisplayName("get and reverseGet should be inverses")
    void roundTrip() {
      UserId original = new UserId(123L);

      // Forward and back
      Long extracted = userIdIso.get(original);
      UserId reconstructed = userIdIso.reverseGet(extracted);
      assertThat(reconstructed).isEqualTo(original);

      // Back and forward
      Long value = 456L;
      UserId constructed = userIdIso.reverseGet(value);
      Long reExtracted = userIdIso.get(constructed);
      assertThat(reExtracted).isEqualTo(value);
    }

    @Test
    @DisplayName("temperature conversion iso should work correctly")
    void temperatureConversion() {
      Celsius celsius = new Celsius(0);
      Fahrenheit fahrenheit = temperatureIso.get(celsius);
      assertThat(fahrenheit.value()).isEqualTo(32.0);

      Celsius backToCelsius = temperatureIso.reverseGet(fahrenheit);
      assertThat(backToCelsius.value()).isCloseTo(0.0, Assertions.within(0.01));
    }
  }

  @Nested
  @DisplayName("modifyF Operation")
  class ModifyFOperation {

    @Test
    @DisplayName("modifyF should work with Optional applicative - success case")
    void modifyFSuccess() {
      UserId userId = new UserId(42L);
      Function<Long, Kind<OptionalKind.Witness, Long>> successModifier =
          v -> OptionalKindHelper.OPTIONAL.widen(Optional.of(v * 2));

      Kind<OptionalKind.Witness, UserId> result =
          userIdIso.modifyF(successModifier, userId, OptionalMonad.INSTANCE);

      assertThat(OptionalKindHelper.OPTIONAL.narrow(result)).isPresent().contains(new UserId(84L));
    }

    @Test
    @DisplayName("modifyF should work with Optional applicative - failure case")
    void modifyFFailure() {
      UserId userId = new UserId(42L);
      Function<Long, Kind<OptionalKind.Witness, Long>> failureModifier =
          v -> OptionalKindHelper.OPTIONAL.widen(Optional.empty());

      Kind<OptionalKind.Witness, UserId> result =
          userIdIso.modifyF(failureModifier, userId, OptionalMonad.INSTANCE);

      assertThat(OptionalKindHelper.OPTIONAL.narrow(result)).isEmpty();
    }
  }

  @Nested
  @DisplayName("reverse Operation")
  class ReverseOperation {

    @Test
    @DisplayName("reverse should create an iso in the opposite direction")
    void reverse() {
      Iso<Long, UserId> reversedIso = userIdIso.reverse();

      Long value = 42L;
      UserId userId = reversedIso.get(value);
      assertThat(userId.value()).isEqualTo(42L);

      Long backToLong = reversedIso.reverseGet(userId);
      assertThat(backToLong).isEqualTo(42L);
    }

    @Test
    @DisplayName("reverse of reverse should be equivalent to original")
    void reverseOfReverse() {
      Iso<Long, UserId> reversed = userIdIso.reverse();
      Iso<UserId, Long> reversedAgain = reversed.reverse();

      UserId userId = new UserId(123L);
      assertThat(reversedAgain.get(userId)).isEqualTo(userIdIso.get(userId));
    }
  }

  @Nested
  @DisplayName("asLens Operation")
  class AsLensOperation {

    @Test
    @DisplayName("asLens should provide a working Lens view")
    void asLens() {
      Lens<UserId, Long> lens = userIdIso.asLens();

      UserId userId = new UserId(42L);
      assertThat(lens.get(userId)).isEqualTo(42L);

      UserId updated = lens.set(100L, userId);
      assertThat(updated.value()).isEqualTo(100L);
    }

    @Test
    @DisplayName("asLens modify should work correctly")
    void asLensModify() {
      Lens<UserId, Long> lens = userIdIso.asLens();

      UserId userId = new UserId(42L);
      UserId doubled = lens.modify(v -> v * 2, userId);
      assertThat(doubled.value()).isEqualTo(84L);
    }
  }

  @Nested
  @DisplayName("asTraversal Operation")
  class AsTraversalOperation {

    @Test
    @DisplayName("asTraversal should provide a working Traversal view")
    void asTraversal() {
      Traversal<UserId, Long> traversal = userIdIso.asTraversal();

      UserId userId = new UserId(42L);
      Function<Long, Kind<OptionalKind.Witness, Long>> modifier =
          v -> OptionalKindHelper.OPTIONAL.widen(Optional.of(v * 2));

      Kind<OptionalKind.Witness, UserId> result =
          traversal.modifyF(modifier, userId, OptionalMonad.INSTANCE);

      assertThat(OptionalKindHelper.OPTIONAL.narrow(result)).isPresent().contains(new UserId(84L));
    }
  }

  @Nested
  @DisplayName("asFold Operation")
  class AsFoldOperation {

    @Test
    @DisplayName("asFold should provide a working Fold view")
    void asFold() {
      Fold<UserId, Long> fold = userIdIso.asFold();
      Monoid<Long> sumMonoid = Monoids.longAddition();

      UserId userId = new UserId(42L);
      Long result = fold.foldMap(sumMonoid, v -> v, userId);
      assertThat(result).isEqualTo(42L);
    }

    @Test
    @DisplayName("asFold should work with string concatenation monoid")
    void asFoldWithStringMonoid() {
      Fold<UserId, Long> fold = userIdIso.asFold();
      Monoid<String> stringMonoid = Monoids.string();

      UserId userId = new UserId(42L);
      String result = fold.foldMap(stringMonoid, Object::toString, userId);
      assertThat(result).isEqualTo("42");
    }
  }

  @Nested
  @DisplayName("andThen Composition")
  class AndThenComposition {

    @Test
    @DisplayName("andThen with Lens should compose correctly")
    void andThenLens() {
      record Container(UserId userId) {}

      Lens<Container, UserId> containerLens =
          Lens.of(Container::userId, (c, id) -> new Container(id));

      // Use Iso's asLens and then compose with another lens
      Lens<Container, Long> composedLens = containerLens.andThen(userIdIso.asLens());

      Container container = new Container(new UserId(42L));
      assertThat(composedLens.get(container)).isEqualTo(42L);

      Container updated = composedLens.set(100L, container);
      assertThat(updated.userId().value()).isEqualTo(100L);
    }

    @Test
    @DisplayName("andThen(Lens) should compose Iso with Lens")
    void isoAndThenLens() {
      record Wrapper(Long value) {}

      Lens<Long, Wrapper> wrapperLens = Lens.of(Wrapper::new, (l, w) -> w.value());

      // This tests the Iso.andThen(Lens) method
      Lens<UserId, Wrapper> composed = userIdIso.andThen(wrapperLens);

      UserId userId = new UserId(42L);
      Wrapper wrapper = composed.get(userId);
      assertThat(wrapper.value()).isEqualTo(42L);

      UserId updated = composed.set(new Wrapper(100L), userId);
      assertThat(updated.value()).isEqualTo(100L);
    }
  }

  @Nested
  @DisplayName("of Factory Method")
  class OfFactoryMethod {

    @Test
    @DisplayName("of should create a valid Iso from two functions")
    void ofCreatesValidIso() {
      Iso<String, Integer> stringLengthIso = Iso.of(String::length, len -> "x".repeat(len));

      String source = "hello";
      Integer length = stringLengthIso.get(source);
      assertThat(length).isEqualTo(5);

      String reconstructed = stringLengthIso.reverseGet(length);
      assertThat(reconstructed).hasSize(5);
    }

    @Test
    @DisplayName("of with identity functions should create identity iso")
    void ofWithIdentity() {
      Iso<String, String> identityIso = Iso.of(Function.identity(), Function.identity());

      String value = "test";
      assertThat(identityIso.get(value)).isEqualTo(value);
      assertThat(identityIso.reverseGet(value)).isEqualTo(value);
    }
  }

  @Nested
  @DisplayName("Iso Laws")
  class IsoLaws {

    @Test
    @DisplayName("Law: reverseGet . get = identity")
    void reverseGetAfterGet() {
      UserId original = new UserId(42L);
      UserId result = userIdIso.reverseGet(userIdIso.get(original));
      assertThat(result).isEqualTo(original);
    }

    @Test
    @DisplayName("Law: get . reverseGet = identity")
    void getAfterReverseGet() {
      Long original = 42L;
      Long result = userIdIso.get(userIdIso.reverseGet(original));
      assertThat(result).isEqualTo(original);
    }
  }
}
