// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.fetch;

import static org.higherkindedj.optics.fetch.FetchKindHelper.FETCH;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.Nullable;

/**
 * The batching {@link Applicative} for {@link Fetch}.
 *
 * <p>Hand this to {@code Optic.modifyF(f, source, applicative)} where {@code f} maps each focused
 * element to a {@link Fetch} request. Because {@link #ap} merges the pending request sets of
 * independent arguments, an optic {@code Traversal} over N elements produces a single {@link
 * Fetch.Blocked} node carrying all N (deduplicated) keys: one batched backend call instead of N.
 * The optic core is untouched; only the supplied applicative changes.
 *
 * @param <K> request key type
 * @param <V> resolved value type
 */
public final class FetchApplicative<K, V> implements Applicative<FetchKind.Witness<K, V>> {

  private static final FetchApplicative<?, ?> INSTANCE = new FetchApplicative<>();

  private FetchApplicative() {}

  @SuppressWarnings("unchecked")
  public static <K, V> FetchApplicative<K, V> instance() {
    return (FetchApplicative<K, V>) INSTANCE;
  }

  @Override
  public <A> Kind<FetchKind.Witness<K, V>, A> of(@Nullable A value) {
    return FETCH.widen(Fetch.done(value));
  }

  @Override
  public <A, B> Kind<FetchKind.Witness<K, V>, B> map(
      Function<? super A, ? extends B> f, Kind<FetchKind.Witness<K, V>, A> fa) {
    Objects.requireNonNull(f, "f");
    Objects.requireNonNull(fa, "fa");
    return FETCH.widen(FETCH.narrow(fa).map(f));
  }

  @Override
  public <A, B> Kind<FetchKind.Witness<K, V>, B> ap(
      Kind<FetchKind.Witness<K, V>, ? extends Function<A, B>> ff,
      Kind<FetchKind.Witness<K, V>, A> fa) {
    Objects.requireNonNull(ff, "ff");
    Objects.requireNonNull(fa, "fa");
    Fetch<K, V, ? extends Function<A, B>> fnf = FETCH.narrow(ff);
    Fetch<K, V, A> fva = FETCH.narrow(fa);
    return FETCH.widen(Fetch.ap(fnf, fva));
  }
}
