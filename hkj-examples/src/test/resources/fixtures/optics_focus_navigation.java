// Fixture for hkj-book/src/optics/focus_navigation.md
//
// The page includes its examples from NavigationBook; the fences left are
// annotation declarations for controlling navigator generation. They name the
// chapter cast's records, declared here with the cast's components, and the
// annotation processor generates their *Focus companions during snippet
// compilation. The depth fence declares Order itself, so it is not declared here, and the
// refused-hop fence uses the cast's own OrderFocus through the on-demand import. The
// generic-target fences declare their own Revision and ContactHistory, and type a chain
// through the generated navigator with FocusPath.
//
// NOTE: imports in a fixture serve the snippet this file is spliced into (this
// one also happens to use its imports itself). Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.higherkindedj.example.book.optics.cast.*;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.focus.FocusPath;

@GenerateFocus
record EmailAddress(String value) {}

@GenerateFocus(generateNavigators = true)
record Customer(String name, EmailAddress email) {}

@GenerateFocus
record LineItem(String sku, Integer quantity, BigDecimal price) {}

enum OrderStatus {
  NEW,
  PAID,
  SHIPPED
}

@GenerateFocus
record Address(String street, String city, String postcode) {}
