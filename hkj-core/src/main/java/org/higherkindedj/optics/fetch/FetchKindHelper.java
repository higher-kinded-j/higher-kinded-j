// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.fetch;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/** {@code widen}/{@code narrow} between {@link Fetch} and its {@link FetchKind} representation. */
public enum FetchKindHelper {
  FETCH;

  /**
   * Widens a concrete {@link Fetch} into its {@link Kind} representation. Since {@code Fetch}
   * extends {@code FetchKind}, this is a cast-free upcast with no wrapper object.
   */
  public <K, V, A> Kind<FetchKind.Witness<K, V>, A> widen(Fetch<K, V, A> fetch) {
    Validation.kind().requireForWiden(fetch, Fetch.class);
    return fetch;
  }

  /** Narrows a {@link Kind} back to its concrete {@link Fetch}. */
  @SuppressWarnings("unchecked") // raw Class token; runtime-checked via Class.isInstance
  public <K, V, A> Fetch<K, V, A> narrow(@Nullable Kind<FetchKind.Witness<K, V>, A> kind) {
    return Validation.kind().narrowWithTypeCheck(kind, Fetch.class);
  }
}
