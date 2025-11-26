/**
 * Core type testing framework.
 *
 * <p>Provides fluent APIs for testing built-in types:
 *
 * <ul>
 *   <li>Either - left/right operations, fold, getters
 *   <li>Maybe - just/nothing operations, get, orElse
 *   <li>IO - delay, unsafeRunSync, lazy evaluation
 *   <li>KindHelper implementations for all core types
 * </ul>
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Test Core Type Operations:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.either(Either.class)
 *     .withLeft(Either.left("error"))
 *     .withRight(Either.right(42))
 *     .testAll();
 * }</pre>
 *
 * <h3>Test KindHelper:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.eitherKindHelper(Either.right("test"))
 *     .test();
 * }</pre>
 *
 * @see org.higherkindedj.hkt.test.api.CoreTypeTest
 */
@NullMarked
package org.higherkindedj.hkt.test.api.coretype;

import org.jspecify.annotations.NullMarked;
