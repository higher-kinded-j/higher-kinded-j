// Fixture for .claude/skills/hkj-effects/reference/free-monad-basics.md
//
// The page builds a console DSL step by step, so by the time it writes a program the smart
// constructors it showed earlier are in scope. Supplying them here is what lets the program snippet
// be compiled as the page writes it — bare `printLine(...)` / `readLine()` — against the real
// generated ConsoleOpOps, rather than against a hand-written stand-in.
//
// The algebra is declared TOP-LEVEL: a nested @EffectAlgebra makes the processor join the enclosing
// simple names (Holder.ConsoleOp -> HolderConsoleOpKind), which is not the ConsoleOpKind the page
// teaches. A snippet that declares its own ConsoleOp shadows the one below; the CPS version is
// deliberately identical to the page's.
//
// NOTE: the imports below look unused *here*. They are for the snippet this file is spliced into.
// That is why spotless excludes src/test/resources/fixtures (see build.gradle.kts).

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.free.FreeFactory;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOKindHelper;
import org.higherkindedj.hkt.io.IOMonad;

/** The console algebra in its CPS form, exactly as the page shows it. */
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

class Fixture {

  /** Step 3's smart constructors, over the generated Ops: identity gives the natural result type. */
  static Free<ConsoleOpKind.Witness, Unit> printLine(String text) {
    return ConsoleOpOps.printLine(text, Function.identity());
  }

  static Free<ConsoleOpKind.Witness, String> readLine() {
    return ConsoleOpOps.readLine(Function.identity());
  }
}
