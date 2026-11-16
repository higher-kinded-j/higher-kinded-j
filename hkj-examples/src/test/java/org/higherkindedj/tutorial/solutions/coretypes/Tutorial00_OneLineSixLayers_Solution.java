// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.coretypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.either;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.higherkindedj.example.book.foundations.InMemoryOrderRepository;
import org.higherkindedj.example.book.foundations.OrderError;
import org.higherkindedj.example.book.foundations.OrderRepository;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderFocus;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.instances.Instances;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Solution for Tutorial 00: One Line, Six Layers.
 *
 * <p>This file is the "teaching solution" template: each exercise gets the working answer plus a
 * short note on <em>why</em> it is idiomatic, one alternative shape, and one common wrong attempt
 * and its symptom.
 */
@DisplayName("Tutorial 00 Solution: One Line, Six Layers")
public class Tutorial00_OneLineSixLayers_Solution {

  // ─── The order service (mirrors the tutorial) ─────────────────────────────

  static final UUID PLACED = UUID.fromString("00000000-0000-0000-0000-000000000001");
  static final UUID SHIPPED = UUID.fromString("00000000-0000-0000-0000-000000000002");
  static final UUID UNKNOWN = UUID.fromString("00000000-0000-0000-0000-000000000009");
  static final String NEW_EMAIL = "ada@example.org";

  static Order order(UUID id, OrderStatus status) {
    return new Order(
        id,
        new Customer("Ada", new EmailAddress("ada@example.com")),
        List.of(new LineItem("LAMP", 1, new BigDecimal("40.00"))),
        Instant.parse("2026-10-01T09:00:00Z"),
        Currency.getInstance("GBP"),
        status);
  }

  static OrderRepository orders() {
    return InMemoryOrderRepository.of(
        order(PLACED, OrderStatus.NEW), order(SHIPPED, OrderStatus.SHIPPED));
  }

  static String storedEmail(OrderRepository orders, UUID id) {
    return orders.find(id).map(order -> order.customer().email().value()).getOrElse("absent");
  }

  // ─── Exercise 0 ────────────────────────────────────────────────────────────

  /**
   * A setup check with nothing to write: if it compiles and passes, the Effect Path API, the
   * generated {@code OrderFocus}, and the test runtime are all on the classpath.
   */
  @Test
  @DisplayName("Exercise 0: Setup check")
  void exercise0_setupCheck() {
    MaybePath<Integer> sanity = Path.just(42);
    assertThat(sanity.getOrElse(-1)).isEqualTo(42);
    assertThat(OrderFocus.customer().email().value().get(order(PLACED, OrderStatus.NEW)))
        .isEqualTo("ada@example.com");
  }

  // ─── Exercise 1 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code getOrElse} says "the value, or this stand-in when it is absent"
   * without ever exposing a {@code null} or forcing an {@code if} on the caller.
   *
   * <p>Alternative: {@code path.run().orElse(...)}, through the {@code Maybe} underneath. Same
   * answer; the {@code MaybePath} method saves a step.
   *
   * <p>Common wrong attempt: {@code path.run().get()}. On an empty path, {@code get()} throws
   * {@code NoSuchElementException}, and the absent case we set out to handle comes back as an
   * exception.
   */
  @Test
  @DisplayName("Exercise 1: Layer 1, the effect type")
  void exercise1_effectType() {
    MaybePath<Order> path = orders().find(PLACED);

    // SOLUTION: take the order out, with a stand-in for the empty case
    Order order = path.getOrElse(order(UNKNOWN, OrderStatus.NEW));

    assertThat(order.id()).isEqualTo(PLACED);
    assertThat(order.customer().email().value()).isEqualTo("ada@example.com");
  }

  // ─── Exercise 2 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code toEitherPath(error)} is the natural transformation from {@code
   * MaybePath} to {@code EitherPath}. It names the reason for the empty case once, where the order
   * is looked up, and leaves a present order untouched.
   *
   * <p>Alternative: {@code toEitherPath(() -> OrderError.notFound(id))}, which builds the error
   * only when the path is empty. Worth it when the error is costly to build; here it is a small
   * record.
   *
   * <p>Common wrong attempt: {@code toEitherPath(new OrderError.NotFound(id))} in the middle of a
   * chain. With no target type there, the chain's error type becomes {@code NotFound} rather than
   * {@code OrderError}, and a later {@code .via(orders::save)}, which can fail with {@code
   * AlreadyShipped}, does not compile. The factory {@code OrderError.notFound(id)} returns the
   * sealed type.
   */
  @Test
  @DisplayName("Exercise 2: Layer 2, the natural transformation")
  void exercise2_naturalTransformation() {
    OrderRepository orders = orders();

    // SOLUTION: the same natural transformation, for an order that exists and one that does not
    EitherPath<OrderError, Order> present =
        orders.find(PLACED).toEitherPath(OrderError.notFound(PLACED));
    EitherPath<OrderError, Order> absent =
        orders.find(UNKNOWN).toEitherPath(OrderError.notFound(UNKNOWN));

    assertThatEither(present.run())
        .isRight()
        .hasRightSatisfying(order -> assertThat(order.id()).isEqualTo(PLACED));
    assertThatEither(absent.run()).isLeft().hasLeft(OrderError.notFound(UNKNOWN));
  }

  // ─── Exercise 3 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: the optic rebuilds {@code Order}, {@code Customer} and {@code
   * EmailAddress} on the way back up, so we write none of the copies, and the original order is
   * left as it was.
   *
   * <p>Alternative: {@code OrderFocus.customer().email().value().modify(old -> NEW_EMAIL,
   * original)}. {@code modify} takes a function of the old value; {@code set} is the shorter form
   * when the new value does not depend on it.
   *
   * <p>Common wrong attempt: building {@code new Order(...)} by hand with a new {@code Customer}.
   * It works until {@code Order} gains a component, and then every hand-written copy must change.
   */
  @Test
  @DisplayName("Exercise 3: Layer 3, the optic")
  void exercise3_opticSet() {
    Order original = order(PLACED, OrderStatus.NEW);

    // SOLUTION: set the email three records deep
    Order changed = OrderFocus.customer().email().value().set(NEW_EMAIL, original);

    assertThat(changed.customer().email().value()).isEqualTo(NEW_EMAIL);
    assertThat(changed.customer().name()).isEqualTo("Ada");
    assertThat(original.customer().email().value()).isEqualTo("ada@example.com");
  }

  // ─── Exercise 4 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code map} applies a step that cannot fail without unwrapping the path,
   * and does nothing on a {@code Left}. The optic's {@code set} is exactly such a step.
   *
   * <p>Alternative: name the step, {@code UnaryOperator<Order> changeEmail = order -> ...}, and
   * pass {@code found.map(changeEmail)}. Useful when the same change appears in several chains.
   *
   * <p>Common wrong attempt: {@code found.via(order -> Path.right(...set(...)))}. It compiles and
   * gives the same answer, but {@code via} is for a step that can fail; wrapping a step that cannot
   * in {@code Path.right} hides which kind of step it is.
   */
  @Test
  @DisplayName("Exercise 4: Layer 4, Functor's map")
  void exercise4_functorMap() {
    EitherPath<OrderError, Order> found =
        orders().find(PLACED).toEitherPath(OrderError.notFound(PLACED));

    // SOLUTION: map the optic's set through the path
    EitherPath<OrderError, Order> changed =
        found.map(order -> OrderFocus.customer().email().value().set(NEW_EMAIL, order));

    assertThatEither(changed.run())
        .isRight()
        .hasRightSatisfying(
            order -> assertThat(order.customer().email().value()).isEqualTo(NEW_EMAIL));
  }

  // ─── Exercise 5 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code via} runs a step that needs the order and can fail for a reason
   * of its own. When {@code save} refuses a shipped order, its {@code Left} ends the chain, and no
   * {@code try}/{@code catch} is needed.
   *
   * <p>Alternative: {@code changed.via(order -> orders.save(order))}, the lambda the method
   * reference stands for. Same answer.
   *
   * <p>Common wrong attempt: {@code changed.map(orders::save)}. It gives an {@code
   * EitherPath<OrderError, EitherPath<OrderError, Order>>}, a path inside a path, so it does not
   * fit the {@code EitherPath<OrderError, Order>} variable; and even unassigned, the refusal stays
   * inside the inner path and never reaches the outer {@code Left}.
   */
  @Test
  @DisplayName("Exercise 5: Layer 5, Monad's via")
  void exercise5_monadVia() {
    OrderRepository orders = orders();
    EitherPath<OrderError, Order> changed =
        orders
            .find(PLACED)
            .toEitherPath(OrderError.notFound(PLACED))
            .map(order -> OrderFocus.customer().email().value().set(NEW_EMAIL, order));
    EitherPath<OrderError, Order> changedShipped =
        orders
            .find(SHIPPED)
            .toEitherPath(OrderError.notFound(SHIPPED))
            .map(order -> OrderFocus.customer().email().value().set(NEW_EMAIL, order));

    // SOLUTION: chain the save, which may refuse
    EitherPath<OrderError, Order> saved = changed.via(orders::save);
    EitherPath<OrderError, Order> refused = changedShipped.via(orders::save);

    assertThatEither(saved.run())
        .isRight()
        .hasRightSatisfying(
            order -> assertThat(order.customer().email().value()).isEqualTo(NEW_EMAIL));
    assertThatEither(refused.run()).isLeft().hasLeft(OrderError.alreadyShipped(SHIPPED));
    assertThat(storedEmail(orders, PLACED)).isEqualTo(NEW_EMAIL);
  }

  // ─── Exercise 6 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code Functor}'s {@code map} and {@code Monad}'s {@code flatMap} are
   * the same operations as the Path's {@code map} and {@code via}, written once for any container
   * {@code F}. The instance is a value we pass, from {@code Instances.monad(either())}; nothing
   * chooses it for us.
   *
   * <p>Alternative: the change step on its own can be written once for any container, as a method
   * that takes a {@code Functor<F>} and a {@code Kind<F, Order>} and returns {@code
   * functor.map(order -> ...set(NEW_EMAIL, order), orders)}. The save cannot, because it is {@code
   * Either}'s own; Tutorial 01 onwards builds methods of the first kind.
   *
   * <p>Common wrong attempt: {@code monad.flatMap(order -> orders.save(order), changed)}. It does
   * not compile: {@code save} returns an {@code EitherPath}, not a {@code Kind}. {@code run()}
   * gives its {@code Either}, and {@code EITHER.widen} the {@code Kind}.
   */
  @Test
  @DisplayName("Exercise 6: Layer 6, the algebra")
  void exercise6_theAlgebra() {
    OrderRepository orders = orders();
    Monad<EitherKind.Witness<OrderError>> monad = Instances.monad(either());
    Kind<EitherKind.Witness<OrderError>, Order> found =
        EITHER.widen(orders.find(PLACED).toEitherPath(OrderError.notFound(PLACED)).run());
    Kind<EitherKind.Witness<OrderError>, Order> foundShipped =
        EITHER.widen(orders.find(SHIPPED).toEitherPath(OrderError.notFound(SHIPPED)).run());

    // SOLUTION: the change with map, then the save with flatMap, on the Monad
    Kind<EitherKind.Witness<OrderError>, Order> saved =
        monad.flatMap(
            order -> EITHER.widen(orders.save(order).run()),
            monad.map(order -> OrderFocus.customer().email().value().set(NEW_EMAIL, order), found));
    Kind<EitherKind.Witness<OrderError>, Order> refused =
        monad.flatMap(
            order -> EITHER.widen(orders.save(order).run()),
            monad.map(
                order -> OrderFocus.customer().email().value().set(NEW_EMAIL, order),
                foundShipped));

    OrderRepository pathOrders = orders();
    Either<OrderError, Order> viaPath =
        pathOrders
            .find(PLACED)
            .toEitherPath(OrderError.notFound(PLACED))
            .map(order -> OrderFocus.customer().email().value().set(NEW_EMAIL, order))
            .via(pathOrders::save)
            .run();
    assertThat(EITHER.narrow(saved)).isEqualTo(viaPath);
    assertThat(EITHER.narrow(refused)).isEqualTo(Either.left(OrderError.alreadyShipped(SHIPPED)));
    assertThat(storedEmail(orders, PLACED)).isEqualTo(NEW_EMAIL);
  }

  // ─── Exercise 7 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: the line reads in the order things happen, and every failure is part of
   * its type, a value the caller can see, not an exception it has to know about. {@code run()} at
   * the end gives the {@code Either}.
   *
   * <p>Alternative: return the {@code EitherPath} from a service method, as the book's {@code
   * changeEmail} does, and call {@code run()} where the result is used.
   *
   * <p>Common wrong attempt: calling {@code run()} after every step and rebuilding a path from the
   * {@code Either}. Each step then has to unwrap and rewrap, which is the work the path does for
   * us.
   */
  @Test
  @DisplayName("Exercise 7: The whole line")
  void exercise7_oneLineSixLayers() {
    OrderRepository orders = orders();

    // SOLUTION: the whole line, once for each id
    Either<OrderError, Order> changed =
        orders
            .find(PLACED)
            .toEitherPath(OrderError.notFound(PLACED))
            .map(order -> OrderFocus.customer().email().value().set(NEW_EMAIL, order))
            .via(orders::save)
            .run();
    Either<OrderError, Order> missing =
        orders
            .find(UNKNOWN)
            .toEitherPath(OrderError.notFound(UNKNOWN))
            .map(order -> OrderFocus.customer().email().value().set(NEW_EMAIL, order))
            .via(orders::save)
            .run();
    Either<OrderError, Order> refused =
        orders
            .find(SHIPPED)
            .toEitherPath(OrderError.notFound(SHIPPED))
            .map(order -> OrderFocus.customer().email().value().set(NEW_EMAIL, order))
            .via(orders::save)
            .run();

    assertThatEither(changed)
        .isRight()
        .hasRightSatisfying(
            order -> {
              assertThat(order.id()).isEqualTo(PLACED);
              assertThat(order.customer().email().value()).isEqualTo(NEW_EMAIL);
            });
    assertThatEither(missing).isLeft().hasLeft(OrderError.notFound(UNKNOWN));
    assertThatEither(refused).isLeft().hasLeft(OrderError.alreadyShipped(SHIPPED));
    assertThat(storedEmail(orders, PLACED)).isEqualTo(NEW_EMAIL);
  }

  // ─── Diagnostic ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code set} inside {@code map} changes the email and keeps the {@code
   * Order}, which is what {@code save} needs. {@code focus} on the path narrows to the focused
   * value, which is right only when the narrowed value is all we want.
   *
   * <p>Alternative: {@code found.map(order -> OrderFocus.customer().email().value().modify(old ->
   * NEW_EMAIL, order))}. Same shape; {@code modify} when the new value depends on the old.
   *
   * <p>Common wrong attempt: {@code found.focus(OrderFocus.customer().email())}. The path now
   * carries an {@code EmailAddress}, and {@code .via(orders::save)} does not compile.
   */
  @Test
  @DisplayName("Diagnostic: focus narrows, set inside map keeps the order")
  void diagnostic_focusVsSet() {
    OrderRepository orders = orders();
    EitherPath<OrderError, Order> found =
        orders.find(PLACED).toEitherPath(OrderError.notFound(PLACED));

    // SOLUTION: set inside map, so the path still carries an Order
    EitherPath<OrderError, Order> changed =
        found.map(order -> OrderFocus.customer().email().value().set(NEW_EMAIL, order));

    assertThatEither(changed.via(orders::save).run())
        .isRight()
        .hasRightSatisfying(
            order -> assertThat(order.customer().email().value()).isEqualTo(NEW_EMAIL));

    EitherPath<OrderError, String> email = found.focus(OrderFocus.customer().email().value());
    assertThatEither(email.run()).isRight().hasRight("ada@example.com");
  }
}
