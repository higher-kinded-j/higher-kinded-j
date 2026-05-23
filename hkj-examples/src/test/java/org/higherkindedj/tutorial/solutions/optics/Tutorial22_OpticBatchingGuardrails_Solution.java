// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

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
 * Solution for Tutorial 22: Plan introspection and guardrails for optic batching.
 *
 * <p>The exercise file uses {@code answerRequired()} placeholders; this solution shows the working
 * code and, per the teaching-solution format, the reasoning that picks one form over the
 * alternatives.
 */
@DisplayName("Tutorial Solution 22: Plan Introspection and Guardrails")
public class Tutorial22_OpticBatchingGuardrails_Solution {

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
     * Why this is idiomatic: {@link Plans#preflight} walks the program offline, recording every
     * round's keyset. For a program whose combine logic tolerates {@code null} (a {@code String}
     * concat survives null) the walk goes all the way to {@code Done} and {@link Plan#truncated()}
     * is {@code false}.
     *
     * <p>Alternative: run with a no-op resolver and read {@code RunResult.fetchedBatches()}. Same
     * keysets, but you now have a fake side-effect path you have to maintain.
     *
     * <p>Common wrong attempt: assume preflight always walks past round 1. It only does for
     * programs whose combine logic accepts {@code null} stub values; see exercise 2 for the common
     * case where the walk truncates.
     */
    @Test
    @DisplayName("Exercise 1: a null-tolerant program preflights to all rounds")
    void exercise1_preflightShowsKeyset() {
      Fetch<Integer, String, String> a = Fetch.fetch(1);
      Fetch<Integer, String, String> b = Fetch.fetch(2);
      Fetch<Integer, String, String> program =
          Fetch.ap(a.map(left -> right -> "(" + left + "," + right + ")"), b);

      Plan<Integer> plan = Plans.preflight(program);

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
     * Why this is idiomatic: {@link Plan#truncated()} is the honest answer that the walk could not
     * see further rounds. For a traversal built with {@code FocusPaths.listElements}, the internal
     * {@code List.of(first)} rejects {@code null}, so the walk halts at the first combine; for a
     * {@code flatMap} dependency the continuation needs a real value to decide what to fetch next.
     * Either way, round 1's keyset is reliable; further rounds are not.
     *
     * <p>Alternative: invent a "best guess" stub value. That would silently mislead callers; we
     * refuse to.
     *
     * <p>Common wrong attempt: treat {@code truncated} as a bug to fix. It is the contract; the
     * <em>round 1 keyset</em> is what an auditor or budget guard cares about, and it is always
     * accurate.
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

      Plan<Integer> plan = Plans.preflight(FETCH.narrow(program));

      assertThat(plan.rounds()).isEqualTo(1);
      assertThat(plan.fetchedBatches().get(0)).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
      assertThat(plan.truncated()).isTrue();
    }
  }

  @Nested
  @DisplayName("Part 3: maxKeysPerRound refuses oversized rounds")
  class MaxKeysGuard {

    /**
     * Why this is idiomatic: the guard inspects the keyset at the round boundary, before the
     * resolver runs. A refused round throws {@link GuardViolationException} and the resolver is
     * never called. That is the contract you want for a runaway-optic safety rail.
     *
     * <p>Alternative: pre-check the input list size in calling code. Brittle, because the optic's
     * dedup means the keyset can be smaller than the input list; you would either over-refuse or
     * under-refuse. The guard sees the real keyset.
     *
     * <p>Common wrong attempt: catch the exception, log it, and run anyway with a smaller list. The
     * guard exists to stop a runaway batch; treat the exception as fatal and either reduce the
     * source list upstream or raise the budget deliberately.
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
      try {
        Guards.runCached(FETCH.narrow(program), doubler(calls), Guards.maxKeysPerRound(3));
        throw new AssertionError("expected GuardViolationException");
      } catch (GuardViolationException e) {
        assertThat(e.roundIndex()).isZero();
        assertThat(e.pendingKeys()).hasSize(5);
        assertThat(calls.get()).as("resolver must not be called when the guard refuses").isZero();
      }
    }
  }

  @Nested
  @DisplayName("Part 4: audit records every keyset that runs")
  class AuditGuard {

    /**
     * Why this is idiomatic: {@link Guards#audit} is a pass-through guard; it never refuses, so it
     * composes inside a chain with enforcement guards without changing their behaviour. The sink
     * runs once per round with the exact keyset and the round index, which is what an auditor
     * wants.
     *
     * <p>Alternative: wrap the resolver in a logging decorator. Same data, but the resolver may not
     * even be called (for an all-cached round, or for a refused round), and the audit is about the
     * planned dispatch, not the executed one.
     *
     * <p>Common wrong attempt: store the audit in a shared mutable field across runs. Use a per-run
     * sink instead so concurrent runs do not interleave.
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
      Guard<Integer> guard = Guards.audit((keys, round) -> seen.add(Set.copyOf(keys)));

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
     * Why this is idiomatic: {@link SafeFetch#runCachedWithGuard} composes the guard with the
     * railway runner so a refusal is a value, not a thrown exception. The caller pattern-matches on
     * {@code Either}, the same as for any other run-boundary failure.
     *
     * <p>Alternative: catch the exception at the call site. Works, but every caller now has its own
     * catch block; the railway form is one shape for every failure.
     *
     * <p>Common wrong attempt: assume the guard signals through the {@code RunResult}. It does not;
     * the guard either passes (you get a {@code RunResult}) or refuses (you get the exception). The
     * railway runner is what turns refusal into a value.
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

      Either<Throwable, Fetch.RunResult<Integer, List<Integer>>> outcome =
          SafeFetch.runCachedWithGuard(
              FETCH.narrow(program), doubler(new AtomicInteger()), Guards.maxKeysPerRound(3));

      assertThat(outcome.isLeft()).isTrue();
      assertThat(outcome.getLeft())
          .isInstanceOf(GuardViolationException.class)
          .hasMessageContaining("keysPerRound=5");
    }
  }
}
