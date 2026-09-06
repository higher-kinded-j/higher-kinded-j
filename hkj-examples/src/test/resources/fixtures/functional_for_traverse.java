// Fixture for hkj-book/src/functional/for_traverse.md
//
// Three comprehensions over the same Maybe-of-List, so the monad and the traverse instance are
// declared once here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.maybe.MaybeKind;

class Fixture {

  static final org.higherkindedj.hkt.MonadError<MaybeKind.Witness, org.higherkindedj.hkt.Unit>
      maybeMonad = Instances.monadError(maybe());

  static final ListTraverse listTraverse = ListTraverse.INSTANCE;
}
