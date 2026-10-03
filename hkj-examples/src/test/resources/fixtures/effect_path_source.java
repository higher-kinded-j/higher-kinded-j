// Fixture for hkj-book/src/effect/path_source.md
//
// The page's snippets use the two effects of the page's example: Traced, with its witness, Monad
// and generated TracedPath, and Outcome, with Problem, its MonadError and generated OutcomePath.
// They come from the compiled example in hkj-examples, so a snippet is checked against the real
// generated classes. The example package is imported on demand, so a snippet may declare its own
// Traced or Outcome to show an annotation, and that declaration shadows the example's.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import java.util.List;
import org.higherkindedj.example.book.effect.pathsource.*;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.GenericPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.annotation.PathSource;
import org.higherkindedj.hkt.either.EitherKind;

class Fixture {

  // Traced, TracedPath and OutcomePath are qualified: a snippet that declares its own Traced or
  // Outcome generates its own Path beside it, and one snippet declares a TracedPath, so each simple
  // name can mean the snippet's type rather than the example's.
  static final org.higherkindedj.example.book.effect.pathsource.TracedPath<Integer> priced =
      org.higherkindedj.example.book.effect.pathsource.TracedPath.of(
          org.higherkindedj.example.book.effect.pathsource.Traced.of(1200, "priced the basket"),
          TracedMonad.INSTANCE);

  static final org.higherkindedj.example.book.effect.pathsource.OutcomePath<String> reservation =
      org.higherkindedj.example.book.effect.pathsource.OutcomePath.of(
          PathSourceBook.reserve("SKU-42"), OutcomeMonad.INSTANCE, OutcomeMonad.INSTANCE);
}
