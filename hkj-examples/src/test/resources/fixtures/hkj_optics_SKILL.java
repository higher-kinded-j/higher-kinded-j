// Fixture for .claude/skills/hkj-optics/SKILL.md
//
// The skill's examples elide the records they navigate, as examples should. Declaring them here and
// annotating them FOR REAL means ShopperFocus, OrgChartFocus, LineItemFocus and LineItemLenses are
// the genuinely generated companions during the gate: the skill's navigator chains, `Edit` leaves
// and `FocusPath.of(lens, "label")` calls are checked against generated code, not against a
// stand-in for it. The org chart and the order line are the book's own, with its components. A
// skill is read by an assistant that generates code from it, so a wrong snippet here becomes code
// that does not build in someone's project.
//
// The records are TOP-LEVEL, not nested in Fixture: a nested record makes the processor join the
// enclosing names (`FixtureLineItemFocus`), which is not what the skill says.
//
// NOTE: the imports below look unused *here*. They are for the snippet this file is spliced into.
// That is why spotless excludes src/test/resources/fixtures (see build.gradle.kts).

import java.math.BigDecimal;
import java.math.RoundingMode;
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
@GenerateFocus
record Address(String street, String city, String postcode) {}

/** The record the walkthrough navigates from. */
@GenerateLenses
@GenerateFocus
record Shopper(String name, Address address, Optional<String> nickname) {}

/** The three levels the cross-type navigator chain walks: OrgChart -> OrgUnit -> OrgMember. */
@GenerateLenses
@GenerateFocus
record OrgMember(String name, int age, Optional<String> email, Integer salary) {}

@GenerateLenses
@GenerateFocus
record OrgUnit(String name, List<OrgMember> members) {}

@GenerateLenses
@GenerateFocus
record OrgChart(String name, List<OrgUnit> units) {}

/** The order line the Edits examples patch. */
@GenerateLenses
@GenerateFocus
record LineItem(String sku, Integer quantity, BigDecimal price) {}

/** A sparse PATCH of one order line: a null component means "not supplied", not "set to null". */
record LineItemPatch(@Nullable String sku, @Nullable Integer qtyDelta, @Nullable String price) {}

/** The boundary parser the PATCH hands to {@code parseIfPresent}. */
final class PriceParser {
  static Validated<NonEmptyList<FieldError>, BigDecimal> parse(String raw) {
    try {
      BigDecimal price = new BigDecimal(raw.strip());
      return price.signum() >= 0
          ? Validated.validNel(price)
          : Validated.invalidNel(FieldError.of("not a price"));
    } catch (NumberFormatException e) {
      return Validated.invalidNel(FieldError.of("not a price"));
    }
  }

  private PriceParser() {}
}

/** The domain value a ValidatedPrism parses into, and renders back out of. */
record EmailAddress(String value) {}

/** The reader's own smart constructor, which the ValidatedPrism example builds on. */
final class EmailParser {
  static Validated<NonEmptyList<FieldError>, EmailAddress> parse(String raw) {
    String email = raw.strip();
    return email.contains("@")
        ? Validated.validNel(new EmailAddress(email))
        : Validated.invalidNel(FieldError.of("not an email address"));
  }

  private EmailParser() {}
}

class Fixture {

  static final Shopper shopper =
      new Shopper("Alice", new Address("1 High Street", "London", "N1 1AA"), Optional.empty());

  static final OrgChart company =
      new OrgChart(
          "Acme",
          List.of(
              new OrgUnit(
                  "Platform", List.of(new OrgMember("Bo", 30, Optional.empty(), 50_000)))));

  static final LineItem line = new LineItem(" lamp ", 1, new BigDecimal("40"));

  static final LineItemPatch patch = new LineItemPatch(" LAMP-2 ", 3, "45.00");

  static final String raw = "bob@example.com";

  static final EmailAddress address = new EmailAddress("bob@example.com");
}
