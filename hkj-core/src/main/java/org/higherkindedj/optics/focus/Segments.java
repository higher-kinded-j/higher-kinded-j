// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.focus;

import java.util.ArrayList;
import java.util.List;

/** Package-private helper: concatenation of optic-path segment lists. */
final class Segments {

  private Segments() {}

  static List<String> concat(List<String> left, List<String> right) {
    if (left.isEmpty()) {
      return right;
    }
    if (right.isEmpty()) {
      return left;
    }
    List<String> combined = new ArrayList<>(left);
    combined.addAll(right);
    return List.copyOf(combined);
  }
}
