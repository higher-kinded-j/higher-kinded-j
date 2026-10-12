// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;

/**
 * Demonstrates {@link Fold#plus}, {@link Fold#empty}, and {@link Fold#sum} for combining multiple
 * folds into a single query that extracts results from all constituent paths.
 *
 * <p>This example showcases:
 *
 * <ul>
 *   <li>Combining two folds that focus on different fields of the same structure
 *   <li>Composing a lens-derived fold with another fold via {@code plus}
 *   <li>Using {@code Fold.sum()} to combine multiple extraction paths at once
 *   <li>Combining folds over different branches of a sealed interface hierarchy
 *   <li>Monoid-based aggregation across combined folds
 * </ul>
 */
public class FoldPlusCombinationExample {

  // --- Domain Model ---

  record BusinessUnit(String name, List<ProjectTeam> teams) {}

  record ProjectTeam(String name, TeamMember lead, List<TeamMember> members) {}

  record TeamMember(String name, String email) {}

  sealed interface ContactMethod permits PhoneContact, EmailContact {}

  record PhoneContact(String number) implements ContactMethod {}

  record EmailContact(String address) implements ContactMethod {}

  // --- Lenses (manual for this standalone example) ---

  static final Lens<BusinessUnit, List<ProjectTeam>> unitTeamsLens =
      Lens.of(BusinessUnit::teams, (u, t) -> new BusinessUnit(u.name(), t));

  static final Lens<ProjectTeam, TeamMember> teamLeadLens =
      Lens.of(ProjectTeam::lead, (t, l) -> new ProjectTeam(t.name(), l, t.members()));

  static final Lens<TeamMember, String> memberEmailLens =
      Lens.of(TeamMember::email, (m, em) -> new TeamMember(m.name(), em));

  static final Lens<TeamMember, String> memberNameLens =
      Lens.of(TeamMember::name, (m, n) -> new TeamMember(n, m.email()));

  public static void main(String[] args) {
    System.out.println("=== Fold.plus() Combination Example ===\n");

    demonstratBasicPlus();
    demonstrateLensPlusFold();
    demonstrateTraversalAsFoldPlus();
    demonstrateSumMultiplePaths();
    demonstrateSealedInterfaceBranches();
    demonstrateMonoidAggregation();

    System.out.println("=== END ===");
  }

  /** Combine two folds over different fields of the same record. */
  private static void demonstratBasicPlus() {
    System.out.println("--- Basic plus: Combining Two Field Folds ---");

    Fold<TeamMember, String> nameFold = memberNameLens.asFold();
    Fold<TeamMember, String> emailFold = memberEmailLens.asFold();

    // Combine: get both name and email
    Fold<TeamMember, String> allStrings = nameFold.plus(emailFold);

    TeamMember member = new TeamMember("Alice", "alice@example.com");
    System.out.println("All strings: " + allStrings.getAll(member));
    System.out.println("Count: " + allStrings.length(member));
    System.out.println("First: " + allStrings.preview(member).orElse("none"));
    System.out.println();
  }

  /** Combine a lens-derived fold with a list fold. */
  private static void demonstrateLensPlusFold() {
    System.out.println("--- Lens.asFold() + Fold: Lead and Members ---");

    Fold<ProjectTeam, String> leadNameFold = teamLeadLens.asFold().andThen(memberNameLens.asFold());

    Fold<ProjectTeam, String> memberNamesFold =
        Fold.<ProjectTeam, TeamMember>of(ProjectTeam::members).andThen(memberNameLens.asFold());

    // Combine: get lead name AND all member names
    Fold<ProjectTeam, String> allTeamNames = leadNameFold.plus(memberNamesFold);

    ProjectTeam team =
        new ProjectTeam(
            "Platform",
            new TeamMember("Alice", "alice@co"),
            List.of(new TeamMember("Bob", "bob@co"), new TeamMember("Carol", "carol@co")));

    System.out.println("All team member names: " + allTeamNames.getAll(team));
    System.out.println("Total people: " + allTeamNames.length(team));
    System.out.println();
  }

  /** Combine a Traversal-derived Fold with a Lens-derived Fold using plus(). */
  private static void demonstrateTraversalAsFoldPlus() {
    System.out.println("--- Traversal.asFold() + plus(): Lead and All Members ---");

    // Lens-derived fold: the team lead's email (single value)
    Fold<ProjectTeam, String> leadEmailFold =
        teamLeadLens.asFold().andThen(memberEmailLens.asFold());

    // Traversal-derived fold: all member emails (multiple values)
    Lens<ProjectTeam, List<TeamMember>> teamMembersLens =
        Lens.of(ProjectTeam::members, (t, m) -> new ProjectTeam(t.name(), t.lead(), m));
    Traversal<ProjectTeam, TeamMember> membersTraversal =
        teamMembersLens.asTraversal().andThen(Traversals.forList());
    Fold<ProjectTeam, String> memberEmailsFold =
        membersTraversal.asFold().andThen(memberEmailLens.asFold());

    // Combine: lead email + all member emails
    Fold<ProjectTeam, String> allTeamEmails = leadEmailFold.plus(memberEmailsFold);

    ProjectTeam team =
        new ProjectTeam(
            "Platform",
            new TeamMember("Alice", "alice@co"),
            List.of(new TeamMember("Bob", "bob@co"), new TeamMember("Carol", "carol@co")));

    System.out.println("Lead email: " + leadEmailFold.getAll(team));
    System.out.println("Member emails: " + memberEmailsFold.getAll(team));
    System.out.println("All team emails (combined): " + allTeamEmails.getAll(team));
    System.out.println("Total emails: " + allTeamEmails.length(team));
    System.out.println();
  }

  /** Use Fold.sum() to combine three or more folds at once. */
  private static void demonstrateSumMultiplePaths() {
    System.out.println("--- Fold.sum(): Multiple Extraction Paths ---");

    BusinessUnit unit =
        new BusinessUnit(
            "Engineering",
            List.of(
                new ProjectTeam(
                    "Backend",
                    new TeamMember("Alice", "alice@co"),
                    List.of(new TeamMember("Bob", "bob@co"))),
                new ProjectTeam(
                    "Frontend",
                    new TeamMember("Carol", "carol@co"),
                    List.of(new TeamMember("Dave", "dave@co")))));

    // Build folds to extract emails from different paths
    Fold<BusinessUnit, ProjectTeam> teamsFold = Fold.of(BusinessUnit::teams);

    Fold<BusinessUnit, String> leadEmails =
        teamsFold.andThen(teamLeadLens.asFold()).andThen(memberEmailLens.asFold());

    Fold<BusinessUnit, String> memberEmails =
        teamsFold
            .andThen(Fold.<ProjectTeam, TeamMember>of(ProjectTeam::members))
            .andThen(memberEmailLens.asFold());

    // Combine using sum
    Fold<BusinessUnit, String> allEmails = Fold.sum(leadEmails, memberEmails);

    System.out.println("All emails: " + allEmails.getAll(unit));
    System.out.println("Has any @co email: " + allEmails.exists(e -> e.endsWith("@co"), unit));
    System.out.println();
  }

  /** Combine folds over different branches of a sealed interface. */
  private static void demonstrateSealedInterfaceBranches() {
    System.out.println("--- Sealed Interface Branches ---");

    Prism<ContactMethod, PhoneContact> phonePrism =
        Prism.of(c -> c instanceof PhoneContact p ? Optional.of(p) : Optional.empty(), c -> c);
    Prism<ContactMethod, EmailContact> emailPrism =
        Prism.of(c -> c instanceof EmailContact e ? Optional.of(e) : Optional.empty(), c -> c);

    // Extract the "value" from either branch
    Fold<ContactMethod, String> phoneFold =
        phonePrism.asFold().andThen(Fold.of(p -> List.of(p.number())));
    Fold<ContactMethod, String> emailFold =
        emailPrism.asFold().andThen(Fold.of(e -> List.of(e.address())));

    Fold<ContactMethod, String> anyContactValue = phoneFold.plus(emailFold);

    ContactMethod phone = new PhoneContact("555-1234");
    ContactMethod email = new EmailContact("test@example.com");

    System.out.println("Phone contact value: " + anyContactValue.getAll(phone));
    System.out.println("Email contact value: " + anyContactValue.getAll(email));
    System.out.println();
  }

  /** Demonstrate monoid-based aggregation across combined folds. */
  private static void demonstrateMonoidAggregation() {
    System.out.println("--- Monoid Aggregation Across Combined Folds ---");

    // A catalogue entry, and a quote for shipping it, which carries the cost the product does not
    record Product(String sku, String name, BigDecimal price, int stock) {}
    record ShippingQuote(Product product, BigDecimal shippingCost) {}

    Lens<ShippingQuote, Product> productLens =
        Lens.of(ShippingQuote::product, (q, p) -> new ShippingQuote(p, q.shippingCost()));
    Lens<Product, BigDecimal> priceLens =
        Lens.of(Product::price, (p, v) -> new Product(p.sku(), p.name(), v, p.stock()));
    Lens<ShippingQuote, BigDecimal> shippingLens =
        Lens.of(ShippingQuote::shippingCost, (q, v) -> new ShippingQuote(q.product(), v));

    // Combine the product's price, one level down, with the quote's shipping cost
    Fold<ShippingQuote, BigDecimal> totalCostFold =
        productLens.andThen(priceLens).asFold().plus(shippingLens.asFold());

    // Monoids has no BigDecimal sum, so money sums with a monoid of its own
    Monoid<BigDecimal> sumMonoid =
        new Monoid<>() {
          @Override
          public BigDecimal empty() {
            return BigDecimal.ZERO;
          }

          @Override
          public BigDecimal combine(BigDecimal a, BigDecimal b) {
            return a.add(b);
          }
        };

    ShippingQuote quote =
        new ShippingQuote(
            new Product("LAPTOP", "Laptop", new BigDecimal("999.99"), 5), new BigDecimal("15.00"));

    BigDecimal totalCost = totalCostFold.foldMap(sumMonoid, d -> d, quote);
    System.out.println("Product: " + quote.product().name());
    System.out.println("Total cost (price + shipping): " + totalCost);
    System.out.println("All cost components: " + totalCostFold.getAll(quote));
    System.out.println();
  }
}
