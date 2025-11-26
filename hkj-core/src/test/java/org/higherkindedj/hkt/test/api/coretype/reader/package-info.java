/**
 * Reader core type testing with progressive disclosure API.
 *
 * <p>This package provides fluent APIs for testing the Reader type:
 *
 * <ul>
 *   <li>Factory operations (of, constant, ask)
 *   <li>run evaluation
 *   <li>map and flatMap transformations
 *   <li>Environment threading
 *   <li>KindHelper implementation
 * </ul>
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Test Core Type Operations:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.reader(Reader.class)
 *     .withReader(Reader.ask())
 *     .withEnvironment("test-env")
 *     .withMappers(String::toUpperCase)
 *     .testAll();
 * }</pre>
 *
 * <h3>Test KindHelper:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.readerKindHelper(Reader.constant("test"))
 *     .test();
 * }</pre>
 *
 * <h3>Selective Testing:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.reader(Reader.class)
 *     .withReader(readerInstance)
 *     .withEnvironment(environment)
 *     .withMappers(mapper)
 *     .skipEdgeCases()
 *     .testAll();
 * }</pre>
 *
 * @see org.higherkindedj.hkt.test.api.CoreTypeTest
 */
@NullMarked
package org.higherkindedj.hkt.test.api.coretype.reader;

import org.jspecify.annotations.NullMarked;
