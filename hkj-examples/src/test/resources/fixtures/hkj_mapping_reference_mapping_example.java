// Fixture for .claude/skills/hkj-mapping/reference/mapping-example.md
//
// The skill elides the records its specs map, exactly as the book pages do. Supplying them here means
// the spec is handed to the REAL annotation processor, so a shape the skill teaches cannot stop
// compiling without the build failing.
//
// Every type is TOP-LEVEL: a nested @GenerateMapping spec joins its enclosing simple names
// (Shop.CustomerMapping -> ShopCustomerMappingImpl), and the page teaches CustomerMappingImpl.
//
// CustomerMapping is declared here rather than only in the page's own snippet because the page's
// later snippets *depend* on it: the nested Invoice mapping resolves through the sibling spec, so
// the spec has to be in the compilation unit even when the snippet does not restate it. The page's
// "The Spec" snippet declares the same three types again, and its own declarations win.
//
// NOTE: the imports below look unused *here*. They are for the snippet this file is spliced into.
// That is why spotless excludes src/test/resources/fixtures (see build.gradle.kts).

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Getter;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.GenerateMerge;
import org.higherkindedj.optics.annotations.MapField;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.laws.MappingLaws;
import org.higherkindedj.optics.validated.ValidatedPrism;
import org.junit.jupiter.api.Test;

record EmailAddress(String value) {}

record Team(String name, List<EmailAddress> members, Optional<EmailAddress> lead) {}

record TeamDto(String name, List<String> members, Optional<String> lead) {}

record Customer(String name, EmailAddress email) {}

record CustomerDto(String name, String email) {}

/** The page's own spec, restated here for the snippets that use it without declaring it. */
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

// Renaming: the wire calls it fullName, the domain calls it name.
record Person(String name) {}

record PersonDto(String fullName) {}

// Merging several sources into one target.
record User(String name, String email) {}

record Account(String iban, int balance) {}

record Settings(boolean darkMode) {}

record Dashboard(String name, String iban, boolean darkMode) {}

class Fixture {

  static final Customer customer = new Customer("Ada", new EmailAddress("ada@example.com"));

  static final User user = new User("Ada", "ada@example.com");
  static final Account account = new Account("GB00", 100);
  static final Settings settings = new Settings(true);
}
