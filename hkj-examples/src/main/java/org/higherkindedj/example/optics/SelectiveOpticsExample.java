// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdSelective;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedMonad;
import org.higherkindedj.hkt.validated.ValidatedSelective;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.util.Traversals;

/**
 * Comprehensive example demonstrating Selective functor enhancements to optics. Shows conditional
 * modifications, branching logic, and performance optimizations.
 */
public class SelectiveOpticsExample {

  @GenerateLenses
  public record Account(String id, double balance, boolean isPremium) {}

  @GenerateLenses
  @GenerateTraversals
  public record Bank(String name, List<Account> accounts) {}

  @GenerateLenses
  public record Email(String address, boolean verified) {}

  @GenerateLenses
  public record UserLogin(String name, Email email, int loginAttempts) {}

  public static void main(String[] args) {
    System.out.println("=== SELECTIVE OPTICS EXAMPLES ===\n");

    conditionalLensExample();
    branchingLensExample();
    conditionalTraversalExample();
    speculativeTraversalExample();
    shortCircuitExample();
    comparativeExample();
  }

  /** Example 1: Conditional Lens Updates Only update a field when a condition is met. */
  private static void conditionalLensExample() {
    System.out.println("--- Example 1: Conditional Lens Updates ---");

    Lens<UserLogin, Integer> attemptsLens = UserLoginLenses.loginAttempts();
    UserLogin userLogin = new UserLogin("Alice", new Email("alice@example.com", true), 2);

    // Only increment login attempts if below threshold
    Predicate<Integer> belowThreshold = attempts -> attempts < 5;

    Kind<IdKind.Witness, UserLogin> result =
        attemptsLens.modifyWhen(
            belowThreshold, attempts -> attempts + 1, userLogin, IdSelective.instance());

    System.out.println("Original: " + userLogin);
    System.out.println("Updated:  " + ID.narrow(result).value());

    // Try with user at threshold
    UserLogin maxedUserLogin = new UserLogin("Bob", new Email("bob@example.com", true), 5);
    Kind<IdKind.Witness, UserLogin> noChange =
        attemptsLens.modifyWhen(
            belowThreshold, attempts -> attempts + 1, maxedUserLogin, IdSelective.instance());

    System.out.println("\nMaxed user (no change): " + ID.narrow(noChange).value());
    System.out.println();
  }

  /** Example 2: Branching Lens Logic Apply different modifications based on current value. */
  private static void branchingLensExample() {
    System.out.println("--- Example 2: Branching Lens Logic ---");

    Lens<Account, Double> balanceLens = AccountLenses.balance();
    Account positiveAccount = new Account("ACC-001", 1000.0, false);
    Account negativeAccount = new Account("ACC-002", -50.0, false);

    // Apply interest if positive, overdraft fee if negative
    Predicate<Double> isPositive = balance -> balance > 0;

    Kind<IdKind.Witness, Account> positive =
        balanceLens.modifyBranch(
            isPositive,
            balance -> balance * 1.05, // 5% interest
            balance -> balance - 25.0, // $25 overdraft fee
            positiveAccount,
            IdSelective.instance());

    Kind<IdKind.Witness, Account> negative =
        balanceLens.modifyBranch(
            isPositive,
            balance -> balance * 1.05,
            balance -> balance - 25.0,
            negativeAccount,
            IdSelective.instance());

    System.out.println(
        "Positive account: " + positiveAccount + " -> " + ID.narrow(positive).value());
    System.out.println(
        "Negative account: " + negativeAccount + " -> " + ID.narrow(negative).value());
    System.out.println();
  }

  /** Example 3: Conditional Traversal Only process elements that match a condition. */
  private static void conditionalTraversalExample() {
    System.out.println("--- Example 3: Conditional Traversal ---");

    Bank bank =
        new Bank(
            "Premium Bank",
            List.of(
                new Account("ACC-001", 5000.0, true),
                new Account("ACC-002", 100.0, false),
                new Account("ACC-003", 10000.0, true),
                new Account("ACC-004", 500.0, false)));

    Traversal<Bank, Account> accountsTraversal = BankTraversals.accounts();

    // Only apply bonus to premium accounts
    Predicate<Account> isPremium = Account::isPremium;

    Kind<IdKind.Witness, Bank> updated =
        accountsTraversal.modifyWhen(
            isPremium,
            account ->
                Id.of(
                    AccountLenses.balance()
                        .modify(
                            balance -> balance * 1.10, // 10% bonus
                            account)),
            bank,
            IdSelective.instance());

    System.out.println("Original accounts:");
    bank.accounts().forEach(acc -> System.out.println("  " + acc));

    System.out.println("\nAfter premium bonus:");
    ID.narrow(updated).value().accounts().forEach(acc -> System.out.println("  " + acc));
    System.out.println();
  }

  /**
   * Example 4: Speculative Execution Both branches visible upfront - can potentially execute in
   * parallel.
   */
  private static void speculativeTraversalExample() {
    System.out.println("--- Example 4: Speculative Execution ---");

    List<Account> accounts =
        List.of(
            new Account("ACC-001", 5000.0, true),
            new Account("ACC-002", 100.0, false),
            new Account("ACC-003", 10000.0, true));

    // Simulate expensive operations
    Function<Account, Kind<IdKind.Witness, Double>> premiumCalculation =
        account -> {
          System.out.println("  Calculating premium rate for " + account.id());
          // Simulate expensive operation
          return Id.of(account.balance() * 1.10);
        };

    Function<Account, Kind<IdKind.Witness, Double>> standardCalculation =
        account -> {
          System.out.println("  Calculating standard rate for " + account.id());
          // Simulate different expensive operation
          return Id.of(account.balance() * 1.02);
        };

    System.out.println("Processing accounts with speculative execution:");
    Kind<IdKind.Witness, List<Double>> results =
        Traversals.speculativeTraverseList(
            accounts,
            Account::isPremium,
            premiumCalculation,
            standardCalculation,
            IdSelective.instance());

    System.out.println("\nResults: " + ID.narrow(results).value());
    System.out.println();
  }

  /** Example 5: Short-Circuit Processing Stop processing when a condition is met. */
  private static void shortCircuitExample() {
    System.out.println("--- Example 5: Short-Circuit Processing ---");

    List<Account> accounts =
        List.of(
            new Account("ACC-001", 1000.0, false),
            new Account("ACC-002", -50.0, false), // This will trigger stop
            new Account("ACC-003", 2000.0, false),
            new Account("ACC-004", 3000.0, false));

    Predicate<Account> isOverdrawn = account -> account.balance() < 0;

    System.out.println("Processing accounts until overdraft found:");
    Kind<IdKind.Witness, List<Account>> results =
        Traversals.traverseListUntil(
            accounts,
            isOverdrawn,
            account -> {
              System.out.println("  Processing: " + account.id());
              return Id.of(account);
            },
            IdSelective.instance());

    System.out.println("Accounts after stop: " + ID.narrow(results).value().size() + " total");
    System.out.println();
  }

  /**
   * Example 6: Comparative Example - Applicative vs Selective Shows the difference in validation
   * approaches.
   */
  private static void comparativeExample() {
    System.out.println("--- Example 6: Applicative vs Selective Comparison ---");

    Bank bank =
        new Bank(
            "Test Bank",
            List.of(
                new Account("ACC-001", 1000.0, false),
                new Account("ACC-002", -50.0, false),
                new Account("ACC-003", -100.0, false)));

    Traversal<Bank, Account> accountsTraversal = BankTraversals.accounts();

    // Validation function
    Function<Account, Kind<ValidatedKind.Witness<String>, Account>> validator =
        account -> {
          if (account.balance() < 0) {
            System.out.println("  Validating " + account.id() + ": FAILED");
            return VALIDATED.widen(Validated.invalid("Account " + account.id() + " is overdrawn"));
          } else {
            System.out.println("  Validating " + account.id() + ": OK");
            return VALIDATED.widen(Validated.valid(account));
          }
        };

    // Approach 1: Applicative - validates ALL accounts, accumulates ALL errors
    System.out.println("\nApplicative approach (accumulates all errors):");
    Validated<String, Bank> applicativeResult =
        VALIDATED.narrow(
            accountsTraversal.modifyF(
                validator, bank, ValidatedMonad.instance(Semigroups.string("; "))));
    System.out.println("Result: " + applicativeResult);

    // Approach 2: Selective - can implement early termination
    System.out.println("\nSelective approach (can short-circuit):");

    Predicate<Account> isValid = account -> account.balance() >= 0;

    Selective<ValidatedKind.Witness<String>> selective =
        ValidatedSelective.instance(Semigroups.string("; "));

    Validated<String, Bank> selectiveResult =
        VALIDATED.narrow(
            accountsTraversal.modifyWhen(
                isValid, validator, // Only validate if basic check passes
                bank, selective));
    System.out.println("Result: " + selectiveResult);
    System.out.println();
  }
}
