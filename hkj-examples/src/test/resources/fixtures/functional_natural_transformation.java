// Fixture for hkj-book/src/functional/natural_transformation.md
//
// The page shows a lambda written as a Natural, which javac refuses. These imports let that
// snippet reach the error the page quotes, rather than stopping at a name it cannot find.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import org.higherkindedj.example.book.foundations.natural.NaturalBook;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.maybe.MaybeKind;
