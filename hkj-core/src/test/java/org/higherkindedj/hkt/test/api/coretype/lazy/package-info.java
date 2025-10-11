/**
 * Lazy core type testing with progressive disclosure API.
 *
 * <p>This package provides fluent APIs for testing the Lazy type:
 * <ul>
 *   <li>defer/now operations
 *   <li>force evaluation
 *   <li>map and flatMap transformations
 *   <li>Memoisation semantics
 *   <li>Thread safety
 *   <li>KindHelper implementation
 * </ul>
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Test Core Type Operations:</h3>
 * <pre>{@code
 * CoreTypeTest.lazy(Lazy.class)
 *     .withDeferred(Lazy.defer(() -> 42))
 *     .withNow(Lazy.now(24))
 *     .withMappers(Object::toString)
 *     .testAll();
 * }</pre>
 *
 * <h3>Test KindHelper:</h3>
 * <pre>{@code
 * CoreTypeTest.lazyKindHelper(Lazy.now("test"))
 *     .test();
 * }</pre>
 *
 * <h3>Selective Testing:</h3>
 * <pre>{@code
 * CoreTypeTest.lazy(Lazy.class)
 *     .withDeferred(deferredInstance)
 *     .withNow(nowInstance)
 *     .withMappers(mapper)
 *     .skipConcurrency()
 *     .testAll();
 * }</pre>
 *
 * @see org.higherkindedj.hkt.test.api.CoreTypeTest
 */
@org.jspecify.annotations.NullMarked
package org.higherkindedj.hkt.test.api.coretype.lazy;