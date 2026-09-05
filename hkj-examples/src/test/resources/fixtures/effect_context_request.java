// Fixture for hkj-book/src/effect/context_request.md
//
// Only the two self-contained utility classes are gated - the `ScopedValue` key holder and the
// trace-id generator - so this fixture is imports and nothing else. The rest of the page is
// handler, service and client sketches that read routers, repositories, publishers and HTTP
// clients they never declare; they show a shape rather than code to copy.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
