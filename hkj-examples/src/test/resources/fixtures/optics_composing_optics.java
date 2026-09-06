// Fixture for hkj-book/src/optics/composing_optics.md
//
// The page composes one path - form to principal to user to permission name - and validates
// through it, then contrasts `modifyF` with the fluent operations on an order form. Both models
// are declared here; the snippet that shows the first shadows this copy.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;
import static org.higherkindedj.optics.fluent.OpticOps.modifyAllEither;
import static org.higherkindedj.optics.fluent.OpticOps.modifyAllValidated;
import static org.higherkindedj.optics.fluent.OpticOps.modifyMaybe;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.annotations.GenerateTraversals;

@GenerateLenses
record Permission(String name) {}

@GeneratePrisms
sealed interface Principal {}

@GenerateLenses
@GenerateTraversals
record User(String username, List<Permission> permissions) implements Principal {}

record Guest() implements Principal {}

@GenerateLenses
record Form(int formId, Principal principal) {}

record OrderForm(String orderId, List<Double> prices, double discount) {}

// The rules an order form is checked against. The page names them without showing them.
class OrderRules {

  static Validated<String, Double> validatePrice(Double price) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static Either<String, Double> checkPrice(Double price) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static Maybe<Double> tryApplyDiscount(Double discount) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}

// The class the page builds up across its sections, named here so a section that uses one of its
// members compiles on its own. The section that shows it shadows this copy.
class ValidationOptics {

  static Kind<ValidatedKind.Witness<String>, String> validatePermissionName(String name) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static Kind<ValidatedKind.Witness<String>, String> validateUsername(String username) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}

// The runnable example the page closes with, named here for the sections that reference its
// validator by method handle.
class ValidatedTraversalExample {

  static Kind<ValidatedKind.Witness<String>, String> validatePermissionName(String name) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}

class Fixture {

  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static final Set<String> VALID_PERMISSIONS =
      Set.of("PERM_READ", "PERM_WRITE", "PERM_DELETE");

  static final Form form = sample();

  static final Form updatedForm = sample();

  static final List<Form> forms = List.of();

  static final OrderForm orderForm = sample();

  static final Traversal<Form, String> FORM_TO_PERMISSION_NAMES = sample();

  static final Traversal<Form, String> traversal = FORM_TO_PERMISSION_NAMES;

  static final Traversal<OrderForm, Double> ORDER_TO_PRICES = sample();

  static final Lens<OrderForm, Double> ORDER_DISCOUNT = sample();

  static final Applicative<ValidatedKind.Witness<String>> applicative =
      Instances.validated(Semigroups.string("; "));

  static Applicative<ValidatedKind.Witness<String>> getValidatedApplicative() {
    return Instances.validated(Semigroups.string("; "));
  }

  static Kind<ValidatedKind.Witness<String>, String> validatePermissionName(String name) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static Validated<String, Form> validatePermissions(Form toCheck) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static Set<String> allowedPermissionsFor(String username) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}
