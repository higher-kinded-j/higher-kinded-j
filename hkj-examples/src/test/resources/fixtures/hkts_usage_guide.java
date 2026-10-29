// Fixture for hkj-book/src/hkts/usage-guide.md
//
// The page's runnable code is included from WorkingWithKindBook. What is gated here is the block of
// imports the steps use, and the two checkpoints: one raises an error through a MonadError, and one
// narrows inside a generic method, which javac refuses. This fixture imports what they name.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.math.BigDecimal;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional.OptionalKind;

class Fixture {}
