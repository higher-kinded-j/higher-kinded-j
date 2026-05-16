// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.instances.Witnesses.*;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.context.Context;
import org.higherkindedj.hkt.context.ContextKind;
import org.higherkindedj.hkt.context.ContextKindHelper;
import org.higherkindedj.hkt.instances.Instances;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Solution for Tutorial02 ContextComposition — teaching-solution format.
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
@DisplayName("Tutorial 02: Context Composition - Solutions")
public class Tutorial02_ContextComposition_Solution {

  private static final ScopedValue<String> USER_NAME = ScopedValue.newInstance();
  private static final ScopedValue<Integer> USER_ID = ScopedValue.newInstance();

  @Nested
  @DisplayName("Part 1: Chaining with flatMap")
  class ChainingWithFlatMap {

    /**
     * Why this is idiomatic: {@code flatMap} threads the read result into the next context. Read
     * the name, then succeed with a greeting computed from it — one chained context.
     *
     * <p>Alternative: {@code Context.asks(USER_NAME, name -> "Hello, " + name + "!")}. Equivalent
     * for one transform; reach for {@code flatMap} when subsequent steps may fail or read
     * additional scoped values.
     *
     * <p>Common wrong attempt: nest {@code map} where {@code flatMap} is wanted. {@code map}
     * returns a {@code Context<R, Context<R, B>>}; {@code flatMap} flattens the nesting.
     */
    @Test
    @DisplayName("Exercise 1: Basic flatMap chaining")
    void exercise1_basicFlatMap() throws Exception {
      Context<String, String> getName = Context.ask(USER_NAME);

      // SOLUTION: Use flatMap to chain computations
      Context<String, String> greeting =
          getName.flatMap(name -> Context.succeed("Hello, " + name + "!"));

      String result = ScopedValue.where(USER_NAME, "Alice").call(() -> greeting.run());
      assertThat(result).isEqualTo("Hello, Alice!");
    }

    /**
     * Why this is idiomatic: {@code flatMap} can change the result type — a {@code Context<R,
     * String>} becomes {@code Context<R, Integer>} when the next step yields an integer.
     *
     * <p>Alternative: {@code map(String::length)} for the same answer. {@code flatMap} is the right
     * shape when the next step may itself be a context.
     *
     * <p>Common wrong attempt: assume {@code flatMap} mutates the original context. The combinator
     * returns a fresh context value; the original is unchanged.
     */
    @Test
    @DisplayName("Exercise 2: Using flatMap for dependent computation")
    void exercise2_flatMapDependent() throws Exception {
      Context<String, String> getName = Context.ask(USER_NAME);

      // SOLUTION: Use flatMap to transform the result type
      Context<String, Integer> getNameLength =
          getName.flatMap(name -> Context.succeed(name.length()));

      Integer result = ScopedValue.where(USER_NAME, "Bob").call(() -> getNameLength.run());
      assertThat(result).isEqualTo(3);
    }

    /**
     * Why this is idiomatic: chained {@code flatMap}s describe a workflow as a pipeline of
     * dependent steps. Each step builds on the previous via the binding name.
     *
     * <p>Alternative: collapse into a single {@code flatMap} with a multi-line lambda. Equivalent
     * runtime; the staged form names each step.
     *
     * <p>Common wrong attempt: mix {@code map} and {@code flatMap} arbitrarily. Use {@code flatMap}
     * when the lambda returns a {@code Context}; use {@code map} otherwise. The compiler enforces
     * this.
     */
    @Test
    @DisplayName("Exercise 3: Multiple flatMap chain")
    void exercise3_multipleFlatMaps() throws Exception {
      Context<String, String> getName = Context.ask(USER_NAME);

      // SOLUTION: Chain multiple flatMap calls
      Context<String, String> formatted =
          getName
              .flatMap(name -> Context.succeed("Hello, " + name))
              .flatMap(greeting -> Context.succeed("[" + greeting + "]"));

      String result = ScopedValue.where(USER_NAME, "Charlie").call(() -> formatted.run());
      assertThat(result).isEqualTo("[Hello, Charlie]");
    }

    /**
     * Why this is idiomatic: {@code map} is for plain transforms (A → B); {@code flatMap} is for
     * context-returning transforms (A → Context&lt;R, B&gt;). The choice is mechanical — pick
     * whichever the lambda returns.
     *
     * <p>Alternative: always use {@code flatMap} and wrap pure values in {@code Context.succeed}.
     * Same answer; the {@code map} version reads cleaner for pure transforms.
     *
     * <p>Common wrong attempt: assume {@code map} and {@code flatMap} are interchangeable. They
     * differ in whether the result is wrapped or flat; the compiler will tell you which one to
     * pick.
     */
    @Test
    @DisplayName("Exercise 4: map vs flatMap")
    void exercise4_mapVsFlatMap() throws Exception {
      Context<String, String> getName = Context.ask(USER_NAME);

      // SOLUTION: Use map for simple transformations
      Context<String, String> upperName = getName.map(String::toUpperCase);

      // SOLUTION: Use flatMap when returning a Context
      Context<String, String> userLabel = getName.flatMap(name -> Context.succeed("User: " + name));

      String upper = ScopedValue.where(USER_NAME, "diana").call(() -> upperName.run());
      String label = ScopedValue.where(USER_NAME, "diana").call(() -> userLabel.run());

      assertThat(upper).isEqualTo("DIANA");
      assertThat(label).isEqualTo("User: diana");
    }
  }

  @Nested
  @DisplayName("Part 2: Context.fail and Error Propagation")
  class FailAndErrorPropagation {

    /**
     * Why this is idiomatic: {@code Context.fail(throwable)} captures a failing computation as a
     * value. The exception only fires when the context runs.
     *
     * <p>Alternative: throw the exception directly. Loses the value — composing with other contexts
     * becomes awkward.
     *
     * <p>Common wrong attempt: assume {@code fail} returns a special value that is "checked" before
     * run. The exception fires at {@code run}, not at construction.
     */
    @Test
    @DisplayName("Exercise 5: Create a failing Context")
    void exercise5_failingContext() {
      // SOLUTION: Use Context.fail() with an exception
      Context<String, String> failing = Context.fail(new IllegalArgumentException("Invalid input"));

      try {
        failing.run();
        assertThat(false).as("Should have thrown").isTrue();
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage()).isEqualTo("Invalid input");
      }
    }

    /**
     * Why this is idiomatic: validate inside a {@code flatMap} — the lambda branches between {@code
     * Context.succeed} (valid) and {@code Context.fail} (invalid). The caller sees the exception
     * only when the validation fails.
     *
     * <p>Alternative: throw early at the boundary. Same outcome; the context-based version composes
     * with surrounding {@code flatMap} chains.
     *
     * <p>Common wrong attempt: validate in a side effect and ignore the result. The context's value
     * channel must carry both outcomes; baking the validation into a {@code Context.fail} keeps it
     * visible.
     */
    @Test
    @DisplayName("Exercise 6: Conditional failure")
    void exercise6_conditionalFailure() throws Exception {
      Context<String, String> getName = Context.ask(USER_NAME);

      // SOLUTION: Use flatMap for conditional logic
      Context<String, String> validateAndGreet =
          getName.flatMap(
              name -> {
                if (name.isEmpty()) {
                  return Context.fail(new IllegalArgumentException("Name cannot be empty"));
                }
                return Context.succeed("Hello, " + name);
              });

      String valid = ScopedValue.where(USER_NAME, "Eve").call(() -> validateAndGreet.run());
      assertThat(valid).isEqualTo("Hello, Eve");

      try {
        ScopedValue.where(USER_NAME, "").call(() -> validateAndGreet.run());
        assertThat(false).as("Should have thrown").isTrue();
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage()).isEqualTo("Name cannot be empty");
      }
    }
  }

  @Nested
  @DisplayName("Part 3: Using asUnit()")
  class UsingAsUnit {

    /**
     * Why this is idiomatic: {@code context.asUnit()} discards the result so the context can be
     * sequenced for its effect alone. Useful when the upstream computation only matters for its
     * side validation, not its return value.
     *
     * <p>Alternative: {@code context.map(x -> Unit.INSTANCE)}. Same result; {@code asUnit} is the
     * named, intent-revealing form.
     *
     * <p>Common wrong attempt: assume {@code asUnit} cancels failures. Failures still propagate;
     * only successful values are replaced by {@code Unit}.
     */
    @Test
    @DisplayName("Exercise 7: Discard result with asUnit()")
    void exercise7_asUnit() throws Exception {
      Context<String, String> getName = Context.ask(USER_NAME);

      // SOLUTION: Use asUnit() to discard the result
      Context<String, Unit> validateName =
          getName
              .flatMap(
                  name -> {
                    if (name.length() < 3) {
                      return Context.fail(new IllegalArgumentException("Name too short"));
                    }
                    return Context.succeed(name);
                  })
              .asUnit();

      Unit result = ScopedValue.where(USER_NAME, "Frank").call(() -> validateName.run());
      assertThat(result).isEqualTo(Unit.INSTANCE);

      try {
        ScopedValue.where(USER_NAME, "Jo").call(() -> validateName.run());
        assertThat(false).as("Should have thrown").isTrue();
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage()).isEqualTo("Name too short");
      }
    }
  }

  @Nested
  @DisplayName("Part 4: Type Class Integration")
  class TypeClassIntegration {

    /**
     * Why this is idiomatic: {@code monad.of(value)} is the {@code Applicative.pure} operation in
     * the Monad type class — same shape as every other monad's pure. {@code Context.succeed} and
     * {@code monad.of} are equivalent here.
     *
     * <p>Alternative: {@code Context.succeed(42)} directly. Same answer; the type-class version
     * makes generic code possible.
     *
     * <p>Common wrong attempt: confuse {@code monad.of} with {@code monad.ask}. {@code of} ignores
     * the scope; {@code ask} reads from it.
     */
    @Test
    @DisplayName("Exercise 8: Using Monad.of()")
    void exercise8_monadOf() {
      Monad<ContextKind.Witness<String>> monad = Instances.monad(context());

      // SOLUTION: Use monad.of() to lift a value (Applicative's pure operation)
      Kind<ContextKind.Witness<String>, Integer> pureKind = monad.of(42);

      Context<String, Integer> context = ContextKindHelper.CONTEXT.narrow(pureKind);
      Integer result = context.run();

      assertThat(result).isEqualTo(42);
    }

    /**
     * Why this is idiomatic: the type-class {@code map} works on widened {@code Kind} values.
     * Generic code that knows nothing about {@code Context} can still apply a function to the
     * result.
     *
     * <p>Alternative: {@code context.map(fn)} on the concrete type. Same answer; the type-class
     * form is for code that needs to be polymorphic over monads.
     *
     * <p>Common wrong attempt: call {@code monad.map} on a non-widened context. The type-class API
     * expects {@code Kind}; widen with {@code CONTEXT.widen} first.
     */
    @Test
    @DisplayName("Exercise 9: Using Functor.map() through monad")
    void exercise9_functorMap() throws Exception {
      Monad<ContextKind.Witness<String>> monad = Instances.monad(context());

      Context<String, String> getName = Context.ask(USER_NAME);
      Kind<ContextKind.Witness<String>, String> nameKind = ContextKindHelper.CONTEXT.widen(getName);

      // SOLUTION: Use monad.map() with the Kind
      Kind<ContextKind.Witness<String>, Integer> lengthKind = monad.map(String::length, nameKind);

      Context<String, Integer> lengthContext = ContextKindHelper.CONTEXT.narrow(lengthKind);
      Integer result = ScopedValue.where(USER_NAME, "Grace").call(() -> lengthContext.run());

      assertThat(result).isEqualTo(5);
    }

    /**
     * Why this is idiomatic: the type-class {@code flatMap} sequences {@code Kind} values. The
     * lambda must return another widened {@code Kind} — the same shape as every monad's flatMap.
     *
     * <p>Alternative: {@code context.flatMap(fn)} on the concrete type. Same answer; the type-class
     * form is what generic interpreters use.
     *
     * <p>Common wrong attempt: forget to widen the lambda's return value. The type-class signature
     * insists on {@code Kind} both ways.
     */
    @Test
    @DisplayName("Exercise 10: Using Monad.flatMap()")
    void exercise10_monadFlatMap() throws Exception {
      Monad<ContextKind.Witness<String>> monad = Instances.monad(context());

      Context<String, String> getName = Context.ask(USER_NAME);
      Kind<ContextKind.Witness<String>, String> nameKind = ContextKindHelper.CONTEXT.widen(getName);

      // SOLUTION: Use monad.flatMap() with widened result
      Kind<ContextKind.Witness<String>, String> greetingKind =
          monad.flatMap(
              name -> ContextKindHelper.CONTEXT.widen(Context.succeed("Hello, " + name)), nameKind);

      Context<String, String> greetingContext = ContextKindHelper.CONTEXT.narrow(greetingKind);
      String result = ScopedValue.where(USER_NAME, "Henry").call(() -> greetingContext.run());

      assertThat(result).isEqualTo("Hello, Henry");
    }
  }

  @Nested
  @DisplayName("Bonus: Real-World Patterns")
  class RealWorldPatterns {

    /**
     * Why this is idiomatic: a real workflow stitches read + validate + lookup + format into one
     * composed context. The pipeline reads top-to-bottom and the caller binds the scope at the very
     * end.
     *
     * <p>Alternative: imperative validation with throws. Equivalent runtime; the context form makes
     * the composition reusable and the failure path typed.
     *
     * <p>Common wrong attempt: bind the scoped value at the start of the pipeline instead of at
     * {@code call}. The bindings only live inside the call lambda; bind once, run inside it.
     */
    @Test
    @DisplayName("Complete composition workflow")
    void completeCompositionWorkflow() throws Exception {
      Context<Integer, Integer> getUserId = Context.ask(USER_ID);

      Context<Integer, Integer> validateId =
          getUserId.flatMap(
              id -> {
                if (id <= 0) {
                  return Context.fail(new IllegalArgumentException("Invalid user ID"));
                }
                return Context.succeed(id);
              });

      Context<Integer, String> lookupUser = validateId.flatMap(id -> Context.succeed("User" + id));

      Context<Integer, String> welcomeMessage =
          lookupUser.map(name -> "Welcome back, " + name + "!");

      String result = ScopedValue.where(USER_ID, 42).call(() -> welcomeMessage.run());

      assertThat(result).isEqualTo("Welcome back, User42!");

      try {
        ScopedValue.where(USER_ID, -1).call(() -> welcomeMessage.run());
        assertThat(false).as("Should have thrown").isTrue();
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage()).isEqualTo("Invalid user ID");
      }
    }
  }
}
