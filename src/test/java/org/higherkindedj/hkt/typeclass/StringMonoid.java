// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.typeclass;

import org.higherkindedj.hkt.Monoid;
import org.jspecify.annotations.NonNull;

/** A concrete Monoid implementation for String concatenation. */
public class StringMonoid implements Monoid<String> {

  @Override
  public @NonNull String empty() {
    return "";
  }

  @Override
  public @NonNull String combine(@NonNull String x, @NonNull String y) {
    return x + y;
  }
}
