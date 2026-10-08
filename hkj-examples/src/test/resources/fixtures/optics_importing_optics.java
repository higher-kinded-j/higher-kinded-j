// Fixture for hkj-book/src/optics/importing_optics.md
//
// The page imports optics for external types. @ImportOptics accepts a package
// or a type, so the fixture carries the import declaration on a holder class
// and the page's snippets use the generated companions. The "external" types the
// page's auto-detection sections show are declared here too, so a section's
// snippet that shows one shadows this copy and still gets its companion.
//
// NOTE: imports in a fixture serve the snippet this file is spliced into (this
// one also happens to use its imports itself). Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import java.time.LocalDate;
import java.util.List;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.ImportOptics;

@ImportOptics({
  LocalDate.class,
  Coordinate.class,
  PaymentMethod.class,
  OrderStatus.class,
  Department.class
})
class OpticsImports {}

@GenerateLenses
record Order(String id, LocalDate orderDate, List<String> lines) {}

record Coordinate(double lat, double lon) {}

sealed interface PaymentMethod permits CreditCard, BankTransfer, Crypto {}

record CreditCard(String number) implements PaymentMethod {}

record BankTransfer(String iban) implements PaymentMethod {}

record Crypto(String wallet) implements PaymentMethod {}

enum OrderStatus {
  PENDING,
  SHIPPED,
  DELIVERED,
  CANCELLED
}

record Employee(String name) {}

record Department(String name, List<Employee> staff) {}

class Fixture {
  static final Order order = new Order("ORD-1", LocalDate.of(2026, 3, 14), List.of("widget"));
}
