// Fixture for hkj-book/src/monads/coyoneda.md
//
// The page lifts a Maybe, a List and two DSLs into Coyoneda and lowers them again. The DSLs and
// their HKT bridges are declared here so a snippet showing one of them still has the rest around
// it; a snippet that declares its own copy shadows this one.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.coyoneda.CoyonedaKindHelper.COYONEDA;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.sql.ResultSet;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.coyoneda.Coyoneda;
import org.higherkindedj.hkt.coyoneda.CoyonedaFunctor;
import org.higherkindedj.hkt.coyoneda.CoyonedaKind;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeFunctor;
import org.higherkindedj.hkt.maybe.MaybeKind;

sealed interface DatabaseOp<A> {
  record Query(String sql) implements DatabaseOp<ResultSet> {}

  record Update(String sql) implements DatabaseOp<Integer> {}
}

interface DatabaseOpKind<A> extends Kind<DatabaseOpKind.Witness, A> {
  final class Witness implements WitnessArity<TypeArity.Unary> {
    private Witness() {}
  }
}

enum DatabaseOpKindHelper {
  DATABASE_OP;

  record Holder<A>(DatabaseOp<A> op) implements DatabaseOpKind<A> {}

  public <A> Kind<DatabaseOpKind.Witness, A> widen(DatabaseOp<A> op) {
    return new Holder<>(op);
  }

  @SuppressWarnings("unchecked") // the holder is the only implementation
  public <A> DatabaseOp<A> narrow(Kind<DatabaseOpKind.Witness, A> kind) {
    return ((Holder<A>) kind).op();
  }
}

sealed interface ConsoleOp<A> {
  record ReadLine() implements ConsoleOp<String> {}
}

interface ConsoleOpKind<A> extends Kind<ConsoleOpKind.Witness, A> {
  final class Witness implements WitnessArity<TypeArity.Unary> {
    private Witness() {}
  }
}

enum ConsoleOpKindHelper {
  CONSOLE_OP;

  record Holder<A>(ConsoleOp<A> op) implements ConsoleOpKind<A> {}

  public <A> Kind<ConsoleOpKind.Witness, A> widen(ConsoleOp<A> op) {
    return new Holder<>(op);
  }

  @SuppressWarnings("unchecked") // the holder is the only implementation
  public <A> ConsoleOp<A> narrow(Kind<ConsoleOpKind.Witness, A> kind) {
    return ((Holder<A>) kind).op();
  }
}

/** The "without Coyoneda" half of the comparison needs one; the Coyoneda half does not. */
class ConsoleOpFunctor implements Functor<ConsoleOpKind.Witness> {
  @Override
  @SuppressWarnings("unchecked") // simplified DSL: map returns the operation unchanged
  public <A, B> Kind<ConsoleOpKind.Witness, B> map(
      Function<? super A, ? extends B> f, Kind<ConsoleOpKind.Witness, A> fa) {
    return (Kind<ConsoleOpKind.Witness, B>) fa;
  }
}

class Fixture {

  static final DatabaseOpKindHelper DATABASE_OP = DatabaseOpKindHelper.DATABASE_OP;

  static final Kind<MaybeKind.Witness, Integer> maybe = MAYBE.widen(Maybe.just(42));

  static final Coyoneda<MaybeKind.Witness, String> mapped =
      Coyoneda.lift(maybe).map(x -> x * 2).map(x -> x + 1).map(Object::toString);

  static final List<Integer> list = List.of(1, 2, 3);

  static final Functor<ListKind.Witness> listFunctor = Instances.functor(list());

  static final Kind<ConsoleOpKind.Witness, String> readLine =
      ConsoleOpKindHelper.CONSOLE_OP.widen(new ConsoleOp.ReadLine());

  static final Kind<ConsoleOpKind.Witness, String> readLineKind = readLine;

  static final ConsoleOpFunctor consoleOpFunctor = new ConsoleOpFunctor();
}
