// Fixture for hkj-book/src/tutorials/capstone/capstone_journey.md
//
// The capstone quotes the checkout step the capstone tutorial builds, on the order-service cast.
// The repository it reads and saves through, the id it looks up and the discount it applies are
// declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.higherkindedj.example.book.foundations.InMemoryOrderRepository;
import org.higherkindedj.example.book.foundations.OrderError;
import org.higherkindedj.example.book.foundations.OrderRepository;
import org.higherkindedj.example.book.optics.cast.OrderFocus;
import org.higherkindedj.example.book.optics.cast.OrderStatus;

class Fixture {

  static final UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");

  static final OrderRepository orders = InMemoryOrderRepository.of();

  static final UnaryOperator<BigDecimal> DISCOUNT = price -> price;
}
