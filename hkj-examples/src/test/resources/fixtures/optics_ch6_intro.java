// Fixture for hkj-book/src/optics/ch6_intro.md
//
// The page's payoff snippet builds one optic program as a value and then runs
// it three ways: directly, through the logging interpreter, and through the
// validating interpreter without executing it at all. The record lives here and
// the annotation processor generates AccountLenses during snippet compilation.
//
// NOTE: imports in a fixture serve the snippet this file is spliced into.
// Spotless excludes src/test/resources so an "unused import" cleanup cannot
// break fixtures (see build.gradle.kts).

import java.util.List;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.free.DirectOpticInterpreter;
import org.higherkindedj.optics.free.LoggingOpticInterpreter;
import org.higherkindedj.optics.free.OpticInterpreters;
import org.higherkindedj.optics.free.OpticOpKind;
import org.higherkindedj.optics.free.OpticPrograms;
import org.higherkindedj.optics.free.ValidationOpticInterpreter;

@GenerateLenses
record Account(String id, int balance) {}

class Fixture {

  static final Account account = new Account("ACC-1", 100);

  /** A withdrawal described as data: nothing has run yet. */
  static Free<OpticOpKind.Witness, Account> withdraw(Account from, int amount) {
    return OpticPrograms.modify(from, AccountLenses.balance(), balance -> balance - amount);
  }
}
