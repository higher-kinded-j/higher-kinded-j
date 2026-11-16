// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.coretypes;

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
 * Tutorial 00: One Line, Six Layers — The Whole Stack in a Single Expression.
 *
 * <p>Pain → Promise. Here is the kind of imperative Java that motivates this library:
 *
 * <pre>
 *   Order order = orders.findOrNull(id);                  // a null check
 *   if (order == null) {
 *     throw new OrderNotFoundException(id);
 *   }
 *   Customer customer =                                    // copy every record on the way down
 *       new Customer(order.customer().name(), new EmailAddress(newEmail));
 *   Order changed = new Order(order.id(), customer, order.lines(),
 *       order.placedAt(), order.currency(), order.status());
 *   try {
 *     orders.save(changed);                                // a save that can refuse
 *   } catch (AlreadyShippedException e) {
 *     // turn it back into a domain error somehow
 *   }
 * </pre>
 *
 * <p>The Higher-Kinded-J version is one expression, and each step of it belongs to a different
 * layer of the library:
 *
 * <pre>
 *   orders.find(id)                                  // 1. Effect type (MaybePath&lt;Order&gt;)
 *       .toEitherPath(OrderError.notFound(id))       // 2. Natural transformation
 *       .map(order -&gt; OrderFocus.customer()        // 4. Functor (map), applying
 *           .email().value().set(newEmail, order))   // 3. an optic three records deep
 *       .via(orders::save);                          // 5. Monad (via)
 *   // 6. The algebra: map and via are Functor's map and Monad's flatMap over Kind&lt;F, A&gt;
 * </pre>
 *
 * <p>Java idiom anchor. Each layer replaces one piece of the imperative version:
 *
 * <ul>
 *   <li>Layer 1 replaces the null check: absence is a value, a {@code MaybePath}.
 *   <li>Layer 2 replaces "turn this absence into that exception": the empty case gets a reason.
 *   <li>Layer 3 replaces the copy constructors: the optic rebuilds every record it passes through.
 *   <li>Layer 4 replaces unwrap and rewrap: {@code map} applies a step that cannot fail to the
 *       value inside the path.
 *   <li>Layer 5 replaces the try/catch around a step that can fail for a reason of its own.
 *   <li>Layer 6 is why the same operations serve any container, not only {@code EitherPath}.
 * </ul>
 *
 * <p>This is the chapter anchor and the setup check. We write, one layer at a time, the line that
 * the {@code One Line, Six Layers} page in the Foundations chapter takes apart: the one that
 * changes the email address on an order. The examples use the order service the book's Foundations,
 * Optics and Mapping chapters share: {@code Order}, {@code Customer}, {@code EmailAddress}, {@code
 * OrderRepository} and {@code OrderError}.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>An effect type holds a value that may be missing or may have failed, as a value.
 *   <li>A natural transformation turns one effect type into another and leaves the value alone.
 *   <li>An optic changes a value deep inside records without copying them by hand.
 *   <li>{@code map} runs a step that cannot fail; {@code via} runs one that can.
 *   <li>The same {@code map} and {@code flatMap} exist as type classes, for any container.
 * </ul>
 *
 * <p>Prerequisites: none. This is the first tutorial.
 *
 * <p>Hints. Each exercise's hints climb from a Nudge, which names the idea, to a Strategy, which
 * names the method, to a Spoiler, which is the code itself. Stop reading once you have enough.
 *
 * <p>Replace each {@code answerRequired()} with the working code; the tests go green one by one.
 */
@DisplayName("Tutorial 00: One Line, Six Layers")
public class Tutorial00_OneLineSixLayers {

  /** Helper for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // ─── The order service ────────────────────────────────────────────────────

  /** An order that has been placed and not yet shipped. */
  static final UUID PLACED = UUID.fromString("00000000-0000-0000-0000-000000000001");

  /** An order that has already shipped, which the repository refuses to save again. */
  static final UUID SHIPPED = UUID.fromString("00000000-0000-0000-0000-000000000002");

  /** An id no order has. */
  static final UUID UNKNOWN = UUID.fromString("00000000-0000-0000-0000-000000000009");

  /** The address every exercise changes Ada's email to. */
  static final String NEW_EMAIL = "ada@example.org";

  /** An order from Ada for one lamp, with the given id and status. */
  static Order order(UUID id, OrderStatus status) {
    return new Order(
        id,
        new Customer("Ada", new EmailAddress("ada@example.com")),
        List.of(new LineItem("LAMP", 1, new BigDecimal("40.00"))),
        Instant.parse("2026-10-01T09:00:00Z"),
        Currency.getInstance("GBP"),
        status);
  }

  /**
   * A repository holding the placed order and the shipped one. Each exercise takes a fresh one,
   * because {@code save} changes what it holds.
   */
  static OrderRepository orders() {
    return InMemoryOrderRepository.of(
        order(PLACED, OrderStatus.NEW), order(SHIPPED, OrderStatus.SHIPPED));
  }

  /** The email the repository now holds for the order, or "absent" when it holds no such order. */
  static String storedEmail(OrderRepository orders, UUID id) {
    return orders.find(id).map(order -> order.customer().email().value()).getOrElse("absent");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 0: Setup check
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 0: Setup check
   *
   * <p>This test does not call {@code answerRequired()}. It checks that the Effect Path API, the
   * generated {@code OrderFocus}, and the JUnit and AssertJ runtime are all on the classpath. If it
   * goes green, the rest of the chapter will work. If it fails to compile, see <a
   * href="../../../../../../../../hkj-book/src/tutorials/troubleshooting.md">Troubleshooting</a>.
   *
   * <p>Task: run the test. There is no code to write.
   */
  @Test
  @DisplayName("Exercise 0: Setup check")
  void exercise0_setupCheck() {
    MaybePath<Integer> sanity = Path.just(42);
    assertThat(sanity.getOrElse(-1)).isEqualTo(42);
    assertThat(OrderFocus.customer().email().value().get(order(PLACED, OrderStatus.NEW)))
        .isEqualTo("ada@example.com");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 1: Layer 1, the effect type
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 1: Layer 1, the effect type
   *
   * <p>{@code orders.find(id)} returns a {@link MaybePath}: an order, or nothing, as a value rather
   * than a {@code null}.
   *
   * <p>Task: take the placed order out of the path, with a stand-in for the case where it is empty.
   *
   * <pre>
   *   // Nudge:    The path holds an order or nothing; ask for the order, with a fallback.
   *   // Strategy: MaybePath's getOrElse takes the value to use when the path is empty.
   *   // Spoiler:  path.getOrElse(order(UNKNOWN, OrderStatus.NEW))
   * </pre>
   */
  @Test
  @DisplayName("Exercise 1: Layer 1, the effect type")
  void exercise1_effectType() {
    MaybePath<Order> path = orders().find(PLACED);

    // TODO: Replace answerRequired() with the order the path holds
    Order order = answerRequired();

    assertThat(order.id()).isEqualTo(PLACED);
    assertThat(order.customer().email().value()).isEqualTo("ada@example.com");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 2: Layer 2, the natural transformation
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 2: Layer 2, the natural transformation
   *
   * <p>A {@link MaybePath} says only that a value is missing; an {@link EitherPath} says why. The
   * bridge between them is a natural transformation: {@code Nothing} becomes a {@code Left} holding
   * the error we give it, and a present order becomes a {@code Right}, untouched. Build the error
   * with the factory {@code OrderError.notFound(id)}, which is typed as the whole {@code
   * OrderError}.
   *
   * <p>Task: look up the placed order and the unknown id, and give each lookup a reason for the
   * empty case.
   *
   * <pre>
   *   // Nudge:    The lookup can turn itself into a path that carries a reason.
   *   // Strategy: MaybePath's toEitherPath takes the error to use when the path is empty.
   *   // Spoiler:  orders.find(PLACED).toEitherPath(OrderError.notFound(PLACED))
   * </pre>
   */
  @Test
  @DisplayName("Exercise 2: Layer 2, the natural transformation")
  void exercise2_naturalTransformation() {
    OrderRepository orders = orders();

    // TODO: Replace both answerRequired() calls with a lookup that carries a reason
    EitherPath<OrderError, Order> present = answerRequired(); // PLACED
    EitherPath<OrderError, Order> absent = answerRequired(); // UNKNOWN

    assertThatEither(present.run())
        .isRight()
        .hasRightSatisfying(order -> assertThat(order.id()).isEqualTo(PLACED));
    assertThatEither(absent.run()).isLeft().hasLeft(OrderError.notFound(UNKNOWN));
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 3: Layer 3, the optic
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 3: Layer 3, the optic
   *
   * <p>An optic is a getter and a setter we can pass around. {@code OrderFocus}, generated by
   * {@code @GenerateFocus} on {@code Order}, gives one for every component: {@code
   * OrderFocus.customer().email().value()} reaches three records deep, to the text of Ada's email
   * address. Changing a value through the optic returns a new {@code Order} and leaves the old one
   * as it was.
   *
   * <p>Task: make a new order whose email is {@code NEW_EMAIL}, through the optic.
   *
   * <pre>
   *   // Nudge:    The optic both reads and writes; writing gives a new order.
   *   // Strategy: The optic's set takes the new value and the order to change.
   *   // Spoiler:  OrderFocus.customer().email().value().set(NEW_EMAIL, original)
   * </pre>
   */
  @Test
  @DisplayName("Exercise 3: Layer 3, the optic")
  void exercise3_opticSet() {
    Order original = order(PLACED, OrderStatus.NEW);

    // TODO: Replace answerRequired() with a new order that has the new email
    Order changed = answerRequired();

    assertThat(changed.customer().email().value()).isEqualTo(NEW_EMAIL);
    assertThat(changed.customer().name()).isEqualTo("Ada");
    assertThat(original.customer().email().value()).isEqualTo("ada@example.com"); // unchanged
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 4: Layer 4, Functor's map
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 4: Layer 4, Functor's map
   *
   * <p>{@code map} is the Functor operation: it changes the order inside an {@link EitherPath} with
   * a function that cannot fail, and passes a {@code Left} along untouched.
   *
   * <p>Task: change the email on the order inside the found path.
   *
   * <pre>
   *   // Nudge:    Changing the email cannot fail, so the path can apply it to the order inside.
   *   // Strategy: EitherPath's map takes a function from Order to Order: the optic from Exercise 3.
   *   // Spoiler:  found.map(order -&gt; OrderFocus.customer().email().value().set(NEW_EMAIL, order))
   * </pre>
   */
  @Test
  @DisplayName("Exercise 4: Layer 4, Functor's map")
  void exercise4_functorMap() {
    EitherPath<OrderError, Order> found =
        orders().find(PLACED).toEitherPath(OrderError.notFound(PLACED));

    // TODO: Replace answerRequired() with the found path, with the email changed inside it
    EitherPath<OrderError, Order> changed = answerRequired();

    assertThatEither(changed.run())
        .isRight()
        .hasRightSatisfying(
            order -> assertThat(order.customer().email().value()).isEqualTo(NEW_EMAIL));
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 5: Layer 5, Monad's via
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 5: Layer 5, Monad's via
   *
   * <p>{@code via} is the Monad operation, {@code flatMap} under the name the Effect Path API uses:
   * it runs a step that needs the order and returns a path of its own. Here that step is {@code
   * orders.save}, which refuses an order that has already shipped. When it refuses, the chain ends
   * with its {@code Left}.
   *
   * <p>Task: save each changed order, the placed one and the shipped one.
   *
   * <pre>
   *   // Nudge:    The save needs the order and can fail, so map is the wrong tool.
   *   // Strategy: EitherPath's via takes a function that returns the next path: the save's.
   *   // Spoiler:  changed.via(orders::save)
   * </pre>
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

    // TODO: Replace both answerRequired() calls with each changed path, then its save
    EitherPath<OrderError, Order> saved = answerRequired();
    EitherPath<OrderError, Order> refused = answerRequired();

    assertThatEither(saved.run())
        .isRight()
        .hasRightSatisfying(
            order -> assertThat(order.customer().email().value()).isEqualTo(NEW_EMAIL));
    assertThatEither(refused.run()).isLeft().hasLeft(OrderError.alreadyShipped(SHIPPED));
    assertThat(storedEmail(orders, PLACED)).isEqualTo(NEW_EMAIL);
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 6: Layer 6, the algebra
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 6: Layer 6, the algebra
   *
   * <p>{@code map} and {@code via} exist twice. On a Path they are methods of a concrete type, as
   * in the exercises so far. As type classes they are {@code Functor}'s {@code map} and {@code
   * Monad}'s {@code flatMap}, written once for any container. Tutorial 01 takes this level apart;
   * here is just enough to use it:
   *
   * <ul>
   *   <li>{@code Kind<EitherKind.Witness<OrderError>, Order>} is an {@code Either<OrderError,
   *       Order>} seen as "some container holding an {@code Order}". {@code EITHER.widen} turns an
   *       {@code Either} into one, and {@code EITHER.narrow} turns it back.
   *   <li>{@code Instances.monad(either())} is {@code Either}'s {@code Monad}: a value we pass,
   *       which knows how to {@code map} and {@code flatMap} that container.
   *   <li>The type class takes the container last: {@code monad.map(f, fa)}, not {@code fa.map(f)},
   *       and likewise {@code monad.flatMap(f, fa)}.
   * </ul>
   *
   * <p>A method written this way can serve any container with a {@code Monad}.
   *
   * <p>Task: change the email and save, for the placed order and the shipped one, with the {@code
   * Monad} rather than the Path. The answers must match the Path's.
   *
   * <pre>
   *   // Nudge:    The change is a map and the save a flatMap, now called on the Monad.
   *   // Strategy: monad.map(f, found), then monad.flatMap with a function that saves; the save
   *   //           returns an EitherPath, so run() gives its Either and EITHER.widen its Kind.
   *   // Spoiler:  monad.flatMap(order -&gt; EITHER.widen(orders.save(order).run()),
   *   //               monad.map(order -&gt; OrderFocus.customer().email().value()
   *   //                   .set(NEW_EMAIL, order), found))
   * </pre>
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

    // TODO: Replace both answerRequired() calls with the change and the save, on the Monad
    Kind<EitherKind.Witness<OrderError>, Order> saved = answerRequired(); // from found
    Kind<EitherKind.Witness<OrderError>, Order> refused = answerRequired(); // from foundShipped

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

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 7: The whole line
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 7: The whole line
   *
   * <p>One expression, and the six layers in it. {@code run()} at the end gives the {@code Either}
   * inside the path. The line changes the email on a placed order, ends with {@code NotFound} for
   * an id no order has, and with {@code AlreadyShipped} for an order the save refuses.
   *
   * <p>Task: write the whole line for each id, ending in {@code run()}.
   *
   * <pre>
   *   // Nudge:    Exercises 2, 4 and 5 make one chain.
   *   // Strategy: look up, toEitherPath, map with the optic, via the save, then run().
   *   // Spoiler:  orders.find(PLACED).toEitherPath(OrderError.notFound(PLACED))
   *   //               .map(order -&gt; OrderFocus.customer().email().value()
   *   //                   .set(NEW_EMAIL, order))
   *   //               .via(orders::save).run()
   * </pre>
   */
  @Test
  @DisplayName("Exercise 7: The whole line")
  void exercise7_oneLineSixLayers() {
    OrderRepository orders = orders();

    // TODO: Replace each answerRequired() with the whole line for its id
    Either<OrderError, Order> changed = answerRequired(); // PLACED
    Either<OrderError, Order> missing = answerRequired(); // UNKNOWN
    Either<OrderError, Order> refused = answerRequired(); // SHIPPED

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

  // ═════════════════════════════════════════════════════════════════════════
  // Diagnostic: focus narrows, set inside map keeps the order
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Diagnostic: focus narrows, set inside map keeps the order
   *
   * <p>The commented code below calls {@code .focus(OrderFocus.customer().email())} on an {@link
   * EitherPath}, hoping to change the email in place. But {@code EitherPath.focus} narrows the path
   * to the focused value, so the {@code Order} is no longer in scope: the result is an {@code
   * EitherPath<OrderError, EmailAddress>}, and {@code save} needs an {@code Order}. To change an
   * inner field and keep the outer record, call the optic's {@code set} inside a {@code map}; use
   * {@code focus} when narrowing is what we want.
   *
   * <p>Task: change the email so that the path still carries an {@code Order}, and the save accepts
   * it.
   *
   * <pre>
   *   // Nudge:    The fix keeps the Order in scope.
   *   // Strategy: map with the optic's set, as in Exercise 4, rather than focus.
   *   // Spoiler:  found.map(order -&gt; OrderFocus.customer().email().value().set(NEW_EMAIL, order))
   * </pre>
   */
  @Test
  @DisplayName("Diagnostic: focus narrows, set inside map keeps the order")
  void diagnostic_focusVsSet() {
    OrderRepository orders = orders();
    EitherPath<OrderError, Order> found =
        orders.find(PLACED).toEitherPath(OrderError.notFound(PLACED));

    // The intent below is wrong: we want to keep an Order, not an EmailAddress.
    // EitherPath<OrderError, EmailAddress> narrowed =
    //     found.focus(OrderFocus.customer().email()).map(email -> new EmailAddress(NEW_EMAIL));
    //
    // Calling .via(orders::save) on `narrowed` does not compile,
    // because save needs an Order, not an EmailAddress.

    // TODO: Replace answerRequired() with the found path, still carrying an Order, with the new
    //       email
    EitherPath<OrderError, Order> changed = answerRequired();

    assertThatEither(changed.via(orders::save).run())
        .isRight()
        .hasRightSatisfying(
            order -> assertThat(order.customer().email().value()).isEqualTo(NEW_EMAIL));

    // focus is the right tool when narrowing is the goal.
    EitherPath<OrderError, String> email = found.focus(OrderFocus.customer().email().value());
    assertThatEither(email.run()).isRight().hasRight("ada@example.com");
  }

  /*
   * Congratulations! You've completed Tutorial 00: One Line, Six Layers
   *
   * You now understand:
   *   ✓ How an effect type holds a value that may be missing, and a natural transformation gives
   *     the missing case a reason
   *   ✓ How an optic changes a value three records deep, and map applies it inside a path
   *   ✓ How via chains a step that can fail, and its failure ends the chain
   *   ✓ That map and via are Functor's map and Monad's flatMap, written once for any container
   *
   * Next: Tutorial 01: Kind Basics, and the One Line, Six Layers page in the Foundations chapter,
   * which takes the same line apart.
   */
}
