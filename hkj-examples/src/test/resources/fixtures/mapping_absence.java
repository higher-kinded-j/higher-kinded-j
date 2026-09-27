// Fixture for hkj-book/src/mapping/absence.md (see hkj-examples/BOOK-SNIPPETS.md).
// The email leaf the refused CustomerProfile pair's second email parses through, as the chapter's
// own BasicsBook declares it. No spec: the snippet declares the one it asks about.
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
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
