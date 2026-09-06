// Fixture for hkj-book/src/transformers/when_to_drop_to_transformers.md
//
// One signature, shown as the shape a library publishes.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadReader;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

record AppConfig(String dbUrl, int maxRetries) {}

record ConnectionString(String value) {}

class Fixture {}
