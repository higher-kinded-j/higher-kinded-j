// Fixture for hkj-book/src/functional/natural_transformation.md
//
// The page writes four transformations by hand, the last of them a Free interpreter. The console
// algebra it interprets, and the HKT bridge around it, are declared here in the shape the page
// shows; a snippet that declares its own `ConsoleOp` shadows this one.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.io;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.List;
import java.util.Scanner;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;

sealed interface ConsoleOp<A> {
  record PrintLine(String text) implements ConsoleOp<Unit> {}

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

class ConsoleOpFunctor implements Functor<ConsoleOpKind.Witness> {
  @Override
  @SuppressWarnings("unchecked") // simplified DSL: map returns the operation unchanged
  public <A, B> Kind<ConsoleOpKind.Witness, B> map(
      Function<? super A, ? extends B> f, Kind<ConsoleOpKind.Witness, A> fa) {
    return (Kind<ConsoleOpKind.Witness, B>) fa;
  }
}

class Fixture {

  static final Scanner scanner = new Scanner(System.in);

  static final ConsoleOpKindHelper CONSOLE_OP = ConsoleOpKindHelper.CONSOLE_OP;

  static final Monad<IOKind.Witness> ioMonad = Instances.monad(io());

  static final Free<ConsoleOpKind.Witness, String> program =
      Free.liftF(
          ConsoleOpKindHelper.CONSOLE_OP.widen(new ConsoleOp.ReadLine()), new ConsoleOpFunctor());
}
