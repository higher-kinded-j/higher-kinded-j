// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.future;

import module java.base;
import module org.higherkindedj.core;

import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.future.CompletableFutureMonad;

/** see {<a href="https://higher-kinded-j.github.io/cf_monad.html">CompletableFuture Monad</a>} */
public class CompletableFutureExample {
  /** Java 25 instance main method - no static modifier or String[] args required. */
  void main() {
    createExample();
    monadExample();
    errorHandlingExample();
    monadUtilityMethodsExample();
  }

  public void errorHandlingExample() {
    // Get the MonadError instance
    CompletableFutureMonad futureMonad = CompletableFutureMonad.INSTANCE;
    RuntimeException runtimeEx = new IllegalStateException("Processing Failed");
    IOException checkedEx = new IOException("File Not Found");

    Kind<CompletableFutureKind.Witness, String> failedRuntimeKind =
        futureMonad.raiseError(runtimeEx);
    Kind<CompletableFutureKind.Witness, String> failedCheckedKind =
        futureMonad.raiseError(checkedEx);
    Kind<CompletableFutureKind.Witness, String> successKind = futureMonad.of("Original Success");

    // --- Handler Function ---
    // Function<Throwable, Kind<CompletableFutureKind.Witness, String>>
    Function<Throwable, Kind<CompletableFutureKind.Witness, String>> recoveryHandler =
        error -> {
          System.out.println("Handling error: " + error.getMessage());
          if (error instanceof IOException) {
            // Recover from specific checked exceptions
            return futureMonad.of("Recovered from IO Error");
          } else if (error instanceof IllegalStateException) {
            // Recover from specific runtime exceptions
            return FUTURE.widen(
                CompletableFuture.supplyAsync(
                    () -> {
                      System.out.println("Async recovery..."); // Recovery can be async too!
                      return "Recovered from State Error (async)";
                    }));
          } else if (error instanceof ArithmeticException) {
            // Recover from ArithmeticException
            return futureMonad.of("Recovered from Arithmetic Error: " + error.getMessage());
          } else {
            // Re-raise unhandled errors
            System.out.println("Unhandled error type: " + error.getClass().getSimpleName());
            return futureMonad.raiseError(new RuntimeException("Recovery failed", error));
          }
        };

    // --- Applying Handler ---

    // Handle RuntimeException
    Kind<CompletableFutureKind.Witness, String> recoveredRuntime =
        futureMonad.handleErrorWith(failedRuntimeKind, recoveryHandler);
    System.out.println("Recovered (Runtime): " + FUTURE.join(recoveredRuntime));
    // Output:
    // Handling error: Processing Failed
    // Async recovery...
    // Recovered (Runtime): Recovered from State Error (async)

    // Handle CheckedException
    Kind<CompletableFutureKind.Witness, String> recoveredChecked =
        futureMonad.handleErrorWith(failedCheckedKind, recoveryHandler);
    System.out.println("Recovered (Checked): " + FUTURE.join(recoveredChecked));
    // Output:
    // Handling error: File Not Found
    // Recovered (Checked): Recovered from IO Error

    // Handler is ignored for success
    Kind<CompletableFutureKind.Witness, String> handledSuccess =
        futureMonad.handleErrorWith(
            successKind, recoveryHandler // This handler is never called
            );
    System.out.println("Handled (Success): " + FUTURE.join(handledSuccess));
    // Output: Handled (Success): Original Success

    // Example of re-raising an unhandled error
    ArithmeticException unhandledEx = new ArithmeticException("Bad Maths");
    Kind<CompletableFutureKind.Witness, String> failedUnhandledKind =
        futureMonad.raiseError(unhandledEx);
    Kind<CompletableFutureKind.Witness, String> failedRecovery =
        futureMonad.handleErrorWith(failedUnhandledKind, recoveryHandler);

    try {
      FUTURE.join(failedRecovery);
    } catch (CompletionException e) { // join wraps the "Recovery failed" exception
      System.err.println("Caught re-raised error: " + e.getCause());
      System.err.println("  Original cause: " + e.getCause().getCause());
    }
    // Output:
    // Handling error: Bad Maths
  }

  public void createExample() {
    // Get the MonadError instance
    CompletableFutureMonad futureMonad = CompletableFutureMonad.INSTANCE;

    // --- Using of() ---
    // Creates a Kind wrapping an already completed future
    Kind<CompletableFutureKind.Witness, String> successKind = futureMonad.of("Success!");

    // --- Using raiseError() ---
    // Creates a Kind wrapping an already failed future
    RuntimeException error = new RuntimeException("Something went wrong");
    Kind<CompletableFutureKind.Witness, String> failureKind = futureMonad.raiseError(error);

    // --- Wrapping existing CompletableFutures ---
    CompletableFuture<Integer> existingFuture =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                TimeUnit.MILLISECONDS.sleep(20);
              } catch (InterruptedException e) {
                /* ignore */
              }
              return 123;
            });
    Kind<CompletableFutureKind.Witness, Integer> wrappedExisting = FUTURE.widen(existingFuture);

    CompletableFuture<Integer> failedExisting = new CompletableFuture<>();
    failedExisting.completeExceptionally(new IllegalArgumentException("Bad input"));
    Kind<CompletableFutureKind.Witness, Integer> wrappedFailed = FUTURE.widen(failedExisting);

    // You typically don't interact with 'unwrap' unless needed at boundaries or for helper methods
    // like 'join'.
    CompletableFuture<String> unwrappedSuccess = FUTURE.narrow(successKind);
    CompletableFuture<String> unwrappedFailure = FUTURE.narrow(failureKind);
  }

  public void monadExample() {
    // Get the MonadError instance
    CompletableFutureMonad futureMonad = CompletableFutureMonad.INSTANCE;

    // --- map (thenApply) ---
    Kind<CompletableFutureKind.Witness, Integer> initialValueKind = futureMonad.of(10);
    Kind<CompletableFutureKind.Witness, String> mappedKind =
        futureMonad.map(value -> "Result: " + value, initialValueKind);
    // Join for testing/demonstration
    System.out.println("Map Result: " + FUTURE.join(mappedKind)); // Output: Result: 10

    // --- flatMap (thenCompose) ---
    // Function A -> Kind<F, B>
    Function<String, Kind<CompletableFutureKind.Witness, String>> asyncStep2 =
        input -> FUTURE.widen(CompletableFuture.supplyAsync(() -> input + " -> Step2 Done"));

    Kind<CompletableFutureKind.Witness, String> flatMappedKind =
        futureMonad.flatMap(
            asyncStep2, mappedKind // Result from previous map step ("Result: 10")
            );
    System.out.println(
        "FlatMap Result: " + FUTURE.join(flatMappedKind)); // Output: Result: 10 -> Step2 Done

    // --- ap (thenCombine) ---
    Kind<CompletableFutureKind.Witness, Function<Integer, String>> funcKind =
        futureMonad.of(i -> "FuncResult:" + i);
    Kind<CompletableFutureKind.Witness, Integer> valKind = futureMonad.of(25);

    Kind<CompletableFutureKind.Witness, String> apResult = futureMonad.ap(funcKind, valKind);
    System.out.println("Ap Result: " + FUTURE.join(apResult)); // Output: FuncResult:25

    // --- mapN ---
    Kind<CompletableFutureKind.Witness, Integer> f1 = futureMonad.of(5);
    Kind<CompletableFutureKind.Witness, String> f2 = futureMonad.of("abc");

    BiFunction<Integer, String, String> combine = (i, s) -> s + i;
    Kind<CompletableFutureKind.Witness, String> map2Result = futureMonad.map2(f1, f2, combine);
    System.out.println("Map2 Result: " + FUTURE.join(map2Result)); // Output: abc5
  }

  public void monadUtilityMethodsExample() {
    CompletableFutureMonad futureMonad = CompletableFutureMonad.INSTANCE;
    Kind<CompletableFutureKind.Witness, Integer> initialValueKind = futureMonad.of(10);
    Function<String, Kind<CompletableFutureKind.Witness, String>> asyncStep2 =
        input -> FUTURE.widen(CompletableFuture.supplyAsync(() -> input + " -> Step2 Done"));

    // Chain of operations
    Kind<CompletableFutureKind.Witness, String> mappedKind =
        futureMonad.map(value -> "Result: " + value, initialValueKind);

    // ✨ Use `peek` to log the intermediate result of the async chain.
    Kind<CompletableFutureKind.Witness, String> peekedKind =
        futureMonad.peek(
            result -> System.out.println("DEBUG: Intermediate result is '" + result + "'"),
            mappedKind);

    Kind<CompletableFutureKind.Witness, String> flatMappedKind =
        futureMonad.flatMap(asyncStep2, peekedKind);

    // ✨ Use `as` to transform the successful result into a simple boolean signal.
    // This is useful when you only care that the pipeline succeeded, not what the value was.
    Kind<CompletableFutureKind.Witness, Boolean> completionSignal =
        futureMonad.as(true, flatMappedKind);

    System.out.println("Pipeline completed successfully: " + FUTURE.join(completionSignal));
  }
}
