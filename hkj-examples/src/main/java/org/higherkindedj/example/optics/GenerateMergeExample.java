// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.math.BigDecimal;
import org.higherkindedj.example.optics.GenerateMappingExample.CustomerDto;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.annotations.GenerateMerge;
import org.higherkindedj.optics.validated.ValidatedPrism;

/**
 * Demonstrates {@code @GenerateMerge} (issue #613): one target record assembled from several
 * sources, declared entirely by the spec method's signature - no class literals, forward-only,
 * truthful types (a merge with a fallible leaf must declare the {@code Validated} return).
 */
public final class GenerateMergeExample {

  public record Account(String accountId, BigDecimal balance) {}

  public record Settings(boolean darkMode) {}

  public record EmailAddress(String value) {}

  public record Dashboard(String name, String accountId, boolean darkMode) {}

  @GenerateMerge
  public interface DashboardAssembly {
    Dashboard assemble(CustomerDto customer, Account account, Settings settings);
  }

  public record TypedDashboard(String name, EmailAddress email, BigDecimal balance) {}

  @GenerateMerge
  public interface TypedDashboardAssembly {
    Validated<NonEmptyList<FieldError>, TypedDashboard> assemble(
        CustomerDto customer, Account account);

    default ValidatedPrism<String, EmailAddress> email() {
      return ValidatedPrism.of(
          raw ->
              raw.contains("@")
                  ? Validated.validNel(new EmailAddress(raw))
                  : Validated.invalidNel(FieldError.of("not an email address")),
          EmailAddress::value);
    }
  }

  public record Wrapper(CustomerDto customer) {}

  public record ProfileCard(String name, GenerateMappingExample.Customer customer) {}

  /**
   * The customer fill resolves through the sibling {@code @GenerateMapping} spec ({@code
   * GenerateMappingExample.CustomerMapping}) - merge composes with the mapper ecosystem.
   */
  @GenerateMerge
  public interface ProfileCardAssembly {
    Validated<NonEmptyList<FieldError>, ProfileCard> assemble(CustomerDto named, Wrapper wrapper);
  }

  public static void main(String[] args) {
    System.out.println("=== Identity Merge Example (lossless, plain return) ===");
    CustomerDto ada = new CustomerDto("Ada", "ada@corp.example");
    Account account = new Account("ACC001", new BigDecimal("4200.00"));
    Settings settings = new Settings(true);
    Dashboard dashboard =
        GenerateMergeExampleDashboardAssemblyImpl.INSTANCE.assemble(ada, account, settings);
    System.out.println("Assembled:  " + dashboard);
    System.out.println(
        "Expected: name from CustomerDto, accountId from Account, darkMode from Settings\n");

    System.out.println("=== Validated Merge Example (fallible leaf, located errors) ===");
    var typedDashboardAssembly = GenerateMergeExampleTypedDashboardAssemblyImpl.INSTANCE;
    System.out.println("Valid:   " + typedDashboardAssembly.assemble(ada, account));
    System.out.println(
        "Invalid: "
            + typedDashboardAssembly.assemble(new CustomerDto("Bob", "not-an-email"), account));
    System.out.println("Expected: Valid(TypedDashboard...), then Invalid located at \"email\"\n");

    System.out.println("=== Nested Merge Example (fill through a sibling @GenerateMapping) ===");
    var profileCardAssembly = GenerateMergeExampleProfileCardAssemblyImpl.INSTANCE;
    Wrapper wrapper = new Wrapper(new CustomerDto("Grace", "grace@corp.example"));
    System.out.println("Valid:   " + profileCardAssembly.assemble(ada, wrapper));
    System.out.println(
        "Invalid: "
            + profileCardAssembly.assemble(ada, new Wrapper(new CustomerDto("Bob", "nope"))));
    System.out.println(
        "Expected: Valid(ProfileCard...), then Invalid located at \"customer.email\"");
  }

  private GenerateMergeExample() {}
}
