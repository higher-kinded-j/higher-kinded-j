// Fixture for hkj-book/src/optics/free_monad_dsl.md
//
// The page describes optic work as a program and then chooses an interpreter, walking a person, an
// employee, a team, a migration, a transfer and a catalogue through the same shape. Every model is
// declared here; the snippet that shows one shadows this copy.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

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
record Person(String name, int age, String status) {}

enum EmployeeStatus {
  JUNIOR,
  SENIOR,
  PROBATION,
  RETIRED
}

@GenerateLenses
record Employee(String name, int salary, EmployeeStatus status) {}

@GenerateLenses
record Player(String name, int score) {}

@GenerateLenses
@GenerateTraversals
record Team(String name, List<Player> players) {}

@GenerateLenses
record UserV1(String username, String email) {}

@GenerateLenses
record UserV2(String username, String email, boolean verified) {}

@GenerateLenses
record Account(String accountId, BigDecimal balance) {}

@GenerateLenses
record Transaction(Account from, Account to, BigDecimal amount) {}

@GenerateLenses
record Product(String id, BigDecimal price, int stock) {}

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
          return Id.of(Free.pure(stubs.apply(op)));
        };
    Kind<IdKind.Witness, A> resultKind = program.foldMap(transform, Instances.monad(id()));
    return IdKindHelper.ID.narrow(resultKind).value();
  }
}

class Fixture {

  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static final Person person = sample();

  static final Free<OpticOpKind.Witness, Integer> getProgram = sample();

  static final Free<OpticOpKind.Witness, Person> setProgram = sample();

  static final Free<OpticOpKind.Witness, Person> modifyProgram = sample();

  static final Free<OpticOpKind.Witness, Person> program = sample();

  static final Team team = sample();
}
