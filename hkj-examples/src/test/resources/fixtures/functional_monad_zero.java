// Fixture for hkj-book/src/functional/monad_zero.md
//
// The page filters inside a comprehension over two lists. Those lists and the monad are declared
// here for the snippets that pick them up.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.maybe.MaybeKind;

class Fixture {

  static final MonadZero<ListKind.Witness> monad = Instances.monadZero(list());

  static final Kind<ListKind.Witness, Integer> list1 = LIST.widen(Arrays.asList(1, 2, 3));

  static final Kind<ListKind.Witness, Integer> list2 = LIST.widen(Arrays.asList(10, 20));
}
