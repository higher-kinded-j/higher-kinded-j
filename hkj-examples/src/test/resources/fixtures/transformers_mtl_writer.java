// Fixture for hkj-book/src/transformers/mtl_writer.md
//
// The page writes audit-log operations against `MonadWriter` over WriterT/Id. The instance the
// snippets share is declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.id;

import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadWriter;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Pair;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.writer_t.WriterTKind;

class Fixture {

  static final Monad<IdKind.Witness> idMonad = Instances.monad(id());

  static final Monoid<List<String>> listMonoid = Monoids.list();

  static final MonadWriter<WriterTKind.Witness<IdKind.Witness, List<String>>, List<String>> audit =
      Instances.writerT(idMonad, listMonoid);
}
