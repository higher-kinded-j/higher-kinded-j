package org.higherkindedj.hkt.trans.state_t;

import org.higherkindedj.hkt.Kind;

/**
 * Higher-kinded type marker for the StateT monad transformer.
 *
 * <p>This interface uses nested marker classes (`Witness`, `μS`, `μF`) to represent the type
 * parameters involved in StateT:
 *
 * <ul>
 *   <li>`S`: The state type.
 *   <li>`F`: The higher-kinded type witness for the underlying monad.
 *   <li>`A`: The value type.
 * </ul>
 *
 * <p>A concrete `StateT<S, F, A>` implements `Kind<StateTKind.Witness<S, F>, A>`.
 *
 * @param <S> The state type.
 * @param <F> The higher-kinded type witness for the underlying monad.
 * @param <A> The value type.
 */
public interface StateTKind<S, F, A> extends Kind<StateTKind.Witness<S, F>, A> {

  /**
   * The witness type for StateT, capturing the State type S and the underlying monad F. This allows
   * StateT<S, F, ?> to be treated as a higher-kinded type `G<_>` where `G` represents `StateT<S, F,
   * ?>`.
   *
   * <p>This is the primary witness type used for the Monad instance.
   *
   * @param <S> The state type.
   * @param <F> The higher-kinded type witness for the underlying monad.
   */
  final class Witness<S, F> {
    private Witness() {}
  }

  /**
   * The witness type for StateT, capturing only the State type S. Useful if F and A are fixed or
   * inferred.
   *
   * @param <S> The state type.
   */
  final class WitnessS<S> {
    private WitnessS() {}
  }

  /**
   * The witness type for StateT, capturing only the underlying monad F. Useful if S and A are fixed
   * or inferred.
   *
   * @param <F> The higher-kinded type witness for the underlying monad.
   */
  final class WitnessF<F> {
    private WitnessF() {}
  }

  /**
   * Unwraps the Kind into a concrete StateT instance.
   *
   * @param kind The higher-kinded StateT representation.
   * @param <S> The state type.
   * @param <F> The higher-kinded type witness for F.
   * @param <A> The value type.
   * @return The concrete StateT instance.
   * @throws ClassCastException if the kind is not actually a StateT.
   */
  static <S, F, A> StateT<S, F, A> narrow(Kind<StateTKind.Witness<S, F>, A> kind) {
    if (kind == null) {
      // It's better to throw ClassCastException here for consistency
      // as casting null isn't valid for StateT.
      throw new ClassCastException("Cannot cast null to StateT");
    }
    try {
      return (StateT<S, F, A>) kind;
    } catch (ClassCastException cce) {
      throw cce;
    }
  }

  /**
   * Unwraps the Kind into a concrete StateT instance (unchecked version). Use with caution when
   * type parameters might not align.
   *
   * @param kind The higher-kinded representation (potentially of a different StateT).
   * @param <S> The target state type.
   * @param <F> The target higher-kinded type witness for F.
   * @param <A> The target value type.
   * @return The concrete StateT instance.
   * @throws ClassCastException if the kind is not actually a StateT.
   */
  @SuppressWarnings("unchecked")
  static <S, F, A> StateT<S, F, A> narrowK(Kind<?, A> kind) {
    // Unsafe cast, assumes the kind is a StateT with the correct structure.
    // Note: The first type parameter of Kind is effectively ignored here.
    // This relies on the caller ensuring 'kind' is indeed a StateT<SomeS, SomeF, A>.
    // The target S and F types are determined by the context where narrowK is called.
    return (StateT<S, F, A>) kind;
  }
}
