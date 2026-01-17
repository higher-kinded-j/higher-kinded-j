// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.focus.FocusPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tutorial: ForPath with VTaskPath - For-Comprehensions for Virtual Threads
 *
 * <p>Learn to compose VTaskPath operations using for-comprehension syntax. ForPath provides a
 * clean, readable way to chain dependent virtual thread operations.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>ForPath.from(vtaskPath) starts a VTaskPath comprehension
 *   <li>.from() chains dependent computations
 *   <li>.let() computes intermediate values without additional effects
 *   <li>.focus() navigates into nested structures using optics
 *   <li>.yield() produces the final result
 * </ul>
 *
 * <p>Prerequisites: Complete TutorialVTask and TutorialVTaskPath first.
 *
 * <p>See the documentation: ForPath Comprehension in hkj-book
 *
 * <p>Replace each placeholder with the correct code to make the tests pass.
 */
@DisplayName("Tutorial: ForPath with VTaskPath")
public class TutorialVTaskForPath {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required - replace answerRequired() with your solution");
  }

  // ===========================================================================
  // Part 1: Basic For-Comprehensions
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Basic For-Comprehensions")
  class BasicComprehensions {

    /**
     * Exercise 1: Create a simple two-step comprehension
     *
     * <p>ForPath.from() starts the comprehension. A second .from() chains a dependent computation.
     * Finally, .yield() combines the values.
     *
     * <p>Task: Fetch a user ID, then fetch a greeting, and combine them
     */
    @Test
    @DisplayName("Exercise 1: Two-step comprehension")
    void exercise1_twoStepComprehension() {
      VTaskPath<String> fetchUserId = Path.vtaskPure("user-123");
      VTaskPath<String> fetchGreeting = Path.vtaskPure("Hello");

      // TODO: Replace answerRequired() with:
      // ForPath.from(fetchUserId)
      //     .from(id -> fetchGreeting)
      //     .yield((id, greeting) -> greeting + ", " + id + "!")
      VTaskPath<String> result = answerRequired();

      Try<String> tryResult = result.runSafe();
      assertThat(tryResult.isSuccess()).isTrue();
      assertThat(tryResult.orElse("error")).isEqualTo("Hello, user-123!");
    }

    /**
     * Exercise 2: Use values from earlier steps
     *
     * <p>In a multi-step comprehension, each .from() can access values from all previous steps. The
     * second step receives the first value, the third receives a Tuple2 of the first two, and so
     * on.
     *
     * <p>Task: Fetch base, multiplier, and compute result using values from earlier steps
     */
    @Test
    @DisplayName("Exercise 2: Multi-step with value access")
    void exercise2_multiStepValueAccess() {
      VTaskPath<Integer> fetchBase = Path.vtaskPure(10);

      // TODO: Replace answerRequired() with:
      // ForPath.from(fetchBase)
      //     .from(base -> Path.vtaskPure(base * 2))  // multiplier depends on base
      //     .from(t -> Path.vtaskPure(t._1() + t._2()))  // sum depends on both
      //     .yield((base, doubled, sum) -> sum)  // result is 10 + 20 = 30
      VTaskPath<Integer> result = answerRequired();

      Try<Integer> tryResult = result.runSafe();
      assertThat(tryResult.isSuccess()).isTrue();
      assertThat(tryResult.orElse(-1)).isEqualTo(30);
    }
  }

  // ===========================================================================
  // Part 2: Using let() for Pure Computations
  // ===========================================================================

  @Nested
  @DisplayName("Part 2: Using let() for Pure Computations")
  class LetBindings {

    /**
     * Exercise 3: Use let() for intermediate calculations
     *
     * <p>The .let() operation computes a pure value without wrapping it in VTaskPath. This is more
     * efficient than .from() when no effectful computation is needed.
     *
     * <p>Task: Fetch a number, use let() to compute intermediate values
     */
    @Test
    @DisplayName("Exercise 3: Pure intermediate values with let()")
    void exercise3_letForIntermediates() {
      VTaskPath<Integer> fetchValue = Path.vtaskPure(5);

      // TODO: Replace answerRequired() with:
      // ForPath.from(fetchValue)
      //     .let(v -> v * v)           // squared = 25 (pure computation)
      //     .let(t -> t._2() + 10)     // plus10 = 35 (pure computation)
      //     .yield((v, squared, plus10) -> "Original: " + v + ", Result: " + plus10)
      VTaskPath<String> result = answerRequired();

      Try<String> tryResult = result.runSafe();
      assertThat(tryResult.isSuccess()).isTrue();
      assertThat(tryResult.orElse("error")).isEqualTo("Original: 5, Result: 35");
    }

    /**
     * Exercise 4: Mix from() and let() in a single comprehension
     *
     * <p>You can freely mix .from() (for effectful steps) and .let() (for pure calculations) in the
     * same comprehension.
     *
     * <p>Task: Fetch price and quantity, compute subtotal and total with tax
     */
    @Test
    @DisplayName("Exercise 4: Mixing from() and let()")
    void exercise4_mixFromAndLet() {
      VTaskPath<Double> fetchPrice = Path.vtaskPure(10.0);
      VTaskPath<Integer> fetchQuantity = Path.vtaskPure(3);

      // TODO: Replace answerRequired() with:
      // ForPath.from(fetchPrice)
      //     .from(price -> fetchQuantity)
      //     .let(t -> t._1() * t._2())        // subtotal = 30.0
      //     .let(t -> t._3() * 1.1)           // total with 10% tax = 33.0
      //     .yield((price, qty, subtotal, total) -> total)
      VTaskPath<Double> result = answerRequired();

      Try<Double> tryResult = result.runSafe();
      assertThat(tryResult.isSuccess()).isTrue();
      assertThat(tryResult.orElse(-1.0)).isEqualTo(33.0);
    }
  }

  // ===========================================================================
  // Part 3: Focus Integration
  // ===========================================================================

  @Nested
  @DisplayName("Part 3: Focus Integration")
  class FocusIntegration {

    record Address(String city, String postcode) {}

    record User(String name, Address address) {}

    // Lens to access address from user
    static final Lens<User, Address> ADDRESS_LENS =
        Lens.of(User::address, (user, addr) -> new User(user.name(), addr));

    // Lens to access city from address
    static final Lens<Address, String> CITY_LENS =
        Lens.of(Address::city, (addr, city) -> new Address(city, addr.postcode()));

    /**
     * Exercise 5: Use focus() to navigate nested structures
     *
     * <p>The .focus() operation uses a FocusPath (built from a Lens) to extract a nested value.
     * This integrates optics with for-comprehensions.
     *
     * <p>Task: Fetch a user and focus on their address, then yield the city
     */
    @Test
    @DisplayName("Exercise 5: Focus on nested structure")
    void exercise5_focusOnNested() {
      User user = new User("Alice", new Address("London", "SW1A 1AA"));
      VTaskPath<User> fetchUser = Path.vtaskPure(user);

      FocusPath<User, Address> addressPath = FocusPath.of(ADDRESS_LENS);

      // TODO: Replace answerRequired() with:
      // ForPath.from(fetchUser)
      //     .focus(addressPath)
      //     .yield((u, addr) -> addr.city())
      VTaskPath<String> result = answerRequired();

      Try<String> tryResult = result.runSafe();
      assertThat(tryResult.isSuccess()).isTrue();
      assertThat(tryResult.orElse("error")).isEqualTo("London");
    }

    /**
     * Exercise 6: Combine focus with effectful operations
     *
     * <p>Focus can be combined with other comprehension operations to build complex pipelines.
     *
     * <p>Task: Fetch user, focus on address, then fetch weather for that city
     */
    @Test
    @DisplayName("Exercise 6: Focus with subsequent effects")
    void exercise6_focusWithEffects() {
      User user = new User("Bob", new Address("Paris", "75001"));
      VTaskPath<User> fetchUser = Path.vtaskPure(user);

      // Simulated weather service
      Function<String, VTaskPath<String>> fetchWeather = city -> Path.vtaskPure("Sunny in " + city);

      FocusPath<User, Address> addressPath = FocusPath.of(ADDRESS_LENS);

      // TODO: Replace answerRequired() with:
      // ForPath.from(fetchUser)
      //     .focus(addressPath)
      //     .from(t -> fetchWeather.apply(t._2().city()))
      //     .yield((user, address, weather) -> user.name() + ": " + weather)
      VTaskPath<String> result = answerRequired();

      Try<String> tryResult = result.runSafe();
      assertThat(tryResult.isSuccess()).isTrue();
      assertThat(tryResult.orElse("error")).isEqualTo("Bob: Sunny in Paris");
    }
  }

  // ===========================================================================
  // Part 4: Error Handling in Comprehensions
  // ===========================================================================

  @Nested
  @DisplayName("Part 4: Error Handling in Comprehensions")
  class ErrorHandling {

    /**
     * Exercise 7: Errors propagate through comprehensions
     *
     * <p>If any step in the comprehension fails, the entire comprehension fails. This is
     * short-circuit evaluation - subsequent steps are not executed.
     *
     * <p>Task: Build a comprehension where the second step fails, verify the error propagates
     */
    @Test
    @DisplayName("Exercise 7: Error propagation")
    void exercise7_errorPropagation() {
      VTaskPath<Integer> step1 = Path.vtaskPure(42);
      VTaskPath<Integer> step2Fails = Path.vtaskFail(new RuntimeException("Step 2 failed"));
      VTaskPath<Integer> step3NeverRuns = Path.vtaskPure(100);

      // TODO: Replace answerRequired() with:
      // ForPath.from(step1)
      //     .from(a -> step2Fails)
      //     .from(t -> step3NeverRuns)
      //     .yield((a, b, c) -> a + b + c)
      VTaskPath<Integer> result = answerRequired();

      Try<Integer> tryResult = result.runSafe();
      assertThat(tryResult.isFailure()).isTrue();
      String errorMessage = tryResult.fold(v -> "no error", Throwable::getMessage);
      assertThat(errorMessage).isEqualTo("Step 2 failed");
    }

    /**
     * Exercise 8: Recover from errors at the end of comprehension
     *
     * <p>You can add error recovery to the final VTaskPath returned by .yield(). This allows
     * graceful fallback for the entire comprehension.
     *
     * <p>Task: Build a comprehension with error recovery
     */
    @Test
    @DisplayName("Exercise 8: Comprehension with recovery")
    void exercise8_comprehensionWithRecovery() {
      VTaskPath<String> fetchData = Path.vtaskFail(new RuntimeException("Network error"));

      // TODO: Replace answerRequired() with:
      // ForPath.from(fetchData)
      //     .let(data -> data.toUpperCase())
      //     .yield((data, upper) -> upper)
      //     .handleError(ex -> "DEFAULT")
      VTaskPath<String> result = answerRequired();

      Try<String> tryResult = result.runSafe();
      assertThat(tryResult.isSuccess()).isTrue();
      assertThat(tryResult.orElse("error")).isEqualTo("DEFAULT");
    }
  }

  // ===========================================================================
  // Part 5: Real-World Workflow
  // ===========================================================================

  @Nested
  @DisplayName("Part 5: Real-World Workflow")
  class RealWorldWorkflow {

    record Order(String id, double amount) {}

    record Payment(String transactionId, double amount) {}

    record Confirmation(String orderId, String transactionId, String message) {}

    /**
     * Exercise 9: Build a complete order processing workflow
     *
     * <p>Combine all the techniques: multiple steps, let bindings, and error handling to build a
     * realistic order processing pipeline.
     *
     * <p>Task: Validate order, process payment, generate confirmation
     */
    @Test
    @DisplayName("Exercise 9: Order processing workflow")
    void exercise9_orderProcessingWorkflow() {
      Order order = new Order("ORD-001", 99.99);

      // Simulated services
      Function<Order, VTaskPath<Order>> validateOrder =
          o -> o.amount() > 0 ? Path.vtaskPure(o) : Path.vtaskFail(new RuntimeException("Invalid"));

      Function<Order, VTaskPath<Payment>> processPayment =
          o -> Path.vtaskPure(new Payment("TXN-" + o.id(), o.amount()));

      BiFunction<Order, Payment, VTaskPath<Confirmation>> sendConfirmation =
          (o, p) ->
              Path.vtaskPure(
                  new Confirmation(o.id(), p.transactionId(), "Order confirmed: " + o.id()));

      // TODO: Replace answerRequired() with:
      // ForPath.from(validateOrder.apply(order))
      //     .from(validated -> processPayment.apply(validated))
      //     .from(t -> sendConfirmation.apply(t._1(), t._2()))
      //     .yield((validated, payment, confirmation) -> confirmation.message())
      VTaskPath<String> result = answerRequired();

      Try<String> tryResult = result.runSafe();
      assertThat(tryResult.isSuccess()).isTrue();
      assertThat(tryResult.orElse("error")).isEqualTo("Order confirmed: ORD-001");
    }

    /**
     * Exercise 10: Workflow with derived values
     *
     * <p>Use let() to compute derived values (like totals, taxes, discounts) between effectful
     * steps.
     *
     * <p>Task: Calculate order total with discount and tax
     */
    @Test
    @DisplayName("Exercise 10: Workflow with calculations")
    void exercise10_workflowWithCalculations() {
      record Product(String name, double price) {}

      record Cart(List<Product> items) {
        double subtotal() {
          return items.stream().mapToDouble(Product::price).sum();
        }
      }

      Cart cart = new Cart(List.of(new Product("Book", 20.0), new Product("Pen", 5.0)));
      double discountRate = 0.1; // 10% discount
      double taxRate = 0.08; // 8% tax

      VTaskPath<Cart> fetchCart = Path.vtaskPure(cart);

      // TODO: Replace answerRequired() with:
      // ForPath.from(fetchCart)
      //     .let(c -> c.subtotal())                              // subtotal = 25.0
      //     .let(t -> t._2() * (1 - discountRate))               // afterDiscount = 22.5
      //     .let(t -> t._3() * (1 + taxRate))                    // total = 24.3
      //     .yield((cart, subtotal, afterDiscount, total) ->
      //         String.format("Total: $%.2f", total))
      VTaskPath<String> result = answerRequired();

      Try<String> tryResult = result.runSafe();
      assertThat(tryResult.isSuccess()).isTrue();
      assertThat(tryResult.orElse("error")).isEqualTo("Total: $24.30");
    }
  }

  /**
   * Congratulations! You've completed Tutorial: ForPath with VTaskPath
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>✓ How to create VTaskPath comprehensions with ForPath.from()
   *   <li>✓ How to chain dependent computations with .from()
   *   <li>✓ How to use .let() for pure intermediate calculations
   *   <li>✓ How to integrate optics with .focus()
   *   <li>✓ How errors propagate and how to recover from them
   *   <li>✓ How to build real-world workflows combining all techniques
   * </ul>
   *
   * <p>Key Takeaways:
   *
   * <ul>
   *   <li>ForPath provides a readable, sequential style for composing VTaskPath
   *   <li>Use .from() for dependent effects, .let() for pure calculations
   *   <li>Optics integration via .focus() enables elegant data navigation
   *   <li>Error handling can be added at the end with .handleError()
   * </ul>
   *
   * <p>Next: Explore VTaskContext for dependency injection patterns.
   */
}
