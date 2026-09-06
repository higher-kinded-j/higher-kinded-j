// Fixture for hkj-book/src/effect/effect_handlers.md
//
// The page builds a console-and-database effect algebra and interprets it. The two algebras carry
// their `@EffectAlgebra` annotations here, so the processor generates the Kind, Functor,
// interpreter skeleton and smart constructors that every snippet on the page names - not only the
// one that shows the declaration. A snippet that declares its own copy shadows this one, and the
// processor generates from that instead.
//
// The wiring example runs against the payment example in this module's main sources, which is on
// the gate's classpath: it is the real `PaymentEffectsWiring`, not a stand-in.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.io;

import java.util.Scanner;
import java.util.function.Function;
import org.higherkindedj.example.payment.effect.PaymentEffectsWiring;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.annotation.ComposeEffects;
import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;
import org.higherkindedj.hkt.eitherf.EitherFKind;
import org.higherkindedj.hkt.eitherf.Interpreters;
import org.higherkindedj.hkt.inject.Inject;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.free.ProgramAnalyser;
import org.higherkindedj.hkt.free.ProgramAnalysis;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOKindHelper;

@EffectAlgebra
sealed interface ConsoleOp<A> permits ConsoleOp.ReadLine, ConsoleOp.PrintLine {

  <B> ConsoleOp<B> mapK(Function<? super A, ? extends B> f);

  record ReadLine<A>(Function<String, A> k) implements ConsoleOp<A> {
    @Override
    public <B> ConsoleOp<B> mapK(Function<? super A, ? extends B> f) {
      return new ReadLine<>(k.andThen(f));
    }
  }

  record PrintLine<A>(String message, Function<Unit, A> k) implements ConsoleOp<A> {
    @Override
    public <B> ConsoleOp<B> mapK(Function<? super A, ? extends B> f) {
      return new PrintLine<>(message, k.andThen(f));
    }
  }
}

@EffectAlgebra
sealed interface DbOp<A> permits DbOp.Save, DbOp.Load {

  <B> DbOp<B> mapK(Function<? super A, ? extends B> f);

  record Save<A>(String value, Function<String, A> k) implements DbOp<A> {
    @Override
    public <B> DbOp<B> mapK(Function<? super A, ? extends B> f) {
      return new Save<>(value, k.andThen(f));
    }
  }

  record Load<A>(String key, Function<String, A> k) implements DbOp<A> {
    @Override
    public <B> DbOp<B> mapK(Function<? super A, ? extends B> f) {
      return new Load<>(key, k.andThen(f));
    }
  }
}

@ComposeEffects
record AppEffects(Class<ConsoleOp<?>> console, Class<DbOp<?>> db) {}

/** The interpreters the combining example folds a program with. */
final class IOConsole extends ConsoleOpInterpreter<IOKind.Witness> {

  private final Scanner scanner = new Scanner(System.in);

  @Override
  protected <A> Kind<IOKind.Witness, A> handleReadLine(ConsoleOp.ReadLine<A> op) {
    return IOKindHelper.IO_OP.widen(IO.delay(() -> op.k().apply(scanner.nextLine())));
  }

  @Override
  protected <A> Kind<IOKind.Witness, A> handlePrintLine(ConsoleOp.PrintLine<A> op) {
    return IOKindHelper.IO_OP.widen(
        IO.delay(
            () -> {
              System.out.println(op.message());
              return op.k().apply(Unit.INSTANCE);
            }));
  }
}

final class IODb extends DbOpInterpreter<IOKind.Witness> {

  @Override
  protected <A> Kind<IOKind.Witness, A> handleSave(DbOp.Save<A> op) {
    return IOKindHelper.IO_OP.widen(IO.delay(() -> op.k().apply(op.value())));
  }

  @Override
  protected <A> Kind<IOKind.Witness, A> handleLoad(DbOp.Load<A> op) {
    return IOKindHelper.IO_OP.widen(IO.delay(() -> op.k().apply("stored")));
  }
}

class Fixture {

  static final Scanner scanner = new Scanner(System.in);

  static final IOConsole consoleInterp = new IOConsole();

  static final IODb dbInterp = new IODb();

  static final String defaultValue = "default";

  static final Free<ConsoleOpKind.Witness, String> program =
      Free.liftF(
          ConsoleOpKindHelper.CONSOLE_OP.widen(new ConsoleOp.ReadLine<String>(s -> s)),
          ConsoleOpFunctor.instance());

  static final Free<ConsoleOpKind.Witness, String> riskyOperation = program;
}
