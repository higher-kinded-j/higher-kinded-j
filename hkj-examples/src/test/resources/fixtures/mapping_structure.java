// Fixture for hkj-book/src/mapping/structure.md (see hkj-examples/BOOK-SNIPPETS.md).
// The "Across modules" snippet declares only the downstream spec. The pair it nests, its
// EmailAddress leaf and the spec mapping that pair stand in for :orders-api, and the invoice pair
// is :billing's own (in the gate they share the compilation, which resolves identically).
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.validated.ValidatedPrism;

record EmailAddress(String value) {}

final class EmailCodecs {
  static final ValidatedPrism<String, EmailAddress> EMAIL =
      ValidatedPrism.of(
          raw ->
              raw.contains("@")
                  ? Validated.validNel(new EmailAddress(raw))
                  : Validated.invalidNel(FieldError.of("not an email address")),
          EmailAddress::value);

  private EmailCodecs() {}
}

record Customer(String name, EmailAddress email) {}

record CustomerDto(String name, String email) {}

@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {
  default ValidatedPrism<String, EmailAddress> email() {
    return EmailCodecs.EMAIL;
  }
}

record Invoice(String id, Customer customer) {}

record InvoiceDto(String id, CustomerDto customer) {}
