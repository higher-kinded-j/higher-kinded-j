// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.optics.fetch.FetchKindHelper.FETCH;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.fetch.Fetch;
import org.higherkindedj.optics.fetch.FetchApplicative;
import org.higherkindedj.optics.fetch.Guard;
import org.higherkindedj.optics.fetch.GuardViolationException;
import org.higherkindedj.optics.fetch.Guards;
import org.higherkindedj.optics.fetch.Plan;
import org.higherkindedj.optics.fetch.Plans;
import org.higherkindedj.optics.fetch.SafeFetch;
import org.higherkindedj.optics.focus.FocusPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 22: Plan introspection and guardrails for optic batching.
 *
 * <p>Pain to Promise. Optic-driven batching (Tutorial 21) lets the optic dispatch one batched call.
 * But you may want to know <em>what</em> it would dispatch before it runs (audit), refuse to run if
 * the batch is too large (safety rails), or assert what the optic would do in a test without
 * standing up a fake backend. {@link Plans} and {@link Guards} are those tools.
 *
 * <pre>
 *   // What would this program dispatch?  Zero I/O.
 *   Plan&lt;K&gt; plan = Plans.preflight(program);
 *
 *   // Refuse if a round would dispatch more than 500 keys.
 *   Guards.runCached(program, resolver, Guards.maxKeysPerRound(500));
 *
 *   // Capture refusal as Either.left rather than as a thrown exception.
 *   Either&lt;Throwable, RunResult&lt;K, A&gt;&gt; outcome =
 *       SafeFetch.runCachedWithGuard(program, resolver, guard);
 * </pre>
 *
 * <p>Key concepts:
 *
 * <ul>
 *   <li>{@link Plans#preflight} folds a {@link Fetch} program into its keyset plan, without I/O,
 *       returning a {@link Plan} that lists every round's keys in dispatch order;
 *   <li>{@link Guards#maxKeysPerRound}, {@link Guards#maxRounds}, {@link Guards#maxBackendCalls},
 *       {@link Guards#audit} are the standard guards; compose with {@link Guard#and(Guard)};
 *   <li>{@link Guards#runCached} / {@link Guards#runAsync} are the guarded runners; a refused round
 *       aborts the run with {@link GuardViolationException};
 *   <li>{@link SafeFetch#runCachedWithGuard} / {@link SafeFetch#runAsyncWithGuard} are the railway
 *       variants: a guard refusal becomes {@code Either.left} on the value channel.
 * </ul>
 *
 * <p>Limits, stated up front: a {@code flatMap} data dependency makes later rounds value-dependent
 * and preflight stops at the dependency ({@link Plan#truncated()} becomes {@code true}); guards see
 * uncached keys only (the dispatch budget reflects the work the guard could prevent).
 *
 * <p>Prerequisites: Complete Tutorial 21 (Optic-Driven Request Batching) before this one.
 *
 * <p>Estimated time: ~12 minutes.
 *
 * <p>Replace each {@code answerRequired()} placeholder with the correct code to make the tests
 * pass.
 */
@DisplayName("Tutorial 22: Plan Introspection and Guardrails")
public class Tutorial22_OpticBatchingGuardrails {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required - replace answerRequired() with your solution");
  }

  private static Function<Set<Integer>, Map<Integer, Integer>> doubler(AtomicInteger calls) {
    return ids -> {
      calls.incrementAndGet();
      Map<Integer, Integer> out = new HashMap<>();
      ids.forEach(id -> out.put(id, id * 2));
      return out;
    };
  }

  @Nested
  @DisplayName("Part 1: preflight a null-tolerant applicative program")
  class Preflight {

    /**
     * Exercise 1: preflight a fully observable program.
     *
     * <p>Task: call {@link Plans#preflight} on the {@code Fetch.ap} program. Its combine is string
     * concatenation, which survives {@code null}, so the walk reaches {@code Done} and {@link
     * Plan#truncated()} is {@code false}.
     *
     * <pre>
     *   // Hint 1: {@code Plans.preflight(...)} takes the {@link Fetch} directly.
     *   // Hint 2: no resolver, no cache, no I/O.
     *   // Hint 3: the result is a {@link Plan} with a {@code fetchedBatches()} list and a
     *   //         {@code truncated()} flag.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 1: a null-tolerant program preflights to all rounds")
    void exercise1_preflightShowsKeyset() {
      Fetch<Integer, String, String> a = Fetch.fetch(1);
      Fetch<Integer, String, String> b = Fetch.fetch(2);
      Fetch<Integer, String, String> program =
          Fetch.ap(a.map(left -> right -> "(" + left + "," + right + ")"), b);

      // TODO: fold the program into its plan with zero I/O.
      Plan<Integer> plan = answerRequired();

      assertThat(plan.rounds()).isEqualTo(1);
      assertThat(plan.fetchedBatches().get(0)).containsExactlyInAnyOrder(1, 2);
      assertThat(plan.totalKeyCount()).isEqualTo(2);
      assertThat(plan.truncated()).isFalse();
    }
  }

  @Nested
  @DisplayName("Part 2: a richer program truncates after round 1")
  class FlatMapTruncates {

    /**
     * Exercise 2: preflight a traversal-built program.
     *
     * <p>Task: preflight a traversal built with {@code FocusPaths.listElements}. The keyset for
     * round 1 is reliable; the internal combine ({@code List.of(first)}) rejects {@code null}, so
     * the walk halts after round 1 and {@link Plan#truncated()} is {@code true}. Round 1 is what an
     * auditor or budget guard cares about; further rounds are not observable offline.
     *
     * <pre>
     *   // Hint 1: same call as exercise 1; only the program differs.
     *   // Hint 2: assert the {@code truncated()} flag is {@code true}.
     *   // Hint 3: round 1's keyset is still accurate.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 2: a traversal preflights to round 1 with truncated = true")
    void exercise2_traversalTruncates() {
      Traversal<List<Integer>, Integer> ids = FocusPaths.listElements();
      var program =
          ids.modifyF(
              id -> FETCH.widen(Fetch.<Integer, Integer>fetch(id)),
              List.of(1, 2, 3, 4, 5),
              FetchApplicative.<Integer, Integer>instance());

      // TODO: preflight the program.
      Plan<Integer> plan = answerRequired();

      assertThat(plan.rounds()).isEqualTo(1);
      assertThat(plan.fetchedBatches().get(0)).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
      assertThat(plan.truncated()).isTrue();
    }
  }

  @Nested
  @DisplayName("Part 3: maxKeysPerRound refuses oversized rounds")
  class MaxKeysGuard {

    /**
     * Exercise 3: refusal.
     *
     * <p>Task: run the program with a {@link Guards#maxKeysPerRound} budget that is too small to
     * fit the five-key round. The run must abort with {@link GuardViolationException} and the
     * resolver must not be called.
     *
     * <pre>
     *   // Hint 1: the budget is the keyset size limit, so anything below 5 will refuse.
     *   // Hint 2: use {@code Guards.runCached(program, resolver, guard)}.
     *   // Hint 3: catch {@code GuardViolationException} and check {@code calls.get()} is 0.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 3: a keyset over the budget refuses with GuardViolationException")
    void exercise3_maxKeysRefuses() {
      Traversal<List<Integer>, Integer> ids = FocusPaths.listElements();
      var program =
          ids.modifyF(
              id -> FETCH.widen(Fetch.<Integer, Integer>fetch(id)),
              List.of(1, 2, 3, 4, 5),
              FetchApplicative.<Integer, Integer>instance());

      var calls = new AtomicInteger();
      // TODO: build a guard that refuses rounds with more than 3 keys, then run with it.
      // The call should throw GuardViolationException; capture it into `caught`.
      GuardViolationException caught = answerRequired();

      assertThat(caught.roundIndex()).isZero();
      assertThat(caught.pendingKeys()).hasSize(5);
      assertThat(calls.get()).isZero();
    }
  }

  @Nested
  @DisplayName("Part 4: audit records every keyset that runs")
  class AuditGuard {

    /**
     * Exercise 4: audit.
     *
     * <p>Task: run the program with a {@link Guards#audit} guard that appends each round's keyset
     * to the {@code seen} list. The run completes normally; the audit captures one keyset.
     *
     * <pre>
     *   // Hint 1: {@code Guards.audit(sink)} takes a {@code BiConsumer<Set<K>, Integer>}.
     *   // Hint 2: copy the keys with {@code Set.copyOf} because the guard hands you a view.
     *   // Hint 3: audit guards never refuse; the run always completes.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 4: audit captures every dispatched round")
    void exercise4_auditCapturesDispatch() {
      Traversal<List<Integer>, Integer> ids = FocusPaths.listElements();
      var program =
          ids.modifyF(
              id -> FETCH.widen(Fetch.<Integer, Integer>fetch(id)),
              List.of(1, 2, 3),
              FetchApplicative.<Integer, Integer>instance());

      List<Set<Integer>> seen = new ArrayList<>();
      // TODO: build the audit guard and run with it.
      Guard<Integer> guard = answerRequired();

      Fetch.RunResult<Integer, List<Integer>> result =
          Guards.runCached(FETCH.narrow(program), doubler(new AtomicInteger()), guard);

      assertThat(result.value()).containsExactly(2, 4, 6);
      assertThat(seen).hasSize(1);
      assertThat(seen.get(0)).containsExactlyInAnyOrder(1, 2, 3);
    }
  }

  @Nested
  @DisplayName("Part 5: SafeFetch turns a guard refusal into Either.left")
  class RailwayGuard {

    /**
     * Exercise 5: railway guard.
     *
     * <p>Task: run the same oversize traversal through {@link SafeFetch#runCachedWithGuard}. The
     * outcome should be {@code Either.left} carrying the {@link GuardViolationException}.
     *
     * <pre>
     *   // Hint 1: same arguments as Guards.runCached, plus the railway wrapper.
     *   // Hint 2: the return type is {@code Either<Throwable, RunResult<K, A>>}.
     *   // Hint 3: assert {@code outcome.isLeft()} and the cause class.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 5: a refused round is captured as Either.left")
    void exercise5_safeFetchRailwayCapture() {
      Traversal<List<Integer>, Integer> ids = FocusPaths.listElements();
      var program =
          ids.modifyF(
              id -> FETCH.widen(Fetch.<Integer, Integer>fetch(id)),
              List.of(1, 2, 3, 4, 5),
              FetchApplicative.<Integer, Integer>instance());

      // TODO: run through SafeFetch with a maxKeysPerRound(3) guard.
      Either<Throwable, Fetch.RunResult<Integer, List<Integer>>> outcome = answerRequired();

      assertThat(outcome.isLeft()).isTrue();
      assertThat(outcome.getLeft())
          .isInstanceOf(GuardViolationException.class)
          .hasMessageContaining("keysPerRound=5");
    }
  }
}
