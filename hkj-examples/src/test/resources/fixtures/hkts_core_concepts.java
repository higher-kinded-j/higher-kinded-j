// Fixture for hkj-book/src/hkts/core-concepts.md
//
// The chapter's quotations of Kind, the Witness classes and a KindHelper are the library's own
// declarations, and stay unmarked. What is gated is the Unit entry's worked example.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.optional.OptionalKind;

class Fixture {}
