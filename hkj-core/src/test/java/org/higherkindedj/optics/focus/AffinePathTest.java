// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.focus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AffinePath<S, A> Tests")
class AffinePathTest {

  // Test Data Structures
  record Config(Optional<Database> database) {}

  record Database(String host, int port, Optional<String> username) {}

  sealed interface Result permits Success, Failure {}

  record Success(String value) implements Result {}

  record Failure(String error) implements Result {}

  @Nested
  @DisplayName("Core Functionality")
  class CoreFunctionality {

    @Test
    @DisplayName("of should create an AffinePath from an Affine")
    void ofShouldCreateAffinePath() {
      Affine<Optional<String>, String> someAffine = FocusPaths.optionalSome();
      AffinePath<Optional<String>, String> path = AffinePath.of(someAffine);

      assertThat(path).isNotNull();
      assertThat(path.toAffine()).isSameAs(someAffine);
    }

    @Test
    @DisplayName("getOptional should return value when present")
    void getOptionalShouldReturnValueWhenPresent() {
      AffinePath<Optional<String>, String> path = AffinePath.of(FocusPaths.optionalSome());

      assertThat(path.getOptional(Optional.of("hello"))).contains("hello");
    }

    @Test
    @DisplayName("getOptional should return empty when absent")
    void getOptionalShouldReturnEmptyWhenAbsent() {
      AffinePath<Optional<String>, String> path = AffinePath.of(FocusPaths.optionalSome());

      assertThat(path.getOptional(Optional.empty())).isEmpty();
    }

    @Test
    @DisplayName("set should update value")
    void setShouldUpdateValue() {
      AffinePath<Optional<String>, String> path = AffinePath.of(FocusPaths.optionalSome());

      Optional<String> result = path.set("world", Optional.of("hello"));
      assertThat(result).contains("world");

      // Set on empty also works
      Optional<String> fromEmpty = path.set("new", Optional.empty());
      assertThat(fromEmpty).contains("new");
    }

    @Test
    @DisplayName("modify should transform value when present")
    void modifyShouldTransformWhenPresent() {
      AffinePath<Optional<String>, String> path = AffinePath.of(FocusPaths.optionalSome());

      Optional<String> result = path.modify(String::toUpperCase, Optional.of("hello"));
      assertThat(result).contains("HELLO");
    }

    @Test
    @DisplayName("modify should return original when absent")
    void modifyShouldReturnOriginalWhenAbsent() {
      AffinePath<Optional<String>, String> path = AffinePath.of(FocusPaths.optionalSome());

      Optional<String> result = path.modify(String::toUpperCase, Optional.empty());
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("matches should return true when value present")
    void matchesShouldReturnTrueWhenPresent() {
      AffinePath<Optional<String>, String> path = AffinePath.of(FocusPaths.optionalSome());

      assertThat(path.matches(Optional.of("hello"))).isTrue();
      assertThat(path.matches(Optional.empty())).isFalse();
    }

    @Test
    @DisplayName("getOrElse should return value or default")
    void getOrElseShouldReturnValueOrDefault() {
      AffinePath<Optional<String>, String> path = AffinePath.of(FocusPaths.optionalSome());

      assertThat(path.getOrElse("default", Optional.of("hello"))).isEqualTo("hello");
      assertThat(path.getOrElse("default", Optional.empty())).isEqualTo("default");
    }

    @Test
    @DisplayName("mapOptional should transform and wrap in Optional")
    void mapOptionalShouldTransformAndWrap() {
      AffinePath<Optional<String>, String> path = AffinePath.of(FocusPaths.optionalSome());

      assertThat(path.mapOptional(String::length, Optional.of("hello"))).contains(5);
      assertThat(path.mapOptional(String::length, Optional.empty())).isEmpty();
    }
  }

  @Nested
  @DisplayName("Composition with Lens")
  class CompositionWithLens {

    @Test
    @DisplayName("via(Lens) should produce an AffinePath")
    void viaLensShouldProduceAffinePath() {
      Lens<Config, Optional<Database>> dbLens = Lens.of(Config::database, (c, d) -> new Config(d));
      Prism<Optional<Database>, Database> somePrism = Prism.of(opt -> opt, db -> Optional.of(db));

      Lens<Database, String> hostLens =
          Lens.of(Database::host, (db, h) -> new Database(h, db.port(), db.username()));

      // Config -> Optional<Database> -> Database -> String
      AffinePath<Config, Database> dbPath = FocusPath.of(dbLens).via(somePrism);
      AffinePath<Config, String> hostPath = dbPath.via(hostLens);

      Config withDb = new Config(Optional.of(new Database("localhost", 5432, Optional.empty())));
      Config withoutDb = new Config(Optional.empty());

      assertThat(hostPath.getOptional(withDb)).contains("localhost");
      assertThat(hostPath.getOptional(withoutDb)).isEmpty();

      Config updated = hostPath.set("127.0.0.1", withDb);
      assertThat(updated.database().get().host()).isEqualTo("127.0.0.1");
    }
  }

  @Nested
  @DisplayName("Composition with Prism")
  class CompositionWithPrism {

    @Test
    @DisplayName("via(Prism) should produce an AffinePath")
    void viaPrismShouldProduceAffinePath() {
      Prism<Result, String> successPrism =
          Prism.of(
              r -> r instanceof Success s ? Optional.of(s.value()) : Optional.empty(),
              Success::new);
      Prism<Optional<Result>, Result> somePrism = Prism.of(opt -> opt, r -> Optional.of(r));

      AffinePath<Optional<Result>, Result> resultPath =
          AffinePath.of(Affine.of(opt -> opt, (opt, r) -> Optional.of(r)));
      AffinePath<Optional<Result>, String> valuePath = resultPath.via(successPrism);

      Optional<Result> success = Optional.of(new Success("data"));
      Optional<Result> failure = Optional.of(new Failure("error"));
      Optional<Result> empty = Optional.empty();

      assertThat(valuePath.getOptional(success)).contains("data");
      assertThat(valuePath.getOptional(failure)).isEmpty();
      assertThat(valuePath.getOptional(empty)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Composition with Affine")
  class CompositionWithAffine {

    @Test
    @DisplayName("via(Affine) should compose two affines")
    void viaAffineShouldComposeTwoAffines() {
      Lens<Config, Optional<Database>> dbLens = Lens.of(Config::database, (c, d) -> new Config(d));
      Affine<Optional<Database>, Database> dbAffine = FocusPaths.optionalSome();

      Lens<Database, Optional<String>> usernameLens =
          Lens.of(Database::username, (db, u) -> new Database(db.host(), db.port(), u));
      Affine<Optional<String>, String> usernameAffine = FocusPaths.optionalSome();

      // Config -> Optional<Database> -> Database -> Optional<String> -> String
      AffinePath<Config, Database> dbPath = FocusPath.of(dbLens).via(dbAffine);
      AffinePath<Config, Optional<String>> userOptPath = dbPath.via(usernameLens);
      AffinePath<Config, String> usernamePath = userOptPath.via(usernameAffine);

      Config withUser =
          new Config(Optional.of(new Database("localhost", 5432, Optional.of("admin"))));
      Config withoutUser =
          new Config(Optional.of(new Database("localhost", 5432, Optional.empty())));
      Config withoutDb = new Config(Optional.empty());

      assertThat(usernamePath.getOptional(withUser)).contains("admin");
      assertThat(usernamePath.getOptional(withoutUser)).isEmpty();
      assertThat(usernamePath.getOptional(withoutDb)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Composition with Traversal")
  class CompositionWithTraversal {

    @Test
    @DisplayName("via(Traversal) should produce a TraversalPath")
    void viaTraversalShouldProduceTraversalPath() {
      record Container(Optional<List<String>> items) {}

      Lens<Container, Optional<List<String>>> itemsLens =
          Lens.of(Container::items, (c, i) -> new Container(i));
      Affine<Optional<List<String>>, List<String>> someAffine = FocusPaths.optionalSome();
      Traversal<List<String>, String> listTraversal = Traversals.forList();

      AffinePath<Container, List<String>> listPath = FocusPath.of(itemsLens).via(someAffine);
      TraversalPath<Container, String> elementsPath = listPath.via(listTraversal);

      Container withItems = new Container(Optional.of(List.of("a", "b", "c")));
      Container withoutItems = new Container(Optional.empty());

      assertThat(elementsPath.getAll(withItems)).containsExactly("a", "b", "c");
      assertThat(elementsPath.getAll(withoutItems)).isEmpty();

      Container modified = elementsPath.modifyAll(String::toUpperCase, withItems);
      assertThat(modified.items().get()).containsExactly("A", "B", "C");
    }
  }

  @Nested
  @DisplayName("Composition with Iso")
  class CompositionWithIso {

    @Test
    @DisplayName("via(Iso) should produce an AffinePath")
    void viaIsoShouldProduceAffinePath() {
      record Wrapper(String value) {}

      Iso<String, Wrapper> wrapperIso = Iso.of(Wrapper::new, Wrapper::value);
      AffinePath<Optional<String>, String> somePath = AffinePath.of(FocusPaths.optionalSome());
      AffinePath<Optional<String>, Wrapper> wrappedPath = somePath.via(wrapperIso);

      assertThat(wrappedPath.getOptional(Optional.of("hello")).map(Wrapper::value))
          .contains("hello");
      assertThat(wrappedPath.getOptional(Optional.empty())).isEmpty();
    }
  }

  @Nested
  @DisplayName("Collection Navigation")
  class CollectionNavigation {

    @Test
    @DisplayName("each() should traverse optional list elements")
    void eachShouldTraverseOptionalListElements() {
      AffinePath<Optional<List<String>>, List<String>> listPath =
          AffinePath.of(FocusPaths.optionalSome());
      TraversalPath<Optional<List<String>>, String> elementsPath = listPath.each();

      Optional<List<String>> present = Optional.of(List.of("a", "b"));
      Optional<List<String>> empty = Optional.empty();

      assertThat(elementsPath.getAll(present)).containsExactly("a", "b");
      assertThat(elementsPath.getAll(empty)).isEmpty();
    }

    @Test
    @DisplayName("at(index) should focus on element in optional list")
    void atShouldFocusOnElementInOptionalList() {
      AffinePath<Optional<List<String>>, List<String>> listPath =
          AffinePath.of(FocusPaths.optionalSome());
      AffinePath<Optional<List<String>>, String> firstPath = listPath.at(0);

      Optional<List<String>> present = Optional.of(List.of("a", "b"));
      Optional<List<String>> emptyList = Optional.of(List.of());
      Optional<List<String>> absent = Optional.empty();

      assertThat(firstPath.getOptional(present)).contains("a");
      assertThat(firstPath.getOptional(emptyList)).isEmpty();
      assertThat(firstPath.getOptional(absent)).isEmpty();
    }

    @Test
    @DisplayName("some() should unwrap nested Optional")
    void someShouldUnwrapNestedOptional() {
      AffinePath<Optional<Optional<String>>, Optional<String>> outerPath =
          AffinePath.of(FocusPaths.optionalSome());
      AffinePath<Optional<Optional<String>>, String> innerPath = outerPath.some();

      Optional<Optional<String>> full = Optional.of(Optional.of("value"));
      Optional<Optional<String>> innerEmpty = Optional.of(Optional.empty());
      Optional<Optional<String>> outerEmpty = Optional.empty();

      assertThat(innerPath.getOptional(full)).contains("value");
      assertThat(innerPath.getOptional(innerEmpty)).isEmpty();
      assertThat(innerPath.getOptional(outerEmpty)).isEmpty();
    }

    @Test
    @DisplayName("atKey() should focus on map value by key")
    void atKeyShouldFocusOnMapValue() {
      AffinePath<Optional<Map<String, String>>, Map<String, String>> mapPath =
          AffinePath.of(FocusPaths.optionalSome());
      AffinePath<Optional<Map<String, String>>, String> themePath = mapPath.atKey("theme");

      Optional<Map<String, String>> present =
          Optional.of(Map.of("theme", "dark", "language", "en"));
      Optional<Map<String, String>> missingKey = Optional.of(Map.of("language", "en"));
      Optional<Map<String, String>> absent = Optional.empty();

      assertThat(themePath.getOptional(present)).contains("dark");
      assertThat(themePath.getOptional(missingKey)).isEmpty();
      assertThat(themePath.getOptional(absent)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Conversion Methods")
  class ConversionMethods {

    @Test
    @DisplayName("asTraversal should return a TraversalPath view")
    void asTraversalShouldReturnTraversalPathView() {
      AffinePath<Optional<String>, String> path = AffinePath.of(FocusPaths.optionalSome());
      TraversalPath<Optional<String>, String> traversalPath = path.asTraversal();

      assertThat(traversalPath.getAll(Optional.of("hello"))).containsExactly("hello");
      assertThat(traversalPath.getAll(Optional.empty())).isEmpty();
    }

    @Test
    @DisplayName("toAffine should return the underlying Affine")
    void toAffineShouldReturnUnderlyingAffine() {
      Affine<Optional<String>, String> affine = FocusPaths.optionalSome();
      AffinePath<Optional<String>, String> path = AffinePath.of(affine);

      assertThat(path.toAffine()).isSameAs(affine);
    }

    @Test
    @DisplayName("asFold should return a Fold view")
    void asFoldShouldReturnFoldView() {
      AffinePath<Optional<String>, String> path = AffinePath.of(FocusPaths.optionalSome());
      Fold<Optional<String>, String> fold = path.asFold();

      // Use foldMap to verify the fold works
      String result = fold.foldMap(Monoids.string(), s -> s, Optional.of("hello"));
      assertThat(result).isEqualTo("hello");

      String emptyResult = fold.foldMap(Monoids.string(), s -> s, Optional.empty());
      assertThat(emptyResult).isEmpty();

      // Preview should also work
      assertThat(fold.preview(Optional.of("hello"))).contains("hello");
      assertThat(fold.preview(Optional.empty())).isEmpty();
    }
  }
}
