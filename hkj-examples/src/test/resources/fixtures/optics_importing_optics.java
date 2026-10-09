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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.ImportOptics;

@ImportOptics({
  LocalDate.class,
  Coordinate.class,
  PaymentInstrument.class,
  TrackingStatus.class,
  Department.class
})
class OpticsImports {}

// An order's settlement, a supporting type beside the chapter cast: the cast's Order holds no
// LocalDate, the external type the page imports optics for.
@GenerateLenses
record Settlement(UUID orderId, LocalDate settledOn, BigDecimal amount) {}

record Coordinate(double lat, double lon) {}

// A payment gateway's SDK type, a guest from outside the chapter's order service: the gateway
// owns it, so the page imports its optics, and it keeps the gateway's names rather than the cast's
// Payment, Card and Bank.
sealed interface PaymentInstrument permits CardInstrument, BankAccount, CryptoWallet {}

record CardInstrument(String token) implements PaymentInstrument {}

record BankAccount(String iban) implements PaymentInstrument {}

record CryptoWallet(String address) implements PaymentInstrument {}

// A courier SDK's enum, a guest for the same reason: the courier owns it, and its constants are
// the courier's tracking states rather than the cast's OrderStatus.
enum TrackingStatus {
  PENDING,
  IN_TRANSIT,
  DELIVERED,
  RETURNED
}

record Employee(String name) {}

record Department(String name, List<Employee> staff) {}

class Fixture {
  static final Settlement settlement =
      new Settlement(
          UUID.fromString("00000000-0000-0000-0000-000000000001"),
          LocalDate.of(2026, 3, 14),
          new BigDecimal("50.00"));
}
