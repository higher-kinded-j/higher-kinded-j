// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.trymonad;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.higherkindedj.hkt.trymonad.Try;

/**
 * Examples demonstrating {@link Try#attempt(org.higherkindedj.hkt.function.CheckedSupplier)} for
 * interop with Java APIs that throw checked exceptions.
 *
 * <p>{@code Try.of(Supplier)} is limited by the fact that a plain {@link
 * java.util.function.Supplier Supplier} cannot declare checked exceptions in its lambda body.
 * {@code Try.attempt} accepts a {@link org.higherkindedj.hkt.function.CheckedSupplier
 * CheckedSupplier}, whose {@code get()} is declared {@code throws X}, so lambdas can call {@code
 * Files.readString}, {@code Class.forName}, JDBC methods, and reflection APIs directly without a
 * wrapping try/catch.
 */
public class TryAttemptExample {

  public static void main(String[] args) throws IOException {
    TryAttemptExample example = new TryAttemptExample();
    example.successfulFileRead();
    example.missingFileBecomesFailure();
    example.reflectionInteropSuccess();
    example.reflectionInteropFailure();
  }

  /**
   * Read a real file; the checked {@link IOException} is declared on the lambda and caught by
   * {@link Try#attempt}.
   */
  public void successfulFileRead() throws IOException {
    // Create a throwaway file so the example runs deterministically.
    Path tmp = Files.createTempFile("hkj-attempt-example", ".txt");
    Files.writeString(tmp, "hello, try.attempt");
    try {
      Try<String> result = Try.attempt(() -> Files.readString(tmp));
      assertSuccess(result, "hello, try.attempt");
      System.out.println("successfulFileRead: " + result);
    } finally {
      Files.deleteIfExists(tmp);
    }
  }

  /** Missing file - the checked exception surfaces inside a {@code Failure}. */
  public void missingFileBecomesFailure() {
    Try<String> result =
        Try.attempt(() -> Files.readString(Path.of("/tmp/definitely-not-a-real-file")));
    assertFailureType(result, IOException.class);
    System.out.println("missingFileBecomesFailure: " + result);
  }

  /** Reflective class lookup - checked {@link ClassNotFoundException} handled cleanly. */
  public void reflectionInteropSuccess() {
    Try<Class<?>> result = Try.attempt(() -> Class.forName("java.lang.String"));
    assertSuccess(result, String.class);
    System.out.println("reflectionInteropSuccess: " + result);
  }

  public void reflectionInteropFailure() {
    Try<Class<?>> result = Try.attempt(() -> Class.forName("com.example.DefinitelyMissing"));
    assertFailureType(result, ClassNotFoundException.class);
    System.out.println("reflectionInteropFailure: " + result);
  }

  // ===== Tiny assertion helpers so the example self-checks when run. =====

  private static <T> void assertSuccess(Try<T> result, T expected) {
    if (!result.isSuccess()) {
      throw new AssertionError("expected Success but got " + result);
    }
    T actual;
    try {
      actual = result.get();
    } catch (Throwable t) {
      throw new AssertionError("unreachable - isSuccess() was true", t);
    }
    if (expected == null ? actual != null : !expected.equals(actual)) {
      throw new AssertionError("expected Success(" + expected + ") but got " + result);
    }
  }

  private static <T> void assertFailureType(Try<T> result, Class<? extends Throwable> expected) {
    if (!result.isFailure()) {
      throw new AssertionError("expected Failure but got " + result);
    }
    Throwable thrown;
    try {
      result.get();
      throw new AssertionError("unreachable - isFailure() was true");
    } catch (Throwable t) {
      thrown = t;
    }
    if (!expected.isInstance(thrown)) {
      throw new AssertionError(
          "expected Failure of " + expected.getSimpleName() + " but got " + thrown.getClass());
    }
  }
}
