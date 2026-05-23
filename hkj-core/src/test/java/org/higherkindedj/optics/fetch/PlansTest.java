// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Plans.preflight folds a program into its keyset plan without I/O")
class PlansTest {

  @Nested
  @DisplayName("Done programs")
  class DoneShape {

    @Test
    @DisplayName("a Done program preflights to the empty plan")
    void donePreflightsEmpty() {
      Plan<Integer> plan = Plans.preflight(Fetch.<Integer, String, String>done("hello"));
      assertThat(plan.rounds()).isZero();
      assertThat(plan.fetchedBatches()).isEmpty();
      assertThat(plan.totalKeyCount()).isZero();
      assertThat(plan.truncated()).isFalse();
    }

    @Test
    @DisplayName("Plan.empty() and a preflight of Done agree")
    void emptyMatchesDone() {
      assertThat(Plans.preflight(Fetch.<Integer, String, String>done("x")))
          .isEqualTo(Plan.<Integer>empty());
    }
  }

  @Nested
  @DisplayName("Applicative programs")
  class ApplicativeShape {

    @Test
    @DisplayName("a single fetch surfaces its key as one round")
    void singleFetchOneRound() {
      Plan<Integer> plan = Plans.preflight(Fetch.<Integer, String>fetch(42));
      assertThat(plan.rounds()).isEqualTo(1);
      assertThat(plan.fetchedBatches()).containsExactly(Set.of(42));
      assertThat(plan.totalKeyCount()).isEqualTo(1);
      assertThat(plan.truncated()).isFalse();
    }

    @Test
    @DisplayName("ap of independent fetches merges into one round")
    void apMergesIntoOneRound() {
      Fetch<Integer, String, String> a = Fetch.fetch(1);
      Fetch<Integer, String, String> b = Fetch.fetch(2);
      Fetch<Integer, String, String> combined = Fetch.ap(a.map(left -> right -> left + right), b);

      Plan<Integer> plan = Plans.preflight(combined);

      assertThat(plan.rounds()).isEqualTo(1);
      assertThat(plan.fetchedBatches().get(0)).containsExactlyInAnyOrder(1, 2);
      assertThat(plan.totalKeyCount()).isEqualTo(2);
      assertThat(plan.truncated()).isFalse();
    }

    @Test
    @DisplayName("map preserves the keyset and adds no rounds")
    void mapIsTransparentToTheKeyset() {
      Fetch<Integer, String, Integer> program = Fetch.<Integer, String>fetch(7).map(String::length);

      Plan<Integer> plan = Plans.preflight(program);

      assertThat(plan.rounds()).isEqualTo(1);
      assertThat(plan.fetchedBatches()).containsExactly(Set.of(7));
    }
  }

  @Nested
  @DisplayName("Monadic dependencies")
  class FlatMapShape {

    @Test
    @DisplayName("a flatMap that inspects the value truncates the plan at the dependency")
    void flatMapTruncates() {
      Fetch<Integer, String, String> program =
          Fetch.<Integer, String>fetch(1).flatMap(value -> Fetch.fetch(value.length()));

      Plan<Integer> plan = Plans.preflight(program);

      assertThat(plan.rounds()).isEqualTo(1);
      assertThat(plan.fetchedBatches()).containsExactly(Set.of(1));
      assertThat(plan.truncated()).isTrue();
    }

    @Test
    @DisplayName("a flatMap that does not inspect the value still records the dependency boundary")
    void flatMapIgnoringValueWalks() {
      Fetch<Integer, String, String> program =
          Fetch.<Integer, String>fetch(1).flatMap(ignored -> Fetch.fetch(2));

      Plan<Integer> plan = Plans.preflight(program);

      // First round visible; second round depends on a value flatMap is given, which the stub
      // makes null. Since this flatMap discards the value, both rounds are observable.
      assertThat(plan.rounds()).isEqualTo(2);
      assertThat(plan.fetchedBatches()).containsExactly(Set.of(1), Set.of(2));
      assertThat(plan.truncated()).isFalse();
    }
  }

  @Nested
  @DisplayName("Input validation")
  class Validation {

    @Test
    @DisplayName("preflight rejects a null program")
    void preflightRejectsNull() {
      assertThatThrownBy(() -> Plans.preflight(null)).isInstanceOf(NullPointerException.class);
    }
  }
}
