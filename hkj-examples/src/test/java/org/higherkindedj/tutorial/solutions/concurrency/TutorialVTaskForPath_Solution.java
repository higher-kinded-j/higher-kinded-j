// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.expression.ForPath;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.focus.FocusPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Solution for TutorialVTaskForPath — teaching-solution format.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions established by
 * the Foundations journey: read the working code first, then the commentary on <em>why</em> the
 * chosen form is idiomatic. The complete-with-commentary template (Why this is idiomatic /
 * Alternative / Common wrong attempt on every exercise) lives in the Foundations solutions
 * coretypes/Tutorial01_KindBasics_Solution.java as the canonical reference.
 *
 * <p>The exercise bodies below are correct working code. Per-exercise teaching commentary is being
 * rolled out across the chapter; if this file does not yet have it, treat the reference code as the
 * answer and consult the pilot solution for the format guide.
 *
 * <p>For the chapter-level guidance on how to learn from a solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
 */
@DisplayName("Solution: ForPath with VTaskPath")
public class TutorialVTaskForPath_Solution {

  // ===========================================================================
  // Part 1: Basic For-Comprehensions
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Basic For-Comprehensions")
  class BasicComprehensions {

    /**
     * Why this is idiomatic: {@code ForPath.from(...).from(...).yield(...)} is the comprehension
     * form of {@code via}. Each step binds a name; {@code yield} combines them.
     *
     * <p>Alternative: chained {@code via} calls. Equivalent; the comprehension keeps every binding
     * accessible to subsequent steps.
     *
     * <p>Common wrong attempt: try to use a value from {@code from} outside the comprehension.
     * Bindings live in the comprehension scope; project with {@code yield}.
     */
    @Test
    @DisplayName("Exercise 1: Two-step comprehension")
    void exercise1_twoStepComprehension() {
      VTaskPath<String> fetchUserId = Path.vtaskPure("user-123");
      VTaskPath<String> fetchGreeting = Path.vtaskPure("Hello");

      // SOLUTION: Chain two VTaskPaths with ForPath
      VTaskPath<String> result =
          ForPath.from(fetchUserId)
              .from(id -> fetchGreeting)
              .yield((id, greeting) -> greeting + ", " + id + "!");

      Try<String> tryResult = result.runSafe();
      assertThat(tryResult.isSuccess()).isTrue();
      assertThat(tryResult.orElse("error")).isEqualTo("Hello, user-123!");
    }

    /**
     * Why this is idiomatic: each {@code from} extends the binding tuple by one slot. Later steps
     * access prior bindings via {@code t._1()}, {@code t._2()}, etc.
     *
     * <p>Alternative: extract intermediate {@code VTaskPath}s into local variables. Same answer;
     * loses the chain shape.
     *
     * <p>Common wrong attempt: forget that the lambda receives a tuple. {@code from(t -> ...)} sees
     * a tuple of all prior bindings; index correctly.
     */
    @Test
    @DisplayName("Exercise 2: Multi-step with value access")
    void exercise2_multiStepValueAccess() {
      VTaskPath<Integer> fetchBase = Path.vtaskPure(10);

      // SOLUTION: Each step can access previous values; tuple grows with each step
      VTaskPath<Integer> result =
          ForPath.from(fetchBase)
              .from(base -> Path.vtaskPure(base * 2))
              .from(t -> Path.vtaskPure(t._1() + t._2()))
              .yield((base, doubled, sum) -> sum);

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
     * Why this is idiomatic: {@code let(fn)} binds a pure intermediate value without wrapping in
     * {@code VTaskPath}. Use it for arithmetic, concatenation, and other pure derivations between
     * effectful steps.
     *
     * <p>Alternative: wrap pure values in {@code Path.vtaskPure} and use {@code from}. Works;
     * {@code let} avoids the unnecessary wrapping.
     *
     * <p>Common wrong attempt: use {@code let} for an effectful step. The lambda is supposed to be
     * pure; effectful steps belong in {@code from}.
     */
    @Test
    @DisplayName("Exercise 3: Pure intermediate values with let()")
    void exercise3_letForIntermediates() {
      VTaskPath<Integer> fetchValue = Path.vtaskPure(5);

      // SOLUTION: Use let() for pure calculations without wrapping in VTaskPath
      VTaskPath<String> result =
          ForPath.from(fetchValue)
              .let(v -> v * v)
              .let(t -> t._2() + 10)
              .yield((v, squared, plus10) -> "Original: " + v + ", Result: " + plus10);

      Try<String> tryResult = result.runSafe();
      assertThat(tryResult.isSuccess()).isTrue();
      assertThat(tryResult.orElse("error")).isEqualTo("Original: 5, Result: 35");
    }

    /**
     * Why this is idiomatic: alternate {@code from} (effectful) and {@code let} (pure) freely. Each
     * step extends the binding tuple; later steps see all prior values.
     *
     * <p>Alternative: collapse pure steps into the {@code yield}. Same answer; the staged form
     * keeps each derived value named.
     *
     * <p>Common wrong attempt: order steps so a {@code let} runs before its inputs are bound. Each
     * step sees only prior bindings; let must come after its inputs.
     */
    @Test
    @DisplayName("Exercise 4: Mixing from() and let()")
    void exercise4_mixFromAndLet() {
      VTaskPath<Double> fetchPrice = Path.vtaskPure(10.0);
      VTaskPath<Integer> fetchQuantity = Path.vtaskPure(3);

      // SOLUTION: Mix from() for effects and let() for pure calculations
      VTaskPath<Double> result =
          ForPath.from(fetchPrice)
              .from(price -> fetchQuantity)
              .let(t -> t._1() * t._2())
              .let(t -> t._3() * 1.1)
              .yield((price, qty, subtotal, total) -> total);

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

    static final Lens<User, Address> ADDRESS_LENS =
        Lens.of(User::address, (user, addr) -> new User(user.name(), addr));

    static final Lens<Address, String> CITY_LENS =
        Lens.of(Address::city, (addr, city) -> new Address(city, addr.postcode()));

    /**
     * Why this is idiomatic: {@code focus(focusPath)} navigates the previous binding through an
     * optic, adding the focused value as a new binding. The comprehension stays declarative.
     *
     * <p>Alternative: chain {@code let(t -> lens.get(t._1()))}. Equivalent; the {@code focus} call
     * is the named optic-aware step.
     *
     * <p>Common wrong attempt: build the focus path inline. The path can be extracted as a
     * constant; pull it out for reuse.
     */
    @Test
    @DisplayName("Exercise 5: Focus on nested structure")
    void exercise5_focusOnNested() {
      User user = new User("Alice", new Address("London", "SW1A 1AA"));
      VTaskPath<User> fetchUser = Path.vtaskPure(user);

      FocusPath<User, Address> addressPath = FocusPath.of(ADDRESS_LENS);

      // SOLUTION: Use focus() to extract nested values with optics
      VTaskPath<String> result =
          ForPath.from(fetchUser).focus(addressPath).yield((u, addr) -> addr.city());

      Try<String> tryResult = result.runSafe();
      assertThat(tryResult.isSuccess()).isTrue();
      assertThat(tryResult.orElse("error")).isEqualTo("London");
    }

    /**
     * Why this is idiomatic: combine {@code focus} (optic navigation) with {@code from} (effectful
     * follow-up). The comprehension threads the focused value into the next service call.
     *
     * <p>Alternative: read the focused value, then call the service in a subsequent {@code via}.
     * Same answer; the comprehension form keeps the pipeline flat.
     *
     * <p>Common wrong attempt: re-read the original value when the focused one suffices. Use the
     * comprehension's binding tuple to access whichever step is needed.
     */
    @Test
    @DisplayName("Exercise 6: Focus with subsequent effects")
    void exercise6_focusWithEffects() {
      User user = new User("Bob", new Address("Paris", "75001"));
      VTaskPath<User> fetchUser = Path.vtaskPure(user);

      Function<String, VTaskPath<String>> fetchWeather = city -> Path.vtaskPure("Sunny in " + city);

      FocusPath<User, Address> addressPath = FocusPath.of(ADDRESS_LENS);

      // SOLUTION: Combine focus with subsequent effectful operations
      VTaskPath<String> result =
          ForPath.from(fetchUser)
              .focus(addressPath)
              .from(t -> fetchWeather.apply(t._2().city()))
              .yield((user1, address, weather) -> user1.name() + ": " + weather);

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
     * Why this is idiomatic: a failure in any {@code from} step short-circuits the comprehension;
     * later steps never run. The semantics match a try/catch surrounding the entire chain.
     *
     * <p>Alternative: explicit early return after each step. Equivalent; the comprehension does it
     * automatically.
     *
     * <p>Common wrong attempt: assume later steps still run because they appear after the failure.
     * They do not; the comprehension halts on first failure.
     */
    @Test
    @DisplayName("Exercise 7: Error propagation")
    void exercise7_errorPropagation() {
      VTaskPath<Integer> step1 = Path.vtaskPure(42);
      VTaskPath<Integer> step2Fails = Path.vtaskFail(new RuntimeException("Step 2 failed"));
      VTaskPath<Integer> step3NeverRuns = Path.vtaskPure(100);

      // SOLUTION: Errors short-circuit the comprehension; step3 never executes
      VTaskPath<Integer> result =
          ForPath.from(step1)
              .from(a -> step2Fails)
              .from(t -> step3NeverRuns)
              .yield((a, b, c) -> a + b + c);

      Try<Integer> tryResult = result.runSafe();
      assertThat(tryResult.isFailure()).isTrue();
      String errorMessage = tryResult.foldFailureFirst(Throwable::getMessage, v -> "no error");
      assertThat(errorMessage).isEqualTo("Step 2 failed");
    }

    /**
     * Why this is idiomatic: chain {@code .handleError(fn)} on the final {@code yield} result. The
     * comprehension fails into the recovery; the recovery substitutes a value.
     *
     * <p>Alternative: wrap each step in {@code handleError}. Tedious; the outer-level recovery
     * covers the entire chain.
     *
     * <p>Common wrong attempt: try to recover inside a {@code from} step and pretend the failure
     * did not happen. The cleanest place is on the outermost path after {@code yield}.
     */
    @Test
    @DisplayName("Exercise 8: Comprehension with recovery")
    void exercise8_comprehensionWithRecovery() {
      VTaskPath<String> fetchData = Path.vtaskFail(new RuntimeException("Network error"));

      // SOLUTION: Add handleError() to the result of yield() for recovery
      VTaskPath<String> result =
          ForPath.from(fetchData)
              .let(data -> data.toUpperCase())
              .yield((data, upper) -> upper)
              .handleError(ex -> "DEFAULT");

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
     * Why this is idiomatic: validate, pay, confirm — three dependent steps, each one's success
     * feeding the next. The comprehension reads as a clear top-to-bottom workflow.
     *
     * <p>Alternative: a procedural method with manual error handling. Same outcome; the
     * comprehension makes the data flow explicit.
     *
     * <p>Common wrong attempt: skip validation and start with payment. Push validation to the
     * boundary so invalid orders never reach the payment service.
     */
    @Test
    @DisplayName("Exercise 9: Order processing workflow")
    void exercise9_orderProcessingWorkflow() {
      Order order = new Order("ORD-001", 99.99);

      Function<Order, VTaskPath<Order>> validateOrder =
          o -> o.amount() > 0 ? Path.vtaskPure(o) : Path.vtaskFail(new RuntimeException("Invalid"));

      Function<Order, VTaskPath<Payment>> processPayment =
          o -> Path.vtaskPure(new Payment("TXN-" + o.id(), o.amount()));

      BiFunction<Order, Payment, VTaskPath<Confirmation>> sendConfirmation =
          (o, p) ->
              Path.vtaskPure(
                  new Confirmation(o.id(), p.transactionId(), "Order confirmed: " + o.id()));

      // SOLUTION: Chain validate -> payment -> confirmation with tuple access
      VTaskPath<String> result =
          ForPath.from(validateOrder.apply(order))
              .from(validated -> processPayment.apply(validated))
              .from(t -> sendConfirmation.apply(t._1(), t._2()))
              .yield((validated, payment, confirmation) -> confirmation.message());

      Try<String> tryResult = result.runSafe();
      assertThat(tryResult.isSuccess()).isTrue();
      assertThat(tryResult.orElse("error")).isEqualTo("Order confirmed: ORD-001");
    }

    /**
     * Why this is idiomatic: mix effectful steps ({@code from}) with pure calculations ({@code
     * let}) in the same comprehension. Cart subtotal is a pure derivation; tax lookup is an effect;
     * total is a pure derivation.
     *
     * <p>Alternative: compute the cart subtotal outside the comprehension and pass it in. Same
     * answer; the comprehension keeps every value visible to later steps.
     *
     * <p>Common wrong attempt: wrap pure calculations in {@code Path.vtaskPure} and use {@code
     * from}. Works but adds unnecessary effect overhead; reach for {@code let} when the value is
     * pure.
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
      double discountRate = 0.1;
      double taxRate = 0.08;

      VTaskPath<Cart> fetchCart = Path.vtaskPure(cart);

      // SOLUTION: Use let() for each calculation step, accessing previous values via tuple
      VTaskPath<String> result =
          ForPath.from(fetchCart)
              .let(c -> c.subtotal())
              .let(t -> t._2() * (1 - discountRate))
              .let(t -> t._3() * (1 + taxRate))
              .yield(
                  (cart1, subtotal, afterDiscount, total) -> String.format("Total: $%.2f", total));

      Try<String> tryResult = result.runSafe();
      assertThat(tryResult.isSuccess()).isTrue();
      assertThat(tryResult.orElse("error")).isEqualTo("Total: $24.30");
    }
  }
}
