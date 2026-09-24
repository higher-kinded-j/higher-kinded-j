// Fixture for hkj-book/src/mapping/self_check.md (see hkj-examples/BOOK-SNIPPETS.md).
// The email leaf and the Customer pair that checkpoints 3 and 6 build on, as the chapter's own
// RecordMappingBook declares them. No spec: each checkpoint declares the one it asks about.
import java.util.Optional;
import java.util.UUID;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.validated.StandardCodecs;
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
