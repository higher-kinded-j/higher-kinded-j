// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.IdPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.OptionalPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.junit.jupiter.api.Test;

/**
 * Solutions for Tutorial 14: Focus-Effect Bridge
 *
 * <p>These are the completed solutions for all exercises in Tutorial14_FocusEffectBridge.java
 */
public class Tutorial14_FocusEffectBridge_Solution {

  // Test Data
  record User(String name, Optional<String> email) {}

  record Address(String street, String city) {}

  record Company(String name, Address headquarters, List<User> employees) {}

  static final Lens<User, String> userNameLens =
      Lens.of(User::name, (u, n) -> new User(n, u.email()));

  static final Lens<User, Optional<String>> userEmailLens =
      Lens.of(User::email, (u, e) -> new User(u.name(), e));

  static final Lens<Company, Address> companyAddressLens =
      Lens.of(Company::headquarters, (c, a) -> new Company(c.name(), a, c.employees()));

  static final Lens<Address, String> addressCityLens =
      Lens.of(Address::city, (a, c) -> new Address(a.street(), c));

  static final Lens<Company, List<User>> companyEmployeesLens =
      Lens.of(Company::employees, (c, e) -> new Company(c.name(), c.headquarters(), e));

  // ═══════════════════════════════════════════════════════════════════════
  // Part 1: FocusPath -> EffectPath Bridge
  // ═══════════════════════════════════════════════════════════════════════

  @Test
  void exercise1_focusPathToMaybePath() {
    FocusPath<User, String> namePath = FocusPath.of(userNameLens);
    User alice = new User("Alice", Optional.of("alice@example.com"));

    // SOLUTION: Use toMaybePath to lift into Maybe effect
    MaybePath<String> maybeNamePath = namePath.toMaybePath(alice);

    assertThat(maybeNamePath.run().isJust()).isTrue();
    assertThat(maybeNamePath.getOrElse("default")).isEqualTo("Alice");

    // SOLUTION: Chain with map
    MaybePath<String> upperPath = maybeNamePath.map(String::toUpperCase);
    assertThat(upperPath.getOrElse("default")).isEqualTo("ALICE");
  }

  @Test
  void exercise2_focusPathToEitherPath() {
    FocusPath<Company, Address> addressPath = FocusPath.of(companyAddressLens);
    Company company = new Company("TechCorp", new Address("123 Main St", "London"), List.of());

    // SOLUTION: Use toEitherPath to lift into Either effect
    EitherPath<String, Address> eitherAddressPath = addressPath.toEitherPath(company);

    assertThat(eitherAddressPath.run().isRight()).isTrue();

    // SOLUTION: Chain with via to navigate deeper
    FocusPath<Address, String> cityPath = FocusPath.of(addressCityLens);
    EitherPath<String, String> cityEffectPath =
        eitherAddressPath.via(addr -> cityPath.toEitherPath(addr));

    String cityResult = cityEffectPath.run().fold(e -> e, c -> c);
    assertThat(cityResult).isEqualTo("London");
  }

  @Test
  void exercise3_affinePathToMaybePath() {
    AffinePath<User, String> emailPath = FocusPath.of(userEmailLens).some();

    User withEmail = new User("Alice", Optional.of("alice@example.com"));
    User withoutEmail = new User("Bob", Optional.empty());

    // SOLUTION: AffinePath.toMaybePath returns Just when present
    MaybePath<String> presentPath = emailPath.toMaybePath(withEmail);
    assertThat(presentPath.run().isJust()).isTrue();
    assertThat(presentPath.getOrElse("no-email")).isEqualTo("alice@example.com");

    // SOLUTION: Returns Nothing when absent
    MaybePath<String> absentPath = emailPath.toMaybePath(withoutEmail);
    assertThat(absentPath.run().isNothing()).isTrue();
    assertThat(absentPath.getOrElse("no-email")).isEqualTo("no-email");
  }

  @Test
  void exercise4_affinePathToEitherPathWithError() {
    AffinePath<User, String> emailPath = FocusPath.of(userEmailLens).some();

    User withEmail = new User("Alice", Optional.of("alice@example.com"));
    User withoutEmail = new User("Bob", Optional.empty());

    // SOLUTION: toEitherPath with error for absent case
    EitherPath<String, String> presentPath =
        emailPath.toEitherPath(withEmail, "Email not configured");
    assertThat(presentPath.run().isRight()).isTrue();

    // SOLUTION: Returns Left with error when absent
    EitherPath<String, String> absentPath =
        emailPath.toEitherPath(withoutEmail, "Email not configured");
    assertThat(absentPath.run().isLeft()).isTrue();
    String errorResult = absentPath.run().fold(e -> e, v -> v);
    assertThat(errorResult).isEqualTo("Email not configured");
  }

  @Test
  void exercise5_traversalPathToListPath() {
    TraversalPath<Company, User> employeesPath = FocusPath.of(companyEmployeesLens).each();

    Company company =
        new Company(
            "TechCorp",
            new Address("123 Main St", "London"),
            List.of(
                new User("Alice", Optional.of("alice@example.com")),
                new User("Bob", Optional.empty()),
                new User("Charlie", Optional.of("charlie@example.com"))));

    // SOLUTION: Use toListPath to get all focused values wrapped in ListPath
    var employeeListPath = employeesPath.toListPath(company);

    List<User> employees = employeesPath.getAll(company);
    assertThat(employees).hasSize(3);
    assertThat(employees.get(0).name()).isEqualTo("Alice");
  }

  // ═══════════════════════════════════════════════════════════════════════
  // Part 2: EffectPath.focus() - Optics Within Effects
  // ═══════════════════════════════════════════════════════════════════════

  @Test
  void exercise6_maybePathFocus() {
    FocusPath<User, String> namePath = FocusPath.of(userNameLens);
    MaybePath<User> userPath = Path.just(new User("Alice", Optional.of("alice@example.com")));

    // SOLUTION: Use focus() to navigate within effect
    MaybePath<String> nameInEffect = userPath.focus(namePath);

    assertThat(nameInEffect.run().isJust()).isTrue();
    assertThat(nameInEffect.getOrElse("unknown")).isEqualTo("Alice");

    MaybePath<User> emptyPath = Path.nothing();
    MaybePath<String> focusedEmpty = emptyPath.focus(namePath);
    assertThat(focusedEmpty.run().isNothing()).isTrue();
  }

  @Test
  void exercise7_eitherPathFocus() {
    FocusPath<Address, String> cityPath = FocusPath.of(addressCityLens);

    EitherPath<String, Address> successPath = Path.right(new Address("123 Main St", "London"));
    EitherPath<String, Address> failurePath = Path.left("Address not found");

    // SOLUTION: focus on success case
    EitherPath<String, String> cityFromSuccess = successPath.focus(cityPath);
    String successResult = cityFromSuccess.run().fold(e -> e, c -> c);
    assertThat(successResult).isEqualTo("London");

    // SOLUTION: focus preserves failure
    EitherPath<String, String> cityFromFailure = failurePath.focus(cityPath);
    assertThat(cityFromFailure.run().isLeft()).isTrue();
    String failureResult = cityFromFailure.run().fold(e -> e, c -> c);
    assertThat(failureResult).isEqualTo("Address not found");
  }

  @Test
  void exercise8_eitherPathFocusAffine() {
    AffinePath<User, String> emailPath = FocusPath.of(userEmailLens).some();

    EitherPath<String, User> userWithEmail =
        Path.right(new User("Alice", Optional.of("alice@example.com")));
    EitherPath<String, User> userWithoutEmail = Path.right(new User("Bob", Optional.empty()));

    // SOLUTION: focus with AffinePath and error
    EitherPath<String, String> emailFound = userWithEmail.focus(emailPath, "Email not configured");
    String foundResult = emailFound.run().fold(e -> e, v -> v);
    assertThat(foundResult).isEqualTo("alice@example.com");

    // SOLUTION: returns Left when AffinePath doesn't match
    EitherPath<String, String> emailMissing =
        userWithoutEmail.focus(emailPath, "Email not configured");
    assertThat(emailMissing.run().isLeft()).isTrue();
    String missingResult = emailMissing.run().fold(e -> e, v -> v);
    assertThat(missingResult).isEqualTo("Email not configured");
  }

  @Test
  void exercise9_tryPathFocus() {
    FocusPath<User, String> namePath = FocusPath.of(userNameLens);
    AffinePath<User, String> emailPath = FocusPath.of(userEmailLens).some();

    TryPath<User> userPath = Path.success(new User("Alice", Optional.of("alice@example.com")));
    TryPath<User> userWithoutEmail = Path.success(new User("Bob", Optional.empty()));

    // SOLUTION: FocusPath always succeeds
    TryPath<String> nameResult = userPath.focus(namePath);
    assertThat(nameResult.run().isSuccess()).isTrue();
    String tryResult = nameResult.run().fold(v -> v, t -> "error");
    assertThat(tryResult).isEqualTo("Alice");

    TryPath<String> emailPresent =
        userPath.focus(emailPath, () -> new RuntimeException("Email required"));
    assertThat(emailPresent.run().isSuccess()).isTrue();

    TryPath<String> emailAbsent =
        userWithoutEmail.focus(emailPath, () -> new RuntimeException("Email required"));
    assertThat(emailAbsent.run().isFailure()).isTrue();
  }

  @Test
  void exercise10_ioPathFocus() {
    FocusPath<User, String> namePath = FocusPath.of(userNameLens);
    User alice = new User("Alice", Optional.of("alice@example.com"));

    IOPath<User> userIO = Path.ioPure(alice);

    // SOLUTION: focus within IOPath
    IOPath<String> nameIO = userIO.focus(namePath);

    assertThat(nameIO.unsafeRun()).isEqualTo("Alice");

    AffinePath<User, String> emailPath = FocusPath.of(userEmailLens).some();
    IOPath<User> userWithoutEmailIO = Path.ioPure(new User("Bob", Optional.empty()));

    IOPath<String> emailIO =
        userWithoutEmailIO.focus(emailPath, () -> new RuntimeException("No email"));

    assertThatThrownBy(emailIO::unsafeRun)
        .isInstanceOf(RuntimeException.class)
        .hasMessage("No email");
  }

  @Test
  void exercise11_validationPathFocus() {
    FocusPath<User, String> namePath = FocusPath.of(userNameLens);
    AffinePath<User, String> emailPath = FocusPath.of(userEmailLens).some();

    ValidationPath<List<String>, User> validUser =
        Path.valid(new User("Alice", Optional.of("alice@example.com")), Semigroups.list());

    ValidationPath<List<String>, User> userMissingEmail =
        Path.valid(new User("Bob", Optional.empty()), Semigroups.list());

    // SOLUTION: FocusPath always succeeds
    ValidationPath<List<String>, String> nameResult = validUser.focus(namePath);
    assertThat(nameResult.run().isValid()).isTrue();
    assertThat(nameResult.run().get()).isEqualTo("Alice");

    ValidationPath<List<String>, String> emailPresent =
        validUser.focus(emailPath, List.of("Email is required"));
    assertThat(emailPresent.run().isValid()).isTrue();

    ValidationPath<List<String>, String> emailAbsent =
        userMissingEmail.focus(emailPath, List.of("Email is required"));
    assertThat(emailAbsent.run().isInvalid()).isTrue();
    assertThat(emailAbsent.run().getError()).containsExactly("Email is required");
  }

  // ═══════════════════════════════════════════════════════════════════════
  // Part 3: Practical Patterns
  // ═══════════════════════════════════════════════════════════════════════

  @Test
  void exercise12_combinedPipeline() {
    FocusPath<User, String> namePath = FocusPath.of(userNameLens);
    AffinePath<User, String> emailPath = FocusPath.of(userEmailLens).some();

    User alice = new User("Alice", Optional.of("alice@example.com"));

    EitherPath<String, User> userPath = Path.right(alice);

    EitherPath<String, String> namePipeline =
        userPath
            .focus(namePath)
            .via(
                name ->
                    name.length() >= 2
                        ? Path.<String, String>right(name)
                        : Path.left("Name too short"));

    String nameValidation = namePipeline.run().fold(e -> e, n -> n);
    assertThat(nameValidation).isEqualTo("Alice");

    // SOLUTION: Combined focus and transformation
    EitherPath<String, String> emailPipeline =
        userPath.focus(emailPath, "Email required").map(String::toLowerCase);

    String emailValidation = emailPipeline.run().fold(e -> e, n -> n);
    assertThat(emailValidation).isEqualTo("alice@example.com");
  }

  @Test
  void exercise13_simplePathsFocus() {
    FocusPath<User, String> namePath = FocusPath.of(userNameLens);
    AffinePath<User, String> emailPath = FocusPath.of(userEmailLens).some();

    User alice = new User("Alice", Optional.of("alice@example.com"));
    User bob = new User("Bob", Optional.empty());

    // SOLUTION: IdPath focus
    IdPath<User> idPath = Path.id(alice);
    IdPath<String> idNamePath = idPath.focus(namePath);
    assertThat(idNamePath.run().value()).isEqualTo("Alice");

    MaybePath<String> idEmailPath = idPath.focus(emailPath);
    assertThat(idEmailPath.run().isJust()).isTrue();

    IdPath<User> bobIdPath = Path.id(bob);
    MaybePath<String> bobEmailPath = bobIdPath.focus(emailPath);
    assertThat(bobEmailPath.run().isNothing()).isTrue();

    // SOLUTION: OptionalPath focus
    OptionalPath<User> optPath = Path.optional(Optional.of(alice));
    OptionalPath<String> optNamePath = optPath.focus(namePath);
    assertThat(optNamePath.run()).contains("Alice");

    OptionalPath<String> optEmailPath = optPath.focus(emailPath);
    assertThat(optEmailPath.run()).contains("alice@example.com");
  }
}
