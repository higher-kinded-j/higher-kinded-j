// Fixture for hkj-book/src/functional/monad_zero.md
//
// The page shows a For comprehension over a plain Monad calling when, which javac refuses. These
// imports let that snippet reach the error the page quotes, rather than stopping at a name it
// cannot find.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.util.List;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.instances.Instances;
