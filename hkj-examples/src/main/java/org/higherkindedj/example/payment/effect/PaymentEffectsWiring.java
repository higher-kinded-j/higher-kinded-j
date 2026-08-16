// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.effect;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.eitherf.EitherFFunctor;
import org.higherkindedj.hkt.eitherf.EitherFKind;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.inject.Inject;
import org.jspecify.annotations.NullMarked;

/**
 * Wiring for the composed PaymentEffects type.
 *
 * <p>{@code @ComposeEffects} on {@link PaymentEffects} generates {@link PaymentEffectsSupport},
 * which carries the Inject instances, the composed Functor and the {@code BoundSet}. This class
 * adds only what a composition cannot derive: a {@link #boundSet()} wired to each algebra's own
 * Functor, and an {@link #interpret} that spells the composed witness once so callers need not.
 *
 * <p>The composed type is right-nested EitherF:
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
 */
@NullMarked
public final class PaymentEffectsWiring {

  private PaymentEffectsWiring() {}

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
    return PaymentEffectsSupport.functor(
        PaymentGatewayOpFunctor.instance(),
        FraudCheckOpFunctor.instance(),
        LedgerOpFunctor.instance(),
        NotificationOpFunctor.instance());
  }

  /**
   * Inject for PaymentGatewayOp (position 0: left).
   *
   * @return a typed Inject instance
   */
  public static Inject<
          PaymentGatewayOpKind.Witness,
          EitherFKind.Witness<
              PaymentGatewayOpKind.Witness,
              EitherFKind.Witness<
                  FraudCheckOpKind.Witness,
                  EitherFKind.Witness<LedgerOpKind.Witness, NotificationOpKind.Witness>>>>
      injectGateway() {
    return PaymentEffectsSupport.injectGateway();
  }

  /**
   * Inject for FraudCheckOp (position 1: right then left).
   *
   * @return a typed Inject instance
   */
  public static Inject<
          FraudCheckOpKind.Witness,
          EitherFKind.Witness<
              PaymentGatewayOpKind.Witness,
              EitherFKind.Witness<
                  FraudCheckOpKind.Witness,
                  EitherFKind.Witness<LedgerOpKind.Witness, NotificationOpKind.Witness>>>>
      injectFraud() {
    return PaymentEffectsSupport.injectFraud();
  }

  /**
   * Inject for LedgerOp (position 2: right, right, left).
   *
   * @return a typed Inject instance
   */
  public static Inject<
          LedgerOpKind.Witness,
          EitherFKind.Witness<
              PaymentGatewayOpKind.Witness,
              EitherFKind.Witness<
                  FraudCheckOpKind.Witness,
                  EitherFKind.Witness<LedgerOpKind.Witness, NotificationOpKind.Witness>>>>
      injectLedger() {
    return PaymentEffectsSupport.injectLedger();
  }

  /**
   * Inject for NotificationOp (position 3: right, right, right).
   *
   * @return a typed Inject instance
   */
  public static Inject<
          NotificationOpKind.Witness,
          EitherFKind.Witness<
              PaymentGatewayOpKind.Witness,
              EitherFKind.Witness<
                  FraudCheckOpKind.Witness,
                  EitherFKind.Witness<LedgerOpKind.Witness, NotificationOpKind.Witness>>>>
      injectNotification() {
    return PaymentEffectsSupport.injectNotification();
  }

  /**
   * Creates a complete BoundSet with all four bound effect instances.
   *
   * @return a BoundSet wired to the composed PaymentEffects type
   */
  public static PaymentEffectsSupport.BoundSet boundSet() {
    var f = functor();
    return new PaymentEffectsSupport.BoundSet(
        PaymentGatewayOpOps.boundTo(injectGateway(), f),
        FraudCheckOpOps.boundTo(injectFraud(), f),
        LedgerOpOps.boundTo(injectLedger(), f),
        NotificationOpOps.boundTo(injectNotification(), f));
  }

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
}
