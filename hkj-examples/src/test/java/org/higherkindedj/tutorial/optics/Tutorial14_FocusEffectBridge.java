// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

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
 * Tutorial 14: Focus-Effect Bridge - Connecting Optics with Effects
 *
 * <p>This tutorial explores the bridge between the Focus DSL (optics-based structural navigation)
 * and Effect Paths (effect-based computation sequencing). Both use {@code via} as their central
 * composition operator, but serve different purposes:
 *
 * <ul>
 *   <li><b>FocusDSL</b>: Navigates through data structures using optics (lenses, prisms,
 *       traversals)
 *   <li><b>EffectPath</b>: Navigates through effect types (Maybe, Either, Try, IO)
 * </ul>
 *
 * <p>The bridge enables two directions of composition:
 *
 * <pre>
 *   Direction 1: FocusPath -> EffectPath
 *   ═══════════════════════════════════════════════════════════════════════
 *
 *       FocusPath<S, A>
 *            │
 *            │ toMaybePath(source)
 *            │ toEitherPath(source)
 *            │ toTryPath(source)
 *            │ toIdPath(source)
 *            ▼
 *       EffectPath<A>
 *
 *
 *   Direction 2: EffectPath -> FocusPath (within effect context)
 *   ═══════════════════════════════════════════════════════════════════════
 *
 *       EffectPath<S>
 *            │
 *            │ focus(FocusPath<S, A>)
 *            │ focus(AffinePath<S, A>, errorIfAbsent)
 *            ▼
 *       EffectPath<A>
 * </pre>
 *
 * <p>Prerequisites: Complete Tutorials 12-13 (Focus DSL) before this one.
 */
public class Tutorial14_FocusEffectBridge {

  // ═══════════════════════════════════════════════════════════════════════
  // Test Data
  // ═══════════════════════════════════════════════════════════════════════

  record User(String name, Optional<String> email) {}

  record Address(String street, String city) {}

  record Company(String name, Address headquarters, List<User> employees) {}

  // Lenses for navigation
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

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // ═══════════════════════════════════════════════════════════════════════
  // Part 1: FocusPath -> EffectPath Bridge
  // ═══════════════════════════════════════════════════════════════════════

  /**
   * Exercise 1: FocusPath to MaybePath
   *
   * <p>FocusPath provides {@code toMaybePath(source)} to extract a value and wrap it in a
   * MaybePath. Since FocusPath always has a focus, the result is always Just.
   *
   * <p>Task: Use toMaybePath to lift a value into the Maybe effect
   */
  @Test
  void exercise1_focusPathToMaybePath() {
    FocusPath<User, String> namePath = FocusPath.of(userNameLens);
    User alice = new User("Alice", Optional.of("alice@example.com"));

    // TODO: Replace null with namePath.toMaybePath(alice)
    MaybePath<String> maybeNamePath = answerRequired();

    // The result should be Just("Alice")
    assertThat(maybeNamePath.run().isJust()).isTrue();
    assertThat(maybeNamePath.getOrElse("default")).isEqualTo("Alice");

    // You can chain further effect operations
    // TODO: Replace null with maybeNamePath.map(String::toUpperCase)
    MaybePath<String> upperPath = answerRequired();
    assertThat(upperPath.getOrElse("default")).isEqualTo("ALICE");
  }

  /**
   * Exercise 2: FocusPath to EitherPath
   *
   * <p>FocusPath provides {@code toEitherPath(source)} to extract a value and wrap it as a Right.
   *
   * <p>Task: Use toEitherPath to lift a value into the Either effect
   */
  @Test
  void exercise2_focusPathToEitherPath() {
    FocusPath<Company, Address> addressPath = FocusPath.of(companyAddressLens);
    Company company = new Company("TechCorp", new Address("123 Main St", "London"), List.of());

    // TODO: Replace null with addressPath.toEitherPath(company)
    EitherPath<String, Address> eitherAddressPath = answerRequired();

    // The result should be Right(Address)
    assertThat(eitherAddressPath.run().isRight()).isTrue();

    // Chain with via to navigate deeper within the effect
    FocusPath<Address, String> cityPath = FocusPath.of(addressCityLens);
    // TODO: Replace null with eitherAddressPath.via(addr -> cityPath.toEitherPath(addr))
    EitherPath<String, String> cityEffectPath = answerRequired();

    String cityResult = cityEffectPath.run().fold(e -> e, c -> c);
    assertThat(cityResult).isEqualTo("London");
  }

  /**
   * Exercise 3: AffinePath to MaybePath
   *
   * <p>AffinePath handles optional focuses. When converted to MaybePath, it returns Nothing if the
   * focus doesn't exist.
   *
   * <p>Task: Convert an AffinePath to MaybePath and handle both present and absent cases
   */
  @Test
  void exercise3_affinePathToMaybePath() {
    // Create an AffinePath for optional email (unwraps the Optional)
    AffinePath<User, String> emailPath = FocusPath.of(userEmailLens).some();

    User withEmail = new User("Alice", Optional.of("alice@example.com"));
    User withoutEmail = new User("Bob", Optional.empty());

    // TODO: Replace null with emailPath.toMaybePath(withEmail)
    MaybePath<String> presentPath = answerRequired();
    assertThat(presentPath.run().isJust()).isTrue();
    assertThat(presentPath.getOrElse("no-email")).isEqualTo("alice@example.com");

    // TODO: Replace null with emailPath.toMaybePath(withoutEmail)
    MaybePath<String> absentPath = answerRequired();
    assertThat(absentPath.run().isNothing()).isTrue();
    assertThat(absentPath.getOrElse("no-email")).isEqualTo("no-email");
  }

  /**
   * Exercise 4: AffinePath to EitherPath with error
   *
   * <p>When an AffinePath doesn't match, you often want a specific error. Use {@code
   * toEitherPath(source, errorIfAbsent)} to provide an error value.
   *
   * <p>Task: Convert an AffinePath to EitherPath with meaningful errors
   */
  @Test
  void exercise4_affinePathToEitherPathWithError() {
    AffinePath<User, String> emailPath = FocusPath.of(userEmailLens).some();

    User withEmail = new User("Alice", Optional.of("alice@example.com"));
    User withoutEmail = new User("Bob", Optional.empty());

    // TODO: Replace null with emailPath.toEitherPath(withEmail, "Email not configured")
    EitherPath<String, String> presentPath = answerRequired();
    assertThat(presentPath.run().isRight()).isTrue();

    // TODO: Replace null with emailPath.toEitherPath(withoutEmail, "Email not configured")
    EitherPath<String, String> absentPath = answerRequired();
    assertThat(absentPath.run().isLeft()).isTrue();
    String errorResult = absentPath.run().fold(e -> e, v -> v);
    assertThat(errorResult).isEqualTo("Email not configured");
  }

  /**
   * Exercise 5: TraversalPath to collection effects
   *
   * <p>TraversalPath can be converted to ListPath or StreamPath for working with multiple values.
   *
   * <p>Task: Use toListPath to collect all focused values
   */
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

    // TODO: Replace null with employeesPath.toListPath(company)
    var employeeListPath = answerRequired();

    // ListPath wraps List and provides list-specific operations
    // Note: For this exercise, just verify the result contains the expected values
    List<User> employees = employeesPath.getAll(company);
    assertThat(employees).hasSize(3);
    assertThat(employees.get(0).name()).isEqualTo("Alice");
  }

  // ═══════════════════════════════════════════════════════════════════════
  // Part 2: EffectPath.focus() - Optics Within Effects
  // ═══════════════════════════════════════════════════════════════════════

  /**
   * Exercise 6: MaybePath.focus(FocusPath)
   *
   * <p>Effect paths provide {@code focus(FocusPath)} to apply structural navigation within the
   * effect context. This is the reverse direction: starting with an effect and drilling down.
   *
   * <p>Task: Use focus() to navigate within a MaybePath
   */
  @Test
  void exercise6_maybePathFocus() {
    FocusPath<User, String> namePath = FocusPath.of(userNameLens);
    MaybePath<User> userPath = Path.just(new User("Alice", Optional.of("alice@example.com")));

    // TODO: Replace null with userPath.focus(namePath)
    MaybePath<String> nameInEffect = answerRequired();

    assertThat(nameInEffect.run().isJust()).isTrue();
    assertThat(nameInEffect.getOrElse("unknown")).isEqualTo("Alice");

    // When MaybePath is Nothing, focus still returns Nothing
    MaybePath<User> emptyPath = Path.nothing();
    MaybePath<String> focusedEmpty = emptyPath.focus(namePath);
    assertThat(focusedEmpty.run().isNothing()).isTrue();
  }

  /**
   * Exercise 7: EitherPath.focus(FocusPath)
   *
   * <p>EitherPath.focus works similarly, preserving Left values while navigating Right values.
   *
   * <p>Task: Use focus() to navigate within an EitherPath
   */
  @Test
  void exercise7_eitherPathFocus() {
    FocusPath<Address, String> cityPath = FocusPath.of(addressCityLens);

    EitherPath<String, Address> successPath = Path.right(new Address("123 Main St", "London"));
    EitherPath<String, Address> failurePath = Path.left("Address not found");

    // TODO: Replace null with successPath.focus(cityPath)
    EitherPath<String, String> cityFromSuccess = answerRequired();
    String successResult = cityFromSuccess.run().fold(e -> e, c -> c);
    assertThat(successResult).isEqualTo("London");

    // TODO: Replace null with failurePath.focus(cityPath)
    EitherPath<String, String> cityFromFailure = answerRequired();
    assertThat(cityFromFailure.run().isLeft()).isTrue();
    String failureResult = cityFromFailure.run().fold(e -> e, c -> c);
    assertThat(failureResult).isEqualTo("Address not found");
  }

  /**
   * Exercise 8: EitherPath.focus(AffinePath, error)
   *
   * <p>When focusing with an AffinePath that might not match, you need to provide an error for the
   * absent case.
   *
   * <p>Task: Use focus() with AffinePath and error handling
   */
  @Test
  void exercise8_eitherPathFocusAffine() {
    AffinePath<User, String> emailPath = FocusPath.of(userEmailLens).some();

    EitherPath<String, User> userWithEmail =
        Path.right(new User("Alice", Optional.of("alice@example.com")));
    EitherPath<String, User> userWithoutEmail = Path.right(new User("Bob", Optional.empty()));

    // TODO: Replace null with userWithEmail.focus(emailPath, "Email not configured")
    EitherPath<String, String> emailFound = answerRequired();
    String foundResult = emailFound.run().fold(e -> e, v -> v);
    assertThat(foundResult).isEqualTo("alice@example.com");

    // TODO: Replace null with userWithoutEmail.focus(emailPath, "Email not configured")
    EitherPath<String, String> emailMissing = answerRequired();
    assertThat(emailMissing.run().isLeft()).isTrue();
    String missingResult = emailMissing.run().fold(e -> e, v -> v);
    assertThat(missingResult).isEqualTo("Email not configured");
  }

  /**
   * Exercise 9: TryPath.focus()
   *
   * <p>TryPath.focus with AffinePath throws an exception when the focus is absent.
   *
   * <p>Task: Use focus() with TryPath
   */
  @Test
  void exercise9_tryPathFocus() {
    FocusPath<User, String> namePath = FocusPath.of(userNameLens);
    AffinePath<User, String> emailPath = FocusPath.of(userEmailLens).some();

    TryPath<User> userPath = Path.success(new User("Alice", Optional.of("alice@example.com")));
    TryPath<User> userWithoutEmail = Path.success(new User("Bob", Optional.empty()));

    // FocusPath always succeeds
    // TODO: Replace null with userPath.focus(namePath)
    TryPath<String> nameResult = answerRequired();
    assertThat(nameResult.run().isSuccess()).isTrue();
    String tryResult = nameResult.run().fold(v -> v, t -> "error");
    assertThat(tryResult).isEqualTo("Alice");

    // AffinePath with present value succeeds
    TryPath<String> emailPresent =
        userPath.focus(emailPath, () -> new RuntimeException("Email required"));
    assertThat(emailPresent.run().isSuccess()).isTrue();

    // AffinePath with absent value fails
    TryPath<String> emailAbsent =
        userWithoutEmail.focus(emailPath, () -> new RuntimeException("Email required"));
    assertThat(emailAbsent.run().isFailure()).isTrue();
  }

  /**
   * Exercise 10: IOPath.focus()
   *
   * <p>IOPath.focus allows optic navigation within deferred computations.
   *
   * <p>Task: Use focus() with IOPath
   */
  @Test
  void exercise10_ioPathFocus() {
    FocusPath<User, String> namePath = FocusPath.of(userNameLens);
    User alice = new User("Alice", Optional.of("alice@example.com"));

    IOPath<User> userIO = Path.ioPure(alice);

    // TODO: Replace null with userIO.focus(namePath)
    IOPath<String> nameIO = answerRequired();

    // IO is lazy, must run to get result
    assertThat(nameIO.unsafeRun()).isEqualTo("Alice");

    // AffinePath with exception supplier for absent case
    AffinePath<User, String> emailPath = FocusPath.of(userEmailLens).some();
    IOPath<User> userWithoutEmailIO = Path.ioPure(new User("Bob", Optional.empty()));

    IOPath<String> emailIO =
        userWithoutEmailIO.focus(emailPath, () -> new RuntimeException("No email"));

    assertThatThrownBy(emailIO::unsafeRun)
        .isInstanceOf(RuntimeException.class)
        .hasMessage("No email");
  }

  /**
   * Exercise 11: ValidationPath.focus()
   *
   * <p>ValidationPath.focus returns Invalid when the AffinePath doesn't match.
   *
   * <p>Task: Use focus() with ValidationPath for validation pipelines
   */
  @Test
  void exercise11_validationPathFocus() {
    FocusPath<User, String> namePath = FocusPath.of(userNameLens);
    AffinePath<User, String> emailPath = FocusPath.of(userEmailLens).some();

    ValidationPath<List<String>, User> validUser =
        Path.valid(new User("Alice", Optional.of("alice@example.com")), Semigroups.list());

    ValidationPath<List<String>, User> userMissingEmail =
        Path.valid(new User("Bob", Optional.empty()), Semigroups.list());

    // FocusPath always succeeds
    // TODO: Replace null with validUser.focus(namePath)
    ValidationPath<List<String>, String> nameResult = answerRequired();
    assertThat(nameResult.run().isValid()).isTrue();
    assertThat(nameResult.run().get()).isEqualTo("Alice");

    // AffinePath with present value
    ValidationPath<List<String>, String> emailPresent =
        validUser.focus(emailPath, List.of("Email is required"));
    assertThat(emailPresent.run().isValid()).isTrue();

    // AffinePath with absent value returns Invalid
    ValidationPath<List<String>, String> emailAbsent =
        userMissingEmail.focus(emailPath, List.of("Email is required"));
    assertThat(emailAbsent.run().isInvalid()).isTrue();
    assertThat(emailAbsent.run().getError()).containsExactly("Email is required");
  }

  // ═══════════════════════════════════════════════════════════════════════
  // Part 3: Practical Patterns
  // ═══════════════════════════════════════════════════════════════════════

  /**
   * Exercise 12: Combined pipeline - fetch, validate, transform
   *
   * <p>Real-world usage often combines both directions: extract with optics, process with effects,
   * then extract more with optics.
   *
   * <p>Task: Build a pipeline that validates and transforms user data
   */
  @Test
  void exercise12_combinedPipeline() {
    FocusPath<User, String> namePath = FocusPath.of(userNameLens);
    AffinePath<User, String> emailPath = FocusPath.of(userEmailLens).some();

    User alice = new User("Alice", Optional.of("alice@example.com"));

    // Start with Either effect wrapping the user
    EitherPath<String, User> userPath = Path.right(alice);

    // Focus on name and validate
    EitherPath<String, String> namePipeline =
        userPath
            .focus(namePath) // Extract name using optics
            .via(
                name ->
                    name.length() >= 2
                        ? Path.<String, String>right(name)
                        : Path.left("Name too short"));

    String nameValidation = namePipeline.run().fold(e -> e, n -> n);
    assertThat(nameValidation).isEqualTo("Alice");

    // Focus on email with error handling
    // TODO: Replace null with userPath.focus(emailPath, "Email required")
    //       .map(String::toLowerCase)
    EitherPath<String, String> emailPipeline = answerRequired();

    String emailValidation = emailPipeline.run().fold(e -> e, n -> n);
    assertThat(emailValidation).isEqualTo("alice@example.com");
  }

  /**
   * Exercise 13: IdPath and OptionalPath focus
   *
   * <p>Even simpler effect types support the focus operation.
   *
   * <p>Task: Use focus with IdPath and OptionalPath
   */
  @Test
  void exercise13_simplePathsFocus() {
    FocusPath<User, String> namePath = FocusPath.of(userNameLens);
    AffinePath<User, String> emailPath = FocusPath.of(userEmailLens).some();

    User alice = new User("Alice", Optional.of("alice@example.com"));
    User bob = new User("Bob", Optional.empty());

    // IdPath - trivial effect, always succeeds
    IdPath<User> idPath = Path.id(alice);
    // TODO: Replace null with idPath.focus(namePath)
    IdPath<String> idNamePath = answerRequired();
    assertThat(idNamePath.run().value()).isEqualTo("Alice");

    // IdPath with AffinePath returns MaybePath (might be absent)
    MaybePath<String> idEmailPath = idPath.focus(emailPath);
    assertThat(idEmailPath.run().isJust()).isTrue();

    IdPath<User> bobIdPath = Path.id(bob);
    MaybePath<String> bobEmailPath = bobIdPath.focus(emailPath);
    assertThat(bobEmailPath.run().isNothing()).isTrue();

    // OptionalPath
    OptionalPath<User> optPath = Path.optional(Optional.of(alice));
    // TODO: Replace null with optPath.focus(namePath)
    OptionalPath<String> optNamePath = answerRequired();
    assertThat(optNamePath.run()).contains("Alice");

    // OptionalPath with AffinePath
    OptionalPath<String> optEmailPath = optPath.focus(emailPath);
    assertThat(optEmailPath.run()).contains("alice@example.com");
  }

  /**
   * Congratulations! You have completed Tutorial 14: Focus-Effect Bridge
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>Bridge Direction 1: FocusPath/AffinePath/TraversalPath → EffectPath
   *       <ul>
   *         <li>toMaybePath(source) - wrap in Maybe effect
   *         <li>toEitherPath(source) / toEitherPath(source, error) - wrap in Either
   *         <li>toTryPath(source) / toTryPath(source, exceptionSupplier) - wrap in Try
   *         <li>toIdPath(source) - wrap in Id (trivial effect)
   *         <li>toListPath(source) / toStreamPath(source) - for TraversalPath
   *       </ul>
   *   <li>Bridge Direction 2: EffectPath.focus() - optics within effects
   *       <ul>
   *         <li>focus(FocusPath) - always succeeds (FocusPath guarantees value)
   *         <li>focus(AffinePath, errorIfAbsent) - fails gracefully with error
   *       </ul>
   *   <li>Practical patterns for combining optics and effects
   * </ul>
   *
   * <p>Key Takeaways:
   *
   * <ul>
   *   <li>FocusPath → EffectPath: Use when you have data and want to start an effect pipeline
   *   <li>EffectPath.focus(): Use when you're already in an effect and need to drill down
   *   <li>AffinePath needs error handling - provide meaningful errors for absent cases
   *   <li>Both directions compose naturally with existing optics and effect operations
   * </ul>
   *
   * <p>Next Steps:
   *
   * <ul>
   *   <li>Explore the effect-specific capabilities (peek, recover, mapError)
   *   <li>Combine with Free Monad DSL for complex optic workflows
   *   <li>Use in validation pipelines with ValidationPath
   * </ul>
   */
}
