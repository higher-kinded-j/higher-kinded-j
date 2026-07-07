// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

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

  public record User(String name, String email) {}

  public record Account(String iban, int balance) {}

  public record Settings(boolean darkMode) {}

  public record EmailAddress(String value) {}

  public record Dashboard(String name, String iban, boolean darkMode) {}

  @GenerateMerge
  public interface DashboardAssembly {
    Dashboard assemble(User user, Account account, Settings settings);
  }

  public record TypedDashboard(String name, EmailAddress email, int balance) {}

  @GenerateMerge
  public interface TypedDashboardAssembly {
    Validated<NonEmptyList<FieldError>, TypedDashboard> assemble(User user, Account account);

    default ValidatedPrism<String, EmailAddress> email() {
      return ValidatedPrism.of(
          raw ->
              raw.contains("@")
                  ? Validated.validNel(new EmailAddress(raw))
                  : Validated.invalidNel(FieldError.of("not an email address")),
          EmailAddress::value);
    }
  }

  public static void main(String[] args) {
    System.out.println("=== Identity Merge Example (lossless, plain return) ===");
    User ada = new User("Ada", "ada@corp.example");
    Account account = new Account("GB29-XXXX", 4200);
    Settings settings = new Settings(true);
    Dashboard dashboard =
        GenerateMergeExampleDashboardAssemblyImpl.INSTANCE.assemble(ada, account, settings);
    System.out.println("Assembled:  " + dashboard);
    System.out.println("Expected: name from User, iban from Account, darkMode from Settings\n");

    System.out.println("=== Validated Merge Example (fallible leaf, located errors) ===");
    System.out.println(
        "Valid:   "
            + GenerateMergeExampleTypedDashboardAssemblyImpl.INSTANCE.assemble(ada, account));
    System.out.println(
        "Invalid: "
            + GenerateMergeExampleTypedDashboardAssemblyImpl.INSTANCE.assemble(
                new User("Bob", "not-an-email"), account));
    System.out.println("Expected: Valid(TypedDashboard...), then Invalid located at \"email\"");
  }

  private GenerateMergeExample() {}
}
