// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.effect;

import static org.higherkindedj.hkt.util.validation.Operation.CONSTRUCTION;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.eitherf.EitherFFunctor;
import org.higherkindedj.hkt.eitherf.EitherFKind;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.inject.Inject;
import org.higherkindedj.hkt.inject.InjectInstances;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.NullMarked;

/**
 * Wiring for the composed PaymentEffects type.
 *
 * <p>Provides inject instances, the composed functor, and a convenience {@link BoundSet} factory
 * for the four payment effect algebras. The composed type is right-nested EitherF:
 *
 * <pre>{@code
 * EitherFKind.Witness<
 *     PaymentGatewayOpKind.Witness,
 *     EitherFKind.Witness<
 *         FraudCheckOpKind.Witness,
 *         EitherFKind.Witness<LedgerOpKind.Witness, NotificationOpKind.Witness>>>
 * }</pre>
 *
 * <p>Java lacks type aliases, so callers should use {@code var} to avoid spelling the full type.
 * This class uses {@code @SuppressWarnings("unchecked")} on inject factory methods because {@link
 * InjectInstances} returns generically-typed injects that must be narrowed to the concrete
 * composition. These casts are safe because the runtime dispatch (Left/Right nesting) is identical.
 *
 * <p>All other code (PaymentService, interpreters, tests) is fully type-safe with no raw types.
 */
@NullMarked
public final class PaymentEffectsWiring {

  private PaymentEffectsWiring() {}

  // ===== Type abbreviations =====
  // The full composed witness is verbose. These package-private aliases keep signatures readable.
  // Callers outside this package use var.

  /** Inner: LedgerOp + NotificationOp. */
  static final EitherFFunctor<LedgerOpKind.Witness, NotificationOpKind.Witness> INNER_FUNCTOR =
      EitherFFunctor.of(LedgerOpFunctor.instance(), NotificationOpFunctor.instance());

  /** Middle: FraudCheckOp + (LedgerOp + NotificationOp). */
  static final EitherFFunctor<
          FraudCheckOpKind.Witness,
          EitherFKind.Witness<LedgerOpKind.Witness, NotificationOpKind.Witness>>
      MIDDLE_FUNCTOR = EitherFFunctor.of(FraudCheckOpFunctor.instance(), INNER_FUNCTOR);

  /**
   * Creates the composed functor for the four payment effects.
   *
   * @return a Functor for the full composed witness type
   */
  public static EitherFFunctor<
          PaymentGatewayOpKind.Witness,
          EitherFKind.Witness<
              FraudCheckOpKind.Witness,
              EitherFKind.Witness<LedgerOpKind.Witness, NotificationOpKind.Witness>>>
      functor() {
    return EitherFFunctor.of(PaymentGatewayOpFunctor.instance(), MIDDLE_FUNCTOR);
  }

  // ===== Inject instances =====

  /**
   * Inject for PaymentGatewayOp (position 0: left).
   *
   * @return a typed Inject instance
   */
  @SuppressWarnings("unchecked")
  public static Inject<
          PaymentGatewayOpKind.Witness,
          EitherFKind.Witness<
              PaymentGatewayOpKind.Witness,
              EitherFKind.Witness<
                  FraudCheckOpKind.Witness,
                  EitherFKind.Witness<LedgerOpKind.Witness, NotificationOpKind.Witness>>>>
      injectGateway() {
    Inject<?, ?> raw =
        Validation.function().require(InjectInstances.injectLeft(), "injectGateway", CONSTRUCTION);
    return (Inject<
            PaymentGatewayOpKind.Witness,
            EitherFKind.Witness<
                PaymentGatewayOpKind.Witness,
                EitherFKind.Witness<
                    FraudCheckOpKind.Witness,
                    EitherFKind.Witness<LedgerOpKind.Witness, NotificationOpKind.Witness>>>>)
        raw;
  }

  /**
   * Inject for FraudCheckOp (position 1: right then left).
   *
   * @return a typed Inject instance
   */
  @SuppressWarnings("unchecked")
  public static Inject<
          FraudCheckOpKind.Witness,
          EitherFKind.Witness<
              PaymentGatewayOpKind.Witness,
              EitherFKind.Witness<
                  FraudCheckOpKind.Witness,
                  EitherFKind.Witness<LedgerOpKind.Witness, NotificationOpKind.Witness>>>>
      injectFraud() {
    Inject<?, ?> raw =
        Validation.function()
            .require(
                InjectInstances.injectRightThen(InjectInstances.injectLeft()),
                "injectFraud",
                CONSTRUCTION);
    return (Inject<
            FraudCheckOpKind.Witness,
            EitherFKind.Witness<
                PaymentGatewayOpKind.Witness,
                EitherFKind.Witness<
                    FraudCheckOpKind.Witness,
                    EitherFKind.Witness<LedgerOpKind.Witness, NotificationOpKind.Witness>>>>)
        raw;
  }

  /**
   * Inject for LedgerOp (position 2: right, right, left).
   *
   * @return a typed Inject instance
   */
  @SuppressWarnings("unchecked")
  public static Inject<
          LedgerOpKind.Witness,
          EitherFKind.Witness<
              PaymentGatewayOpKind.Witness,
              EitherFKind.Witness<
                  FraudCheckOpKind.Witness,
                  EitherFKind.Witness<LedgerOpKind.Witness, NotificationOpKind.Witness>>>>
      injectLedger() {
    Inject<?, ?> raw =
        Validation.function()
            .require(
                InjectInstances.injectRightThen(
                    InjectInstances.injectRightThen(InjectInstances.injectLeft())),
                "injectLedger",
                CONSTRUCTION);
    return (Inject<
            LedgerOpKind.Witness,
            EitherFKind.Witness<
                PaymentGatewayOpKind.Witness,
                EitherFKind.Witness<
                    FraudCheckOpKind.Witness,
                    EitherFKind.Witness<LedgerOpKind.Witness, NotificationOpKind.Witness>>>>)
        raw;
  }

  /**
   * Inject for NotificationOp (position 3: right, right, right).
   *
   * @return a typed Inject instance
   */
  @SuppressWarnings("unchecked")
  public static Inject<
          NotificationOpKind.Witness,
          EitherFKind.Witness<
              PaymentGatewayOpKind.Witness,
              EitherFKind.Witness<
                  FraudCheckOpKind.Witness,
                  EitherFKind.Witness<LedgerOpKind.Witness, NotificationOpKind.Witness>>>>
      injectNotification() {
    Inject<?, ?> raw =
        Validation.function()
            .require(
                InjectInstances.injectRightThen(
                    InjectInstances.injectRightThen(InjectInstances.injectRight())),
                "injectNotification",
                CONSTRUCTION);
    return (Inject<
            NotificationOpKind.Witness,
            EitherFKind.Witness<
                PaymentGatewayOpKind.Witness,
                EitherFKind.Witness<
                    FraudCheckOpKind.Witness,
                    EitherFKind.Witness<LedgerOpKind.Witness, NotificationOpKind.Witness>>>>)
        raw;
  }

  // ===== BoundSet =====

  /**
   * Creates a complete BoundSet with all four bound effect instances.
   *
   * @return a BoundSet wired to the composed PaymentEffects type
   */
  public static BoundSet boundSet() {
    var f = functor();
    return new BoundSet(
        PaymentGatewayOpOps.boundTo(injectGateway(), f),
        FraudCheckOpOps.boundTo(injectFraud(), f),
        LedgerOpOps.boundTo(injectLedger(), f),
        NotificationOpOps.boundTo(injectNotification(), f));
  }

  // ===== Interpretation =====

  /**
   * Interprets a Free program built with the composed payment effect type.
   *
   * <p>This method exists because the composed witness type is long to spell. Callers can use
   * {@code var} for the program and pass it here with the combined interpreter.
   *
   * @param program the Free program to interpret
   * @param interpreter the combined natural transformation (from {@code Interpreters.combine})
   * @param monad the target monad instance
   * @param <A> the result type
   * @param <M> the target monad type
   * @return the interpreted result in monad M
   */
  public static <A, M extends WitnessArity<TypeArity.Unary>> Kind<M, A> interpret(
      Free<
              EitherFKind.Witness<
                  PaymentGatewayOpKind.Witness,
                  EitherFKind.Witness<
                      FraudCheckOpKind.Witness,
                      EitherFKind.Witness<LedgerOpKind.Witness, NotificationOpKind.Witness>>>,
              A>
          program,
      Natural<
              EitherFKind.Witness<
                  PaymentGatewayOpKind.Witness,
                  EitherFKind.Witness<
                      FraudCheckOpKind.Witness,
                      EitherFKind.Witness<LedgerOpKind.Witness, NotificationOpKind.Witness>>>,
              M>
          interpreter,
      Monad<M> monad) {
    return program.foldMap(interpreter, monad);
  }

  /**
   * Convenience record holding all Bound instances for the composed effect type.
   *
   * @param gateway bound payment gateway operations
   * @param fraud bound fraud check operations
   * @param ledger bound ledger operations
   * @param notification bound notification operations
   */
  public record BoundSet(
      PaymentGatewayOpOps.Bound<
              EitherFKind.Witness<
                  PaymentGatewayOpKind.Witness,
                  EitherFKind.Witness<
                      FraudCheckOpKind.Witness,
                      EitherFKind.Witness<LedgerOpKind.Witness, NotificationOpKind.Witness>>>>
          gateway,
      FraudCheckOpOps.Bound<
              EitherFKind.Witness<
                  PaymentGatewayOpKind.Witness,
                  EitherFKind.Witness<
                      FraudCheckOpKind.Witness,
                      EitherFKind.Witness<LedgerOpKind.Witness, NotificationOpKind.Witness>>>>
          fraud,
      LedgerOpOps.Bound<
              EitherFKind.Witness<
                  PaymentGatewayOpKind.Witness,
                  EitherFKind.Witness<
                      FraudCheckOpKind.Witness,
                      EitherFKind.Witness<LedgerOpKind.Witness, NotificationOpKind.Witness>>>>
          ledger,
      NotificationOpOps.Bound<
              EitherFKind.Witness<
                  PaymentGatewayOpKind.Witness,
                  EitherFKind.Witness<
                      FraudCheckOpKind.Witness,
                      EitherFKind.Witness<LedgerOpKind.Witness, NotificationOpKind.Witness>>>>
          notification) {}
}
