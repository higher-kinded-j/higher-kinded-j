// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.navigation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.each.EachInstances;
import org.higherkindedj.optics.extensions.EachExtensions;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.indexed.Pair;
import org.higherkindedj.optics.util.ListPrisms;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/focus_navigation.html">Collections,
 * Optionals and Sealed Types</a> page. The page {@code {{#include}}}s the anchored regions, and
 * {@code NavigationBookTest} holds the claims the page makes about this code.
 */
public final class NavigationBook {

  private NavigationBook() {}

  static List<List<Item>> eachGenerated(Container container) {
    // ANCHOR: each_generated
    // Generated: FocusPath.of(lens).each()
    TraversalPath<Container, Item> allItems = ContainerFocus.items();
    List<Item> items = allItems.getAll(container);

    // Applying .each() yourself, starting from the lens to the whole list
    TraversalPath<Container, Item> sameThing = FocusPath.of(ContainerLenses.items()).each();
    // ANCHOR_END: each_generated
    return List.of(items, sameThing.getAll(container));
  }

  static List<Setting> eachCustom(Config config, Wrapper wrapper) {
    // ANCHOR: each_custom
    // A Map field: traverse the values
    TraversalPath<Config, Setting> allSettings =
        ConfigFocus.settings().each(EachInstances.mapValuesEach());

    // An HKJ container held behind a hand-written lens
    Lens<Wrapper, Maybe<Setting>> settingLens =
        Lens.of(Wrapper::setting, (_, setting) -> new Wrapper(setting));
    TraversalPath<Wrapper, Setting> maybeSetting =
        FocusPath.of(settingLens).each(EachExtensions.maybeEach());
    // ANCHOR_END: each_custom
    List<Setting> found = new ArrayList<>(allSettings.getAll(config));
    found.addAll(maybeSetting.getAll(wrapper));
    return found;
  }

  /** What the page's indexing block reads. */
  record Indexed(
      Optional<Item> first, AffinePath<Container, Item> alsoFirst, Optional<Setting> setting) {}

  static Indexed byIndex(Container container, Config config) {
    // ANCHOR: by_index
    // A List field: start from the lens, because the generated path is element-level
    AffinePath<Container, Item> firstItem = FocusPath.of(ContainerLenses.items()).at(0);
    Optional<Item> first = firstItem.getOptional(container); // empty if out of bounds

    // Or narrow the generated traversal to its first element. Mind the asymmetry:
    // headOption reads the first element but writes to all of them
    AffinePath<Container, Item> alsoFirst = ContainerFocus.items().headOption();

    // A Map field: the generated path still focuses the whole map, so .atKey() applies
    AffinePath<Config, Setting> database = ConfigFocus.settings().atKey("database");
    Optional<Setting> setting = database.getOptional(config);
    // ANCHOR_END: by_index
    return new Indexed(first, alsoFirst, setting);
  }

  static List<Optional<String>> nullable() {
    // ANCHOR: nullable
    FocusPath<LegacyUser, String> rawPath = LegacyUserFocus.nickname();
    AffinePath<LegacyUser, String> safePath = rawPath.nullable();

    Optional<String> missing = safePath.getOptional(new LegacyUser("Alice", null));
    Optional<String> present = safePath.getOptional(new LegacyUser("Bob", "Bobby"));
    // ANCHOR_END: nullable
    return List.of(missing, present);
  }

  /** What the page's sealed-type block reads and writes. */
  record Variants(List<Double> radii, Drawing doubled, List<Double> sameRadii) {}

  static Variants sealedVariants(Drawing drawing) {
    // ANCHOR: sealed
    // A sealed type you own: @GeneratePrisms names each variant
    TraversalPath<Drawing, Double> circleRadii =
        DrawingFocus.shapes().via(ShapePrisms.circle()).via(CircleFocus.radius());

    List<Double> radii = circleRadii.getAll(drawing); // the squares are skipped
    Drawing doubled = circleRadii.modifyAll(radius -> radius * 2, drawing);

    // A sealed type you do not own: AffinePath.instanceOf matches by runtime type
    TraversalPath<Drawing, Double> sameRadii =
        DrawingFocus.shapes().via(AffinePath.instanceOf(Circle.class)).via(CircleFocus.radius());
    // ANCHOR_END: sealed
    return new Variants(radii, doubled, sameRadii.getAll(drawing));
  }

  /** The three paths the page's composition block builds. */
  record Composed(
      FocusPath<Company, String> hqStreet,
      AffinePath<Container, Item> firstItem,
      TraversalPath<Company, Employee> allEmployees) {}

  static Composed composeExisting() {
    // ANCHOR: compose_existing
    // Path + Lens = Path
    FocusPath<Company, String> hqStreet =
        FocusPath.of(CompanyLenses.headquarters()).via(AddressLenses.street());

    // Path + Prism or Affine = AffinePath
    AffinePath<Container, Item> firstItem =
        FocusPath.of(ContainerLenses.items()).via(ListPrisms.head());

    // Path + Traversal = TraversalPath
    TraversalPath<Company, Employee> allEmployees =
        CompanyFocus.departments().via(DepartmentFocus.employees());
    // ANCHOR_END: compose_existing
    return new Composed(hqStreet, firstItem, allEmployees);
  }

  static List<String> withAndWithoutNavigators(Company company) {
    // ANCHOR: navigator_use
    // With navigators
    String city = CompanyFocus.headquarters().city().get(company);

    // Without them, the same path, spelled out
    String same = FocusPath.of(CompanyLenses.headquarters()).via(AddressFocus.city()).get(company);
    // ANCHOR_END: navigator_use
    return List.of(city, same);
  }

  /** What the page's navigator-or-via block reads. */
  record NavigatorOrVia(String city, List<String> employeeNames) {}

  static NavigatorOrVia navigatorOrVia(Company company) {
    // ANCHOR: navigator_or_via
    // headquarters is a plain navigable field: navigator, so .city() chains
    String city = CompanyFocus.headquarters().city().get(company);

    // departments is a List: a TraversalPath, so the next hop is .via()
    List<String> employeeNames =
        CompanyFocus.departments()
            .via(DepartmentFocus.employees())
            .via(EmployeeFocus.name())
            .getAll(company);
    // ANCHOR_END: navigator_or_via
    return new NavigatorOrVia(city, employeeNames);
  }

  /** What the page's toPath block computes, and the cities its trace saw. */
  record Relocated(Company company, List<String> seen) {}

  static Relocated relocate(Company company) {
    // ANCHOR: to_path
    List<String> seen = new ArrayList<>();
    Company relocated =
        CompanyFocus.headquarters()
            .toPath()
            .traced((_, address) -> seen.add(address.city()))
            .modify(address -> AddressLenses.city().set("Manchester", address), company);
    // ANCHOR_END: to_path
    return new Relocated(relocated, seen);
  }

  /** What the page's SPI block reads and writes. */
  record Verified(Optional<String> name, Warehouse renamed, Warehouse untouched) {}

  static Verified verified(Warehouse warehouse) {
    // ANCHOR: spi_either
    // Either<String, String> field: the generated method already applies
    // .some(Affines.eitherRight()), focusing the Right value
    AffinePath<Warehouse, String> verified = WarehouseFocus.verifiedName();

    Optional<String> name = verified.getOptional(warehouse); // empty for a Left
    Warehouse renamed =
        verified.set("Northern", warehouse); // replaces a Left with Right("Northern")
    Warehouse untouched = verified.modify(String::toUpperCase, warehouse); // a no-op on a Left
    // ANCHOR_END: spi_either
    return new Verified(name, renamed, untouched);
  }

  /** What the page's list-decomposition block reads. */
  record Decomposed(Optional<Item> first, Optional<Item> last, Optional<List<Item>> tail) {}

  static Decomposed decompose(Container container) {
    // ANCHOR: list_prisms
    FocusPath<Container, List<Item>> items = FocusPath.of(ContainerLenses.items());

    AffinePath<Container, Item> firstItem = items.via(ListPrisms.head());
    Optional<Item> first = firstItem.getOptional(container);

    AffinePath<Container, Item> lastItem = items.via(ListPrisms.last());

    // Pattern match with cons (head, tail)
    AffinePath<Container, Pair<Item, List<Item>>> consPath = items.via(ListPrisms.cons());
    Optional<List<Item>> tail = consPath.getOptional(container).map(Pair::second);
    // ANCHOR_END: list_prisms
    return new Decomposed(first, lastItem.getOptional(container), tail);
  }

  static List<Integer> spiWidening(Warehouse warehouse) {
    // ANCHOR: spi_widening
    // Either is ZERO_OR_ONE via the SPI: AffinePath
    AffinePath<Warehouse, String> verified = WarehouseFocus.verifiedName();

    // Map is ZERO_OR_MORE via the SPI, but a static Focus method widens it only
    // under widenCollections; otherwise the path still focuses the whole map
    FocusPath<Warehouse, Map<String, Integer>> inventory = WarehouseFocus.inventory();
    TraversalPath<Warehouse, Integer> quantities = inventory.each(EachInstances.mapValuesEach());
    // ANCHOR_END: spi_widening
    return quantities.getAll(warehouse);
  }

  /** What the page's nested-container block reads. */
  record Nested(
      List<String> tagValues,
      Optional<String> innerValue,
      List<Integer> data,
      Optional<Map<String, Integer>> meta,
      List<Integer> hits) {}

  static Nested nested(NestedConfig nestedConfig, WidenedConfig widenedConfig) {
    // ANCHOR: nested
    TraversalPath<NestedConfig, String> allTags = NestedConfigFocus.tags();
    List<String> tagValues = allTags.getAll(nestedConfig);

    AffinePath<NestedConfig, String> nestedOpt = NestedConfigFocus.nested();
    Optional<String> innerValue = nestedOpt.getOptional(nestedConfig);

    // Either<String, List<Integer>>: a nested List is stepped into unconditionally
    TraversalPath<NestedConfig, Integer> data = NestedConfigFocus.data();

    // Either<String, Map<String, Integer>>: an SPI ZERO_OR_MORE stops at the Map...
    AffinePath<NestedConfig, Map<String, Integer>> meta = NestedConfigFocus.meta();

    // ...unless widenCollections is on
    TraversalPath<WidenedConfig, Integer> hits = WidenedConfigFocus.meta();
    // ANCHOR_END: nested
    return new Nested(
        tagValues,
        innerValue,
        data.getAll(nestedConfig),
        meta.getOptional(nestedConfig),
        hits.getAll(widenedConfig));
  }
}

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

record Wrapper(Maybe<Setting> setting) {}

@GenerateLenses
@GenerateFocus
record LegacyUser(String name, String nickname) {}

// ANCHOR: shape_records
@GeneratePrisms
sealed interface Shape permits Circle, Square {}

@GenerateFocus
record Circle(double radius) implements Shape {}

record Square(double side) implements Shape {}

@GenerateFocus
record Drawing(List<Shape> shapes) {}

// ANCHOR_END: shape_records

// ANCHOR: navigator_records
@GenerateLenses
@GenerateFocus(generateNavigators = true)
record Address(String street, String city) {}

@GenerateLenses
@GenerateFocus(generateNavigators = true)
record Company(String name, Address headquarters, List<Department> departments) {}

// ANCHOR_END: navigator_records

@GenerateLenses
@GenerateFocus(generateNavigators = true)
record Department(String name, List<Employee> employees) {}

@GenerateLenses
@GenerateFocus(generateNavigators = true)
record Employee(String name, Address workplace) {}

@GenerateLenses
@GenerateFocus(generateNavigators = true)
record Warehouse(
    String name, Map<String, Integer> inventory, Either<String, String> verifiedName) {}

@GenerateLenses
@GenerateFocus
record NestedConfig(
    Optional<List<String>> tags,
    List<Optional<String>> items,
    Optional<Optional<String>> nested,
    List<List<String>> grid,
    Optional<Either<String, String>> approval,
    Either<String, List<Integer>> data,
    Either<String, Map<String, Integer>> meta) {}

@GenerateLenses
@GenerateFocus(widenCollections = true)
record WidenedConfig(Either<String, Map<String, Integer>> meta) {}
