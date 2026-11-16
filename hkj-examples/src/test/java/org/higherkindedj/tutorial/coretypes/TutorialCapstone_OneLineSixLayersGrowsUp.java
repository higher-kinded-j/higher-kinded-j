// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.coretypes;

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
 * Capstone Tutorial: One Line, Six Layers Grows Up — From One Line to a Checkout Step.
 *
 * <p>Tutorial 00 wrote one line that changed the email on an order and touched every layer of the
 * library. This capstone grows the same line into a checkout step: find the order, mark it paid,
 * take a discount off every line, and save it, where the save refuses an order that has already
 * shipped.
 *
 * <pre>
 *   orders.find(id)                                     // 1. Effect type: MaybePath&lt;Order&gt;
 *       .toEitherPath(OrderError.notFound(id))          // 2. Natural transformation
 *       .map(order -&gt; OrderFocus.status()             // 3 and 4. Optics, applied with
 *           .set(OrderStatus.PAID, order))              //    map (Functor): mark it paid,
 *       .map(order -&gt; OrderFocus.lines().price()      //    then discount every line
 *           .modifyAll(DISCOUNT, order))
 *       .via(orders::save);                             // 5. Monad: a save that can refuse
 * </pre>
 *
 * <p>The examples use the order service the book's Foundations, Optics and Mapping chapters share,
 * as in Tutorial 00, and {@link OrderFocus}, generated on {@code Order}, for its optics.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>A traversal changes every line's price at once.
 *   <li>{@code map} for a step that cannot fail, {@code via} for one that can.
 *   <li>A circuit breaker around the one step that talks to the outside world.
 *   <li>{@code ForPath}, which names each value in a chain.
 *   <li>A recovery that handles one error and passes the others on.
 * </ul>
 *
 * <p>Prerequisites: the Foundations journey, including Tutorial 00, and the Effect API journey.
 *
 * <p>Hints. Each exercise's hints climb from a Nudge, which names the idea, to a Strategy, which
 * names the method, to a Spoiler, which is the code itself. Stop reading once you have enough.
 *
 * <p>Replace each {@code answerRequired()} with the working code; the tests go green one by one.
 */
@DisplayName("Capstone: One Line, Six Layers Grows Up")
public class TutorialCapstone_OneLineSixLayersGrowsUp {

  /** Helper for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // ─── The order service ────────────────────────────────────────────────────

  /** An order that has been placed and not yet paid. */
  static final UUID PLACED = UUID.fromString("00000000-0000-0000-0000-000000000001");

  /** An order that has already shipped, which the repository refuses to save again. */
  static final UUID SHIPPED = UUID.fromString("00000000-0000-0000-0000-000000000002");

  /** An id no order has. */
  static final UUID UNKNOWN = UUID.fromString("00000000-0000-0000-0000-000000000009");

  /** Ten per cent off a price, kept to two decimal places. */
  static final UnaryOperator<BigDecimal> DISCOUNT =
      price -> price.multiply(new BigDecimal("0.90")).setScale(2, RoundingMode.HALF_EVEN);

  /** Ada's order for two lamps at 40.00 and a bulb at 5.00, with the given id and status. */
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

  /** A fresh repository holding the placed order and the shipped one. */
  static OrderRepository orders() {
    return InMemoryOrderRepository.of(
        order(PLACED, OrderStatus.NEW), order(SHIPPED, OrderStatus.SHIPPED));
  }

  /** The order the repository now holds, or a stand-in with the unknown id when it holds none. */
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

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 1: Lift the lookup
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 1: Lift the lookup
   *
   * <p>{@code orders.find} returns a {@code MaybePath}, which says only that an order may be
   * missing. Every later step needs to know why, so the lookup starts by giving the empty case a
   * reason.
   *
   * <p>Task: look up the placed order and the unknown id, each as a path that says why it failed.
   *
   * <pre>
   *   // Nudge:    The lookup is Tutorial 00's Exercise 2 again.
   *   // Strategy: MaybePath's toEitherPath, with the factory OrderError.notFound(id).
   *   // Spoiler:  orders.find(PLACED).toEitherPath(OrderError.notFound(PLACED))
   * </pre>
   */
  @Test
  @DisplayName("Exercise 1: Lift the lookup")
  void exercise1_liftToEitherPath() {
    OrderRepository orders = orders();

    // TODO: Replace both answerRequired() calls with a lookup that carries a reason
    EitherPath<OrderError, Order> found = answerRequired(); // PLACED
    EitherPath<OrderError, Order> missing = answerRequired(); // UNKNOWN

    assertThatEither(found.run())
        .isRight()
        .hasRightSatisfying(order -> assertThat(order.id()).isEqualTo(PLACED));
    assertThatEither(missing.run()).isLeft().hasLeft(OrderError.notFound(UNKNOWN));
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 2: Discount every line
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 2: Discount every line
   *
   * <p>{@code OrderFocus.lines().price()} is a traversal: it reaches the price of every line at
   * once. Changing the prices through it returns a new {@code Order}, with every line rebuilt.
   *
   * <p>Task: take ten per cent off every line's price, with {@code DISCOUNT}, inside the found
   * path.
   *
   * <pre>
   *   // Nudge:    The change cannot fail, so the path applies it to the order inside.
   *   // Strategy: map, with the traversal's modifyAll(DISCOUNT, order).
   *   // Spoiler:  found.map(order -&gt; OrderFocus.lines().price().modifyAll(DISCOUNT, order))
   * </pre>
   */
  @Test
  @DisplayName("Exercise 2: Discount every line")
  void exercise2_discountEveryLine() {
    EitherPath<OrderError, Order> found =
        orders().find(PLACED).toEitherPath(OrderError.notFound(PLACED));

    // TODO: Replace answerRequired() with the found path, every line's price discounted
    EitherPath<OrderError, Order> discounted = answerRequired();

    assertThatEither(discounted.run())
        .isRight()
        .hasRightSatisfying(
            order ->
                assertThat(order.lines())
                    .extracting(LineItem::price)
                    .containsExactly(new BigDecimal("36.00"), new BigDecimal("4.50")));
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 3: Mark it paid, then save
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 3: Mark it paid, then save
   *
   * <p>Marking the order paid cannot fail, so it is a {@code map}. The save can refuse, so it is a
   * {@code via}. {@code OrderFocus.status()} reaches the order's status.
   *
   * <p>Task: mark the found order paid, then save it.
   *
   * <pre>
   *   // Nudge:    Two steps: one that cannot fail, then one that can.
   *   // Strategy: map with the status optic's set, then via with the save.
   *   // Spoiler:  found.map(order -&gt; OrderFocus.status().set(OrderStatus.PAID, order))
   *   //               .via(orders::save)
   * </pre>
   */
  @Test
  @DisplayName("Exercise 3: Mark it paid, then save")
  void exercise3_payThenSave() {
    OrderRepository orders = orders();
    EitherPath<OrderError, Order> found =
        orders.find(PLACED).toEitherPath(OrderError.notFound(PLACED));

    // TODO: Replace answerRequired() with the found path, marked paid and then saved
    EitherPath<OrderError, Order> saved = answerRequired();

    assertThatEither(saved.run())
        .isRight()
        .hasRightSatisfying(order -> assertThat(order.status()).isEqualTo(OrderStatus.PAID));
    assertThat(stored(orders, PLACED).status()).isEqualTo(OrderStatus.PAID);
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 4: The whole step
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 4: The whole step
   *
   * <p>Find, give the empty case a reason, mark the order paid, discount every line, save, and
   * {@code run()} for the {@code Either}. The same expression saves the placed order and ends with
   * {@code AlreadyShipped} for the shipped one. The order of the steps matters: the repository
   * should hold the paid, discounted order.
   *
   * <p>Task: write the whole step for each id, ending in {@code run()}.
   *
   * <pre>
   *   // Nudge:    Exercises 1, 2 and 3 make one chain, with the save last.
   *   // Strategy: toEitherPath, map with the status optic, map with the traversal, via the save,
   *   //           then run().
   *   // Spoiler:  the chain in this class's Javadoc, with run() on the end.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 4: The whole step")
  void exercise4_oneLineSixLayersGrowsUp() {
    OrderRepository orders = orders();

    // TODO: Replace each answerRequired() with the whole step for its id
    Either<OrderError, Order> checkedOut = answerRequired(); // PLACED
    Either<OrderError, Order> refused = answerRequired(); // SHIPPED

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

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 5: The save behind a circuit breaker
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 5: The save behind a circuit breaker
   *
   * <p>A real repository talks to a database that can fail, and a circuit breaker stops calling it
   * after repeated failures. {@code EitherPath} is eager: by the time one exists its work has run,
   * so it has no chained {@code withCircuitBreaker}. Instead the static {@code
   * EitherPath.withCircuitBreaker(step, breaker)} takes the step as a supplier, and runs it inside
   * the breaker. A {@code Left} is an answer, not a fault, so a refused save does not count against
   * the breaker; a save that throws does. {@code FlakyOrders} is a repository whose database the
   * test can take down.
   *
   * <p>Task: write the save, through the breaker, as a function the test passes to {@code via}.
   *
   * <pre>
   *   // Nudge:    The breaker wraps the one step that talks to the outside world: the save.
   *   // Strategy: EitherPath.withCircuitBreaker(supplier, breaker), with the save inside the
   *   //           supplier.
   *   // Spoiler:  order -&gt; EitherPath.withCircuitBreaker(() -&gt; orders.save(order), breaker)
   * </pre>
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

    // TODO: Replace answerRequired() with a function that saves an order through the breaker
    Function<Order, EitherPath<OrderError, Order>> protectedSave = answerRequired();

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

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 6: Exercise 3's step with ForPath
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 6: Exercise 3's step with ForPath
   *
   * <p>{@link ForPath} names each value as it goes: {@code from} for a step that returns a path,
   * {@code let} for a plain value, and {@code yield} for the result, given every value so far. A
   * later {@code from} receives a tuple of the values so far, so {@code t._2()} is the second.
   * {@code ForPath.from(path)} starts the comprehension from an {@code EitherPath}.
   *
   * <p>Task: mark the found order paid and save it, as in Exercise 3, as a comprehension.
   *
   * <pre>
   *   // Nudge:    Three values: the order found, the paid order, and the saved order.
   *   // Strategy: from the found path, let the paid order, from the save of t._2(), and yield
   *   //           the saved order.
   *   // Spoiler:  ForPath.from(found)
   *   //               .let(order -&gt; OrderFocus.status().set(OrderStatus.PAID, order))
   *   //               .from(t -&gt; orders.save(t._2()))
   *   //               .yield((order, paid, stored) -&gt; stored)
   * </pre>
   */
  @Test
  @DisplayName("Exercise 6: Exercise 3's step with ForPath")
  void exercise6_forPathSpelling() {
    OrderRepository orders = orders();
    EitherPath<OrderError, Order> found =
        orders.find(PLACED).toEitherPath(OrderError.notFound(PLACED));

    // TODO: Replace answerRequired() with a ForPath comprehension over found
    EitherPath<OrderError, Order> saved = answerRequired();

    assertThatEither(saved.run())
        .isRight()
        .hasRightSatisfying(order -> assertThat(order.status()).isEqualTo(OrderStatus.PAID));
    assertThat(stored(orders, PLACED).status()).isEqualTo(OrderStatus.PAID);
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 7: Recover a missing order, pass a refusal on
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 7: Recover a missing order, pass a refusal on
   *
   * <p>{@code recoverWith} gives each error a path of its own. {@code OrderError} is sealed, so a
   * {@code switch} over it covers every kind of error, and the compiler checks that none is left
   * out. A {@code NotFound} becomes a new order with the id that was asked for, built with {@link
   * Path#right}; any other error goes back on the {@code Left}, with {@link Path#left}.
   *
   * <p>Task: write the recovery once, as a function; the test applies it to a missing order and to
   * a refused save.
   *
   * <pre>
   *   // Nudge:    One kind of error can be put right; the other cannot.
   *   // Strategy: a switch with a record pattern for OrderError.NotFound, giving Path.right, and a
   *   //           case for AlreadyShipped, giving Path.left.
   *   // Spoiler:  error -&gt; switch (error) {
   *   //             case OrderError.NotFound(UUID id) -&gt; Path.right(order(id, OrderStatus.NEW));
   *   //             case OrderError.AlreadyShipped shipped -&gt; Path.left(shipped);
   *   //           }
   * </pre>
   */
  @Test
  @DisplayName("Exercise 7: Recover a missing order, pass a refusal on")
  void exercise7_recovery() {
    OrderRepository orders = orders();
    EitherPath<OrderError, Order> missing =
        orders.find(UNKNOWN).toEitherPath(OrderError.notFound(UNKNOWN));
    EitherPath<OrderError, Order> refused =
        orders.find(SHIPPED).toEitherPath(OrderError.notFound(SHIPPED)).via(orders::save);

    // TODO: Replace answerRequired() with the recovery
    Function<OrderError, EitherPath<OrderError, Order>> startAfresh = answerRequired();

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

  /*
   * Congratulations! You've completed the Capstone: One Line, Six Layers Grows Up
   *
   * You now understand:
   *   ✓ How a traversal changes every line of an order at once, inside a path
   *   ✓ How map and via split a step into the parts that cannot fail and the part that can
   *   ✓ How a circuit breaker protects a step of an eager EitherPath
   *   ✓ How ForPath names the values of a chain
   *   ✓ How a switch over a sealed error recovers one error and passes the others on
   *
   * Next: the Effect API journey for every Path type and the resilience methods, and the One
   * Line, Six Layers page in the Foundations chapter.
   */
}
