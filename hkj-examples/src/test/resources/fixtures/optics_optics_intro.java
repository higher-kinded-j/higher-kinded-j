// Fixture for hkj-book/src/optics/optics_intro.md
//
// The chapter opens on a user with a nested address and then shows one snippet per optic, each
// against whatever model suits it. All of them are declared here; a snippet that shows one shadows
// this copy.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.tuple.Tuple;
import org.higherkindedj.hkt.tuple.Tuple2;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateFolds;
import org.higherkindedj.optics.annotations.GenerateIsos;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.util.Affines;

@GenerateLenses
record Street(String name, int number) {}

@GenerateLenses
record Address(Street street, String city) {}

@GenerateLenses
record User(String name, Address address) {}

record Point(int x, int y) {}

@GenerateLenses
record ContactInfo(String email, Optional<String> phone) {}

@GeneratePrisms
sealed interface DomainError permits ShippingError, PaymentError {}

record ShippingError(String message, boolean recoverable) implements DomainError {

  boolean isRecoverable() {
    return recoverable;
  }
}

record PaymentError(String message) implements DomainError {}

@GenerateLenses
record Product(String name, double price) {}

@GenerateLenses
@GenerateFolds
record Order(String id, List<Product> items) {}

@GenerateTraversals
record OrderData(String id, List<String> promoCodes) {}

class Fixture {

  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static final User user = sample();

  static final Lens<User, String> userToStreetName = sample();

  static final DomainError error = sample();

  static final Order order = sample();

  static final OrderData orderData = sample();

  static final Applicative<ValidatedKind.Witness<String>> validatedApplicative = sample();

  void handleRecovery(ShippingError shippingError) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}
