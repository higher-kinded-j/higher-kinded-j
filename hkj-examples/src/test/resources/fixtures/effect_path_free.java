// Fixture for hkj-book/src/effect/path_free.md
//
// The page builds one console DSL and interprets it twice. The algebra is a `Kind` in its own
// right, which is what lets `Path.freeLift` take an operation straight off the page without a
// widen helper standing in the way.
//
// `ask` and `tell` are instance methods so the snippet that shows their bodies overrides them
// rather than clashing with them.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.io;

import java.util.Scanner;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.FreePath;
import org.higherkindedj.hkt.effect.GenericPath;
import org.higherkindedj.hkt.effect.NaturalTransformation;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKindHelper;

sealed interface ConsoleOp<A> extends Kind<ConsoleOp.Witness, A> permits Ask, Tell {
  interface Witness extends WitnessArity<TypeArity.Unary> {}
}

record Ask<A>(String prompt, Function<String, A> next) implements ConsoleOp<A> {}

record Tell<A>(String message, A next) implements ConsoleOp<A> {}

class Fixture {

  static final Scanner scanner = new Scanner(System.in);

  static final Monad<IO.Witness> ioMonad = Instances.monad(io());

  static final Functor<ConsoleOp.Witness> consoleFunctor =
      new Functor<>() {
        @Override
        public <A, B> Kind<ConsoleOp.Witness, B> map(
            Function<? super A, ? extends B> f, Kind<ConsoleOp.Witness, A> fa) {
          return switch ((ConsoleOp<A>) fa) {
            case Ask<A>(String prompt, Function<String, A> next) ->
                new Ask<B>(prompt, s -> f.apply(next.apply(s)));
            case Tell<A>(String message, A next) -> new Tell<B>(message, f.apply(next));
          };
        }
      };

  static final NaturalTransformation<ConsoleOp.Witness, IO.Witness> realInterpreter =
      new NaturalTransformation<>() {
        @Override
        public <A> Kind<IO.Witness, A> apply(Kind<ConsoleOp.Witness, A> fa) {
          return switch ((ConsoleOp<A>) fa) {
            case Ask<A> a ->
                IO.delay(
                    () -> {
                      System.out.print(a.prompt() + " ");
                      return a.next().apply(scanner.nextLine());
                    });
            case Tell<A> t ->
                IO.delay(
                    () -> {
                      System.out.println(t.message());
                      return t.next();
                    });
          };
        }
      };

  static final NaturalTransformation<ConsoleOp.Witness, IO.Witness> testInterpreter =
      new NaturalTransformation<>() {
        @Override
        public <A> Kind<IO.Witness, A> apply(Kind<ConsoleOp.Witness, A> fa) {
          return switch ((ConsoleOp<A>) fa) {
            case Ask<A> a -> IO.delay(() -> a.next().apply("Alice"));
            case Tell<A> t -> IO.delay(t::next);
          };
        }
      };

  FreePath<ConsoleOp.Witness, String> ask(String prompt) {
    return Path.freeLift(new Ask<>(prompt, Function.identity()), consoleFunctor);
  }

  FreePath<ConsoleOp.Witness, Void> tell(String message) {
    return Path.freeLift(new Tell<>(message, null), consoleFunctor);
  }

  final FreePath<ConsoleOp.Witness, String> greetUser =
      ask("What is your name?").via(name -> tell("Hello, " + name + "!").map(v -> name));
}
