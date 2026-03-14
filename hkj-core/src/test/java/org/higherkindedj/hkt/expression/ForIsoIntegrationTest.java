// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.List;
import java.util.Optional;
import org.assertj.core.data.Offset;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@code through(Iso)} integration with For comprehensions.
 *
 * <p>This test class covers the {@code through()} method on both {@code MonadicSteps1} and {@code
 * FilterableSteps1}, verifying that an {@link Iso} can be used to convert the bound value and
 * accumulate the result alongside the original value.
 */
@DisplayName("For Comprehension Iso Integration Tests")
class ForIsoIntegrationTest {

  // --- Domain Model Records ---

  record Celsius(double value) {}

  record Fahrenheit(double value) {}

  record Wrapper(String inner) {}

  // --- Sealed Hierarchy for Prism Tests ---

  sealed interface Shape permits Circle, Square {}

  record Circle(double radius) implements Shape {}

  record Square(double side) implements Shape {}

  // --- Shared Isos ---

  private Iso<Celsius, Fahrenheit> celsiusToFahrenheit;
  private Iso<String, Wrapper> stringToWrapper;

  @BeforeEach
  void setUpIsos() {
    celsiusToFahrenheit =
        Iso.of(
            c -> new Fahrenheit(c.value() * 9.0 / 5.0 + 32),
            f -> new Celsius((f.value() - 32) * 5.0 / 9.0));
    stringToWrapper = Iso.of(Wrapper::new, Wrapper::inner);
  }

  // --- through() with Identity Monad ---

  @Nested
  @DisplayName("through() with Identity Monad")
  class ThroughWithIdMonadTests {
    private final IdMonad idMonad = IdMonad.instance();

    @Test
    @DisplayName("should convert Celsius to Fahrenheit and yield both values")
    void throughBasicConversion() {
      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(new Celsius(100.0)))
              .through(celsiusToFahrenheit)
              .yield((celsius, fahrenheit) -> celsius.value() + "C = " + fahrenheit.value() + "F");

      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("100.0C = 212.0F");
    }

    @Test
    @DisplayName("should wrap a string value and verify both original and wrapped are accessible")
    void throughStringToWrapper() {
      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of("hello"))
              .through(stringToWrapper)
              .yield((original, wrapped) -> original + " -> " + wrapped.inner());

      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("hello -> hello");
    }

    @Test
    @DisplayName("should support chaining through() followed by from()")
    void throughThenFrom() {
      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(new Celsius(0.0)))
              .through(celsiusToFahrenheit)
              .from(t -> Id.of("Freezing point: " + t._2().value() + "F"))
              .yield((celsius, fahrenheit, description) -> description);

      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("Freezing point: 32.0F");
    }

    @Test
    @DisplayName("should support chaining through() followed by let()")
    void throughThenLet() {
      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(new Celsius(100.0)))
              .through(celsiusToFahrenheit)
              .let(t -> "Boiling: " + t._1().value() + "C / " + t._2().value() + "F")
              .yield((celsius, fahrenheit, summary) -> summary);

      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("Boiling: 100.0C / 212.0F");
    }

    @Test
    @DisplayName("should use both original and converted values in yield projection")
    void throughThenYieldProjection() {
      Kind<IdKind.Witness, Double> result =
          For.from(idMonad, Id.of(new Celsius(37.0)))
              .through(celsiusToFahrenheit)
              .yield((celsius, fahrenheit) -> fahrenheit.value() - celsius.value());

      // 37C = 98.6F, difference = 98.6 - 37.0 = 61.6
      assertThat(IdKindHelper.ID.unwrap(result)).isCloseTo(61.6, Offset.offset(0.001));
    }
  }

  // --- through() with Maybe Monad ---

  @Nested
  @DisplayName("through() with Maybe Monad")
  class ThroughWithMaybeMonadTests {
    private final MaybeMonad maybeMonad = MaybeMonad.INSTANCE;

    @Test
    @DisplayName("should convert value when applied to Just")
    void throughWithMaybeJust() {
      Kind<MaybeKind.Witness, String> result =
          For.from(maybeMonad, MAYBE.just(new Celsius(100.0)))
              .through(celsiusToFahrenheit)
              .yield((celsius, fahrenheit) -> celsius.value() + "C = " + fahrenheit.value() + "F");

      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.just("100.0C = 212.0F"));
    }

    @Test
    @DisplayName("should propagate Nothing through the conversion")
    void throughWithMaybeNothing() {
      Kind<MaybeKind.Witness, String> result =
          For.from(maybeMonad, MAYBE.<Celsius>nothing())
              .through(celsiusToFahrenheit)
              .yield((celsius, fahrenheit) -> "should not reach");

      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
    }
  }

  // --- through() with List Monad ---

  @Nested
  @DisplayName("through() with List Monad")
  class ThroughWithListMonadTests {
    private final ListMonad listMonad = ListMonad.INSTANCE;

    @Test
    @DisplayName("should apply Iso to each element in a list")
    void throughWithList() {
      List<Celsius> temperatures = List.of(new Celsius(0.0), new Celsius(100.0), new Celsius(37.0));

      Kind<ListKind.Witness, String> result =
          For.from(listMonad, LIST.widen(temperatures))
              .through(celsiusToFahrenheit)
              .yield((celsius, fahrenheit) -> celsius.value() + "C=" + fahrenheit.value() + "F");

      assertThat(LIST.narrow(result)).containsExactly("0.0C=32.0F", "100.0C=212.0F", "37.0C=98.6F");
    }
  }

  // --- through() with FilterableSteps ---

  @Nested
  @DisplayName("through() with Filterable Steps")
  class ThroughWithFilterableStepsTests {
    private final MaybeMonad maybeMonad = MaybeMonad.INSTANCE;
    private final ListMonad listMonad = ListMonad.INSTANCE;

    @Test
    @DisplayName("should convert value via Iso on FilterableSteps1")
    void throughOnFilterableSteps() {
      // Using MonadZero entry point to get FilterableSteps1
      Kind<MaybeKind.Witness, String> result =
          For.from((MonadZero<MaybeKind.Witness>) maybeMonad, MAYBE.just(new Celsius(100.0)))
              .through(celsiusToFahrenheit)
              .yield((celsius, fahrenheit) -> celsius.value() + "C = " + fahrenheit.value() + "F");

      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.just("100.0C = 212.0F"));
    }

    @Test
    @DisplayName("should support through() followed by when() guard")
    void throughThenWhen() {
      List<Celsius> temperatures = List.of(new Celsius(0.0), new Celsius(100.0), new Celsius(37.0));

      Kind<ListKind.Witness, String> result =
          For.from(listMonad, LIST.widen(temperatures))
              .through(celsiusToFahrenheit)
              .when(t -> t._2().value() > 50.0)
              .yield((celsius, fahrenheit) -> celsius.value() + "C");

      // 0C=32F (filtered out), 100C=212F (kept), 37C=98.6F (kept)
      assertThat(LIST.narrow(result)).containsExactly("100.0C", "37.0C");
    }

    @Test
    @DisplayName("should support through() followed by match() with Prism")
    void throughThenMatch() {
      Iso<Double, Shape> doubleToShape =
          Iso.of(
              d -> d > 0 ? new Circle(d) : new Square(Math.abs(d)),
              s ->
                  switch (s) {
                    case Circle c -> c.radius();
                    case Square sq -> -sq.side();
                  });

      Prism<Shape, Circle> circlePrism =
          Prism.of(s -> s instanceof Circle c ? Optional.of(c) : Optional.empty(), c -> c);

      List<Double> values = List.of(5.0, -3.0, 2.5);

      Kind<ListKind.Witness, String> result =
          For.from(listMonad, LIST.widen(values))
              .through(doubleToShape)
              .match(t -> circlePrism.getOptional(t._2()))
              .yield((d, shape, circle) -> "Circle(r=" + circle.radius() + ")");

      // 5.0 -> Circle(5.0) -> matches, -3.0 -> Square(3.0) -> filtered, 2.5 -> Circle(2.5) ->
      // matches
      assertThat(LIST.narrow(result)).containsExactly("Circle(r=5.0)", "Circle(r=2.5)");
    }
  }

  // --- Null Validation ---

  @Nested
  @DisplayName("through() Null Validation")
  class ThroughNullValidationTests {
    private final IdMonad idMonad = IdMonad.instance();
    private final MaybeMonad maybeMonad = MaybeMonad.INSTANCE;

    @Test
    @DisplayName("should throw NullPointerException when iso is null on MonadicSteps1")
    void throughNullIsoThrows_monadic() {
      assertThatThrownBy(() -> For.from(idMonad, Id.of("test")).through(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("iso");
    }

    @Test
    @DisplayName("should throw NullPointerException when iso is null on FilterableSteps1")
    void throughNullIsoThrows_filterable() {
      assertThatThrownBy(
              () ->
                  For.from((MonadZero<MaybeKind.Witness>) maybeMonad, MAYBE.just("test"))
                      .through(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("iso");
    }
  }

  // --- Composition Tests ---

  @Nested
  @DisplayName("through() Composition Tests")
  class ThroughCompositionTests {
    private final IdMonad idMonad = IdMonad.instance();
    private final ListMonad listMonad = ListMonad.INSTANCE;

    @Test
    @DisplayName("should combine through() then focus() for multi-step extraction")
    void throughThenFocusCombined() {
      record Person(String name, Celsius bodyTemp) {}

      Lens<Person, Celsius> bodyTempLens =
          Lens.of(Person::bodyTemp, (p, c) -> new Person(p.name(), c));

      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(new Person("Alice", new Celsius(37.0))))
              .focus(bodyTempLens)
              .let(t -> celsiusToFahrenheit.get(t._2()))
              .yield(
                  (person, celsius, fahrenheit) -> person.name() + ": " + fahrenheit.value() + "F");

      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("Alice: 98.6F");
    }

    @Test
    @DisplayName("should chain from -> through -> from -> yield in a full comprehension")
    void throughWithIsoThenContinueChain() {
      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of("hello"))
              .through(stringToWrapper)
              .from(t -> Id.of(t._2().inner().length()))
              .yield(
                  (original, wrapped, length) ->
                      original + " wrapped as " + wrapped + " has length " + length);

      assertThat(IdKindHelper.ID.unwrap(result))
          .isEqualTo("hello wrapped as Wrapper[inner=hello] has length 5");
    }
  }
}
