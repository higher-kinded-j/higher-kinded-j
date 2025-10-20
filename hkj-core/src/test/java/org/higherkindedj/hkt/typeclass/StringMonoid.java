// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.typeclass;

import org.higherkindedj.hkt.Monoid;

/** A concrete Monoid implementation for String concatenation. */
public class StringMonoid implements Monoid<String> {

  @Override
  public String empty() {
    return "";
  }

  @Override
  public String combine(String x, String y) {
    return x + y;
  }
}
