// Fixture for hkj-book/src/optics/focus_navigation.md
//
// The page navigates collections, maps, nullable fields, SPI containers and
// nested containers; the records live here and the annotation processor
// generates the *Focus and *Lenses companions during snippet compilation.
//
// NOTE: imports in a fixture serve the snippet this file is spliced into (this
// one also happens to use its imports itself). Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.each.EachInstances;
import org.higherkindedj.optics.extensions.EachExtensions;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.indexed.Pair;
import org.higherkindedj.optics.util.Affines;
import org.higherkindedj.optics.util.ListPrisms;

@GenerateLenses
@GenerateFocus
record Item(String sku, double price) {}

@GenerateLenses
@GenerateFocus
record Container(List<Item> items) {}

@GenerateLenses
@GenerateFocus
record Setting(String value) {}

@GenerateLenses
@GenerateFocus
record Config(Map<String, Setting> settings) {}

@GenerateLenses
@GenerateFocus
record LegacyUser(String name, String nickname) {}

@GenerateLenses
@GenerateFocus(generateNavigators = true)
record Address(String street, String city) {}

@GenerateLenses
@GenerateFocus(generateNavigators = true)
record Employee(String name, Address workplace) {}

@GenerateLenses
@GenerateFocus(generateNavigators = true)
record Department(String name, List<Employee> employees) {}

@GenerateLenses
@GenerateFocus(generateNavigators = true)
record Company(String name, Address headquarters, List<Department> departments) {}

@GenerateLenses
@GenerateFocus(generateNavigators = true)
record Warehouse(String name, Map<String, Integer> inventory, Either<String, String> verifiedName) {}

@GenerateLenses
@GenerateFocus
record NestedConfig(
    Optional<List<String>> tags,
    List<Optional<String>> items,
    Optional<Optional<String>> nested,
    Either<String, List<Integer>> data,
    Either<String, Map<String, Integer>> meta) {}

@GenerateLenses
@GenerateFocus(widenCollections = true)
record WidenedConfig(Either<String, Map<String, Integer>> meta) {}

record Wrapper(Maybe<Setting> setting) {}

class Fixture {
  static final Item laptop = new Item("LAP-1", 999.0);

  static final Container container = new Container(List.of(laptop, new Item("MOU-1", 25.0)));

  static final Config config = new Config(Map.of("database", new Setting("primary")));

  static final Address london = new Address("1 Long Street", "London");

  static final Company company =
      new Company(
          "Acme",
          london,
          List.of(new Department("Engineering", List.of(new Employee("Alice", london)))));

  static final Warehouse warehouse =
      new Warehouse("North", Map.of("widget", 12), Either.right("North"));

  static final NestedConfig nestedConfig =
      new NestedConfig(
          Optional.of(List.of("alpha")),
          List.of(Optional.of("beta")),
          Optional.of(Optional.of("gamma")),
          Either.right(List.of(1, 2, 3)),
          Either.right(Map.of("hits", 7)));

  static final Lens<Wrapper, Maybe<Setting>> settingLens =
      Lens.of(Wrapper::setting, (w, s) -> new Wrapper(s));
}
