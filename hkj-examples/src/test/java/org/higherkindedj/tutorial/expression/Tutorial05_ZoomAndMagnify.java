// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.MaybeAssert.assertThatMaybe;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.ReaderPath;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 05: Optic-Polymorphic Zoom and Magnify — Bending the Outer Carrier.
 *
 * <p>Pain → Promise. A sub-service that operates on a small record cannot easily run inside a
 * workflow over a larger record without manual lens extraction or function plumbing. The tools
 * {@code ForState.zoom} and {@code ReaderPath.magnify} bend the outer carrier (state for zoom,
 * environment for magnify) so the focused computation lifts into the larger scope. This tutorial
 * walks through the optic-polymorphic forms added in issue #506: passing {@code FocusPath}, {@code
 * AffinePath}, and {@code Iso} directly, and the symmetric {@code magnify} on {@code ReaderPath}.
 *
 * <p>Java idiom anchor. Think of {@code zoom} as scoping a Java try-block to a sub-record: inside
 * the block, you only see the sub-record; on exit, the outer record is rebuilt around the result.
 * {@code magnify} is the read-only mirror image: the inner code reads only what it needs, and the
 * outer caller decides how to extract that from the larger environment.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>{@code zoom(FocusPath)} — pass a generated path directly, no manual lens extraction
 *   <li>{@code zoom(Iso)} — representation-change zoom (units, swapped tuples, wrappers)
 *   <li>{@code zoom(AffinePath)} on FilterableSteps — short-circuits when the affine target is
 *       absent
 *   <li>{@code ReaderPath.magnify(FocusPath)} — lift a sub-service into a larger environment
 *   <li>{@code ReaderPath.local(Function)} — escape hatch for non-optic environment adaptation
 * </ul>
 *
 * <p>Prerequisites: Tutorial 01 (ForState basics) and a passing familiarity with FocusPath.
 *
 * <p>Estimated time: ~12 minutes.
 *
 * <p>Tiered hints. Each exercise uses the Nudge / Strategy / Spoiler ladder. Stop reading the hints
 * as soon as you have what you need.
 *
 * <p>Replace each {@code answerRequired()} call with the correct code to make the tests pass.
 */
@DisplayName("Tutorial 05: Optic-Polymorphic Zoom and Magnify")
public class Tutorial05_ZoomAndMagnify {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // --- Shared Domain Model ---

  record Address(String street, String city, String zip) {}

  record Customer(String name, Address address, int loyaltyPoints) {}

  record OptionalAddressCustomer(String name, Optional<Address> address) {}

  record DbConfig(String url, int maxConnections) {}

  record AuthConfig(String issuer, int tokenTtlSeconds) {}

  record AppEnv(DbConfig db, AuthConfig auth, String tenant) {}

  record User(String id, String name) {}

  // --- Shared Optics ---

  static final Lens<Customer, Address> addressLens =
      Lens.of(Customer::address, (c, a) -> new Customer(c.name(), a, c.loyaltyPoints()));
  static final Lens<Customer, Integer> loyaltyLens =
      Lens.of(Customer::loyaltyPoints, (c, lp) -> new Customer(c.name(), c.address(), lp));
  static final Lens<Address, String> streetLens =
      Lens.of(Address::street, (a, s) -> new Address(s, a.city(), a.zip()));
  static final Lens<Address, String> cityLens =
      Lens.of(Address::city, (a, c) -> new Address(a.street(), c, a.zip()));

  static final Affine<OptionalAddressCustomer, Address> optionalAddressAffine =
      Affine.of(
          OptionalAddressCustomer::address,
          (c, a) -> new OptionalAddressCustomer(c.name(), Optional.of(a)));

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 1: zoom(FocusPath) - Generated paths flow directly into ForState
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 1: zoom into a Customer's Address using a {@link FocusPath}.
   *
   * <p>Before this overload existed, the only way to zoom was to pass a {@code Lens} directly,
   * forcing callers to unwrap {@code FocusPath.toLens()} every time. {@code zoom(FocusPath)} now
   * accepts the path types that {@code @GenerateFocus} emits without ceremony.
   *
   * <p>Task: zoom into the address sub-record and update the street to {@code "456 Oak Ave"}, then
   * end the zoom and return the customer.
   *
   * <pre>
   *   // Nudge:    There is a single zoom call that takes the FocusPath directly.
   *   // Strategy: ForState.withState(...).zoom(addressPath).update(streetLens, ...).endZoom().yield()
   *   // Spoiler:  ForState.withState(idMonad, Id.of(initial)).zoom(addressPath).update(streetLens, "456 Oak Ave").endZoom().yield()
   * </pre>
   */
  @Test
  @DisplayName("Exercise 1: zoom(FocusPath) narrows state via a generated path")
  void exercise1_zoomFocusPath() {
    Monad<IdKind.Witness> idMonad = Instances.monad(id());
    Customer initial =
        new Customer("Alice", new Address("123 Main St", "Springfield", "62701"), 100);
    FocusPath<Customer, Address> addressPath = FocusPath.of(addressLens);

    // TODO: Replace answerRequired() with a ForState chain that zooms into addressPath
    //       and updates the street to "456 Oak Ave".
    Kind<IdKind.Witness, Customer> result = answerRequired();

    Customer finalCustomer = IdKindHelper.ID.unwrap(result);
    assertThat(finalCustomer.address().street()).isEqualTo("456 Oak Ave");
    assertThat(finalCustomer.address().city()).isEqualTo("Springfield");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 2: zoom(Iso) - Representation-change zoom
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 2: zoom through an {@link Iso} between two equivalent shapes.
   *
   * <p>An {@code Iso} is a lossless, two-way conversion. When you zoom through one, the inner steps
   * operate on the alternative representation, and on {@code endZoom} the outer state is
   * reconstructed via {@code iso.reverseGet}. This is the right tool for unit conversions, swapped
   * tuples, or wrapper-type transparency.
   *
   * <p>Task: use the supplied {@code customerToView} Iso to zoom into a {@code CustomerView},
   * update the {@code loyaltyPoints} field on the view, then end the zoom.
   *
   * <pre>
   *   // Nudge:    The same zoom call accepts an Iso. The inner state is the iso target type.
   *   // Strategy: zoom(customerToView).update(viewLoyalty, 999).endZoom()
   *   // Spoiler:  ForState.withState(idMonad, Id.of(initial)).zoom(customerToView).update(viewLoyalty, 999).endZoom().yield()
   * </pre>
   */
  @Test
  @DisplayName("Exercise 2: zoom(Iso) reconstructs the outer state via reverseGet")
  void exercise2_zoomIso() {
    record CustomerView(int loyaltyPoints, String name, Address address) {}

    Iso<Customer, CustomerView> customerToView =
        Iso.of(
            c -> new CustomerView(c.loyaltyPoints(), c.name(), c.address()),
            v -> new Customer(v.name(), v.address(), v.loyaltyPoints()));

    Lens<CustomerView, Integer> viewLoyalty =
        Lens.of(
            CustomerView::loyaltyPoints, (v, lp) -> new CustomerView(lp, v.name(), v.address()));

    Monad<IdKind.Witness> idMonad = Instances.monad(id());
    Customer initial = new Customer("Alice", new Address("123", "Springfield", "00000"), 0);

    // TODO: Replace answerRequired() with a ForState chain that zooms through customerToView
    //       and sets viewLoyalty to 999.
    Kind<IdKind.Witness, Customer> result = answerRequired();

    Customer finalCustomer = IdKindHelper.ID.unwrap(result);
    assertThat(finalCustomer.loyaltyPoints()).isEqualTo(999);
    assertThat(finalCustomer.name()).isEqualTo("Alice");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 3: zoom(AffinePath) - happy path on FilterableSteps
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 3: zoom into an optional sub-record using an {@link AffinePath}.
   *
   * <p>{@code AffinePath} zoom is only available on {@code FilterableSteps}, i.e. when the
   * surrounding monad is a {@code MonadZero} such as {@code Maybe} or {@code List}. When the affine
   * target is present, the inner steps run normally and update the sub-record.
   *
   * <p>Task: use the supplied {@code addressPath} {@code AffinePath} to zoom into the customer's
   * present address and set the street.
   *
   * <pre>
   *   // Nudge:    The Maybe monad supports MonadZero, so zoom(affinePath) is available.
   *   // Strategy: ForState.withState(maybeMonad, MAYBE.just(initial)).zoom(addressPath).update(streetLens, "Oak Ave").endZoom().yield()
   *   // Spoiler:  Same as Strategy.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 3: zoom(AffinePath) updates inner state when target is present")
  void exercise3_zoomAffinePathPresent() {
    MonadError<MaybeKind.Witness, Unit> maybeMonad = Instances.monadError(maybe());
    OptionalAddressCustomer initial =
        new OptionalAddressCustomer(
            "Alice", Optional.of(new Address("123 Main St", "Springfield", "62701")));
    AffinePath<OptionalAddressCustomer, Address> addressPath = AffinePath.of(optionalAddressAffine);

    // TODO: Replace answerRequired() with a ForState chain over the Maybe monad that zooms
    //       through the AffinePath and updates the street to "Oak Ave".
    Kind<MaybeKind.Witness, OptionalAddressCustomer> result = answerRequired();

    Maybe<OptionalAddressCustomer> outcome = MAYBE.narrow(result);
    assertThatMaybe(outcome).isJust();
    Address updated = outcome.orElse(initial).address().orElseThrow();
    assertThat(updated.street()).isEqualTo("Oak Ave");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 4: zoom(AffinePath) - short-circuit on absent target
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 4: zoom into an absent affine target, observe the short-circuit.
   *
   * <p>When the {@code AffinePath} cannot find its target, the comprehension short-circuits via the
   * surrounding {@code MonadZero}. For {@code Maybe}, that means the whole result becomes {@code
   * Nothing}, regardless of any updates queued inside the zoom block.
   *
   * <p>Task: zoom into a customer whose address is {@code Optional.empty()} and try to update the
   * street. The expected outcome is {@code Maybe.nothing()}.
   *
   * <pre>
   *   // Nudge:    The exact same code as exercise 3 works; only the input changes.
   *   // Strategy: Run the same chain on a customer whose address() returns Optional.empty().
   *   // Spoiler:  ForState.withState(maybeMonad, MAYBE.just(initial)).zoom(addressPath).update(streetLens, "Oak Ave").endZoom().yield()
   * </pre>
   */
  @Test
  @DisplayName("Exercise 4: zoom(AffinePath) short-circuits to Nothing when target is absent")
  void exercise4_zoomAffinePathAbsent() {
    MonadError<MaybeKind.Witness, Unit> maybeMonad = Instances.monadError(maybe());
    OptionalAddressCustomer initial = new OptionalAddressCustomer("Alice", Optional.empty());
    AffinePath<OptionalAddressCustomer, Address> addressPath = AffinePath.of(optionalAddressAffine);

    // TODO: Replace answerRequired() with the same ForState chain as exercise 3.
    //       The result should be Maybe.nothing() because the affine target is absent.
    Kind<MaybeKind.Witness, OptionalAddressCustomer> result = answerRequired();

    assertThatMaybe(MAYBE.narrow(result)).isNothing();
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 5: ReaderPath.magnify(FocusPath) - lifting a sub-service
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 5: lift a {@code ReaderPath} that reads a sub-environment into a larger environment
   * via {@code magnify(FocusPath)}.
   *
   * <p>The sub-service {@code loadUser} only knows about {@code DbConfig}. The application bundles
   * {@code DbConfig} into a larger {@code AppEnv}. Use {@code magnify} to lift the sub-service
   * without rewriting it.
   *
   * <p>Task: use the supplied {@code dbPath} to lift {@code loadUser} into an {@code
   * ReaderPath<AppEnv, User>}, then run it against {@code production}.
   *
   * <pre>
   *   // Nudge:    There is a single magnify call on ReaderPath that takes a FocusPath.
   *   // Strategy: loadUser.magnify(dbPath).run(production)
   *   // Spoiler:  Same as Strategy.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 5: magnify(FocusPath) lifts a sub-service into a larger environment")
  void exercise5_magnifyFocusPath() {
    AppEnv production =
        new AppEnv(new DbConfig("jdbc:prod", 50), new AuthConfig("auth.example.com", 3600), "acme");

    ReaderPath<DbConfig, User> loadUser =
        ReaderPath.asks(db -> new User("u-1", "Alice (" + db.url() + ")"));

    Lens<AppEnv, DbConfig> dbLens =
        Lens.of(AppEnv::db, (env, c) -> new AppEnv(c, env.auth(), env.tenant()));
    FocusPath<AppEnv, DbConfig> dbPath = FocusPath.of(dbLens);

    // TODO: Replace answerRequired() with a magnify(...) call that lifts loadUser into AppEnv,
    //       then runs against production.
    User user = answerRequired();

    assertThat(user.id()).isEqualTo("u-1");
    assertThat(user.name()).contains("jdbc:prod");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 6: Diagnostic - magnify(FocusPath) vs local(Function)
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Diagnostic exercise: a wrong attempt and its fix.
   *
   * <p>Below is a commented-out attempt to lift {@code loadUser} into {@code AppEnv} by calling
   * {@code .magnify(AppEnv::db)}. {@code magnify} only accepts a {@code Getter} or a {@code
   * FocusPath}, not a bare {@code Function}, so this does not compile. The lesson: use {@code
   * magnify} for optic adaptations, and the existing {@code local(Function)} as the escape hatch
   * for ad hoc adaptations.
   *
   * <p>Task: write the correct version. Use {@code local(AppEnv::db)} to demonstrate the
   * function-based escape hatch, then verify both forms produce the same result.
   *
   * <pre>
   *   // Nudge:    The escape hatch is named local; it takes a plain Function.
   *   // Strategy: loadUser.local(AppEnv::db).run(production)
   *   // Spoiler:  Same as Strategy.
   * </pre>
   */
  @Test
  @DisplayName("Diagnostic: magnify takes optics; local takes plain Functions")
  void diagnostic_magnifyVsLocal() {
    AppEnv production =
        new AppEnv(new DbConfig("jdbc:prod", 50), new AuthConfig("auth.example.com", 3600), "acme");

    ReaderPath<DbConfig, User> loadUser =
        ReaderPath.asks(db -> new User("u-1", "Alice (" + db.url() + ")"));

    // ❌ The following does NOT compile, because magnify expects an optic:
    //   User wrong = loadUser.magnify(AppEnv::db).run(production);
    //
    // The right escape hatch when you do not have an optic is local(Function).

    // TODO: Replace answerRequired() with loadUser.local(AppEnv::db).run(production).
    User viaLocal = answerRequired();

    // Confirm the optic form gives the same answer.
    Lens<AppEnv, DbConfig> dbLens =
        Lens.of(AppEnv::db, (env, c) -> new AppEnv(c, env.auth(), env.tenant()));
    User viaMagnify = loadUser.magnify(FocusPath.of(dbLens)).run(production);

    assertThat(viaLocal).isEqualTo(viaMagnify);
  }

  /**
   * Congratulations! You've completed Tutorial 05: Optic-Polymorphic Zoom and Magnify.
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>How to pass a generated {@code FocusPath} directly to {@code ForState.zoom}
   *   <li>How {@code zoom(Iso)} reconstructs the outer state via {@code reverseGet}
   *   <li>That {@code zoom(AffinePath)} on {@code FilterableSteps} short-circuits via {@code
   *       MonadZero.zero()} when the affine target is absent
   *   <li>How {@code ReaderPath.magnify(FocusPath)} lifts a sub-service into a larger environment
   *   <li>Why {@code magnify} is reserved for optics and {@code local} for plain functions
   * </ul>
   *
   * <p>Next Steps:
   *
   * <ul>
   *   <li>See {@code MagnifyServiceLayerExample} for a runnable Reader-as-DI walkthrough
   *   <li>Read the {@code Axes of Transformer Transformation} page in hkj-book
   * </ul>
   */
}
