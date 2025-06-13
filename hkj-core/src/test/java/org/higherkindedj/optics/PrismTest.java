// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListKindHelper;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.optics.util.Traversals; // Assuming you have this utility class
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Prism<S, A> Tests")
class PrismTest {

  // A sealed interface representing a simple JSON value (a sum type)
  sealed interface Json permits JsonString, JsonNumber {}

  record JsonString(String value) implements Json {}

  record JsonNumber(int value) implements Json {}

  private Prism<Json, String> jsonStringPrism;

  @BeforeEach
  void setUp() {
    jsonStringPrism =
        Prism.of(
            json -> {
              if (json instanceof JsonString s) {
                return Optional.of(s.value());
              }
              return Optional.empty();
            },
            JsonString::new);
  }

  @Nested
  @DisplayName("Core Functionality")
  class CoreFunctionality {
    @Test
    @DisplayName("of should create a Prism instance")
    void of_shouldCreatePrism() {
      assertThat(jsonStringPrism).isNotNull();
    }

    @Test
    @DisplayName("getOptional should return value when prism matches")
    void getOptional_shouldReturnValueOnMatch() {
      Json json = new JsonString("hello");
      Optional<String> result = jsonStringPrism.getOptional(json);
      assertThat(result).isPresent().contains("hello");
    }

    @Test
    @DisplayName("getOptional should return empty when prism does not match")
    void getOptional_shouldReturnEmptyOnMismatch() {
      Json json = new JsonNumber(42);
      Optional<String> result = jsonStringPrism.getOptional(json);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("build should construct the outer type from a value")
    void build_shouldConstructType() {
      Json result = jsonStringPrism.build("world"); // Renamed from reverseGet
      assertThat(result).isEqualTo(new JsonString("world"));
    }
  }

  @Nested
  @DisplayName("Composition and Conversion")
  class CompositionAndConversion {
    // A second sealed interface for composition testing
    sealed interface Result permits Success, SuccessWithList, Failure {}

    record Success(Json value) implements Result {}

    record SuccessWithList(List<String> values) implements Result {}

    record Failure(String error) implements Result {}

    private Prism<Result, Json> successPrism;

    @BeforeEach
    void setup() {
      successPrism =
          Prism.of(
              result -> {
                if (result instanceof Success s) {
                  return Optional.of(s.value());
                }
                return Optional.empty();
              },
              Success::new);
    }

    // Helper to create a Traversal for any List.
    private <T> Traversal<List<T>, T> listElements() {
      return new Traversal<>() {
        @Override
        public <F> Kind<F, List<T>> modifyF(
            Function<T, Kind<F, T>> f, List<T> source, Applicative<F> applicative) {
          Kind<F, Kind<ListKind.Witness, T>> traversed =
              ListTraverse.INSTANCE.traverse(applicative, ListKindHelper.LIST.widen(source), f);
          return applicative.map(ListKindHelper.LIST::narrow, traversed);
        }
      };
    }

    @Test
    @DisplayName("andThen(Prism) should compose prisms correctly")
    void andThen_withPrism() {
      Prism<Result, String> resultToStringPrism = successPrism.andThen(jsonStringPrism);

      assertThat(resultToStringPrism.getOptional(new Success(new JsonString("data"))))
          .isPresent()
          .contains("data");
      assertThat(resultToStringPrism.getOptional(new Success(new JsonNumber(1)))).isEmpty();
      assertThat(resultToStringPrism.getOptional(new Failure("error"))).isEmpty();

      Result builtResult = resultToStringPrism.build("new data");
      assertThat(builtResult).isEqualTo(new Success(new JsonString("new data")));
    }

    @Test
    @DisplayName("andThen(Traversal) should compose Prism and Traversal")
    void andThen_withTraversal() {
      Prism<Result, List<String>> listPrism =
          Prism.of(
              result ->
                  (result instanceof SuccessWithList s)
                      ? Optional.of(s.values())
                      : Optional.empty(),
              SuccessWithList::new);
      Traversal<List<String>, String> elementTraversal = listElements();

      // FIX: Start with .asTraversal() to maintain the Traversal type
      Traversal<Result, String> composedTraversal =
          listPrism.asTraversal().andThen(elementTraversal);

      Result source = new SuccessWithList(List.of("a", "b", "c"));
      Result modified = Traversals.modify(composedTraversal, String::toUpperCase, source);
      assertThat(modified).isEqualTo(new SuccessWithList(List.of("A", "B", "C")));

      Result failedSource = new Failure("error");
      Result unmodified = Traversals.modify(composedTraversal, String::toUpperCase, failedSource);
      assertThat(unmodified).isSameAs(failedSource);
    }

    @Test
    @DisplayName("andThen(Lens) should compose Prism and Lens into a Traversal")
    void andThen_withLens() {
      // Note: This Lens is unsafe, but fine for this specific test case.
      Lens<Json, String> jsonStringLens =
          Lens.of(
              json -> (json instanceof JsonString s) ? s.value() : "",
              (json, newValue) -> new JsonString(newValue));

      // FIX: Start with .asTraversal() and convert the lens to a traversal
      Traversal<Result, String> composed =
          successPrism.asTraversal().andThen(jsonStringLens.asTraversal());

      Result source = new Success(new JsonString("hello"));
      Result modified = Traversals.modify(composed, String::toUpperCase, source);
      assertThat(modified).isEqualTo(new Success(new JsonString("HELLO")));

      Result failedSource = new Failure("error");
      Result unmodified = Traversals.modify(composed, String::toUpperCase, failedSource);
      assertThat(unmodified).isSameAs(failedSource);
    }

    @Test
    @DisplayName("asTraversal should create a working Traversal for both success and failure paths")
    void asTraversal_shouldWork() {
      Traversal<Json, String> traversal = jsonStringPrism.asTraversal();

      Json successSource = new JsonString("test");
      Json modified = Traversals.modify(traversal, String::toUpperCase, successSource);
      assertThat(modified).isEqualTo(new JsonString("TEST"));

      Json failureSource = new JsonNumber(123);
      Json unmodified = Traversals.modify(traversal, String::toUpperCase, failureSource);
      assertThat(unmodified).isSameAs(failureSource);
    }
  }
}
