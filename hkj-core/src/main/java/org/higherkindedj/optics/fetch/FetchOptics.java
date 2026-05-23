// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.fetch;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.util.FList;
import org.higherkindedj.optics.Optic;

/**
 * Optic builders for heterogeneous fetch.
 *
 * <p>The base {@link Optic Optic&lt;S, T, A, B&gt;} is type-changing ({@code modifyF} maps {@code A
 * → Kind<F, B>}), so heterogeneous fetch is mechanically supported. But the code-generated {@code
 * Lens}/{@code Traversal} types are the type-preserving specialisations ({@code A = B}), so the
 * obvious idiom {@code traversal.modifyF(id -> fetch(id), …)} can only express same-type batching
 * ({@code K → K}). For the marquee multi-source use case — load a list of identifiers into a list
 * of resolved entities of a different type — a small type-changing optic builder closes the
 * ergonomic gap.
 *
 * <p>The optic this builder produces is a generic type-changing list traversal; it is not bound to
 * {@code FetchApplicative}. Threaded through {@code FetchApplicative} via {@link Optic#modifyF},
 * each focus is fetched and the structure rebuilt — in one batched round.
 */
public final class FetchOptics {

  private FetchOptics() {}

  /**
   * Builds a type-changing list traversal as an {@link Optic Optic&lt;S, T, A, B&gt;}.
   *
   * <p>Given:
   *
   * <ul>
   *   <li>{@code source} — how to read the {@code List<A>} field out of {@code S} (e.g. {@code
   *       Team::memberIds});
   *   <li>{@code rebuild} — how to put a {@code List<B>} back into the structure to produce {@code
   *       T} (e.g. {@code (team, members) -> new EnrichedTeam(team.name(), members)}).
   * </ul>
   *
   * The resulting optic, run through {@link FetchApplicative}, fetches each {@code A} into a {@code
   * B} (one batched dispatch over the substrate) and produces the rebuilt {@code T}.
   *
   * @param <S> the source structure type
   * @param <T> the rebuilt structure type
   * @param <A> the focused element type (typically a key/identifier)
   * @param <B> the rebuilt element type (typically a resolved entity)
   */
  public static <S, T, A, B> Optic<S, T, A, B> fetchEach(
      Function<? super S, ? extends List<A>> source,
      BiFunction<? super S, ? super List<B>, ? extends T> rebuild) {
    requireNonNull(source, "source");
    requireNonNull(rebuild, "rebuild");
    return new Optic<>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, T> modifyF(
          Function<A, Kind<F, B>> f, S s, Applicative<F> applicative) {
        List<A> items = source.apply(s);
        // Accumulate into an immutable cons-list so each step is O(1); reverse to ordered List<B>
        // once at the end. Same pattern as ListTraverse.traverse, safe under any well-behaved
        // applicative (including multi-branch ones like List).
        Kind<F, FList<B>> accumulator = applicative.of(new FList.Nil<>());
        for (A item : items) {
          Kind<F, B> next = f.apply(item);
          accumulator = applicative.map2(accumulator, next, FList::cons);
        }
        return applicative.map(flist -> rebuild.apply(s, flist.toList()), accumulator);
      }
    };
  }
}
