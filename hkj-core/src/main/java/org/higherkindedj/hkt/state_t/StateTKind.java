// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state_t;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * A higher-kinded type marker for the {@link StateT} monad transformer.
 *
 * <p>In the higher-kinded types (HKT) emulation provided by this library, Java's lack of native
 * type constructor polymorphism is addressed using "witness" types or marker interfaces like {@code
 * StateTKind}. This interface allows {@link StateT} to be treated abstractly in contexts requiring
 * HKTs.
 *
 * <p>A {@code StateT<S, F, A>} computation encapsulates a function that takes an initial state of
 * type {@code S} and produces a result of type {@code A} along with a new state, all within the
 * context of an underlying monad {@code F}.
 *
 * <p>This interface, {@code StateTKind<S, F, A>}, serves as a "kinded" version of {@code StateT<S,
 * F, A>}. It allows {@code StateT} to be treated as a type constructor {@code StateTKind.Witness<S,
 * F>} (or simply {@code StateT<S,F,?>} in conceptual terms). This constructor takes one type
 * argument {@code A} (the value type of the {@code StateT}'s computation) while keeping {@code S}
 * (the state type) and {@code F} (the underlying monad's witness type) fixed for a particular
 * "kinded" instance.
 *
 * <p>Specifically, when using {@code StateTKind} in generic HKT abstractions:
 *
 * <ul>
 *   <li>The "higher-kinded type witness" (often denoted as {@code Mu} or the type constructor
 *       itself) is {@link Witness Witness&lt;S, F&gt;}. This represents the {@code StateT} type
 *       constructor, partially applied with the state type {@code S} and the underlying monad
 *       witness {@code F}.
 *   <li>The "value type" (often denoted as {@code A}) is {@code A}, representing the primary result
 *       type of the computation encapsulated by the {@code StateT}.
 * </ul>
 *
 * <p>The nested {@code WitnessS} and {@code WitnessF} classes are alternative, less commonly used
 * witness types that fix only one of the {@code S} or {@code F} parameters, respectively. The
 * primary witness for HKT usage is {@link Witness Witness&lt;S, F&gt;}.
 *
 * @param <S> The type of the state threaded through the computations. This parameter is part of the
 *     primary witness type {@link Witness Witness&lt;S, F&gt;}.
 * @param <F> The higher-kinded type witness for the underlying monad (e.g., {@code
 *     OptionalKind.Witness}, {@code ListKind.Witness}). This parameter is also part of the primary
 *     witness type.
 * @param <A> The type of the value produced by the {@link StateT} computation. This is the type
 *     parameter that varies for the higher-kinded type.
 * @see StateT
 * @see StateTMonad
 * @see StateTKindHelper
 * @see org.higherkindedj.hkt.Kind
 */
public interface StateTKind<S, F, A> extends Kind<StateTKind.Witness<S, F>, A> {

  /**
   * The primary witness type for {@code StateT<S, F, A>}, representing the type constructor {@code
   * StateT<S, F, _>} (where {@code _} is the placeholder for the value type {@code A}).
   *
   * <p>This witness is used to parameterize {@link Kind} as {@code Kind<StateTKind.Witness<S, F>,
   * A>}, allowing {@code StateT} to be used as a higher-kinded type in generic abstractions like
   * {@link org.higherkindedj.hkt.Monad}, {@link org.higherkindedj.hkt.Applicative}, etc. It "fixes"
   * the state type {@code S} and the underlying monad witness {@code F}.
   *
   * @param <S> The state type.
   * @param <F> The higher-kinded type witness for the underlying monad.
   */
  final class Witness<S, F> implements WitnessArity<TypeArity.Unary> {
    // Private constructor to prevent instantiation of the witness type itself.
    // Its purpose is purely for type-level representation.
    private Witness() {}
  }

  /**
   * An alternative witness type for {@code StateT}, capturing only the State type {@code S}. This
   * could be useful in very specific generic contexts where {@code F} and {@code A} might be fixed
   * or inferred differently. However, {@link Witness Witness&lt;S, F&gt;} is the standard witness
   * for HKT operations.
   *
   * @param <S> The state type.
   */
  final class WitnessS<S> implements WitnessArity<TypeArity.Unary> {
    private WitnessS() {}
  }

  /**
   * An alternative witness type for {@code StateT}, capturing only the underlying monad witness
   * {@code F}. This could be useful in very specific generic contexts where {@code S} and {@code A}
   * might be fixed or inferred differently. However, {@link Witness Witness&lt;S, F&gt;} is the
   * standard witness for HKT operations.
   *
   * @param <F> The higher-kinded type witness for the underlying monad.
   */
  final class WitnessF<F> implements WitnessArity<TypeArity.Unary> {
    private WitnessF() {}
  }

  /**
   * Safely converts (narrows) a {@link Kind} representation of a {@code StateT} back to its
   * concrete {@link StateT} type using standardised error handling.
   *
   * <p>This method provides a safe, validated approach to narrowing Kind representations to
   * concrete StateT instances. It uses the standardised error handling utilities to ensure
   * consistent error messages and behaviour across the library.
   *
   * @param kind The higher-kinded {@code StateT} representation. Must not be null.
   * @param <S> The state type of the target {@code StateT}.
   * @param <F> The higher-kinded type witness for the underlying monad of the target {@code
   *     StateT}.
   * @param <A> The value type of the target {@code StateT}.
   * @return The concrete {@link StateT} instance, guaranteed to be non-null if the input `kind` was
   *     a valid, non-null {@code StateT} representation.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code kind} is {@code null} or
   *     not actually an instance of {@link StateT} (or a compatible subtype).
   */
  static <S, F extends WitnessArity<TypeArity.Unary>, A> StateT<S, F, A> narrow(
      @Nullable Kind<StateTKind.Witness<S, F>, A> kind) {
    return Validation.kind().narrowWithTypeCheck(kind, StateT.class);
  }

  /**
   * Unwraps (narrows) a {@link Kind} to a concrete {@link StateT} instance with less type safety
   * regarding the witness type.
   *
   * <p><b>Use with extreme caution.</b> This method performs an unchecked cast and relies on the
   * caller to ensure that the provided {@code Kind<?, A>} is indeed a {@code StateT} that can be
   * meaningfully cast to {@code StateT<S, F, A>}. The witness part of the {@code Kind} (the first
   * type parameter, {@code ?}) is effectively ignored by the cast, and the {@code S} and {@code F}
   * types are determined by the call site's context.
   *
   * <p>This might be used in highly generic HKT code where the exact witness structure is
   * abstracted away, but it sacrifices compile-time safety for flexibility. Prefer {@link
   * #narrow(Kind)} whenever possible.
   *
   * @param kind The higher-kinded representation, typed broadly as {@code Kind<?, A>}. It is
   *     assumed to be a {@code StateT} instance. Must not be null.
   * @param <S> The target state type for the resulting {@code StateT}.
   * @param <F> The target higher-kinded type witness for the underlying monad of the resulting
   *     {@code StateT}.
   * @param <A> The target value type for the resulting {@code StateT}.
   * @return The concrete {@link StateT} instance, guaranteed to be non-null if the input `kind` was
   *     a valid, non-null {@code StateT} representation.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code kind} is {@code null} or
   *     not actually an instance of {@link StateT}.
   */
  @SuppressWarnings("unchecked")
  static <S, F extends WitnessArity<TypeArity.Unary>, A> StateT<S, F, A> narrowK(
      @Nullable Kind<?, A> kind) {
    return Validation.kind().narrowWithTypeCheck(kind, StateT.class);
  }
}
