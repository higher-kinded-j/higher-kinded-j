// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.optics.util.Traversals; // Assuming you have this utility class
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
    @DisplayName("andThen(Prism) should compose Lens and Prism")
    void andThenPrism() {
      Lens<Profile, Json> profileJsonLens = Lens.of(Profile::data, (p, j) -> new Profile(j));
      Prism<Json, String> jsonStringPrism =
          Prism.of(
              json -> json instanceof JsonString s ? Optional.of(s.value()) : Optional.empty(),
              JsonString::new);

      // FIX: Convert both the Lens and the Prism to Traversals
      Traversal<Profile, String> profileToStringTraversal =
          profileJsonLens.asTraversal().andThen(jsonStringPrism.asTraversal());

      Profile stringProfile = new Profile(new JsonString("some data"));
      Profile numberProfile = new Profile(new JsonNumber(123));

      Profile updatedStringProfile =
          Traversals.modify(profileToStringTraversal, String::toUpperCase, stringProfile);
      assertThat(updatedStringProfile.data()).isEqualTo(new JsonString("SOME DATA"));

      Profile updatedNumberProfile =
          Traversals.modify(profileToStringTraversal, String::toUpperCase, numberProfile);
      assertThat(updatedNumberProfile.data()).isEqualTo(new JsonNumber(123));
    }
  }
}
