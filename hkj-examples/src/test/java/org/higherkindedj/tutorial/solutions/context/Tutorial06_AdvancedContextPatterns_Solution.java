// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.MaybeAssert.assertThatMaybe;

import java.security.Principal;
import java.util.Set;
import org.higherkindedj.hkt.context.Context;
import org.higherkindedj.hkt.context.SecurityContext;
import org.higherkindedj.hkt.maybe.Maybe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Solution for Tutorial06 AdvancedContextPatterns — teaching-solution format.
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
@DisplayName("Tutorial 06: Advanced Context Patterns - Solutions")
public class Tutorial06_AdvancedContextPatterns_Solution {

  private static final ScopedValue<String> CONFIG_VALUE = ScopedValue.newInstance();

  @Nested
  @DisplayName("Part 1: Error Recovery")
  class ErrorRecovery {

    /**
     * Why this is idiomatic: {@code context.recover(error -> default)} replaces a failing context
     * with a default value. The success path runs through unchanged; recovery only fires when
     * validation fails.
     *
     * <p>Alternative: wrap the whole context in a {@code try/catch}. Same outcome; the recover
     * combinator stays inside the {@code Context} algebra.
     *
     * <p>Common wrong attempt: assume {@code recover} runs even on success. The success value
     * passes through; the recovery function only fires on failure.
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

      // SOLUTION: Use recover() with a default value
      Context<String, String> withDefault = validateName.recover(error -> "default");

      String valid = ScopedValue.where(CONFIG_VALUE, "Alice").call(() -> withDefault.run());
      assertThat(valid).isEqualTo("Alice");

      String recovered = ScopedValue.where(CONFIG_VALUE, "").call(() -> withDefault.run());
      assertThat(recovered).isEqualTo("default");
    }

    /**
     * Why this is idiomatic: the recovery lambda receives the {@code Throwable}, so the fallback
     * can incorporate the error message or class. Useful when the error informs the recovery (e.g.
     * picking a backup endpoint by error code).
     *
     * <p>Alternative: a constant default that ignores the error. Same shape; pick whichever the
     * recovery semantics need.
     *
     * <p>Common wrong attempt: assume the recovery throws if the lambda runs. It does not — the
     * recovery is the new success value.
     */
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

    /**
     * Why this is idiomatic: {@code recoverWith} returns a fallback {@code Context}, not a plain
     * value. The fallback may itself read scoped values or fail; the chain keeps going.
     *
     * <p>Alternative: {@code recover(e -> default)} for a constant fallback. Use {@code
     * recoverWith} when the fallback is itself a context.
     *
     * <p>Common wrong attempt: return a plain value from {@code recoverWith}. The lambda must
     * return another {@code Context}; widen with {@code Context.succeed} for plain values.
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

    /**
     * Why this is idiomatic: {@code mapError(fn)} replaces the failure's exception type while
     * keeping the failure mode. The new exception preserves the original as its cause for
     * diagnostics.
     *
     * <p>Alternative: catch and rethrow at the call boundary. Equivalent; the combinator
     * centralises the wrapping.
     *
     * <p>Common wrong attempt: drop the cause when wrapping. The original stack trace is invaluable
     * in production; pass it as the {@code cause} parameter.
     */
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

    /**
     * Why this is idiomatic: chain {@code mapError} (transform the error) with {@code recover}
     * (replace it with a value). The pipeline reads "first wrap the error, then recover from it".
     *
     * <p>Alternative: do both in one {@code recover} that inspects the original error. Same answer;
     * the staged form keeps each transform single-purpose.
     *
     * <p>Common wrong attempt: swap the order ({@code recover} first, then {@code mapError}). After
     * {@code recover} the value is a success; {@code mapError} has nothing to wrap.
     */
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

    /**
     * Why this is idiomatic: {@code toMaybe()} on a successful context yields a {@code Just} with
     * the value. The conversion bridges the {@code Context} effect into {@code Maybe} for callers
     * that prefer presence/absence semantics.
     *
     * <p>Alternative: {@code Maybe.just(context.run())}. Same answer; the bridge runs in scope and
     * converts in one call.
     *
     * <p>Common wrong attempt: call {@code toMaybe} outside the binding scope. The conversion still
     * needs the bindings to read; bind first, convert second.
     */
    @Test
    @DisplayName("Exercise 6: toMaybe with success")
    void exercise6_toMaybeSuccess() throws Exception {
      Context<String, String> getName = Context.ask(CONFIG_VALUE);

      // SOLUTION: Call toMaybe() within the scope
      Maybe<String> result = ScopedValue.where(CONFIG_VALUE, "value").call(() -> getName.toMaybe());

      assertThatMaybe(result).isJust();
      assertThat(result.orElse("none")).isEqualTo("value");
    }

    /**
     * Why this is idiomatic: {@code toMaybe()} on a failing context yields {@code Nothing}. The
     * exception is swallowed in favour of a typed absence; ideal when the caller only cares whether
     * a value exists.
     *
     * <p>Alternative: catch the exception manually and return {@code Maybe.nothing()}. Same
     * outcome; the bridge does the catch for you.
     *
     * <p>Common wrong attempt: assume {@code toMaybe} preserves the exception. It does not — for
     * diagnostic-preserving conversion use {@code toEither} or {@code toTry}.
     */
    @Test
    @DisplayName("Exercise 7: toMaybe with failure")
    void exercise7_toMaybeFailure() {
      Context<String, String> failing = Context.fail(new RuntimeException("Something went wrong"));

      // SOLUTION: Use toMaybe() to convert failure to Nothing
      Maybe<String> result = failing.toMaybe();

      assertThatMaybe(result).isNothing();
      assertThat(result.orElse("fallback")).isEqualTo("fallback");
    }
  }

  @Nested
  @DisplayName("Part 4: Graceful Degradation")
  class GracefulDegradation {

    /**
     * Why this is idiomatic: gracefully degrade by recovering from the {@code requireRole} failure
     * with a less-privileged dashboard. Admins see the full UI; users see the basic version — no
     * exception leaks to the caller.
     *
     * <p>Alternative: branch on {@code hasRole} explicitly. Same answer; the recover-style version
     * reads top-down.
     *
     * <p>Common wrong attempt: throw on the unauthorised path. The endpoint should present a
     * different view, not crash; recover keeps the user in the app.
     */
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

    /**
     * Why this is idiomatic: branch on {@code isAuthenticated()} and produce different content per
     * branch. The conditional logic stays inside the {@code Context}; the caller only sees a final
     * string.
     *
     * <p>Alternative: a top-level {@code if} on the principal. Same answer; the context-driven
     * version composes with surrounding {@code flatMap} chains.
     *
     * <p>Common wrong attempt: check authentication outside the context and bind the result. The
     * branch must run inside the scope; do it inside {@code flatMap}.
     */
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

    /**
     * Why this is idiomatic: chain {@code recoverWith} calls to define a tiered fallback — primary,
     * then cache, then static data. The first one to succeed wins.
     *
     * <p>Alternative: nested {@code try/catch} blocks. Equivalent runtime; the recover chain reads
     * top-to-bottom in priority order.
     *
     * <p>Common wrong attempt: assume the order of recovery does not matter. Each {@code
     * recoverWith} runs only when its predecessor fails; the order is the priority.
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
