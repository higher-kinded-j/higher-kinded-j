// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Declarative base for assertion-class contract tests.
 *
 * <p>Each subclass enumerates {@link Row}s describing one method (or method chain) on the assertion
 * type under test. A row carries:
 *
 * <ul>
 *   <li>a {@code label} for diagnostic reporting,
 *   <li>an optional {@code passingInput}: a subject value that should satisfy the chain,
 *   <li>an optional {@code failingInput}: a subject value that should make the chain throw {@link
 *       AssertionError},
 *   <li>the {@code chain}: a {@link Consumer} that drives the assertion methods being tested.
 * </ul>
 *
 * <p>Two {@link TestFactory}s dispatch each row as one or two dynamic tests:
 *
 * <ul>
 *   <li>happy path — invoking the chain on the passing subject must not throw.
 *   <li>failure path — invoking the chain on the failing subject must throw {@link AssertionError}.
 * </ul>
 *
 * <p>Rows missing one of the inputs are skipped for that path, which lets tests express the
 * occasional asymmetric case (e.g. a chain whose closure always throws regardless of input).
 *
 * <p>Multiple rows can target the same method when it has multiple failure branches; each row
 * covers one branch.
 *
 * @param <S> the subject the assertion runs on (e.g. {@code Either<L, R>})
 * @param <A> the assertion type produced by the entry point (e.g. {@code EitherAssert<L, R>})
 */
public abstract class AssertContract<S, A> {

  /**
   * Returns the static factory that lifts a subject into the assertion type. For example, {@code
   * EitherAssert::assertThatEither}.
   */
  protected abstract Function<S, A> entry();

  /** Returns the rows comprising the contract. */
  protected abstract Stream<Row<S, A>> rows();

  /** A {@link java.util.function.Consumer}-like that may throw any {@link Throwable}. */
  @FunctionalInterface
  protected interface ThrowingChain<A> {
    void accept(A assertion) throws Throwable;
  }

  /**
   * A single contract row. Use the static factories rather than calling the canonical constructor.
   */
  protected record Row<S, A>(
      String label, Optional<S> passingInput, Optional<S> failingInput, ThrowingChain<A> chain) {}

  /** Row asserting both a passing and a failing input for the chain. */
  protected static <S, A> Row<S, A> row(
      String label, S passingInput, S failingInput, ThrowingChain<A> chain) {
    return new Row<>(label, Optional.of(passingInput), Optional.of(failingInput), chain);
  }

  /**
   * Row asserting only that the chain throws on the failing input. Use for chains whose inner
   * closure always throws (e.g. {@code hasRightSatisfying(v -> { throw new AssertionError(); })}).
   */
  protected static <S, A> Row<S, A> failOnly(String label, S failingInput, ThrowingChain<A> chain) {
    return new Row<>(label, Optional.empty(), Optional.of(failingInput), chain);
  }

  /** Row asserting only that the chain succeeds on the passing input. */
  protected static <S, A> Row<S, A> passOnly(String label, S passingInput, ThrowingChain<A> chain) {
    return new Row<>(label, Optional.of(passingInput), Optional.empty(), chain);
  }

  @TestFactory
  Stream<DynamicTest> happy_path_passes_for_valid_inputs() {
    return rows()
        .filter(r -> r.passingInput().isPresent())
        .map(
            r ->
                dynamicTest(
                    "[passes] " + r.label(),
                    () -> r.chain().accept(entry().apply(r.passingInput().get()))));
  }

  @TestFactory
  Stream<DynamicTest> failure_path_throws_for_invalid_inputs() {
    return rows()
        .filter(r -> r.failingInput().isPresent())
        .map(
            r ->
                dynamicTest(
                    "[throws] " + r.label(),
                    () ->
                        assertThatExceptionOfType(AssertionError.class)
                            .isThrownBy(
                                () -> r.chain().accept(entry().apply(r.failingInput().get())))));
  }
}
