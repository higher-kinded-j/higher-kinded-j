// Fixture for .claude/skills/hkj-mapping/SKILL.md
//
// The skill elides the records its specs map, exactly as the book pages do. Supplying them here means
// the spec is handed to the REAL annotation processor, so a shape the skill teaches cannot stop
// compiling without the build failing.
//
// Every type here is TOP-LEVEL: a nested @GenerateMapping spec joins its enclosing simple names
// (Shop.CustomerMapping -> ShopCustomerMappingImpl), and the skill teaches the top-level names.
//
// Each type keeps one shape: the book's, where the book declares it (BasicsBook, StructureBook,
// SparsePatchBook, MergeBook, StandardCodecsBook, OrderErrorBook). A snippet that redeclares a type
// restates that shape, so the fixture's values construct it in every snippet. The gate compiles; it
// does not run.
//
// NOTE: the imports below look unused *here*. They are for the snippet this file is spliced into.
// That is why spotless excludes src/test/resources/fixtures (see build.gradle.kts).

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.higherkindedj.hkt.error.ErrorEnvelope;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Getter;
import org.higherkindedj.optics.annotations.Flatten;
import org.higherkindedj.optics.annotations.GenerateAssembly;
import org.higherkindedj.optics.annotations.GenerateErrorEnvelope;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.GenerateMerge;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.annotations.MapField;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.annotations.OptionalBridge;
import org.higherkindedj.optics.annotations.UpdateSpec;
import org.higherkindedj.optics.edit.Edits;
import org.higherkindedj.optics.validated.ValidatedPrism;
import org.jspecify.annotations.Nullable;

record EmailAddress(String value) {}

// The record <-> DTO pair the StandardCodecs snippet maps: every conversion is a stock codec.
enum OrderStatus {
  NEW,
  PAID,
  SHIPPED
}

record Order(UUID id, LocalDate placedOn, OrderStatus status, BigDecimal total) {}

record OrderDto(String id, String placedOn, String status, String total) {}

record Rota(String name, List<EmailAddress> members, Optional<EmailAddress> lead) {}

record RotaDto(String name, List<String> members, Optional<String> lead) {}

// The opening spec's pair, which maps by name alone, and the pair the mapping sections map.
// CustomerMapping is also the sibling spec the Invoice snippet's empty spec resolves through.
record Address(String street, String city, String postcode) {}

record AddressDto(String street, String city, String postcode) {}

@GenerateMapping
interface AddressMapping extends MappingSpec<Address, AddressDto> {}

record Customer(String name, EmailAddress email) {}

record CustomerDto(String name, String email) {}

@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {
  default ValidatedPrism<String, EmailAddress> email() {
    return ValidatedPrism.of(
        raw ->
            raw.contains("@")
                ? Validated.validNel(new EmailAddress(raw))
                : Validated.invalidNel(FieldError.of("not an email address")),
        EmailAddress::value);
  }
}

// Renaming: a partner's feed calls the name fullName.
record PartnerCustomerDto(String fullName, String email) {}

// A field renamed and converted at once: a mailing list's export.
record MailingListCustomerDto(String name, String emailAddress) {}

// A wire-only derived field.
record Recipient(String first, String last) {}

record RecipientDto(String first, String last, String displayName) {}

// A flat wire for a nested domain record, which the assembly sections parse their fields out of.
record CollectionPointDto(String name, String street, String city, String postcode) {}

// A sealed domain hierarchy and the sealed wire hierarchy it dispatches to.
sealed interface Payment permits Card, Bank {}

record Card(String pan) implements Payment {}

record Bank(String iban) implements Payment {}

sealed interface PaymentDto permits CardDto, BankDto {}

record CardDto(String pan) implements PaymentDto {}

record BankDto(String iban) implements PaymentDto {}

// UpdateSpec (sparse PATCH): a bean-shaped request where null means "not provided, leave unchanged".
class ContactPatchBean {
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
interface ContactPatchMapping extends UpdateSpec<Customer, ContactPatchBean> {
  default ValidatedPrism<String, EmailAddress> email() {
    return ValidatedPrism.of(
        raw ->
            raw.contains("@")
                ? Validated.validNel(new EmailAddress(raw))
                : Validated.invalidNel(FieldError.of("not an email address")),
        EmailAddress::value);
  }
}

// @GenerateMerge: N sources -> one target.
record Account(String accountId, BigDecimal balance) {}

record Settings(boolean darkMode) {}

record Dashboard(String name, String accountId, boolean darkMode) {}

// @GenerateAssembly: the record the staged builder is generated for.
@GenerateAssembly
record SignupUser(String name, String email, int age) {}

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

  static final Address address = new Address("1 High Street", "Leeds", "LS1 4AP");
  static final Customer customer = new Customer("Ada", new EmailAddress("ada@example.com"));

  static final CollectionPointDto dto =
      new CollectionPointDto("Kirkstall", "1 High Street", "Leeds", "LS1 4AP");

  static final Account account = new Account("ACC001", new BigDecimal("4200.00"));
  static final Settings settings = new Settings(true);

  static final List<ProductId> products = List.of(new ProductId("PROD-1"));
  static final OrderId orderId = new OrderId("ORD-1");
  static final TraceId traceId = new TraceId("trace-1");

  static Validated<NonEmptyList<FieldError>, String> parseName(String raw) {
    return Validated.validNel(raw);
  }

  static Validated<NonEmptyList<FieldError>, String> parseStreet(String raw) {
    return Validated.validNel(raw);
  }

  static Validated<NonEmptyList<FieldError>, String> parseCity(String raw) {
    return Validated.validNel(raw);
  }

  static Validated<NonEmptyList<FieldError>, String> parsePostcode(String raw) {
    return raw.isBlank()
        ? Validated.invalidNel(FieldError.of("not a postcode"))
        : Validated.validNel(raw);
  }
}
