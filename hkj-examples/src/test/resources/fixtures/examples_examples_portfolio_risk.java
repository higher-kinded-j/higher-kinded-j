// Fixture for hkj-book/src/examples/examples_portfolio_risk.md
//
// The page walks the portfolio example's focus paths. Its records are nested in
// PortfolioRiskExample and its generated focuses are top-level beside it, both on the gate's
// classpath, so the fixture supplies only the portfolio the snippets navigate.
//
// The nested records are imported on demand, because the snippet that shows their declarations
// declares them for itself, and a single-type import of a name a snippet declares is a duplicate
// declaration.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.collections.api.list.ImmutableList;
import org.higherkindedj.example.optics.focus.AssetClassFocus;
import org.higherkindedj.example.optics.focus.PortfolioFocus;
import org.higherkindedj.example.optics.focus.PortfolioRiskExample.*;
import org.higherkindedj.example.optics.focus.PositionFocus;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.util.Traversals;

class Fixture {

  // The gate compiles snippets; it never runs them. Building a six-position portfolio here would
  // say nothing about the paths the page is showing.
  static final Portfolio portfolio = sample();

  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}
