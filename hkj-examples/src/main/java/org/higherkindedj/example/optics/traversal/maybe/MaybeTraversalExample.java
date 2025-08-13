// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.traversal.maybe;

import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedMonad;
import org.higherkindedj.optics.annotations.GenerateTraversals;

/**
 * A runnable example demonstrating the use of a generated Traversal for a field of type {@link
 * org.higherkindedj.hkt.maybe.Maybe}.
 */
public class MaybeTraversalExample {

  /** A simple record representing a device owner. */
  public record Owner(String name) {}

  /**
   * An immutable Device record. A device must have a serial number, but it may not have an assigned
   * owner (represented by {@code Maybe<Owner>}). We use {@code @GenerateTraversals} to generate the
   * optics for this record.
   */
  @GenerateTraversals
  public record Device(String serialNumber, Maybe<Owner> owner) {}

  /**
   * An "effectful" function that checks the status of an owner. For this example, we'll say an
   * owner is "Active" if their name is not empty. The function returns a {@link Validated} type to
   * make success/failure explicit.
   *
   * @param owner The Owner to check.
   * @return A {@code Kind<Validated.Witness<String>, Owner>} which is either Valid(owner) or
   *     Invalid("Error...").
   */
  public static Kind<ValidatedKind.Witness<String>, Owner> checkOwnerStatus(Owner owner) {
    if (owner != null && owner.name() != null && !owner.name().isEmpty()) {
      return VALIDATED.widen(Validated.valid(owner)); // Success
    } else {
      return VALIDATED.widen(Validated.invalid("Owner account is inactive")); // Failure
    }
  }

  public static void main(String[] args) {
    // 1. Setup: We need an Applicative for our effect type (Validated).
    // Define a Semigroup for combining String errors.
    final Semigroup<String> stringSemigroup = Semigroups.string("; ");
    // Create the Applicative instance with the error-combining logic.
    Applicative<ValidatedKind.Witness<String>> validatedApplicative =
        ValidatedMonad.instance(stringSemigroup);

    // 2. Get the generated traversal for the 'owner' field.
    var ownerTraversal = DeviceTraversals.owner();

    System.out.println("--- Running Traversal Scenarios for Maybe ---");

    // --- Scenario 1: A device with an active owner ---
    Device deviceWithActiveOwner = new Device("SN-001", Maybe.just(new Owner("Magnus")));
    System.out.println("\nInput: " + deviceWithActiveOwner);

    // Use the traversal to apply the status check to the owner field.
    Kind<ValidatedKind.Witness<String>, Device> result1 =
        ownerTraversal.modifyF(
            MaybeTraversalExample::checkOwnerStatus, deviceWithActiveOwner, validatedApplicative);

    Validated<String, Device> validatedResult1 = VALIDATED.narrow(result1);
    System.out.println("Result: " + validatedResult1);
    // Expected: Valid(Device[serialNumber=SN-001, owner=Just[value=Owner[name=Magnus]]])

    // --- Scenario 2: A device with an inactive owner ---
    Device deviceWithInactiveOwner = new Device("SN-002", Maybe.just(new Owner("")));
    System.out.println("\nInput: " + deviceWithInactiveOwner);

    Kind<ValidatedKind.Witness<String>, Device> result2 =
        ownerTraversal.modifyF(
            MaybeTraversalExample::checkOwnerStatus, deviceWithInactiveOwner, validatedApplicative);

    Validated<String, Device> validatedResult2 = VALIDATED.narrow(result2);
    System.out.println("Result: " + validatedResult2);
    // Expected: Invalid(Owner account is inactive)

    // --- Scenario 3: A device with no owner ---
    Device deviceWithNoOwner = new Device("SN-003", Maybe.nothing());
    System.out.println("\nInput: " + deviceWithNoOwner);

    // The traversal function is NOT called because the Maybe is Nothing.
    Kind<ValidatedKind.Witness<String>, Device> result3 =
        ownerTraversal.modifyF(
            MaybeTraversalExample::checkOwnerStatus, deviceWithNoOwner, validatedApplicative);

    Validated<String, Device> validatedResult3 = VALIDATED.narrow(result3);
    System.out.println("Result: " + validatedResult3);
    // Expected: Valid(Device[serialNumber=SN-003, owner=Nothing])
  }
}
