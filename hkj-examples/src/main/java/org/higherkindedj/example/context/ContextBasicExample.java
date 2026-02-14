// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.context;

import java.util.NoSuchElementException;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.context.Context;
import org.higherkindedj.hkt.context.ContextKind;
import org.higherkindedj.hkt.context.ContextKindHelper;
import org.higherkindedj.hkt.context.ContextMonad;

/**
 * Examples demonstrating basic Context usage with Java's ScopedValue API.
 *
 * <p>Context provides a functional approach to reading from thread-scoped values. It's the Reader
 * monad pattern adapted for Java's ScopedValue, enabling context propagation that works correctly
 * with virtual threads and structured concurrency.
 *
 * <p>This example demonstrates:
 *
 * <ul>
 *   <li>Creating contexts with ask and asks
 *   <li>Transforming contexts with map and flatMap
 *   <li>Running contexts within ScopedValue bindings
 *   <li>Error handling with recover and mapError
 *   <li>Using Context with the HKT type class hierarchy
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.context.ContextBasicExample}
 *
 * @see org.higherkindedj.hkt.context.Context
 * @see org.higherkindedj.hkt.context.ContextMonad
 */
public class ContextBasicExample {

  // Define scoped values for configuration
  private static final ScopedValue<String> USER_NAME = ScopedValue.newInstance();
  private static final ScopedValue<Integer> MAX_RETRIES = ScopedValue.newInstance();
  private static final ScopedValue<Boolean> DEBUG_MODE = ScopedValue.newInstance();

  public static void main(String[] args) throws Exception {
    System.out.println("=== Context Basic Examples ===\n");

    askExample();
    asksExample();
    mapAndFlatMapExample();
    multipleValuesExample();
    errorHandlingExample();
    typeClassExample();
  }

  // ============================================================
  // ask: Read scoped value directly
  // ============================================================

  private static void askExample() throws Exception {
    System.out.println("--- ask: Reading Scoped Values ---\n");

    // Context.ask reads from a ScopedValue and returns it unchanged
    Context<String, String> getUserName = Context.ask(USER_NAME);

    // Run within a scope binding
    String result = ScopedValue.where(USER_NAME, "Alice").call(() -> getUserName.run());

    System.out.println("User name: " + result);

    // Without a binding, ScopedValue.get() throws NoSuchElementException
    try {
      getUserName.run();
      System.out.println("Unexpected: should have thrown");
    } catch (NoSuchElementException e) {
      System.out.println("Expected error when unbound: " + e.getClass().getSimpleName());
    }
    System.out.println();
  }

  // ============================================================
  // asks: Read and transform in one step
  // ============================================================

  private static void asksExample() throws Exception {
    System.out.println("--- asks: Read and Transform ---\n");

    // Context.asks reads from a ScopedValue and applies a transformation
    Context<String, String> getGreeting = Context.asks(USER_NAME, name -> "Hello, " + name + "!");

    Context<Integer, String> getRetryMessage =
        Context.asks(MAX_RETRIES, retries -> "Will retry up to " + retries + " times");

    // Run each in its appropriate scope
    String greeting = ScopedValue.where(USER_NAME, "Bob").call(() -> getGreeting.run());

    String retryMsg = ScopedValue.where(MAX_RETRIES, 3).call(() -> getRetryMessage.run());

    System.out.println(greeting);
    System.out.println(retryMsg);
    System.out.println();
  }

  // ============================================================
  // map and flatMap: Composing contexts
  // ============================================================

  private static void mapAndFlatMapExample() throws Exception {
    System.out.println("--- map and flatMap: Composition ---\n");

    // map transforms the result
    Context<String, Integer> getNameLength = Context.ask(USER_NAME).map(String::length);

    // flatMap chains context computations
    Context<String, String> formatName =
        Context.ask(USER_NAME).flatMap(name -> Context.succeed("[" + name.toUpperCase() + "]"));

    // Chaining multiple operations
    Context<String, String> elaborate =
        Context.ask(USER_NAME)
            .map(String::trim)
            .map(String::toUpperCase)
            .flatMap(upper -> Context.succeed("*** " + upper + " ***"));

    String result =
        ScopedValue.where(USER_NAME, "  charlie  ")
            .call(
                () -> {
                  System.out.println("Name length: " + getNameLength.run());
                  System.out.println("Formatted: " + formatName.run());
                  System.out.println("Elaborate: " + elaborate.run());
                  return "done";
                });

    System.out.println();
  }

  // ============================================================
  // Multiple scoped values
  // ============================================================

  private static void multipleValuesExample() throws Exception {
    System.out.println("--- Multiple Scoped Values ---\n");

    // Reading from multiple scoped values requires binding all of them
    // Each Context reads from one ScopedValue type

    Context<String, String> getName = Context.ask(USER_NAME);
    Context<Integer, Integer> getRetries = Context.ask(MAX_RETRIES);
    Context<Boolean, Boolean> isDebug = Context.ask(DEBUG_MODE);

    // Bind multiple values in nested scopes
    String result =
        ScopedValue.where(USER_NAME, "Diana")
            .where(MAX_RETRIES, 5)
            .where(DEBUG_MODE, true)
            .call(
                () -> {
                  String name = getName.run();
                  int retries = getRetries.run();
                  boolean debug = isDebug.run();

                  return String.format(
                      "Config: user=%s, retries=%d, debug=%s", name, retries, debug);
                });

    System.out.println(result);
    System.out.println();
  }

  // ============================================================
  // Error handling
  // ============================================================

  private static void errorHandlingExample() throws Exception {
    System.out.println("--- Error Handling ---\n");

    // Context can fail
    Context<String, String> mightFail =
        Context.ask(USER_NAME)
            .flatMap(
                name -> {
                  if (name.isEmpty()) {
                    return Context.fail(new IllegalArgumentException("Name cannot be empty"));
                  }
                  return Context.succeed("Valid: " + name);
                });

    // recover: Provide a default on failure
    Context<String, String> withRecovery =
        mightFail.recover(error -> "Recovered from: " + error.getMessage());

    // mapError: Transform the error type
    Context<String, String> withMappedError =
        mightFail.mapError(error -> new RuntimeException("Wrapped: " + error.getMessage(), error));

    // recoverWith: Recover with another context computation
    Context<String, String> withFallback =
        mightFail.recoverWith(error -> Context.succeed("Fallback value"));

    // Test with empty name (triggers failure)
    String recovered = ScopedValue.where(USER_NAME, "").call(() -> withRecovery.run());
    System.out.println("Recovered: " + recovered);

    // Test with valid name
    String valid = ScopedValue.where(USER_NAME, "Eve").call(() -> mightFail.run());
    System.out.println("Valid: " + valid);

    System.out.println();
  }

  // ============================================================
  // Using Context with HKT type classes
  // ============================================================

  private static void typeClassExample() throws Exception {
    System.out.println("--- HKT Type Class Usage ---\n");

    // Get the monad instance for Context<String, _>
    ContextMonad<String> monad = ContextMonad.instance();

    // Use of (from Applicative) to lift a value
    Kind<ContextKind.Witness<String>, Integer> pureKind = monad.of(42);
    Context<String, Integer> pureContext = ContextKindHelper.CONTEXT.narrow(pureKind);

    // Use map through the Functor interface
    Kind<ContextKind.Witness<String>, String> mappedKind = monad.map(n -> "Number: " + n, pureKind);
    Context<String, String> mappedContext = ContextKindHelper.CONTEXT.narrow(mappedKind);

    // Use flatMap through the Monad interface
    Function<Integer, Kind<ContextKind.Witness<String>, String>> f =
        n -> ContextKindHelper.CONTEXT.widen(Context.succeed("Value is " + n));
    Kind<ContextKind.Witness<String>, String> flatMappedKind = monad.flatMap(f, pureKind);
    Context<String, String> flatMappedContext = ContextKindHelper.CONTEXT.narrow(flatMappedKind);

    // Run them (no ScopedValue needed since these don't read from one)
    System.out.println("Pure: " + pureContext.run());
    System.out.println("Mapped: " + mappedContext.run());
    System.out.println("FlatMapped: " + flatMappedContext.run());

    // Using ask directly from Context
    Context<String, String> askContext = Context.ask(USER_NAME);
    String askResult = ScopedValue.where(USER_NAME, "Frank").call(() -> askContext.run());
    System.out.println("Ask: " + askResult);

    System.out.println();
  }
}
