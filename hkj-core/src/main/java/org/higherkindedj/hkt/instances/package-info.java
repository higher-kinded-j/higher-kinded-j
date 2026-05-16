// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * A uniform facade for obtaining type-class instances.
 *
 * <p>Today a type-class instance is reached through one of three inconsistent idioms depending on
 * the data type: a static {@code INSTANCE} field ({@code MaybeMonad.INSTANCE}), a generic static
 * method ({@code EitherMonad.instance()}), or a constructor/factory that requires an argument
 * ({@code ValidatedMonad.instance(semigroup)}, {@code new EitherTMonad<>(outer)}). This package
 * collapses all of them into a single, predictable entry point.
 *
 * <h2>{@link org.higherkindedj.hkt.instances.Instances}</h2>
 *
 * <p>A static facade with one shape, {@code Instances.x(...)}, regardless of how the underlying
 * accessor is spelled:
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.instances.Witnesses.*;
 *
 * Monad<MaybeKind.Witness>              m1 = Instances.monad(maybe());
 * Monad<EitherKind.Witness<DomainError>> m2 = Instances.monad(either());
 * MonadError<ValidatedKind.Witness<E>, E> v  = Instances.validated(Semigroups.list());
 * MonadError<EitherTKind.Witness<F, L>, L> et = Instances.eitherT(Instances.monad(optional()));
 * }</pre>
 *
 * <p>The facade is a thin static re-export of the existing accessors. It is not Spring-wired and is
 * not backed by {@code PathRegistry}/{@code ServiceLoader}, so compile-time safety is preserved: a
 * built-in instance cannot be missing at runtime.
 *
 * <h2>{@link org.higherkindedj.hkt.instances.Witnesses}</h2>
 *
 * <p>Typed witness tokens ({@code maybe()}, {@code either()}, ...) used as keys for the
 * zero-argument lookups. They are generic rather than {@code Class}-keyed so that phantom-typed
 * witnesses (such as {@code Either}, {@code Reader}, {@code State}) still infer their type
 * parameter from the assignment target, matching the behaviour of {@code EitherMonad.<L>instance()}
 * today.
 *
 * <h2>Scope</h2>
 *
 * <p>Covers the {@code Functor} &rarr; {@code Applicative} &rarr; {@code Monad} &rarr; {@code
 * MonadError} stack (free via subtyping) plus the argument-carrying re-exports ({@code validated},
 * {@code writer}, {@code eitherT}, {@code maybeT}, {@code optionalT}, {@code readerT}, {@code
 * stateT}, {@code writerT}). {@code Traverse}, {@code Selective} and {@code Foldable} are
 * intentionally out of scope for this facade and tracked separately, as are the special
 * constructions {@code Free} and {@code FreeApplicative} (built and interpreted, not retrieved as a
 * ready instance) and the {@code Applicative}-only {@code Const} (a token here always carries a
 * {@code Monad}). The {@code MonadReader}/{@code MonadState}/{@code MonadWriter} MTL capability
 * classes are likewise a separate surface.
 */
@NullMarked
package org.higherkindedj.hkt.instances;

import org.jspecify.annotations.NullMarked;
