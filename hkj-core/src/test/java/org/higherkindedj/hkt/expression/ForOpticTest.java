// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the optics integration with For comprehensions.
 *
 * <p>This test class covers:
 *
 * <ul>
 *   <li>{@code focus()} - Lens-based extraction in comprehensions
 *   <li>{@code match()} - Prism-based pattern matching with short-circuit semantics
 * </ul>
 */
@DisplayName("For Comprehension Optics Integration Tests")
class ForOpticTest {

  // --- Test Data Classes ---

  record Street(String name) {}

  record Address(Street street, String city) {}

  record User(String name, Address address) {}

  sealed interface Result permits Success, Failure {}

  record Success(String value) implements Result {}

  record Failure(String error) implements Result {}

  sealed interface JsonValue permits JsonString, JsonNumber, JsonNull {}

  record JsonString(String value) implements JsonValue {}

  record JsonNumber(int value) implements JsonValue {}

  record JsonNull() implements JsonValue {}

  // --- focus() Tests with Identity Monad ---

  @Nested
  @DisplayName("focus() with Identity Monad")
  class FocusWithIdTest {
    private final IdMonad idMonad = IdMonad.instance();

    private Lens<User, Address> userAddressLens;
    private Lens<Address, String> addressCityLens;
    private Lens<Address, Street> addressStreetLens;
    private Lens<Street, String> streetNameLens;

    @BeforeEach
    void setUp() {
      userAddressLens = Lens.of(User::address, (u, a) -> new User(u.name(), a));
      addressCityLens = Lens.of(Address::city, (a, c) -> new Address(a.street(), c));
      addressStreetLens = Lens.of(Address::street, (a, s) -> new Address(s, a.city()));
      streetNameLens = Lens.of(Street::name, (s, n) -> new Street(n));
    }

    @Test
    @DisplayName("should extract value via single lens at arity 1")
    void focusSingleLensArity1() {
      User user = new User("Alice", new Address(new Street("Main"), "NYC"));

      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(user)).focus(userAddressLens).yield((u, addr) -> addr.city());

      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("NYC");
    }

    @Test
    @DisplayName("should extract nested value via chained focus operations")
    void focusChainedLenses() {
      User user = new User("Bob", new Address(new Street("Oak"), "LA"));

      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(user))
              .focus(userAddressLens)
              .focus(t -> addressStreetLens.get(t._2()))
              .yield((u, addr, street) -> street.name());

      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("Oak");
    }

    @Test
    @DisplayName("should accumulate all focused values in tuple")
    void focusAccumulatesInTuple() {
      User user = new User("Charlie", new Address(new Street("Pine"), "Seattle"));

      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(user))
              .focus(userAddressLens)
              .yield((u, addr) -> u.name() + " lives in " + addr.city());

      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("Charlie lives in Seattle");
    }

    @Test
    @DisplayName("should work with composed lens")
    void focusWithComposedLens() {
      User user = new User("Diana", new Address(new Street("Elm"), "Boston"));
      Lens<User, String> userCityLens = userAddressLens.andThen(addressCityLens);

      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(user)).focus(userCityLens).yield((u, city) -> city.toUpperCase());

      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("BOSTON");
    }

    @Test
    @DisplayName("should support focus followed by from")
    void focusThenFrom() {
      User user = new User("Eve", new Address(new Street("Maple"), "Denver"));

      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(user))
              .focus(userAddressLens)
              .from(t -> Id.of(t._2().city().length()))
              .yield((u, addr, len) -> u.name() + "'s city has " + len + " chars");

      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("Eve's city has 6 chars");
    }

    @Test
    @DisplayName("should support focus followed by let")
    void focusThenLet() {
      User user = new User("Frank", new Address(new Street("Cedar"), "Miami"));

      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(user))
              .focus(userAddressLens)
              .let(t -> t._2().city().toUpperCase())
              .yield((u, addr, upperCity) -> u.name() + " is in " + upperCity);

      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("Frank is in MIAMI");
    }

    @Test
    @DisplayName("should throw NullPointerException when lens is null")
    void focusWithNullLensThrows() {
      assertThatThrownBy(() -> For.from(idMonad, Id.of("test")).focus(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("lens");
    }

    @Test
    @DisplayName("Arity 2: should support focus after from")
    void arity2FocusAfterFrom() {
      User user = new User("Grace", new Address(new Street("Birch"), "Portland"));

      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(user))
              .from(u -> Id.of(u.address()))
              .focus(t -> addressCityLens.get(t._2()))
              .yield((u, addr, city) -> city);

      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("Portland");
    }

    @Test
    @DisplayName("Arity 3: should chain multiple focus operations")
    void arity3MultipleFocus() {
      User user = new User("Henry", new Address(new Street("Willow"), "Austin"));

      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(user))
              .focus(userAddressLens)
              .focus(t -> addressStreetLens.get(t._2()))
              .focus(t -> streetNameLens.get(t._3()))
              .yield((u, addr, street, name) -> name);

      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("Willow");
    }
  }

  // --- focus() Tests with List Monad ---

  @Nested
  @DisplayName("focus() with List Monad")
  class FocusWithListTest {
    private final ListMonad listMonad = ListMonad.INSTANCE;

    private Lens<User, Address> userAddressLens;
    private Lens<Address, String> addressCityLens;

    @BeforeEach
    void setUp() {
      userAddressLens = Lens.of(User::address, (u, a) -> new User(u.name(), a));
      addressCityLens = Lens.of(Address::city, (a, c) -> new Address(a.street(), c));
    }

    @Test
    @DisplayName("should focus into each list element")
    void focusIntoListElements() {
      List<User> users =
          List.of(
              new User("Alice", new Address(new Street("A St"), "NYC")),
              new User("Bob", new Address(new Street("B St"), "LA")));

      Kind<ListKind.Witness, String> result =
          For.from(listMonad, LIST.widen(users))
              .focus(userAddressLens)
              .yield((user, addr) -> addr.city());

      assertThat(LIST.narrow(result)).containsExactly("NYC", "LA");
    }

    @Test
    @DisplayName("should combine focus with when filter")
    void focusWithFilter() {
      List<User> users =
          List.of(
              new User("Alice", new Address(new Street("A St"), "NYC")),
              new User("Bob", new Address(new Street("B St"), "LA")),
              new User("Charlie", new Address(new Street("C St"), "NYC")));

      Kind<ListKind.Witness, String> result =
          For.from(listMonad, LIST.widen(users))
              .focus(userAddressLens)
              .when(t -> t._2().city().equals("NYC"))
              .yield((user, addr) -> user.name());

      assertThat(LIST.narrow(result)).containsExactly("Alice", "Charlie");
    }

    @Test
    @DisplayName("should work with empty list")
    void focusOnEmptyList() {
      List<User> users = List.of();

      Kind<ListKind.Witness, String> result =
          For.from(listMonad, LIST.widen(users))
              .focus(userAddressLens)
              .yield((user, addr) -> addr.city());

      assertThat(LIST.narrow(result)).isEmpty();
    }
  }

  // --- focus() Tests with Maybe Monad ---

  @Nested
  @DisplayName("focus() with Maybe Monad")
  class FocusWithMaybeTest {
    private final MaybeMonad maybeMonad = MaybeMonad.INSTANCE;

    private Lens<User, Address> userAddressLens;

    @BeforeEach
    void setUp() {
      userAddressLens = Lens.of(User::address, (u, a) -> new User(u.name(), a));
    }

    @Test
    @DisplayName("should extract value from Just")
    void focusOnJust() {
      User user = new User("Alice", new Address(new Street("Main"), "NYC"));

      Kind<MaybeKind.Witness, String> result =
          For.from(maybeMonad, MAYBE.just(user))
              .focus(userAddressLens)
              .yield((u, addr) -> addr.city());

      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.just("NYC"));
    }

    @Test
    @DisplayName("should propagate Nothing through focus")
    void focusOnNothing() {
      Kind<MaybeKind.Witness, String> result =
          For.from(maybeMonad, MAYBE.<User>nothing())
              .focus(userAddressLens)
              .yield((u, addr) -> addr.city());

      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("should short-circuit when filter after focus returns false")
    void focusThenWhenShortCircuits() {
      User user = new User("Bob", new Address(new Street("Oak"), "LA"));

      Kind<MaybeKind.Witness, String> result =
          For.from(maybeMonad, MAYBE.just(user))
              .focus(userAddressLens)
              .when(t -> t._2().city().equals("NYC"))
              .yield((u, addr) -> "Found NYC user");

      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
    }
  }

  // --- match() Tests with Maybe Monad ---

  @Nested
  @DisplayName("match() with Maybe Monad (Pattern Matching)")
  class MatchWithMaybeTest {
    private final MaybeMonad maybeMonad = MaybeMonad.INSTANCE;

    private Prism<Result, Success> successPrism;
    private Prism<Result, Failure> failurePrism;

    @BeforeEach
    void setUp() {
      successPrism =
          Prism.of(r -> r instanceof Success s ? Optional.of(s) : Optional.empty(), s -> s);
      failurePrism =
          Prism.of(r -> r instanceof Failure f ? Optional.of(f) : Optional.empty(), f -> f);
    }

    @Test
    @DisplayName("should extract value when prism matches")
    void matchSuccess() {
      Result result = new Success("data");

      Kind<MaybeKind.Witness, String> output =
          For.from(maybeMonad, MAYBE.just(result))
              .match(successPrism)
              .yield((original, success) -> success.value().toUpperCase());

      assertThat(MAYBE.narrow(output)).isEqualTo(Maybe.just("DATA"));
    }

    @Test
    @DisplayName("should short-circuit to Nothing when prism doesn't match")
    void matchFailureReturnsNothing() {
      Result result = new Failure("error");

      Kind<MaybeKind.Witness, String> output =
          For.from(maybeMonad, MAYBE.just(result))
              .match(successPrism)
              .yield((original, success) -> "should not reach");

      assertThat(MAYBE.narrow(output)).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("should propagate Nothing through match")
    void matchOnNothingPropagatesToNothing() {
      Kind<MaybeKind.Witness, String> output =
          For.from(maybeMonad, MAYBE.<Result>nothing())
              .match(successPrism)
              .yield((original, success) -> "should not reach");

      assertThat(MAYBE.narrow(output)).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("should chain match with when for additional filtering")
    void matchThenWhen() {
      Result result = new Success("important-data");

      Kind<MaybeKind.Witness, String> output =
          For.from(maybeMonad, MAYBE.just(result))
              .match(successPrism)
              .when(t -> t._2().value().startsWith("important"))
              .yield((original, success) -> success.value());

      assertThat(MAYBE.narrow(output)).isEqualTo(Maybe.just("important-data"));

      // With non-matching when
      Result result2 = new Success("normal-data");
      Kind<MaybeKind.Witness, String> output2 =
          For.from(maybeMonad, MAYBE.just(result2))
              .match(successPrism)
              .when(t -> t._2().value().startsWith("important"))
              .yield((original, success) -> success.value());

      assertThat(MAYBE.narrow(output2)).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("should throw NullPointerException when prism is null")
    void matchWithNullPrismThrows() {
      assertThatThrownBy(() -> For.from(maybeMonad, MAYBE.just(new Success("test"))).match(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("prism");
    }
  }

  // --- match() Tests with List Monad ---

  @Nested
  @DisplayName("match() with List Monad")
  class MatchWithListTest {
    private final ListMonad listMonad = ListMonad.INSTANCE;

    private Prism<Result, Success> successPrism;
    private Prism<JsonValue, JsonString> jsonStringPrism;
    private Prism<JsonValue, JsonNumber> jsonNumberPrism;

    @BeforeEach
    void setUp() {
      successPrism =
          Prism.of(r -> r instanceof Success s ? Optional.of(s) : Optional.empty(), s -> s);
      jsonStringPrism =
          Prism.of(j -> j instanceof JsonString s ? Optional.of(s) : Optional.empty(), s -> s);
      jsonNumberPrism =
          Prism.of(j -> j instanceof JsonNumber n ? Optional.of(n) : Optional.empty(), n -> n);
    }

    @Test
    @DisplayName("should filter list elements by prism match")
    void matchFiltersVariants() {
      List<Result> results =
          List.of(new Success("a"), new Failure("err1"), new Success("b"), new Failure("err2"));

      Kind<ListKind.Witness, String> successes =
          For.from(listMonad, LIST.widen(results)).match(successPrism).yield((r, s) -> s.value());

      assertThat(LIST.narrow(successes)).containsExactly("a", "b");
    }

    @Test
    @DisplayName("should extract all JSON strings from mixed values")
    void matchJsonStrings() {
      List<JsonValue> values =
          List.of(new JsonString("hello"), new JsonNumber(42), new JsonString("world"));

      Kind<ListKind.Witness, String> strings =
          For.from(listMonad, LIST.widen(values))
              .match(jsonStringPrism)
              .yield((j, s) -> s.value().toUpperCase());

      assertThat(LIST.narrow(strings)).containsExactly("HELLO", "WORLD");
    }

    @Test
    @DisplayName("should return empty list when no elements match")
    void matchReturnsEmptyWhenNoMatches() {
      List<Result> results = List.of(new Failure("err1"), new Failure("err2"));

      Kind<ListKind.Witness, String> successes =
          For.from(listMonad, LIST.widen(results)).match(successPrism).yield((r, s) -> s.value());

      assertThat(LIST.narrow(successes)).isEmpty();
    }

    @Test
    @DisplayName("should work with empty list")
    void matchOnEmptyList() {
      List<Result> results = List.of();

      Kind<ListKind.Witness, String> successes =
          For.from(listMonad, LIST.widen(results)).match(successPrism).yield((r, s) -> s.value());

      assertThat(LIST.narrow(successes)).isEmpty();
    }

    @Test
    @DisplayName("should combine match with when filter")
    void matchWithAdditionalFilter() {
      List<JsonValue> values =
          List.of(
              new JsonString("short"),
              new JsonNumber(42),
              new JsonString("a longer string"),
              new JsonString("medium text"));

      Kind<ListKind.Witness, String> longStrings =
          For.from(listMonad, LIST.widen(values))
              .match(jsonStringPrism)
              .when(t -> t._2().value().length() > 10)
              .yield((j, s) -> s.value());

      assertThat(LIST.narrow(longStrings)).containsExactly("a longer string", "medium text");
    }
  }

  // --- Combined focus() and match() Tests ---

  @Nested
  @DisplayName("Combined focus() and match() Operations")
  class CombinedFocusAndMatchTest {
    private final MaybeMonad maybeMonad = MaybeMonad.INSTANCE;
    private final ListMonad listMonad = ListMonad.INSTANCE;

    record Container(Result result) {}

    private Lens<Container, Result> containerResultLens;
    private Prism<Result, Success> successPrism;

    @BeforeEach
    void setUp() {
      containerResultLens = Lens.of(Container::result, (c, r) -> new Container(r));
      successPrism =
          Prism.of(r -> r instanceof Success s ? Optional.of(s) : Optional.empty(), s -> s);
    }

    @Test
    @DisplayName("should chain focus then match")
    void focusThenMatch() {
      Container container = new Container(new Success("data"));

      Kind<MaybeKind.Witness, String> output =
          For.from(maybeMonad, MAYBE.just(container))
              .focus(containerResultLens)
              .match(t -> successPrism.getOptional(t._2()))
              .yield((c, r, s) -> s.value());

      assertThat(MAYBE.narrow(output)).isEqualTo(Maybe.just("data"));
    }

    @Test
    @DisplayName("should short-circuit when inner prism doesn't match")
    void focusThenMatchShortCircuits() {
      Container container = new Container(new Failure("error"));

      Kind<MaybeKind.Witness, String> output =
          For.from(maybeMonad, MAYBE.just(container))
              .focus(containerResultLens)
              .match(t -> successPrism.getOptional(t._2()))
              .yield((c, r, s) -> "should not reach");

      assertThat(MAYBE.narrow(output)).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("should work with list of containers")
    void focusThenMatchOnList() {
      List<Container> containers =
          List.of(
              new Container(new Success("a")),
              new Container(new Failure("err")),
              new Container(new Success("b")));

      Kind<ListKind.Witness, String> successes =
          For.from(listMonad, LIST.widen(containers))
              .focus(containerResultLens)
              .match(t -> successPrism.getOptional(t._2()))
              .yield((c, r, s) -> s.value());

      assertThat(LIST.narrow(successes)).containsExactly("a", "b");
    }
  }

  // --- Higher Arity focus() Tests ---

  @Nested
  @DisplayName("focus() at Higher Arities")
  class FocusHigherArityTest {
    private final IdMonad idMonad = IdMonad.instance();

    record Level1(Level2 l2) {}

    record Level2(Level3 l3) {}

    record Level3(Level4 l4) {}

    record Level4(String value) {}

    private Lens<Level1, Level2> l1l2;
    private Lens<Level2, Level3> l2l3;
    private Lens<Level3, Level4> l3l4;
    private Lens<Level4, String> l4val;

    @BeforeEach
    void setUp() {
      l1l2 = Lens.of(Level1::l2, (l1, l2) -> new Level1(l2));
      l2l3 = Lens.of(Level2::l3, (l2, l3) -> new Level2(l3));
      l3l4 = Lens.of(Level3::l4, (l3, l4) -> new Level3(l4));
      l4val = Lens.of(Level4::value, (l4, v) -> new Level4(v));
    }

    @Test
    @DisplayName("Arity 4: should chain four focus operations")
    void arity4Focus() {
      Level1 data = new Level1(new Level2(new Level3(new Level4("deep"))));

      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(data))
              .focus(l1l2)
              .focus(t -> l2l3.get(t._2()))
              .focus(t -> l3l4.get(t._3()))
              .focus(t -> l4val.get(t._4()))
              .yield((l1, l2, l3, l4, val) -> val.toUpperCase());

      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("DEEP");
    }

    @Test
    @DisplayName("Arity 5: should chain five focus operations into arity 6")
    void arity5Focus() {
      Level1 data = new Level1(new Level2(new Level3(new Level4("deep"))));

      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(data))
              .focus(l1l2)
              .focus(t -> l2l3.get(t._2()))
              .focus(t -> l3l4.get(t._3()))
              .focus(t -> l4val.get(t._4()))
              .focus(t -> t._5().length())
              .yield((l1, l2, l3, l4, val, len) -> val + "(" + len + ")");

      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("deep(4)");
    }

    @Test
    @DisplayName("Arity 6: should chain six focus operations into arity 7")
    void arity6Focus() {
      Level1 data = new Level1(new Level2(new Level3(new Level4("deep"))));

      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(data))
              .focus(l1l2)
              .focus(t -> l2l3.get(t._2()))
              .focus(t -> l3l4.get(t._3()))
              .focus(t -> l4val.get(t._4()))
              .focus(t -> t._5().length())
              .focus(t -> t._5().toUpperCase())
              .yield((l1, l2, l3, l4, val, len, upper) -> upper + "(" + len + ")");

      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("DEEP(4)");
    }

    @Test
    @DisplayName("Arity 7: should chain seven focus operations into arity 8")
    void arity7Focus() {
      Level1 data = new Level1(new Level2(new Level3(new Level4("deep"))));

      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(data))
              .focus(l1l2)
              .focus(t -> l2l3.get(t._2()))
              .focus(t -> l3l4.get(t._3()))
              .focus(t -> l4val.get(t._4()))
              .focus(t -> t._5().length())
              .focus(t -> t._5().toUpperCase())
              .focus(t -> t._7().charAt(0))
              .yield((l1, l2, l3, l4, val, len, upper, ch) -> "" + ch + ":" + upper + ":" + len);

      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("D:DEEP:4");
    }

    @Test
    @DisplayName("Arity 8: should chain eight focus operations into arity 9")
    void arity8Focus() {
      Level1 data = new Level1(new Level2(new Level3(new Level4("deep"))));

      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(data))
              .focus(l1l2)
              .focus(t -> l2l3.get(t._2()))
              .focus(t -> l3l4.get(t._3()))
              .focus(t -> l4val.get(t._4()))
              .focus(t -> t._5().length())
              .focus(t -> t._5().toUpperCase())
              .focus(t -> t._7().charAt(0))
              .focus(t -> t._5().charAt(1))
              .yield((l1, l2, l3, l4, val, len, upper, ch, ch2) -> "" + ch + ch2 + ":" + upper);

      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("De:DEEP");
    }

    @Test
    @DisplayName("Arity 9: should chain nine focus operations into arity 10")
    void arity9Focus() {
      Level1 data = new Level1(new Level2(new Level3(new Level4("deep"))));

      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(data))
              .focus(l1l2)
              .focus(t -> l2l3.get(t._2()))
              .focus(t -> l3l4.get(t._3()))
              .focus(t -> l4val.get(t._4()))
              .focus(t -> t._5().length())
              .focus(t -> t._5().toUpperCase())
              .focus(t -> t._7().charAt(0))
              .focus(t -> t._5().charAt(1))
              .focus(t -> t._5().substring(0, 2))
              .yield(
                  (l1, l2, l3, l4, val, len, upper, ch, ch2, sub) ->
                      sub + "-" + upper + "(" + len + ")");

      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("de-DEEP(4)");
    }

    @Test
    @DisplayName("Arity 10: should chain ten focus operations into arity 11")
    void arity10Focus() {
      Level1 data = new Level1(new Level2(new Level3(new Level4("deep"))));

      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(data))
              .focus(l1l2)
              .focus(t -> l2l3.get(t._2()))
              .focus(t -> l3l4.get(t._3()))
              .focus(t -> l4val.get(t._4()))
              .focus(t -> t._5().length())
              .focus(t -> t._5().toUpperCase())
              .focus(t -> t._7().charAt(0))
              .focus(t -> t._5().charAt(1))
              .focus(t -> t._5().substring(0, 2))
              .focus(t -> t._10().length())
              .yield(
                  (l1, l2, l3, l4, val, len, upper, ch, ch2, sub, subLen) ->
                      sub + "[" + subLen + "]=" + upper);

      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("de[2]=DEEP");
    }

    @Test
    @DisplayName("Arity 11: should chain eleven focus operations into arity 12")
    void arity11Focus() {
      Level1 data = new Level1(new Level2(new Level3(new Level4("deep"))));

      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(data))
              .focus(l1l2)
              .focus(t -> l2l3.get(t._2()))
              .focus(t -> l3l4.get(t._3()))
              .focus(t -> l4val.get(t._4()))
              .focus(t -> t._5().length())
              .focus(t -> t._5().toUpperCase())
              .focus(t -> t._7().charAt(0))
              .focus(t -> t._5().charAt(1))
              .focus(t -> t._5().substring(0, 2))
              .focus(t -> t._10().length())
              .focus(t -> t._5().contains("ee"))
              .yield(
                  (l1, l2, l3, l4, val, len, upper, ch, ch2, sub, subLen, hasEE) ->
                      upper + ":" + hasEE + ":" + subLen);

      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("DEEP:true:2");
    }
  }

  // --- Higher Arity match() Tests ---

  @Nested
  @DisplayName("match() at Higher Arities")
  class MatchHigherArityTest {
    private final ListMonad listMonad = ListMonad.INSTANCE;

    sealed interface Outer permits OuterA, OuterB {}

    record OuterA(Inner inner) implements Outer {}

    record OuterB() implements Outer {}

    sealed interface Inner permits InnerX, InnerY {}

    record InnerX(String value) implements Inner {}

    record InnerY() implements Inner {}

    private Prism<Outer, OuterA> outerAPrism;
    private Prism<Inner, InnerX> innerXPrism;

    @BeforeEach
    void setUp() {
      outerAPrism =
          Prism.of(o -> o instanceof OuterA a ? Optional.of(a) : Optional.empty(), a -> a);
      innerXPrism =
          Prism.of(i -> i instanceof InnerX x ? Optional.of(x) : Optional.empty(), x -> x);
    }

    @Test
    @DisplayName("Arity 3: should chain two match operations")
    void arity3NestedMatch() {
      List<Outer> outers =
          List.of(new OuterA(new InnerX("found")), new OuterA(new InnerY()), new OuterB());

      Kind<ListKind.Witness, String> result =
          For.from(listMonad, LIST.widen(outers))
              .match(outerAPrism)
              .match(t -> innerXPrism.getOptional(t._2().inner()))
              .yield((o, a, x) -> x.value());

      assertThat(LIST.narrow(result)).containsExactly("found");
    }
  }

  // --- FilterableSteps2 Comprehensive Tests ---

  @Nested
  @DisplayName("FilterableSteps2 focus() and match() Tests")
  class FilterableSteps2Test {
    private final ListMonad listMonad = ListMonad.INSTANCE;

    record Item(String name, int value) {}

    @Test
    @DisplayName("focus() should extract value from tuple and add to result")
    void focusExtractsFromTuple() {
      List<String> names = List.of("Alice", "Bob");

      Kind<ListKind.Witness, String> result =
          For.from(listMonad, LIST.widen(names))
              .from(name -> LIST.widen(List.of(name.length())))
              .focus(t -> t._1().toUpperCase() + ":" + t._2())
              .yield((name, len, formatted) -> formatted);

      assertThat(LIST.narrow(result)).containsExactly("ALICE:5", "BOB:3");
    }

    @Test
    @DisplayName("focus() should throw NullPointerException when extractor is null")
    void focusThrowsOnNullExtractor() {
      assertThatThrownBy(
              () ->
                  For.from(listMonad, LIST.widen(List.of("test")))
                      .from(s -> LIST.widen(List.of(1)))
                      .focus(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("extractor");
    }

    @Test
    @DisplayName("match() should filter elements based on optional result")
    void matchFiltersBasedOnOptional() {
      List<Item> items =
          List.of(new Item("apple", 10), new Item("banana", -5), new Item("cherry", 20));

      Kind<ListKind.Witness, String> result =
          For.from(listMonad, LIST.widen(items))
              .from(item -> LIST.widen(List.of(item.value())))
              .match(t -> t._2() > 0 ? Optional.of(t._2() * 2) : Optional.empty())
              .yield((item, val, doubled) -> item.name() + "=" + doubled);

      assertThat(LIST.narrow(result)).containsExactly("apple=20", "cherry=40");
    }

    @Test
    @DisplayName("match() should throw NullPointerException when matcher is null")
    void matchThrowsOnNullMatcher() {
      assertThatThrownBy(
              () ->
                  For.from(listMonad, LIST.widen(List.of("test")))
                      .from(s -> LIST.widen(List.of(1)))
                      .match(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("matcher");
    }

    @Test
    @DisplayName("match() should short-circuit to empty when all optionals are empty")
    void matchShortCircuitsOnAllEmpty() {
      List<Integer> numbers = List.of(1, 2, 3);

      Kind<ListKind.Witness, String> result =
          For.from(listMonad, LIST.widen(numbers))
              .from(n -> LIST.widen(List.of(n * 10)))
              .match(t -> Optional.<String>empty()) // Always empty
              .yield((n, times10, matched) -> "never reached");

      assertThat(LIST.narrow(result)).isEmpty();
    }

    @Test
    @DisplayName("focus() should chain with subsequent operations")
    void focusChainsWithSubsequentOps() {
      List<String> words = List.of("hello", "world");

      Kind<ListKind.Witness, Integer> result =
          For.from(listMonad, LIST.widen(words))
              .from(w -> LIST.widen(List.of(w.length())))
              .focus(t -> t._1().charAt(0))
              .when(t -> t._3() == 'h')
              .yield((word, len, firstChar) -> len);

      assertThat(LIST.narrow(result)).containsExactly(5);
    }
  }

  // --- FilterableSteps3 Comprehensive Tests ---

  @Nested
  @DisplayName("FilterableSteps3 focus() and match() Tests")
  class FilterableSteps3Test {
    private final ListMonad listMonad = ListMonad.INSTANCE;

    record Product(String name, String category, int price) {}

    @Test
    @DisplayName("focus() should extract value from 3-tuple and add to result")
    void focusExtractsFromTuple3() {
      List<Product> products = List.of(new Product("Laptop", "Electronics", 1000));

      Kind<ListKind.Witness, String> result =
          For.from(listMonad, LIST.widen(products))
              .from(p -> LIST.widen(List.of(p.category())))
              .from(t -> LIST.widen(List.of(t._1().price())))
              .focus(t -> t._1().name() + " in " + t._2() + " costs $" + t._3())
              .yield((prod, cat, price, summary) -> summary);

      assertThat(LIST.narrow(result)).containsExactly("Laptop in Electronics costs $1000");
    }

    @Test
    @DisplayName("focus() should throw NullPointerException when extractor is null")
    void focusThrowsOnNullExtractor() {
      assertThatThrownBy(
              () ->
                  For.from(listMonad, LIST.widen(List.of("a")))
                      .from(s -> LIST.widen(List.of(1)))
                      .from(t -> LIST.widen(List.of(2)))
                      .focus(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("extractor");
    }

    @Test
    @DisplayName("match() should filter based on optional from 3-tuple")
    void matchFiltersFromTuple3() {
      List<Integer> xs = List.of(1, 2, 3);

      Kind<ListKind.Witness, Integer> result =
          For.from(listMonad, LIST.widen(xs))
              .from(x -> LIST.widen(List.of(x * 2)))
              .from(t -> LIST.widen(List.of(t._2() + 1)))
              .match(t -> t._3() > 5 ? Optional.of(t._1() + t._2() + t._3()) : Optional.empty())
              .yield((x, doubled, plusOne, sum) -> sum);

      // x=2: doubled=4, plusOne=5, 5 > 5 is false -> filtered
      // x=3: doubled=6, plusOne=7, 7 > 5 is true -> sum = 3+6+7 = 16
      assertThat(LIST.narrow(result)).containsExactly(16);
    }

    @Test
    @DisplayName("match() should throw NullPointerException when matcher is null")
    void matchThrowsOnNullMatcher() {
      assertThatThrownBy(
              () ->
                  For.from(listMonad, LIST.widen(List.of("a")))
                      .from(s -> LIST.widen(List.of(1)))
                      .from(t -> LIST.widen(List.of(2)))
                      .match(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("matcher");
    }

    @Test
    @DisplayName("match() should chain with when for combined filtering")
    void matchChainsWithWhen() {
      List<String> strings = List.of("abc", "defgh", "ij");

      Kind<ListKind.Witness, String> result =
          For.from(listMonad, LIST.widen(strings))
              .from(s -> LIST.widen(List.of(s.length())))
              .from(t -> LIST.widen(List.of(t._1().toUpperCase())))
              .match(t -> t._2() >= 3 ? Optional.of(t._3()) : Optional.empty())
              .when(t -> t._4().startsWith("D"))
              .yield((s, len, upper, matched) -> matched);

      assertThat(LIST.narrow(result)).containsExactly("DEFGH");
    }
  }

  // --- FilterableSteps4 Comprehensive Tests ---

  @Nested
  @DisplayName("FilterableSteps4 focus() and match() Tests")
  class FilterableSteps4Test {
    private final ListMonad listMonad = ListMonad.INSTANCE;

    @Test
    @DisplayName("focus() should extract value from 4-tuple and add to result")
    void focusExtractsFromTuple4() {
      List<Integer> nums = List.of(2);

      Kind<ListKind.Witness, String> result =
          For.from(listMonad, LIST.widen(nums))
              .from(n -> LIST.widen(List.of(n * 2)))
              .from(t -> LIST.widen(List.of(t._2() * 2)))
              .from(t -> LIST.widen(List.of(t._3() * 2)))
              .focus(t -> t._1() + "-" + t._2() + "-" + t._3() + "-" + t._4())
              .yield((a, b, c, d, chain) -> chain);

      assertThat(LIST.narrow(result)).containsExactly("2-4-8-16");
    }

    @Test
    @DisplayName("focus() should throw NullPointerException when extractor is null")
    void focusThrowsOnNullExtractor() {
      assertThatThrownBy(
              () ->
                  For.from(listMonad, LIST.widen(List.of(1)))
                      .from(n -> LIST.widen(List.of(2)))
                      .from(t -> LIST.widen(List.of(3)))
                      .from(t -> LIST.widen(List.of(4)))
                      .focus(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("extractor");
    }

    @Test
    @DisplayName("match() should filter based on optional from 4-tuple")
    void matchFiltersFromTuple4() {
      List<Integer> xs = List.of(1, 2, 3, 4);

      Kind<ListKind.Witness, Integer> result =
          For.from(listMonad, LIST.widen(xs))
              .from(x -> LIST.widen(List.of(x + 1)))
              .from(t -> LIST.widen(List.of(t._2() + 1)))
              .from(t -> LIST.widen(List.of(t._3() + 1)))
              .match(
                  t ->
                      (t._1() + t._2() + t._3() + t._4()) > 15
                          ? Optional.of(t._4())
                          : Optional.empty())
              .yield((a, b, c, d, matched) -> matched);

      // x=3: 3+4+5+6 = 18 > 15 -> matched = 6
      // x=4: 4+5+6+7 = 22 > 15 -> matched = 7
      assertThat(LIST.narrow(result)).containsExactly(6, 7);
    }

    @Test
    @DisplayName("match() should throw NullPointerException when matcher is null")
    void matchThrowsOnNullMatcher() {
      assertThatThrownBy(
              () ->
                  For.from(listMonad, LIST.widen(List.of(1)))
                      .from(n -> LIST.widen(List.of(2)))
                      .from(t -> LIST.widen(List.of(3)))
                      .from(t -> LIST.widen(List.of(4)))
                      .match(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("matcher");
    }

    @Test
    @DisplayName("match() should short-circuit to empty when optional is empty")
    void matchShortCircuitsOnEmpty() {
      List<String> items = List.of("a", "b", "c");

      Kind<ListKind.Witness, String> result =
          For.from(listMonad, LIST.widen(items))
              .from(s -> LIST.widen(List.of(s + "1")))
              .from(t -> LIST.widen(List.of(t._2() + "2")))
              .from(t -> LIST.widen(List.of(t._3() + "3")))
              .match(t -> Optional.<String>empty())
              .yield((a, b, c, d, matched) -> "never reached");

      assertThat(LIST.narrow(result)).isEmpty();
    }

    @Test
    @DisplayName("focus() should work correctly with subsequent when filter")
    void focusWithSubsequentWhen() {
      List<Integer> nums = List.of(1, 2, 3);

      Kind<ListKind.Witness, Integer> result =
          For.from(listMonad, LIST.widen(nums))
              .from(n -> LIST.widen(List.of(n * 10)))
              .from(t -> LIST.widen(List.of(t._2() + 5)))
              .from(t -> LIST.widen(List.of(t._3() - 2)))
              .focus(t -> t._1() + t._2() + t._3() + t._4())
              .when(t -> t._5() > 50)
              .yield((a, b, c, d, sum) -> sum);

      // n=1: 1 + 10 + 15 + 13 = 39 <= 50 -> filtered
      // n=2: 2 + 20 + 25 + 23 = 70 > 50 -> included
      // n=3: 3 + 30 + 35 + 33 = 101 > 50 -> included
      assertThat(LIST.narrow(result)).containsExactly(70, 101);
    }

    @Test
    @DisplayName("match() should chain correctly with let and yield")
    void matchChainsWithLetAndYield() {
      List<Integer> nums = List.of(5, 10, 15);

      Kind<ListKind.Witness, String> result =
          For.from(listMonad, LIST.widen(nums))
              .from(n -> LIST.widen(List.of(n - 3)))
              .from(t -> LIST.widen(List.of(t._1() + t._2())))
              .from(t -> LIST.widen(List.of("val")))
              .match(t -> t._3() > 10 ? Optional.of(t._3() * 2) : Optional.empty())
              .yield((a, b, c, d, doubled) -> "Result: " + doubled);

      // n=5: sum = 5 + 2 = 7 <= 10 -> filtered
      // n=10: sum = 10 + 7 = 17 > 10 -> doubled = 34
      // n=15: sum = 15 + 12 = 27 > 10 -> doubled = 54
      assertThat(LIST.narrow(result)).containsExactly("Result: 34", "Result: 54");
    }
  }

  // --- FilterableSteps5 Comprehensive Tests ---

  @Nested
  @DisplayName("FilterableSteps5 focus() and match() Tests")
  class FilterableSteps5Test {
    private final ListMonad listMonad = ListMonad.INSTANCE;

    @Test
    @DisplayName("focus() should extract value from 5-tuple into arity 6")
    void focusExtractsFromTuple5() {
      List<Integer> nums = List.of(1);

      Kind<ListKind.Witness, String> result =
          For.from(listMonad, LIST.widen(nums))
              .from(n -> LIST.widen(List.of(n * 2)))
              .from(t -> LIST.widen(List.of(t._2() * 3)))
              .from(t -> LIST.widen(List.of(t._3() * 4)))
              .from(t -> LIST.widen(List.of(t._4() * 5)))
              .focus(t -> t._1() + t._2() + t._3() + t._4() + t._5())
              .yield((a, b, c, d, e, sum) -> "sum=" + sum);

      // 1 + 2 + 6 + 24 + 120 = 153
      assertThat(LIST.narrow(result)).containsExactly("sum=153");
    }

    @Test
    @DisplayName("match() should filter based on optional from 5-tuple into arity 6")
    void matchFiltersFromTuple5() {
      List<Integer> xs = List.of(1, 2, 3);

      Kind<ListKind.Witness, Integer> result =
          For.from(listMonad, LIST.widen(xs))
              .from(x -> LIST.widen(List.of(x * 10)))
              .from(t -> LIST.widen(List.of(t._1() + t._2())))
              .from(t -> LIST.widen(List.of(0)))
              .from(t -> LIST.widen(List.of(0)))
              .match(t -> t._3() > 20 ? Optional.of(t._3()) : Optional.empty())
              .yield((a, b, c, d, e, matched) -> matched);

      // x=1: sum=1+10=11, 11 > 20 false -> filtered
      // x=2: sum=2+20=22, 22 > 20 true -> matched=22
      // x=3: sum=3+30=33, 33 > 20 true -> matched=33
      assertThat(LIST.narrow(result)).containsExactly(22, 33);
    }

    @Test
    @DisplayName("focus() should work with subsequent when filter at arity 6")
    void focusWithWhenAtArity6() {
      List<Integer> nums = List.of(1, 2, 3);

      Kind<ListKind.Witness, Integer> result =
          For.from(listMonad, LIST.widen(nums))
              .from(n -> LIST.widen(List.of(n * 10)))
              .from(t -> LIST.widen(List.of(t._2() + 5)))
              .from(t -> LIST.widen(List.of(0)))
              .from(t -> LIST.widen(List.of(0)))
              .focus(t -> t._1() + t._2() + t._3())
              .when(t -> t._6() > 30)
              .yield((a, b, c, d, e, sum) -> sum);

      // n=1: sum=1+10+15=26, 26>30 false -> filtered
      // n=2: sum=2+20+25=47, 47>30 true -> included
      // n=3: sum=3+30+35=68, 68>30 true -> included
      assertThat(LIST.narrow(result)).containsExactly(47, 68);
    }
  }

  // --- FilterableSteps6 Comprehensive Tests ---

  @Nested
  @DisplayName("FilterableSteps6 focus() and match() Tests")
  class FilterableSteps6Test {
    private final ListMonad listMonad = ListMonad.INSTANCE;

    @Test
    @DisplayName("focus() should extract value from 6-tuple into arity 7")
    void focusExtractsFromTuple6() {
      List<Integer> nums = List.of(1);

      Kind<ListKind.Witness, String> result =
          For.from(listMonad, LIST.widen(nums))
              .from(n -> LIST.widen(List.of(2)))
              .from(t -> LIST.widen(List.of(3)))
              .from(t -> LIST.widen(List.of(4)))
              .from(t -> LIST.widen(List.of(5)))
              .from(t -> LIST.widen(List.of(6)))
              .focus(t -> "" + t._1() + t._2() + t._3() + t._4() + t._5() + t._6())
              .yield((a, b, c, d, e, f, concat) -> concat);

      assertThat(LIST.narrow(result)).containsExactly("123456");
    }

    @Test
    @DisplayName("match() should filter based on optional from 6-tuple into arity 7")
    void matchFiltersFromTuple6() {
      List<Integer> xs = List.of(1, 2, 3);

      Kind<ListKind.Witness, Integer> result =
          For.from(listMonad, LIST.widen(xs))
              .from(x -> LIST.widen(List.of(x * 2)))
              .from(t -> LIST.widen(List.of(0)))
              .from(t -> LIST.widen(List.of(0)))
              .from(t -> LIST.widen(List.of(0)))
              .from(t -> LIST.widen(List.of(0)))
              .match(t -> t._2() > 3 ? Optional.of(t._2()) : Optional.empty())
              .yield((a, b, c, d, e, f, matched) -> matched);

      // x=1: x*2=2, 2>3 false -> filtered
      // x=2: x*2=4, 4>3 true -> matched=4
      // x=3: x*2=6, 6>3 true -> matched=6
      assertThat(LIST.narrow(result)).containsExactly(4, 6);
    }

    @Test
    @DisplayName("focus() should chain with when at arity 7")
    void focusWithWhenAtArity7() {
      List<String> words = List.of("short", "longer word");

      Kind<ListKind.Witness, String> result =
          For.from(listMonad, LIST.widen(words))
              .from(w -> LIST.widen(List.of(w.length())))
              .from(t -> LIST.widen(List.of(0)))
              .from(t -> LIST.widen(List.of(0)))
              .from(t -> LIST.widen(List.of(0)))
              .from(t -> LIST.widen(List.of(0)))
              .focus(t -> t._1().toUpperCase())
              .when(t -> t._2() > 5)
              .yield((w, len, c, d, e, f, upper) -> upper);

      assertThat(LIST.narrow(result)).containsExactly("LONGER WORD");
    }
  }

  // --- FilterableSteps7 Comprehensive Tests ---

  @Nested
  @DisplayName("FilterableSteps7 focus() and match() Tests")
  class FilterableSteps7Test {
    private final ListMonad listMonad = ListMonad.INSTANCE;

    @Test
    @DisplayName("focus() should extract value from 7-tuple into arity 8")
    void focusExtractsFromTuple7() {
      List<Integer> nums = List.of(1);

      Kind<ListKind.Witness, String> result =
          For.from(listMonad, LIST.widen(nums))
              .from(n -> LIST.widen(List.of(2)))
              .from(t -> LIST.widen(List.of(3)))
              .from(t -> LIST.widen(List.of(4)))
              .from(t -> LIST.widen(List.of(5)))
              .from(t -> LIST.widen(List.of(6)))
              .from(t -> LIST.widen(List.of(7)))
              .focus(t -> t._1() + t._2() + t._3() + t._4() + t._5() + t._6() + t._7())
              .yield((a, b, c, d, e, f, g, sum) -> "sum=" + sum);

      assertThat(LIST.narrow(result)).containsExactly("sum=28");
    }

    @Test
    @DisplayName("match() should filter based on optional from 7-tuple into arity 8")
    void matchFiltersFromTuple7() {
      List<Integer> xs = List.of(1, 2, 3);

      Kind<ListKind.Witness, Integer> result =
          For.from(listMonad, LIST.widen(xs))
              .from(x -> LIST.widen(List.of(x * 3)))
              .from(t -> LIST.widen(List.of(0)))
              .from(t -> LIST.widen(List.of(0)))
              .from(t -> LIST.widen(List.of(0)))
              .from(t -> LIST.widen(List.of(0)))
              .from(t -> LIST.widen(List.of(0)))
              .match(t -> t._2() > 5 ? Optional.of(t._2()) : Optional.empty())
              .yield((a, b, c, d, e, f, g, matched) -> matched);

      // x=1: x*3=3, 3>5 false -> filtered
      // x=2: x*3=6, 6>5 true -> matched=6
      // x=3: x*3=9, 9>5 true -> matched=9
      assertThat(LIST.narrow(result)).containsExactly(6, 9);
    }
  }
}
