/**
 * Provides a fluent API for building for-comprehensions to compose monadic operations.
 *
 * <p>The central class, {@link org.higherkindedj.hkt.expression.For}, offers a statically-typed
 * builder that simulates the for-comprehension syntax found in languages like Scala, making it
 * easier to sequence {@code flatMap}, {@code map}, and filtering operations in a readable, linear
 * style.
 */
@NullMarked
@GenerateForComprehensions(minArity = 2, maxArity = 8)
package org.higherkindedj.hkt.expression;

import org.higherkindedj.optics.annotations.GenerateForComprehensions;
import org.jspecify.annotations.NullMarked;
