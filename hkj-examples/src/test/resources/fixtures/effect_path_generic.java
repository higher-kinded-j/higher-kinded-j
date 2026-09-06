// Fixture for hkj-book/src/effect/path_generic.md
//
// The page runs one list through GenericPath, so the witness, its Monad and one already-wrapped
// value have to be in scope for each snippet.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.list;

import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.effect.GenericPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListKindHelper;

class Fixture {

  static final Monad<ListKind.Witness> listMonad = Instances.monadZero(list());

  static final Kind<ListKind.Witness, Integer> listKind =
      ListKindHelper.LIST.widen(List.of(1, 2, 3));

  static final GenericPath<ListKind.Witness, Integer> path = Path.generic(listKind, listMonad);
}
