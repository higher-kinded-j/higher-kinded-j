/**
 * Provides extended functional interfaces that are not available in the standard Java {@code
 * java.util.function} package.
 *
 * <p>This includes interfaces for functions with more than two arguments: {@link
 * org.higherkindedj.hkt.function.Function3}, {@link org.higherkindedj.hkt.function.Function4},
 * {@link org.higherkindedj.hkt.function.Function5}, {@link
 * org.higherkindedj.hkt.function.Function6}, {@link org.higherkindedj.hkt.function.Function7}, and
 * {@link org.higherkindedj.hkt.function.Function8}. These are primarily used to support the {@code
 * mapN} and {@code yield} methods in type classes like {@link org.higherkindedj.hkt.Applicative}
 * and in the {@code org.higherkindedj.hkt.expression.For} comprehension builder, enabling the
 * combination of up to 8 monadic values.
 */
@NullMarked
package org.higherkindedj.hkt.function;

import org.jspecify.annotations.NullMarked;
