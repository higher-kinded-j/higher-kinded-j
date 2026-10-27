// Fixture for hkj-book/src/hkts/one_line_six_layers.md
//
// The refused shape is the page's own method with one change, so it names the same repository,
// error and cast as OneLinerBook, which the gate's classpath already holds.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import java.util.UUID;
import org.higherkindedj.example.book.foundations.OrderError;
import org.higherkindedj.example.book.foundations.OrderRepository;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderFocus;
import org.higherkindedj.hkt.effect.EitherPath;
