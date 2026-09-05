// Fixture for hkj-book/src/effect/context_security.md
//
// Only the two self-contained declarations are gated - the `ScopedValue` key holder and the
// principal record - so this fixture is imports and nothing else. The rest of the page is servlet
// filter, service and client sketches, and jakarta.servlet is not on this module's classpath.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.security.Principal;
import java.time.Instant;
import java.util.Set;
