// Copyright (c) 2025 - 2026 Magnus Smith
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
 * Solution for Tutorial14 FocusEffectBridge — teaching-solution format.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions established by
 * the Foundations journey: read the working code first, then the commentary on <em>why</em> the
 * chosen form is idiomatic. The complete-with-commentary template (Why this is idiomatic /
 * Alternative / Common wrong attempt on every exercise) lives in the Foundations solutions
 * coretypes/Tutorial01_KindBasics_Solution.java as the canonical reference.
 *
 * <p>The exercise bodies below are correct working code. Per-exercise teaching commentary is being
 * rolled out across the chapter; if this file does not yet have it, treat the reference code as the
 * answer and consult the pilot solution for the format guide.
 *
 * <p>For the chapter-level guidance on how to learn from a solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
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

  /**
   * Why this is idiomatic: {@code path.toMaybePath(source)} lifts a focus path into a {@code
   * MaybePath}, ready to chain with effect-aware operations like {@code map} and {@code via}. The
   * optic supplies the navigation; the effect supplies the failure channel.
   *
   * <p>Alternative: read the value with the path and wrap with {@code Path.just}. Same answer in
   * this case; {@code toMaybePath} is the named lift that signals the bridge intent.
   *
   * <p>Common wrong attempt: try to chain effect operations on a raw {@code FocusPath}. Optics
   * describe pure navigation; effect operations live on the {@code Path} types.
   */
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

  /**
   * Why this is idiomatic: {@code path.toEitherPath(source)} lifts the path into an {@code
   * EitherPath}; subsequent {@code via} steps can refine the focus while staying in the typed-error
   * channel.
   *
   * <p>Alternative: {@code Path.right(path.get(source))}. Equivalent runtime; the lifted version
   * makes the next {@code via} step natural.
   *
   * <p>Common wrong attempt: assume {@code toEitherPath} introduces a failure case. The source-side
   * {@code FocusPath} always succeeds, so the result is always {@code Right}; use {@code
   * AffinePath.toEitherPath(error)} when failure must be encoded.
   */
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

  /**
   * Why this is idiomatic: an affine path is partial; {@code toMaybePath} promotes the partiality
   * into the {@code Maybe} effect — present becomes {@code Just}, absent becomes {@code Nothing}.
   * The bridge is total in both directions.
   *
   * <p>Alternative: {@code path.getOptional(source)} and wrap manually. Same outcome; the bridge
   * keeps every transformation consistent with the rest of the path API.
   *
   * <p>Common wrong attempt: assume the affine throws on absence. {@code Maybe} is the typed
   * answer; {@code toMaybePath} is the canonical lift.
   */
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

  /**
   * Why this is idiomatic: {@code affine.toEitherPath(source, error)} promotes absence to a typed
   * {@code Left}. The error is supplied at the bridge call so the rest of the pipeline reads
   * "either the focus or this reason".
   *
   * <p>Alternative: {@code affine.toMaybePath(...)} followed by {@code .toEitherPath(error)} on the
   * resulting {@code MaybePath}. Same outcome; the direct overload removes the intermediate type.
   *
   * <p>Common wrong attempt: pass a generic {@code String} error type even when a typed enum is
   * wanted. The error type is yours to choose; pick the type the rest of the domain already speaks.
   */
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

  /**
   * Why this is idiomatic: a traversal can yield zero or many focuses; {@code toListPath} is the
   * bridge into the list-shaped path so every focus becomes an element of the resulting effect.
   *
   * <p>Alternative: {@code traversal.getAll(source)} and wrap with the list factory. Same outcome;
   * the named bridge keeps the navigation consistent across helpers.
   *
   * <p>Common wrong attempt: try to {@code toMaybePath} a multi-focus traversal. The effect would
   * have to choose one element; {@code toListPath} keeps every focus visible.
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

    // SOLUTION: Use toListPath to get all focused values wrapped in ListPath
    var employeeListPath = employeesPath.toListPath(company);

    List<User> employees = employeesPath.getAll(company);
    assertThat(employees).hasSize(3);
    assertThat(employees.get(0).name()).isEqualTo("Alice");
  }

  // ═══════════════════════════════════════════════════════════════════════
  // Part 2: EffectPath.focus() - Optics Within Effects
  // ═══════════════════════════════════════════════════════════════════════

  /**
   * Why this is idiomatic: {@code effectPath.focus(focusPath)} navigates into the success value of
   * the effect. {@code Just(user).focus(name)} is "if there is a user, give me the name"; {@code
   * Nothing} stays {@code Nothing}.
   *
   * <p>Alternative: {@code map(user -> namePath.get(user))} on the {@code MaybePath}. Equivalent;
   * the {@code focus} spelling avoids re-reading the path's contract at the call site.
   *
   * <p>Common wrong attempt: try to dereference the {@code Maybe} before focusing. The point of
   * {@code focus} is that it works on the path-as-data; reach into the value only at the end with
   * {@code run}/{@code getOrElse}.
   */
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

  /**
   * Why this is idiomatic: focusing on an {@code EitherPath} preserves the typed error channel.
   * {@code Right} values are navigated into; {@code Left} values pass through unchanged.
   *
   * <p>Alternative: {@code map} on the {@code EitherPath} with the focus path's getter. Same
   * answer; the {@code focus} variant reads better when the navigation is the central concern.
   *
   * <p>Common wrong attempt: try to lift the error type with the focus. The error channel is
   * independent of the focus; only the success path is touched.
   */
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

  /**
   * Why this is idiomatic: focusing an {@code EitherPath} through an {@code AffinePath} needs an
   * error for the absent case — pass it as the second argument and the affine's partiality lifts
   * into the {@code Either}'s error channel.
   *
   * <p>Alternative: convert the affine to an {@code EitherPath} first and chain. The combined call
   * is a one-liner; the staged form helps when the error needs different computation per call site.
   *
   * <p>Common wrong attempt: forget the error argument. The affine's partiality has nowhere to go
   * and the call fails to type-check; supply the error explicitly.
   */
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

  /**
   * Why this is idiomatic: {@code TryPath} carries a {@code Throwable}; focusing through an {@code
   * AffinePath} provides an exception supplier for the absent case. The bridge routes the affine's
   * "not present" through the try's failure channel.
   *
   * <p>Alternative: convert the affine to a {@code TryPath} explicitly. Same outcome; the focus
   * overload is the named shortcut.
   *
   * <p>Common wrong attempt: throw inside the supplier rather than returning a {@code Throwable}.
   * {@code TryPath} expects a value-style {@code Throwable} so the supplier should construct, not
   * throw.
   */
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

  /**
   * Why this is idiomatic: {@code IOPath} defers execution; focusing through an optic keeps the
   * deferral. The optic does the navigation when {@code unsafeRun} is invoked.
   *
   * <p>Alternative: run the IO eagerly, focus, and re-wrap. Loses the laziness — the effect runs
   * even if the result is discarded.
   *
   * <p>Common wrong attempt: forget that {@code IOPath} can throw at {@code unsafeRun}. Wrap the
   * call in {@code TryPath} or check the supplied exception when the affine path does not match.
   */
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

  /**
   * Why this is idiomatic: {@code ValidationPath} accumulates errors via a {@code Semigroup}.
   * Focusing through an affine routes the absent case into the error list, keeping every reason
   * visible.
   *
   * <p>Alternative: use {@code EitherPath} for fail-fast. Pick {@code ValidationPath} when the user
   * wants every error at once, not just the first.
   *
   * <p>Common wrong attempt: pass a single error string instead of a list. The semigroup shape
   * needs the combiner, so the error type usually matches the accumulator (e.g. {@code
   * List<String>}).
   */
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

  /**
   * Why this is idiomatic: a real pipeline mixes {@code focus} with effect-shaped {@code via} steps
   * and pure {@code map} transforms. Each piece does one thing; the typed-error channel carries
   * failures forward without interleaving plumbing.
   *
   * <p>Alternative: bind intermediate {@code EitherPath}s to local variables. Same semantics; the
   * chained version stays declarative when each step is short.
   *
   * <p>Common wrong attempt: collapse the validation into a single lambda after running the effect.
   * Doing so runs everything regardless of the typed errors; keep the path lazy and let {@code via}
   * short-circuit.
   */
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

  /**
   * Why this is idiomatic: {@code IdPath} carries no failure channel; focusing through an affine
   * returns a {@code MaybePath} because the affine itself may not match. The bridge picks the
   * result type that matches the navigation's partiality.
   *
   * <p>Alternative: use {@code FocusPath.get} on the underlying value directly. Equivalent for
   * total navigation; reach for {@code IdPath.focus(affine)} when the affine's partiality should be
   * preserved.
   *
   * <p>Common wrong attempt: assume {@code IdPath.focus(affine)} returns an {@code IdPath}. The
   * affine may not match; the result is necessarily the wider {@code MaybePath}.
   */
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
