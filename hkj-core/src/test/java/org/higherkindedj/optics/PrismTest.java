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
                if (result instanceof Success(Json value)) {
                  return Optional.of(value);
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
              ListTraverse.INSTANCE.traverse(applicative, f, ListKindHelper.LIST.widen(source));
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

      // Direct composition: Prism >>> Lens = Traversal
      Traversal<Result, String> composed = successPrism.andThen(jsonStringLens);

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

  @Nested
  @DisplayName("Convenience Methods")
  class ConvenienceMethods {
    @Test
    @DisplayName("matches should return true when prism matches")
    void matches_shouldReturnTrueOnMatch() {
      Json json = new JsonString("hello");
      assertThat(jsonStringPrism.matches(json)).isTrue();
    }

    @Test
    @DisplayName("matches should return false when prism does not match")
    void matches_shouldReturnFalseOnMismatch() {
      Json json = new JsonNumber(42);
      assertThat(jsonStringPrism.matches(json)).isFalse();
    }

    @Test
    @DisplayName("doesNotMatch should return true when prism does not match")
    void doesNotMatch_shouldReturnTrueOnMismatch() {
      Json json = new JsonNumber(42);
      assertThat(jsonStringPrism.doesNotMatch(json)).isTrue();
    }

    @Test
    @DisplayName("doesNotMatch should return false when prism matches")
    void doesNotMatch_shouldReturnFalseOnMatch() {
      Json json = new JsonString("hello");
      assertThat(jsonStringPrism.doesNotMatch(json)).isFalse();
    }

    @Test
    @DisplayName("doesNotMatch should be the negation of matches")
    void doesNotMatch_shouldBeNegationOfMatches() {
      List<Json> testCases =
          List.of(
              new JsonString("hello"),
              new JsonString(""),
              new JsonNumber(42),
              new JsonNumber(0),
              new JsonNumber(-1));

      for (Json testCase : testCases) {
        assertThat(jsonStringPrism.doesNotMatch(testCase))
            .isEqualTo(!jsonStringPrism.matches(testCase));
      }
    }

    @Test
    @DisplayName("getOrElse should return value when prism matches")
    void getOrElse_shouldReturnValueOnMatch() {
      Json json = new JsonString("hello");
      String result = jsonStringPrism.getOrElse("default", json);
      assertThat(result).isEqualTo("hello");
    }

    @Test
    @DisplayName("getOrElse should return default when prism does not match")
    void getOrElse_shouldReturnDefaultOnMismatch() {
      Json json = new JsonNumber(42);
      String result = jsonStringPrism.getOrElse("default", json);
      assertThat(result).isEqualTo("default");
    }

    @Test
    @DisplayName("mapOptional should transform matched value")
    void mapOptional_shouldTransformOnMatch() {
      Json json = new JsonString("hello");
      Optional<Integer> result = jsonStringPrism.mapOptional(String::length, json);
      assertThat(result).isPresent().contains(5);
    }

    @Test
    @DisplayName("mapOptional should return empty when prism does not match")
    void mapOptional_shouldReturnEmptyOnMismatch() {
      Json json = new JsonNumber(42);
      Optional<Integer> result = jsonStringPrism.mapOptional(String::length, json);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("modify should modify value when prism matches")
    void modify_shouldModifyOnMatch() {
      Json json = new JsonString("hello");
      Json result = jsonStringPrism.modify(String::toUpperCase, json);
      assertThat(result).isEqualTo(new JsonString("HELLO"));
    }

    @Test
    @DisplayName("modify should return original when prism does not match")
    void modify_shouldReturnOriginalOnMismatch() {
      Json json = new JsonNumber(42);
      Json result = jsonStringPrism.modify(String::toUpperCase, json);
      assertThat(result).isSameAs(json);
    }

    @Test
    @DisplayName("modifyWhen should modify when both prism matches and condition is met")
    void modifyWhen_shouldModifyWhenConditionMet() {
      Json json = new JsonString("hello");
      Json result = jsonStringPrism.modifyWhen(s -> s.length() > 3, String::toUpperCase, json);
      assertThat(result).isEqualTo(new JsonString("HELLO"));
    }

    @Test
    @DisplayName("modifyWhen should not modify when prism matches but condition is not met")
    void modifyWhen_shouldNotModifyWhenConditionNotMet() {
      Json json = new JsonString("hi");
      Json result = jsonStringPrism.modifyWhen(s -> s.length() > 3, String::toUpperCase, json);
      assertThat(result).isSameAs(json);
    }

    @Test
    @DisplayName("modifyWhen should not modify when prism does not match")
    void modifyWhen_shouldNotModifyWhenPrismDoesNotMatch() {
      Json json = new JsonNumber(42);
      Json result = jsonStringPrism.modifyWhen(s -> s.length() > 3, String::toUpperCase, json);
      assertThat(result).isSameAs(json);
    }

    @Test
    @DisplayName("setWhen should set value when both prism matches and condition is met")
    void setWhen_shouldSetWhenConditionMet() {
      Json json = new JsonString("old");
      Json result = jsonStringPrism.setWhen(s -> !s.isEmpty(), "new", json);
      assertThat(result).isEqualTo(new JsonString("new"));
    }

    @Test
    @DisplayName("setWhen should not set when prism matches but condition is not met")
    void setWhen_shouldNotSetWhenConditionNotMet() {
      Json json = new JsonString("");
      Json result = jsonStringPrism.setWhen(s -> !s.isEmpty(), "new", json);
      assertThat(result).isSameAs(json);
    }

    @Test
    @DisplayName("setWhen should not set when prism does not match")
    void setWhen_shouldNotSetWhenPrismDoesNotMatch() {
      Json json = new JsonNumber(42);
      Json result = jsonStringPrism.setWhen(s -> !s.isEmpty(), "new", json);
      assertThat(result).isSameAs(json);
    }

    @Test
    @DisplayName("orElse should use first prism when it matches")
    void orElse_shouldUseFirstPrismOnMatch() {
      Prism<Json, String> alternative =
          Prism.of(json -> Optional.of("alternative"), s -> new JsonString("alt:" + s));
      Prism<Json, String> combined = jsonStringPrism.orElse(alternative);

      Json json = new JsonString("hello");
      Optional<String> result = combined.getOptional(json);
      assertThat(result).isPresent().contains("hello");
    }

    @Test
    @DisplayName("orElse should use second prism when first does not match")
    void orElse_shouldUseSecondPrismOnMismatch() {
      Prism<Json, String> alternative =
          Prism.of(
              json ->
                  (json instanceof JsonNumber n)
                      ? Optional.of(String.valueOf(n.value()))
                      : Optional.empty(),
              s -> new JsonNumber(Integer.parseInt(s)));
      Prism<Json, String> combined = jsonStringPrism.orElse(alternative);

      Json json = new JsonNumber(42);
      Optional<String> result = combined.getOptional(json);
      assertThat(result).isPresent().contains("42");
    }

    @Test
    @DisplayName("orElse should use first prism's build method")
    void orElse_shouldUseFirstPrismBuild() {
      Prism<Json, String> alternative =
          Prism.of(
              json ->
                  (json instanceof JsonNumber n)
                      ? Optional.of(String.valueOf(n.value()))
                      : Optional.empty(),
              s -> new JsonNumber(Integer.parseInt(s)));
      Prism<Json, String> combined = jsonStringPrism.orElse(alternative);

      Json result = combined.build("test");
      assertThat(result).isEqualTo(new JsonString("test"));
    }
  }

  @Nested
  @DisplayName("Extended Composition Methods")
  class ExtendedComposition {

    // Types for andThenTraversal test
    record Container(List<String> items) {}

    sealed interface Value permits StringValue, ContainerValue {}

    record StringValue(String s) implements Value {}

    record ContainerValue(Container container) implements Value {}

    @Test
    @DisplayName("andThen(Iso) should compose Prism with Iso to produce Prism")
    void andThenIso() {
      record StringWrapper(String value) {}

      Iso<String, StringWrapper> wrapperIso = Iso.of(StringWrapper::new, StringWrapper::value);

      // Compose: Prism >>> Iso = Prism
      Prism<Json, StringWrapper> composed = jsonStringPrism.andThen(wrapperIso);

      // Test matching case
      Json stringJson = new JsonString("hello");
      Optional<StringWrapper> result = composed.getOptional(stringJson);
      assertThat(result).isPresent();
      assertThat(result.get().value()).isEqualTo("hello");

      // Test non-matching case
      Json numberJson = new JsonNumber(42);
      assertThat(composed.getOptional(numberJson)).isEmpty();

      // Test build
      Json built = composed.build(new StringWrapper("world"));
      assertThat(built).isEqualTo(new JsonString("world"));

      // Test modify
      Json modified = composed.modify(w -> new StringWrapper(w.value().toUpperCase()), stringJson);
      assertThat(modified).isEqualTo(new JsonString("HELLO"));

      // Test modify on non-matching returns original
      Json notModified =
          composed.modify(w -> new StringWrapper(w.value().toUpperCase()), numberJson);
      assertThat(notModified).isSameAs(numberJson);
    }

    @Test
    @DisplayName("andThen(Traversal) should compose Prism with Traversal to produce Traversal")
    void andThenTraversal() {
      Prism<Value, Container> containerPrism =
          Prism.of(
              v -> v instanceof ContainerValue cv ? Optional.of(cv.container()) : Optional.empty(),
              ContainerValue::new);

      Traversal<Container, String> itemsTraversal =
          new Traversal<>() {
            @Override
            public <F> Kind<F, Container> modifyF(
                Function<String, Kind<F, String>> f, Container source, Applicative<F> app) {
              Kind<F, Kind<ListKind.Witness, String>> traversed =
                  ListTraverse.INSTANCE.traverse(app, f, ListKindHelper.LIST.widen(source.items()));
              return app.map(
                  listKind -> new Container(ListKindHelper.LIST.narrow(listKind)), traversed);
            }
          };

      // Compose: Prism >>> Traversal = Traversal
      Traversal<Value, String> composed = containerPrism.andThen(itemsTraversal);

      // Test with matching prism
      Value containerValue = new ContainerValue(new Container(List.of("a", "b", "c")));
      List<String> items = Traversals.getAll(composed, containerValue);
      assertThat(items).containsExactly("a", "b", "c");

      Value modified = Traversals.modify(composed, String::toUpperCase, containerValue);
      assertThat(modified).isEqualTo(new ContainerValue(new Container(List.of("A", "B", "C"))));

      // Test with non-matching prism
      Value stringValue = new StringValue("hello");
      List<String> emptyItems = Traversals.getAll(composed, stringValue);
      assertThat(emptyItems).isEmpty();

      Value notModified = Traversals.modify(composed, String::toUpperCase, stringValue);
      assertThat(notModified).isSameAs(stringValue);

      // Test with empty container
      Value emptyContainer = new ContainerValue(new Container(List.of()));
      List<String> noItems = Traversals.getAll(composed, emptyContainer);
      assertThat(noItems).isEmpty();
    }
  }
}
