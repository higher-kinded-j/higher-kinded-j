// Fixture for .claude/skills/hkj-optics/SKILL.md
//
// The skill's examples elide the records they navigate, as examples should. Declaring them here and
// annotating them FOR REAL means UserFocus, CompanyFocus, OrderFocus and OrderLenses are the
// genuinely generated companions during the gate: the skill's navigator chains, `Edit` leaves and
// `FocusPath.of(lens, "label")` calls are checked against generated code, not against a stand-in
// for it. A skill is read by an assistant that generates code from it, so a wrong snippet here
// becomes code that does not build in someone's project.
//
// The records are TOP-LEVEL, not nested in Fixture: a nested record makes the processor join the
// enclosing names (`FixtureOrderFocus`), which is not what the skill says.
//
// NOTE: the imports below look unused *here*. They are for the snippet this file is spliced into.
// That is why spotless excludes src/test/resources/fixtures (see build.gradle.kts).

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Update;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.edit.Edit;
import org.higherkindedj.optics.edit.Edits;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.annotations.ImportOptics;
import org.higherkindedj.optics.annotations.InstanceOf;
import org.higherkindedj.optics.annotations.OpticsSpec;
import org.higherkindedj.optics.validated.ValidatedPrism;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

/** The address of the "Setting Up Optics" walkthrough. */
@GenerateLenses
@GenerateFocus(generateNavigators = true)
record Address(String city, String postcode, Optional<String> floor) {}

/** The record the walkthrough navigates from. */
@GenerateLenses
@GenerateFocus(generateNavigators = true)
record User(String name, Address address) {}

/** The three levels the cross-type navigator chain walks: Company -> Department -> Employee. */
@GenerateLenses
@GenerateFocus(generateNavigators = true)
record Employee(String name) {}

@GenerateLenses
@GenerateFocus(generateNavigators = true)
record Department(List<Employee> employees) {}

@GenerateLenses
@GenerateFocus(generateNavigators = true)
record Company(List<Department> departments) {}

/** The structure the Edits examples patch. */
@GenerateLenses
@GenerateFocus
record Order(String id, String email, String sku, int quantity) {}

/** A REST PATCH request: every field is optional, so a null means "not supplied". */
record OrderRequest(@Nullable String email, @Nullable Integer qtyDelta, @Nullable String sku) {}

/** The domain value a ValidatedPrism parses into, and renders back out of. */
record EmailAddress(String value) {
  String render() {
    return value;
  }
}

/**
 * The reader's own smart constructor. It is generic in its result because the skill uses {@code
 * Email::parse} twice, against two different targets: the {@code String} field an {@code Edit}
 * writes to, and the {@code EmailAddress} a {@code ValidatedPrism} parses into. Only the shape of
 * the parse function is load-bearing for a compile gate, so the stub commits to neither.
 */
final class Email {
  static <A> Validated<NonEmptyList<FieldError>, A> parse(String raw) {
    throw new UnsupportedOperationException("the reader supplies this");
  }
}

class Fixture {

  static final User user = new User("Alice", new Address("London", "N1 1AA", Optional.empty()));

  static final Company company = new Company(List.of(new Department(List.of(new Employee("Bo")))));

  static final Order order = new Order("o-1", "ALICE@Example.com ", " SKU-1 ", 2);

  static final OrderRequest request = new OrderRequest("bob@example.com", 3, "SKU-2");

  static final String raw = "bob@example.com";

  static final EmailAddress address = new EmailAddress("bob@example.com");
}
