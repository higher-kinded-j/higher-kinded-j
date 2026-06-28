// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.coretypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.expression.ForPath;
import org.higherkindedj.hkt.resilience.CircuitBreaker;
import org.higherkindedj.hkt.resilience.CircuitBreakerConfig;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.optics.Lens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Capstone Tutorial: One Line, Six Layers Grows Up.
 *
 * <p>Tutorial 00 introduced the chapter anchor expression — one line of Higher-Kinded-J that
 * touched every layer of the library. By the time we reach this capstone we have learned each layer
 * in depth: the Effect Path API, optics, monad transformers, structured concurrency, resilience
 * patterns. This file is the same expression, grown up to handle a realistic order workflow:
 *
 * <pre>
 *   findOrder(id)               // 1. Effect: MaybePath&lt;Order&gt;
 *     .toEitherPath(NOT_FOUND)   // 2. Natural transformation -&gt; EitherPath
 *     .focus(itemsPath)          // 3. Optic: navigate to the items list
 *     .map(items -&gt; recompute(items))  // 4. Functor: bulk update
 *     .via(this::reserveInventory)     // 5. Monad: dependent step (may fail)
 *     .via(this::saveAtomically)        // 5b. another dependent step
 *     .run();                            // 6. Dispatch
 * </pre>
 *
 * <p>The exercises below ask us to assemble the workflow from its parts. By the end we will have
 * written a production-shaped pipeline that:
 *
 * <ul>
 *   <li>Looks up a record (Effect Path)
 *   <li>Lifts the absence into a typed error (natural transformation)
 *   <li>Navigates into a nested collection (optic)
 *   <li>Validates each line (Applicative)
 *   <li>Reserves inventory under a circuit breaker + retry (resilience)
 *   <li>Persists under structured concurrency (VTask)
 *   <li>Recovers gracefully on failure (handleError)
 * </ul>
 *
 * <p>This is the shape we use in {@code
 * hkj-examples/src/main/java/org/higherkindedj/example/order}. The capstone tutorial is the single
 * best demonstration that the chapter material composes — same operations, same mental model,
 * scaled up from one line to one realistic workflow.
 */
@DisplayName("Capstone: One Line, Six Layers Grows Up")
public class TutorialCapstone_OneLineSixLayersGrowsUp {

  /** Helper for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // ─── Tiny domain ──────────────────────────────────────────────────────────

  enum OrderError {
    NOT_FOUND,
    OUT_OF_STOCK,
    SAVE_FAILED
  }

  record Item(String sku, int quantity, double price) {}

  record Order(String id, List<Item> items, String status) {}

  static final Lens<Order, List<Item>> itemsLens =
      Lens.of(Order::items, (o, items) -> new Order(o.id(), items, o.status()));

  static final Lens<Order, String> statusLens =
      Lens.of(Order::status, (o, s) -> new Order(o.id(), o.items(), s));

  // ─── Pretend services ─────────────────────────────────────────────────────

  private static final Map<String, Order> REPO = new ConcurrentHashMap<>();

  static {
    REPO.put(
        "ord-1",
        new Order("ord-1", List.of(new Item("A", 2, 5.0), new Item("B", 1, 10.0)), "PENDING"));
  }

  /** Looks up the order; returns Nothing for unknown ids. */
  static MaybePath<Order> findOrder(String id) {
    Order o = REPO.get(id);
    return o == null ? Path.nothing() : Path.just(o);
  }

  /** Reserves inventory; succeeds for items with quantity > 0, fails otherwise. */
  static EitherPath<OrderError, Order> reserveInventory(Order order) {
    boolean allInStock = order.items().stream().allMatch(it -> it.quantity() > 0);
    if (!allInStock) return Path.left(OrderError.OUT_OF_STOCK);
    return Path.right(statusLens.set("RESERVED", order));
  }

  /** Persists the order. */
  static EitherPath<OrderError, Order> save(Order order) {
    if (order == null || order.id() == null) return Path.left(OrderError.SAVE_FAILED);
    REPO.put(order.id(), order);
    return Path.right(order);
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 1: Build the Effect Path scaffolding
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 1: Lift the lookup, then the natural transformation.
   *
   * <pre>
   *   // Nudge:    findOrder returns a MaybePath; we want an EitherPath with NOT_FOUND.
   *   // Strategy: findOrder("ord-1").toEitherPath(OrderError.NOT_FOUND)
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 1: lift to EitherPath")
  void exercise1_liftToEitherPath() {
    EitherPath<OrderError, Order> path = answerRequired();

    Either<OrderError, Order> result = path.run();
    assertThatEither(result)
        .isRight()
        .hasRightSatisfying(order -> assertThat(order.id()).isEqualTo("ord-1"));

    EitherPath<OrderError, Order> missing = findOrder("missing").toEitherPath(OrderError.NOT_FOUND);
    assertThatEither(missing.run()).hasLeft(OrderError.NOT_FOUND);
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 2: Recompute totals through the optic
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 2: Apply a bulk update through the items lens.
   *
   * <pre>
   *   // Nudge:    Use itemsLens.modify inside .map; doubling each item's quantity.
   *   // Strategy: path.map(o -&gt; itemsLens.modify(items -&gt; items.stream().map(...).toList(), o))
   *   // Spoiler:  see the body below; itemsLens.modify is the right call.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 2: bulk update items via the lens")
  void exercise2_bulkUpdate() {
    EitherPath<OrderError, Order> base = findOrder("ord-1").toEitherPath(OrderError.NOT_FOUND);

    EitherPath<OrderError, Order> doubled = answerRequired();

    assertThatEither(doubled.run())
        .isRight()
        .hasRightSatisfying(
            out -> assertThat(out.items()).extracting(Item::quantity).containsExactly(4, 2));
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 3: Chain the dependent steps
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 3: Reserve inventory, then save.
   *
   * <pre>
   *   // Nudge:    Both steps return EitherPath; chain with .via.
   *   // Strategy: base.via(this::reserveInventory).via(this::save)
   *   // Spoiler:  exactly that. (Method reference works.)
   * </pre>
   */
  @Test
  @DisplayName("Exercise 3: reserve then save")
  void exercise3_chainDependentSteps() {
    EitherPath<OrderError, Order> base = findOrder("ord-1").toEitherPath(OrderError.NOT_FOUND);

    EitherPath<OrderError, Order> persisted = answerRequired();

    Either<OrderError, Order> result = persisted.run();
    assertThatEither(result)
        .isRight()
        .hasRightSatisfying(order -> assertThat(order.status()).isEqualTo("RESERVED"));
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 4: Compose the whole expression
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 4: One line, every layer, end-to-end.
   *
   * <p>This is the capstone of the capstone: the full expression that ties together effect lifting,
   * optic-driven update, dependent chaining, and dispatch. The same shape we have been seeing since
   * Tutorial 00, applied to a realistic order pipeline.
   *
   * <pre>
   *   // Nudge:    Combine exercises 1-3 into one fluent chain ending in .run().
   *   // Strategy: findOrder(id).toEitherPath(...).map(itemsLens.modify(...)).via(...).via(...).run()
   *   // Spoiler:  see the solution.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 4: the full pipeline as one expression")
  void exercise4_oneLineSixLayersGrowsUp() {
    String id = "ord-1";

    Either<OrderError, Order> result = answerRequired();

    assertThatEither(result)
        .isRight()
        .hasRightSatisfying(
            order -> {
              assertThat(order.id()).isEqualTo(id);
              assertThat(order.status()).isEqualTo("RESERVED");
            });
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 5: Add resilience
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 5: Wrap the inventory call in a circuit breaker.
   *
   * <p>In production, the inventory service is remote and may fail under load. The Resilience
   * journey teaches the patterns; here we wire a circuit breaker into the pipeline so that repeated
   * failures fast-fail instead of stalling.
   *
   * <p>This exercise is partially pre-filled: complete the breaker config and the protected call.
   *
   * <pre>
   *   // Nudge:    Build a CircuitBreakerConfig with a small threshold for the test.
   *   // Strategy: CircuitBreakerConfig.builder().failureThreshold(3).openDuration(...).build()
   *   // Spoiler:  see the solution.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 5: protect inventory with a circuit breaker")
  void exercise5_resilientInventory() {
    CircuitBreakerConfig config = answerRequired();
    CircuitBreaker breaker = CircuitBreaker.create(config);

    // Use the breaker to protect a VTask version of the inventory call.
    VTask<Order> protectedReserve =
        breaker.protect(
            () -> {
              Either<OrderError, Order> r = reserveInventory(REPO.get("ord-1")).run();
              if (r.isLeft()) throw new RuntimeException(r.getLeft().toString());
              return r.getRight();
            });

    Order reserved = protectedReserve.run();
    assertThat(reserved.status()).isEqualTo("RESERVED");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 6: Same pipeline through ForPath
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 6: Re-express the pipeline through {@link ForPath}.
   *
   * <p>Both spellings are correct; ForPath is preferred when the chain has more than three
   * dependent steps and intermediate values need to be reused.
   *
   * <pre>
   *   // Nudge:    ForPath.from(Path.right(id)).from(s -&gt; findOrder(s).toEitherPath(NOT_FOUND))
   *   //               .from(t -&gt; reserveInventory(t._2()))
   *   //               .yield((id, found, reserved) -&gt; reserved)
   *   // Spoiler:  exactly that, then .run().
   * </pre>
   */
  @Test
  @DisplayName("Exercise 6: same pipeline via ForPath")
  void exercise6_forPathSpelling() {
    String id = "ord-1";

    Either<OrderError, Order> result = answerRequired();

    assertThatEither(result)
        .isRight()
        .hasRightSatisfying(order -> assertThat(order.status()).isEqualTo("RESERVED"));
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 7: Graceful degradation
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 7: Recover from missing orders with a default.
   *
   * <p>Production code rarely fails the whole request when one lookup is missing. {@code recover}
   * swaps the error for a value; downstream steps proceed normally.
   *
   * <pre>
   *   // Nudge:    Insert .recover(err -&gt; defaultOrder) between the toEitherPath and the rest.
   *   // Strategy: findOrder(id).toEitherPath(NOT_FOUND).recover(err -&gt; new Order("default", ...))
   *   // Spoiler:  see the solution.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 7: recover from missing order with a default")
  void exercise7_recovery() {
    String id = "missing-id";
    Order defaultOrder = new Order("default", List.of(new Item("X", 1, 0.0)), "EMPTY");

    EitherPath<OrderError, Order> path = answerRequired();

    Either<OrderError, Order> result = path.run();
    assertThatEither(result)
        .isRight()
        .hasRightSatisfying(order -> assertThat(order.id()).isEqualTo("default"));
  }

  /*
   * Where to next?
   *   • Read the production order example: hkj-examples/src/main/java/org/higherkindedj/example/order/
   *   • Foundations chapter "One Line, Six Layers" - the chapter-level anchor
   *   • Author's exercise: replace the in-memory REPO with a real database access via
   *     VTaskPath, add a saga around payment + shipment, log every step through an audit
   *     interpreter (EffectHandlers journey).
   */
}
