// Fixture for hkj-book/src/optics/free_monad_dsl.md
//
// The page describes optic work as a program and then chooses an interpreter, walking a club
// member, a payroll entry, a team, a migration, a transfer and a catalogue through the same shape.
// Every model is declared here; the snippet that shows one shadows this copy.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.id;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.tuple.Tuple;
import org.higherkindedj.hkt.tuple.Tuple2;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.fluent.OpticOps;
import org.higherkindedj.optics.free.DirectOpticInterpreter;
import org.higherkindedj.optics.free.LoggingOpticInterpreter;
import org.higherkindedj.optics.free.OpticInterpreters;
import org.higherkindedj.optics.free.OpticOp;
import org.higherkindedj.optics.free.OpticOpKind;
import org.higherkindedj.optics.free.OpticOpKindHelper;
import org.higherkindedj.optics.free.OpticPrograms;
import org.higherkindedj.optics.free.ValidationOpticInterpreter;

@GenerateLenses
record ClubMember(String name, int age, String status) {}

enum EmployeeStatus {
  JUNIOR,
  SENIOR,
  PROBATION,
  RETIRED
}

@GenerateLenses
record PayrollEntry(String name, int salary, EmployeeStatus status) {}

@GenerateLenses
record Player(String name, int score) {}

@GenerateLenses
@GenerateTraversals
record Team(String name, List<Player> players) {}

@GenerateLenses
record LegacyUser(String username, String email) {}

@GenerateLenses
record MigratedUser(String username, String email, boolean verified) {}

@GenerateLenses
record Account(String accountId, BigDecimal balance) {}

@GenerateLenses
record Transaction(String txnId, Account from, Account to, BigDecimal amount) {}

@GenerateLenses
record Product(String sku, String name, BigDecimal price, int stock) {}

@GenerateLenses
@GenerateTraversals
record ProductCatalogue(List<Product> products) {}

// The stub interpreter the page builds on `interpreters.md` and reaches for again here.
final class MockOpticInterpreter {

  private final Function<OpticOp<?, ?>, Object> stubs;

  MockOpticInterpreter(Function<OpticOp<?, ?>, Object> stubs) {
    this.stubs = stubs;
  }

  @SuppressWarnings("unchecked")
  <A> A run(Free<OpticOpKind.Witness, A> program) {
    Function<Kind<OpticOpKind.Witness, ?>, Kind<IdKind.Witness, ?>> transform =
        kind -> {
          OpticOp<?, ?> op =
              OpticOpKindHelper.OP.narrow((Kind<OpticOpKind.Witness, Object>) kind);
          // The stub stands in for what the operation produces, so it goes to the continuation
          return Id.of(((Function<Object, ?>) op.k()).apply(stubs.apply(op)));
        };
    Kind<IdKind.Witness, A> resultKind = program.foldMap(transform, Instances.monad(id()));
    return IdKindHelper.ID.narrow(resultKind).value();
  }
}

class Fixture {

  /**
   * A value the page names but does not build. Snippets are compiled, never run, and a snippet
   * that shows a model shadows the one above, so naming a constructor here would tie the fixture
   * to one shape of it.
   */
  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static final ClubMember person = sample();

  static final Free<OpticOpKind.Witness, Integer> getProgram = sample();

  static final Free<OpticOpKind.Witness, ClubMember> setProgram = sample();

  static final Free<OpticOpKind.Witness, ClubMember> modifyProgram = sample();

  static final Free<OpticOpKind.Witness, ClubMember> program = sample();

  static final Team team = sample();
}
