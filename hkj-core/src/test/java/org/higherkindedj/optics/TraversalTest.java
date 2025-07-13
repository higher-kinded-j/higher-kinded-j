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
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Traversal<S, A> Tests")
class TraversalTest {

  // Test Data Structures from LensTest
  record Street(String name) {}

  sealed interface Json permits JsonString, JsonNumber {}

  record JsonString(String value) implements Json {}

  record JsonNumber(int value) implements Json {}

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
  @DisplayName("andThen(Lens) should compose Traversal and Lens")
  void andThen_withLens() {
    Traversal<List<Street>, Street> listStreetTraversal = listElements();
    Lens<Street, String> streetNameLens = Lens.of(Street::name, (s, n) -> new Street(n));

    Traversal<List<Street>, String> composed =
        listStreetTraversal.andThen(streetNameLens.asTraversal());

    List<Street> source = List.of(new Street("Elm"), new Street("Oak"));
    List<String> names = Traversals.getAll(composed, source);
    assertThat(names).containsExactly("Elm", "Oak");

    List<Street> modified = Traversals.modify(composed, String::toUpperCase, source);
    assertThat(modified).extracting(Street::name).containsExactly("ELM", "OAK");
  }

  @Test
  @DisplayName("andThen(Prism) should compose Traversal and Prism")
  void andThen_withPrism() {
    Traversal<List<Json>, Json> listJsonTraversal = listElements();
    Prism<Json, String> jsonStringPrism =
        Prism.of(
            json -> json instanceof JsonString s ? Optional.of(s.value()) : Optional.empty(),
            JsonString::new);

    // FIX: Convert the Prism to a Traversal before composition
    Traversal<List<Json>, String> composed =
        listJsonTraversal.andThen(jsonStringPrism.asTraversal());

    List<Json> source =
        List.of(new JsonString("hello"), new JsonNumber(1), new JsonString("world"));
    List<String> strings = Traversals.getAll(composed, source);
    assertThat(strings).containsExactly("hello", "world");

    List<Json> modified = Traversals.modify(composed, String::toUpperCase, source);
    assertThat(modified)
        .containsExactly(new JsonString("HELLO"), new JsonNumber(1), new JsonString("WORLD"));
  }
}
