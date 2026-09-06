// Fixture for hkj-book/src/transformers/mtl_state.md
//
// The page writes counter and cart operations against `MonadState` and runs them under StateT over
// Id. The outer monad is declared here; the state records are declared by the snippets themselves.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.id;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadState;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.state_t.StateTKind;
import org.higherkindedj.hkt.state_t.StateTMonadState;

record Counter(int count, int total) {}

class Fixture {

  static final Monad<IdKind.Witness> idMonad = Instances.monad(id());
}
