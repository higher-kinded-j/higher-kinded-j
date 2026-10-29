// Fixture for hkj-book/src/hkts/core-concepts.md
//
// The page's runnable code is included from EncodingBook. The quotations of the Witness classes
// are the library's own declarations, and stay unmarked. What is gated here is the refused
// method over a type parameter that takes arguments, and the arity checkpoint's Bifunctor over a
// unary witness, also refused, so this fixture imports the two types that checkpoint names.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.either.EitherKind;

class Fixture {}
