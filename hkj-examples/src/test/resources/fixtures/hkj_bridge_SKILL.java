// Fixture for .claude/skills/hkj-bridge/SKILL.md
//
// The skill is about the crossings between the optics world and the effects world, so its snippets are
// the crossing itself and nothing else: the records, the prisms and the fetch are elided. Supplying
// them here means every `toXxxPath` / `.focus(...)` / `Edits` call is compiled against the real
// library, with the real annotation processor generating UserFocus, OrderFocus and friends -- so a
// renamed bridge method fails the build instead of quietly misleading a reader who is generating code
// from this file. It already caught a `.employees().email()` navigator chain written against paths that
// are not navigators.
//
// The records are TOP-LEVEL: the processor joins enclosing names, so a nested `User` would generate
// `FixtureUserFocus`, not the `UserFocus` the skill teaches.
//
// `Error` in the snippets is java.lang.Error, which is a perfectly good (if unusual) left type; the
// page uses it as a stand-in for the reader's own error, and nothing here needs to change that.
//
// NOTE: the imports below look unused *here*. They are for the snippet this file is spliced into.
// That is why spotless excludes src/test/resources/fixtures (see build.gradle.kts).

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Update;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IdPath;
import org.higherkindedj.hkt.effect.ListPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.StreamPath;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.hkt.expression.ForPath;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.edit.Edit;
import org.higherkindedj.optics.edit.Edits;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.validated.ValidatedPrism;

/** The reader's own domain. */
@GenerateLenses
@GenerateFocus
record Address(String street, String postcode) {}

@GenerateLenses
@GenerateFocus
record User(String name, Optional<String> email, Address address) {}

@GenerateLenses
@GenerateFocus
record Employee(String name, Optional<String> email) {}

@GenerateLenses
@GenerateFocus
record Department(String name, Optional<Employee> manager, List<Employee> employees) {}

@GenerateLenses
@GenerateFocus
record Company(String name, List<User> employees, List<Department> departments) {}

/** The parsed types the ValidatedPrism examples produce. */
record EmailAddress(String value) {}

record Customer(String name, EmailAddress email) {}

/** The wire shape the accumulating assembly parses from. */
record CustomerDto(String name, String email) {}

record Money(BigDecimal amount) {
  Money multiply(double factor) {
    return new Money(amount.multiply(BigDecimal.valueOf(factor)));
  }
}

enum OrderStatus {
  DRAFT,
  CONFIRMED
}

record GeoLocation(double latitude, double longitude) {}

record Item(String sku, int quantity) {}

@GenerateLenses
@GenerateFocus
record Order(
    String id, String notes, String email, String postcode, OrderStatus status, Money total) {}

record OrderResult(User user, Address address) {}

/** The reader's own exception for a missing optional. */
class MissingEmailException extends RuntimeException {
  MissingEmailException() {
    super("no email");
  }
}

class Fixture {

  static User user;
  static Company company;
  static Department department;
  static Order order;
  static EmailAddress email;
  static CustomerDto dto;
  static String rawEmail = "alice@example.com";
  static String userId = "u-1";
  static String id = "u-1";
  static List<Item> items = List.of();

  static MaybePath<User> userMaybe = Path.nothing();
  static TryPath<User> userTry = Path.failure(new MissingEmailException());
  static EitherPath<Error, User> userResult = Path.left(new Error("not found"));

  static ValidatedPrism<String, String> namePrism =
      ValidatedPrism.of(
          raw ->
              raw.isBlank()
                  ? Validated.invalidNel(FieldError.of("must not be blank"))
                  : Validated.validNel(raw),
          name -> name);

  static ValidatedPrism<String, EmailAddress> emailPrism =
      ValidatedPrism.of(
          raw ->
              raw.contains("@")
                  ? Validated.validNel(new EmailAddress(raw))
                  : Validated.invalidNel(FieldError.of("must contain @")),
          EmailAddress::value);

  static ValidatedPrism<String, String> postcodePrism =
      ValidatedPrism.of(
          raw ->
              raw.isBlank()
                  ? Validated.invalidNel(FieldError.of("must not be blank"))
                  : Validated.validNel(raw),
          postcode -> postcode);

  EitherPath<Error, User> fetchUser(String id) {
    return Path.right(user);
  }

  EitherPath<Error, String> validatePostcode(String postcode) {
    return Path.right(postcode);
  }

  EitherPath<Error, Address> validateAddress(Address address) {
    return Path.right(address);
  }

  EitherPath<Error, GeoLocation> geocodeAddress(Address address) {
    return Path.right(new GeoLocation(0, 0));
  }

  Order createOrder(GeoLocation location, List<Item> lines) {
    return order;
  }

  OrderResult createOrder(User forUser, Address at) {
    return new OrderResult(forUser, at);
  }
}
