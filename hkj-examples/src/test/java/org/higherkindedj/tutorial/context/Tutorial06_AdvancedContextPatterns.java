// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Principal;
import java.util.Set;
import org.higherkindedj.hkt.context.Context;
import org.higherkindedj.hkt.context.RequestContext;
import org.higherkindedj.hkt.context.SecurityContext;
import org.higherkindedj.hkt.maybe.Maybe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tutorial: Advanced Context Patterns - Error Handling and Recovery
 *
 * <p>Learn advanced patterns for working with Context including error recovery, error
 * transformation, conversion to other types, and building robust context-aware applications.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>recover() for providing fallback values on failure
 *   <li>recoverWith() for fallback context computations
 *   <li>mapError() for error transformation
 *   <li>toMaybe() for optional handling of failures
 *   <li>Combining multiple context types safely
 * </ul>
 *
 * <p>Requirements: Java 25+ (ScopedValue is finalised)
 *
 * <p>Estimated time: 25-30 minutes
 *
 * <p>Replace each placeholder with the correct code to make the tests pass.
 */
@DisplayName("Tutorial 06: Advanced Context Patterns")
public class Tutorial06_AdvancedContextPatterns {

  private static final ScopedValue<String> CONFIG_VALUE = ScopedValue.newInstance();

  /** Helper method for incomplete exercises. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required - replace answerRequired() with your solution");
  }

  // ===========================================================================
  // Part 1: Error Recovery with recover()
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Error Recovery")
  class ErrorRecovery {

    /**
     * Exercise 1: Recover from a failing context
     *
     * <p>recover(fn) provides a fallback value when the context computation fails.
     *
     * <p>Task: Use recover() to provide a default when validation fails
     */
    @Test
    @DisplayName("Exercise 1: Basic recover")
    void exercise1_basicRecover() throws Exception {
      Context<String, String> getName = Context.ask(CONFIG_VALUE);

      Context<String, String> validateName =
          getName.flatMap(
              name -> {
                if (name.isEmpty()) {
                  return Context.fail(new IllegalArgumentException("Empty name"));
                }
                return Context.succeed(name);
              });

      // TODO: Replace answerRequired() with:
      // validateName.recover(error -> "default")
      Context<String, String> withDefault = answerRequired();

      // With valid name
      String valid = ScopedValue.where(CONFIG_VALUE, "Alice").call(() -> withDefault.run());
      assertThat(valid).isEqualTo("Alice");

      // With empty name (triggers recovery)
      String recovered = ScopedValue.where(CONFIG_VALUE, "").call(() -> withDefault.run());
      assertThat(recovered).isEqualTo("default");
    }

    /**
     * Exercise 2: Recover with error message
     *
     * <p>The recovery function receives the error, so you can use it in the fallback.
     *
     * <p>Task: Recover using the error message
     */
    @Test
    @DisplayName("Exercise 2: Recover with error info")
    void exercise2_recoverWithErrorInfo() {
      Context<String, String> failing = Context.fail(new RuntimeException("Connection failed"));

      // TODO: Replace answerRequired() with:
      // failing.recover(error -> "Error: " + error.getMessage())
      Context<String, String> withMessage = answerRequired();

      String result = withMessage.run();
      assertThat(result).isEqualTo("Error: Connection failed");
    }

    /**
     * Exercise 3: Use recoverWith for fallback computation
     *
     * <p>recoverWith() allows recovering with another Context computation instead of a plain value.
     *
     * <p>Task: Use recoverWith to try an alternative context
     */
    @Test
    @DisplayName("Exercise 3: recoverWith for fallback context")
    void exercise3_recoverWith() throws Exception {
      Context<String, String> primary =
          Context.ask(CONFIG_VALUE)
              .flatMap(
                  val -> {
                    if (val.equals("fail")) {
                      return Context.fail(new RuntimeException("Primary failed"));
                    }
                    return Context.succeed("Primary: " + val);
                  });

      // TODO: Replace answerRequired() with:
      // primary.recoverWith(error -> Context.succeed("Fallback value"))
      Context<String, String> withFallback = answerRequired();

      // Primary succeeds
      String success = ScopedValue.where(CONFIG_VALUE, "data").call(() -> withFallback.run());
      assertThat(success).isEqualTo("Primary: data");

      // Primary fails, fallback used
      String fallback = ScopedValue.where(CONFIG_VALUE, "fail").call(() -> withFallback.run());
      assertThat(fallback).isEqualTo("Fallback value");
    }
  }

  // ===========================================================================
  // Part 2: Error Transformation with mapError()
  // ===========================================================================

  @Nested
  @DisplayName("Part 2: Error Transformation")
  class ErrorTransformation {

    /**
     * Exercise 4: Transform errors with mapError
     *
     * <p>mapError() transforms the exception type without changing the success path.
     *
     * <p>Task: Use mapError to wrap an exception
     */
    @Test
    @DisplayName("Exercise 4: Transform error type")
    void exercise4_mapError() {
      Context<String, String> failing = Context.fail(new RuntimeException("Original error"));

      // TODO: Replace answerRequired() with:
      // failing.mapError(e -> new IllegalStateException("Wrapped: " + e.getMessage(), e))
      Context<String, String> wrapped = answerRequired();

      try {
        wrapped.run();
        assertThat(false).as("Should have thrown").isTrue();
      } catch (IllegalStateException e) {
        assertThat(e.getMessage()).isEqualTo("Wrapped: Original error");
        assertThat(e.getCause()).isInstanceOf(RuntimeException.class);
      }
    }

    /**
     * Exercise 5: Chain mapError with recover
     *
     * <p>You can chain mapError and recover for sophisticated error handling.
     *
     * <p>Task: Transform the error, then recover with a typed message
     */
    @Test
    @DisplayName("Exercise 5: Chain mapError and recover")
    void exercise5_chainMapErrorAndRecover() {
      Context<String, String> failing = Context.fail(new RuntimeException("Database unavailable"));

      // TODO: Replace answerRequired() with:
      // failing
      //   .mapError(e -> new IllegalStateException(e.getMessage()))
      //   .recover(e -> "Recovered from: " + e.getClass().getSimpleName())
      Context<String, String> handled = answerRequired();

      String result = handled.run();
      assertThat(result).isEqualTo("Recovered from: IllegalStateException");
    }
  }

  // ===========================================================================
  // Part 3: Converting to Maybe
  // ===========================================================================

  @Nested
  @DisplayName("Part 3: Conversion to Maybe")
  class ConversionToMaybe {

    /**
     * Exercise 6: Convert successful context to Maybe
     *
     * <p>toMaybe() converts a Context result to Maybe, returning Nothing on failure.
     *
     * <p>Task: Use toMaybe to handle a successful context
     */
    @Test
    @DisplayName("Exercise 6: toMaybe with success")
    void exercise6_toMaybeSuccess() throws Exception {
      Context<String, String> getName = Context.ask(CONFIG_VALUE);

      // TODO: Replace answerRequired() with getName.toMaybe()
      // Note: toMaybe() runs the context, so call it within the scope
      Maybe<String> result = ScopedValue.where(CONFIG_VALUE, "value").call(() -> answerRequired());

      assertThat(result.isJust()).isTrue();
      assertThat(result.orElse("none")).isEqualTo("value");
    }

    /**
     * Exercise 7: Convert failing context to Maybe
     *
     * <p>When a context fails, toMaybe() returns Nothing instead of throwing.
     *
     * <p>Task: Use toMaybe to handle a failing context
     */
    @Test
    @DisplayName("Exercise 7: toMaybe with failure")
    void exercise7_toMaybeFailure() {
      Context<String, String> failing = Context.fail(new RuntimeException("Something went wrong"));

      // TODO: Replace answerRequired() with failing.toMaybe()
      Maybe<String> result = answerRequired();

      assertThat(result.isNothing()).isTrue();
      assertThat(result.orElse("fallback")).isEqualTo("fallback");
    }
  }

  // ===========================================================================
  // Part 4: Graceful Degradation Patterns
  // ===========================================================================

  @Nested
  @DisplayName("Part 4: Graceful Degradation")
  class GracefulDegradation {

    /**
     * Exercise 8: Feature degradation based on roles
     *
     * <p>Task: Create a context that returns full features for admins, degraded for others
     */
    @Test
    @DisplayName("Exercise 8: Role-based feature degradation")
    void exercise8_roleDegradation() throws Exception {
      Set<String> adminRoles = Set.of("user", "admin");
      Set<String> userRoles = Set.of("user");

      // Admin feature that degrades gracefully
      // TODO: Replace answerRequired() with:
      // SecurityContext.requireRole("admin")
      //   .map(u -> "Full admin dashboard")
      //   .recover(e -> "Basic user dashboard")
      Context<Set<String>, String> dashboard = answerRequired();

      String adminView =
          ScopedValue.where(SecurityContext.ROLES, adminRoles).call(() -> dashboard.run());

      String userView =
          ScopedValue.where(SecurityContext.ROLES, userRoles).call(() -> dashboard.run());

      assertThat(adminView).isEqualTo("Full admin dashboard");
      assertThat(userView).isEqualTo("Basic user dashboard");
    }

    /**
     * Exercise 9: Authentication-aware content
     *
     * <p>Task: Create content that varies based on authentication status
     */
    @Test
    @DisplayName("Exercise 9: Auth-aware content")
    void exercise9_authAwareContent() throws Exception {
      Principal user = () -> "alice@example.com";

      // TODO: Replace answerRequired() with:
      // SecurityContext.isAuthenticated()
      //   .flatMap(isAuth -> {
      //     if (isAuth) {
      //       return SecurityContext.requireAuthenticated()
      //         .map(p -> "Welcome, " + p.getName());
      //     } else {
      //       return Context.succeed("Please log in");
      //     }
      //   })
      Context<Principal, String> content = answerRequired();

      // Authenticated user
      String authed = ScopedValue.where(SecurityContext.PRINCIPAL, user).call(() -> content.run());

      assertThat(authed).isEqualTo("Welcome, alice@example.com");

      // Anonymous user
      String anon =
          ScopedValue.where(SecurityContext.PRINCIPAL, (Principal) null).call(() -> content.run());

      assertThat(anon).isEqualTo("Please log in");
    }

    /**
     * Exercise 10: Tiered service response
     *
     * <p>Task: Create a response that degrades based on available data
     */
    @Test
    @DisplayName("Exercise 10: Tiered response")
    void exercise10_tieredResponse() throws Exception {
      Context<String, String> primary =
          Context.ask(CONFIG_VALUE)
              .flatMap(
                  val -> {
                    if (val.equals("available")) {
                      return Context.succeed("Premium data");
                    }
                    return Context.fail(new RuntimeException("Primary unavailable"));
                  });

      Context<String, String> secondary = Context.succeed("Cached data");
      Context<String, String> fallback = Context.succeed("Static data");

      // TODO: Replace answerRequired() with:
      // primary
      //   .recoverWith(e -> secondary)
      //   .recoverWith(e -> fallback)
      Context<String, String> tiered = answerRequired();

      // Primary available
      String premium = ScopedValue.where(CONFIG_VALUE, "available").call(() -> tiered.run());

      assertThat(premium).isEqualTo("Premium data");

      // Primary unavailable - secondary used
      String cached = ScopedValue.where(CONFIG_VALUE, "unavailable").call(() -> tiered.run());

      assertThat(cached).isEqualTo("Cached data");
    }
  }

  // ===========================================================================
  // Bonus: Complete Resilient Service
  // ===========================================================================

  @Nested
  @DisplayName("Bonus: Complete Resilient Service")
  class CompleteResilientService {

    /** This test demonstrates a complete resilient service pattern. */
    @Test
    @DisplayName("Complete resilient service")
    void completeResilientService() throws Exception {
      Principal user = () -> "user@example.com";
      Set<String> roles = Set.of("user", "premium");
      String traceId = RequestContext.generateTraceId();

      record ServiceResponse(String content, String tier, String trace) {}

      ServiceResponse response =
          ScopedValue.where(SecurityContext.PRINCIPAL, user)
              .where(SecurityContext.ROLES, roles)
              .where(RequestContext.TRACE_ID, traceId)
              .call(
                  () -> {
                    // Determine content tier based on roles
                    String tier =
                        SecurityContext.hasRole("premium")
                            .map(isPremium -> isPremium ? "premium" : "basic")
                            .recover(e -> "guest")
                            .run();

                    // Get personalised content with fallback
                    String content =
                        SecurityContext.requireAuthenticated()
                            .map(p -> "Hello, " + p.getName())
                            .recover(e -> "Hello, Guest")
                            .run();

                    // Include trace for debugging
                    String trace = RequestContext.getTraceIdOrDefault("no-trace").substring(0, 8);

                    return new ServiceResponse(content, tier, trace);
                  });

      assertThat(response.content()).isEqualTo("Hello, user@example.com");
      assertThat(response.tier()).isEqualTo("premium");
      assertThat(response.trace()).hasSize(8);
    }
  }

  /**
   * Congratulations! You've completed Tutorial 06: Advanced Context Patterns
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>✓ How to use recover() for fallback values
   *   <li>✓ How to use recoverWith() for fallback computations
   *   <li>✓ How to transform errors with mapError()
   *   <li>✓ How to convert Context to Maybe with toMaybe()
   *   <li>✓ How to build gracefully degrading services
   * </ul>
   *
   * <p>Key Takeaways:
   *
   * <ul>
   *   <li>recover() catches failures and returns a fallback value
   *   <li>recoverWith() catches failures and runs a fallback Context
   *   <li>mapError() transforms the error type for better handling
   *   <li>toMaybe() converts errors to Nothing for optional handling
   *   <li>Combine these patterns for robust, resilient services
   * </ul>
   *
   * <p>Congratulations! You've completed the Context tutorial series!
   */
}
