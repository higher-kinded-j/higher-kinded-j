// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.coretypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.higherkindedj.example.book.foundations.InMemoryOrderRepository;
import org.higherkindedj.example.book.foundations.OrderError;
import org.higherkindedj.example.book.foundations.OrderRepository;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderFocus;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.expression.ForPath;
import org.higherkindedj.hkt.resilience.CircuitBreaker;
import org.higherkindedj.hkt.resilience.CircuitBreakerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Solution for the Capstone: One Line, Six Layers Grows Up.
 *
 * <p>Each exercise gets the working answer and a teaching block: why the answer is idiomatic, one
 * alternative shape, and one common wrong attempt with its symptom. For how to learn from a
 * solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
 */
@DisplayName("Capstone Solution: One Line, Six Layers Grows Up")
public class TutorialCapstone_OneLineSixLayersGrowsUp_Solution {

  // ─── The order service (mirrors the tutorial) ─────────────────────────────

  static final UUID PLACED = UUID.fromString("00000000-0000-0000-0000-000000000001");
  static final UUID SHIPPED = UUID.fromString("00000000-0000-0000-0000-000000000002");
  static final UUID UNKNOWN = UUID.fromString("00000000-0000-0000-0000-000000000009");

  static final UnaryOperator<BigDecimal> DISCOUNT =
      price -> price.multiply(new BigDecimal("0.90")).setScale(2, RoundingMode.HALF_EVEN);

  static Order order(UUID id, OrderStatus status) {
    return new Order(
        id,
        new Customer("Ada", new EmailAddress("ada@example.com")),
        List.of(
            new LineItem("LAMP", 2, new BigDecimal("40.00")),
            new LineItem("BULB", 1, new BigDecimal("5.00"))),
        Instant.parse("2026-10-01T09:00:00Z"),
        Currency.getInstance("GBP"),
        status);
  }

  static OrderRepository orders() {
    return InMemoryOrderRepository.of(
        order(PLACED, OrderStatus.NEW), order(SHIPPED, OrderStatus.SHIPPED));
  }

  static Order stored(OrderRepository orders, UUID id) {
    return orders.find(id).getOrElse(order(UNKNOWN, OrderStatus.NEW));
  }

  /**
   * A repository whose database can go down. It holds the placed order and the shipped one; once
   * {@code goDown()} is called, {@code save} throws, as a real database call does when its server
   * is unreachable.
   */
  static final class FlakyOrders implements OrderRepository {
    private final OrderRepository orders = orders();
    private boolean down;

    void goDown() {
      down = true;
    }

    @Override
    public MaybePath<Order> find(UUID id) {
      return orders.find(id);
    }

    @Override
    public EitherPath<OrderError, Order> save(Order order) {
      if (down) {
        throw new IllegalStateException("database unavailable");
      }
      return orders.save(order);
    }
  }

  // ─── Exercise 1 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: the lookup says only that the order may be missing; {@code toEitherPath}
   * names the reason once, where the order is looked up, so every later step works on an {@code
   * EitherPath<OrderError, Order>}.
   *
   * <p>Alternative: {@code toEitherPath(() -> OrderError.notFound(PLACED))}, which builds the error
   * only when the order is missing.
   *
   * <p>Common wrong attempt: {@code toEitherPath(new OrderError.NotFound(PLACED))} in the middle of
   * a chain. With no target type there, the chain's error type becomes {@code NotFound}, and a
   * later {@code .via(orders::save)}, which can fail with {@code AlreadyShipped}, does not compile.
   * Assigned straight to an {@code EitherPath<OrderError, Order>}, as here, it compiles; the
   * factory is safe in both places.
   */
  @Test
  @DisplayName("Exercise 1: Lift the lookup")
  void exercise1_liftToEitherPath() {
    OrderRepository orders = orders();

    // SOLUTION: the lookup, with a reason for the empty case
    EitherPath<OrderError, Order> found =
        orders.find(PLACED).toEitherPath(OrderError.notFound(PLACED));
    EitherPath<OrderError, Order> missing =
        orders.find(UNKNOWN).toEitherPath(OrderError.notFound(UNKNOWN));

    assertThatEither(found.run())
        .isRight()
        .hasRightSatisfying(order -> assertThat(order.id()).isEqualTo(PLACED));
    assertThatEither(missing.run()).isLeft().hasLeft(OrderError.notFound(UNKNOWN));
  }

  // ─── Exercise 2 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: the traversal reaches every line's price at once and rebuilds the order
   * around the new prices, so no loop and no copy of {@code LineItem} or {@code Order} is written
   * by hand.
   *
   * <p>Alternative: map the lines with a stream and rebuild each {@code LineItem}, then the order.
   * It works, and it is the copying the traversal exists to save.
   *
   * <p>Common wrong attempt: {@code found.focus(...)} with the traversal. {@code EitherPath.focus}
   * takes a path that reaches one value, a {@code FocusPath} or an {@code AffinePath}, so a
   * traversal does not compile there; reaching every line is {@code modifyAll}'s job, inside a
   * {@code map}.
   */
  @Test
  @DisplayName("Exercise 2: Discount every line")
  void exercise2_discountEveryLine() {
    EitherPath<OrderError, Order> found =
        orders().find(PLACED).toEitherPath(OrderError.notFound(PLACED));

    // SOLUTION: modifyAll applies the discount to every line's price
    EitherPath<OrderError, Order> discounted =
        found.map(order -> OrderFocus.lines().price().modifyAll(DISCOUNT, order));

    assertThatEither(discounted.run())
        .isRight()
        .hasRightSatisfying(
            order ->
                assertThat(order.lines())
                    .extracting(LineItem::price)
                    .containsExactly(new BigDecimal("36.00"), new BigDecimal("4.50")));
  }

  // ─── Exercise 3 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: each step says what kind of step it is. Marking the order paid cannot
   * fail, so it is a {@code map}; the save can refuse, so it is a {@code via}.
   *
   * <p>Alternative: {@code found.via(order -> orders.save(OrderFocus.status().set(OrderStatus.PAID,
   * order)))}. Same answer, with the two steps folded into one.
   *
   * <p>Common wrong attempt: {@code found.map(orders::save)}, which nests one path inside another;
   * the save's refusal stays inside the inner path and never reaches the outer {@code Left}.
   */
  @Test
  @DisplayName("Exercise 3: Mark it paid, then save")
  void exercise3_payThenSave() {
    OrderRepository orders = orders();
    EitherPath<OrderError, Order> found =
        orders.find(PLACED).toEitherPath(OrderError.notFound(PLACED));

    // SOLUTION: map for the step that cannot fail, via for the one that can
    EitherPath<OrderError, Order> saved =
        found.map(order -> OrderFocus.status().set(OrderStatus.PAID, order)).via(orders::save);

    assertThatEither(saved.run())
        .isRight()
        .hasRightSatisfying(order -> assertThat(order.status()).isEqualTo(OrderStatus.PAID));
    assertThat(stored(orders, PLACED).status()).isEqualTo(OrderStatus.PAID);
  }

  // ─── Exercise 4 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: the whole step reads in the order things happen, each failure is part of
   * its type, and the same expression handles the shipped order by ending with the save's refusal.
   *
   * <p>Alternative: a {@code checkout(orders, id)} method that returns the {@code EitherPath}, and
   * a caller that runs it. That is how a service would hold it.
   *
   * <p>Common wrong attempt: putting the save before the changes, {@code .via(orders::save)}
   * straight after the lookup. The result returned is paid and discounted, but the repository keeps
   * the order as it was, and the test's check of what was stored fails.
   */
  @Test
  @DisplayName("Exercise 4: The whole step")
  void exercise4_oneLineSixLayersGrowsUp() {
    OrderRepository orders = orders();

    // SOLUTION: find, lift, mark paid, discount, save, run
    Either<OrderError, Order> checkedOut =
        orders
            .find(PLACED)
            .toEitherPath(OrderError.notFound(PLACED))
            .map(order -> OrderFocus.status().set(OrderStatus.PAID, order))
            .map(order -> OrderFocus.lines().price().modifyAll(DISCOUNT, order))
            .via(orders::save)
            .run();
    Either<OrderError, Order> refused =
        orders
            .find(SHIPPED)
            .toEitherPath(OrderError.notFound(SHIPPED))
            .map(order -> OrderFocus.status().set(OrderStatus.PAID, order))
            .map(order -> OrderFocus.lines().price().modifyAll(DISCOUNT, order))
            .via(orders::save)
            .run();

    assertThatEither(checkedOut)
        .isRight()
        .hasRightSatisfying(order -> assertThat(order.status()).isEqualTo(OrderStatus.PAID));
    assertThatEither(refused).isLeft().hasLeft(OrderError.alreadyShipped(SHIPPED));
    Order kept = stored(orders, PLACED);
    assertThat(kept.status()).isEqualTo(OrderStatus.PAID);
    assertThat(kept.lines())
        .extracting(LineItem::price)
        .containsExactly(new BigDecimal("36.00"), new BigDecimal("4.50"));
  }

  // ─── Exercise 5 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: the breaker protects exactly the step that talks to the outside world,
   * and nothing else. {@code EitherPath} is eager, so the protected step goes in as a supplier, and
   * the breaker sees what the save does: a {@code Left} comes back as an answer, and a thrown
   * exception counts as a failure, which opens the breaker at a threshold of one.
   *
   * <p>Alternative: name the function as a method, {@code EitherPath<OrderError, Order>
   * saveThroughBreaker(Order order)}, and pass a method reference to {@code via}. Same answer, and
   * the name says what the step does.
   *
   * <p>Common wrong attempt: {@code order -> EitherPath.withCircuitBreaker(() -> Path.right(order),
   * breaker).via(orders::save)}, which protects a step that cannot fail and runs the save outside
   * the breaker. Every protected call succeeds, so when the database goes down the breaker counts
   * no failure and stays closed, and the test's last checks fail.
   */
  @Test
  @DisplayName("Exercise 5: The save behind a circuit breaker")
  void exercise5_protectTheSave() {
    FlakyOrders orders = new FlakyOrders();
    CircuitBreaker breaker =
        CircuitBreaker.create(
            CircuitBreakerConfig.builder()
                .failureThreshold(1)
                .openDuration(Duration.ofSeconds(30))
                .build());
    EitherPath<OrderError, Order> found =
        orders.find(PLACED).toEitherPath(OrderError.notFound(PLACED));
    EitherPath<OrderError, Order> foundShipped =
        orders.find(SHIPPED).toEitherPath(OrderError.notFound(SHIPPED));

    // SOLUTION: the save, and only the save, runs inside the breaker
    Function<Order, EitherPath<OrderError, Order>> protectedSave =
        order -> EitherPath.withCircuitBreaker(() -> orders.save(order), breaker);

    assertThatEither(found.via(protectedSave).run()).isRight();
    assertThatEither(foundShipped.via(protectedSave).run())
        .isLeft()
        .hasLeft(OrderError.alreadyShipped(SHIPPED));
    // The refusal is an answer, not a fault: the breaker counted two successful saves.
    assertThat(breaker.metrics().successfulCalls()).isEqualTo(2);
    assertThat(breaker.metrics().failedCalls()).isZero();

    // When the database goes down, the save throws inside the breaker, which counts the failure
    // and, with a failure threshold of one, opens.
    orders.goDown();
    assertThatThrownBy(() -> found.via(protectedSave))
        .hasStackTraceContaining("database unavailable");
    assertThat(breaker.metrics().failedCalls()).isEqualTo(1);
    assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);
  }

  // ─── Exercise 6 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code ForPath} gives each value a name, which reads better than nested
   * lambdas once a later step needs an earlier value. The step itself is Exercise 3's, unchanged.
   *
   * <p>Alternative: the {@code map} and {@code via} chain from Exercise 3. With two steps and no
   * value reused, the chain is shorter; {@code ForPath} pays off as the steps grow.
   *
   * <p>Common wrong attempt: {@code from} for the paid order. Marking it paid returns an {@code
   * Order}, not a path, so it is a {@code let}; {@code from} needs a step that returns an {@code
   * EitherPath}, and with an {@code Order} it does not compile.
   */
  @Test
  @DisplayName("Exercise 6: Exercise 3's step with ForPath")
  void exercise6_forPathSpelling() {
    OrderRepository orders = orders();
    EitherPath<OrderError, Order> found =
        orders.find(PLACED).toEitherPath(OrderError.notFound(PLACED));

    // SOLUTION: from the lookup, let the paid order, from the save, yield the saved order
    EitherPath<OrderError, Order> saved =
        ForPath.from(found)
            .let(order -> OrderFocus.status().set(OrderStatus.PAID, order))
            .from(t -> orders.save(t._2()))
            .yield((order, paid, stored) -> stored);

    assertThatEither(saved.run())
        .isRight()
        .hasRightSatisfying(order -> assertThat(order.status()).isEqualTo(OrderStatus.PAID));
    assertThat(stored(orders, PLACED).status()).isEqualTo(OrderStatus.PAID);
  }

  // ─── Exercise 7 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: the {@code switch} over the sealed {@code OrderError} handles the one
   * error it can put right and hands the other back unchanged, and the compiler checks that every
   * kind of error has a case: a new one fails to compile until it is handled.
   *
   * <p>Alternative: {@code error instanceof OrderError.NotFound(UUID id) ? Path.right(...) :
   * Path.left(error)}. Shorter, but a new kind of error falls into the {@code left} branch without
   * the compiler saying so.
   *
   * <p>Common wrong attempt: {@code recover(error -> order(...))}. It turns every error into a
   * fresh order, the refused save included, and the refusal is lost.
   */
  @Test
  @DisplayName("Exercise 7: Recover a missing order, pass a refusal on")
  void exercise7_recovery() {
    OrderRepository orders = orders();
    EitherPath<OrderError, Order> missing =
        orders.find(UNKNOWN).toEitherPath(OrderError.notFound(UNKNOWN));
    EitherPath<OrderError, Order> refused =
        orders.find(SHIPPED).toEitherPath(OrderError.notFound(SHIPPED)).via(orders::save);

    // SOLUTION: a fresh order for NotFound, the refusal back on the Left
    Function<OrderError, EitherPath<OrderError, Order>> startAfresh =
        error ->
            switch (error) {
              case OrderError.NotFound(UUID id) -> Path.right(order(id, OrderStatus.NEW));
              case OrderError.AlreadyShipped shipped -> Path.left(shipped);
            };

    assertThatEither(missing.recoverWith(startAfresh).run())
        .isRight()
        .hasRightSatisfying(
            order -> {
              assertThat(order.id()).isEqualTo(UNKNOWN);
              assertThat(order.status()).isEqualTo(OrderStatus.NEW);
            });
    assertThatEither(refused.recoverWith(startAfresh).run())
        .isLeft()
        .hasLeft(OrderError.alreadyShipped(SHIPPED));
  }
}
