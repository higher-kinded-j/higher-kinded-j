// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.coretypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;

import java.time.Duration;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Solution for TutorialCapstone OneLineSixLayersGrowsUp — teaching-solution format.
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
@DisplayName("Capstone Solution: One Line, Six Layers Grows Up")
public class TutorialCapstone_OneLineSixLayersGrowsUp_Solution {

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

  private static final Map<String, Order> REPO = new ConcurrentHashMap<>();

  private static final Order SEED_ORDER =
      new Order("ord-1", List.of(new Item("A", 2, 5.0), new Item("B", 1, 10.0)), "PENDING");

  @BeforeEach
  void resetRepo() {
    // Each exercise starts from the same seed; Exercise 4 mutates REPO via save(),
    // so without this reset the test order would matter.
    REPO.clear();
    REPO.put(SEED_ORDER.id(), SEED_ORDER);
  }

  static MaybePath<Order> findOrder(String id) {
    Order o = REPO.get(id);
    return o == null ? Path.nothing() : Path.just(o);
  }

  static EitherPath<OrderError, Order> reserveInventory(Order order) {
    boolean allInStock = order.items().stream().allMatch(it -> it.quantity() > 0);
    if (!allInStock) return Path.left(OrderError.OUT_OF_STOCK);
    return Path.right(statusLens.set("RESERVED", order));
  }

  static EitherPath<OrderError, Order> save(Order order) {
    if (order == null || order.id() == null) return Path.left(OrderError.SAVE_FAILED);
    REPO.put(order.id(), order);
    return Path.right(order);
  }

  // ─── Exercise 1 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code toEitherPath(error)} is the named natural transformation from
   * {@code MaybePath} to {@code EitherPath} — "absent becomes this error". One word at the seam,
   * and the rest of the pipeline can stop thinking about {@code Maybe}.
   *
   * <p>Alternative: pattern-match on the {@code Maybe} and rebuild an {@code Either} by hand
   * ({@code maybe.fold(() -> Path.left(NOT_FOUND), Path::right)}). Same answer, three more tokens,
   * and a place to typo the error value.
   *
   * <p>Common wrong attempt: keeping the {@code MaybePath} and reaching for {@code map} when the
   * next step needs to fail with a typed error. {@code MaybePath} has no error channel, so the
   * NOT_FOUND signal vanishes — lift first, then chain.
   */
  @Test
  @DisplayName("Exercise 1: lift to EitherPath")
  void exercise1_liftToEitherPath() {
    EitherPath<OrderError, Order> path = findOrder("ord-1").toEitherPath(OrderError.NOT_FOUND);

    Either<OrderError, Order> result = path.run();
    assertThatEither(result)
        .isRight()
        .hasRightSatisfying(order -> assertThat(order.id()).isEqualTo("ord-1"));
  }

  // ─── Exercise 2 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code map} on the path lets us reach inside the success channel without
   * unwrapping; {@code itemsLens.modify} reaches inside the {@code Order} without rebuilding it.
   * Two focused operations compose cleanly — the path knows about success/failure, the lens knows
   * about the field.
   *
   * <p>Alternative: {@code base.via(o -> Path.right(itemsLens.modify(..., o)))}. Same result;
   * {@code via} signals "this step might fail" and we know it cannot, so {@code map} is the smaller
   * hammer.
   *
   * <p>Common wrong attempt: rebuilding the {@code Order} by hand inside {@code map} ({@code new
   * Order(o.id(), newItems, o.status())}). It works once, but the moment a field is added the
   * compiler will not catch the omission — the lens does.
   */
  @Test
  @DisplayName("Exercise 2: bulk update items via the lens")
  void exercise2_bulkUpdate() {
    EitherPath<OrderError, Order> base = findOrder("ord-1").toEitherPath(OrderError.NOT_FOUND);

    EitherPath<OrderError, Order> doubled =
        base.map(
            o ->
                itemsLens.modify(
                    items ->
                        items.stream()
                            .map(it -> new Item(it.sku(), it.quantity() * 2, it.price()))
                            .toList(),
                    o));

    assertThatEither(doubled.run())
        .isRight()
        .hasRightSatisfying(
            out -> assertThat(out.items()).extracting(Item::quantity).containsExactly(4, 2));
  }

  // ─── Exercise 3 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code via} is the path's {@code flatMap} — each step returns a fresh
   * {@code EitherPath}, and a {@code Left} short-circuits the rest. The two business rules read
   * top-to-bottom in the order they happen.
   *
   * <p>Alternative: nest the calls ({@code save(reserveInventory(o).run().getRight())}). That works
   * only if neither step ever fails; the moment {@code reserveInventory} returns {@code Left},
   * calling {@code getRight} throws.
   *
   * <p>Common wrong attempt: chaining with {@code map} instead of {@code via}. {@code map} expects
   * {@code A -> B}; our step returns {@code A -> EitherPath<E, B>}, which would give {@code
   * EitherPath<E, EitherPath<E, B>>} — a nested path that never short-circuits.
   */
  @Test
  @DisplayName("Exercise 3: reserve then save")
  void exercise3_chainDependentSteps() {
    EitherPath<OrderError, Order> base = findOrder("ord-1").toEitherPath(OrderError.NOT_FOUND);

    EitherPath<OrderError, Order> persisted =
        base.via(TutorialCapstone_OneLineSixLayersGrowsUp_Solution::reserveInventory)
            .via(TutorialCapstone_OneLineSixLayersGrowsUp_Solution::save);

    Either<OrderError, Order> result = persisted.run();
    assertThatEither(result)
        .isRight()
        .hasRightSatisfying(order -> assertThat(order.status()).isEqualTo("RESERVED"));
  }

  // ─── Exercise 4 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: this is the chapter's anchor expression — six concerns (lookup, lifting,
   * reservation, persistence, error short-circuit, execution) read as a single top-to-bottom
   * pipeline. {@code run} is the only place anything actually executes.
   *
   * <p>Alternative: extract intermediate variables ({@code var found = ...; var lifted = ...;}).
   * Same shape, easier to debug, recommended once the pipeline grows beyond half a dozen steps or
   * any step needs its own assertion.
   *
   * <p>Common wrong attempt: calling {@code run} after every {@code via} to "see what happens".
   * That collapses the path into a value and discards the typed error channel for the next step;
   * keep the path lazy until the very end.
   */
  @Test
  @DisplayName("Exercise 4: the full pipeline as one expression")
  void exercise4_oneLineSixLayersGrowsUp() {
    String id = "ord-1";

    Either<OrderError, Order> result =
        findOrder(id)
            .toEitherPath(OrderError.NOT_FOUND)
            .map(
                o ->
                    itemsLens.modify(
                        items ->
                            items.stream()
                                .map(it -> new Item(it.sku(), it.quantity() * 2, it.price()))
                                .toList(),
                        o))
            .via(TutorialCapstone_OneLineSixLayersGrowsUp_Solution::reserveInventory)
            .via(TutorialCapstone_OneLineSixLayersGrowsUp_Solution::save)
            .run();

    assertThatEither(result)
        .isRight()
        .hasRightSatisfying(
            order -> {
              assertThat(order.id()).isEqualTo(id);
              assertThat(order.status()).isEqualTo("RESERVED");
            });
  }

  // ─── Exercise 5 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code breaker.protect(supplier)} returns a {@code VTask} that, when
   * run, asks the breaker first. The protected work and the breaker policy stay separate — the
   * supplier knows nothing about thresholds, the breaker knows nothing about orders.
   *
   * <p>Alternative: thread the breaker into the path itself with a custom {@code via} step. Works
   * for an EitherPath-shaped breaker, but {@code VTask} integration is the standard wrapper for any
   * {@code Supplier<T>}; reach for the path-level integration only when the breaker truly needs the
   * typed error channel.
   *
   * <p>Common wrong attempt: invoking the supplier eagerly and passing the result to {@code
   * protect} ({@code breaker.protect(reserveInventory(...).run().getRight())}). The breaker now has
   * a value, not a unit of work — it cannot retry, count failures, or open.
   */
  @Test
  @DisplayName("Exercise 5: protect inventory with a circuit breaker")
  void exercise5_resilientInventory() {
    CircuitBreakerConfig config =
        CircuitBreakerConfig.builder()
            .failureThreshold(3)
            .openDuration(Duration.ofMillis(100))
            .build();
    CircuitBreaker breaker = CircuitBreaker.create(config);

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

  // ─── Exercise 6 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code ForPath} is the comprehension spelling of the same monad. Each
   * {@code from} binds a name visible to later steps, and {@code yield} builds the result from any
   * prior binding — useful when a later step needs an earlier value, not just the immediate
   * predecessor.
   *
   * <p>Alternative: the {@code via} chain from Exercise 4. Both spellings are exact equivalents;
   * pick the comprehension when you need the bindings, the chain when each step only consumes its
   * predecessor.
   *
   * <p>Common wrong attempt: forgetting that {@code yield} receives every binding in order. Trying
   * to take {@code (reserved)} alone fails to compile — the lambda arity must match the number of
   * {@code from} steps.
   */
  @Test
  @DisplayName("Exercise 6: same pipeline via ForPath")
  void exercise6_forPathSpelling() {
    String id = "ord-1";

    Either<OrderError, Order> result =
        ForPath.from(Path.<OrderError, String>right(id))
            .from(s -> findOrder(s).toEitherPath(OrderError.NOT_FOUND))
            .from(t -> reserveInventory(t._2()))
            .yield((idIn, found, reserved) -> reserved)
            .run();

    assertThatEither(result)
        .isRight()
        .hasRightSatisfying(order -> assertThat(order.status()).isEqualTo("RESERVED"));
  }

  // ─── Exercise 7 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code recover} is the path's exception handler — given the typed error,
   * return a fresh success. The pipeline downstream never sees the failure; the recovery value is
   * just data.
   *
   * <p>Alternative: {@code path.fold(err -> defaultOrder, Function.identity())} after running. Same
   * outcome; collapses the path early and loses the option to chain further steps after recovery.
   *
   * <p>Common wrong attempt: catching exceptions thrown from {@code run}. The path never throws for
   * business errors — it returns {@code Left(err)}. Wrapping {@code run} in a {@code try} misses
   * every error the typed channel was designed to surface.
   */
  @Test
  @DisplayName("Exercise 7: recover from missing order with a default")
  void exercise7_recovery() {
    String id = "missing-id";
    Order defaultOrder = new Order("default", List.of(new Item("X", 1, 0.0)), "EMPTY");

    EitherPath<OrderError, Order> path =
        findOrder(id).toEitherPath(OrderError.NOT_FOUND).recover(err -> defaultOrder);

    Either<OrderError, Order> result = path.run();
    assertThatEither(result)
        .isRight()
        .hasRightSatisfying(order -> assertThat(order.id()).isEqualTo("default"));
  }
}
