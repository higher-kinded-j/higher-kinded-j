// Fixture for .claude/skills/hkj-mapping/SKILL.md
//
// The skill elides the records its specs map, exactly as the book pages do. Supplying them here means
// the spec is handed to the REAL annotation processor, so a shape the skill teaches cannot stop
// compiling without the build failing.
//
// Every type here is TOP-LEVEL: a nested @GenerateMapping spec joins its enclosing simple names
// (Shop.CustomerMapping -> ShopCustomerMappingImpl), and the skill teaches the top-level names.
//
// Customer is (name, address) rather than (name, email) on purpose. A snippet that declares its own
// type shadows the fixture's, but the fixture's OTHER types keep referring to the name: the nested
// @GenerateAssembly section redeclares `Customer` as (String name, Address address), and
// CustomerMapping - which the build/parse and Invoice snippets need - has to keep compiling against
// whatever `Customer` is in scope. `customer` is left uninitialised for the same reason: the leaf
// prism section redeclares Customer with an EmailAddress component, so no single initialiser could
// construct it in every snippet. The gate compiles; it does not run.
//
// NOTE: the imports below look unused *here*. They are for the snippet this file is spliced into.
// That is why spotless excludes src/test/resources/fixtures (see build.gradle.kts).

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.higherkindedj.hkt.error.ErrorEnvelope;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Getter;
import org.higherkindedj.optics.annotations.GenerateAssembly;
import org.higherkindedj.optics.annotations.GenerateErrorEnvelope;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.GenerateMerge;
import org.higherkindedj.optics.annotations.MapField;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.annotations.UpdateSpec;
import org.higherkindedj.optics.edit.Edits;
import org.higherkindedj.optics.validated.ValidatedPrism;
import org.jspecify.annotations.Nullable;

record EmailAddress(String value) {}

record Team(String name, List<EmailAddress> members, Optional<EmailAddress> lead) {}

record TeamDto(String name, List<String> members, Optional<String> lead) {}

// The record <-> DTO pair the mapping sections map, plus the sibling specs a nested component and an
// empty spec resolve through.
record Address(String zip, String city) {}

record AddressDto(String zip, String city) {}

record Customer(String name, Address address) {}

record CustomerDto(String name, AddressDto address) {}

@GenerateMapping
interface AddressMapping extends MappingSpec<Address, AddressDto> {}

@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {}

// Renaming, and a wire-only derived field.
record Person(String name) {}

record PersonDto(String fullName) {}

record Profile(String first, String last) {}

record ProfileDto(String first, String last, String displayName) {}

// A sealed domain hierarchy and the sealed wire hierarchy it dispatches to.
sealed interface Payment permits Card, Bank {}

record Card(String number) implements Payment {}

record Bank(String iban) implements Payment {}

sealed interface PaymentDto permits CardDto, BankDto {}

record CardDto(String number) implements PaymentDto {}

record BankDto(String iban) implements PaymentDto {}

// @GenerateMerge: N sources -> one target.
record User(String name, String email) {}

// UpdateSpec (sparse PATCH): a bean-shaped request where null means "not provided, leave unchanged".
class UserPatchBean {
  private String name;
  private String email;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }
}

@GenerateMapping
interface UserPatchMapping extends UpdateSpec<User, UserPatchBean> {}

record Account(String iban, int balance) {}

record Settings(boolean darkMode) {}

record Dashboard(String name, String iban, boolean darkMode) {}

// @GenerateAssembly: the record the staged builder is generated for.
@GenerateAssembly
record SignupUser(String name, String email, int age) {}

/** The wire form the assembly sections parse their fields out of. */
record SignupDto(String name, String zip, String city) {}

// @GenerateErrorEnvelope: the reader's sealed error hierarchy and its typed context.
record OrderId(String value) {}

record TraceId(String value) {}

record ProductId(String value) {}

record CardRef(String value) {}

/** The context is records-as-schema: nullable components, an all-absent default. */
record OrderErrorContext(@Nullable OrderId orderId, @Nullable TraceId traceId) {}

@GenerateErrorEnvelope
sealed interface OrderError {
  ErrorEnvelope<OrderErrorContext> envelope();

  default OrderError editContext(UnaryOperator<OrderErrors.ContextBuilder> edit) {
    return OrderErrors.editContext(this, edit);
  }

  record OutOfStock(List<ProductId> products, ErrorEnvelope<OrderErrorContext> envelope)
      implements OrderError {}

  record PaymentDeclined(CardRef card, ErrorEnvelope<OrderErrorContext> envelope)
      implements OrderError {}
}

class Fixture {

  /** Uninitialised on purpose: a snippet redeclares Customer with a different shape. See above. */
  static Customer customer;

  static final SignupDto dto = new SignupDto("Ada", "SW1A 1AA", "London");

  static final User user = new User("Ada", "ada@example.com");
  static final Account account = new Account("GB00", 100);
  static final Settings settings = new Settings(true);

  static final List<ProductId> products = List.of(new ProductId("PROD-1"));
  static final OrderId orderId = new OrderId("ORD-1");
  static final TraceId traceId = new TraceId("trace-1");

  static Validated<NonEmptyList<FieldError>, String> parseName(String raw) {
    return Validated.validNel(raw);
  }

  static Validated<NonEmptyList<FieldError>, String> parseZip(String raw) {
    return raw.isBlank() ? Validated.invalidNel(FieldError.of("not a postcode")) : Validated.validNel(raw);
  }

  static Validated<NonEmptyList<FieldError>, String> parseCity(String raw) {
    return Validated.validNel(raw);
  }
}
