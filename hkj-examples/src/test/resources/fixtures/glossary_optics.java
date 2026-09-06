// Fixture for hkj-book/src/glossary/optics.md
//
// Every generator the glossary names is exercised for real here - lenses, folds, setters, prisms,
// assemblies, error envelopes, mappings and merges - so the page's snippets navigate through
// genuinely generated optics rather than a stand-in. A snippet that declares a record for itself
// shadows this copy, and the processor generates from that.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.optics.edit.Edit.modify;
import static org.higherkindedj.optics.edit.Edit.modifyIfPresent;
import static org.higherkindedj.optics.edit.Edit.parseIfPresent;
import static org.higherkindedj.optics.edit.Edit.setIfPresent;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Update;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.error.ErrorEnvelope;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.time.TimeSource;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.At;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Setter;
import org.higherkindedj.optics.annotations.GenerateAssembly;
import org.higherkindedj.optics.annotations.GenerateErrorEnvelope;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateFolds;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.GenerateMerge;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.annotations.GenerateSetters;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.at.AtInstances;
import org.higherkindedj.optics.util.Traversals;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.annotations.UpdateSpec;
import org.higherkindedj.optics.validated.StandardCodecs;
import org.higherkindedj.optics.validated.ValidatedPrism;
import java.time.LocalDate;
import java.util.UUID;
import org.higherkindedj.optics.edit.Edits;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.laws.MappingLaws;
import org.jspecify.annotations.Nullable;

record OrderId(String value) {}

record TraceId(String value) {}

record ProductId(String value) {}

record Email(String value) {

  static Validated<NonEmptyList<FieldError>, Email> parse(String raw) {
    return raw != null && raw.contains("@")
        ? Validated.validNel(new Email(raw))
        : Validated.invalidNel(FieldError.of("not an email address"));
  }
}

record Name(String value) {}

@GenerateLenses
@GenerateFocus
record Address(String street, String city, String postcode) {}

@GenerateLenses
@GenerateFocus
@GenerateSetters
record Company(String name, Address address, List<Department> departments) {}

@GenerateLenses
@GenerateFocus
@GenerateSetters
record Employee(String name, String email, Company company) {}

@GenerateSetters
record Department(String name, List<Employee> employees) {}

@GenerateLenses
@GenerateSetters
record LineItem(String sku, BigDecimal price) {}

@GenerateLenses
@GenerateFocus
@GenerateSetters
record Order(
    String orderNumber,
    String email,
    Email contact,
    String sku,
    int quantity,
    List<LineItem> items) {}

@GenerateTraversals
record Basket(String id, List<LineItem> items) {}

record Player(String name, int score, boolean active) {

  boolean isActive() {
    return active;
  }
}

@GenerateFolds
record Team(String name, List<Player> players) {}

@GenerateFolds
record League(String name, List<Team> teams) {}

@GeneratePrisms
sealed interface PaymentMethod {
  record CreditCard(String number) implements PaymentMethod {}

  record BankTransfer(String iban) implements PaymentMethod {}
}

record EmailAddress(String value) {

  @Override
  public String toString() {
    return value;
  }
}

record OrderDto(String id, String placed) {}

record PatchableUser(String name) {}

/** A sparse PATCH wire is bean-shaped: null means "not provided". */
class UserPatchDto {

  private String name;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}

record Person(String name, String email) {}

record PersonDto(String name, String email) {}

@GenerateMapping
interface PersonMapping extends MappingSpec<Person, PersonDto> {}

@GenerateLenses
record Settings(Map<String, Integer> preferences) {}

@GenerateLenses
record UserProfile(String name, Settings settings) {}

record Account(String id) {}

/** Mergeable: every component is filled by a same-named component of a source. */
record Dashboard(String name, String id, Map<String, Integer> preferences) {}

@GenerateAssembly
@GenerateFocus
record User(String name, String email, Address address) {}

record UserDto(String name, String email, Address address) {}

interface UserService {
  EitherPath<Error, User> findById(String id);
}

interface EmployeeService {
  EitherPath<Error, Employee> findById(String id);
}

record Error(String message) {}

record OrderErrorContext(@Nullable OrderId orderId, @Nullable TraceId traceId) {}

@GenerateErrorEnvelope
sealed interface OrderError {
  ErrorEnvelope<OrderErrorContext> envelope();

  record OutOfStock(List<ProductId> products, ErrorEnvelope<OrderErrorContext> envelope)
      implements OrderError {}
}

record PatchRequest(String orderNumber, String email, Integer qtyDelta) {}

class Fixture {

  // Edit takes a Setter or a FocusPath, so the constants are the generated focuses.
  static final FocusPath<Order, String> EMAIL = OrderFocus.email();

  static final FocusPath<Order, String> SKU = OrderFocus.sku();

  static final FocusPath<Order, String> ORDER_NUMBER = OrderFocus.orderNumber();

  static final PatchRequest req = new PatchRequest("ORD-1", "a@b.test", 2);

  static final FocusPath<Order, Integer> QUANTITY = OrderFocus.quantity();

  static final FocusPath<Order, Email> CONTACT = OrderFocus.contact();

  static final Order order = sample();

  static final Company company = sample();

  static final LineItem lineItem = new LineItem("SKU-1", BigDecimal.ONE);

  static final Basket basket = new Basket("b-1", List.of(lineItem));

  static final UserDto dto = sample();

  static final UserService userService = sample();

  static final EmployeeService employeeService = sample();

  static final String id = "u-1";

  static final TimeSource timeSource = TimeSource.system();

  static final OrderErrorContext context = new OrderErrorContext(null, null);

  static final List<ProductId> products = List.of(new ProductId("p-1"));

  static final OrderId orderId = new OrderId("ORD-1");

  static final TraceId traceId = new TraceId("t-1");

  static final Employee employee = sample();

  static final Employee originalEmployee = employee;

  static final League league = sample();

  static final PaymentMethod payment = new PaymentMethod.CreditCard("4242");

  static final Person person = new Person("Alice", "a@b.test");

  static final PersonDto validDto = new PersonDto("Alice", "a@b.test");

  static final PersonDto invalidDto = new PersonDto(null, "nope");

  static final EmailAddress addr = new EmailAddress("a@b.test");

  static final PatchableUser current = sample();

  static final UserPatchDto patchDto = sample();

  static Validated<NonEmptyList<FieldError>, EmailAddress> parseEmailAddress(String raw) {
    return raw != null && raw.contains("@")
        ? Validated.validNel(new EmailAddress(raw.strip()))
        : Validated.invalidNel(FieldError.of("not an email address"));
  }

  static final Account account = new Account("acc-1");

  static final Settings settings = new Settings(Map.of());

  static final Dashboard dashboard = sample();

  static final Lens<UserProfile, Settings> settingsLens = UserProfileLenses.settings();

  static final User user = sample();

  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static EitherPath<Error, User> loadUser(String id) {
    return userService.findById(id);
  }

  static Validated<NonEmptyList<FieldError>, Name> parseName(String raw) {
    return Validated.validNel(new Name(raw));
  }

  static Validated<NonEmptyList<FieldError>, Email> parseEmail(String raw) {
    return Email.parse(raw);
  }
}
