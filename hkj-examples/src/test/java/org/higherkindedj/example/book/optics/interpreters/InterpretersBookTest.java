// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.interpreters;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.higherkindedj.optics.free.ValidationOpticInterpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the Interpreters page makes about its direct and validating runs. */
@DisplayName("the Interpreters page: running a program directly, and checking it")
class InterpretersBookTest {

  @Test
  @DisplayName("the direct interpreter applies the modify, and the person prints as Alice, 26")
  void directRunAppliesTheModify() {
    assertThat(InterpretersBook.directRun())
        .isEqualTo(new Person("Alice", 26))
        .hasToString("Person[name=Alice, age=26]");
  }

  @Test
  @DisplayName("a set of null is a warning, not an error, and names the lens object, not the field")
  void nullSetIsAWarningNamingTheLens() {
    ValidationOpticInterpreter.ValidationResult result = InterpretersBook.validatingRun();

    assertThat(result.isValid()).isTrue();
    assertThat(result.errors()).isEmpty();
    assertThat(result.warnings())
        .singleElement()
        .asString()
        .startsWith("SET operation with null value: ")
        .doesNotContain("name");
  }

  @Test
  @DisplayName("the logging interpreter applies the modify and logs it as one MODIFY line")
  void loggingRunLogsTheModify() {
    InterpretersBook.Logged<Account> logged = InterpretersBook.loggingRun();

    assertThat(logged.result().balance()).isEqualTo(new BigDecimal("900.00"));
    assertThat(logged.log())
        .singleElement()
        .asString()
        .startsWith("MODIFY: ")
        .endsWith(" from 1000.00 to 900.00");
  }

  @Test
  @DisplayName("the audited transfer logs the read and both balance changes, each under its id")
  void auditedTransferLogsEachStep() {
    List<String> recorded = new ArrayList<>();

    InterpretersBook.Logged<Transaction> logged =
        InterpretersBook.auditedTransfer((txnId, entry) -> recorded.add(txnId + " " + entry));

    assertThat(logged.result().from().balance()).isEqualTo(new BigDecimal("750.00"));
    assertThat(logged.result().to().balance()).isEqualTo(new BigDecimal("750.00"));
    assertThat(logged.log()).hasSize(3);
    assertThat(logged.log().get(0)).startsWith("GET: ").endsWith(" -> 250.00");
    assertThat(logged.log().get(1)).startsWith("MODIFY: ").endsWith(" from 1000.00 to 750.00");
    assertThat(logged.log().get(2)).startsWith("MODIFY: ").endsWith(" from 500.00 to 750.00");
    assertThat(recorded).hasSize(3).allMatch(line -> line.startsWith("TXN-12345 "));
  }
}
