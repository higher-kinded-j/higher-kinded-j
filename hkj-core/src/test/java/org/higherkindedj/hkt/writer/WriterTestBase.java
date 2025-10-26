// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer;

import static org.higherkindedj.hkt.writer.WriterKindHelper.WRITER;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.typeclass.StringMonoid;
import org.higherkindedj.hkt.unit.Unit;

/**
 * Base class for Writer type class tests.
 *
 * <p>Provides common fixture creation, standardised test constants, and helper methods for all
 * Writer type class tests, eliminating duplication across Functor, Applicative, and Monad tests.
 *
 * <h2>Log Type</h2>
 *
 * <p>Uses {@link String} as the log type with a {@link StringMonoid} for all tests, providing
 * simple log concatenation semantics.
 *
 * <h2>Test Constants</h2>
 *
 * <p>Standardised test values are provided to ensure consistency across all Writer tests:
 *
 * <ul>
 *   <li>{@link #STRING_MONOID} - The monoid instance for combining logs
 *   <li>{@link #DEFAULT_LOG} - The default log string
 *   <li>{@link #DEFAULT_VALUE} - The default integer value
 *   <li>{@link #ALTERNATIVE_LOG} - An alternative log string for testing
 *   <li>{@link #ALTERNATIVE_VALUE} - An alternative integer value for testing
 * </ul>
 *
 * <h2>Helper Methods</h2>
 *
 * <p>Provides convenience methods for creating Writer instances and working with Kind:
 *
 * <ul>
 *   <li>{@link #writerOf(String, Object)} - Creates a Writer with a log and value
 *   <li>{@link #valueWriter(Object)} - Creates a Writer with an empty log
 *   <li>{@link #tellWriter(String)} - Creates a Writer with only a log (Unit value)
 *   <li>{@link #narrowToWriter(Kind)} - Converts a Kind to a Writer instance
 *   <li>{@link #runWriter(Kind)} - Extracts the value from a Writer Kind
 *   <li>{@link #execWriter(Kind)} - Extracts the log from a Writer Kind
 * </ul>
 */
abstract class WriterTestBase
    extends TypeClassTestBase<WriterKind.Witness<String>, Integer, String> {

  // ============================================================================
  // Test Constants - Standardised Values
  // ============================================================================

  /** Monoid instance for String log type. */
  protected static final Monoid<String> STRING_MONOID = new StringMonoid();

  /** Default log string for test Writers. */
  protected static final String DEFAULT_LOG = "Log;";

  /** Default integer value for test Writers. */
  protected static final Integer DEFAULT_VALUE = 42;

  /** Alternative log string for testing with different values. */
  protected static final String ALTERNATIVE_LOG = "AltLog;";

  /** Alternative integer value for testing with different values. */
  protected static final Integer ALTERNATIVE_VALUE = 24;

  // ============================================================================
  // Helper Methods for Writer Creation
  // ============================================================================

  /**
   * Creates a Writer with the given log and value.
   *
   * @param <A> The type of the value
   * @param log The log to include
   * @param value The value to include
   * @return A new Writer instance
   */
  protected <A> Writer<String, A> writerOf(String log, A value) {
    return new Writer<>(log, value);
  }

  /**
   * Creates a Writer with an empty log and the given value.
   *
   * @param <A> The type of the value
   * @param value The value to include
   * @return A new Writer instance with an empty log
   */
  protected <A> Writer<String, A> valueWriter(A value) {
    return Writer.value(STRING_MONOID, value);
  }

  /**
   * Creates a Writer with only a log and Unit as the value.
   *
   * @param log The log to include
   * @return A new Writer instance with Unit value
   */
  protected Writer<String, Unit> tellWriter(String log) {
    return Writer.tell(log);
  }

  /**
   * Converts a Kind to a Writer instance.
   *
   * <p>This is a convenience method to make test code more readable by avoiding repeated
   * WRITER.narrow() calls.
   *
   * @param <A> The type of the value in the Writer
   * @param kind The Kind to convert
   * @return The underlying Writer instance
   */
  protected <A> Writer<String, A> narrowToWriter(Kind<WriterKind.Witness<String>, A> kind) {
    return WRITER.narrow(kind);
  }

  /**
   * Extracts the value from a Writer Kind.
   *
   * @param <A> The type of the value
   * @param kind The Kind wrapping the Writer
   * @return The value from the Writer
   */
  protected <A> A runWriter(Kind<WriterKind.Witness<String>, A> kind) {
    return WRITER.run(kind);
  }

  /**
   * Extracts the log from a Writer Kind.
   *
   * @param <A> The type of the value (ignored)
   * @param kind The Kind wrapping the Writer
   * @return The log from the Writer
   */
  protected <A> String execWriter(Kind<WriterKind.Witness<String>, A> kind) {
    return WRITER.exec(kind);
  }

  // ============================================================================
  // Convenience Methods for Common Writer Patterns
  // ============================================================================

  /**
   * Creates a Writer with the default log and value.
   *
   * @return A Writer with DEFAULT_LOG and DEFAULT_VALUE
   */
  protected Writer<String, Integer> defaultWriter() {
    return writerOf(DEFAULT_LOG, DEFAULT_VALUE);
  }

  /**
   * Creates a Writer with an alternative log and value.
   *
   * @return A Writer with ALTERNATIVE_LOG and ALTERNATIVE_VALUE
   */
  protected Writer<String, Integer> alternativeWriter() {
    return writerOf(ALTERNATIVE_LOG, ALTERNATIVE_VALUE);
  }

  /**
   * Creates a Kind wrapping a Writer with the default log and value.
   *
   * @return A Kind wrapping the default Writer
   */
  protected Kind<WriterKind.Witness<String>, Integer> defaultKind() {
    return WRITER.widen(defaultWriter());
  }

  /**
   * Creates a Kind wrapping a Writer with an alternative log and value.
   *
   * @return A Kind wrapping the alternative Writer
   */
  protected Kind<WriterKind.Witness<String>, Integer> alternativeKind() {
    return WRITER.widen(alternativeWriter());
  }

  // ============================================================================
  // Integer-based Fixtures (from parent class requirements)
  // ============================================================================

  @Override
  protected Kind<WriterKind.Witness<String>, Integer> createValidKind() {
    return WRITER.widen(writerOf(DEFAULT_LOG, DEFAULT_VALUE));
  }

  @Override
  protected Kind<WriterKind.Witness<String>, Integer> createValidKind2() {
    return WRITER.widen(writerOf(ALTERNATIVE_LOG, ALTERNATIVE_VALUE));
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return i -> "Value:" + i;
  }

  @Override
  protected Function<Integer, Kind<WriterKind.Witness<String>, String>> createValidFlatMapper() {
    return i -> WRITER.widen(writerOf("Mapped:" + i + ";", "Result:" + i));
  }

  @Override
  protected Kind<WriterKind.Witness<String>, Function<Integer, String>> createValidFunctionKind() {
    return WRITER.widen(writerOf("Func;", i -> "Applied:" + i));
  }

  @Override
  protected BiFunction<Integer, Integer, String> createValidCombiningFunction() {
    return (a, b) -> "Combined:" + a + "," + b;
  }

  @Override
  protected Integer createTestValue() {
    return DEFAULT_VALUE;
  }

  @Override
  protected Function<String, String> createSecondMapper() {
    return String::toUpperCase;
  }

  @Override
  protected Function<Integer, Kind<WriterKind.Witness<String>, String>> createTestFunction() {
    return i -> WRITER.widen(writerOf("Test:" + i + ";", "Test:" + i));
  }

  @Override
  protected Function<String, Kind<WriterKind.Witness<String>, String>> createChainFunction() {
    return s -> WRITER.widen(writerOf("Chain:" + s + ";", s + "!"));
  }

  @Override
  protected BiPredicate<Kind<WriterKind.Witness<String>, ?>, Kind<WriterKind.Witness<String>, ?>>
      createEqualityChecker() {
    return (k1, k2) -> {
      Writer<String, ?> w1 = WRITER.narrow(k1);
      Writer<String, ?> w2 = WRITER.narrow(k2);
      return w1.equals(w2);
    };
  }
}
