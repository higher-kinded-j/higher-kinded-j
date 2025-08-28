// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.workflow;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.either_t.EitherTKindHelper.EITHER_T;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.List;
import org.higherkindedj.example.order.error.DomainError;
import org.higherkindedj.example.order.model.WorkflowModels;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either_t.EitherT;
import org.higherkindedj.hkt.either_t.EitherTKind;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.unit.Unit;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedMonad;
import org.jspecify.annotations.NonNull;

/**
 * Demonstrates the power and use of the {@link org.higherkindedj.hkt.Traverse} typeclass.
 *
 * <p>This workflow extends {@link Workflow1} with an additional step that validates a collection of
 * items (promo codes). The key feature is the use of {@code traverse}, which applies an effectful
 * validation function to each item in a list and aggregates the results into a single effect.
 *
 * <h3>What `Traverse` Solves</h3>
 *
 * <p>Without {@code traverse}, if you wanted to validate a list of items where each validation
 * returned a {@code Validated<Error, Item>}, you would have to manually loop through the list, call
 * the validation function, and collect the results, all while managing the "fail-fast" or
 * error-accumulating logic yourself.
 *
 * <p>{@code traverse} abstracts this entire pattern away. You simply provide:
 *
 * <ol>
 *   <li>The collection to traverse (e.g., a {@code List<String>}).
 *   <li>A function to apply to each item (e.g., {@code String -> Validated<Error, String>}).
 *   <li>An {@link Applicative} instance that defines how to combine the effectful results.
 * </ol>
 *
 * <p>The result is a single effect wrapping the entire collection (e.g., {@code Validated<Error,
 * List<String>>}), making it easy to integrate into a larger functional workflow.
 */
public class WorkflowTraverse {

  private final @NonNull Dependencies dependencies;
  private final @NonNull OrderWorkflowSteps steps;
  private final @NonNull MonadError<CompletableFutureKind.Witness, Throwable> futureMonad;
  private final @NonNull
      MonadError<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, DomainError>
      eitherTMonad;
  private final @NonNull Applicative<ValidatedKind.Witness<DomainError>> validatedApplicative;
  private final @NonNull ListTraverse listTraverse = ListTraverse.INSTANCE;

  public WorkflowTraverse(
      @NonNull Dependencies dependencies,
      @NonNull OrderWorkflowSteps steps,
      @NonNull MonadError<CompletableFutureKind.Witness, Throwable> futureMonad,
      @NonNull
          MonadError<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, DomainError>
          eitherTMonad) {
    this.dependencies = dependencies;
    this.steps = steps;
    this.futureMonad = futureMonad;
    this.eitherTMonad = eitherTMonad;

    // Define the Semigroup for combining DomainErrors.
    // This allows the Applicative to accumulate errors.
    final Semigroup<DomainError> errorSemigroup =
        (e1, e2) -> {
          if (e1 instanceof DomainError.ValidationError(String message1)
              && e2 instanceof DomainError.ValidationError(String message2)) {
            return new DomainError.ValidationError(message1 + ", " + message2);
          }
          // Default behavior: return the first error if types don't match.
          return e1;
        };
    this.validatedApplicative = ValidatedMonad.instance(errorSemigroup);
  }

  /**
   * An effectful validation function for a single promo code. It returns a {@code Validated} which
   * is then widened to a {@code Kind} to be compatible with the {@code traverse} method signature.
   */
  private Kind<ValidatedKind.Witness<DomainError>, String> validatePromoCode(String code) {
    if (code != null && code.startsWith("PROMO") && code.length() > 5) {
      return VALIDATED.widen(Validated.valid(code));
    } else {
      return VALIDATED.widen(
          Validated.invalid(new DomainError.ValidationError("Invalid promo code format: " + code)));
    }
  }

  /** Runs the complete order processing workflow, including the new `traverse` step. */
  public Kind<CompletableFutureKind.Witness, Either<DomainError, WorkflowModels.FinalResult>> run(
      WorkflowModels.OrderData orderData) {

    dependencies.log(
        "Starting Order Workflow [WorkflowTraverse] for Order: " + orderData.orderId());

    var initialContext = WorkflowModels.WorkflowContext.start(orderData);
    var initialT = eitherTMonad.of(initialContext);

    var validationT =
        eitherTMonad.flatMap(
            ctx1 -> {
              var validatedOrderET =
                  EitherT.fromEither(
                      futureMonad, EITHER.narrow(steps.validateOrder(ctx1.initialData())));
              return eitherTMonad.map(ctx1::withValidatedOrder, validatedOrderET);
            },
            initialT);

    var promoT =
        eitherTMonad.flatMap(
            ctx2 -> {
              var codesToValidate = ctx2.initialData().promoCodes();
              Kind<ListKind.Witness, String> codesAsKind = LIST.widen(codesToValidate);

              // =======================================================================
              // This is the core Traverse logic.
              // We are "traversing" the list of promo codes.
              //
              // - `listTraverse`: The Traverse instance for List, providing the `traverse` method.
              // - `validatedApplicative`: The Applicative for Validated. This tells `traverse` HOW
              // to combine the results. Because our Semigroup for DomainError concatenates
              // validation reasons, it will accumulate all errors from invalid promo codes.
              // - `this::validatePromoCode`: The effectful function (`String -> Validated<...>`)
              // to apply to each element in the list.
              //
              // The call effectively turns a `List<Validated<Error, String>>` inside-out
              // into a single `Validated<Error, List<String>>`.
              // =======================================================================
              Kind<ValidatedKind.Witness<DomainError>, Kind<ListKind.Witness, String>>
                  traverseResult =
                      listTraverse.traverse(
                          validatedApplicative, this::validatePromoCode, codesAsKind);

              Validated<DomainError, List<String>> validatedCodes =
                  VALIDATED.narrow(traverseResult).map(LIST::narrow);

              Either<DomainError, List<String>> eitherOfCodes =
                  validatedCodes.fold(Either::left, Either::right);

              var eitherTOfCodes = EitherT.fromEither(futureMonad, eitherOfCodes);

              return eitherTMonad.map(
                  validCodes -> {
                    var currentVO = ctx2.validatedOrder();
                    var nextVO =
                        new WorkflowModels.ValidatedOrder(
                            currentVO.orderId(),
                            currentVO.productId(),
                            currentVO.quantity(),
                            currentVO.paymentDetails(),
                            currentVO.amount(),
                            currentVO.shippingAddress(),
                            currentVO.customerId(),
                            validCodes);
                    return ctx2.withValidatedOrder(nextVO);
                  },
                  eitherTOfCodes);
            },
            validationT);

    var inventoryT =
        eitherTMonad.flatMap(
            ctx3 -> {
              var inventoryCheckET =
                  EitherT.fromKind(
                      steps.checkInventoryAsync(
                          ctx3.validatedOrder().productId(), ctx3.validatedOrder().quantity()));
              return eitherTMonad.map(ignored -> ctx3.withInventoryChecked(), inventoryCheckET);
            },
            promoT);

    var paymentT =
        eitherTMonad.flatMap(
            ctx4 -> {
              var paymentConfirmET =
                  EitherT.fromKind(
                      steps.processPaymentAsync(
                          ctx4.validatedOrder().paymentDetails(), ctx4.validatedOrder().amount()));
              return eitherTMonad.map(ctx4::withPaymentConfirmation, paymentConfirmET);
            },
            inventoryT);

    var shipmentT =
        eitherTMonad.flatMap(
            ctx5 -> {
              var shipmentAttemptET =
                  EitherT.fromKind(
                      steps.createShipmentAsync(
                          ctx5.validatedOrder().orderId(),
                          ctx5.validatedOrder().shippingAddress()));
              var recoveredShipmentET =
                  eitherTMonad.handleErrorWith(
                      shipmentAttemptET,
                      error -> {
                        if (error instanceof DomainError.ShippingError shippingError
                            && "Temporary Glitch".equals(shippingError.reason())) {
                          return eitherTMonad.of(
                              new WorkflowModels.ShipmentInfo("DEFAULT_SHIPPING_USED"));
                        }
                        return eitherTMonad.raiseError(error);
                      });
              return eitherTMonad.map(ctx5::withShipmentInfo, recoveredShipmentET);
            },
            paymentT);

    var finalResultT =
        eitherTMonad.flatMap(
            finalContext -> {
              var finalResult =
                  new WorkflowModels.FinalResult(
                      finalContext.validatedOrder().orderId(),
                      finalContext.paymentConfirmation().transactionId(),
                      finalContext.shipmentInfo().trackingId());
              var notifyET =
                  EitherT.fromKind(
                      steps.notifyCustomerAsync(
                          finalContext.initialData().customerId(),
                          "Order processed: " + finalResult.orderId()));
              var recoveredNotifyET =
                  eitherTMonad.handleError(
                      notifyET,
                      notifyError -> {
                        dependencies.log("WARN: Notification failed: " + notifyError.message());
                        return Unit.INSTANCE;
                      });
              return eitherTMonad.map(ignored -> finalResult, recoveredNotifyET);
            },
            shipmentT);

    var finalConcreteET = EITHER_T.narrow(finalResultT);
    return finalConcreteET.value();
  }
}
