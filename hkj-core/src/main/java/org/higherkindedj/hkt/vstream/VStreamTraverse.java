// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import static org.higherkindedj.hkt.util.validation.Operation.*;
import static org.higherkindedj.hkt.vstream.VStreamKindHelper.VSTREAM;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.NullMarked;

/**
 * Implements the {@link Traverse} and {@link org.higherkindedj.hkt.Foldable} type classes for
 * {@link VStream}, using {@link VStreamKind.Witness} as the higher-kinded type witness.
 *
 * <p><b>Important: Stream Materialisation</b>
 *
 * <p>Unlike the lazy operations in {@link VStreamFunctor} and {@link VStreamMonad}, the operations
 * provided by {@code Traverse} and {@code Foldable} necessarily force evaluation of the stream:
 *
 * <ul>
 *   <li>{@link #traverse} - Must consume the entire stream to collect results and sequence effects
 *   <li>{@link #foldMap} - Must consume the stream to compute the folded result
 * </ul>
 *
 * <p><b>Warning:</b> These operations are only safe for finite streams. Using them on infinite
 * streams will not terminate.
 *
 * <p>The {@link #traverse} operation materialises the stream to a list first, then uses an
 * iterative loop with {@code applicative.map2()} for stack-safe processing.
 *
 * @see Traverse
 * @see VStream
 * @see VStreamKind
 * @see VStreamMonad
 * @see VStreamFunctor
 */
@NullMarked
public enum VStreamTraverse implements Traverse<VStreamKind.Witness> {
  /** Singleton instance of {@code VStreamTraverse}. */
  INSTANCE;

  private static final Class<VStreamTraverse> VSTREAM_TRAVERSE_CLASS = VStreamTraverse.class;

  /**
   * Maps a function over a VStream in a higher-kinded context.
   *
   * <p>This operation delegates to {@link VStreamFunctor} and maintains lazy evaluation. It does
   * NOT force evaluation of the stream.
   *
   * @param <A> The type of elements in the input stream.
   * @param <B> The type of elements in the output stream.
   * @param f The non-null function to apply to each element.
   * @param fa The non-null {@code Kind<VStreamKind.Witness, A>} representing the input stream.
   * @return A new non-null {@code Kind<VStreamKind.Witness, B>} with the function applied lazily.
   * @throws NullPointerException if {@code f} or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fa} cannot be unwrapped.
   */
  @Override
  public <A, B> Kind<VStreamKind.Witness, B> map(
      Function<? super A, ? extends B> f, Kind<VStreamKind.Witness, A> fa) {

    Validation.function().requireMapper(f, "f", VSTREAM_TRAVERSE_CLASS, MAP);
    Validation.kind().requireNonNull(fa, VSTREAM_TRAVERSE_CLASS, MAP);

    return VStreamFunctor.INSTANCE.map(f, fa);
  }

  /**
   * Traverses a VStream from left to right, applying an effectful function {@code f} to each
   * element and collecting the results within the context of the {@link Applicative} {@code G}.
   *
   * <p><b>Stream Materialisation Warning:</b> This operation forces evaluation of the entire
   * stream. The stream is materialised to a list during traversal. This operation is only safe for
   * finite streams.
   *
   * <p>The traversal proceeds as follows:
   *
   * <ol>
   *   <li>Materialise the VStream to a list by running all VTasks
   *   <li>Start with an empty list wrapped in the applicative context
   *   <li>For each element, apply the effectful function and combine with the accumulator
   *   <li>Convert the result list back to a VStream
   * </ol>
   *
   * @param <G> The higher-kinded type witness for the {@link Applicative} context.
   * @param <A> The type of elements in the input stream.
   * @param <B> The type of elements in the resulting stream.
   * @param applicative The non-null {@link Applicative} instance for the context {@code G}.
   * @param f A non-null function from {@code A} to {@code Kind<G, ? extends B>}.
   * @param ta The non-null {@code Kind<VStreamKind.Witness, A>} to traverse. This stream will be
   *     materialised.
   * @return A {@code Kind<G, Kind<VStreamKind.Witness, B>>} representing the traversed result.
   *     Never null.
   * @throws NullPointerException if {@code applicative}, {@code f}, or {@code ta} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ta} cannot be unwrapped.
   */
  @Override
  public <G extends WitnessArity<TypeArity.Unary>, A, B>
      Kind<G, Kind<VStreamKind.Witness, B>> traverse(
          Applicative<G> applicative,
          Function<? super A, ? extends Kind<G, ? extends B>> f,
          Kind<VStreamKind.Witness, A> ta) {

    Validation.function().validateTraverse(applicative, f, ta, VSTREAM_TRAVERSE_CLASS);

    VStream<A> stream = VSTREAM.narrow(ta);

    // Materialise the stream to a list
    List<A> elements = stream.toList().run();

    // Start with empty list in applicative context
    Kind<G, List<B>> result = applicative.of(new ArrayList<>());

    // Process each element, accumulating in applicative context
    for (A a : elements) {
      Kind<G, ? extends B> effectOfB = f.apply(a);
      result =
          applicative.map2(
              result,
              effectOfB,
              (list, b) -> {
                @SuppressWarnings("unchecked")
                B bValue = (B) b;
                list.add(bValue);
                return list;
              });
    }

    // Convert list to VStream and wrap
    return applicative.map(list -> VSTREAM.widen(VStream.fromList(list)), result);
  }

  /**
   * Maps each element of the VStream to a {@link Monoid} and combines the results.
   *
   * <p><b>Stream Materialisation Warning:</b> This operation forces evaluation of the entire
   * stream. It is a terminal operation that runs all VTasks. This operation is only safe for finite
   * streams.
   *
   * @param <A> The type of elements in the stream.
   * @param <M> The Monoidal type to which elements are mapped and combined.
   * @param monoid The {@code Monoid} used to combine the results. Must not be null.
   * @param f A function to map each element to the Monoidal type. Must not be null.
   * @param fa The {@code Kind<VStreamKind.Witness, A>} representing the stream to fold. Must not be
   *     null.
   * @return The aggregated result of type {@code M}. Never null.
   * @throws NullPointerException if {@code monoid}, {@code f}, or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fa} cannot be unwrapped.
   */
  @Override
  public <A, M> M foldMap(
      Monoid<M> monoid, Function<? super A, ? extends M> f, Kind<VStreamKind.Witness, A> fa) {

    Validation.function().validateFoldMap(monoid, f, fa, VSTREAM_TRAVERSE_CLASS);

    VStream<A> stream = VSTREAM.narrow(fa);

    // Materialise and fold using iterative consumption for stack safety
    return stream
        .foldLeft(
            monoid.empty(),
            (acc, a) -> {
              M mapped = f.apply(a);
              return monoid.combine(acc, mapped);
            })
        .run();
  }
}
