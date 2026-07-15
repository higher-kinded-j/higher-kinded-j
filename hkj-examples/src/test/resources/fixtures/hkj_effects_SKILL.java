// Fixture for .claude/skills/hkj-effects/SKILL.md
//
// The skill teaches one effect algebra at a time, so its snippets elide the rest of the application:
// the other algebras, the interpreters that handle them, and the program they interpret. Supplying
// those here means every snippet is compiled against the code the *real* annotation processor
// generates from a real @EffectAlgebra / @ComposeEffects source, not against a stand-in.
//
// The algebras are declared TOP-LEVEL. A nested @EffectAlgebra makes the processor join the
// enclosing simple names (Holder.ConsoleOp -> HolderConsoleOpKind), which is not what the skill
// teaches. A snippet that declares its own ConsoleOp shadows the one below, so the processor
// generates from the snippet's copy: they are deliberately identical.
//
// F / A / MyError are placeholders for the reader's own types: the error-recovery snippet is written
// against an abstract witness `F` and result `A`, and the fixture cannot lend *bounded* type
// parameters to a snippet, so they are supplied as concrete stand-ins instead.
//
// NOTE: the imports below look unused *here*. They are for the snippet this file is spliced into.
// That is why spotless excludes src/test/resources/fixtures (see build.gradle.kts).

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.annotation.ComposeEffects;
import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;
import org.higherkindedj.hkt.effect.boundary.EffectBoundary;
import org.higherkindedj.hkt.effect.boundary.ProgramAnalysis;
import org.higherkindedj.hkt.effect.boundary.TestBoundary;
import org.higherkindedj.hkt.eitherf.EitherFFunctor;
import org.higherkindedj.hkt.eitherf.EitherFKind;
import org.higherkindedj.hkt.eitherf.Interpreters;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.free.ProgramAnalyser;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.inject.InjectInstances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOKindHelper;
import org.higherkindedj.hkt.io.IOMonad;
import org.higherkindedj.hkt.trymonad.Try;

/** The reader's console algebra, exactly as Step 1 shows it. */
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

/** The reader's database algebra: `db.save(name, Function.identity())` in Step 3. */
@EffectAlgebra
sealed interface DbOp<A> permits DbOp.Save {

  <B> DbOp<B> mapK(Function<? super A, ? extends B> f);

  record Save<A>(String name, Function<String, A> k) implements DbOp<A> {
    @Override
    public <B> DbOp<B> mapK(Function<? super A, ? extends B> f) {
      return new Save<>(name, k.andThen(f));
    }
  }
}

/** The reader's logging algebra: the third effect in the @ComposeEffects record. */
@EffectAlgebra
sealed interface LogOp<A> permits LogOp.Info {

  <B> LogOp<B> mapK(Function<? super A, ? extends B> f);

  record Info<A>(String message, Function<Unit, A> k) implements LogOp<A> {
    @Override
    public <B> LogOp<B> mapK(Function<? super A, ? extends B> f) {
      return new Info<>(message, k.andThen(f));
    }
  }
}

/** The algebra the "Pattern for Each Record" template is written against. */
interface MyOp<A> {
  <B> MyOp<B> mapK(Function<? super A, ? extends B> f);
}

record ParamType1(String value) {}

record ParamType2(int value) {}

record ResultType(String value) {}

/** Stand-in for the abstract witness the error-recovery snippet calls `F`. */
final class F implements WitnessArity<TypeArity.Unary> {
  private F() {}
}

/** Stand-in for the abstract result type the error-recovery snippet calls `A`. */
record A(String value) {}

/** The reader's own error type. */
final class MyError extends RuntimeException {
  MyError(String message) {
    super(message);
  }
}

/** A production interpreter for ConsoleOp, targeting IO. */
final class IoConsole extends ConsoleOpInterpreter<IOKind.Witness> {
  @Override
  protected <T> Kind<IOKind.Witness, T> handleReadLine(ConsoleOp.ReadLine<T> op) {
    return IOKindHelper.IO_OP.widen(IO.delay(() -> op.k().apply("Ada")));
  }

  @Override
  protected <T> Kind<IOKind.Witness, T> handlePrintLine(ConsoleOp.PrintLine<T> op) {
    return IOKindHelper.IO_OP.widen(
        IO.delay(
            () -> {
              System.out.println(op.message());
              return op.k().apply(Unit.INSTANCE);
            }));
  }
}

/** A production interpreter for DbOp, targeting IO. */
final class IoDb extends DbOpInterpreter<IOKind.Witness> {
  @Override
  protected <T> Kind<IOKind.Witness, T> handleSave(DbOp.Save<T> op) {
    return IOKindHelper.IO_OP.widen(IO.delay(() -> op.k().apply("id-" + op.name())));
  }
}

/** A production interpreter for LogOp, targeting IO. */
final class IoLog extends LogOpInterpreter<IOKind.Witness> {
  @Override
  protected <T> Kind<IOKind.Witness, T> handleInfo(LogOp.Info<T> op) {
    return IOKindHelper.IO_OP.widen(IO.delay(() -> op.k().apply(Unit.INSTANCE)));
  }
}

/** A test interpreter for ConsoleOp, targeting Id: no mocks, no IO. */
final class TestConsole extends ConsoleOpInterpreter<IdKind.Witness> {
  @Override
  protected <T> Kind<IdKind.Witness, T> handleReadLine(ConsoleOp.ReadLine<T> op) {
    return IdKindHelper.ID.widen(Id.of(op.k().apply("Ada")));
  }

  @Override
  protected <T> Kind<IdKind.Witness, T> handlePrintLine(ConsoleOp.PrintLine<T> op) {
    return IdKindHelper.ID.widen(Id.of(op.k().apply(Unit.INSTANCE)));
  }
}

/** A test interpreter for DbOp, targeting Id. */
final class TestDb extends DbOpInterpreter<IdKind.Witness> {
  @Override
  protected <T> Kind<IdKind.Witness, T> handleSave(DbOp.Save<T> op) {
    return IdKindHelper.ID.widen(Id.of(op.k().apply("id-" + op.name())));
  }
}

/** A test interpreter for LogOp, targeting Id. */
final class TestLog extends LogOpInterpreter<IdKind.Witness> {
  @Override
  protected <T> Kind<IdKind.Witness, T> handleInfo(LogOp.Info<T> op) {
    return IdKindHelper.ID.widen(Id.of(op.k().apply(Unit.INSTANCE)));
  }
}

class Fixture {

  static final ConsoleOpInterpreter<IOKind.Witness> consoleInterp = new IoConsole();
  static final DbOpInterpreter<IOKind.Witness> dbInterp = new IoDb();
  static final LogOpInterpreter<IOKind.Witness> logInterp = new IoLog();

  static final ConsoleOpInterpreter<IdKind.Witness> testConsoleInterp = new TestConsole();
  static final DbOpInterpreter<IdKind.Witness> testDbInterp = new TestDb();
  static final LogOpInterpreter<IdKind.Witness> testLogInterp = new TestLog();

  /** Program analysis needs only two interpreters: the second is itself a combination. */
  static final Natural<ConsoleOpKind.Witness, IdKind.Witness> testInterp1 = testConsoleInterp;

  static final Natural<EitherFKind.Witness<DbOpKind.Witness, LogOpKind.Witness>, IdKind.Witness>
      testInterp2 = Interpreters.combine(testDbInterp, testLogInterp);

  /** The program Step 3 builds. Java has no type alias, so the composed witness is spelled out. */
  static final Free<
          EitherFKind.Witness<
              ConsoleOpKind.Witness, EitherFKind.Witness<DbOpKind.Witness, LogOpKind.Witness>>,
          String>
      program = Free.pure("Saved with id: id-Ada");

  static final Free<F, A> riskyProgram = Free.pure(new A("risky"));
  static final Free<F, A> fallbackProgram = Free.pure(new A("fallback"));
}
