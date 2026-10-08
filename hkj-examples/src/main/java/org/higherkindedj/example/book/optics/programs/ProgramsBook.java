// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.programs;

import java.util.List;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.free.DirectOpticInterpreter;
import org.higherkindedj.optics.free.LoggingOpticInterpreter;
import org.higherkindedj.optics.free.OpticInterpreters;
import org.higherkindedj.optics.free.OpticOpKind;
import org.higherkindedj.optics.free.OpticPrograms;
import org.higherkindedj.optics.free.ValidationOpticInterpreter;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/ch6_intro.html">Programs as Data</a> page.
 * The page {@code {{#include}}}s the anchored region, and {@code ProgramsBookTest} holds the claims
 * the page makes about this code.
 */
public final class ProgramsBook {

  /** The page's sample account, which starts on 100. */
  static final Account ACCOUNT = new Account("ACC-1", 100);

  /** What the three runs of the one program hand back. */
  record Runs(Account settled, Account audited, List<String> trail, boolean safeToRun) {}

  private ProgramsBook() {}

  /** A withdrawal described as data: nothing has run yet. */
  static Free<OpticOpKind.Witness, Account> withdraw(Account from, int amount) {
    return OpticPrograms.modify(from, AccountLenses.balance(), balance -> balance - amount);
  }

  static Runs runThreeWays(Account account) {
    // ANCHOR: payoff
    // A description, not an action: nothing has touched the account yet
    Free<OpticOpKind.Witness, Account> withdrawal = withdraw(account, 30);

    // Run it for real
    DirectOpticInterpreter direct = OpticInterpreters.direct();
    Account settled = direct.run(withdrawal);
    // Account[id=ACC-1, balance=70]

    // Run the same value again, recording every optic operation on the way
    LoggingOpticInterpreter logging = OpticInterpreters.logging();
    Account audited = logging.run(withdrawal);
    List<String> trail = logging.getLog();
    // one entry per optic operation the program performed

    // Or run it and get a report of what went wrong instead of the result
    ValidationOpticInterpreter validator = OpticInterpreters.validating();
    ValidationOpticInterpreter.ValidationResult check = validator.validate(withdrawal);
    boolean safeToRun = check.isValid();
    // true: no nulls written, no modifier threw
    // ANCHOR_END: payoff
    return new Runs(settled, audited, trail, safeToRun);
  }
}

@GenerateLenses
record Account(String id, int balance) {}
