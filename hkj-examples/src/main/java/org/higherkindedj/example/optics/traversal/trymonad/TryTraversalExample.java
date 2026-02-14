// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.traversal.trymonad;

import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedMonad;
import org.higherkindedj.optics.annotations.GenerateTraversals;

/**
 * A runnable example demonstrating the use of a generated Traversal for a field of type {@link
 * org.higherkindedj.hkt.trymonad.Try}.
 */
public class TryTraversalExample {

  @GenerateTraversals
  public record Configuration(String appName, Try<String> dbHostname) {}

  /**
   * An "effectful" function that attempts to ping a database given a hostname. This failable
   * operation returns a {@link Validated} type.
   *
   * @param hostname The hostname to ping.
   * @return A {@code Kind<Validated.Witness<String>, String>} which is either Valid(hostname) or
   *     Invalid("Error...").
   */
  public static Kind<ValidatedKind.Witness<String>, String> pingDb(String hostname) {
    System.out.println("  -> Attempting to ping '" + hostname + "'...");
    if ("db.example.com".equals(hostname)) {
      System.out.println("     ...Success!");
      return VALIDATED.widen(Validated.valid(hostname)); // Return the original hostname on success
    } else {
      System.out.println("     ...FAILED!");
      return VALIDATED.widen(Validated.invalid("Ping failed for " + hostname));
    }
  }

  public static void main(String[] args) {
    // Define a Semigroup for combining String errors.
    final Semigroup<String> stringSemigroup = Semigroups.string("; ");
    // Create the Applicative instance with the error-combining logic.
    Applicative<ValidatedKind.Witness<String>> validatedApplicative =
        ValidatedMonad.instance(stringSemigroup);
    var hostnameTraversal = ConfigurationTraversals.dbHostname();

    System.out.println("--- Running Traversal Scenarios for Try ---");

    // --- Scenario 1: Hostname loaded and DB ping succeeds ---
    Configuration configSuccess = new Configuration("MyApp", Try.success("db.example.com"));
    System.out.println("\nInput: " + configSuccess);

    // The type of the result is Validated<String, Configuration>
    Kind<ValidatedKind.Witness<String>, Configuration> result1 =
        hostnameTraversal.modifyF(TryTraversalExample::pingDb, configSuccess, validatedApplicative);

    Validated<String, Configuration> validatedResult1 = VALIDATED.narrow(result1);
    System.out.println("Result: " + validatedResult1);
    // The inner Try is unchanged because the validation succeeded.
    // Expected: Valid(Configuration[appName=MyApp, dbHostname=Success[value=db.example.com]])

    // --- Scenario 2: Hostname loaded, but DB ping FAILS ---
    Configuration configConnectFail =
        new Configuration("DataService", Try.success("db.invalid.com"));
    System.out.println("\nInput: " + configConnectFail);

    Kind<ValidatedKind.Witness<String>, Configuration> result2 =
        hostnameTraversal.modifyF(
            TryTraversalExample::pingDb, configConnectFail, validatedApplicative);

    Validated<String, Configuration> validatedResult2 = VALIDATED.narrow(result2);
    System.out.println("Result: " + validatedResult2);
    // The traversal fails because the pingDb function returns Invalid.
    // Expected: Invalid(Ping failed for db.invalid.com)

    // --- Scenario 3: Hostname loading FAILED initially ---
    Configuration configLoadFail =
        new Configuration(
            "ReportService", Try.failure(new RuntimeException("Config file not found")));
    System.out.println("\nInput: " + configLoadFail);

    // The traversal function `pingDb` is NOT even called because the Try is a Failure.
    Kind<ValidatedKind.Witness<String>, Configuration> result3 =
        hostnameTraversal.modifyF(
            TryTraversalExample::pingDb, configLoadFail, validatedApplicative);

    Validated<String, Configuration> validatedResult3 = VALIDATED.narrow(result3);
    System.out.println("Result: " + validatedResult3);
    // The result is Valid because the traversal itself didn't fail, it just did nothing.
    // Expected: Valid(Configuration[appName=ReportService,
    // dbHostname=Failure[cause=java.lang.RuntimeException: Config file not found]])
  }
}
