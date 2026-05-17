// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.fetch;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * A free-applicative-style reification of deferred, batchable data requests.
 *
 * <p>A {@code Fetch<K, V, A>} describes a computation producing an {@code A} that may require
 * resolving one or more requests of key type {@code K} into values of type {@code V}. Crucially, it
 * is <em>data, not execution</em>: building a {@code Fetch} performs no I/O. Only {@link
 * #run(Fetch, Function)} executes, and it does so in <em>rounds</em>, handing every key discovered
 * in a round to a single batch resolver call.
 *
 * <p>The applicative combinator {@link #ap(Fetch, Fetch)} unions the pending request sets of its
 * two independent arguments. This is the batch point: when an optic {@code Traversal} threads its
 * per-element effects through {@link FetchApplicative}, every element's request lands in the same
 * round, so a traversal over N elements collapses to one backend call instead of N.
 *
 * <p>{@link #flatMap(Function)} is provided to make the Applicative/Monad batching boundary
 * concrete: a monadic bind cannot see the second stage's requests until the first stage's values
 * are resolved, so it necessarily costs an extra round. Applicative composition does not.
 *
 * @param <K> request key type
 * @param <V> resolved value type
 * @param <A> result type
 */
public sealed interface Fetch<K, V, A> permits Fetch.Done, Fetch.Blocked {

  /** A completed computation with no outstanding requests. */
  record Done<K, V, A>(A value) implements Fetch<K, V, A> {}

  /**
   * A computation blocked on a set of pending requests, with a continuation that resumes once those
   * requests have been resolved.
   */
  record Blocked<K, V, A>(Set<K> pending, Function<Map<K, V>, Fetch<K, V, A>> resume)
      implements Fetch<K, V, A> {}

  /** Lifts a pure value (no requests). */
  static <K, V, A> Fetch<K, V, A> done(A value) {
    return new Done<>(value);
  }

  /** A single request for {@code key}; resolves to the value the resolver returns for it. */
  static <K, V> Fetch<K, V, V> fetch(K key) {
    Set<K> one = new LinkedHashSet<>();
    one.add(key);
    return new Blocked<>(one, resolved -> done(resolved.get(key)));
  }

  /** Functorial map: structure (and pending requests) preserved, result transformed. */
  default <B> Fetch<K, V, B> map(Function<? super A, ? extends B> f) {
    return switch (this) {
      case Done<K, V, A> d -> done(f.apply(d.value()));
      case Blocked<K, V, A> b -> new Blocked<>(b.pending(), m -> b.resume().apply(m).map(f));
    };
  }

  /**
   * Applicative apply. The two arguments are independent, so their pending request sets are
   * <em>merged</em> into one round — this is what turns N per-element fetches into one batch.
   */
  static <K, V, A, B> Fetch<K, V, B> ap(
      Fetch<K, V, ? extends Function<A, B>> ff, Fetch<K, V, A> fa) {
    if (ff instanceof Done<K, V, ? extends Function<A, B>> df
        && fa instanceof Done<K, V, A> da) {
      return done(df.value().apply(da.value()));
    }
    if (ff instanceof Done<K, V, ? extends Function<A, B>> df) {
      Blocked<K, V, A> ba = (Blocked<K, V, A>) fa;
      return new Blocked<>(ba.pending(), m -> ap(df, ba.resume().apply(m)));
    }
    if (fa instanceof Done<K, V, A> da) {
      Blocked<K, V, ? extends Function<A, B>> bf =
          (Blocked<K, V, ? extends Function<A, B>>) ff;
      return new Blocked<>(bf.pending(), m -> ap(bf.resume().apply(m), da));
    }
    Blocked<K, V, ? extends Function<A, B>> bf =
        (Blocked<K, V, ? extends Function<A, B>>) ff;
    Blocked<K, V, A> ba = (Blocked<K, V, A>) fa;
    Set<K> merged = new LinkedHashSet<>(bf.pending());
    merged.addAll(ba.pending());
    return new Blocked<>(merged, m -> ap(bf.resume().apply(m), ba.resume().apply(m)));
  }

  /**
   * Monadic bind. Included to demonstrate the batching boundary: the continuation's requests are
   * unknowable until {@code this} is resolved, so a dependency chain of N binds costs N rounds.
   */
  default <B> Fetch<K, V, B> flatMap(Function<? super A, ? extends Fetch<K, V, B>> f) {
    return switch (this) {
      case Done<K, V, A> d -> f.apply(d.value());
      case Blocked<K, V, A> b -> new Blocked<>(b.pending(), m -> b.resume().apply(m).flatMap(f));
    };
  }

  /** The outcome of running a {@code Fetch}, including observability for the batching behaviour. */
  record RunResult<K, A>(A value, int rounds, List<Set<K>> batches) {}

  /**
   * Runs the computation. Each round collects the current pending key set, invokes {@code
   * batchResolver} exactly once with it, and resumes. Pure applicative programs terminate in a
   * single round; monadic dependency chains take one round per dependency.
   *
   * @param batchResolver resolves a whole set of keys in one call (e.g. {@code findAllById}, a
   *     single {@code $in} query, or a GraphQL DataLoader dispatch)
   */
  static <K, V, A> RunResult<K, A> run(
      Fetch<K, V, A> fetch, Function<Set<K>, Map<K, V>> batchResolver) {
    int rounds = 0;
    java.util.ArrayList<Set<K>> batches = new java.util.ArrayList<>();
    Fetch<K, V, A> current = fetch;
    while (current instanceof Blocked<K, V, A> blocked) {
      Set<K> keys = blocked.pending();
      batches.add(new LinkedHashSet<>(keys));
      rounds++;
      Map<K, V> resolved = batchResolver.apply(keys);
      current = blocked.resume().apply(resolved);
    }
    return new RunResult<>(((Done<K, V, A>) current).value(), rounds, List.copyOf(batches));
  }
}
