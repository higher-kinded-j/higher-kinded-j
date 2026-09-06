// Fixture for hkj-book/src/monads/free_monad.md
//
// The page hand-rolls a console DSL and two interpreters for it, one step at a time, and shows the
// finished program before any of the steps. The whole DSL is declared here so that a snippet
// showing one step still has the rest of it around; a snippet that declares its own copy of a step
// shadows this one. It mirrors ConsoleProgram in this module's main sources, which is the example
// the page links to.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.free.FreeFactory;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.instances.Instances;
import org.junit.jupiter.api.Test;

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
  CONSOLE;

  record ConsoleOpHolder<A>(ConsoleOp<A> op) implements ConsoleOpKind<A> {}

  public <A> Kind<ConsoleOpKind.Witness, A> widen(ConsoleOp<A> op) {
    return new ConsoleOpHolder<>(op);
  }

  @SuppressWarnings("unchecked") // the holder is the only implementation
  public <A> ConsoleOp<A> narrow(Kind<ConsoleOpKind.Witness, A> kind) {
    return ((ConsoleOpHolder<A>) kind).op();
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

class ConsoleOps {
  private static final ConsoleOpFunctor FUNCTOR = new ConsoleOpFunctor();

  public static Free<ConsoleOpKind.Witness, Unit> printLine(String text) {
    return Free.liftF(ConsoleOpKindHelper.CONSOLE.widen(new ConsoleOp.PrintLine(text)), FUNCTOR);
  }

  public static Free<ConsoleOpKind.Witness, String> readLine() {
    return Free.liftF(ConsoleOpKindHelper.CONSOLE.widen(new ConsoleOp.ReadLine()), FUNCTOR);
  }
}

class IOInterpreter {
  private static final Scanner scanner = new Scanner(System.in);

  public <A> A run(Free<ConsoleOpKind.Witness, A> program) {
    Function<Kind<ConsoleOpKind.Witness, ?>, Kind<IdKind.Witness, ?>> transform =
        kind -> {
          ConsoleOp<?> op = ConsoleOpKindHelper.CONSOLE.narrow(kind);
          Object result =
              switch (op) {
                case ConsoleOp.PrintLine p -> {
                  System.out.println(p.text());
                  yield Unit.INSTANCE;
                }
                case ConsoleOp.ReadLine r -> scanner.nextLine();
              };
          return Id.of(result);
        };
    return IdKindHelper.ID.narrow(program.foldMap(transform, Instances.monad(id()))).value();
  }
}

class TestInterpreter {
  private final List<String> input;
  private final List<String> output = new ArrayList<>();
  private int inputIndex = 0;

  public TestInterpreter(List<String> input) {
    this.input = input;
  }

  public <A> A run(Free<ConsoleOpKind.Witness, A> program) {
    Function<Kind<ConsoleOpKind.Witness, ?>, Kind<IdKind.Witness, ?>> transform =
        kind -> {
          ConsoleOp<?> op = ConsoleOpKindHelper.CONSOLE.narrow(kind);
          Object result =
              switch (op) {
                case ConsoleOp.PrintLine p -> {
                  output.add(p.text());
                  yield Unit.INSTANCE;
                }
                case ConsoleOp.ReadLine r -> input.get(inputIndex++);
              };
          return Id.of(result);
        };
    return IdKindHelper.ID.narrow(program.foldMap(transform, Instances.monad(id()))).value();
  }

  public List<String> getOutput() {
    return output;
  }
}

class Fixture {

  static final IOInterpreter ioInterpreter = new IOInterpreter();

  static final Free<ConsoleOpKind.Witness, Unit> program =
      ConsoleOps.printLine("What is your name?")
          .flatMap(
              ignored ->
                  ConsoleOps.readLine()
                      .flatMap(name -> ConsoleOps.printLine("Hello, " + name + "!")));

  static final Free<ConsoleOpKind.Witness, Unit> greetingProgram = program;

  static Free<ConsoleOpKind.Witness, Unit> printLine(String text) {
    return ConsoleOps.printLine(text);
  }

  static Free<ConsoleOpKind.Witness, String> readLine() {
    return ConsoleOps.readLine();
  }
}
