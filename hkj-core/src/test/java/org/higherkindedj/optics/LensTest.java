// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdSelective;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Lens<S, A> Tests")
class LensTest {

  // Test Data Structures
  record Street(String name) {}

  record Address(Street street) {}

  record User(String name, Address address) {}

  sealed interface Json permits JsonString, JsonNumber {}

  record JsonString(String value) implements Json {}

  record JsonNumber(int value) implements Json {}

  record Profile(Json data) {}

  private Lens<Street, String> streetNameLens;

  @BeforeEach
  void setUp() {
    streetNameLens = Lens.of(Street::name, (street, name) -> new Street(name));
  }

  @Nested
  @DisplayName("Core Functionality")
  class CoreFunctionality {
    @Test
    @DisplayName("of, get, and set should work correctly")
    void ofGetAndSet() {
      Street street = new Street("Main St");
      assertThat(streetNameLens.get(street)).isEqualTo("Main St");
      Street updatedStreet = streetNameLens.set("Broadway", street);
      assertThat(updatedStreet.name()).isEqualTo("Broadway");
      assertThat(street.name()).isEqualTo("Main St");
    }

    @Test
    @DisplayName("modify should apply a function to the target")
    void modify() {
      Street street = new Street("Main St");
      Street modifiedStreet = streetNameLens.modify(String::toUpperCase, street);
      assertThat(modifiedStreet.name()).isEqualTo("MAIN ST");
    }

    @Test
    @DisplayName("modifyF should work with an Applicative like Optional")
    void modifyF() {
      Street street = new Street("Main St");
      Function<String, Kind<OptionalKind.Witness, String>> successModifier =
          s -> OptionalKindHelper.OPTIONAL.widen(Optional.of(s.toUpperCase()));
      Function<String, Kind<OptionalKind.Witness, String>> failureModifier =
          s -> OptionalKindHelper.OPTIONAL.widen(Optional.empty());

      Kind<OptionalKind.Witness, Street> successResult =
          streetNameLens.modifyF(successModifier, street, OptionalMonad.INSTANCE);
      assertThat(OptionalKindHelper.OPTIONAL.narrow(successResult))
          .isPresent()
          .contains(new Street("MAIN ST"));

      Kind<OptionalKind.Witness, Street> failureResult =
          streetNameLens.modifyF(failureModifier, street, OptionalMonad.INSTANCE);
      assertThat(OptionalKindHelper.OPTIONAL.narrow(failureResult)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Conversion and Composition")
  class ConversionAndComposition {

    private final Lens<User, Address> userAddressLens =
        Lens.of(User::address, (user, address) -> new User(user.name(), address));
    private final Lens<Address, Street> addressStreetLens =
        Lens.of(Address::street, (address, street) -> new Address(street));
    private final User user = new User("John", new Address(new Street("Elm Street")));

    @Test
    @DisplayName("asTraversal should convert Lens to a working Traversal")
    void asTraversal() {
      Traversal<Street, String> traversal = streetNameLens.asTraversal();
      Street street = new Street("Elm");
      Street modified = Traversals.modify(traversal, s -> s + " St", street);
      assertThat(modified.name()).isEqualTo("Elm St");
    }

    @Test
    @DisplayName("andThen(Lens) should compose two lenses")
    void andThenLens() {
      Lens<User, String> userStreetNameLens =
          userAddressLens.andThen(addressStreetLens).andThen(streetNameLens);

      assertThat(userStreetNameLens.get(user)).isEqualTo("Elm Street");
      User updatedUser = userStreetNameLens.set("Oak Avenue", user);
      assertThat(updatedUser.address().street().name()).isEqualTo("Oak Avenue");

      Function<String, Kind<OptionalKind.Witness, String>> modifier =
          s -> OptionalKindHelper.OPTIONAL.widen(Optional.of(s.toUpperCase()));
      Kind<OptionalKind.Witness, User> modifiedUser =
          userStreetNameLens.modifyF(modifier, user, OptionalMonad.INSTANCE);
      assertThat(OptionalKindHelper.OPTIONAL.narrow(modifiedUser))
          .isPresent()
          .map(u -> u.address().street().name())
          .contains("ELM STREET");
    }

    @Test
    @DisplayName("andThen(Traversal) should compose Lens and Traversal")
    void andThenTraversal() {
      record Post(List<String> comments) {}
      Lens<Post, List<String>> postCommentsLens =
          Lens.of(Post::comments, (post, comments) -> new Post(comments));

      Traversal<List<String>, String> commentsTraversal = Traversals.forList();

      Traversal<Post, String> postToCommentTraversal =
          postCommentsLens.asTraversal().andThen(commentsTraversal);

      Post post = new Post(List.of("good", "bad"));
      Post updatedPost = Traversals.modify(postToCommentTraversal, String::toUpperCase, post);
      assertThat(updatedPost.comments()).containsExactly("GOOD", "BAD");
    }

    @Test
    @DisplayName("andThen(Prism) should compose Lens and Prism into Affine")
    void andThenPrism() {
      Lens<Profile, Json> profileJsonLens = Lens.of(Profile::data, (p, j) -> new Profile(j));
      Prism<Json, String> jsonStringPrism =
          Prism.of(
              json -> json instanceof JsonString s ? Optional.of(s.value()) : Optional.empty(),
              JsonString::new);

      // Direct composition: Lens >>> Prism = Affine
      Affine<Profile, String> profileToStringAffine = profileJsonLens.andThen(jsonStringPrism);

      Profile stringProfile = new Profile(new JsonString("some data"));
      Profile numberProfile = new Profile(new JsonNumber(123));

      Profile updatedStringProfile =
          profileToStringAffine.modify(String::toUpperCase, stringProfile);
      assertThat(updatedStringProfile.data()).isEqualTo(new JsonString("SOME DATA"));

      Profile updatedNumberProfile =
          profileToStringAffine.modify(String::toUpperCase, numberProfile);
      assertThat(updatedNumberProfile.data()).isEqualTo(new JsonNumber(123));
    }

    @Test
    @DisplayName("andThen(Affine) should compose Lens and Affine into Affine")
    void andThenAffine() {
      // Create an Affine that focuses on the value of a JsonString if present
      Affine<Json, String> jsonStringAffine =
          Affine.of(
              json -> json instanceof JsonString s ? Optional.of(s.value()) : Optional.empty(),
              (json, newValue) -> new JsonString(newValue));

      Lens<Profile, Json> profileJsonLens = Lens.of(Profile::data, (p, j) -> new Profile(j));

      // Direct composition: Lens >>> Affine = Affine
      Affine<Profile, String> composed = profileJsonLens.andThen(jsonStringAffine);

      Profile stringProfile = new Profile(new JsonString("some data"));
      Profile numberProfile = new Profile(new JsonNumber(123));

      // Test getOptional when Affine matches
      assertThat(composed.getOptional(stringProfile)).isPresent().contains("some data");

      // Test getOptional when Affine doesn't match
      assertThat(composed.getOptional(numberProfile)).isEmpty();

      // Test set - always uses the lens path
      Profile updated = composed.set("NEW DATA", stringProfile);
      assertThat(updated.data()).isEqualTo(new JsonString("NEW DATA"));
    }

    @Test
    @DisplayName("andThen(Iso) should compose Lens with Iso to produce Lens")
    void andThenIso() {
      record NameWrapper(String value) {}

      Iso<String, NameWrapper> wrapperIso = Iso.of(NameWrapper::new, NameWrapper::value);

      // Compose: Lens >>> Iso = Lens
      Lens<Street, NameWrapper> composed = streetNameLens.andThen(wrapperIso);

      Street street = new Street("Main St");

      // Test get
      NameWrapper wrapper = composed.get(street);
      assertThat(wrapper.value()).isEqualTo("Main St");

      // Test set
      Street updated = composed.set(new NameWrapper("Broadway"), street);
      assertThat(updated.name()).isEqualTo("Broadway");

      // Test modify
      Street modified = composed.modify(w -> new NameWrapper(w.value().toUpperCase()), street);
      assertThat(modified.name()).isEqualTo("MAIN ST");
    }

    @Test
    @DisplayName("andThen(Traversal) should compose Lens with Traversal directly")
    void andThenTraversalDirect() {
      record Team(String name, List<String> members) {}

      Lens<Team, List<String>> membersLens =
          Lens.of(Team::members, (team, members) -> new Team(team.name(), members));

      Traversal<List<String>, String> listTraversal = Traversals.forList();

      // Direct composition: Lens >>> Traversal = Traversal
      Traversal<Team, String> teamMembersTraversal = membersLens.andThen(listTraversal);

      Team team = new Team("Red", List.of("Alice", "Bob", "Charlie"));

      // Test getAll
      List<String> members = Traversals.getAll(teamMembersTraversal, team);
      assertThat(members).containsExactly("Alice", "Bob", "Charlie");

      // Test modify
      Team modified = Traversals.modify(teamMembersTraversal, String::toUpperCase, team);
      assertThat(modified.members()).containsExactly("ALICE", "BOB", "CHARLIE");
      assertThat(modified.name()).isEqualTo("Red");

      // Test with empty list
      Team emptyTeam = new Team("Empty", List.of());
      List<String> emptyMembers = Traversals.getAll(teamMembersTraversal, emptyTeam);
      assertThat(emptyMembers).isEmpty();

      Team modifiedEmpty = Traversals.modify(teamMembersTraversal, String::toUpperCase, emptyTeam);
      assertThat(modifiedEmpty.members()).isEmpty();
    }
  }

  @Nested
  @DisplayName("asFold Operation")
  class AsFoldOperation {

    @Test
    @DisplayName("asFold should provide a working Fold view")
    void asFold() {
      Fold<Street, String> fold = streetNameLens.asFold();
      Monoid<String> stringMonoid = Monoids.string();

      Street street = new Street("Oak Street");
      String result = fold.foldMap(stringMonoid, s -> s, street);
      assertThat(result).isEqualTo("Oak Street");
    }

    @Test
    @DisplayName("asFold should work with sum monoid")
    void asFoldWithSum() {
      Lens<Street, String> lens = streetNameLens;
      Fold<Street, String> fold = lens.asFold();
      Monoid<Integer> sumMonoid = Monoids.integerAddition();

      Street street = new Street("Hello");
      Integer result = fold.foldMap(sumMonoid, String::length, street);
      assertThat(result).isEqualTo(5);
    }
  }

  @Nested
  @DisplayName("Selective Operations")
  class SelectiveOperations {

    private final IdSelective selective = IdSelective.instance();
    private final Lens<Street, String> lens = Lens.of(Street::name, (s, n) -> new Street(n));

    @Test
    @DisplayName("setIf should set value when predicate is true")
    void setIfTrue() {
      Street street = new Street("Main St");

      Kind<IdKind.Witness, Street> result =
          lens.setIf(s -> s.contains("@"), "newemail@test.com", street, selective);

      Street resultStreet = IdKindHelper.ID.narrow(result).value();
      assertThat(resultStreet.name()).isEqualTo("newemail@test.com");
    }

    @Test
    @DisplayName("setIf should not set value when predicate is false")
    void setIfFalse() {
      Street street = new Street("Main St");

      Kind<IdKind.Witness, Street> result =
          lens.setIf(s -> s.isEmpty(), "New Street", street, selective);

      Street resultStreet = IdKindHelper.ID.narrow(result).value();
      assertThat(resultStreet.name()).isEqualTo("Main St");
    }

    @Test
    @DisplayName("modifyWhen should modify when predicate is true")
    void modifyWhenTrue() {
      Street street = new Street("abc");

      Kind<IdKind.Witness, Street> result =
          lens.modifyWhen(s -> s.length() < 10, String::toUpperCase, street, selective);

      Street resultStreet = IdKindHelper.ID.narrow(result).value();
      assertThat(resultStreet.name()).isEqualTo("ABC");
    }

    @Test
    @DisplayName("modifyWhen should not modify when predicate is false")
    void modifyWhenFalse() {
      Street street = new Street("This is a very long street name");

      Kind<IdKind.Witness, Street> result =
          lens.modifyWhen(s -> s.length() < 10, String::toUpperCase, street, selective);

      Street resultStreet = IdKindHelper.ID.narrow(result).value();
      assertThat(resultStreet.name()).isEqualTo("This is a very long street name");
    }

    @Test
    @DisplayName("modifyBranch should use thenModifier when predicate is true")
    void modifyBranchTrue() {
      Street street = new Street("short");

      Kind<IdKind.Witness, Street> result =
          lens.modifyBranch(
              s -> s.length() < 10, String::toUpperCase, s -> s + " (long)", street, selective);

      Street resultStreet = IdKindHelper.ID.narrow(result).value();
      assertThat(resultStreet.name()).isEqualTo("SHORT");
    }

    @Test
    @DisplayName("modifyBranch should use elseModifier when predicate is false")
    void modifyBranchFalse() {
      Street street = new Street("This is a long name");

      Kind<IdKind.Witness, Street> result =
          lens.modifyBranch(
              s -> s.length() < 10, String::toUpperCase, s -> s + " (long)", street, selective);

      Street resultStreet = IdKindHelper.ID.narrow(result).value();
      assertThat(resultStreet.name()).isEqualTo("This is a long name (long)");
    }
  }
}
