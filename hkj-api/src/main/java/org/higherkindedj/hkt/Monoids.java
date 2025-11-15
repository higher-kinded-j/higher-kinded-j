// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
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

  /** Returns a {@code Monoid} for long addition. Combination is `+`, identity is `0L`. */
  static Monoid<Long> longAddition() {
    return new Monoid<Long>() {
      @Override
      public Long empty() {
        return 0L;
      }

      @Override
      public Long combine(Long l1, Long l2) {
        return l1 + l2;
      }
    };
  }

  /** Returns a {@code Monoid} for long multiplication. Combination is `*`, identity is `1L`. */
  static Monoid<Long> longMultiplication() {
    return new Monoid<Long>() {
      @Override
      public Long empty() {
        return 1L;
      }

      @Override
      public Long combine(Long l1, Long l2) {
        return l1 * l2;
      }
    };
  }

  /** Returns a {@code Monoid} for double addition. Combination is `+`, identity is `0.0`. */
  static Monoid<Double> doubleAddition() {
    return new Monoid<Double>() {
      @Override
      public Double empty() {
        return 0.0;
      }

      @Override
      public Double combine(Double d1, Double d2) {
        return d1 + d2;
      }
    };
  }

  /** Returns a {@code Monoid} for double multiplication. Combination is `*`, identity is `1.0`. */
  static Monoid<Double> doubleMultiplication() {
    return new Monoid<Double>() {
      @Override
      public Double empty() {
        return 1.0;
      }

      @Override
      public Double combine(Double d1, Double d2) {
        return d1 * d2;
      }
    };
  }

  /**
   * Returns a {@code Monoid} for {@link Optional} that selects the first non-empty optional.
   *
   * <p>The combination takes the first optional if it is present, otherwise the second. The
   * identity is {@code Optional.empty()}.
   *
   * @param <A> The type contained in the optional.
   * @return A non-null {@code Monoid} for first-wins optional combination.
   */
  static <A> Monoid<Optional<A>> firstOptional() {
    return new Monoid<Optional<A>>() {
      @Override
      public Optional<A> empty() {
        return Optional.empty();
      }

      @Override
      public Optional<A> combine(Optional<A> opt1, Optional<A> opt2) {
        return opt1.isPresent() ? opt1 : opt2;
      }
    };
  }

  /**
   * Returns a {@code Monoid} for {@link Optional} that selects the last non-empty optional.
   *
   * <p>The combination takes the second optional if it is present, otherwise the first. The
   * identity is {@code Optional.empty()}.
   *
   * @param <A> The type contained in the optional.
   * @return A non-null {@code Monoid} for last-wins optional combination.
   */
  static <A> Monoid<Optional<A>> lastOptional() {
    return new Monoid<Optional<A>>() {
      @Override
      public Optional<A> empty() {
        return Optional.empty();
      }

      @Override
      public Optional<A> combine(Optional<A> opt1, Optional<A> opt2) {
        return opt2.isPresent() ? opt2 : opt1;
      }
    };
  }

  /**
   * Returns a {@code Monoid} for {@link Optional} that returns the maximum value according to the
   * given comparator.
   *
   * <p>The combination returns whichever optional contains the greater value, or the non-empty one
   * if only one is present. The identity is {@code Optional.empty()}.
   *
   * @param <A> The type contained in the optional.
   * @param comparator The comparator to use for determining the maximum value.
   * @return A non-null {@code Monoid} for maximum optional combination.
   */
  static <A> Monoid<Optional<A>> maximum(final Comparator<A> comparator) {
    return new Monoid<Optional<A>>() {
      @Override
      public Optional<A> empty() {
        return Optional.empty();
      }

      @Override
      public Optional<A> combine(Optional<A> opt1, Optional<A> opt2) {
        if (opt1.isPresent() && opt2.isPresent()) {
          return comparator.compare(opt1.get(), opt2.get()) >= 0 ? opt1 : opt2;
        }
        return opt1.isPresent() ? opt1 : opt2;
      }
    };
  }

  /**
   * Returns a {@code Monoid} for {@link Optional} that returns the maximum value for comparable
   * types.
   *
   * <p>The combination returns whichever optional contains the greater value, or the non-empty one
   * if only one is present. The identity is {@code Optional.empty()}.
   *
   * @param <A> The comparable type contained in the optional.
   * @return A non-null {@code Monoid} for maximum optional combination.
   */
  static <A extends Comparable<? super A>> Monoid<Optional<A>> maximum() {
    return maximum(Comparator.<A>naturalOrder());
  }

  /**
   * Returns a {@code Monoid} for {@link Optional} that returns the minimum value according to the
   * given comparator.
   *
   * <p>The combination returns whichever optional contains the lesser value, or the non-empty one
   * if only one is present. The identity is {@code Optional.empty()}.
   *
   * @param <A> The type contained in the optional.
   * @param comparator The comparator to use for determining the minimum value.
   * @return A non-null {@code Monoid} for minimum optional combination.
   */
  static <A> Monoid<Optional<A>> minimum(final Comparator<A> comparator) {
    return new Monoid<Optional<A>>() {
      @Override
      public Optional<A> empty() {
        return Optional.empty();
      }

      @Override
      public Optional<A> combine(Optional<A> opt1, Optional<A> opt2) {
        if (opt1.isPresent() && opt2.isPresent()) {
          return comparator.compare(opt1.get(), opt2.get()) <= 0 ? opt1 : opt2;
        }
        return opt1.isPresent() ? opt1 : opt2;
      }
    };
  }

  /**
   * Returns a {@code Monoid} for {@link Optional} that returns the minimum value for comparable
   * types.
   *
   * <p>The combination returns whichever optional contains the lesser value, or the non-empty one
   * if only one is present. The identity is {@code Optional.empty()}.
   *
   * @param <A> The comparable type contained in the optional.
   * @return A non-null {@code Monoid} for minimum optional combination.
   */
  static <A extends Comparable<? super A>> Monoid<Optional<A>> minimum() {
    return minimum(Comparator.<A>naturalOrder());
  }
}
