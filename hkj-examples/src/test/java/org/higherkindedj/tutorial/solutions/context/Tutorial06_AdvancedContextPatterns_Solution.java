// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Principal;
import java.util.Set;
import org.higherkindedj.hkt.context.Context;
import org.higherkindedj.hkt.context.SecurityContext;
import org.higherkindedj.hkt.maybe.Maybe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Solutions for Tutorial 06: Advanced Context Patterns */
@DisplayName("Tutorial 06: Advanced Context Patterns - Solutions")
public class Tutorial06_AdvancedContextPatterns_Solution {

  private static final ScopedValue<String> CONFIG_VALUE = ScopedValue.newInstance();

  @Nested
  @DisplayName("Part 1: Error Recovery")
  class ErrorRecovery {

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

      // SOLUTION: Use recover() with a default value
      Context<String, String> withDefault = validateName.recover(error -> "default");

      String valid = ScopedValue.where(CONFIG_VALUE, "Alice").call(() -> withDefault.run());
      assertThat(valid).isEqualTo("Alice");

      String recovered = ScopedValue.where(CONFIG_VALUE, "").call(() -> withDefault.run());
      assertThat(recovered).isEqualTo("default");
    }

    @Test
    @DisplayName("Exercise 2: Recover with error info")
    void exercise2_recoverWithErrorInfo() {
      Context<String, String> failing = Context.fail(new RuntimeException("Connection failed"));

      // SOLUTION: Use error message in recovery
      Context<String, String> withMessage =
          failing.recover(error -> "Error: " + error.getMessage());

      String result = withMessage.run();
      assertThat(result).isEqualTo("Error: Connection failed");
    }

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

      // SOLUTION: Use recoverWith() for fallback context
      Context<String, String> withFallback =
          primary.recoverWith(error -> Context.succeed("Fallback value"));

      String success = ScopedValue.where(CONFIG_VALUE, "data").call(() -> withFallback.run());
      assertThat(success).isEqualTo("Primary: data");

      String fallback = ScopedValue.where(CONFIG_VALUE, "fail").call(() -> withFallback.run());
      assertThat(fallback).isEqualTo("Fallback value");
    }
  }

  @Nested
  @DisplayName("Part 2: Error Transformation")
  class ErrorTransformation {

    @Test
    @DisplayName("Exercise 4: Transform error type")
    void exercise4_mapError() {
      Context<String, String> failing = Context.fail(new RuntimeException("Original error"));

      // SOLUTION: Use mapError() to wrap the exception
      Context<String, String> wrapped =
          failing.mapError(e -> new IllegalStateException("Wrapped: " + e.getMessage(), e));

      try {
        wrapped.run();
        assertThat(false).as("Should have thrown").isTrue();
      } catch (IllegalStateException e) {
        assertThat(e.getMessage()).isEqualTo("Wrapped: Original error");
        assertThat(e.getCause()).isInstanceOf(RuntimeException.class);
      }
    }

    @Test
    @DisplayName("Exercise 5: Chain mapError and recover")
    void exercise5_chainMapErrorAndRecover() {
      Context<String, String> failing = Context.fail(new RuntimeException("Database unavailable"));

      // SOLUTION: Chain mapError then recover
      Context<String, String> handled =
          failing
              .mapError(e -> new IllegalStateException(e.getMessage()))
              .recover(e -> "Recovered from: " + e.getClass().getSimpleName());

      String result = handled.run();
      assertThat(result).isEqualTo("Recovered from: IllegalStateException");
    }
  }

  @Nested
  @DisplayName("Part 3: Conversion to Maybe")
  class ConversionToMaybe {

    @Test
    @DisplayName("Exercise 6: toMaybe with success")
    void exercise6_toMaybeSuccess() throws Exception {
      Context<String, String> getName = Context.ask(CONFIG_VALUE);

      // SOLUTION: Call toMaybe() within the scope
      Maybe<String> result = ScopedValue.where(CONFIG_VALUE, "value").call(() -> getName.toMaybe());

      assertThat(result.isJust()).isTrue();
      assertThat(result.orElse("none")).isEqualTo("value");
    }

    @Test
    @DisplayName("Exercise 7: toMaybe with failure")
    void exercise7_toMaybeFailure() {
      Context<String, String> failing = Context.fail(new RuntimeException("Something went wrong"));

      // SOLUTION: Use toMaybe() to convert failure to Nothing
      Maybe<String> result = failing.toMaybe();

      assertThat(result.isNothing()).isTrue();
      assertThat(result.orElse("fallback")).isEqualTo("fallback");
    }
  }

  @Nested
  @DisplayName("Part 4: Graceful Degradation")
  class GracefulDegradation {

    @Test
    @DisplayName("Exercise 8: Role-based feature degradation")
    void exercise8_roleDegradation() throws Exception {
      Set<String> adminRoles = Set.of("user", "admin");
      Set<String> userRoles = Set.of("user");

      // SOLUTION: Use requireRole with recover for degradation
      Context<Set<String>, String> dashboard =
          SecurityContext.requireRole("admin")
              .map(u -> "Full admin dashboard")
              .recover(e -> "Basic user dashboard");

      String adminView =
          ScopedValue.where(SecurityContext.ROLES, adminRoles).call(() -> dashboard.run());
      String userView =
          ScopedValue.where(SecurityContext.ROLES, userRoles).call(() -> dashboard.run());

      assertThat(adminView).isEqualTo("Full admin dashboard");
      assertThat(userView).isEqualTo("Basic user dashboard");
    }

    @Test
    @DisplayName("Exercise 9: Auth-aware content")
    void exercise9_authAwareContent() throws Exception {
      Principal user = () -> "alice@example.com";

      // SOLUTION: Use isAuthenticated with conditional flatMap
      Context<Principal, String> content =
          SecurityContext.isAuthenticated()
              .flatMap(
                  isAuth -> {
                    if (isAuth) {
                      return SecurityContext.requireAuthenticated()
                          .map(p -> "Welcome, " + p.getName());
                    } else {
                      return Context.succeed("Please log in");
                    }
                  });

      String authed = ScopedValue.where(SecurityContext.PRINCIPAL, user).call(() -> content.run());
      assertThat(authed).isEqualTo("Welcome, alice@example.com");

      String anon =
          ScopedValue.where(SecurityContext.PRINCIPAL, (Principal) null).call(() -> content.run());
      assertThat(anon).isEqualTo("Please log in");
    }

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

      // SOLUTION: Chain recoverWith for tiered fallback
      Context<String, String> tiered =
          primary.recoverWith(e -> secondary).recoverWith(e -> fallback);

      String premium = ScopedValue.where(CONFIG_VALUE, "available").call(() -> tiered.run());
      assertThat(premium).isEqualTo("Premium data");

      String cached = ScopedValue.where(CONFIG_VALUE, "unavailable").call(() -> tiered.run());
      assertThat(cached).isEqualTo("Cached data");
    }
  }
}
