// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.inject;

import java.util.Objects;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.eitherf.EitherF;
import org.higherkindedj.hkt.eitherf.EitherFKind;
import org.higherkindedj.hkt.eitherf.EitherFKindHelper;
import org.jspecify.annotations.NullMarked;

/**
 * Standard {@link Inject} instances for {@link EitherF} composition.
 *
 * <p>Provides factory methods for creating Inject instances that embed individual effect types into
 * right-nested {@code EitherF} compositions. For N effects composed as {@code EitherF<F, EitherF<G,
 * EitherF<H, I>>>}, each effect type needs an Inject instance that navigates the nesting to the
 * correct position.
 *
 * <h2>Standard instances</h2>
 *
 * <ul>
 *   <li>{@link #injectLeft()} — inject into the left of an EitherF
 *   <li>{@link #injectRight()} — inject into the right of an EitherF
 *   <li>{@link #injectRightThen(Inject)} — inject into the right, then delegate to another Inject
 * </ul>
 *
 * @see Inject
 * @see EitherF
 */
@NullMarked
public final class InjectInstances {

  private InjectInstances() {}

  /**
   * Creates an Inject instance that embeds effect type F into the left of {@code EitherF<F, G>}.
   *
   * @param <F> The effect type being injected
   * @param <G> The right effect type in the EitherF
   * @return An Inject that wraps instructions in {@link EitherF.Left}
   */
  public static <F extends WitnessArity<?>, G extends WitnessArity<?>>
      Inject<F, EitherFKind.Witness<F, G>> injectLeft() {
    return new Inject<>() {
      @Override
      public <A> Kind<EitherFKind.Witness<F, G>, A> inject(Kind<F, A> fa) {
        return EitherFKindHelper.EITHERF.widen(EitherF.left(fa));
      }
    };
  }

  /**
   * Creates an Inject instance that embeds effect type G into the right of {@code EitherF<F, G>}.
   *
   * @param <F> The left effect type in the EitherF
   * @param <G> The effect type being injected
   * @return An Inject that wraps instructions in {@link EitherF.Right}
   */
  public static <F extends WitnessArity<?>, G extends WitnessArity<?>>
      Inject<G, EitherFKind.Witness<F, G>> injectRight() {
    return new Inject<>() {
      @Override
      public <A> Kind<EitherFKind.Witness<F, G>, A> inject(Kind<G, A> fa) {
        return EitherFKindHelper.EITHERF.widen(EitherF.right(fa));
      }
    };
  }

  /**
   * Creates a transitive Inject instance that first injects into the right of an EitherF, then
   * delegates to another Inject for deeper nesting.
   *
   * <p>This is used for composing 3 or more effects. For example, to inject {@code H} into {@code
   * EitherF<F, EitherF<G, H>>}, you would use:
   *
   * <pre>{@code
   * Inject<H, EitherFKind.Witness<G, H>> innerInject = InjectInstances.injectRight();
   * Inject<H, EitherFKind.Witness<F, EitherFKind.Witness<G, H>>> composed =
   *     InjectInstances.injectRightThen(innerInject);
   * }</pre>
   *
   * @param inner The Inject instance for the inner (right) EitherF. Must not be null.
   * @param <F> The left effect type of the outer EitherF
   * @param <G> The target witness type of the inner EitherF (right side of outer)
   * @param <H> The effect type being injected
   * @return A composed Inject that navigates through the right side before delegating
   */
  public static <F extends WitnessArity<?>, G extends WitnessArity<?>, H extends WitnessArity<?>>
      Inject<H, EitherFKind.Witness<F, G>> injectRightThen(Inject<H, G> inner) {
    Objects.requireNonNull(inner, "inner Inject must not be null");
    return new Inject<>() {
      @Override
      public <A> Kind<EitherFKind.Witness<F, G>, A> inject(Kind<H, A> fa) {
        Kind<G, A> innerResult = inner.inject(fa);
        return EitherFKindHelper.EITHERF.widen(EitherF.right(innerResult));
      }
    };
  }
}
