// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader;

import static org.higherkindedj.hkt.reader.ReaderKindHelper.READER;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;

/**
 * Base class for Reader type class tests.
 *
 * <p>Provides common fixture creation, standardised test constants, and helper methods for all
 * Reader type class tests, eliminating duplication across Functor, Applicative, and Monad tests.
 *
 * <h2>Test Environment</h2>
 *
 * <p>Uses a simple {@link TestConfig} record as the environment type for all tests, providing:
 *
 * <ul>
 *   <li>{@link TestConfig#url()} - A database URL string
 *   <li>{@link TestConfig#maxConnections()} - Maximum number of connections
 * </ul>
 *
 * <h2>Test Constants</h2>
 *
 * <p>Standardised test values are provided to ensure consistency across all Reader tests:
 *
 * <ul>
 *   <li>{@link #TEST_CONFIG} - The primary test environment
 *   <li>{@link #ALTERNATIVE_CONFIG} - A secondary test environment
 *   <li>{@link #DEFAULT_URL} - The default database URL
 *   <li>{@link #DEFAULT_MAX_CONNECTIONS} - The default max connections value
 * </ul>
 *
 * <h2>Helper Methods</h2>
 *
 * <p>Provides convenience methods for creating Reader instances and working with the test
 * environment:
 *
 * <ul>
 *   <li>{@link #readerOf(Function)} - Creates a Reader from a function
 *   <li>{@link #constantReader(Object)} - Creates a constant Reader
 *   <li>{@link #askReader()} - Creates a Reader that returns the environment
 *   <li>{@link #narrowToReader(Kind)} - Converts a Kind to a Reader instance
 *   <li>{@link #runReader(Kind, TestConfig)} - Executes a Reader with the given environment
 * </ul>
 */
abstract class ReaderTestBase
        extends TypeClassTestBase<
        ReaderKind.Witness<ReaderTestBase.TestConfig>, Integer, String> {

    // ============================================================================
    // Test Environment Type
    // ============================================================================

    /** Simple environment type for testing Reader operations. */
    public record TestConfig(String url, int maxConnections) {}

    // ============================================================================
    // Test Constants - Standardised Values
    // ============================================================================

    /** Default database URL for test configurations. */
    protected static final String DEFAULT_URL = "jdbc:test:database";

    /** Default maximum connections for test configurations. */
    protected static final int DEFAULT_MAX_CONNECTIONS = 10;

    /** Alternative database URL for testing with different configurations. */
    protected static final String ALTERNATIVE_URL = "jdbc:alternative:database";

    /** Alternative maximum connections for testing with different configurations. */
    protected static final int ALTERNATIVE_MAX_CONNECTIONS = 5;

    /** Primary test environment used across all Reader tests. */
    protected static final TestConfig TEST_CONFIG = new TestConfig(DEFAULT_URL, DEFAULT_MAX_CONNECTIONS);

    /** Alternative test environment for testing with different configurations. */
    protected static final TestConfig ALTERNATIVE_CONFIG =
            new TestConfig(ALTERNATIVE_URL, ALTERNATIVE_MAX_CONNECTIONS);

    // ============================================================================
    // Helper Methods for Reader Creation
    // ============================================================================

    /**
     * Creates a Reader from a function.
     *
     * @param <A> The type of the value produced by the Reader
     * @param runFunction The function to wrap in a Reader
     * @return A new Reader instance
     */
    protected <A> Reader<TestConfig, A> readerOf(Function<TestConfig, A> runFunction) {
        return Reader.of(runFunction);
    }

    /**
     * Creates a constant Reader that ignores the environment.
     *
     * @param <A> The type of the constant value
     * @param value The constant value to be returned by the Reader
     * @return A new constant Reader instance
     */
    protected <A> Reader<TestConfig, A> constantReader(A value) {
        return Reader.constant(value);
    }

    /**
     * Creates a Reader that returns the environment itself.
     *
     * @return A new Reader instance that yields the environment
     */
    protected Reader<TestConfig, TestConfig> askReader() {
        return Reader.ask();
    }

    /**
     * Converts a Kind to a Reader instance.
     *
     * <p>This is a convenience method to make test code more readable by avoiding repeated
     * READER.narrow() calls.
     *
     * @param <A> The type of the value produced by the Reader
     * @param kind The Kind to convert
     * @return The underlying Reader instance
     */
    protected <A> Reader<TestConfig, A> narrowToReader(Kind<ReaderKind.Witness<TestConfig>, A> kind) {
        return READER.narrow(kind);
    }

    /**
     * Executes a Reader with the given environment.
     *
     * @param <A> The type of the value produced by the Reader
     * @param kind The Kind wrapping the Reader to execute
     * @param environment The environment to provide to the Reader
     * @return The result of running the Reader
     */
    protected <A> A runReader(Kind<ReaderKind.Witness<TestConfig>, A> kind, TestConfig environment) {
        return READER.runReader(kind, environment);
    }

    // ============================================================================
    // Convenience Methods for Common Reader Patterns
    // ============================================================================

    /**
     * Creates a Reader that extracts the URL from the environment.
     *
     * @return A Reader that produces the URL string
     */
    protected Reader<TestConfig, String> urlReader() {
        return Reader.of(TestConfig::url);
    }

    /**
     * Creates a Reader that extracts the max connections from the environment.
     *
     * @return A Reader that produces the max connections integer
     */
    protected Reader<TestConfig, Integer> maxConnectionsReader() {
        return Reader.of(TestConfig::maxConnections);
    }

    /**
     * Creates a Kind wrapping a Reader that extracts the URL from the environment.
     *
     * @return A Kind wrapping the URL Reader
     */
    protected Kind<ReaderKind.Witness<TestConfig>, String> urlKind() {
        return READER.widen(urlReader());
    }

    /**
     * Creates a Kind wrapping a Reader that extracts the max connections from the environment.
     *
     * @return A Kind wrapping the max connections Reader
     */
    protected Kind<ReaderKind.Witness<TestConfig>, Integer> maxConnectionsKind() {
        return READER.widen(maxConnectionsReader());
    }

    // ============================================================================
    // Integer-based Fixtures (from parent class requirements)
    // ============================================================================

    @Override
    protected Kind<ReaderKind.Witness<TestConfig>, Integer> createValidKind() {
        return READER.widen(Reader.of(TestConfig::maxConnections));
    }

    @Override
    protected Kind<ReaderKind.Witness<TestConfig>, Integer> createValidKind2() {
        return READER.widen(Reader.of(config -> config.maxConnections() * 2));
    }

    @Override
    protected Function<Integer, String> createValidMapper() {
        return i -> "Connections: " + i;
    }

    @Override
    protected Function<Integer, Kind<ReaderKind.Witness<TestConfig>, String>> createValidFlatMapper() {
        return i -> READER.widen(Reader.of(config -> config.url() + ":" + i));
    }

    @Override
    protected Kind<ReaderKind.Witness<TestConfig>, Function<Integer, String>>
    createValidFunctionKind() {
        return READER.widen(Reader.constant(i -> "Value: " + i));
    }

    @Override
    protected BiFunction<Integer, Integer, String> createValidCombiningFunction() {
        return (a, b) -> "Combined: " + a + " and " + b;
    }

    @Override
    protected Integer createTestValue() {
        return DEFAULT_MAX_CONNECTIONS;
    }

    @Override
    protected Function<String, String> createSecondMapper() {
        return s -> s.toUpperCase();
    }

    @Override
    protected Function<Integer, Kind<ReaderKind.Witness<TestConfig>, String>> createTestFunction() {
        return i -> READER.widen(Reader.of(config -> config.url() + " [" + i + "]"));
    }

    @Override
    protected Function<String, Kind<ReaderKind.Witness<TestConfig>, String>> createChainFunction() {
        return s -> READER.widen(Reader.of(config -> s + " @ " + config.url()));
    }

    @Override
    protected BiPredicate<
            Kind<ReaderKind.Witness<TestConfig>, ?>, Kind<ReaderKind.Witness<TestConfig>, ?>>
    createEqualityChecker() {
        return (k1, k2) -> {
            // For Reader, we compare results when run with the test environment
            Object result1 = READER.runReader(k1, TEST_CONFIG);
            Object result2 = READER.runReader(k2, TEST_CONFIG);
            if (result1 == null && result2 == null) return true;
            if (result1 == null || result2 == null) return false;
            return result1.equals(result2);
        };
    }
}