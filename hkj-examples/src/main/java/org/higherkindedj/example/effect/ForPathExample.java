// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.effect;

import java.util.Optional;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.NonDetPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.expression.ForPath;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;

/**
 * Examples demonstrating ForPath: for-comprehensions that work directly with Effect Path types.
 *
 * <p>ForPath bridges the For comprehension system and the Effect Path API, allowing composition of
 * Path types using for-comprehension style while preserving Path semantics.
 *
 * <p>This example shows:
 *
 * <ul>
 *   <li>Basic MaybePath comprehensions with from(), let(), when(), yield()
 *   <li>EitherPath comprehensions for error handling workflows
 *   <li>IOPath comprehensions for deferred side-effectful operations
 *   <li>NonDetPath comprehensions for generating combinations
 *   <li>Optics integration with focus() and match()
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.effect.ForPathExample}
 */
public class ForPathExample {

  // Record types for optics examples
  record Address(String city, String postcode) {}

  record User(String name, Address address, String email) {
    User(String name, Address address) {
      this(name, address, null);
    }
  }

  // Sealed types for AffinePath pattern matching example
  sealed interface Result permits Success, Failure {}

  record Success(String value) implements Result {}

  record Failure(String error) implements Result {}

  // Lenses for optics integration
  static final Lens<User, Address> addressLens =
      Lens.of(User::address, (u, a) -> new User(u.name(), a, u.email()));

  static final Lens<Address, String> cityLens =
      Lens.of(Address::city, (a, c) -> new Address(c, a.postcode()));

  // FocusPath from lenses
  static final FocusPath<User, Address> addressPath = FocusPath.of(addressLens);
  static final FocusPath<Address, String> cityPath = FocusPath.of(cityLens);

  // AffinePath for optional email field
  static final AffinePath<User, String> emailPath =
      AffinePath.of(
          Affine.of(
              u -> Optional.ofNullable(u.email()), (u, e) -> new User(u.name(), u.address(), e)));

  public static void main(String[] args) {
    System.out.println("=== ForPath: For-Comprehensions with Effect Paths ===\n");

    maybePathBasics();
    maybePathWithGuards();
    eitherPathWorkflow();
    ioPathComposition();
    nonDetPathCombinations();
    opticsIntegration();
    highArityExample();
  }

  private static void maybePathBasics() {
    System.out.println("--- MaybePath Basics ---");

    // Simple comprehension: extract and combine values
    MaybePath<Integer> result =
        ForPath.from(Path.just(10))
            .from(a -> Path.just(20))
            .from(t -> Path.just(5)) // t is Tuple2<Integer, Integer>
            .yield((a, b, c) -> a + b + c);

    System.out.println("Sum of 10 + 20 + 5 = " + result.getOrElse(-1)); // 35

    // Using let() for pure computations
    MaybePath<String> withLet =
        ForPath.from(Path.just(10))
            .let(a -> a * 2) // b = 20 (pure calculation)
            .let(t -> t._1() + t._2()) // c = 30 (sum via tuple)
            .yield((a, b, c) -> "Original: " + a + ", Doubled: " + b + ", Sum: " + c);

    System.out.println(withLet.getOrElse("no value"));
    // Original: 10, Doubled: 20, Sum: 30

    // Short-circuiting with Nothing
    MaybePath<Integer> shortCircuit =
        ForPath.from(Path.just(5))
            .<Integer>from(a -> Path.nothing()) // stops here
            .from(t -> Path.just(100)) // never executed
            .yield((a, b, c) -> a + b + c);

    System.out.println("Short-circuited result: " + shortCircuit.getOrElse(-1)); // -1

    System.out.println();
  }

  private static void maybePathWithGuards() {
    System.out.println("--- MaybePath with Guards (when) ---");

    // Guard that passes
    MaybePath<Integer> evenOnly =
        ForPath.from(Path.just(4)).when(n -> n % 2 == 0).yield(n -> n * 10);

    System.out.println("4 is even, result: " + evenOnly.getOrElse(-1)); // 40

    // Guard that fails
    MaybePath<Integer> oddFiltered =
        ForPath.from(Path.just(3)).when(n -> n % 2 == 0).yield(n -> n * 10);

    System.out.println("3 is odd, filtered out: " + oddFiltered.getOrElse(-1)); // -1

    // Guard in multi-step comprehension
    MaybePath<String> multiStepGuard =
        ForPath.from(Path.just(5))
            .from(a -> Path.just(10))
            .when(t -> t._1() + t._2() > 10) // 5 + 10 = 15 > 10, passes
            .yield((a, b) -> "Sum is " + (a + b));

    System.out.println(multiStepGuard.getOrElse("filtered")); // Sum is 15

    System.out.println();
  }

  private static void eitherPathWorkflow() {
    System.out.println("--- EitherPath Workflow ---");

    // Successful workflow using ForPath
    EitherPath<String, String> successResult =
        ForPath.from(Path.<String, String>right("Alice"))
            .from(name -> Path.right("order-123"))
            .yield((name, orderId) -> "Created " + orderId + " for " + name);

    System.out.println("Success: " + successResult.run().getRight());

    // Failed workflow - starts with Left
    EitherPath<String, String> failedResult =
        ForPath.from(Path.<String, String>left("User not found"))
            .from(name -> Path.right("order-456"))
            .yield((name, orderId) -> "Created " + orderId);

    String leftValue = failedResult.run().getLeft();
    System.out.println("Failure: " + leftValue);

    System.out.println();
  }

  private static void ioPathComposition() {
    System.out.println("--- IOPath Composition (Deferred Execution) ---");

    // IOPath comprehensions are lazy - nothing executes until unsafeRun()
    IOPath<String> readConfig = Path.io(() -> "production");
    IOPath<Integer> readPort = Path.io(() -> 8080);

    IOPath<String> serverInfo =
        ForPath.from(readConfig)
            .from(env -> readPort)
            .let(t -> t._1().toUpperCase())
            .yield((env, port, upperEnv) -> upperEnv + " server on port " + port);

    System.out.println("IOPath created (nothing executed yet)");
    String result = serverInfo.unsafeRun();
    System.out.println("After unsafeRun(): " + result);
    // PRODUCTION server on port 8080

    // Chaining multiple IO operations
    IOPath<String> multiStep =
        ForPath.from(Path.io(() -> "Step 1"))
            .from(s1 -> Path.io(() -> s1 + " -> Step 2"))
            .from(t -> Path.io(() -> t._2() + " -> Step 3"))
            .yield((s1, s2, s3) -> "Final: " + s3);

    System.out.println(multiStep.unsafeRun());
    // Final: Step 1 -> Step 2 -> Step 3

    System.out.println();
  }

  private static void nonDetPathCombinations() {
    System.out.println("--- NonDetPath Combinations ---");

    // NonDetPath generates all combinations (like nested loops)
    // Use Path.list() to create NonDetPath
    NonDetPath<String> allCombinations =
        ForPath.from(Path.list("red", "blue", "green"))
            .from(c -> Path.list("S", "M", "L"))
            .yield((colour, size) -> colour + "-" + size);

    System.out.println("All combinations: " + allCombinations.run());
    // [red-S, red-M, red-L, blue-S, blue-M, blue-L, green-S, green-M, green-L]

    // With filtering using when()
    NonDetPath<String> filteredCombinations =
        ForPath.from(Path.list("red", "blue", "green"))
            .from(c -> Path.list("S", "M", "L"))
            .when(t -> !t._1().equals("blue") || !t._2().equals("S")) // filter out blue-S
            .yield((colour, size) -> colour + "-" + size);

    System.out.println("Filtered (no blue-S): " + filteredCombinations.run());
    // [red-S, red-M, red-L, blue-M, blue-L, green-S, green-M, green-L]

    // Number combinations with arithmetic filtering
    NonDetPath<String> pairs =
        ForPath.from(Path.list(1, 2, 3, 4, 5))
            .from(a -> Path.list(1, 2, 3, 4, 5))
            .when(t -> t._1() < t._2()) // only pairs where first < second
            .yield((a, b) -> "(" + a + "," + b + ")");

    System.out.println("Ordered pairs: " + pairs.run());
    // [(1,2), (1,3), (1,4), (1,5), (2,3), (2,4), (2,5), (3,4), (3,5), (4,5)]

    System.out.println();
  }

  private static void opticsIntegration() {
    System.out.println("--- Optics Integration ---");

    User user = new User("Alice", new Address("London", "SW1A 1AA"));

    // Using focus() to extract nested values - compose paths with via()
    FocusPath<User, String> userCityPath = addressPath.via(cityPath);
    MaybePath<String> cityResult =
        ForPath.from(Path.just(user))
            .focus(userCityPath)
            .yield((u, city) -> u.name() + " lives in " + city);

    System.out.println(cityResult.getOrElse("no result"));
    // Alice lives in London

    // Alternative: use focus() then extract with function in second focus()
    MaybePath<String> cityResult2 =
        ForPath.from(Path.just(user))
            .focus(addressPath)
            .focus(t -> t._2().city()) // t is Tuple2<User, Address>
            .yield((u, address, city) -> u.name() + " lives in " + city);

    System.out.println(cityResult2.getOrElse("no result"));
    // Alice lives in London

    // Using match() with AffinePath for optional fields
    User userWithEmail = new User("Bob", new Address("Paris", "75001"), "bob@example.com");
    User userWithoutEmail = new User("Carol", new Address("Berlin", "10115"));

    // User with email - successful match
    MaybePath<String> matchSuccess =
        ForPath.from(Path.just(userWithEmail))
            .match(emailPath)
            .yield((u, email) -> u.name() + "'s email: " + email);

    System.out.println("Match with email: " + matchSuccess.getOrElse("no match"));
    // Match with email: Bob's email: bob@example.com

    // User without email - returns Nothing
    MaybePath<String> matchFailure =
        ForPath.from(Path.just(userWithoutEmail))
            .match(emailPath)
            .yield((u, email) -> u.name() + "'s email: " + email);

    System.out.println("Match without email: " + matchFailure.getOrElse("no match"));
    // Match without email: no match

    System.out.println();
  }

  private static void highArityExample() {
    System.out.println("--- Extended Arities (6+ bindings) ---");

    // ForPath supports up to 12 chained bindings across all path types.
    // Steps 2-12 are generated by the hkj-processor annotation processor.

    // 6-binding MaybePath comprehension
    MaybePath<String> sixStep =
        ForPath.from(Path.just("Alice"))
            .let(name -> name.length())
            .from(t -> Path.just(t._1().toUpperCase()))
            .let(t -> t._2() * 10)
            .let(t -> t._3() + "!")
            .let(t -> t._1() + " has " + t._2() + " letters")
            .yield(
                (name, len, upper, score, exclaimed, summary) ->
                    summary + " (score: " + score + ")");

    System.out.println("6-step MaybePath: " + sixStep.getOrElse("nothing"));
    // Alice has 5 letters (score: 50)

    // NonDetPath with 6 bindings: generate combinations and filter
    NonDetPath<String> combos =
        ForPath.from(Path.list("A", "B"))
            .from(letter -> Path.list(1, 2, 3))
            .let(t -> t._1() + t._2())
            .let(t -> t._3().length())
            .when(t -> t._4() == 2)
            .let(t -> t._3() + " (len=" + t._4() + ")")
            .yield((letter, num, combined, len, formatted) -> formatted);

    System.out.println("6-step NonDetPath: " + combos.run());

    // 8-binding MaybePath comprehension — the maximum arity
    MaybePath<String> eightStep =
        ForPath.from(Path.just("Bob"))
            .let(name -> name.length()) // b = 3
            .from(t -> Path.just(t._1().toLowerCase())) // c = "bob"
            .let(t -> t._2() * 100) // d = 300
            .let(t -> t._3() + "!") // e = "bob!"
            .let(t -> t._1() + "#" + t._4()) // f = "Bob#300"
            .let(t -> t._6().length()) // g = 7
            .let(t -> "result=" + t._6() + " (len " + t._7() + ")") // h
            .yield((name, len, lower, score, excl, tag, tagLen, summary) -> summary);

    System.out.println("8-step MaybePath: " + eightStep.getOrElse("nothing"));
    // result=Bob#300 (len 7)

    System.out.println();
  }
}
