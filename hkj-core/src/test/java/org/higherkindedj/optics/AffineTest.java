// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Affine<S, A> Tests")
class AffineTest {

  // Test Data Structures
  record UserProfile(String name, Optional<String> email) {}

  record Config(Optional<DatabaseSettings> database) {}

  record DatabaseSettings(String host, int port) {}

  sealed interface Json permits JsonString, JsonNumber {}

  record JsonString(String value) implements Json {}

  record JsonNumber(int value) implements Json {}

  record Container(Json data) {}

  // Sealed interfaces for factory method tests (must be at class level, not local)
  sealed interface Response permits SuccessResponse, ErrorResponse {}

  record SuccessResponse(String data, int code) implements Response {}

  record ErrorResponse(String message) implements Response {}

  // Sealed interfaces for composition tests
  sealed interface ApiResponse permits Success, Failure {}

  record Success(String data, int code) implements ApiResponse {}

  record Failure(String error) implements ApiResponse {}

  private Affine<UserProfile, String> emailAffine;

  @BeforeEach
  void setUp() {
    emailAffine =
        Affine.of(
            UserProfile::email,
            (profile, newEmail) -> new UserProfile(profile.name(), Optional.of(newEmail)));
  }

  @Nested
  @DisplayName("Core Functionality")
  class CoreFunctionality {

    @Test
    @DisplayName("of, getOptional, and set should work correctly when value is present")
    void ofGetOptionalAndSetWhenPresent() {
      UserProfile profile = new UserProfile("Alice", Optional.of("alice@example.com"));

      // getOptional should return the value
      assertThat(emailAffine.getOptional(profile)).isPresent().contains("alice@example.com");

      // set should update the value
      UserProfile updated = emailAffine.set("newemail@example.com", profile);
      assertThat(updated.email()).isPresent().contains("newemail@example.com");
      assertThat(updated.name()).isEqualTo("Alice");

      // Original should be unchanged (immutability)
      assertThat(profile.email()).isPresent().contains("alice@example.com");
    }

    @Test
    @DisplayName("getOptional should return empty when value is absent")
    void getOptionalWhenAbsent() {
      UserProfile profile = new UserProfile("Bob", Optional.empty());

      assertThat(emailAffine.getOptional(profile)).isEmpty();
    }

    @Test
    @DisplayName("set should insert value even when previously absent")
    void setWhenAbsent() {
      UserProfile profile = new UserProfile("Bob", Optional.empty());

      UserProfile updated = emailAffine.set("bob@example.com", profile);
      assertThat(updated.email()).isPresent().contains("bob@example.com");
    }

    @Test
    @DisplayName("modify should apply function when value is present")
    void modifyWhenPresent() {
      UserProfile profile = new UserProfile("Alice", Optional.of("alice@example.com"));

      UserProfile modified = emailAffine.modify(String::toUpperCase, profile);
      assertThat(modified.email()).isPresent().contains("ALICE@EXAMPLE.COM");
    }

    @Test
    @DisplayName("modify should return original when value is absent")
    void modifyWhenAbsent() {
      UserProfile profile = new UserProfile("Bob", Optional.empty());

      UserProfile modified = emailAffine.modify(String::toUpperCase, profile);
      assertThat(modified).isSameAs(profile);
    }

    @Test
    @DisplayName("modifyF should work with an Applicative like Optional")
    void modifyF() {
      UserProfile profile = new UserProfile("Alice", Optional.of("alice@example.com"));

      Function<String, Kind<OptionalKind.Witness, String>> successModifier =
          s -> OptionalKindHelper.OPTIONAL.widen(Optional.of(s.toUpperCase()));
      Function<String, Kind<OptionalKind.Witness, String>> failureModifier =
          s -> OptionalKindHelper.OPTIONAL.widen(Optional.empty());

      Kind<OptionalKind.Witness, UserProfile> successResult =
          emailAffine.modifyF(successModifier, profile, OptionalMonad.INSTANCE);
      assertThat(OptionalKindHelper.OPTIONAL.narrow(successResult))
          .isPresent()
          .get()
          .extracting(p -> p.email().orElse(""))
          .isEqualTo("ALICE@EXAMPLE.COM");

      Kind<OptionalKind.Witness, UserProfile> failureResult =
          emailAffine.modifyF(failureModifier, profile, OptionalMonad.INSTANCE);
      assertThat(OptionalKindHelper.OPTIONAL.narrow(failureResult)).isEmpty();
    }

    @Test
    @DisplayName("modifyF should return original in context when value is absent")
    void modifyFWhenAbsent() {
      UserProfile profile = new UserProfile("Bob", Optional.empty());

      Function<String, Kind<OptionalKind.Witness, String>> modifier =
          s -> OptionalKindHelper.OPTIONAL.widen(Optional.of(s.toUpperCase()));

      Kind<OptionalKind.Witness, UserProfile> result =
          emailAffine.modifyF(modifier, profile, OptionalMonad.INSTANCE);

      assertThat(OptionalKindHelper.OPTIONAL.narrow(result)).isPresent().contains(profile);
    }
  }

  @Nested
  @DisplayName("Convenience Methods")
  class ConvenienceMethods {

    @Test
    @DisplayName("matches should return true when value is present")
    void matchesWhenPresent() {
      UserProfile profile = new UserProfile("Alice", Optional.of("alice@example.com"));
      assertThat(emailAffine.matches(profile)).isTrue();
    }

    @Test
    @DisplayName("matches should return false when value is absent")
    void matchesWhenAbsent() {
      UserProfile profile = new UserProfile("Bob", Optional.empty());
      assertThat(emailAffine.matches(profile)).isFalse();
    }

    @Test
    @DisplayName("doesNotMatch should be the negation of matches")
    void doesNotMatchIsNegation() {
      UserProfile present = new UserProfile("Alice", Optional.of("alice@example.com"));
      UserProfile absent = new UserProfile("Bob", Optional.empty());

      assertThat(emailAffine.doesNotMatch(present)).isFalse();
      assertThat(emailAffine.doesNotMatch(absent)).isTrue();
    }

    @Test
    @DisplayName("getOrElse should return value when present")
    void getOrElseWhenPresent() {
      UserProfile profile = new UserProfile("Alice", Optional.of("alice@example.com"));
      assertThat(emailAffine.getOrElse("default@example.com", profile))
          .isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("getOrElse should return default when absent")
    void getOrElseWhenAbsent() {
      UserProfile profile = new UserProfile("Bob", Optional.empty());
      assertThat(emailAffine.getOrElse("default@example.com", profile))
          .isEqualTo("default@example.com");
    }

    @Test
    @DisplayName("mapOptional should transform value when present")
    void mapOptionalWhenPresent() {
      UserProfile profile = new UserProfile("Alice", Optional.of("alice@example.com"));
      Optional<Integer> length = emailAffine.mapOptional(String::length, profile);
      assertThat(length).isPresent().contains(17);
    }

    @Test
    @DisplayName("mapOptional should return empty when absent")
    void mapOptionalWhenAbsent() {
      UserProfile profile = new UserProfile("Bob", Optional.empty());
      Optional<Integer> length = emailAffine.mapOptional(String::length, profile);
      assertThat(length).isEmpty();
    }

    @Test
    @DisplayName("modifyWhen should modify when present and condition met")
    void modifyWhenConditionMet() {
      UserProfile profile = new UserProfile("Alice", Optional.of("alice@example.com"));
      UserProfile result =
          emailAffine.modifyWhen(s -> s.contains("@"), String::toUpperCase, profile);
      assertThat(result.email()).isPresent().contains("ALICE@EXAMPLE.COM");
    }

    @Test
    @DisplayName("modifyWhen should not modify when condition not met")
    void modifyWhenConditionNotMet() {
      UserProfile profile = new UserProfile("Alice", Optional.of("alice@example.com"));
      UserProfile result =
          emailAffine.modifyWhen(s -> s.contains("$"), String::toUpperCase, profile);
      assertThat(result).isSameAs(profile);
    }

    @Test
    @DisplayName("modifyWhen should not modify when absent")
    void modifyWhenAbsent() {
      UserProfile profile = new UserProfile("Bob", Optional.empty());
      UserProfile result =
          emailAffine.modifyWhen(s -> s.contains("@"), String::toUpperCase, profile);
      assertThat(result).isSameAs(profile);
    }

    @Test
    @DisplayName("setWhen should set when present and condition met")
    void setWhenConditionMet() {
      UserProfile profile = new UserProfile("Alice", Optional.of("old@example.com"));
      UserProfile result = emailAffine.setWhen(s -> !s.isEmpty(), "new@example.com", profile);
      assertThat(result.email()).isPresent().contains("new@example.com");
    }

    @Test
    @DisplayName("setWhen should not set when condition not met")
    void setWhenConditionNotMet() {
      UserProfile profile = new UserProfile("Alice", Optional.of(""));
      UserProfile result = emailAffine.setWhen(s -> !s.isEmpty(), "new@example.com", profile);
      assertThat(result).isSameAs(profile);
    }

    @Test
    @DisplayName("setWhen should not set when absent")
    void setWhenAbsent() {
      UserProfile profile = new UserProfile("Bob", Optional.empty());
      UserProfile result = emailAffine.setWhen(s -> !s.isEmpty(), "new@example.com", profile);
      assertThat(result).isSameAs(profile);
    }
  }

  @Nested
  @DisplayName("Remove Operation")
  class RemoveOperation {

    @Test
    @DisplayName("remove should throw UnsupportedOperationException for basic Affine")
    void removeThrowsForBasicAffine() {
      UserProfile profile = new UserProfile("Alice", Optional.of("alice@example.com"));
      assertThatThrownBy(() -> emailAffine.remove(profile))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("remove should work when remover is provided")
    void removeWorksWithRemover() {
      Affine<UserProfile, String> removableEmailAffine =
          Affine.of(
              UserProfile::email,
              (profile, email) -> new UserProfile(profile.name(), Optional.of(email)),
              profile -> new UserProfile(profile.name(), Optional.empty()));

      UserProfile profile = new UserProfile("Alice", Optional.of("alice@example.com"));
      UserProfile result = removableEmailAffine.remove(profile);

      assertThat(result.email()).isEmpty();
      assertThat(result.name()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("3-arg factory Affine getOptional should work")
    void threeArgFactoryGetOptional() {
      Affine<UserProfile, String> removableEmailAffine =
          Affine.of(
              UserProfile::email,
              (profile, email) -> new UserProfile(profile.name(), Optional.of(email)),
              profile -> new UserProfile(profile.name(), Optional.empty()));

      UserProfile profileWithEmail = new UserProfile("Alice", Optional.of("alice@example.com"));
      UserProfile profileWithoutEmail = new UserProfile("Bob", Optional.empty());

      assertThat(removableEmailAffine.getOptional(profileWithEmail))
          .isPresent()
          .contains("alice@example.com");
      assertThat(removableEmailAffine.getOptional(profileWithoutEmail)).isEmpty();
    }

    @Test
    @DisplayName("3-arg factory Affine set should work")
    void threeArgFactorySet() {
      Affine<UserProfile, String> removableEmailAffine =
          Affine.of(
              UserProfile::email,
              (profile, email) -> new UserProfile(profile.name(), Optional.of(email)),
              profile -> new UserProfile(profile.name(), Optional.empty()));

      UserProfile profile = new UserProfile("Alice", Optional.of("old@example.com"));
      UserProfile result = removableEmailAffine.set("new@example.com", profile);

      assertThat(result.email()).isPresent().contains("new@example.com");
      assertThat(result.name()).isEqualTo("Alice");
    }
  }

  @Nested
  @DisplayName("Conversion Methods")
  class ConversionMethods {

    @Test
    @DisplayName("asTraversal should convert Affine to a working Traversal")
    void asTraversal() {
      UserProfile profile = new UserProfile("Alice", Optional.of("alice@example.com"));
      Traversal<UserProfile, String> traversal = emailAffine.asTraversal();

      List<String> emails = Traversals.getAll(traversal, profile);
      assertThat(emails).containsExactly("alice@example.com");

      UserProfile modified = Traversals.modify(traversal, String::toUpperCase, profile);
      assertThat(modified.email()).isPresent().contains("ALICE@EXAMPLE.COM");
    }

    @Test
    @DisplayName("asTraversal should work with absent values")
    void asTraversalWithAbsent() {
      UserProfile profile = new UserProfile("Bob", Optional.empty());
      Traversal<UserProfile, String> traversal = emailAffine.asTraversal();

      List<String> emails = Traversals.getAll(traversal, profile);
      assertThat(emails).isEmpty();

      UserProfile modified = Traversals.modify(traversal, String::toUpperCase, profile);
      assertThat(modified).isSameAs(profile);
    }

    @Test
    @DisplayName("asFold should provide a working Fold view")
    void asFold() {
      UserProfile profile = new UserProfile("Alice", Optional.of("alice@example.com"));
      Fold<UserProfile, String> fold = emailAffine.asFold();
      Monoid<String> stringMonoid = Monoids.string();

      String result = fold.foldMap(stringMonoid, Function.identity(), profile);
      assertThat(result).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("asFold should return monoid empty when absent")
    void asFoldWithAbsent() {
      UserProfile profile = new UserProfile("Bob", Optional.empty());
      Fold<UserProfile, String> fold = emailAffine.asFold();
      Monoid<String> stringMonoid = Monoids.string();

      String result = fold.foldMap(stringMonoid, Function.identity(), profile);
      assertThat(result).isEqualTo("");
    }
  }

  @Nested
  @DisplayName("Composition with Affine")
  class CompositionWithAffine {

    @Test
    @DisplayName("andThen(Affine) should compose two affines")
    void andThenAffine() {
      // Config -> Optional<DatabaseSettings> -> Optional<String> (simulated nested optional)
      Affine<Config, DatabaseSettings> dbAffine =
          Affine.of(Config::database, (config, db) -> new Config(Optional.of(db)));

      // Simulate an affine that might fail on the host
      Affine<DatabaseSettings, String> hostAffine =
          Affine.of(
              db -> db.host().isEmpty() ? Optional.empty() : Optional.of(db.host()),
              (db, host) -> new DatabaseSettings(host, db.port()));

      Affine<Config, String> composed = dbAffine.andThen(hostAffine);

      Config config = new Config(Optional.of(new DatabaseSettings("localhost", 5432)));
      assertThat(composed.getOptional(config)).isPresent().contains("localhost");

      Config updated = composed.set("newhost", config);
      assertThat(updated.database()).isPresent();
      assertThat(updated.database().get().host()).isEqualTo("newhost");

      Config emptyConfig = new Config(Optional.empty());
      assertThat(composed.getOptional(emptyConfig)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Composition with Lens")
  class CompositionWithLens {

    @Test
    @DisplayName("andThen(Lens) should compose Affine and Lens")
    void andThenLens() {
      Affine<Config, DatabaseSettings> dbAffine =
          Affine.of(Config::database, (config, db) -> new Config(Optional.of(db)));

      Lens<DatabaseSettings, String> hostLens =
          Lens.of(DatabaseSettings::host, (db, host) -> new DatabaseSettings(host, db.port()));

      Affine<Config, String> composed = dbAffine.andThen(hostLens);

      Config config = new Config(Optional.of(new DatabaseSettings("localhost", 5432)));
      assertThat(composed.getOptional(config)).isPresent().contains("localhost");

      Config updated = composed.set("newhost", config);
      assertThat(updated.database()).isPresent();
      assertThat(updated.database().get().host()).isEqualTo("newhost");
      assertThat(updated.database().get().port()).isEqualTo(5432);

      Config emptyConfig = new Config(Optional.empty());
      assertThat(composed.getOptional(emptyConfig)).isEmpty();
      Config unchangedEmpty = composed.set("newhost", emptyConfig);
      assertThat(unchangedEmpty).isSameAs(emptyConfig);
    }
  }

  @Nested
  @DisplayName("Composition with Prism")
  class CompositionWithPrism {

    @Test
    @DisplayName("andThen(Prism) should compose Affine and Prism")
    void andThenPrism() {
      // Container has Json data, we want to focus on JsonString value through an Affine
      Affine<Container, Json> dataAffine =
          Affine.of(c -> Optional.of(c.data()), (c, json) -> new Container(json));

      Prism<Json, String> jsonStringPrism =
          Prism.of(
              json -> json instanceof JsonString js ? Optional.of(js.value()) : Optional.empty(),
              JsonString::new);

      Affine<Container, String> composed = dataAffine.andThen(jsonStringPrism);

      Container stringContainer = new Container(new JsonString("hello"));
      assertThat(composed.getOptional(stringContainer)).isPresent().contains("hello");

      Container updated = composed.set("world", stringContainer);
      assertThat(updated.data()).isEqualTo(new JsonString("world"));

      Container numberContainer = new Container(new JsonNumber(42));
      assertThat(composed.getOptional(numberContainer)).isEmpty();

      // Set on non-matching should use prism's build
      Container builtContainer = composed.set("new", numberContainer);
      assertThat(builtContainer.data()).isEqualTo(new JsonString("new"));
    }
  }

  @Nested
  @DisplayName("Composition with Iso")
  class CompositionWithIso {

    @Test
    @DisplayName("andThen(Iso) should compose Affine and Iso")
    void andThenIso() {
      record EmailWrapper(String value) {}

      Iso<String, EmailWrapper> wrapperIso = Iso.of(EmailWrapper::new, EmailWrapper::value);

      Affine<UserProfile, EmailWrapper> composed = emailAffine.andThen(wrapperIso);

      UserProfile profile = new UserProfile("Alice", Optional.of("alice@example.com"));
      Optional<EmailWrapper> wrapper = composed.getOptional(profile);
      assertThat(wrapper).isPresent();
      assertThat(wrapper.get().value()).isEqualTo("alice@example.com");

      UserProfile updated = composed.set(new EmailWrapper("new@example.com"), profile);
      assertThat(updated.email()).isPresent().contains("new@example.com");

      UserProfile absent = new UserProfile("Bob", Optional.empty());
      assertThat(composed.getOptional(absent)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Composition with Traversal")
  class CompositionWithTraversal {

    @Test
    @DisplayName("andThen(Traversal) should compose Affine and Traversal")
    void andThenTraversal() {
      record Team(Optional<List<String>> members) {}

      Affine<Team, List<String>> membersAffine =
          Affine.of(Team::members, (team, members) -> new Team(Optional.of(members)));

      Traversal<List<String>, String> listTraversal = Traversals.forList();

      Traversal<Team, String> composed = membersAffine.andThen(listTraversal);

      Team team = new Team(Optional.of(List.of("Alice", "Bob", "Charlie")));
      List<String> members = Traversals.getAll(composed, team);
      assertThat(members).containsExactly("Alice", "Bob", "Charlie");

      Team modified = Traversals.modify(composed, String::toUpperCase, team);
      assertThat(modified.members()).isPresent();
      assertThat(modified.members().get()).containsExactly("ALICE", "BOB", "CHARLIE");

      Team emptyTeam = new Team(Optional.empty());
      List<String> emptyMembers = Traversals.getAll(composed, emptyTeam);
      assertThat(emptyMembers).isEmpty();
    }
  }

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethods {

    @Test
    @DisplayName("fromLensAndPrism should create Affine from Lens and Prism")
    void fromLensAndPrism() {
      Lens<Container, Json> dataLens = Lens.of(Container::data, (c, json) -> new Container(json));

      Prism<Json, String> jsonStringPrism =
          Prism.of(
              json -> json instanceof JsonString js ? Optional.of(js.value()) : Optional.empty(),
              JsonString::new);

      Affine<Container, String> affine = Affine.fromLensAndPrism(dataLens, jsonStringPrism);

      Container stringContainer = new Container(new JsonString("hello"));
      assertThat(affine.getOptional(stringContainer)).isPresent().contains("hello");

      Container updated = affine.set("world", stringContainer);
      assertThat(updated.data()).isEqualTo(new JsonString("world"));

      Container numberContainer = new Container(new JsonNumber(42));
      assertThat(affine.getOptional(numberContainer)).isEmpty();
    }

    @Test
    @DisplayName("fromPrismAndLens should create Affine from Prism and Lens")
    void fromPrismAndLens() {
      Prism<Response, SuccessResponse> successPrism =
          Prism.of(r -> r instanceof SuccessResponse s ? Optional.of(s) : Optional.empty(), s -> s);

      Lens<SuccessResponse, String> dataLens =
          Lens.of(SuccessResponse::data, (s, data) -> new SuccessResponse(data, s.code()));

      Affine<Response, String> affine = Affine.fromPrismAndLens(successPrism, dataLens);

      Response success = new SuccessResponse("OK", 200);
      assertThat(affine.getOptional(success)).isPresent().contains("OK");

      Response updated = affine.set("Updated", success);
      assertThat(updated).isInstanceOf(SuccessResponse.class);
      assertThat(((SuccessResponse) updated).data()).isEqualTo("Updated");

      Response error = new ErrorResponse("Not Found");
      assertThat(affine.getOptional(error)).isEmpty();

      Response unchangedError = affine.set("Ignored", error);
      assertThat(unchangedError).isSameAs(error);
    }
  }

  @Nested
  @DisplayName("Lens.andThen(Prism) returns Affine")
  class LensAndThenPrismReturnsAffine {

    @Test
    @DisplayName("Lens composed with Prism should return Affine")
    void lensAndThenPrism() {
      Lens<Container, Json> dataLens = Lens.of(Container::data, (c, json) -> new Container(json));

      Prism<Json, String> jsonStringPrism =
          Prism.of(
              json -> json instanceof JsonString js ? Optional.of(js.value()) : Optional.empty(),
              JsonString::new);

      // This should now return Affine, not Traversal
      Affine<Container, String> composed = dataLens.andThen(jsonStringPrism);

      Container stringContainer = new Container(new JsonString("hello"));
      assertThat(composed.getOptional(stringContainer)).isPresent().contains("hello");

      Container updated = composed.set("world", stringContainer);
      assertThat(updated.data()).isEqualTo(new JsonString("world"));
    }
  }

  @Nested
  @DisplayName("Prism.andThen(Lens) returns Affine")
  class PrismAndThenLensReturnsAffine {

    @Test
    @DisplayName("Prism composed with Lens should return Affine")
    void prismAndThenLens() {
      Prism<ApiResponse, Success> successPrism =
          Prism.of(r -> r instanceof Success s ? Optional.of(s) : Optional.empty(), s -> s);

      Lens<Success, String> dataLens =
          Lens.of(Success::data, (s, data) -> new Success(data, s.code()));

      // This should now return Affine, not Traversal
      Affine<ApiResponse, String> composed = successPrism.andThen(dataLens);

      ApiResponse success = new Success("OK", 200);
      assertThat(composed.getOptional(success)).isPresent().contains("OK");

      ApiResponse updated = composed.set("Updated", success);
      assertThat(updated).isInstanceOf(Success.class);
      assertThat(((Success) updated).data()).isEqualTo("Updated");

      ApiResponse failure = new Failure("Not Found");
      assertThat(composed.getOptional(failure)).isEmpty();
    }
  }
}
