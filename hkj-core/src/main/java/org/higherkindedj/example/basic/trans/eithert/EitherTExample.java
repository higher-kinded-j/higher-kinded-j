// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.trans.eithert;

import java.util.concurrent.CompletableFuture;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either.EitherKindHelper;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.future.CompletableFutureKindHelper;
import org.higherkindedj.hkt.future.CompletableFutureMonadError;
import org.higherkindedj.hkt.trans.either_t.EitherT;
import org.higherkindedj.hkt.trans.either_t.EitherTKind;
import org.higherkindedj.hkt.trans.either_t.EitherTMonad;

public class EitherTExample {

  // --- Setup ---

  // Assume DomainError is a sealed interface for specific errors
  // Re-defining a local DomainError to avoid dependency on the full DomainError hierarchy for this
  // isolated example.
  // In a real scenario, you would use the shared DomainError.
  record DomainError(String message) {}

  record ValidatedData(String data) {}

  record ProcessedData(String data) {}

  MonadError<CompletableFutureKind.Witness, Throwable> futureMonad =
      new CompletableFutureMonadError();
  MonadError<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, DomainError>
      eitherTMonad = new EitherTMonad<>(futureMonad);

  // --- Workflow Steps (returning Kinds) ---

  // Simulates a sync validation returning Either
  Kind<EitherKind.Witness<DomainError>, ValidatedData> validateSync(String input) {
    System.out.println("Validating synchronously...");
    if (input.isEmpty()) {
      return EitherKindHelper.wrap(Either.left(new DomainError("Input empty")));
    }
    return EitherKindHelper.wrap(Either.right(new ValidatedData("Validated:" + input)));
  }

  // Simulates an async processing step returning Future<Either>
  Kind<CompletableFutureKind.Witness, Either<DomainError, ProcessedData>> processAsync(
      ValidatedData vd) {
    System.out.println("Processing asynchronously for: " + vd.data());
    CompletableFuture<Either<DomainError, ProcessedData>> future =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                Thread.sleep(50);
              } catch (InterruptedException e) {
                /* ignore */
              }
              if (vd.data().contains("fail")) {
                return Either.left(new DomainError("Processing failed"));
              }
              return Either.right(new ProcessedData("Processed:" + vd.data()));
            });
    return CompletableFutureKindHelper.wrap(future);
  }

  // Function to run the workflow for given input
  Kind<CompletableFutureKind.Witness, Either<DomainError, ProcessedData>> runWorkflow(
      String initialInput) {

    // Start with initial data lifted into EitherT
    Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, String> initialET =
        eitherTMonad.of(initialInput);

    // Step 1: Validate (Sync Either lifted into EitherT)
    Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, ValidatedData>
        validatedET =
            eitherTMonad.flatMap(
                input -> {
                  // Call sync step returning Kind<EitherKind.Witness,...>
                  // Correction 1: Use EitherKind.Witness here
                  Kind<EitherKind.Witness<DomainError>, ValidatedData> validationResult =
                      validateSync(input);
                  // Lift the Either result into EitherT using fromEither
                  return EitherT.fromEither(futureMonad, EitherKindHelper.unwrap(validationResult));
                },
                initialET);

    // Step 2: Process (Async Future<Either> lifted into EitherT)
    Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, ProcessedData>
        processedET =
            eitherTMonad.flatMap(
                validatedData -> {
                  // Call async step returning Kind<CompletableFutureKind.Witness,...>
                  // Correction 2: Use CompletableFutureKind.Witness here for the variable type
                  Kind<CompletableFutureKind.Witness, Either<DomainError, ProcessedData>>
                      processingResultKind = processAsync(validatedData);
                  // Lift the F<Either> result directly using fromKind
                  return EitherT.fromKind(processingResultKind);
                },
                validatedET);

    // Unwrap the final EitherT to get the underlying Future<Either>
    return ((EitherT<CompletableFutureKind.Witness, DomainError, ProcessedData>) processedET)
        .value();
  }

  public void asyncWorkflowErrorHandlingExample() {
    // --- Workflow Definition using EitherT ---

    // Input data
    String inputData = "Data";
    String badInputData = "";
    String processingFailData = "Data-fail";

    // --- Execution ---
    System.out.println("--- Running Good Workflow ---");

    Kind<CompletableFutureKind.Witness, Either<DomainError, ProcessedData>> resultGoodKind =
        runWorkflow(inputData);
    System.out.println("Good Result: " + CompletableFutureKindHelper.join(resultGoodKind));
    // Expected: Right(ProcessedData[data=Processed:Validated:Data])

    System.out.println("\n--- Running Bad Input Workflow ---");

    Kind<CompletableFutureKind.Witness, Either<DomainError, ProcessedData>> resultBadInputKind =
        runWorkflow(badInputData);
    System.out.println("Bad Input Result: " + CompletableFutureKindHelper.join(resultBadInputKind));
    // Expected: Left(DomainError[message=Input empty])

    System.out.println("\n--- Running Processing Failure Workflow ---");

    Kind<CompletableFutureKind.Witness, Either<DomainError, ProcessedData>> resultProcFailKind =
        runWorkflow(processingFailData);
    System.out.println(
        "Processing Fail Result: " + CompletableFutureKindHelper.join(resultProcFailKind));
    // Expected: Left(DomainError[message=Processing failed])

  }

  public static void main(String[] args) {
    EitherTExample example = new EitherTExample();
    example.asyncWorkflowErrorHandlingExample();
  }
}
