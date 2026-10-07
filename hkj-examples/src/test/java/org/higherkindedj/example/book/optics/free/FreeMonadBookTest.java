// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.free;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the Free Monad DSL page makes about the programs it runs. */
@DisplayName("the Free Monad DSL page: programs described once, then run")
class FreeMonadBookTest {

  @Test
  @DisplayName("the direct interpreter runs the get, set and modify programs")
  void directInterpreterRunsTheFirstPrograms() {
    FreeMonadBook.FirstRuns runs = FreeMonadBook.firstPrograms();

    assertThat(runs.age()).isEqualTo(25);
    assertThat(runs.updated()).isEqualTo(new Person("Alice", 30, "ACTIVE"));
    assertThat(runs.modified()).isEqualTo(new Person("Alice", 26, "ACTIVE"));
  }

  @Test
  @DisplayName("the annual review raises Alice by 10% and promotes her past 100,000")
  void annualReviewPromotes() {
    assertThat(FreeMonadBook.annualReview())
        .isEqualTo(new Employee("Alice", 104_500, EmployeeStatus.SENIOR))
        .hasToString("Employee[name=Alice, salary=104500, status=SENIOR]");
  }

  @Test
  @DisplayName("every doubled score passes")
  void everyDoubledScorePasses() {
    assertThat(FreeMonadBook.doubleScores()).isTrue();
  }

  @Test
  @DisplayName("the logging interpreter records the read and both balance changes, in order")
  void loggingInterpreterRecordsTheTransfer() {
    FreeMonadBook.Audited audited = FreeMonadBook.auditedTransfer();

    // BigDecimal equality is scale-sensitive: the balances keep the two places they started with
    assertThat(audited.result().from().balance()).isEqualTo(new BigDecimal("900.00"));
    assertThat(audited.result().to().balance()).isEqualTo(new BigDecimal("600.00"));
    assertThat(audited.trail()).hasSize(3);
    assertThat(audited.trail().get(0)).startsWith("GET: ").endsWith(" -> 100.00");
    assertThat(audited.trail().get(1)).startsWith("MODIFY: ").endsWith(" from 1000.00 to 900.00");
    assertThat(audited.trail().get(2)).startsWith("MODIFY: ").endsWith(" from 500.00 to 600.00");
  }
}
