// Fixture for hkj-book/src/hkts/extending-simulation.md
//
// The page walks a reader through adding a type of their own to the simulation, so every snippet is
// a declaration and there is nothing to elide but the imports each one already shows. The page's
// `package` lines are kept and ignored by the gate: saying where the file goes is worth showing.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.util.Objects;
import java.util.Set;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** The page builds these up one snippet at a time; each snippet declares the ones it shows. */
@NullMarked
interface SetKind<A> extends Kind<SetKind.Witness, A> {

  final class Witness implements WitnessArity<TypeArity.Unary> {
    private Witness() {}
  }
}

interface SetConverterOps {
  <A> @NonNull Kind<SetKind.Witness, A> widen(@NonNull Set<A> set);

  <A> @NonNull Set<A> narrow(@Nullable Kind<SetKind.Witness, A> kind) throws KindUnwrapException;
}

@NullMarked
interface MyTypeKind<A> extends Kind<MyTypeKind.Witness, A> {

  final class Witness implements WitnessArity<TypeArity.Unary> {
    private Witness() {}
  }
}

record MyType<A>(A value) implements MyTypeKind<A> {}

interface MyTypeConverterOps {
  <A> @NonNull Kind<MyTypeKind.Witness, A> widen(@NonNull MyType<A> myTypeValue);

  <A> @NonNull MyType<A> narrow(@Nullable Kind<MyTypeKind.Witness, A> kind)
      throws KindUnwrapException;
}

class Fixture {}
