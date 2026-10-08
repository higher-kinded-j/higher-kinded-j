// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.programs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the Programs as Data introduction makes about its one program. */
@DisplayName("the Programs as Data introduction: one program, run three ways")
class ProgramsBookTest {

  @Test
  @DisplayName("describing the withdrawal leaves the account as it was")
  void describingChangesNothing() {
    ProgramsBook.withdraw(ProgramsBook.ACCOUNT, 30);

    assertThat(ProgramsBook.ACCOUNT).isEqualTo(new Account("ACC-1", 100));
  }

  @Test
  @DisplayName("the direct run settles the account on 70")
  void directRunSettles() {
    assertThat(ProgramsBook.runThreeWays(ProgramsBook.ACCOUNT).settled())
        .isEqualTo(new Account("ACC-1", 70))
        .hasToString("Account[id=ACC-1, balance=70]");
  }

  @Test
  @DisplayName("the logging run gives the same account and one entry for its one operation")
  void loggingRunRecordsOneEntry() {
    ProgramsBook.Runs runs = ProgramsBook.runThreeWays(ProgramsBook.ACCOUNT);

    assertThat(runs.audited()).isEqualTo(runs.settled());
    assertThat(runs.trail()).singleElement().asString().startsWith("MODIFY").contains("100 to 70");
  }

  @Test
  @DisplayName("the validating run reports the program safe to run")
  void validatingRunIsSafe() {
    assertThat(ProgramsBook.runThreeWays(ProgramsBook.ACCOUNT).safeToRun()).isTrue();
  }
}
