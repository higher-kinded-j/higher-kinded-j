// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.io;

import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.data.TestFunctions;

/**
 * Base class for IO type class tests.
 *
 * <p>Provides common fixture creation, standardised test constants, and helper methods for all IO
 * type class tests, eliminating duplication across Functor, Applicative, and Monad tests.
 *
 * <h2>Test Constants</h2>
 *
 * <p>Standardised test values are provided to ensure consistency across all IO tests:
 *
 * <ul>
 *   <li>{@link #DEFAULT_IO_VALUE} - The primary test value (42)
 *   <li>{@link #ALTERNATIVE_IO_VALUE} - A secondary test value (24)
 * </ul>
 *
 * <h2>Fixture Methods</h2>
 *
 * <p>This base class provides fixtures for both Integer-based and String-based tests, allowing
 * flexibility in test implementations:
 *
 * <ul>
 *   <li>{@link #ioKind(Object)} - Creates an IO Kind with any value
 *   <li>{@link #stringIOKind(String)} - Creates an IO Kind with a String value
 *   <li>{@link #failingIO(RuntimeException)} - Creates an IO that throws an exception
 *   <li>{@link #stringToIntMapper()} - Maps String to Integer (String::length)
 *   <li>{@link #stringToIntFlatMapper()} - FlatMaps String to Kind&lt;IO, Integer&gt;
 * </ul>
 *
 * <h2>Lazy Evaluation Handling</h2>
 *
 * <p>Since IO computations are lazy, this base class provides an equality checker that properly
 * executes both IOs before comparing their results. This ensures that tests correctly verify the
 * behaviour of IO operations.
 */
abstract class IOTestBase extends TypeClassTestBase<IOKind.Witness, Integer, String> {

  /** Default value for IO instances in tests. */
  protected static final Integer DEFAULT_IO_VALUE = 42;

  /** Alternative value for IO instances when testing with multiple values. */
  protected static final Integer ALTERNATIVE_IO_VALUE = 24;

  /**
   * Creates an IO Kind with the specified value.
   *
   * <p>The computation is lazy - the value is wrapped in a supplier that will only be executed when
   * {@code unsafeRunSync()} is called.
   *
   * @param <A> The type of the value
   * @param value The value to wrap in an IO
   * @return An IO Kind containing the specified value
   */
  protected <A> Kind<IOKind.Witness, A> ioKind(A value) {
    return IO_OP.widen(IO.delay(() -> value));
  }

  /**
   * Creates a failing IO Kind that throws the specified exception when executed.
   *
   * <p>This is useful for testing error handling and exception propagation.
   *
   * @param <A> The type of the value (phantom type - never produced)
   * @param exception The exception to throw when the IO is executed
   * @return An IO Kind that will throw the specified exception
   */
  protected <A> Kind<IOKind.Witness, A> failingIO(RuntimeException exception) {
    return IO_OP.widen(
        IO.delay(
            () -> {
              throw exception;
            }));
  }

  /**
   * Converts a Kind to an IO instance.
   *
   * <p>This is a convenience method to make test code more readable by avoiding repeated
   * IO_OP.narrow() calls.
   *
   * @param <A> The type of the value
   * @param kind The Kind to convert
   * @return The underlying IO instance
   */
  protected <A> IO<A> narrowToIO(Kind<IOKind.Witness, A> kind) {
    return IO_OP.narrow(kind);
  }

  /**
   * Executes an IO Kind and returns its result.
   *
   * <p>This is a convenience method to make test code more readable.
   *
   * @param <A> The type of the value
   * @param kind The Kind to execute
   * @return The result of executing the IO
   */
  protected <A> A executeIO(Kind<IOKind.Witness, A> kind) {
    return IO_OP.unsafeRunSync(kind);
  }

  /**
   * Creates an IO Kind with a String value.
   *
   * @param value The String value to wrap in an IO
   * @return An IO Kind containing the specified String
   */
  protected Kind<IOKind.Witness, String> stringIOKind(String value) {
    return IO_OP.widen(IO.delay(() -> value));
  }

  /**
   * Mapper from String to Integer (String::length).
   *
   * @return A function that maps String to its length
   */
  protected Function<String, Integer> stringToIntMapper() {
    return String::length;
  }

  /**
   * FlatMapper from String to Kind&lt;IO, Integer&gt;.
   *
   * @return A function that flatmaps String to an IO containing its length
   */
  protected Function<String, Kind<IOKind.Witness, Integer>> stringToIntFlatMapper() {
    return s -> IO_OP.widen(IO.delay(() -> s.length()));
  }

  /**
   * Function Kind for String to Integer mapping.
   *
   * @return An IO Kind containing a function from String to Integer
   */
  protected Kind<IOKind.Witness, Function<String, Integer>> stringToIntFunctionKind() {
    return IO_OP.widen(IO.delay(() -> String::length));
  }

  /**
   * Combining function for two Strings producing Integer.
   *
   * @return A BiFunction that combines two Strings by summing their lengths
   */
  protected BiFunction<String, String, Integer> stringCombiningFunction() {
    return (s1, s2) -> s1.length() + s2.length();
  }

  /**
   * Second mapper from Integer to String for composition testing.
   *
   * @return A function that maps Integer to String
   */
  protected Function<Integer, String> intToStringMapper() {
    return i -> "Value:" + i;
  }

  /**
   * Test function from String to Kind&lt;IO, Integer&gt;.
   *
   * @return A function for testing monad operations
   */
  protected Function<String, Kind<IOKind.Witness, Integer>> stringTestFunction() {
    return s -> IO_OP.widen(IO.delay(() -> s.length()));
  }

  /**
   * Chain function from Integer to Kind&lt;IO, Integer&gt;.
   *
   * @return A function for testing monad chaining
   */
  protected Function<Integer, Kind<IOKind.Witness, Integer>> intChainFunction() {
    return i -> IO_OP.widen(IO.delay(() -> i * 2));
  }

  // ============================================================================
  // Integer-based Fixtures (from parent class requirements)
  // ============================================================================

  @Override
  protected Kind<IOKind.Witness, Integer> createValidKind() {
    return IO_OP.widen(IO.delay(() -> DEFAULT_IO_VALUE));
  }

  @Override
  protected Kind<IOKind.Witness, Integer> createValidKind2() {
    return ioKind(ALTERNATIVE_IO_VALUE);
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return TestFunctions.INT_TO_STRING;
  }

  @Override
  protected Function<Integer, Kind<IOKind.Witness, String>> createValidFlatMapper() {
    return i -> IO_OP.widen(IO.delay(() -> "flat:" + i));
  }

  @Override
  protected Kind<IOKind.Witness, Function<Integer, String>> createValidFunctionKind() {
    return IO_OP.widen(IO.delay(() -> TestFunctions.INT_TO_STRING));
  }

  @Override
  protected BiFunction<Integer, Integer, String> createValidCombiningFunction() {
    return (a, b) -> "Result:" + a + "," + b;
  }

  @Override
  protected Integer createTestValue() {
    return DEFAULT_IO_VALUE;
  }

  @Override
  protected Function<String, String> createSecondMapper() {
    return s -> "Transformed:" + s;
  }

  @Override
  protected Function<Integer, Kind<IOKind.Witness, String>> createTestFunction() {
    return i -> IO_OP.widen(IO.delay(() -> "test:" + i));
  }

  @Override
  protected Function<String, Kind<IOKind.Witness, String>> createChainFunction() {
    return s -> IO_OP.widen(IO.delay(() -> s + "!"));
  }

  @Override
  protected BiPredicate<Kind<IOKind.Witness, ?>, Kind<IOKind.Witness, ?>> createEqualityChecker() {
    // IO equality checker that executes both IOs and compares their results
    return (k1, k2) -> {
      // Execute both IOs and compare their results using Object.equals()
      Object v1 = IO_OP.narrow(castKind(k1)).unsafeRunSync();
      Object v2 = IO_OP.narrow(castKind(k2)).unsafeRunSync();
      return Objects.equals(v1, v2);
    };
  }

  /**
   * Helper method to safely cast Kind wildcards.
   *
   * <p>This is needed because the equality checker receives {@code Kind<IOKind.Witness, ?>} but we
   * need to narrow it to {@code IO<?>}.
   *
   * @param <A> The type of the value
   * @param kind The Kind to cast
   * @return The cast Kind
   */
  @SuppressWarnings("unchecked")
  private static <A> Kind<IOKind.Witness, A> castKind(Kind<IOKind.Witness, ?> kind) {
    return (Kind<IOKind.Witness, A>) kind;
  }
}
