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
 * Solution: ForPath with VTaskPath - For-Comprehensions for Virtual Threads
 *
 * <p>This file contains the complete solutions for TutorialVTaskForPath.
 */
@DisplayName("Solution: ForPath with VTaskPath")
public class TutorialVTaskForPath_Solution {

  // ===========================================================================
  // Part 1: Basic For-Comprehensions
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Basic For-Comprehensions")
  class BasicComprehensions {

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
      String errorMessage = tryResult.fold(v -> "no error", Throwable::getMessage);
      assertThat(errorMessage).isEqualTo("Step 2 failed");
    }

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
