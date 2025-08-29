// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.either_t;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;

import java.util.concurrent.CompletableFuture;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either_t.EitherT;
import org.higherkindedj.hkt.either_t.EitherTKind;
import org.higherkindedj.hkt.either_t.EitherTMonad;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.future.CompletableFutureMonad;

public class EitherTExample {

  // --- Setup ---
  record DomainError(String message) {}

  record ValidatedData(String data) {}

  record ProcessedData(String data) {}

  MonadError<CompletableFutureKind.Witness, Throwable> futureMonad =
      CompletableFutureMonad.INSTANCE;
  MonadError<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, DomainError>
      eitherTMonad = new EitherTMonad<>(futureMonad);

  // --- Workflow Steps (now purer, without logging) ---

  // Simulates a sync validation returning Either
  Kind<EitherKind.Witness<DomainError>, ValidatedData> validateSync(String input) {
    if (input.isEmpty()) {
      return EITHER.widen(Either.left(new DomainError("Input empty")));
    }
    return EITHER.widen(Either.right(new ValidatedData("Validated:" + input)));
  }

  // Simulates an async processing step returning Future<Either>
  Kind<CompletableFutureKind.Witness, Either<DomainError, ProcessedData>> processAsync(
      ValidatedData vd) {
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
    return FUTURE.widen(future);
  }

  // --- Original Workflow ---
  Kind<CompletableFutureKind.Witness, Either<DomainError, ProcessedData>> runWorkflow(
      String initialInput) {

    Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, String> initialET =
        eitherTMonad.of(initialInput);

    Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, ValidatedData>
        validatedET =
            eitherTMonad.flatMap(
                input -> EitherT.fromEither(futureMonad, EITHER.narrow(validateSync(input))),
                initialET);

    Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, ProcessedData>
        processedET =
            eitherTMonad.flatMap(
                validatedData -> EitherT.fromKind(processAsync(validatedData)), validatedET);

    return ((EitherT<CompletableFutureKind.Witness, DomainError, ProcessedData>) processedET)
        .value();
  }

  // --- Refactored Workflow using `peek` for logging ---
  Kind<CompletableFutureKind.Witness, Either<DomainError, ProcessedData>> runWorkflowAndLog(
      String initialInput) {

    Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, String> initialET =
        eitherTMonad.of(initialInput);

    //  Use `peek` to log the input without altering the flow.
    Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, String> loggedInitialET =
        eitherTMonad.peek(
            input -> System.out.println("LOG: Validating input -> " + input), initialET);

    Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, ValidatedData>
        validatedET =
            eitherTMonad.flatMap(
                input -> EitherT.fromEither(futureMonad, EITHER.narrow(validateSync(input))),
                loggedInitialET);

    Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, ValidatedData>
        loggedValidatedET =
            eitherTMonad.peek(v -> System.out.println("LOG: Processing data -> " + v), validatedET);

    Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, ProcessedData>
        processedET =
            eitherTMonad.flatMap(
                validatedData -> EitherT.fromKind(processAsync(validatedData)), loggedValidatedET);

    return ((EitherT<CompletableFutureKind.Witness, DomainError, ProcessedData>) processedET)
        .value();
  }

  // --- Refactored Workflow using `as` for a final status ---
  Kind<CompletableFutureKind.Witness, Either<DomainError, String>> runWorkflowAndSignalCompletion(
      String initialInput) {
    // This workflow follows the original logic...
    Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, ProcessedData>
        processedET = EitherT.fromKind(runWorkflow(initialInput));

    // ✨ Use `as` to replace the successful `ProcessedData` with a simple String message.
    // If the workflow failed, the original `Left` (error) is preserved.
    Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, String> completionET =
        eitherTMonad.as("Workflow Completed Successfully", processedET);

    return ((EitherT<CompletableFutureKind.Witness, DomainError, String>) completionET).value();
  }

  public void asyncWorkflowErrorHandlingExample() {
    String inputData = "Data";
    String badInputData = "";
    String processingFailData = "Data-fail";

    System.out.println("--- 1. Running Original Good Workflow ---");
    Kind<CompletableFutureKind.Witness, Either<DomainError, ProcessedData>> resultGoodKind =
        runWorkflow(inputData);
    System.out.println("Result: " + FUTURE.join(resultGoodKind));
    // Expected: Right(ProcessedData[data=Processed:Validated:Data])

    System.out.println("\n--- 2. Running Good Workflow with `peek` Logging ---");
    Kind<CompletableFutureKind.Witness, Either<DomainError, ProcessedData>> resultLoggedKind =
        runWorkflowAndLog(inputData);
    System.out.println("Result: " + FUTURE.join(resultLoggedKind));
    // Expected: (Logs will be printed) Right(ProcessedData[data=Processed:Validated:Data])

    System.out.println("\n--- 3. Running Workflows with `as` Completion Signal ---");
    Kind<CompletableFutureKind.Witness, Either<DomainError, String>> resultAsSuccess =
        runWorkflowAndSignalCompletion(inputData);
    System.out.println("Success Result: " + FUTURE.join(resultAsSuccess));
    // Expected: Right(Workflow Completed Successfully)

    Kind<CompletableFutureKind.Witness, Either<DomainError, String>> resultAsFailure =
        runWorkflowAndSignalCompletion(badInputData);
    System.out.println("Failure Result: " + FUTURE.join(resultAsFailure));
    // Expected: Left(DomainError[message=Input empty])
  }

  public static void main(String[] args) {
    EitherTExample example = new EitherTExample();
    example.asyncWorkflowErrorHandlingExample();
  }
}
