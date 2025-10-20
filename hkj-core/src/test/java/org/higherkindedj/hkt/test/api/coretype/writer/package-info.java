/**
 * Writer core type testing with progressive disclosure API.
 *
 * <p>This package provides fluent APIs for testing the Writer type:
 *
 * <ul>
 *   <li>Factory operations (value, tell)
 *   <li>run and exec evaluation
 *   <li>map and flatMap transformations
 *   <li>Log accumulation with Monoid
 *   <li>KindHelper implementation
 * </ul>
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Test Core Type Operations:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.writer(Writer.class)
 *     .withWriter(Writer.tell("log"))
 *     .withMonoid(stringMonoid)
 *     .withMappers(String::toUpperCase)
 *     .testAll();
 * }</pre>
 *
 * <h3>Test KindHelper:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.writerKindHelper(Writer.value(monoid, 42))
 *     .test();
 * }</pre>
 *
 * <h3>Selective Testing:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.writer(Writer.class)
 *     .withWriter(writerInstance)
 *     .withMonoid(monoid)
 *     .withMappers(mapper)
 *     .skipValidations()
 *     .testAll();
 * }</pre>
 *
 * @see org.higherkindedj.hkt.test.api.CoreTypeTest
 */
@org.jspecify.annotations.NullMarked
package org.higherkindedj.hkt.test.api.coretype.writer;
