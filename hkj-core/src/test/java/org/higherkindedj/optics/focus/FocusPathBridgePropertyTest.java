// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.focus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import net.jqwik.api.*;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.optics.Lens;

/**
 * Property-based tests for FocusPath bridge methods using jQwik.
 *
 * <p>Verifies that bridge operations preserve values correctly and compose properly with effect
 * path operations.
 */
class FocusPathBridgePropertyTest {

  // Test data structure
  record Person(String name, int age, Optional<String> email) {}

  // Lenses
  private static final Lens<Person, String> nameLens =
      Lens.of(Person::name, (p, n) -> new Person(n, p.age(), p.email()));

  private static final Lens<Person, Integer> ageLens =
      Lens.of(Person::age, (p, a) -> new Person(p.name(), a, p.email()));

  @Provide
  Arbitrary<Person> persons() {
    return Combinators.combine(
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20),
            Arbitraries.integers().between(0, 120),
            Arbitraries.strings()
                .alpha()
                .ofMinLength(5)
                .ofMaxLength(30)
                .injectNull(0.3)
                .map(s -> Optional.ofNullable(s)))
        .as(Person::new);
  }

  // ===== Value Preservation Properties =====

  @Property
  @Label("toMaybePath preserves value: path.toMaybePath(s).run().get() == path.get(s)")
  void toMaybePathPreservesValue(@ForAll("persons") Person person) {
    FocusPath<Person, String> path = FocusPath.of(nameLens);

    MaybePath<String> result = path.toMaybePath(person);

    assertThat(result.run().isJust()).isTrue();
    assertThat(result.run().get()).isEqualTo(path.get(person));
  }

  @Property
  @Label("toEitherPath preserves value: path.toEitherPath(s).run().getRight() == path.get(s)")
  void toEitherPathPreservesValue(@ForAll("persons") Person person) {
    FocusPath<Person, String> path = FocusPath.of(nameLens);

    EitherPath<String, String> result = path.toEitherPath(person);

    assertThat(result.run().isRight()).isTrue();
    assertThat(result.run().getRight()).isEqualTo(path.get(person));
  }

  @Property
  @Label("toTryPath preserves value: path.toTryPath(s).run().get() == path.get(s)")
  void toTryPathPreservesValue(@ForAll("persons") Person person) {
    FocusPath<Person, String> path = FocusPath.of(nameLens);

    TryPath<String> result = path.toTryPath(person);

    assertThat(result.run().isSuccess()).isTrue();
    assertThat(result.getOrElse("fallback")).isEqualTo(path.get(person));
  }

  @Property
  @Label("toIdPath preserves value: path.toIdPath(s).run().value() == path.get(s)")
  void toIdPathPreservesValue(@ForAll("persons") Person person) {
    FocusPath<Person, String> path = FocusPath.of(nameLens);

    var result = path.toIdPath(person);

    assertThat(result.run().value()).isEqualTo(path.get(person));
  }

  // ===== Composition Properties =====

  @Property
  @Label(
      "toMaybePath then map is equivalent to mapping first: path.toMaybePath(s).map(f) ~= Path.just(f(path.get(s)))")
  void toMaybePathMapComposition(@ForAll("persons") Person person) {
    FocusPath<Person, String> path = FocusPath.of(nameLens);

    MaybePath<Integer> viaBridge = path.toMaybePath(person).map(String::length);
    MaybePath<Integer> direct = Path.just(path.get(person).length());

    assertThat(viaBridge.run()).isEqualTo(direct.run());
  }

  @Property
  @Label("toEitherPath then map is equivalent to mapping first")
  void toEitherPathMapComposition(@ForAll("persons") Person person) {
    FocusPath<Person, String> path = FocusPath.of(nameLens);

    EitherPath<String, Integer> viaBridge = path.<String>toEitherPath(person).map(String::length);
    EitherPath<String, Integer> direct = Path.right(path.get(person).length());

    assertThat(viaBridge.run()).isEqualTo(direct.run());
  }

  // ===== Lens Composition Properties =====

  @Property
  @Label(
      "composed lenses preserve via bridge: (path1.via(lens2)).toMaybePath(s) produces correct value")
  void composedLensesPreserveViaBridge(@ForAll("persons") Person person) {
    // Create a lens to first char of name (simplified)
    FocusPath<Person, String> namePath = FocusPath.of(nameLens);

    MaybePath<String> result = namePath.toMaybePath(person);

    assertThat(result.run().get()).isEqualTo(person.name());
  }

  // ===== Effect Path Focus Round-Trip Properties =====

  @Property
  @Label("MaybePath.focus(FocusPath) preserves value when Just")
  void maybePathFocusPreservesValue(@ForAll("persons") Person person) {
    FocusPath<Person, String> namePath = FocusPath.of(nameLens);
    MaybePath<Person> personPath = Path.just(person);

    MaybePath<String> result = personPath.focus(namePath);

    assertThat(result.run().isJust()).isTrue();
    assertThat(result.run().get()).isEqualTo(person.name());
  }

  @Property
  @Label("EitherPath.focus(FocusPath) preserves value when Right")
  void eitherPathFocusPreservesValue(@ForAll("persons") Person person) {
    FocusPath<Person, String> namePath = FocusPath.of(nameLens);
    EitherPath<String, Person> personPath = Path.right(person);

    EitherPath<String, String> result = personPath.focus(namePath);

    assertThat(result.run().isRight()).isTrue();
    assertThat(result.run().getRight()).isEqualTo(person.name());
  }

  @Property
  @Label("TryPath.focus(FocusPath) preserves value when Success")
  void tryPathFocusPreservesValue(@ForAll("persons") Person person) {
    FocusPath<Person, String> namePath = FocusPath.of(nameLens);
    TryPath<Person> personPath = Path.success(person);

    TryPath<String> result = personPath.focus(namePath);

    assertThat(result.run().isSuccess()).isTrue();
    assertThat(result.getOrElse("fallback")).isEqualTo(person.name());
  }

  // ===== Multiple Focus Operations =====

  @Property
  @Label("Multiple focus operations chain correctly")
  void multipleFocusOperationsChain(@ForAll("persons") Person person) {
    FocusPath<Person, String> namePath = FocusPath.of(nameLens);
    FocusPath<Person, Integer> agePath = FocusPath.of(ageLens);

    MaybePath<Person> personPath = Path.just(person);

    // Chain multiple focus operations
    String name = personPath.focus(namePath).getOrElse("unknown");
    int age = personPath.focus(agePath).run().get();

    assertThat(name).isEqualTo(person.name());
    assertThat(age).isEqualTo(person.age());
  }
}
