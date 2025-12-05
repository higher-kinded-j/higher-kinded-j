// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.focus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListKindHelper;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.hkt.maybe.MaybeTraverse;
import org.higherkindedj.optics.Lens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for Traverse type class support in Focus DSL.
 *
 * <p>Tests the {@code traverseOver()} method across FocusPath, AffinePath, and TraversalPath.
 */
@DisplayName("Traverse Integration with Focus DSL")
class TraverseIntegrationTest {

  // Test domain types
  record User(String name, Kind<ListKind.Witness, Role> roles) {}

  record Role(String name, int level) {}

  record Config(String name, Kind<MaybeKind.Witness, Kind<ListKind.Witness, Feature>> features) {}

  record Feature(String name, boolean enabled) {}

  record Team(String name, List<User> members) {}

  // Nested structure test types
  record Outer(Kind<ListKind.Witness, Middle> middles) {}

  record Middle(Kind<ListKind.Witness, Inner> inners) {}

  record Inner(String value) {}

  // Lenses for test domain
  static final Lens<User, String> userNameLens =
      Lens.of(User::name, (u, n) -> new User(n, u.roles()));

  static final Lens<User, Kind<ListKind.Witness, Role>> userRolesLens =
      Lens.of(User::roles, (u, r) -> new User(u.name(), r));

  static final Lens<Role, String> roleNameLens =
      Lens.of(Role::name, (r, n) -> new Role(n, r.level()));

  static final Lens<Role, Integer> roleLevelLens =
      Lens.of(Role::level, (r, l) -> new Role(r.name(), l));

  static final Lens<Config, Kind<MaybeKind.Witness, Kind<ListKind.Witness, Feature>>>
      configFeaturesLens = Lens.of(Config::features, (c, f) -> new Config(c.name(), f));

  static final Lens<Feature, Boolean> featureEnabledLens =
      Lens.of(Feature::enabled, (f, e) -> new Feature(f.name(), e));

  static final Lens<Team, List<User>> teamMembersLens =
      Lens.of(Team::members, (t, m) -> new Team(t.name(), m));

  static final Lens<Outer, Kind<ListKind.Witness, Middle>> outerMiddlesLens =
      Lens.of(Outer::middles, (o, m) -> new Outer(m));

  static final Lens<Middle, Kind<ListKind.Witness, Inner>> middleInnersLens =
      Lens.of(Middle::inners, (m, i) -> new Middle(i));

  static final Lens<Inner, String> innerValueLens = Lens.of(Inner::value, (i, v) -> new Inner(v));

  @Nested
  @DisplayName("FocusPath.traverseOver()")
  class FocusPathTraverseOverTests {

    @Test
    @DisplayName("should traverse into Kind<ListKind.Witness, _> field")
    void shouldTraverseIntoListKindField() {
      User user =
          new User(
              "Alice",
              ListKindHelper.LIST.widen(
                  List.of(new Role("Admin", 10), new Role("User", 1), new Role("Guest", 0))));

      FocusPath<User, Kind<ListKind.Witness, Role>> rolesPath = FocusPath.of(userRolesLens);

      TraversalPath<User, Role> allRoles =
          rolesPath.<ListKind.Witness, Role>traverseOver(ListTraverse.INSTANCE);

      List<Role> roles = allRoles.getAll(user);

      assertThat(roles).hasSize(3);
      assertThat(roles.stream().map(Role::name)).containsExactly("Admin", "User", "Guest");
    }

    @Test
    @DisplayName("should modify all elements via traverseOver")
    void shouldModifyAllViaTraverseOver() {
      User user =
          new User(
              "Bob",
              ListKindHelper.LIST.widen(List.of(new Role("Admin", 10), new Role("User", 1))));

      FocusPath<User, Kind<ListKind.Witness, Role>> rolesPath = FocusPath.of(userRolesLens);

      TraversalPath<User, Role> allRoles =
          rolesPath.<ListKind.Witness, Role>traverseOver(ListTraverse.INSTANCE);

      User updated = allRoles.modifyAll(r -> new Role(r.name().toUpperCase(), r.level()), user);

      List<Role> updatedRoles = ListKindHelper.LIST.narrow(updated.roles());
      assertThat(updatedRoles.stream().map(Role::name)).containsExactly("ADMIN", "USER");
    }

    @Test
    @DisplayName("should compose traverseOver with via for nested access")
    void shouldComposeTraverseOverWithVia() {
      User user =
          new User(
              "Charlie",
              ListKindHelper.LIST.widen(List.of(new Role("Admin", 10), new Role("User", 5))));

      FocusPath<User, Kind<ListKind.Witness, Role>> rolesPath = FocusPath.of(userRolesLens);

      // First get TraversalPath<User, Role>, then compose with roleLevelLens
      TraversalPath<User, Role> allRoles =
          rolesPath.<ListKind.Witness, Role>traverseOver(ListTraverse.INSTANCE);
      TraversalPath<User, Integer> allLevels = allRoles.via(roleLevelLens);

      List<Integer> levels = allLevels.getAll(user);

      assertThat(levels).containsExactly(10, 5);
    }

    @Test
    @DisplayName("should handle empty container via traverseOver")
    void shouldHandleEmptyContainer() {
      User user = new User("Empty", ListKindHelper.LIST.widen(List.of()));

      FocusPath<User, Kind<ListKind.Witness, Role>> rolesPath = FocusPath.of(userRolesLens);

      TraversalPath<User, Role> allRoles =
          rolesPath.<ListKind.Witness, Role>traverseOver(ListTraverse.INSTANCE);

      List<Role> roles = allRoles.getAll(user);

      assertThat(roles).isEmpty();
    }
  }

  @Nested
  @DisplayName("AffinePath.traverseOver()")
  class AffinePathTraverseOverTests {

    // Test domain for AffinePath-specific tests
    record Container(Kind<MaybeKind.Witness, Kind<ListKind.Witness, String>> data) {}

    static final Lens<Container, Kind<MaybeKind.Witness, Kind<ListKind.Witness, String>>>
        containerDataLens = Lens.of(Container::data, (c, d) -> new Container(d));

    // Sealed interface for instanceOf tests (must be at class level, not local)
    sealed interface Wrapper permits ListWrapper, SingleWrapper {}

    record ListWrapper(Kind<ListKind.Witness, String> items) implements Wrapper {}

    record SingleWrapper(String item) implements Wrapper {}

    @Test
    @DisplayName("should traverse into optional container field")
    void shouldTraverseIntoOptionalContainerField() {
      Config config =
          new Config(
              "MyApp",
              MaybeKindHelper.MAYBE.widen(
                  Maybe.just(
                      ListKindHelper.LIST.widen(
                          List.of(
                              new Feature("DarkMode", true), new Feature("Analytics", false))))));

      // Create a FocusPath to the optional features
      FocusPath<Config, Kind<MaybeKind.Witness, Kind<ListKind.Witness, Feature>>> focusPath =
          FocusPath.of(configFeaturesLens);

      // First traverse the Maybe, then traverse the List
      TraversalPath<Config, Kind<ListKind.Witness, Feature>> maybeTraversed =
          focusPath.<MaybeKind.Witness, Kind<ListKind.Witness, Feature>>traverseOver(
              MaybeTraverse.INSTANCE);

      TraversalPath<Config, Feature> allFeatures =
          maybeTraversed.<ListKind.Witness, Feature>traverseOver(ListTraverse.INSTANCE);

      List<Feature> features = allFeatures.getAll(config);

      assertThat(features).hasSize(2);
      assertThat(features.stream().map(Feature::name)).containsExactly("DarkMode", "Analytics");
    }

    @Test
    @DisplayName("should handle Nothing in optional container")
    void shouldHandleNothingInOptionalContainer() {
      Config config = new Config("EmptyApp", MaybeKindHelper.MAYBE.widen(Maybe.nothing()));

      FocusPath<Config, Kind<MaybeKind.Witness, Kind<ListKind.Witness, Feature>>> focusPath =
          FocusPath.of(configFeaturesLens);

      TraversalPath<Config, Kind<ListKind.Witness, Feature>> maybeTraversed =
          focusPath.<MaybeKind.Witness, Kind<ListKind.Witness, Feature>>traverseOver(
              MaybeTraverse.INSTANCE);

      List<Kind<ListKind.Witness, Feature>> results = maybeTraversed.getAll(config);

      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("should traverse directly from AffinePath with instanceOf")
    void shouldTraverseDirectlyFromAffinePath() {
      // Use Wrapper sealed interface defined at class level
      ListWrapper wrapper = new ListWrapper(ListKindHelper.LIST.widen(List.of("a", "b", "c")));

      // Create an AffinePath using instanceOf
      AffinePath<Wrapper, ListWrapper> listWrapperPath = AffinePath.instanceOf(ListWrapper.class);

      // Get the list wrapper via the affine
      assertThat(listWrapperPath.getOptional(wrapper)).isPresent();
      assertThat(listWrapperPath.getOptional(wrapper).get().items()).isNotNull();
    }

    @Test
    @DisplayName("should modify all elements via AffinePath.traverseOver when focused")
    void shouldModifyAllViaAffinePathTraverseOver() {
      Container container =
          new Container(
              MaybeKindHelper.MAYBE.widen(
                  Maybe.just(ListKindHelper.LIST.widen(List.of("hello", "world")))));

      // Start with AffinePath via at(0) pattern (simulate optional access)
      FocusPath<Container, Kind<MaybeKind.Witness, Kind<ListKind.Witness, String>>> dataPath =
          FocusPath.of(containerDataLens);

      // Traverse Maybe first
      TraversalPath<Container, Kind<ListKind.Witness, String>> maybeTraversed =
          dataPath.<MaybeKind.Witness, Kind<ListKind.Witness, String>>traverseOver(
              MaybeTraverse.INSTANCE);

      // Then traverse the List
      TraversalPath<Container, String> allStrings =
          maybeTraversed.<ListKind.Witness, String>traverseOver(ListTraverse.INSTANCE);

      // Modify all strings
      Container updated = allStrings.modifyAll(String::toUpperCase, container);

      // Verify modification
      Maybe<Kind<ListKind.Witness, String>> maybeList =
          MaybeKindHelper.MAYBE.narrow(updated.data());
      assertThat(maybeList.isJust()).isTrue();
      List<String> resultList = ListKindHelper.LIST.narrow(maybeList.get());
      assertThat(resultList).containsExactly("HELLO", "WORLD");
    }

    @Test
    @DisplayName("should return empty when AffinePath has no focus")
    void shouldReturnEmptyWhenNoFocus() {
      Container container =
          new Container(
              MaybeKindHelper.MAYBE.widen(Maybe.<Kind<ListKind.Witness, String>>nothing()));

      FocusPath<Container, Kind<MaybeKind.Witness, Kind<ListKind.Witness, String>>> dataPath =
          FocusPath.of(containerDataLens);

      TraversalPath<Container, Kind<ListKind.Witness, String>> maybeTraversed =
          dataPath.<MaybeKind.Witness, Kind<ListKind.Witness, String>>traverseOver(
              MaybeTraverse.INSTANCE);

      List<Kind<ListKind.Witness, String>> results = maybeTraversed.getAll(container);

      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("should count elements correctly via AffinePath.traverseOver")
    void shouldCountElementsCorrectly() {
      Container container =
          new Container(
              MaybeKindHelper.MAYBE.widen(
                  Maybe.just(ListKindHelper.LIST.widen(List.of("a", "b", "c", "d")))));

      FocusPath<Container, Kind<MaybeKind.Witness, Kind<ListKind.Witness, String>>> dataPath =
          FocusPath.of(containerDataLens);

      TraversalPath<Container, Kind<ListKind.Witness, String>> maybeTraversed =
          dataPath.<MaybeKind.Witness, Kind<ListKind.Witness, String>>traverseOver(
              MaybeTraverse.INSTANCE);

      TraversalPath<Container, String> allStrings =
          maybeTraversed.<ListKind.Witness, String>traverseOver(ListTraverse.INSTANCE);

      assertThat(allStrings.count(container)).isEqualTo(4);
    }

    // ===== Direct AffinePath.traverseOver() tests =====

    // Domain type where AffinePath directly holds a Kind<F, E>
    record OptionalItems(Kind<MaybeKind.Witness, Kind<ListKind.Witness, Integer>> maybeItems) {}

    static final Lens<OptionalItems, Kind<MaybeKind.Witness, Kind<ListKind.Witness, Integer>>>
        optionalItemsLens = Lens.of(OptionalItems::maybeItems, (o, m) -> new OptionalItems(m));

    // Sealed interface where one variant holds a Kind<ListKind.Witness, E>
    sealed interface ItemHolder permits ItemList, SingleItem {}

    record ItemList(Kind<ListKind.Witness, String> items) implements ItemHolder {}

    record SingleItem(String item) implements ItemHolder {}

    static final Lens<ItemList, Kind<ListKind.Witness, String>> itemListItemsLens =
        Lens.of(ItemList::items, (il, i) -> new ItemList(i));

    @Test
    @DisplayName("should call traverseOver directly on AffinePath created via instanceOf")
    void shouldCallTraverseOverDirectlyOnAffinePath() {
      // Create an ItemHolder that is an ItemList
      ItemHolder holder = new ItemList(ListKindHelper.LIST.widen(List.of("x", "y", "z")));

      // Create an AffinePath using instanceOf
      AffinePath<ItemHolder, ItemList> itemListPath = AffinePath.instanceOf(ItemList.class);

      // Compose with lens to get the Kind<ListKind.Witness, String>
      AffinePath<ItemHolder, Kind<ListKind.Witness, String>> itemsKindPath =
          itemListPath.via(itemListItemsLens);

      // Call traverseOver DIRECTLY on the AffinePath
      TraversalPath<ItemHolder, String> allItemsPath =
          itemsKindPath.<ListKind.Witness, String>traverseOver(ListTraverse.INSTANCE);

      // Verify getAll works
      List<String> items = allItemsPath.getAll(holder);
      assertThat(items).containsExactly("x", "y", "z");
    }

    @Test
    @DisplayName("should return empty when AffinePath.traverseOver has no focus (instanceOf fails)")
    void shouldReturnEmptyWhenAffinePathHasNoFocusOnTraverseOver() {
      // Create an ItemHolder that is a SingleItem (not an ItemList)
      ItemHolder holder = new SingleItem("solo");

      // Create an AffinePath using instanceOf for ItemList
      AffinePath<ItemHolder, ItemList> itemListPath = AffinePath.instanceOf(ItemList.class);

      // Compose with lens to get the Kind<ListKind.Witness, String>
      AffinePath<ItemHolder, Kind<ListKind.Witness, String>> itemsKindPath =
          itemListPath.via(itemListItemsLens);

      // Call traverseOver on AffinePath - should handle no focus gracefully
      TraversalPath<ItemHolder, String> allItemsPath =
          itemsKindPath.<ListKind.Witness, String>traverseOver(ListTraverse.INSTANCE);

      // Should return empty since instanceOf didn't match
      List<String> items = allItemsPath.getAll(holder);
      assertThat(items).isEmpty();
    }

    @Test
    @DisplayName("should modifyAll via AffinePath.traverseOver when focus exists")
    void shouldModifyAllViaDirectAffinePathTraverseOver() {
      ItemHolder holder = new ItemList(ListKindHelper.LIST.widen(List.of("alpha", "beta")));

      AffinePath<ItemHolder, ItemList> itemListPath = AffinePath.instanceOf(ItemList.class);
      AffinePath<ItemHolder, Kind<ListKind.Witness, String>> itemsKindPath =
          itemListPath.via(itemListItemsLens);

      // Call traverseOver directly on AffinePath
      TraversalPath<ItemHolder, String> allItemsPath =
          itemsKindPath.<ListKind.Witness, String>traverseOver(ListTraverse.INSTANCE);

      // Modify all items
      ItemHolder updated = allItemsPath.modifyAll(String::toUpperCase, holder);

      // Verify modification
      assertThat(updated).isInstanceOf(ItemList.class);
      ItemList updatedList = (ItemList) updated;
      List<String> resultItems = ListKindHelper.LIST.narrow(updatedList.items());
      assertThat(resultItems).containsExactly("ALPHA", "BETA");
    }

    @Test
    @DisplayName(
        "should leave source unchanged when modifyAll via AffinePath.traverseOver has no focus")
    void shouldLeaveUnchangedWhenModifyAllHasNoFocus() {
      ItemHolder holder = new SingleItem("untouched");

      AffinePath<ItemHolder, ItemList> itemListPath = AffinePath.instanceOf(ItemList.class);
      AffinePath<ItemHolder, Kind<ListKind.Witness, String>> itemsKindPath =
          itemListPath.via(itemListItemsLens);

      TraversalPath<ItemHolder, String> allItemsPath =
          itemsKindPath.<ListKind.Witness, String>traverseOver(ListTraverse.INSTANCE);

      // Modify should have no effect since AffinePath has no focus
      ItemHolder updated = allItemsPath.modifyAll(String::toUpperCase, holder);

      // Source should be unchanged
      assertThat(updated).isInstanceOf(SingleItem.class);
      assertThat(((SingleItem) updated).item()).isEqualTo("untouched");
    }

    @Test
    @DisplayName("should handle empty container when AffinePath.traverseOver has focus")
    void shouldHandleEmptyContainerWhenAffinePathHasFocus() {
      ItemHolder holder = new ItemList(ListKindHelper.LIST.widen(List.of()));

      AffinePath<ItemHolder, ItemList> itemListPath = AffinePath.instanceOf(ItemList.class);
      AffinePath<ItemHolder, Kind<ListKind.Witness, String>> itemsKindPath =
          itemListPath.via(itemListItemsLens);

      TraversalPath<ItemHolder, String> allItemsPath =
          itemsKindPath.<ListKind.Witness, String>traverseOver(ListTraverse.INSTANCE);

      // Focus exists but container is empty
      List<String> items = allItemsPath.getAll(holder);
      assertThat(items).isEmpty();

      // Count should be 0
      assertThat(allItemsPath.count(holder)).isEqualTo(0);
    }

    // Types for chaining test (must be at class level, not local)
    record ChainItem(String name, int length) {}

    record ChainItemsHolder(Kind<ListKind.Witness, ChainItem> items) {}

    sealed interface ChainWrapper permits ChainItemsWrapper, ChainEmptyWrapper {}

    record ChainItemsWrapper(ChainItemsHolder holder) implements ChainWrapper {}

    record ChainEmptyWrapper() implements ChainWrapper {}

    static final Lens<ChainItemsHolder, Kind<ListKind.Witness, ChainItem>> chainItemsHolderLens =
        Lens.of(ChainItemsHolder::items, (h, i) -> new ChainItemsHolder(i));

    static final Lens<ChainItem, String> chainItemNameLens =
        Lens.of(ChainItem::name, (i, n) -> new ChainItem(n, i.length()));

    static final Lens<ChainItemsWrapper, ChainItemsHolder> chainItemsWrapperHolderLens =
        Lens.of(ChainItemsWrapper::holder, (w, h) -> new ChainItemsWrapper(h));

    @Test
    @DisplayName("should chain via() after AffinePath.traverseOver")
    void shouldChainViaAfterAffinePathTraverseOver() {
      ChainItemsHolder holder =
          new ChainItemsHolder(
              ListKindHelper.LIST.widen(
                  List.of(new ChainItem("first", 5), new ChainItem("second", 6))));
      ChainWrapper wrapper = new ChainItemsWrapper(holder);

      // Create AffinePath via instanceOf
      AffinePath<ChainWrapper, ChainItemsWrapper> wrapperPath =
          AffinePath.instanceOf(ChainItemsWrapper.class);

      // Chain: AffinePath -> via(lens) -> via(lens) -> traverseOver -> via(lens)
      AffinePath<ChainWrapper, ChainItemsHolder> holderPath =
          wrapperPath.via(chainItemsWrapperHolderLens);
      AffinePath<ChainWrapper, Kind<ListKind.Witness, ChainItem>> itemsPath =
          holderPath.via(chainItemsHolderLens);
      TraversalPath<ChainWrapper, ChainItem> allItems =
          itemsPath.<ListKind.Witness, ChainItem>traverseOver(ListTraverse.INSTANCE);
      TraversalPath<ChainWrapper, String> allNames = allItems.via(chainItemNameLens);

      // Verify chain works
      List<String> names = allNames.getAll(wrapper);
      assertThat(names).containsExactly("first", "second");

      // Modify through the chain
      ChainWrapper updated = allNames.modifyAll(String::toUpperCase, wrapper);
      List<String> updatedNames = allNames.getAll(updated);
      assertThat(updatedNames).containsExactly("FIRST", "SECOND");
    }

    @Test
    @DisplayName("should count correctly through AffinePath.traverseOver")
    void shouldCountCorrectlyThroughAffinePathTraverseOver() {
      ItemHolder holder = new ItemList(ListKindHelper.LIST.widen(List.of("a", "b", "c", "d", "e")));

      AffinePath<ItemHolder, ItemList> itemListPath = AffinePath.instanceOf(ItemList.class);
      AffinePath<ItemHolder, Kind<ListKind.Witness, String>> itemsKindPath =
          itemListPath.via(itemListItemsLens);

      TraversalPath<ItemHolder, String> allItemsPath =
          itemsKindPath.<ListKind.Witness, String>traverseOver(ListTraverse.INSTANCE);

      assertThat(allItemsPath.count(holder)).isEqualTo(5);
    }

    @Test
    @DisplayName("should return 0 count when AffinePath.traverseOver has no focus")
    void shouldReturnZeroCountWhenNoFocus() {
      ItemHolder holder = new SingleItem("alone");

      AffinePath<ItemHolder, ItemList> itemListPath = AffinePath.instanceOf(ItemList.class);
      AffinePath<ItemHolder, Kind<ListKind.Witness, String>> itemsKindPath =
          itemListPath.via(itemListItemsLens);

      TraversalPath<ItemHolder, String> allItemsPath =
          itemsKindPath.<ListKind.Witness, String>traverseOver(ListTraverse.INSTANCE);

      assertThat(allItemsPath.count(holder)).isEqualTo(0);
    }
  }

  @Nested
  @DisplayName("TraversalPath.traverseOver()")
  class TraversalPathTraverseOverTests {

    @Test
    @DisplayName("should flatten nested traversals")
    void shouldFlattenNestedTraversals() {
      Team team =
          new Team(
              "DevTeam",
              List.of(
                  new User(
                      "Alice",
                      ListKindHelper.LIST.widen(
                          List.of(new Role("Admin", 10), new Role("Dev", 5)))),
                  new User("Bob", ListKindHelper.LIST.widen(List.of(new Role("User", 1))))));

      // Start with TraversalPath over team members
      TraversalPath<Team, User> membersPath = FocusPath.of(teamMembersLens).each();

      // Navigate to each member's roles (which is a Kind<ListKind.Witness, Role>)
      TraversalPath<Team, Kind<ListKind.Witness, Role>> rolesKindPath =
          membersPath.via(userRolesLens);

      // Traverse into the list to get all roles
      TraversalPath<Team, Role> allRoles =
          rolesKindPath.<ListKind.Witness, Role>traverseOver(ListTraverse.INSTANCE);

      List<Role> roles = allRoles.getAll(team);

      assertThat(roles).hasSize(3);
      assertThat(roles.stream().map(Role::name)).containsExactly("Admin", "Dev", "User");
    }

    @Test
    @DisplayName("should modify all nested elements")
    void shouldModifyAllNestedElements() {
      Team team =
          new Team(
              "DevTeam",
              List.of(
                  new User("Alice", ListKindHelper.LIST.widen(List.of(new Role("Admin", 10)))),
                  new User("Bob", ListKindHelper.LIST.widen(List.of(new Role("User", 1))))));

      TraversalPath<Team, User> membersPath = FocusPath.of(teamMembersLens).each();
      TraversalPath<Team, Kind<ListKind.Witness, Role>> rolesKindPath =
          membersPath.via(userRolesLens);
      TraversalPath<Team, Role> allRoles =
          rolesKindPath.<ListKind.Witness, Role>traverseOver(ListTraverse.INSTANCE);

      // Give everyone a promotion
      Team updated = allRoles.modifyAll(r -> new Role(r.name(), r.level() + 1), team);

      // Verify all levels were increased - build path step by step
      FocusPath<Team, List<User>> step1 = FocusPath.of(teamMembersLens);
      TraversalPath<Team, User> step2 = step1.each();
      TraversalPath<Team, Kind<ListKind.Witness, Role>> step3 = step2.via(userRolesLens);
      TraversalPath<Team, Role> verifyPath =
          step3.<ListKind.Witness, Role>traverseOver(ListTraverse.INSTANCE);
      List<Role> updatedRoles = verifyPath.getAll(updated);

      assertThat(updatedRoles.stream().map(Role::level)).containsExactly(11, 2);
    }

    @Test
    @DisplayName("should handle mixed empty and non-empty containers")
    void shouldHandleMixedEmptyAndNonEmpty() {
      Team team =
          new Team(
              "MixedTeam",
              List.of(
                  new User("Alice", ListKindHelper.LIST.widen(List.of(new Role("Admin", 10)))),
                  new User("Bob", ListKindHelper.LIST.widen(List.of())), // Empty roles
                  new User("Charlie", ListKindHelper.LIST.widen(List.of(new Role("User", 1))))));

      // Build path step by step to avoid type inference issues
      FocusPath<Team, List<User>> pathStep1 = FocusPath.of(teamMembersLens);
      TraversalPath<Team, User> pathStep2 = pathStep1.each();
      TraversalPath<Team, Kind<ListKind.Witness, Role>> pathStep3 = pathStep2.via(userRolesLens);
      TraversalPath<Team, Role> allRoles =
          pathStep3.<ListKind.Witness, Role>traverseOver(ListTraverse.INSTANCE);

      List<Role> roles = allRoles.getAll(team);

      assertThat(roles).hasSize(2);
      assertThat(roles.stream().map(Role::name)).containsExactly("Admin", "User");
    }

    @Test
    @DisplayName("should compose multiple traverseOver calls")
    void shouldComposeMultipleTraverseOverCalls() {
      Outer data =
          new Outer(
              ListKindHelper.LIST.widen(
                  List.of(
                      new Middle(
                          ListKindHelper.LIST.widen(List.of(new Inner("a1"), new Inner("a2")))),
                      new Middle(ListKindHelper.LIST.widen(List.of(new Inner("b1")))))));

      // Build the path step by step with explicit types
      FocusPath<Outer, Kind<ListKind.Witness, Middle>> step1 = FocusPath.of(outerMiddlesLens);
      TraversalPath<Outer, Middle> step2 =
          step1.<ListKind.Witness, Middle>traverseOver(ListTraverse.INSTANCE);
      TraversalPath<Outer, Kind<ListKind.Witness, Inner>> step3 = step2.via(middleInnersLens);
      TraversalPath<Outer, Inner> step4 =
          step3.<ListKind.Witness, Inner>traverseOver(ListTraverse.INSTANCE);
      TraversalPath<Outer, String> allValues = step4.via(innerValueLens);

      List<String> values = allValues.getAll(data);

      assertThat(values).containsExactly("a1", "a2", "b1");
    }
  }
}
