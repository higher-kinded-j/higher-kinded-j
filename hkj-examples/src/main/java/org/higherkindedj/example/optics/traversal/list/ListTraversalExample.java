// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.traversal.list;

import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.List;
import java.util.Set;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedMonad;
import org.higherkindedj.optics.annotations.GenerateTraversals;

/**
 * A runnable example demonstrating the use of a generated Traversal for a field of type {@link
 * java.util.List}.
 */
public class ListTraversalExample {

  /**
   * An immutable Project record. A project has a name and a list of team members. We use
   * {@code @GenerateTraversals} to generate optics for this record.
   */
  @GenerateTraversals
  public record Project(String name, List<String> teamMembers) {}

  // A simple "database" of valid users.
  private static final Set<String> VALID_USERS = Set.of("Alice", "Bob", "Charlie");

  /**
   * An "effectful" function that looks up a user by name. It returns a {@link Validated} type to
   * make success and failure explicit.
   *
   * @param username The name to look up.
   * @return A {@code Kind<Validated.Witness<String>, String>} which is either Valid(username) or
   *     Invalid("Error...").
   */
  public static Kind<ValidatedKind.Witness<String>, String> lookupUser(String username) {
    if (VALID_USERS.contains(username)) {
      System.out.println("  -> Looking up '" + username + "'... Success!");
      return VALIDATED.widen(Validated.valid(username)); // Success
    } else {
      System.out.println("  -> Looking up '" + username + "'... FAILED!");
      return VALIDATED.widen(Validated.invalid("User '" + username + "' not found")); // Failure
    }
  }

  public static void main(String[] args) {
    // 1. Setup: We need an Applicative for our effect type (Validated).
    Applicative<ValidatedKind.Witness<String>> validatedApplicative = ValidatedMonad.instance();
    // And an instance of the Traverse typeclass for List.
    ListTraverse listTraverse = ListTraverse.INSTANCE;

    // 2. Get the generated traversal for the 'teamMembers' field.
    var membersTraversal = ProjectTraversals.teamMembers();

    System.out.println("--- Running Traversal Scenarios for List ---");

    // --- Scenario 1: A project with all valid team members ---
    Project projectAllValid = new Project("Project Phoenix", List.of("Alice", "Bob", "Charlie"));
    System.out.println("\nInput: " + projectAllValid);

    // Use the traversal to apply the lookup function to every member in the list.
    Kind<ValidatedKind.Witness<String>, Project> result1 =
        membersTraversal.modifyF(
            ListTraversalExample::lookupUser, projectAllValid, validatedApplicative);

    Validated<String, Project> validatedResult1 = VALIDATED.narrow(result1);
    System.out.println("Result: " + validatedResult1);
    // Expected: Valid(Project[name=Project Phoenix, teamMembers=[Alice, Bob, Charlie]])

    // --- Scenario 2: A project with an invalid team member ---
    Project projectInvalidMember = new Project("Project Hydra", List.of("Alice", "Zeke", "Bob"));
    System.out.println("\nInput: " + projectInvalidMember);

    // The traversal will stop at the first failure ("Zeke").
    Kind<ValidatedKind.Witness<String>, Project> result2 =
        membersTraversal.modifyF(
            ListTraversalExample::lookupUser, projectInvalidMember, validatedApplicative);

    Validated<String, Project> validatedResult2 = VALIDATED.narrow(result2);
    System.out.println("Result: " + validatedResult2);
    // Expected: Invalid(User 'Zeke' not found)

    // --- Scenario 3: A project with no team members ---
    Project projectEmptyTeam = new Project("Project Solo", List.of());
    System.out.println("\nInput: " + projectEmptyTeam);

    // The traversal function is never called because the list is empty.
    Kind<ValidatedKind.Witness<String>, Project> result3 =
        membersTraversal.modifyF(
            ListTraversalExample::lookupUser, projectEmptyTeam, validatedApplicative);

    Validated<String, Project> validatedResult3 = VALIDATED.narrow(result3);
    System.out.println("Result: " + validatedResult3);
    // Expected: Valid(Project[name=Project Solo, teamMembers=[]])
  }
}
