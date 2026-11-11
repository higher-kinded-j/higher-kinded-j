// Fixture for hkj-book/src/monads/free_monad.md
//
// The page's code is included from FreeBook. The one snippet the gate compiles shows a chain that
// javac refuses, so these imports let it reach the error the page quotes rather than an unresolved
// name.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).
import java.math.BigDecimal;
import org.higherkindedj.example.book.foundations.OrderOpKind;
import org.higherkindedj.hkt.free.Free;
