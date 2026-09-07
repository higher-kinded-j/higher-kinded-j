// Fixture for hkj-book/src/optics/focus_external_bridging.md
//
// The page's bridge is real code: `org.higherkindedj.example.optics.bridge` holds the domain, the
// external Address and the CompanyBridge the snippets read, and the gate compiles against the
// module's own main sources. The imports below are on-demand so that the snippet which shows the
// domain records can declare them itself.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.math.BigDecimal;
import java.util.List;
import java.util.function.UnaryOperator;
import org.higherkindedj.example.optics.bridge.*;
import org.higherkindedj.example.optics.bridge.domain.*;
import org.higherkindedj.example.optics.bridge.external.*;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.laws.LensLaws;
import org.junit.jupiter.api.Test;

class Fixture {

  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static final Company acme = sample();
}
