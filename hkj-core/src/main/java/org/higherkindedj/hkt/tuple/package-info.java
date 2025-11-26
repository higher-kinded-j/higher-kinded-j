/**
 * Provides immutable, structural tuple types for holding a fixed number of potentially
 * heterogeneous elements.
 *
 * <p>This package includes the sealed interface {@link org.higherkindedj.hkt.tuple.Tuple} and its
 * concrete record implementations from {@link org.higherkindedj.hkt.tuple.Tuple2} to {@link
 * org.higherkindedj.hkt.tuple.Tuple5}. These are primarily used by the for-comprehension builder in
 * the {@code hkt.expression} package to accumulate results from monadic steps.
 */
@NullMarked
package org.higherkindedj.hkt.tuple;

import org.jspecify.annotations.NullMarked;
