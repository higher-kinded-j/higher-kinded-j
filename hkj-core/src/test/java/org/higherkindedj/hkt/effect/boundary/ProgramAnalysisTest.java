// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.boundary;

import static org.assertj.core.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link ProgramAnalysis}. */
@DisplayName("ProgramAnalysis Tests")
class ProgramAnalysisTest {

  @Nested
  @DisplayName("Record Construction")
  class ConstructionTests {

    @Test
    @DisplayName("Should store all fields")
    void shouldStoreFields() {
      Set<String> effects = Set.of("StoreOp", "LoadOp");
      ProgramAnalysis analysis = new ProgramAnalysis(effects, 3, 1, 2);

      assertThat(analysis.effectsUsed()).containsExactlyInAnyOrder("StoreOp", "LoadOp");
      assertThat(analysis.totalInstructions()).isEqualTo(3);
      assertThat(analysis.recoveryPoints()).isEqualTo(1);
      assertThat(analysis.applicativeBlocks()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should create defensive copy of effects set")
    void shouldDefensivelyCopyEffects() {
      Set<String> mutable = new HashSet<>();
      mutable.add("Op1");
      ProgramAnalysis analysis = new ProgramAnalysis(mutable, 1, 0, 0);

      // Mutate original — should not affect the analysis
      mutable.add("Op2");
      assertThat(analysis.effectsUsed()).containsExactly("Op1");
    }

    @Test
    @DisplayName("Should return unmodifiable effects set")
    void shouldReturnUnmodifiableEffects() {
      ProgramAnalysis analysis = new ProgramAnalysis(Set.of("Op1"), 1, 0, 0);
      assertThatThrownBy(() -> analysis.effectsUsed().add("Op2"))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  @DisplayName("empty() Factory")
  class EmptyTests {

    @Test
    @DisplayName("Should create empty analysis with zero counts")
    void shouldCreateEmpty() {
      ProgramAnalysis empty = ProgramAnalysis.empty();

      assertThat(empty.effectsUsed()).isEmpty();
      assertThat(empty.totalInstructions()).isZero();
      assertThat(empty.recoveryPoints()).isZero();
      assertThat(empty.applicativeBlocks()).isZero();
    }
  }

  @Nested
  @DisplayName("equals/hashCode/toString")
  class EqualityTests {

    @Test
    @DisplayName("Should be equal for same values")
    void shouldBeEqualForSameValues() {
      ProgramAnalysis a = new ProgramAnalysis(Set.of("Op"), 2, 1, 0);
      ProgramAnalysis b = new ProgramAnalysis(Set.of("Op"), 2, 1, 0);

      assertThat(a).isEqualTo(b);
      assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("Should not be equal for different values")
    void shouldNotBeEqualForDifferentValues() {
      ProgramAnalysis a = new ProgramAnalysis(Set.of("Op1"), 2, 1, 0);
      ProgramAnalysis b = new ProgramAnalysis(Set.of("Op2"), 2, 1, 0);

      assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("Should have descriptive toString")
    void shouldHaveToString() {
      ProgramAnalysis analysis = new ProgramAnalysis(Set.of("Op"), 1, 0, 0);
      assertThat(analysis.toString()).contains("Op");
      assertThat(analysis.toString()).contains("1");
    }
  }
}
