// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.fetch;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Kind marker for the partially-applied {@code Fetch<K, V, ?>} type constructor.
 *
 * <p>{@code K} and {@code V} (the request domain) are fixed; the free parameter is the result type
 * {@code A}, giving a unary witness usable with {@link org.higherkindedj.hkt.Applicative}.
 *
 * @param <K> request key type (fixed)
 * @param <V> resolved value type (fixed)
 * @param <A> the free result type
 */
public interface FetchKind<K, V, A> extends Kind<FetchKind.Witness<K, V>, A> {

  /** Phantom witness for {@code Fetch<K, V, ?>}. */
  final class Witness<K, V> implements WitnessArity<TypeArity.Unary> {
    private Witness() {}
  }
}
