// Fixture for hkj-book/src/hkts/usage-guide.md
//
// The guide walks one Optional through widen, map, flatMap, handleErrorWith and narrow, a step per
// snippet, so each step elides the ones before it. They are supplied here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.io;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.optional.OptionalKind;

class Fixture {

  static final Kind<OptionalKind.Witness, String> checkedKind =
      OPTIONAL.widen(Optional.of("Long enough"));

  static final Kind<OptionalKind.Witness, String> handledKind =
      OPTIONAL.widen(Optional.of("Default Value"));
}
