// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A utility interface providing static factory methods for common {@link Monoid} instances.
 *
 * <p>A Monoid extends {@link Semigroup} with an identity element (`empty`), making it useful for
 * fold operations on potentially empty collections.
 */
public interface Monoids {

  /**
   * Returns a {@code Monoid} for {@link List}, where the combination is list concatenation and the
   * identity element is an empty list.
   */
  static <A> Monoid<List<A>> list() {
    return new Monoid<List<A>>() {
      @Override
      public List<A> empty() {
        return Collections.emptyList();
      }

      @Override
      public List<A> combine(List<A> list1, List<A> list2) {
        List<A> combined = new ArrayList<>(list1);
        combined.addAll(list2);
        return combined;
      }
    };
  }

  /**
   * Returns a {@code Monoid} for {@link Set}, where the combination is set union and the identity
   * element is an empty set.
   */
  static <A> Monoid<Set<A>> set() {
    return new Monoid<Set<A>>() {
      @Override
      public Set<A> empty() {
        return Collections.emptySet();
      }

      @Override
      public Set<A> combine(Set<A> set1, Set<A> set2) {
        Set<A> combined = new HashSet<>(set1);
        combined.addAll(set2);
        return combined;
      }
    };
  }

  /**
   * Returns a {@code Monoid} for {@link String}, where the combination is concatenation and the
   * identity element is an empty string.
   */
  static Monoid<String> string() {
    return new Monoid<String>() {
      @Override
      public String empty() {
        return "";
      }

      @Override
      public String combine(String s1, String s2) {
        return s1 + s2;
      }
    };
  }

  /** Returns a {@code Monoid} for integer addition. Combination is `+`, identity is `0`. */
  static Monoid<Integer> integerAddition() {
    return new Monoid<Integer>() {
      @Override
      public Integer empty() {
        return 0;
      }

      @Override
      public Integer combine(Integer i1, Integer i2) {
        return i1 + i2;
      }
    };
  }

  /** Returns a {@code Monoid} for integer multiplication. Combination is `*`, identity is `1`. */
  static Monoid<Integer> integerMultiplication() {
    return new Monoid<Integer>() {
      @Override
      public Integer empty() {
        return 1;
      }

      @Override
      public Integer combine(Integer i1, Integer i2) {
        return i1 * i2;
      }
    };
  }

  /**
   * Returns a {@code Monoid} for boolean conjunction (`&&`). Combination is `&&`, identity is
   * `true`.
   */
  static Monoid<Boolean> booleanAnd() {
    return new Monoid<Boolean>() {
      @Override
      public Boolean empty() {
        return true;
      }

      @Override
      public Boolean combine(Boolean b1, Boolean b2) {
        return b1 && b2;
      }
    };
  }

  /**
   * Returns a {@code Monoid} for boolean disjunction (`||`). Combination is `||`, identity is
   * `false`.
   */
  static Monoid<Boolean> booleanOr() {
    return new Monoid<Boolean>() {
      @Override
      public Boolean empty() {
        return false;
      }

      @Override
      public Boolean combine(Boolean b1, Boolean b2) {
        return b1 || b2;
      }
    };
  }
}
