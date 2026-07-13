/**
 * Provides extended functional interfaces that are not available in the standard Java {@code
 * java.util.function} package.
 *
 * <p>This includes interfaces for functions with more than two arguments: {@link
 * org.higherkindedj.hkt.function.Function3}, {@link org.higherkindedj.hkt.function.Function4},
 * {@link org.higherkindedj.hkt.function.Function5}, {@link
 * org.higherkindedj.hkt.function.Function6}, {@link org.higherkindedj.hkt.function.Function7},
 * {@link org.higherkindedj.hkt.function.Function8}, {@link
 * org.higherkindedj.hkt.function.Function9}, {@link org.higherkindedj.hkt.function.Function10},
 * {@link org.higherkindedj.hkt.function.Function11}, {@link
 * org.higherkindedj.hkt.function.Function12}, and {@link org.higherkindedj.hkt.function.Function13}
 * through {@link org.higherkindedj.hkt.function.Function16}. {@code Function3} through {@code
 * Function12} support the {@code mapN} and {@code yield} methods in type classes like {@link
 * org.higherkindedj.hkt.Applicative} and in the {@code org.higherkindedj.hkt.expression.For}
 * comprehension builder (up to 12 values); {@code Function13} through {@code Function16}
 * additionally back the terminal step of the accumulating-assembly ladder ({@code
 * Validated.fields()} / {@code accumulate()}), combining up to 16 validated fields into a record.
 */
@NullMarked
package org.higherkindedj.hkt.function;

import org.jspecify.annotations.NullMarked;
