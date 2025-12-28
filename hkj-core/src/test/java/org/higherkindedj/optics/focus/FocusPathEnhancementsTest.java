// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.focus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for Focus DSL enhancements including:
 *
 * <ul>
 *   <li>Simplified via() overloads accepting path types
 *   <li>then() aliases for via()
 *   <li>instanceOf() for sum type support
 *   <li>modifyF() effectful operations
 *   <li>modifyWhen() conditional modification
 *   <li>foldMap() Monoid integration
 * </ul>
 */
@DisplayName("Focus DSL Enhancements")
class FocusPathEnhancementsTest {

  // Test domain types
  record Person(String name, int age, Address address) {}

  record Address(String street, String city) {}

  record Team(String name, List<Person> members) {}

  record Config(Optional<String> apiKey) {}

  sealed interface Shape permits Circle, Rectangle {}

  record Circle(double radius) implements Shape {}

  record Rectangle(double width, double height) implements Shape {}

  record Drawing(List<Shape> shapes) {}

  // Lenses for test domain
  static final Lens<Person, String> personNameLens =
      Lens.of(Person::name, (p, n) -> new Person(n, p.age(), p.address()));

  static final Lens<Person, Address> personAddressLens =
      Lens.of(Person::address, (p, a) -> new Person(p.name(), p.age(), a));

  static final Lens<Address, String> addressCityLens =
      Lens.of(Address::city, (a, c) -> new Address(a.street(), c));

  static final Lens<Address, String> addressStreetLens =
      Lens.of(Address::street, (a, s) -> new Address(s, a.city()));

  static final Lens<Team, List<Person>> teamMembersLens =
      Lens.of(Team::members, (t, m) -> new Team(t.name(), m));

  static final Lens<Person, Integer> personAgeLens =
      Lens.of(Person::age, (p, a) -> new Person(p.name(), a, p.address()));

  @Nested
  @DisplayName("Simplified via() Overloads")
  class ViaOverloadsTests {

    @Test
    @DisplayName("FocusPath.via(FocusPath) should compose paths without toLens()")
    void focusPathViaFocusPath() {
      FocusPath<Person, Address> addressPath = FocusPath.of(personAddressLens);
      FocusPath<Address, String> cityPath = FocusPath.of(addressCityLens);

      // Using via(FocusPath) directly instead of via(path.toLens())
      FocusPath<Person, String> personCityPath = addressPath.via(cityPath);

      Person person = new Person("Alice", 30, new Address("123 Main St", "London"));
      assertThat(personCityPath.get(person)).isEqualTo("London");

      Person updated = personCityPath.set("Paris", person);
      assertThat(updated.address().city()).isEqualTo("Paris");
    }

    @Test
    @DisplayName("FocusPath.via(AffinePath) should produce AffinePath")
    void focusPathViaAffinePath() {
      FocusPath<Config, Optional<String>> apiKeyOptPath =
          FocusPath.of(Lens.of(Config::apiKey, (c, k) -> new Config(k)));
      AffinePath<Optional<String>, String> somePath = AffinePath.of(FocusPaths.optionalSome());

      // Using via(AffinePath) directly
      AffinePath<Config, String> apiKeyPath = apiKeyOptPath.via(somePath);

      Config withKey = new Config(Optional.of("secret-123"));
      Config withoutKey = new Config(Optional.empty());

      assertThat(apiKeyPath.getOptional(withKey)).contains("secret-123");
      assertThat(apiKeyPath.getOptional(withoutKey)).isEmpty();
    }

    @Test
    @DisplayName("FocusPath.via(TraversalPath) should produce TraversalPath")
    void focusPathViaTraversalPath() {
      FocusPath<Team, List<Person>> membersPath = FocusPath.of(teamMembersLens);
      TraversalPath<List<Person>, Person> allPath = TraversalPath.of(FocusPaths.listElements());

      // Using via(TraversalPath) directly
      TraversalPath<Team, Person> teamMembersPath = membersPath.via(allPath);

      Team team =
          new Team(
              "Engineering",
              List.of(
                  new Person("Alice", 30, new Address("A", "X")),
                  new Person("Bob", 25, new Address("B", "Y"))));

      assertThat(teamMembersPath.getAll(team)).hasSize(2);
      assertThat(teamMembersPath.getAll(team).stream().map(Person::name))
          .containsExactly("Alice", "Bob");
    }

    @Test
    @DisplayName("AffinePath.via(FocusPath) should compose correctly")
    void affinePathViaFocusPath() {
      Lens<Config, Optional<String>> apiKeyOptLens =
          Lens.of(Config::apiKey, (c, k) -> new Config(k));
      AffinePath<Config, String> apiKeyPath = FocusPath.of(apiKeyOptLens).some();

      // Create a trivial FocusPath for String manipulation
      FocusPath<String, String> identityPath = FocusPath.identity();

      AffinePath<Config, String> composedPath = apiKeyPath.via(identityPath);

      Config withKey = new Config(Optional.of("secret"));
      assertThat(composedPath.getOptional(withKey)).contains("secret");
    }

    @Test
    @DisplayName("TraversalPath.via(FocusPath) should compose correctly")
    void traversalPathViaFocusPath() {
      TraversalPath<Team, Person> membersPath = FocusPath.of(teamMembersLens).each();
      FocusPath<Person, String> namePath = FocusPath.of(personNameLens);

      // Using via(FocusPath) directly
      TraversalPath<Team, String> namesPath = membersPath.via(namePath);

      Team team =
          new Team(
              "Dev",
              List.of(
                  new Person("Alice", 30, new Address("A", "X")),
                  new Person("Bob", 25, new Address("B", "Y"))));

      assertThat(namesPath.getAll(team)).containsExactly("Alice", "Bob");
    }
  }

  @Nested
  @DisplayName("then() Aliases")
  class ThenAliasesTests {

    @Test
    @DisplayName("FocusPath.then(Lens) should work like via(Lens)")
    void focusPathThenLens() {
      FocusPath<Person, Address> addressPath = FocusPath.of(personAddressLens);

      FocusPath<Person, String> viaResult = addressPath.via(addressCityLens);
      FocusPath<Person, String> thenResult = addressPath.then(addressCityLens);

      Person person = new Person("Alice", 30, new Address("Main", "London"));

      assertThat(viaResult.get(person)).isEqualTo(thenResult.get(person));
      assertThat(viaResult.set("Paris", person)).isEqualTo(thenResult.set("Paris", person));
    }

    @Test
    @DisplayName("FocusPath.then(FocusPath) should work like via(FocusPath)")
    void focusPathThenFocusPath() {
      FocusPath<Person, Address> addressPath = FocusPath.of(personAddressLens);
      FocusPath<Address, String> cityPath = FocusPath.of(addressCityLens);

      FocusPath<Person, String> viaResult = addressPath.via(cityPath);
      FocusPath<Person, String> thenResult = addressPath.then(cityPath);

      Person person = new Person("Bob", 25, new Address("Oak", "Berlin"));

      assertThat(viaResult.get(person)).isEqualTo(thenResult.get(person));
    }

    @Test
    @DisplayName("AffinePath.then() should work as alias for via()")
    void affinePathThen() {
      AffinePath<Config, String> apiKeyPath =
          FocusPath.of(Lens.of(Config::apiKey, (c, k) -> new Config(k))).some();

      FocusPath<String, String> identityPath = FocusPath.identity();

      AffinePath<Config, String> viaResult = apiKeyPath.via(identityPath);
      AffinePath<Config, String> thenResult = apiKeyPath.then(identityPath);

      Config config = new Config(Optional.of("key123"));

      assertThat(viaResult.getOptional(config)).isEqualTo(thenResult.getOptional(config));
    }

    @Test
    @DisplayName("TraversalPath.then() should work as alias for via()")
    void traversalPathThen() {
      TraversalPath<Team, Person> membersPath = FocusPath.of(teamMembersLens).each();

      TraversalPath<Team, String> viaResult = membersPath.via(personNameLens);
      TraversalPath<Team, String> thenResult = membersPath.then(personNameLens);

      Team team =
          new Team(
              "QA",
              List.of(
                  new Person("Carol", 35, new Address("C", "Z")),
                  new Person("Dave", 40, new Address("D", "W"))));

      assertThat(viaResult.getAll(team)).isEqualTo(thenResult.getAll(team));
    }
  }

  @Nested
  @DisplayName("instanceOf() for Sum Types")
  class InstanceOfTests {

    @Test
    @DisplayName("instanceOf should focus on matching subclass")
    void instanceOfMatchingSubclass() {
      AffinePath<Shape, Circle> circlePath = AffinePath.instanceOf(Circle.class);

      Shape circle = new Circle(5.0);
      Shape rectangle = new Rectangle(3.0, 4.0);

      assertThat(circlePath.getOptional(circle)).contains(new Circle(5.0));
      assertThat(circlePath.getOptional(rectangle)).isEmpty();
    }

    @Test
    @DisplayName("instanceOf should allow setting (replacing) the value")
    void instanceOfSetting() {
      AffinePath<Shape, Circle> circlePath = AffinePath.instanceOf(Circle.class);

      Shape circle = new Circle(5.0);
      Shape updated = circlePath.set(new Circle(10.0), circle);

      assertThat(updated).isEqualTo(new Circle(10.0));
    }

    @Test
    @DisplayName("instanceOf should work with matches()")
    void instanceOfMatches() {
      AffinePath<Shape, Circle> circlePath = AffinePath.instanceOf(Circle.class);
      AffinePath<Shape, Rectangle> rectanglePath = AffinePath.instanceOf(Rectangle.class);

      Shape circle = new Circle(5.0);

      assertThat(circlePath.matches(circle)).isTrue();
      assertThat(rectanglePath.matches(circle)).isFalse();
    }

    @Test
    @DisplayName("instanceOf should compose with further paths")
    void instanceOfComposition() {
      Lens<Circle, Double> radiusLens = Lens.of(Circle::radius, (c, r) -> new Circle(r));

      AffinePath<Shape, Circle> circlePath = AffinePath.instanceOf(Circle.class);
      AffinePath<Shape, Double> radiusPath = circlePath.via(radiusLens);

      Shape circle = new Circle(5.0);
      Shape rectangle = new Rectangle(3.0, 4.0);

      assertThat(radiusPath.getOptional(circle)).contains(5.0);
      assertThat(radiusPath.getOptional(rectangle)).isEmpty();

      Shape updatedCircle = radiusPath.set(7.5, circle);
      assertThat(((Circle) updatedCircle).radius()).isEqualTo(7.5);
    }

    @Test
    @DisplayName("instanceOf should work with TraversalPath over sealed types")
    void instanceOfWithTraversal() {
      Lens<Drawing, List<Shape>> shapesLens = Lens.of(Drawing::shapes, (d, s) -> new Drawing(s));

      TraversalPath<Drawing, Shape> allShapes = FocusPath.of(shapesLens).each();
      AffinePath<Shape, Circle> circlePath = AffinePath.instanceOf(Circle.class);

      // Compose traversal with instanceOf
      TraversalPath<Drawing, Circle> allCircles =
          allShapes.via(circlePath.toAffine().asTraversal());

      Drawing drawing =
          new Drawing(
              List.of(new Circle(1.0), new Rectangle(2.0, 3.0), new Circle(4.0), new Circle(5.0)));

      List<Circle> circles = allCircles.getAll(drawing);
      assertThat(circles).hasSize(3);
      assertThat(circles.stream().map(Circle::radius)).containsExactly(1.0, 4.0, 5.0);
    }
  }

  @Nested
  @DisplayName("modifyF() Effectful Operations")
  class ModifyFTests {

    @Test
    @DisplayName("FocusPath.modifyF should work with Maybe functor")
    void focusPathModifyFWithMaybe() {
      FocusPath<Person, String> namePath = FocusPath.of(personNameLens);
      Person person = new Person("Alice", 30, new Address("Main", "London"));

      // Effectful modification: uppercase if not empty
      Function<String, Kind<MaybeKind.Witness, String>> upperIfNotEmpty =
          s ->
              s.isEmpty()
                  ? MaybeKindHelper.MAYBE.widen(Maybe.nothing())
                  : MaybeKindHelper.MAYBE.widen(Maybe.just(s.toUpperCase()));

      Kind<MaybeKind.Witness, Person> result =
          namePath.modifyF(upperIfNotEmpty, person, MaybeMonad.INSTANCE);

      Maybe<Person> maybePerson = MaybeKindHelper.MAYBE.narrow(result);
      assertThat(maybePerson.isJust()).isTrue();
      assertThat(maybePerson.orElse(null).name()).isEqualTo("ALICE");
    }

    @Test
    @DisplayName("AffinePath.modifyF should return source unchanged when no focus")
    void affinePathModifyFNoFocus() {
      AffinePath<Config, String> apiKeyPath =
          FocusPath.of(Lens.of(Config::apiKey, (c, k) -> new Config(k))).some();

      Config withoutKey = new Config(Optional.empty());

      Function<String, Kind<MaybeKind.Witness, String>> transform =
          s -> MaybeKindHelper.MAYBE.widen(Maybe.just(s.toUpperCase()));

      Kind<MaybeKind.Witness, Config> result =
          apiKeyPath.modifyF(transform, withoutKey, MaybeMonad.INSTANCE);

      Maybe<Config> maybeConfig = MaybeKindHelper.MAYBE.narrow(result);
      assertThat(maybeConfig.isJust()).isTrue();
      assertThat(maybeConfig.orElse(null).apiKey()).isEmpty();
    }

    @Test
    @DisplayName("TraversalPath.modifyF should sequence effects across elements")
    void traversalPathModifyFSequencesEffects() {
      TraversalPath<Team, Person> membersPath = FocusPath.of(teamMembersLens).each();

      Team team =
          new Team(
              "Dev",
              List.of(
                  new Person("Alice", 30, new Address("A", "X")),
                  new Person("Bob", 25, new Address("B", "Y"))));

      // Increment age wrapped in Maybe
      Function<Person, Kind<MaybeKind.Witness, Person>> incrementAge =
          p ->
              MaybeKindHelper.MAYBE.widen(
                  Maybe.just(new Person(p.name(), p.age() + 1, p.address())));

      Kind<MaybeKind.Witness, Team> result =
          membersPath.modifyF(incrementAge, team, MaybeMonad.INSTANCE);

      Maybe<Team> maybeTeam = MaybeKindHelper.MAYBE.narrow(result);
      assertThat(maybeTeam.isJust()).isTrue();

      Team updatedTeam = maybeTeam.orElse(null);
      assertThat(updatedTeam.members().stream().map(Person::age)).containsExactly(31, 26);
    }
  }

  @Nested
  @DisplayName("modifyWhen() Conditional Modification")
  class ModifyWhenTests {

    @Test
    @DisplayName("modifyWhen should only modify elements matching predicate")
    void modifyWhenOnlyModifiesMatchingElements() {
      TraversalPath<Team, Person> membersPath = FocusPath.of(teamMembersLens).each();

      Team team =
          new Team(
              "Dev",
              List.of(
                  new Person("Alice", 30, new Address("A", "X")),
                  new Person("Bob", 25, new Address("B", "Y")),
                  new Person("Carol", 35, new Address("C", "Z"))));

      // Only modify people over 28
      Team updated =
          membersPath.modifyWhen(
              p -> p.age() > 28,
              p -> new Person(p.name().toUpperCase(), p.age(), p.address()),
              team);

      List<String> names = updated.members().stream().map(Person::name).toList();
      assertThat(names).containsExactly("ALICE", "Bob", "CAROL");
    }

    @Test
    @DisplayName("modifyWhen should leave non-matching elements unchanged")
    void modifyWhenLeavesNonMatchingUnchanged() {
      TraversalPath<Team, Person> membersPath = FocusPath.of(teamMembersLens).each();

      Team team =
          new Team(
              "Team",
              List.of(
                  new Person("Young", 20, new Address("A", "X")),
                  new Person("Old", 50, new Address("B", "Y"))));

      // Give raise only to seniors (age >= 30)
      Team updated =
          membersPath.modifyWhen(
              p -> p.age() >= 30, p -> new Person(p.name(), p.age(), p.address()), team);

      // Young person should be completely unchanged
      assertThat(updated.members().get(0)).isEqualTo(team.members().get(0));
    }

    @Test
    @DisplayName("modifyWhen with no matches should return original")
    void modifyWhenNoMatchesReturnsOriginal() {
      TraversalPath<Team, Person> membersPath = FocusPath.of(teamMembersLens).each();

      Team team =
          new Team(
              "Young Team",
              List.of(
                  new Person("A", 20, new Address("A", "X")),
                  new Person("B", 22, new Address("B", "Y"))));

      // No one is over 100
      Team updated =
          membersPath.modifyWhen(
              p -> p.age() > 100, p -> new Person("MODIFIED", p.age(), p.address()), team);

      assertThat(updated.members()).isEqualTo(team.members());
    }
  }

  @Nested
  @DisplayName("foldMap() Monoid Integration")
  class FoldMapTests {

    @Test
    @DisplayName("foldMap should aggregate values with string monoid")
    void foldMapWithStringMonoid() {
      TraversalPath<Team, Person> membersPath = FocusPath.of(teamMembersLens).each();

      Team team =
          new Team(
              "Dev",
              List.of(
                  new Person("Alice", 30, new Address("A", "X")),
                  new Person("Bob", 25, new Address("B", "Y")),
                  new Person("Carol", 35, new Address("C", "Z"))));

      String concatenatedNames = membersPath.foldMap(Monoids.string(), Person::name, team);

      assertThat(concatenatedNames).isEqualTo("AliceBobCarol");
    }

    @Test
    @DisplayName("foldMap should work with integer sum")
    void foldMapWithIntegerSum() {
      TraversalPath<Team, Person> membersPath = FocusPath.of(teamMembersLens).each();

      Team team =
          new Team(
              "Dev",
              List.of(
                  new Person("Alice", 30, new Address("A", "X")),
                  new Person("Bob", 25, new Address("B", "Y")),
                  new Person("Carol", 35, new Address("C", "Z"))));

      Integer totalAge = membersPath.foldMap(Monoids.integerAddition(), Person::age, team);

      assertThat(totalAge).isEqualTo(90);
    }

    @Test
    @DisplayName("foldMap should return empty for empty traversal")
    void foldMapEmptyTraversal() {
      TraversalPath<Team, Person> membersPath = FocusPath.of(teamMembersLens).each();

      Team emptyTeam = new Team("Empty", List.of());

      String result = membersPath.foldMap(Monoids.string(), Person::name, emptyTeam);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("fold should combine elements directly")
    void foldCombinesElements() {
      Lens<Team, List<String>> tagsLens =
          Lens.of(t -> t.members().stream().map(Person::name).toList(), (t, tags) -> t);
      TraversalPath<Team, String> tagsPath = FocusPath.of(tagsLens).each();

      Team team =
          new Team(
              "Dev",
              List.of(
                  new Person("A", 30, new Address("A", "X")),
                  new Person("B", 25, new Address("B", "Y"))));

      String folded = tagsPath.fold(Monoids.string(), team);

      assertThat(folded).isEqualTo("AB");
    }

    @Test
    @DisplayName("foldMap with custom monoid")
    void foldMapWithCustomMonoid() {
      TraversalPath<Team, Person> membersPath = FocusPath.of(teamMembersLens).each();

      // Custom monoid for finding max age
      Monoid<Integer> maxMonoid =
          new Monoid<>() {
            @Override
            public Integer empty() {
              return Integer.MIN_VALUE;
            }

            @Override
            public Integer combine(Integer a, Integer b) {
              return Math.max(a, b);
            }
          };

      Team team =
          new Team(
              "Dev",
              List.of(
                  new Person("Alice", 30, new Address("A", "X")),
                  new Person("Bob", 25, new Address("B", "Y")),
                  new Person("Carol", 45, new Address("C", "Z"))));

      Integer maxAge = membersPath.foldMap(maxMonoid, Person::age, team);

      assertThat(maxAge).isEqualTo(45);
    }
  }

  @Nested
  @DisplayName("Additional FocusPath then() Aliases Coverage")
  class FocusPathThenAliasesTests {

    // Iso: String <-> String (uppercase/lowercase)
    static final Iso<String, String> uppercaseIso =
        Iso.of(String::toUpperCase, String::toLowerCase);

    // Prism: String -> Integer (parse)
    static final Prism<String, Integer> parseIntPrism =
        Prism.of(
            s -> {
              try {
                return Optional.of(Integer.parseInt(s));
              } catch (NumberFormatException e) {
                return Optional.empty();
              }
            },
            Object::toString);

    // Affine: accessing Optional content
    static final Affine<Optional<String>, String> optionalAffine =
        Affine.of(opt -> opt, (opt, s) -> Optional.of(s));

    // Traversal over list
    static final Traversal<List<String>, String> listTraversal = FocusPaths.listElements();

    @Test
    @DisplayName("FocusPath.then(Iso) should work like via(Iso)")
    void focusPathThenIso() {
      FocusPath<Person, String> namePath = FocusPath.of(personNameLens);

      FocusPath<Person, String> thenResult = namePath.then(uppercaseIso);

      Person person = new Person("alice", 30, new Address("Main", "London"));
      assertThat(thenResult.get(person)).isEqualTo("ALICE");

      Person updated = thenResult.set("bob", person);
      assertThat(updated.name()).isEqualTo("bob");
    }

    @Test
    @DisplayName("FocusPath.then(Prism) should produce AffinePath")
    void focusPathThenPrism() {
      // Person with a numeric name for testing
      record NumericName(String value) {}
      record PersonWithNumeric(NumericName numericName) {}

      Lens<PersonWithNumeric, String> numericLens =
          Lens.of(
              p -> p.numericName().value(), (p, v) -> new PersonWithNumeric(new NumericName(v)));
      FocusPath<PersonWithNumeric, String> numPath = FocusPath.of(numericLens);

      AffinePath<PersonWithNumeric, Integer> parsedPath = numPath.then(parseIntPrism);

      PersonWithNumeric validNum = new PersonWithNumeric(new NumericName("123"));
      PersonWithNumeric invalidNum = new PersonWithNumeric(new NumericName("abc"));

      assertThat(parsedPath.getOptional(validNum)).contains(123);
      assertThat(parsedPath.getOptional(invalidNum)).isEmpty();
    }

    @Test
    @DisplayName("FocusPath.then(Affine) should produce AffinePath")
    void focusPathThenAffine() {
      FocusPath<Config, Optional<String>> configPath =
          FocusPath.of(Lens.of(Config::apiKey, (c, k) -> new Config(k)));

      AffinePath<Config, String> keyPath = configPath.then(optionalAffine);

      Config withKey = new Config(Optional.of("secret"));
      Config withoutKey = new Config(Optional.empty());

      assertThat(keyPath.getOptional(withKey)).contains("secret");
      assertThat(keyPath.getOptional(withoutKey)).isEmpty();
    }

    @Test
    @DisplayName("FocusPath.then(AffinePath) should produce AffinePath")
    void focusPathThenAffinePath() {
      FocusPath<Config, Optional<String>> configPath =
          FocusPath.of(Lens.of(Config::apiKey, (c, k) -> new Config(k)));
      AffinePath<Optional<String>, String> somePath = AffinePath.of(optionalAffine);

      AffinePath<Config, String> keyPath = configPath.then(somePath);

      Config withKey = new Config(Optional.of("api-key"));
      assertThat(keyPath.getOptional(withKey)).contains("api-key");
    }

    @Test
    @DisplayName("FocusPath.then(Traversal) should produce TraversalPath")
    void focusPathThenTraversal() {
      record Container(List<String> items) {}
      Lens<Container, List<String>> itemsLens =
          Lens.of(Container::items, (c, i) -> new Container(i));
      FocusPath<Container, List<String>> containerPath = FocusPath.of(itemsLens);

      TraversalPath<Container, String> allItems = containerPath.then(listTraversal);

      Container container = new Container(List.of("a", "b", "c"));
      assertThat(allItems.getAll(container)).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("FocusPath.then(TraversalPath) should produce TraversalPath")
    void focusPathThenTraversalPath() {
      record Container(List<String> items) {}
      Lens<Container, List<String>> itemsLens =
          Lens.of(Container::items, (c, i) -> new Container(i));
      FocusPath<Container, List<String>> containerPath = FocusPath.of(itemsLens);
      TraversalPath<List<String>, String> allPath = TraversalPath.of(listTraversal);

      TraversalPath<Container, String> allItems = containerPath.then(allPath);

      Container container = new Container(List.of("x", "y", "z"));
      assertThat(allItems.getAll(container)).containsExactly("x", "y", "z");
    }
  }

  @Nested
  @DisplayName("Additional AffinePath via() and then() Coverage")
  class AffinePathViaAndThenTests {

    static final Prism<String, Integer> parseIntPrism =
        Prism.of(
            s -> {
              try {
                return Optional.of(Integer.parseInt(s));
              } catch (NumberFormatException e) {
                return Optional.empty();
              }
            },
            Object::toString);

    static final Affine<Optional<String>, String> optionalAffine =
        Affine.of(opt -> opt, (opt, s) -> Optional.of(s));

    static final Iso<String, String> uppercaseIso =
        Iso.of(String::toUpperCase, String::toLowerCase);

    static final Traversal<List<String>, String> listTraversal = FocusPaths.listElements();

    @Test
    @DisplayName("AffinePath.via(AffinePath) should compose affine paths")
    void affinePathViaAffinePath() {
      // Nested optionals: Optional<Optional<String>>
      record DoubleOptional(Optional<Optional<String>> value) {}

      Lens<DoubleOptional, Optional<Optional<String>>> valueLens =
          Lens.of(DoubleOptional::value, (d, v) -> new DoubleOptional(v));

      AffinePath<DoubleOptional, Optional<String>> firstOpt = FocusPath.of(valueLens).some();
      AffinePath<Optional<String>, String> secondOpt = AffinePath.of(optionalAffine);

      AffinePath<DoubleOptional, String> nestedPath = firstOpt.via(secondOpt);

      DoubleOptional present = new DoubleOptional(Optional.of(Optional.of("hello")));
      DoubleOptional partiallyPresent = new DoubleOptional(Optional.of(Optional.empty()));
      DoubleOptional absent = new DoubleOptional(Optional.empty());

      assertThat(nestedPath.getOptional(present)).contains("hello");
      assertThat(nestedPath.getOptional(partiallyPresent)).isEmpty();
      assertThat(nestedPath.getOptional(absent)).isEmpty();
    }

    @Test
    @DisplayName("AffinePath.via(TraversalPath) should produce TraversalPath")
    void affinePathViaTraversalPath() {
      record OptionalList(Optional<List<String>> items) {}

      Lens<OptionalList, Optional<List<String>>> itemsLens =
          Lens.of(OptionalList::items, (o, i) -> new OptionalList(i));

      AffinePath<OptionalList, List<String>> optListPath = FocusPath.of(itemsLens).some();
      TraversalPath<List<String>, String> allItems = TraversalPath.of(listTraversal);

      TraversalPath<OptionalList, String> allOptItems = optListPath.via(allItems);

      OptionalList present = new OptionalList(Optional.of(List.of("a", "b")));
      OptionalList absent = new OptionalList(Optional.empty());

      assertThat(allOptItems.getAll(present)).containsExactly("a", "b");
      assertThat(allOptItems.getAll(absent)).isEmpty();
    }

    @Test
    @DisplayName("AffinePath.then(Lens) should work like via(Lens)")
    void affinePathThenLens() {
      AffinePath<Config, String> apiKeyPath =
          FocusPath.of(Lens.of(Config::apiKey, (c, k) -> new Config(k))).some();

      // Create a simple lens that gets the length of a string
      Lens<String, Integer> lengthLens =
          Lens.of(String::length, (s, len) -> s.substring(0, Math.min(len, s.length())));

      AffinePath<Config, Integer> keyLengthPath = apiKeyPath.then(lengthLens);

      Config withKey = new Config(Optional.of("hello"));
      assertThat(keyLengthPath.getOptional(withKey)).contains(5);
    }

    @Test
    @DisplayName("AffinePath.then(Prism) should produce AffinePath")
    void affinePathThenPrism() {
      record NumericConfig(Optional<String> numericValue) {}

      Lens<NumericConfig, Optional<String>> valueLens =
          Lens.of(NumericConfig::numericValue, (c, v) -> new NumericConfig(v));

      AffinePath<NumericConfig, String> valuePath = FocusPath.of(valueLens).some();
      AffinePath<NumericConfig, Integer> intPath = valuePath.then(parseIntPrism);

      NumericConfig validNum = new NumericConfig(Optional.of("42"));
      NumericConfig invalidNum = new NumericConfig(Optional.of("abc"));
      NumericConfig empty = new NumericConfig(Optional.empty());

      assertThat(intPath.getOptional(validNum)).contains(42);
      assertThat(intPath.getOptional(invalidNum)).isEmpty();
      assertThat(intPath.getOptional(empty)).isEmpty();
    }

    @Test
    @DisplayName("AffinePath.then(Affine) should produce AffinePath")
    void affinePathThenAffine() {
      record NestedOpt(Optional<Optional<String>> nested) {}

      Lens<NestedOpt, Optional<Optional<String>>> nestedLens =
          Lens.of(NestedOpt::nested, (n, v) -> new NestedOpt(v));

      AffinePath<NestedOpt, Optional<String>> firstLevel = FocusPath.of(nestedLens).some();
      AffinePath<NestedOpt, String> deepPath = firstLevel.then(optionalAffine);

      NestedOpt present = new NestedOpt(Optional.of(Optional.of("deep")));
      assertThat(deepPath.getOptional(present)).contains("deep");
    }

    @Test
    @DisplayName("AffinePath.then(AffinePath) should produce AffinePath")
    void affinePathThenAffinePath() {
      record NestedOpt(Optional<Optional<String>> nested) {}

      Lens<NestedOpt, Optional<Optional<String>>> nestedLens =
          Lens.of(NestedOpt::nested, (n, v) -> new NestedOpt(v));

      AffinePath<NestedOpt, Optional<String>> firstLevel = FocusPath.of(nestedLens).some();
      AffinePath<Optional<String>, String> secondLevel = AffinePath.of(optionalAffine);

      AffinePath<NestedOpt, String> deepPath = firstLevel.then(secondLevel);

      NestedOpt present = new NestedOpt(Optional.of(Optional.of("nested")));
      assertThat(deepPath.getOptional(present)).contains("nested");
    }

    @Test
    @DisplayName("AffinePath.then(Iso) should produce AffinePath")
    void affinePathThenIso() {
      AffinePath<Config, String> apiKeyPath =
          FocusPath.of(Lens.of(Config::apiKey, (c, k) -> new Config(k))).some();

      AffinePath<Config, String> uppercasePath = apiKeyPath.then(uppercaseIso);

      Config withKey = new Config(Optional.of("secret"));
      assertThat(uppercasePath.getOptional(withKey)).contains("SECRET");
    }

    @Test
    @DisplayName("AffinePath.then(Traversal) should produce TraversalPath")
    void affinePathThenTraversal() {
      record OptionalList(Optional<List<String>> items) {}

      Lens<OptionalList, Optional<List<String>>> itemsLens =
          Lens.of(OptionalList::items, (o, i) -> new OptionalList(i));

      AffinePath<OptionalList, List<String>> optListPath = FocusPath.of(itemsLens).some();

      TraversalPath<OptionalList, String> allItems = optListPath.then(listTraversal);

      OptionalList present = new OptionalList(Optional.of(List.of("1", "2", "3")));
      assertThat(allItems.getAll(present)).containsExactly("1", "2", "3");
    }

    @Test
    @DisplayName("AffinePath.then(TraversalPath) should produce TraversalPath")
    void affinePathThenTraversalPath() {
      record OptionalList(Optional<List<String>> items) {}

      Lens<OptionalList, Optional<List<String>>> itemsLens =
          Lens.of(OptionalList::items, (o, i) -> new OptionalList(i));

      AffinePath<OptionalList, List<String>> optListPath = FocusPath.of(itemsLens).some();
      TraversalPath<List<String>, String> allPath = TraversalPath.of(listTraversal);

      TraversalPath<OptionalList, String> allItems = optListPath.then(allPath);

      OptionalList present = new OptionalList(Optional.of(List.of("x", "y")));
      assertThat(allItems.getAll(present)).containsExactly("x", "y");
    }
  }

  @Nested
  @DisplayName("Additional TraversalPath via() and then() Coverage")
  class TraversalPathViaAndThenTests {

    static final Prism<String, Integer> parseIntPrism =
        Prism.of(
            s -> {
              try {
                return Optional.of(Integer.parseInt(s));
              } catch (NumberFormatException e) {
                return Optional.empty();
              }
            },
            Object::toString);

    static final Affine<Optional<String>, String> optionalAffine =
        Affine.of(opt -> opt, (opt, s) -> Optional.of(s));

    static final Iso<String, String> uppercaseIso =
        Iso.of(String::toUpperCase, String::toLowerCase);

    static final Traversal<List<String>, String> listTraversal = FocusPaths.listElements();

    @Test
    @DisplayName("TraversalPath.via(AffinePath) should compose with affine path")
    void traversalPathViaAffinePath() {
      record Container(List<Optional<String>> items) {}

      Lens<Container, List<Optional<String>>> itemsLens =
          Lens.of(Container::items, (c, i) -> new Container(i));

      TraversalPath<Container, Optional<String>> allOptionals = FocusPath.of(itemsLens).each();
      AffinePath<Optional<String>, String> somePath = AffinePath.of(optionalAffine);

      TraversalPath<Container, String> allPresent = allOptionals.via(somePath);

      Container container =
          new Container(List.of(Optional.of("a"), Optional.empty(), Optional.of("b")));

      assertThat(allPresent.getAll(container)).containsExactly("a", "b");
    }

    @Test
    @DisplayName("TraversalPath.via(TraversalPath) should compose traversal paths")
    void traversalPathViaTraversalPath() {
      record NestedLists(List<List<String>> items) {}

      Lens<NestedLists, List<List<String>>> itemsLens =
          Lens.of(NestedLists::items, (n, i) -> new NestedLists(i));

      TraversalPath<NestedLists, List<String>> allLists = FocusPath.of(itemsLens).each();
      TraversalPath<List<String>, String> allStrings = TraversalPath.of(listTraversal);

      TraversalPath<NestedLists, String> allDeep = allLists.via(allStrings);

      NestedLists nested = new NestedLists(List.of(List.of("a", "b"), List.of("c")));

      assertThat(allDeep.getAll(nested)).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("TraversalPath.then(FocusPath) should work like via(FocusPath)")
    void traversalPathThenFocusPath() {
      TraversalPath<Team, Person> membersPath = FocusPath.of(teamMembersLens).each();
      FocusPath<Person, String> namePath = FocusPath.of(personNameLens);

      TraversalPath<Team, String> namesPath = membersPath.then(namePath);

      Team team =
          new Team(
              "Dev",
              List.of(
                  new Person("Alice", 30, new Address("A", "X")),
                  new Person("Bob", 25, new Address("B", "Y"))));

      assertThat(namesPath.getAll(team)).containsExactly("Alice", "Bob");
    }

    @Test
    @DisplayName("TraversalPath.then(Prism) should produce TraversalPath")
    void traversalPathThenPrism() {
      record NumericList(List<String> numbers) {}

      Lens<NumericList, List<String>> numbersLens =
          Lens.of(NumericList::numbers, (n, l) -> new NumericList(l));

      TraversalPath<NumericList, String> allStrings = FocusPath.of(numbersLens).each();
      TraversalPath<NumericList, Integer> allInts = allStrings.then(parseIntPrism);

      NumericList mixed = new NumericList(List.of("1", "abc", "2", "def", "3"));

      assertThat(allInts.getAll(mixed)).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("TraversalPath.then(Affine) should produce TraversalPath")
    void traversalPathThenAffine() {
      record OptionalContainer(List<Optional<String>> items) {}

      Lens<OptionalContainer, List<Optional<String>>> itemsLens =
          Lens.of(OptionalContainer::items, (c, i) -> new OptionalContainer(i));

      TraversalPath<OptionalContainer, Optional<String>> allOptionals =
          FocusPath.of(itemsLens).each();

      TraversalPath<OptionalContainer, String> allPresent = allOptionals.then(optionalAffine);

      OptionalContainer container =
          new OptionalContainer(List.of(Optional.of("x"), Optional.empty(), Optional.of("y")));

      assertThat(allPresent.getAll(container)).containsExactly("x", "y");
    }

    @Test
    @DisplayName("TraversalPath.then(AffinePath) should produce TraversalPath")
    void traversalPathThenAffinePath() {
      record OptionalContainer(List<Optional<String>> items) {}

      Lens<OptionalContainer, List<Optional<String>>> itemsLens =
          Lens.of(OptionalContainer::items, (c, i) -> new OptionalContainer(i));

      TraversalPath<OptionalContainer, Optional<String>> allOptionals =
          FocusPath.of(itemsLens).each();
      AffinePath<Optional<String>, String> somePath = AffinePath.of(optionalAffine);

      TraversalPath<OptionalContainer, String> allPresent = allOptionals.then(somePath);

      OptionalContainer container =
          new OptionalContainer(List.of(Optional.of("a"), Optional.empty(), Optional.of("b")));

      assertThat(allPresent.getAll(container)).containsExactly("a", "b");
    }

    @Test
    @DisplayName("TraversalPath.then(Traversal) should produce TraversalPath")
    void traversalPathThenTraversal() {
      record NestedLists(List<List<String>> items) {}

      Lens<NestedLists, List<List<String>>> itemsLens =
          Lens.of(NestedLists::items, (n, i) -> new NestedLists(i));

      TraversalPath<NestedLists, List<String>> allLists = FocusPath.of(itemsLens).each();

      TraversalPath<NestedLists, String> allStrings = allLists.then(listTraversal);

      NestedLists nested = new NestedLists(List.of(List.of("a", "b"), List.of("c", "d")));

      assertThat(allStrings.getAll(nested)).containsExactly("a", "b", "c", "d");
    }

    @Test
    @DisplayName("TraversalPath.then(TraversalPath) should produce TraversalPath")
    void traversalPathThenTraversalPath() {
      record NestedLists(List<List<String>> items) {}

      Lens<NestedLists, List<List<String>>> itemsLens =
          Lens.of(NestedLists::items, (n, i) -> new NestedLists(i));

      TraversalPath<NestedLists, List<String>> allLists = FocusPath.of(itemsLens).each();
      TraversalPath<List<String>, String> allPath = TraversalPath.of(listTraversal);

      TraversalPath<NestedLists, String> allStrings = allLists.then(allPath);

      NestedLists nested = new NestedLists(List.of(List.of("x"), List.of("y", "z")));

      assertThat(allStrings.getAll(nested)).containsExactly("x", "y", "z");
    }

    @Test
    @DisplayName("TraversalPath.then(Iso) should produce TraversalPath")
    void traversalPathThenIso() {
      record Container(List<String> items) {}

      Lens<Container, List<String>> itemsLens =
          Lens.of(Container::items, (c, i) -> new Container(i));

      TraversalPath<Container, String> allStrings = FocusPath.of(itemsLens).each();
      TraversalPath<Container, String> allUppercase = allStrings.then(uppercaseIso);

      Container container = new Container(List.of("hello", "world"));

      assertThat(allUppercase.getAll(container)).containsExactly("HELLO", "WORLD");
    }
  }

  @Nested
  @DisplayName("traced() Path Debugging")
  class TracedPathDebuggingTests {

    @Test
    @DisplayName("FocusPath.traced() should invoke observer on get")
    void focusPathTracedInvokesObserver() {
      FocusPath<Person, String> namePath = FocusPath.of(personNameLens);
      Person person = new Person("Alice", 30, new Address("Main", "London"));

      // Track what was observed
      List<String> observedValues = new ArrayList<>();
      List<Person> observedSources = new ArrayList<>();

      FocusPath<Person, String> tracedPath =
          namePath.traced(
              (source, value) -> {
                observedSources.add(source);
                observedValues.add(value);
              });

      // Get should invoke the observer
      String result = tracedPath.get(person);

      assertThat(result).isEqualTo("Alice");
      assertThat(observedSources).containsExactly(person);
      assertThat(observedValues).containsExactly("Alice");
    }

    @Test
    @DisplayName("FocusPath.traced() should still allow set operations")
    void focusPathTracedAllowsSet() {
      FocusPath<Person, String> namePath = FocusPath.of(personNameLens);
      Person person = new Person("Alice", 30, new Address("Main", "London"));

      List<String> observedValues = new ArrayList<>();

      FocusPath<Person, String> tracedPath =
          namePath.traced((source, value) -> observedValues.add(value));

      // Set should work without invoking observer
      Person updated = tracedPath.set("Bob", person);

      assertThat(updated.name()).isEqualTo("Bob");
      // Observer not called on set
      assertThat(observedValues).isEmpty();
    }

    @Test
    @DisplayName("FocusPath.traced() can be chained multiple times")
    void focusPathTracedCanBeChained() {
      FocusPath<Person, String> namePath = FocusPath.of(personNameLens);
      Person person = new Person("Alice", 30, new Address("Main", "London"));

      List<String> trace1 = new ArrayList<>();
      List<String> trace2 = new ArrayList<>();

      FocusPath<Person, String> doubleTracedPath =
          namePath
              .traced((s, v) -> trace1.add("first: " + v))
              .traced((s, v) -> trace2.add("second: " + v));

      String result = doubleTracedPath.get(person);

      assertThat(result).isEqualTo("Alice");
      assertThat(trace1).containsExactly("first: Alice");
      assertThat(trace2).containsExactly("second: Alice");
    }

    @Test
    @DisplayName("AffinePath.traced() should invoke observer with Optional")
    void affinePathTracedInvokesObserver() {
      AffinePath<Config, String> apiKeyPath =
          FocusPath.of(Lens.of(Config::apiKey, (c, k) -> new Config(k))).some();

      Config withKey = new Config(Optional.of("secret-123"));
      Config withoutKey = new Config(Optional.empty());

      List<Optional<String>> observedValues = new ArrayList<>();

      AffinePath<Config, String> tracedPath =
          apiKeyPath.traced((source, value) -> observedValues.add(value));

      // Get with present value
      Optional<String> result1 = tracedPath.getOptional(withKey);
      // Get with absent value
      Optional<String> result2 = tracedPath.getOptional(withoutKey);

      assertThat(result1).contains("secret-123");
      assertThat(result2).isEmpty();
      assertThat(observedValues).containsExactly(Optional.of("secret-123"), Optional.empty());
    }

    @Test
    @DisplayName("AffinePath.traced() should still allow set operations")
    void affinePathTracedAllowsSet() {
      AffinePath<Config, String> apiKeyPath =
          FocusPath.of(Lens.of(Config::apiKey, (c, k) -> new Config(k))).some();

      Config config = new Config(Optional.of("old-key"));

      List<Optional<String>> observedValues = new ArrayList<>();

      AffinePath<Config, String> tracedPath =
          apiKeyPath.traced((source, value) -> observedValues.add(value));

      Config updated = tracedPath.set("new-key", config);

      assertThat(updated.apiKey()).contains("new-key");
      // Observer not called on set
      assertThat(observedValues).isEmpty();
    }

    @Test
    @DisplayName("TraversalPath.traced() should invoke observer with list")
    void traversalPathTracedInvokesObserver() {
      TraversalPath<Team, Person> membersPath = FocusPath.of(teamMembersLens).each();

      Team team =
          new Team(
              "Dev",
              List.of(
                  new Person("Alice", 30, new Address("A", "X")),
                  new Person("Bob", 25, new Address("B", "Y"))));

      List<Integer> observedCounts = new ArrayList<>();
      List<Team> observedSources = new ArrayList<>();

      TraversalPath<Team, Person> tracedPath =
          membersPath.traced(
              (source, values) -> {
                observedSources.add(source);
                observedCounts.add(values.size());
              });

      List<Person> result = tracedPath.getAll(team);

      assertThat(result).hasSize(2);
      assertThat(observedSources).containsExactly(team);
      assertThat(observedCounts).containsExactly(2);
    }

    @Test
    @DisplayName("TraversalPath.traced() should still allow modify operations")
    void traversalPathTracedAllowsModify() {
      TraversalPath<Team, Person> membersPath = FocusPath.of(teamMembersLens).each();

      Team team =
          new Team(
              "Dev",
              List.of(
                  new Person("Alice", 30, new Address("A", "X")),
                  new Person("Bob", 25, new Address("B", "Y"))));

      List<Integer> observedCounts = new ArrayList<>();

      TraversalPath<Team, Person> tracedPath =
          membersPath.traced((source, values) -> observedCounts.add(values.size()));

      Team updated =
          tracedPath.modifyAll(p -> new Person(p.name().toUpperCase(), p.age(), p.address()), team);

      assertThat(updated.members().stream().map(Person::name)).containsExactly("ALICE", "BOB");
      // Observer not called on modify
      assertThat(observedCounts).isEmpty();
    }

    @Test
    @DisplayName("TraversalPath.traced() with empty traversal")
    void traversalPathTracedWithEmpty() {
      TraversalPath<Team, Person> membersPath = FocusPath.of(teamMembersLens).each();

      Team emptyTeam = new Team("Empty", List.of());

      List<Integer> observedCounts = new ArrayList<>();

      TraversalPath<Team, Person> tracedPath =
          membersPath.traced((source, values) -> observedCounts.add(values.size()));

      List<Person> result = tracedPath.getAll(emptyTeam);

      assertThat(result).isEmpty();
      assertThat(observedCounts).containsExactly(0);
    }

    @Test
    @DisplayName("TracedTraversalFocusPath.setAll() should delegate to underlying without tracing")
    void tracedTraversalPathSetAll() {
      TraversalPath<Team, Person> membersPath = FocusPath.of(teamMembersLens).each();

      Team team =
          new Team(
              "Dev",
              List.of(
                  new Person("Alice", 30, new Address("A", "X")),
                  new Person("Bob", 25, new Address("B", "Y"))));

      List<Integer> observedCounts = new ArrayList<>();

      TraversalPath<Team, Person> tracedPath =
          membersPath.traced((source, values) -> observedCounts.add(values.size()));

      Person defaultPerson = new Person("Default", 0, new Address("D", "D"));
      Team updated = tracedPath.setAll(defaultPerson, team);

      assertThat(updated.members()).hasSize(2);
      assertThat(updated.members().stream().map(Person::name))
          .containsExactly("Default", "Default");
      // Observer not called on setAll
      assertThat(observedCounts).isEmpty();
    }

    @Test
    @DisplayName("TracedTraversalFocusPath.filter() should delegate to underlying")
    void tracedTraversalPathFilter() {
      TraversalPath<Team, Person> membersPath = FocusPath.of(teamMembersLens).each();

      Team team =
          new Team(
              "Dev",
              List.of(
                  new Person("Alice", 30, new Address("A", "X")),
                  new Person("Bob", 25, new Address("B", "Y")),
                  new Person("Charlie", 35, new Address("C", "Z"))));

      List<Integer> observedCounts = new ArrayList<>();

      TraversalPath<Team, Person> tracedPath =
          membersPath.traced((source, values) -> observedCounts.add(values.size()));

      TraversalPath<Team, Person> filteredPath = tracedPath.filter(p -> p.age() > 28);
      List<Person> result = filteredPath.getAll(team);

      assertThat(result).hasSize(2);
      assertThat(result.stream().map(Person::name)).containsExactly("Alice", "Charlie");
    }

    @Test
    @DisplayName("TracedTraversalFocusPath.via(Lens) should delegate to underlying")
    void tracedTraversalPathViaLens() {
      TraversalPath<Team, Person> membersPath = FocusPath.of(teamMembersLens).each();

      Team team =
          new Team(
              "Dev",
              List.of(
                  new Person("Alice", 30, new Address("A", "X")),
                  new Person("Bob", 25, new Address("B", "Y"))));

      List<Integer> observedCounts = new ArrayList<>();

      TraversalPath<Team, Person> tracedPath =
          membersPath.traced((source, values) -> observedCounts.add(values.size()));

      TraversalPath<Team, String> namesPath = tracedPath.via(personNameLens);
      List<String> names = namesPath.getAll(team);

      assertThat(names).containsExactly("Alice", "Bob");
    }

    @Test
    @DisplayName("TracedTraversalFocusPath.via(Prism) should delegate to underlying")
    void tracedTraversalPathViaPrism() {
      // Create a prism for strings that parses to integers
      Prism<String, Integer> parseIntPrism =
          Prism.of(
              s -> {
                try {
                  return Optional.of(Integer.parseInt(s));
                } catch (NumberFormatException e) {
                  return Optional.empty();
                }
              },
              Object::toString);

      Lens<Team, List<Person>> membersLensLocal =
          Lens.of(Team::members, (t, m) -> new Team(t.name(), m));

      Lens<Person, String> nameAsStringLens =
          Lens.of(Person::name, (p, n) -> new Person(n, p.age(), p.address()));

      TraversalPath<Team, Person> membersPath = FocusPath.of(membersLensLocal).each();
      TraversalPath<Team, String> namesPath = membersPath.via(nameAsStringLens);

      Team team =
          new Team(
              "Numbers",
              List.of(
                  new Person("123", 30, new Address("A", "X")),
                  new Person("notAnumber", 25, new Address("B", "Y")),
                  new Person("456", 35, new Address("C", "Z"))));

      List<Integer> observedCounts = new ArrayList<>();

      TraversalPath<Team, String> tracedPath =
          namesPath.traced((source, values) -> observedCounts.add(values.size()));

      TraversalPath<Team, Integer> numbersPath = tracedPath.via(parseIntPrism);
      List<Integer> numbers = numbersPath.getAll(team);

      assertThat(numbers).containsExactly(123, 456);
    }

    @Test
    @DisplayName("TracedTraversalFocusPath.via(Affine) should delegate to underlying")
    void tracedTraversalPathViaAffine() {
      TraversalPath<Team, Person> membersPath = FocusPath.of(teamMembersLens).each();

      // Create an affine that gets the first character of a name if non-empty
      Affine<String, Character> firstCharAffine =
          Affine.of(
              s -> s.isEmpty() ? Optional.empty() : Optional.of(s.charAt(0)),
              (s, c) -> s.isEmpty() ? s : c + s.substring(1));

      Team team =
          new Team(
              "Dev",
              List.of(
                  new Person("Alice", 30, new Address("A", "X")),
                  new Person("Bob", 25, new Address("B", "Y"))));

      List<Integer> observedCounts = new ArrayList<>();

      TraversalPath<Team, Person> tracedMembersPath =
          membersPath.traced((source, values) -> observedCounts.add(values.size()));

      TraversalPath<Team, String> namesPath = tracedMembersPath.via(personNameLens);

      // Now we need to create a traced TraversalPath for names to test via(Affine)
      TraversalPath<Team, String> tracedNamesPath =
          namesPath.traced((source, values) -> observedCounts.add(values.size()));

      TraversalPath<Team, Character> firstCharsPath = tracedNamesPath.via(firstCharAffine);
      List<Character> firstChars = firstCharsPath.getAll(team);

      assertThat(firstChars).containsExactly('A', 'B');
    }

    @Test
    @DisplayName("TracedTraversalFocusPath.via(Traversal) should delegate to underlying")
    void tracedTraversalPathViaTraversal() {
      TraversalPath<Team, Person> membersPath = FocusPath.of(teamMembersLens).each();

      Team team =
          new Team(
              "Dev",
              List.of(
                  new Person("Alice", 30, new Address("A", "X")),
                  new Person("Bob", 25, new Address("B", "Y"))));

      List<Integer> observedCounts = new ArrayList<>();

      TraversalPath<Team, Person> tracedPath =
          membersPath.traced((source, values) -> observedCounts.add(values.size()));

      // Use a traversal that returns nothing (empty traversal as identity)
      Traversal<Person, Person> identityTraversal =
          new Traversal<>() {
            @Override
            public <F extends WitnessArity<TypeArity.Unary>> Kind<F, Person> modifyF(
                Function<Person, Kind<F, Person>> f, Person source, Applicative<F> applicative) {
              return f.apply(source);
            }
          };

      TraversalPath<Team, Person> composedPath = tracedPath.via(identityTraversal);
      List<Person> result = composedPath.getAll(team);

      assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("TracedTraversalFocusPath.via(Iso) should delegate to underlying")
    void tracedTraversalPathViaIso() {
      TraversalPath<Team, Person> membersPath = FocusPath.of(teamMembersLens).each();

      Team team =
          new Team(
              "Dev",
              List.of(
                  new Person("Alice", 30, new Address("A", "X")),
                  new Person("Bob", 25, new Address("B", "Y"))));

      List<Integer> observedCounts = new ArrayList<>();

      TraversalPath<Team, Person> tracedMembersPath =
          membersPath.traced((source, values) -> observedCounts.add(values.size()));

      TraversalPath<Team, String> namesPath = tracedMembersPath.via(personNameLens);

      TraversalPath<Team, String> tracedNamesPath =
          namesPath.traced((source, values) -> observedCounts.add(values.size()));

      // Create an ISO that converts between String and its uppercase version
      Iso<String, String> upperIso = Iso.of(String::toUpperCase, String::toLowerCase);

      TraversalPath<Team, String> upperNamesPath = tracedNamesPath.via(upperIso);
      List<String> upperNames = upperNamesPath.getAll(team);

      assertThat(upperNames).containsExactly("ALICE", "BOB");
    }

    @Test
    @DisplayName("TracedTraversalFocusPath.toTraversal() should return underlying traversal")
    void tracedTraversalPathToTraversal() {
      TraversalPath<Team, Person> membersPath = FocusPath.of(teamMembersLens).each();

      Team team =
          new Team(
              "Dev",
              List.of(
                  new Person("Alice", 30, new Address("A", "X")),
                  new Person("Bob", 25, new Address("B", "Y"))));

      List<Integer> observedCounts = new ArrayList<>();

      TraversalPath<Team, Person> tracedPath =
          membersPath.traced((source, values) -> observedCounts.add(values.size()));

      Traversal<Team, Person> traversal = tracedPath.toTraversal();

      // The traversal should work correctly
      List<Person> result = Traversals.getAll(traversal, team);
      assertThat(result).hasSize(2);
      assertThat(result.stream().map(Person::name)).containsExactly("Alice", "Bob");
    }
  }
}
