// Fixture for hkj-book/src/tooling/openrewrite.md
//
// The page shows how to view a wildcard container as the container exactly, so that a typed form
// fits the path. Only the record and the two paths onto its wildcard components are elided.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import java.util.Collections;
import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.each.EachInstances;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;

record Basket(
    List<? extends Number> amounts, Kind<ListKind.Witness, ? extends CharSequence> labels) {}

class Fixture {

  static final FocusPath<Basket, List<? extends Number>> amountsPath = sample();
  static final FocusPath<Basket, Kind<ListKind.Witness, ? extends CharSequence>> labelsPath =
      sample();

  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}
