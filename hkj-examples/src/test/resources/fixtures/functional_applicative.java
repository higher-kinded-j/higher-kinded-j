// Fixture for hkj-book/src/functional/applicative.md
//
// The page shows Applicative's signature, so this fixture supplies the types it names. It must not
// import Applicative itself, because the signature snippet declares it. The page's other code is
// included from compiled examples.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.jspecify.annotations.Nullable;

class Fixture {}
