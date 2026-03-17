// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
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

  record Department(String name, List<Team> teams) {}

  record Team(String name, Employee lead, List<Employee> members) {}

  record Employee(String name, String email) {}

  sealed interface ContactInfo permits PhoneContact, EmailContact {}

  record PhoneContact(String number) implements ContactInfo {}

  record EmailContact(String address) implements ContactInfo {}

  // --- Lenses (manual for this standalone example) ---

  static final Lens<Department, List<Team>> deptTeamsLens =
      Lens.of(Department::teams, (d, t) -> new Department(d.name(), t));

  static final Lens<Team, Employee> teamLeadLens =
      Lens.of(Team::lead, (t, l) -> new Team(t.name(), l, t.members()));

  static final Lens<Employee, String> employeeEmailLens =
      Lens.of(Employee::email, (e, em) -> new Employee(e.name(), em));

  static final Lens<Employee, String> employeeNameLens =
      Lens.of(Employee::name, (e, n) -> new Employee(n, e.email()));

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

    Fold<Employee, String> nameFold = employeeNameLens.asFold();
    Fold<Employee, String> emailFold = employeeEmailLens.asFold();

    // Combine: get both name and email
    Fold<Employee, String> allStrings = nameFold.plus(emailFold);

    Employee emp = new Employee("Alice", "alice@example.com");
    System.out.println("All strings: " + allStrings.getAll(emp));
    System.out.println("Count: " + allStrings.length(emp));
    System.out.println("First: " + allStrings.preview(emp).orElse("none"));
    System.out.println();
  }

  /** Combine a lens-derived fold with a list fold. */
  private static void demonstrateLensPlusFold() {
    System.out.println("--- Lens.asFold() + Fold: Lead and Members ---");

    Fold<Team, String> leadNameFold = teamLeadLens.asFold().andThen(employeeNameLens.asFold());

    Fold<Team, String> memberNamesFold =
        Fold.<Team, Employee>of(Team::members).andThen(employeeNameLens.asFold());

    // Combine: get lead name AND all member names
    Fold<Team, String> allTeamNames = leadNameFold.plus(memberNamesFold);

    Team team =
        new Team(
            "Platform",
            new Employee("Alice", "alice@co"),
            List.of(new Employee("Bob", "bob@co"), new Employee("Carol", "carol@co")));

    System.out.println("All team member names: " + allTeamNames.getAll(team));
    System.out.println("Total people: " + allTeamNames.length(team));
    System.out.println();
  }

  /** Combine a Traversal-derived Fold with a Lens-derived Fold using plus(). */
  private static void demonstrateTraversalAsFoldPlus() {
    System.out.println("--- Traversal.asFold() + plus(): Lead and All Members ---");

    // Lens-derived fold: the team lead's email (single value)
    Fold<Team, String> leadEmailFold = teamLeadLens.asFold().andThen(employeeEmailLens.asFold());

    // Traversal-derived fold: all member emails (multiple values)
    Lens<Team, List<Employee>> teamMembersLens =
        Lens.of(Team::members, (t, m) -> new Team(t.name(), t.lead(), m));
    Traversal<Team, Employee> membersTraversal =
        teamMembersLens.asTraversal().andThen(Traversals.forList());
    Fold<Team, String> memberEmailsFold =
        membersTraversal.asFold().andThen(employeeEmailLens.asFold());

    // Combine: lead email + all member emails
    Fold<Team, String> allTeamEmails = leadEmailFold.plus(memberEmailsFold);

    Team team =
        new Team(
            "Platform",
            new Employee("Alice", "alice@co"),
            List.of(new Employee("Bob", "bob@co"), new Employee("Carol", "carol@co")));

    System.out.println("Lead email: " + leadEmailFold.getAll(team));
    System.out.println("Member emails: " + memberEmailsFold.getAll(team));
    System.out.println("All team emails (combined): " + allTeamEmails.getAll(team));
    System.out.println("Total emails: " + allTeamEmails.length(team));
    System.out.println();
  }

  /** Use Fold.sum() to combine three or more folds at once. */
  private static void demonstrateSumMultiplePaths() {
    System.out.println("--- Fold.sum(): Multiple Extraction Paths ---");

    Department dept =
        new Department(
            "Engineering",
            List.of(
                new Team(
                    "Backend",
                    new Employee("Alice", "alice@co"),
                    List.of(new Employee("Bob", "bob@co"))),
                new Team(
                    "Frontend",
                    new Employee("Carol", "carol@co"),
                    List.of(new Employee("Dave", "dave@co")))));

    // Build folds to extract emails from different paths
    Fold<Department, Team> teamsFold = Fold.of(Department::teams);

    Fold<Department, String> leadEmails =
        teamsFold.andThen(teamLeadLens.asFold()).andThen(employeeEmailLens.asFold());

    Fold<Department, String> memberEmails =
        teamsFold
            .andThen(Fold.<Team, Employee>of(Team::members))
            .andThen(employeeEmailLens.asFold());

    // Combine using sum
    Fold<Department, String> allEmails = Fold.sum(leadEmails, memberEmails);

    System.out.println("All emails: " + allEmails.getAll(dept));
    System.out.println("Has any @co email: " + allEmails.exists(e -> e.endsWith("@co"), dept));
    System.out.println();
  }

  /** Combine folds over different branches of a sealed interface. */
  private static void demonstrateSealedInterfaceBranches() {
    System.out.println("--- Sealed Interface Branches ---");

    Prism<ContactInfo, PhoneContact> phonePrism =
        Prism.of(c -> c instanceof PhoneContact p ? Optional.of(p) : Optional.empty(), c -> c);
    Prism<ContactInfo, EmailContact> emailPrism =
        Prism.of(c -> c instanceof EmailContact e ? Optional.of(e) : Optional.empty(), c -> c);

    // Extract the "value" from either branch
    Fold<ContactInfo, String> phoneFold =
        phonePrism.asFold().andThen(Fold.of(p -> List.of(p.number())));
    Fold<ContactInfo, String> emailFold =
        emailPrism.asFold().andThen(Fold.of(e -> List.of(e.address())));

    Fold<ContactInfo, String> anyContactValue = phoneFold.plus(emailFold);

    ContactInfo phone = new PhoneContact("555-1234");
    ContactInfo email = new EmailContact("test@example.com");

    System.out.println("Phone contact value: " + anyContactValue.getAll(phone));
    System.out.println("Email contact value: " + anyContactValue.getAll(email));
    System.out.println();
  }

  /** Demonstrate monoid-based aggregation across combined folds. */
  private static void demonstrateMonoidAggregation() {
    System.out.println("--- Monoid Aggregation Across Combined Folds ---");

    record Product(String name, double price, double shippingCost) {}

    Lens<Product, Double> priceLens =
        Lens.of(Product::price, (p, v) -> new Product(p.name(), v, p.shippingCost()));
    Lens<Product, Double> shippingLens =
        Lens.of(Product::shippingCost, (p, v) -> new Product(p.name(), p.price(), v));

    // Combine price and shipping cost folds
    Fold<Product, Double> totalCostFold = priceLens.asFold().plus(shippingLens.asFold());

    Monoid<Double> sumMonoid = Monoids.doubleAddition();

    Product product = new Product("Laptop", 999.99, 15.00);

    double totalCost = totalCostFold.foldMap(sumMonoid, d -> d, product);
    System.out.println("Product: " + product.name());
    System.out.println("Total cost (price + shipping): " + totalCost);
    System.out.println("All cost components: " + totalCostFold.getAll(product));
    System.out.println();
  }
}
