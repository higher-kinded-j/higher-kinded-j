// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.instances;

import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.context.ContextKind;
import org.higherkindedj.hkt.context.ContextMonad;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either.EitherMonad;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.future.CompletableFutureMonad;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOMonad;
import org.higherkindedj.hkt.lazy.LazyKind;
import org.higherkindedj.hkt.lazy.LazyMonad;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListMonad;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.hkt.nonemptylist.NonEmptyListKind;
import org.higherkindedj.hkt.nonemptylist.NonEmptyListMonad;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.reader.ReaderKind;
import org.higherkindedj.hkt.reader.ReaderMonad;
import org.higherkindedj.hkt.state.StateKind;
import org.higherkindedj.hkt.state.StateMonad;
import org.higherkindedj.hkt.stream.StreamKind;
import org.higherkindedj.hkt.stream.StreamMonad;
import org.higherkindedj.hkt.trampoline.TrampolineKind;
import org.higherkindedj.hkt.trampoline.TrampolineMonad;
import org.higherkindedj.hkt.trymonad.TryKind;
import org.higherkindedj.hkt.trymonad.TryMonad;
import org.higherkindedj.hkt.vstream.VStreamKind;
import org.higherkindedj.hkt.vstream.VStreamMonad;
import org.higherkindedj.hkt.vtask.VTaskKind;
import org.higherkindedj.hkt.vtask.VTaskMonad;

/**
 * Typed witness tokens used as keys for the zero-argument lookups on {@link Instances}.
 *
 * <p>Each factory method returns an {@link Of} token that carries the canonical {@link Monad} for
 * the corresponding data type. Because the token is generic (rather than {@code Class}-keyed),
 * phantom-typed witnesses such as {@code Either}, {@code Reader}, {@code Context} and {@code State}
 * still infer their type parameter from the assignment target:
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.instances.Witnesses.*;
 *
 * Monad<MaybeKind.Witness>               m = Instances.monad(maybe());
 * Monad<EitherKind.Witness<DomainError>> e = Instances.monad(either());   // <DomainError> inferred
 * Functor<ListKind.Witness>              f = Instances.functor(list());
 * }</pre>
 *
 * <p>These tokens cover the stateless-singleton and phantom-typed-nullary families. The
 * argument-carrying instances ({@code validated}, {@code writer}, the monad transformers) are not
 * tokenised because they have a required dependency; they are exposed directly on {@link Instances}
 * with that dependency in the method signature.
 *
 * @see Instances
 */
public final class Witnesses {

  private Witnesses() {}

  /**
   * A typed witness token carrying the canonical {@link Monad} for a unary type constructor.
   *
   * <p>Obtain instances via the factory methods on {@link Witnesses} (for example {@link
   * Witnesses#maybe()}); this type is not meant to be constructed directly.
   *
   * @param <F> the witness type of the unary type constructor
   */
  public static final class Of<F extends WitnessArity<TypeArity.Unary>> {

    private final Monad<F> monad;

    private Of(Monad<F> monad) {
      this.monad = monad;
    }

    /**
     * Returns the canonical monad this token carries. Package-private; used by {@link Instances}.
     */
    Monad<F> monad() {
      return monad;
    }
  }

  // --- Stateless singletons -------------------------------------------------

  /** Token for {@code Maybe} ({@link MaybeMonad#INSTANCE}). */
  public static Of<MaybeKind.Witness> maybe() {
    return new Of<>(MaybeMonad.INSTANCE);
  }

  /** Token for {@code IO} ({@link IOMonad#INSTANCE}). */
  public static Of<IOKind.Witness> io() {
    return new Of<>(IOMonad.INSTANCE);
  }

  /** Token for {@code List} ({@link ListMonad#INSTANCE}). */
  public static Of<ListKind.Witness> list() {
    return new Of<>(ListMonad.INSTANCE);
  }

  /** Token for {@code NonEmptyList} ({@link NonEmptyListMonad#INSTANCE}). */
  public static Of<NonEmptyListKind.Witness> nonEmptyList() {
    return new Of<>(NonEmptyListMonad.INSTANCE);
  }

  /** Token for {@code Optional} ({@link OptionalMonad#INSTANCE}). */
  public static Of<OptionalKind.Witness> optional() {
    return new Of<>(OptionalMonad.INSTANCE);
  }

  /**
   * Token for {@code Try} ({@link TryMonad#INSTANCE}).
   *
   * <p>Named {@code try_} because {@code try} is a Java reserved word.
   */
  public static Of<TryKind.Witness> try_() {
    return new Of<>(TryMonad.INSTANCE);
  }

  /** Token for {@code VTask} ({@link VTaskMonad#INSTANCE}). */
  public static Of<VTaskKind.Witness> vtask() {
    return new Of<>(VTaskMonad.INSTANCE);
  }

  /** Token for {@code VStream} ({@link VStreamMonad#INSTANCE}). */
  public static Of<VStreamKind.Witness> vstream() {
    return new Of<>(VStreamMonad.INSTANCE);
  }

  /** Token for {@code Lazy} ({@link LazyMonad#INSTANCE}). */
  public static Of<LazyKind.Witness> lazy() {
    return new Of<>(LazyMonad.INSTANCE);
  }

  /** Token for {@code Stream} ({@link StreamMonad#INSTANCE}). */
  public static Of<StreamKind.Witness> stream() {
    return new Of<>(StreamMonad.INSTANCE);
  }

  /** Token for {@code CompletableFuture} ({@link CompletableFutureMonad#INSTANCE}). */
  public static Of<CompletableFutureKind.Witness> completableFuture() {
    return new Of<>(CompletableFutureMonad.INSTANCE);
  }

  /** Token for {@code Trampoline} ({@link TrampolineMonad#INSTANCE}). */
  public static Of<TrampolineKind.Witness> trampoline() {
    return new Of<>(TrampolineMonad.INSTANCE);
  }

  // --- Phantom-typed nullary ------------------------------------------------

  /** Token for {@code Id} ({@link IdMonad#instance()}). */
  public static Of<IdKind.Witness> id() {
    return new Of<>(IdMonad.instance());
  }

  /**
   * Token for {@code Either} ({@link EitherMonad#instance()}). The left type {@code L} is inferred
   * from the assignment target.
   *
   * @param <L> the fixed "left" type of the {@code Either}
   */
  public static <L> Of<EitherKind.Witness<L>> either() {
    return new Of<>(EitherMonad.<L>instance());
  }

  /**
   * Token for {@code Reader} ({@link ReaderMonad#instance()}). The environment type {@code R} is
   * inferred from the assignment target.
   *
   * @param <R> the environment type of the {@code Reader}
   */
  public static <R> Of<ReaderKind.Witness<R>> reader() {
    return new Of<>(ReaderMonad.<R>instance());
  }

  /**
   * Token for {@code Context} ({@link ContextMonad#instance()}). The environment type {@code R} is
   * inferred from the assignment target.
   *
   * @param <R> the environment type of the {@code Context}
   */
  public static <R> Of<ContextKind.Witness<R>> context() {
    return new Of<>(ContextMonad.<R>instance());
  }

  /**
   * Token for {@code State} ({@link StateMonad#instance()}). The state type {@code S} is inferred
   * from the assignment target.
   *
   * @param <S> the state type of the {@code State} computation
   */
  public static <S> Of<StateKind.Witness<S>> state() {
    return new Of<>(StateMonad.<S>instance());
  }
}
