// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.effect.ReaderPath;
import org.higherkindedj.hkt.expression.ForState;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Solution for Tutorial 05: Optic-Polymorphic Zoom and Magnify.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions: the working
 * code is paired with a brief commentary on <em>why</em> the chosen form is idiomatic, what an
 * alternative would look like, and a common wrong attempt to be aware of.
 */
public class Tutorial05_ZoomAndMagnify_Solution {

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

  static final Affine<OptionalAddressCustomer, Address> optionalAddressAffine =
      Affine.of(
          OptionalAddressCustomer::address,
          (c, a) -> new OptionalAddressCustomer(c.name(), Optional.of(a)));

  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: passing a {@code FocusPath} directly to {@code zoom} keeps the chain
   * uniform with the rest of the {@code @GenerateFocus}-driven code, with no manual {@code
   * .toLens()} ceremony.
   *
   * <p>Alternative: {@code zoom(addressPath.toLens())}. Same answer; the new overload is sugar.
   *
   * <p>Common wrong attempt: composing {@code addressPath.via(streetPath)} into a single {@code
   * FocusPath<Customer, String>} and calling {@code zoom} on that. It compiles, but the inner state
   * in the zoom block is now a {@code String}, which loses the sub-record-then-fields shape that
   * makes nested zoom readable.
   */
  @Test
  @DisplayName("Exercise 1: zoom(FocusPath) narrows state via a generated path")
  void exercise1_zoomFocusPath() {
    IdMonad idMonad = IdMonad.instance();
    Customer initial =
        new Customer("Alice", new Address("123 Main St", "Springfield", "62701"), 100);
    FocusPath<Customer, Address> addressPath = FocusPath.of(addressLens);

    // SOLUTION: zoom takes the FocusPath directly; no .toLens() required.
    Kind<IdKind.Witness, Customer> result =
        ForState.withState(idMonad, IdKindHelper.ID.widen(Id.of(initial)))
            .zoom(addressPath)
            .update(streetLens, "456 Oak Ave")
            .endZoom()
            .yield();

    Customer finalCustomer = IdKindHelper.ID.unwrap(result);
    assertThat(finalCustomer.address().street()).isEqualTo("456 Oak Ave");
    assertThat(finalCustomer.address().city()).isEqualTo("Springfield");
  }

  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code zoom(Iso)} expresses "operate on an equivalent representation,
   * then put it back." The reconstruction via {@code reverseGet} happens automatically on {@code
   * endZoom}, so the comprehension stays declarative.
   *
   * <p>Alternative: extract the iso to a {@code Lens} via {@code iso.asLens()} and pass that to
   * {@code zoom}. Same answer; the new overload is sugar.
   *
   * <p>Common wrong attempt: writing a hand-rolled {@code map} that calls {@code iso.get}, mutates
   * the view, and then writes back with {@code iso.reverseGet}. That works but loses the
   * comprehension shape and silently strips away nested {@code zoom} composition.
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

    IdMonad idMonad = IdMonad.instance();
    Customer initial = new Customer("Alice", new Address("123", "Springfield", "00000"), 0);

    // SOLUTION: zoom(iso) operates on the iso's target type for the duration of the block.
    Kind<IdKind.Witness, Customer> result =
        ForState.withState(idMonad, IdKindHelper.ID.widen(Id.of(initial)))
            .zoom(customerToView)
            .update(viewLoyalty, 999)
            .endZoom()
            .yield();

    Customer finalCustomer = IdKindHelper.ID.unwrap(result);
    assertThat(finalCustomer.loyaltyPoints()).isEqualTo(999);
    assertThat(finalCustomer.name()).isEqualTo("Alice");
  }

  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: when the affine target is present, the zoom block runs exactly as it
   * would under a {@code Lens} zoom. The user does not need to remember whether the field is
   * optional; the path type encodes that.
   *
   * <p>Alternative: extract the inner {@code Optional} manually with {@code from(...)} and a {@code
   * matchThen} on {@code Optional::ofNullable}. More plumbing, no benefit.
   *
   * <p>Common wrong attempt: trying to call {@code zoom(affinePath)} on a plain {@code Steps} (i.e.
   * a comprehension over {@code Id}). The overload is intentionally only available on {@code
   * FilterableSteps} — without {@code MonadZero} there is no clean way to express "the focus is
   * absent; skip the rest". The compiler error guides you to use a {@code MonadZero}.
   */
  @Test
  @DisplayName("Exercise 3: zoom(AffinePath) updates inner state when target is present")
  void exercise3_zoomAffinePathPresent() {
    MaybeMonad maybeMonad = MaybeMonad.INSTANCE;
    OptionalAddressCustomer initial =
        new OptionalAddressCustomer(
            "Alice", Optional.of(new Address("123 Main St", "Springfield", "62701")));
    AffinePath<OptionalAddressCustomer, Address> addressPath = AffinePath.of(optionalAddressAffine);

    // SOLUTION: zoom over the affine path; on FilterableSteps it dispatches to the
    // present-target branch when the affine has a value.
    Kind<MaybeKind.Witness, OptionalAddressCustomer> result =
        ForState.withState(maybeMonad, MAYBE.just(initial))
            .zoom(addressPath)
            .update(streetLens, "Oak Ave")
            .endZoom()
            .yield();

    Maybe<OptionalAddressCustomer> outcome = MAYBE.narrow(result);
    assertThat(outcome.isJust()).isTrue();
    Address updated = outcome.orElse(initial).address().orElseThrow();
    assertThat(updated.street()).isEqualTo("Oak Ave");
  }

  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: the same code as exercise 3 just works on absent input. The
   * comprehension short-circuits via {@code MonadZero.zero()}, and the caller observes a {@code
   * Nothing}.
   *
   * <p>Alternative: pre-checking for presence with {@code when(c -> c.address().isPresent())}
   * before the zoom. The result is the same, but you have asserted the precondition rather than
   * letting the path type express it.
   *
   * <p>Common wrong attempt: writing a manual {@code Optional.ifPresent} inside a {@code from}
   * step. That dispatches based on a value, not on the surrounding monad's zero, so it does not
   * actually short-circuit downstream operations.
   */
  @Test
  @DisplayName("Exercise 4: zoom(AffinePath) short-circuits to Nothing when target is absent")
  void exercise4_zoomAffinePathAbsent() {
    MaybeMonad maybeMonad = MaybeMonad.INSTANCE;
    OptionalAddressCustomer initial = new OptionalAddressCustomer("Alice", Optional.empty());
    AffinePath<OptionalAddressCustomer, Address> addressPath = AffinePath.of(optionalAddressAffine);

    // SOLUTION: identical chain to exercise 3; the empty input short-circuits the whole result.
    Kind<MaybeKind.Witness, OptionalAddressCustomer> result =
        ForState.withState(maybeMonad, MAYBE.just(initial))
            .zoom(addressPath)
            .update(streetLens, "Oak Ave")
            .endZoom()
            .yield();

    assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
  }

  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: each sub-service stays narrowly typed against its dependency ({@code
   * DbConfig}). Composition with the larger environment shape happens at the call site via {@code
   * magnify(FocusPath)}, which keeps the lift visible and removes the boilerplate of a manual
   * {@code Function} extraction.
   *
   * <p>Alternative: {@code loadUser.local(AppEnv::db).run(production)}. Same answer; the difference
   * is naming intent: {@code magnify} signals "via an optic", {@code local} signals "via a plain
   * function".
   *
   * <p>Common wrong attempt: rewriting {@code loadUser} to take {@code AppEnv} directly. That works
   * but bleeds the wider environment into a service that does not need it, making the service
   * harder to reuse and harder to test in isolation.
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

    // SOLUTION: magnify(FocusPath) lifts the narrowly-typed service into the larger environment.
    User user = loadUser.magnify(dbPath).run(production);

    assertThat(user.id()).isEqualTo("u-1");
    assertThat(user.name()).contains("jdbc:prod");
  }

  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code local} accepts any {@code Function<R2, R>}, including method
   * references like {@code AppEnv::db}. It is the right tool when an environment adaptation has no
   * natural optic, for example when you are bridging two records with no shared structure or
   * adapting to a third-party type that is not part of your @GenerateFocus model.
   *
   * <p>Alternative: building a one-off {@code Lens<AppEnv, DbConfig>} just to call {@code magnify}.
   * More boilerplate, no benefit.
   *
   * <p>Common wrong attempt: trying to call {@code magnify(AppEnv::db)} directly. The overload does
   * not exist; the compile error directs you to {@code local} for the bare-{@code Function} form.
   * The naming convention is deliberate: {@code magnify} is reserved for optics so the call site
   * signals "this adaptation is a domain-modelled optic" rather than "this is an arbitrary
   * function".
   */
  @Test
  @DisplayName("Diagnostic: magnify takes optics; local takes plain Functions")
  void diagnostic_magnifyVsLocal() {
    AppEnv production =
        new AppEnv(new DbConfig("jdbc:prod", 50), new AuthConfig("auth.example.com", 3600), "acme");

    ReaderPath<DbConfig, User> loadUser =
        ReaderPath.asks(db -> new User("u-1", "Alice (" + db.url() + ")"));

    // SOLUTION: local(Function) is the escape hatch; magnify(FocusPath) is the optic form.
    User viaLocal = loadUser.local(AppEnv::db).run(production);

    Lens<AppEnv, DbConfig> dbLens =
        Lens.of(AppEnv::db, (env, c) -> new AppEnv(c, env.auth(), env.tenant()));
    User viaMagnify = loadUser.magnify(FocusPath.of(dbLens)).run(production);

    assertThat(viaLocal).isEqualTo(viaMagnify);
  }
}
