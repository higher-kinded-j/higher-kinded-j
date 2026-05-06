// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.NoSuchElementException;
import org.higherkindedj.hkt.context.Context;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Solution for Tutorial01 ContextBasics — teaching-solution format.
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
@DisplayName("Tutorial 01: Context Basics - Solutions")
public class Tutorial01_ContextBasics_Solution {

  private static final ScopedValue<String> USER_NAME = ScopedValue.newInstance();
  private static final ScopedValue<Integer> MAX_ITEMS = ScopedValue.newInstance();
  private static final ScopedValue<Boolean> DEBUG_MODE = ScopedValue.newInstance();

  @Nested
  @DisplayName("Part 1: Creating Contexts")
  class CreatingContexts {

    /**
     * Why this is idiomatic: {@code Context.ask(scopedValue)} captures "read this value from the
     * current scope" as a value. The reader runs inside a {@code ScopedValue.where (...).call(...)}
     * scope and sees the bound value.
     *
     * <p>Alternative: read the {@code ScopedValue} directly with {@code USER_NAME.get()}. The
     * {@code Context} form composes with {@code map}/{@code flatMap}; the bare get does not.
     *
     * <p>Common wrong attempt: call {@code USER_NAME.get()} outside any scope. The scoped value
     * throws {@code NoSuchElementException}; the {@code Context} value is fine until {@code run} is
     * called.
     */
    @Test
    @DisplayName("Exercise 1: Read a ScopedValue with ask()")
    void exercise1_readWithAsk() throws Exception {
      // SOLUTION: Use Context.ask() with the ScopedValue key
      Context<String, String> getUserName = Context.ask(USER_NAME);

      String result = ScopedValue.where(USER_NAME, "Alice").call(() -> getUserName.run());

      assertThat(result).isEqualTo("Alice");
    }

    /**
     * Why this is idiomatic: {@code Context.succeed(value)} is the no-op context — a value lifted
     * into the {@code Context} type without reading anything from the scope. Useful for combining
     * pure values with read results.
     *
     * <p>Alternative: pass the value directly. Loses the {@code Context} type so it cannot be
     * combined with other contexts via {@code map} or {@code flatMap}.
     *
     * <p>Common wrong attempt: assume {@code succeed} reads from a scope. It is the "ignore the
     * scope" constructor; the value is the same regardless of bindings.
     */
    @Test
    @DisplayName("Exercise 2: Create a pure value with succeed()")
    void exercise2_pureWithSucceed() {
      // SOLUTION: Use Context.succeed() to create a pure value
      Context<String, Integer> pureValue = Context.succeed(42);

      Integer result = pureValue.run();

      assertThat(result).isEqualTo(42);
    }

    /**
     * Why this is idiomatic: a {@code Context.ask} run outside any binding scope throws {@code
     * NoSuchElementException}. The exception is the JVM telling you the {@code ScopedValue} was
     * never set; encode the optionality at the call site.
     *
     * <p>Alternative: {@code Context.asks(value, default)} or wrap the run in a {@code Try}. The
     * exception is a hard signal that scope wiring is missing.
     *
     * <p>Common wrong attempt: assume {@code Context.ask} returns {@code null} for missing
     * bindings. It throws — wrap or default explicitly when absence is allowed.
     */
    @Test
    @DisplayName("Exercise 3: Unbound ScopedValue throws exception")
    void exercise3_unboundThrows() {
      Context<String, String> getUserName = Context.ask(USER_NAME);

      // SOLUTION: NoSuchElementException is thrown when ScopedValue is not bound
      assertThatThrownBy(() -> getUserName.run()).isInstanceOf(NoSuchElementException.class);
    }
  }

  @Nested
  @DisplayName("Part 2: Reading and Transforming")
  class ReadingAndTransforming {

    /**
     * Why this is idiomatic: {@code Context.asks(scopedValue, fn)} fuses read and transform into a
     * single context. The reader's job is to take the scoped value and shape it; the function is
     * the shaping.
     *
     * <p>Alternative: {@code Context.ask(value).map(fn)}. Equivalent; {@code asks} is the named
     * one-call shorthand.
     *
     * <p>Common wrong attempt: pass a function that ignores its argument. The fused form is for
     * transforms that depend on the scope value — use {@code succeed} for scope-independent values.
     */
    @Test
    @DisplayName("Exercise 4: Read and transform with asks()")
    void exercise4_readAndTransform() throws Exception {
      // SOLUTION: Use Context.asks() with a transformation function
      Context<String, String> getUpperName = Context.asks(USER_NAME, String::toUpperCase);

      String result = ScopedValue.where(USER_NAME, "bob").call(() -> getUpperName.run());

      assertThat(result).isEqualTo("BOB");
    }

    /**
     * Why this is idiomatic: {@code asks} is parametric in the result type — read a {@code String},
     * return its length as an {@code Integer}. The transform decides the result shape.
     *
     * <p>Alternative: {@code Context.ask(value).map(String::length)}. Same answer; the fused {@code
     * asks} is shorter when the read and transform belong together.
     *
     * <p>Common wrong attempt: assume the result must match the scoped value's type. Type parameter
     * B is independent of A; transform freely.
     */
    @Test
    @DisplayName("Exercise 5: Change result type with asks()")
    void exercise5_changeType() throws Exception {
      // SOLUTION: The transformation can change the result type
      Context<String, Integer> getNameLength = Context.asks(USER_NAME, String::length);

      Integer result = ScopedValue.where(USER_NAME, "Charlie").call(() -> getNameLength.run());

      assertThat(result).isEqualTo(7);
    }

    /**
     * Why this is idiomatic: format a string from a scoped int. {@code asks} reads the int and the
     * lambda builds the message. The result is a {@code Context<Integer, String>} ready to run in
     * any matching scope.
     *
     * <p>Alternative: build the format inline at the call site. Loses the named context — every
     * site that wants a "limit message" duplicates the formatting.
     *
     * <p>Common wrong attempt: build the message before binding the value. The {@code Context}
     * captures the format function; the value is supplied at run time.
     */
    @Test
    @DisplayName("Exercise 6: Format a message with asks()")
    void exercise6_formatMessage() throws Exception {
      // SOLUTION: Use string concatenation in the transformation
      Context<Integer, String> getLimitMessage =
          Context.asks(MAX_ITEMS, n -> "Limit: " + n + " items");

      String result = ScopedValue.where(MAX_ITEMS, 100).call(() -> getLimitMessage.run());

      assertThat(result).isEqualTo("Limit: 100 items");
    }
  }

  @Nested
  @DisplayName("Part 3: Transforming with map()")
  class TransformingWithMap {

    /**
     * Why this is idiomatic: {@code context.map(fn)} transforms the result without changing the
     * context's relationship to the scope. Read a name, get its length — the scoped value is
     * unchanged.
     *
     * <p>Alternative: {@code Context.asks(value, String::length)}. Equivalent; the {@code map}
     * chain is the right shape when the base context already exists.
     *
     * <p>Common wrong attempt: try to pass the scoped value into {@code map}. {@code map} sees the
     * result, not the source; use {@code asks} to access the scoped value.
     */
    @Test
    @DisplayName("Exercise 7: Transform with map()")
    void exercise7_mapTransform() throws Exception {
      Context<String, String> getName = Context.ask(USER_NAME);

      // SOLUTION: Use map() to transform the result
      Context<String, Integer> getLength = getName.map(String::length);

      Integer result = ScopedValue.where(USER_NAME, "Diana").call(() -> getLength.run());

      assertThat(result).isEqualTo(5);
    }

    /**
     * Why this is idiomatic: chained {@code map} calls describe the transformation as a pipeline —
     * trim, uppercase, decorate. The functor laws guarantee the chain fuses to a single function
     * over the result.
     *
     * <p>Alternative: a single lambda that does all three steps. Equivalent; the staged form keeps
     * each transform reviewable in isolation.
     *
     * <p>Common wrong attempt: assume each {@code map} runs eagerly. The context stays a
     * description until {@code run} fires; the chain is fused at run time.
     */
    @Test
    @DisplayName("Exercise 8: Chain multiple maps")
    void exercise8_chainMaps() throws Exception {
      Context<String, String> getName = Context.ask(USER_NAME);

      // SOLUTION: Chain multiple map() calls for step-by-step transformation
      Context<String, String> formatted =
          getName.map(String::trim).map(String::toUpperCase).map(s -> "[" + s + "]");

      String result = ScopedValue.where(USER_NAME, "  eve  ").call(() -> formatted.run());

      assertThat(result).isEqualTo("[EVE]");
    }

    /**
     * Why this is idiomatic: a boolean scoped value maps to one of two strings via a conditional
     * expression. The same context handles both bindings — bind {@code true} for DEBUG, {@code
     * false} for INFO.
     *
     * <p>Alternative: build two contexts, one per outcome, and pick at the call site. Loses the
     * symmetry — the {@code map} expression names both branches together.
     *
     * <p>Common wrong attempt: use a {@code switch} for two cases. The ternary is the idiomatic
     * Java spelling for boolean dispatch.
     */
    @Test
    @DisplayName("Exercise 9: Map boolean to string")
    void exercise9_mapBoolean() throws Exception {
      Context<Boolean, Boolean> isDebug = Context.ask(DEBUG_MODE);

      // SOLUTION: Use conditional expression in map
      Context<Boolean, String> getLogLevel = isDebug.map(debug -> debug ? "DEBUG" : "INFO");

      String debugLevel = ScopedValue.where(DEBUG_MODE, true).call(() -> getLogLevel.run());
      String infoLevel = ScopedValue.where(DEBUG_MODE, false).call(() -> getLogLevel.run());

      assertThat(debugLevel).isEqualTo("DEBUG");
      assertThat(infoLevel).isEqualTo("INFO");
    }
  }

  @Nested
  @DisplayName("Part 4: Running in Scopes")
  class RunningInScopes {

    /**
     * Why this is idiomatic: chain {@code where(key, value)} calls to bind multiple scoped values
     * in one scope. Both contexts read their respective bindings inside the same {@code call}.
     *
     * <p>Alternative: nest two {@code where(...).call(...)} calls. Same answer; the fluent chain
     * reads as one binding declaration.
     *
     * <p>Common wrong attempt: assume binding order matters. The order of {@code where} calls is
     * irrelevant; bindings are independent.
     */
    @Test
    @DisplayName("Exercise 10: Bind multiple values")
    void exercise10_multipleBindings() throws Exception {
      Context<String, String> getName = Context.ask(USER_NAME);
      Context<Integer, Integer> getMax = Context.ask(MAX_ITEMS);

      // SOLUTION: Provide the correct values in where() calls
      String userName =
          ScopedValue.where(USER_NAME, "Frank").where(MAX_ITEMS, 50).call(() -> getName.run());

      Integer maxItems =
          ScopedValue.where(USER_NAME, "Frank").where(MAX_ITEMS, 50).call(() -> getMax.run());

      assertThat(userName).isEqualTo("Frank");
      assertThat(maxItems).isEqualTo(50);
    }

    /**
     * Why this is idiomatic: {@code ScopedValue.where(...).run(Runnable)} executes a block for its
     * side effects within the binding. The {@code Context} reads the binding while the side effect
     * runs.
     *
     * <p>Alternative: {@code .call(Callable)} when a return value is needed. {@code run} is the
     * side-effect variant; same scope semantics.
     *
     * <p>Common wrong attempt: assume side effects work outside the {@code run} block. The bindings
     * only live inside the lambda; once the lambda returns, the values are unbound again.
     */
    @Test
    @DisplayName("Exercise 11: Using run() for side effects")
    void exercise11_runForSideEffects() {
      Context<String, String> getName = Context.ask(USER_NAME);
      StringBuilder log = new StringBuilder();

      // SOLUTION: Use "Grace" as the binding value
      ScopedValue.where(USER_NAME, "Grace")
          .run(
              () -> {
                String name = getName.run();
                log.append("Processing: ").append(name);
              });

      assertThat(log.toString()).isEqualTo("Processing: Grace");
    }
  }

  @Nested
  @DisplayName("Bonus: Complete Examples")
  class BonusExamples {

    /**
     * Why this is idiomatic: a complete workflow combines several contexts (greeting, limit
     * message, debug banner) into one read pipeline. Each context targets one scoped value; the
     * bindings provide all three at once.
     *
     * <p>Alternative: a single {@code asks} that reads every scoped value via separate {@code
     * .get()} calls. Loses the per-concern naming; the multi-context version makes each piece
     * reviewable in isolation.
     *
     * <p>Common wrong attempt: forget to bind every scoped value the workflow reads. Any unbound
     * scope throws; bind every value the contexts will need.
     */
    @Test
    @DisplayName("Complete workflow example")
    void completeWorkflowExample() throws Exception {
      Context<String, String> greeting = Context.asks(USER_NAME, name -> "Hello, " + name + "!");

      Context<Integer, String> itemsMessage =
          Context.asks(MAX_ITEMS, n -> "You can add up to " + n + " items.");

      Context<Boolean, String> modeMessage =
          Context.asks(DEBUG_MODE, debug -> debug ? "[DEBUG MODE ENABLED]" : "");

      String result =
          ScopedValue.where(USER_NAME, "Tutorial User")
              .where(MAX_ITEMS, 25)
              .where(DEBUG_MODE, true)
              .call(
                  () -> {
                    String greet = greeting.run();
                    String items = itemsMessage.run();
                    String mode = modeMessage.run();
                    return String.join("\n", greet, items, mode);
                  });

      assertThat(result)
          .contains("Hello, Tutorial User!")
          .contains("You can add up to 25 items.")
          .contains("[DEBUG MODE ENABLED]");
    }
  }
}
