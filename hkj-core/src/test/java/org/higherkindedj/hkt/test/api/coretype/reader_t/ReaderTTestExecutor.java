// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for licence information.
package org.higherkindedj.hkt.test.api.coretype.reader_t;

import static org.assertj.core.api.Assertions.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.reader_t.ReaderT;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.util.validation.Operation;

/**
 * Internal executor for ReaderT core type tests.
 *
 * <p>This class coordinates test execution by delegating to appropriate test methods.
 *
 * @param <F> The outer monad witness type
 * @param <R> The environment type
 * @param <A> The value type
 * @param <B> The mapped type
 */
final class ReaderTTestExecutor<F, R, A, B> {
    private final Class<?> contextClass;
    private final Monad<F> outerMonad;
    private final ReaderT<F, R, A> readerTInstance;
    private final Function<A, B> mapper;

    private final boolean includeFactoryMethods;
    private final boolean includeRunnerMethods;
    private final boolean includeValidations;
    private final boolean includeEdgeCases;

    private final ReaderTValidationStage<F, R, A, B> validationStage;

    ReaderTTestExecutor(
            Class<?> contextClass,
            Monad<F> outerMonad,
            ReaderT<F, R, A> readerTInstance,
            Function<A, B> mapper,
            boolean includeFactoryMethods,
            boolean includeRunnerMethods,
            boolean includeValidations,
            boolean includeEdgeCases) {
        this(
                contextClass,
                outerMonad,
                readerTInstance,
                mapper,
                includeFactoryMethods,
                includeRunnerMethods,
                includeValidations,
                includeEdgeCases,
                null);
    }

    ReaderTTestExecutor(
            Class<?> contextClass,
            Monad<F> outerMonad,
            ReaderT<F, R, A> readerTInstance,
            Function<A, B> mapper,
            boolean includeFactoryMethods,
            boolean includeRunnerMethods,
            boolean includeValidations,
            boolean includeEdgeCases,
            ReaderTValidationStage<F, R, A, B> validationStage) {

        this.contextClass = contextClass;
        this.outerMonad = outerMonad;
        this.readerTInstance = readerTInstance;
        this.mapper = mapper;
        this.includeFactoryMethods = includeFactoryMethods;
        this.includeRunnerMethods = includeRunnerMethods;
        this.includeValidations = includeValidations;
        this.includeEdgeCases = includeEdgeCases;
        this.validationStage = validationStage;
    }

    void executeAll() {
        if (includeFactoryMethods) testFactoryMethods();
        if (includeRunnerMethods) testRunnerMethods();
        if (includeValidations) testValidations();
        if (includeEdgeCases) testEdgeCases();
    }

    private void testFactoryMethods() {
        // Test of() factory method
        Function<R, Kind<F, A>> runFunction = r -> outerMonad.of(null);
        ReaderT<F, R, A> fromOf = ReaderT.of(runFunction);
        assertThat(fromOf).isNotNull();
        assertThat(fromOf.run()).isNotNull();

        // Test liftF() factory method
        Kind<F, A> lifted = outerMonad.of(null);
        ReaderT<F, R, A> fromLiftF = ReaderT.liftF(outerMonad, lifted);
        assertThat(fromLiftF).isNotNull();
        assertThat(fromLiftF.run()).isNotNull();

        // Test reader() factory method
        Function<R, A> simpleFunction = r -> null;
        ReaderT<F, R, A> fromReader = ReaderT.reader(outerMonad, simpleFunction);
        assertThat(fromReader).isNotNull();
        assertThat(fromReader.run()).isNotNull();

        // Test ask() factory method
        ReaderT<F, R, R> fromAsk = ReaderT.ask(outerMonad);
        assertThat(fromAsk).isNotNull();
        assertThat(fromAsk.run()).isNotNull();
    }

    private void testRunnerMethods() {
        // Test run() accessor returns non-null
        Function<R, Kind<F, A>> runFunction = readerTInstance.run();
        assertThat(runFunction).as("run() should return non-null function").isNotNull();
    }

    void testValidations() {
        // Determine which class context to use
        Class<?> validationContext =
                (validationStage != null && validationStage.getValidationContext() != null)
                        ? validationStage.getValidationContext()
                        : contextClass;

        ValidationTestBuilder builder = ValidationTestBuilder.create();

        // Test of() null validation - uses FunctionValidator
        builder.assertFunctionNull(
                () -> ReaderT.of(null), "run", validationContext, Operation.CONSTRUCTION);

        // Test liftF() null monad validation - uses DomainValidator.requireOuterMonad
        builder.assertTransformerOuterMonadNull(
                () -> ReaderT.liftF(null, outerMonad.of(null)), validationContext, Operation.LIFT_F);

        // Test liftF() null Kind validation - uses KindValidator
        builder.assertKindNull(
                () -> ReaderT.liftF(outerMonad, null),
                validationContext,
                Operation.LIFT_F,
                "source Kind");

        // Test reader() null monad validation - uses DomainValidator.requireOuterMonad
        Function<R, A> validFunction = r -> null;
        builder.assertTransformerOuterMonadNull(
                () -> ReaderT.reader(null, validFunction), validationContext, Operation.READER);

        // Test reader() null function validation - uses FunctionValidator
        builder.assertFunctionNull(
                () -> ReaderT.reader(outerMonad, null),
                "environment function",
                validationContext,
                Operation.READER);

        // Test ask() null monad validation - uses DomainValidator.requireOuterMonad
        builder.assertTransformerOuterMonadNull(
                () -> ReaderT.ask(null), validationContext, Operation.ASK);

        builder.execute();
    }

    private void testEdgeCases() {
        // Test with null environment value (if allowed)
        Function<R, Kind<F, A>> nullEnvFunction = r -> outerMonad.of(null);
        ReaderT<F, R, A> withNullEnv = ReaderT.of(nullEnvFunction);
        assertThat(withNullEnv).isNotNull();
        assertThat(withNullEnv.run()).isNotNull();

        // Test liftF with null value
        Kind<F, A> nullLiftedValue = outerMonad.of(null);
        ReaderT<F, R, A> liftedNull = ReaderT.liftF(outerMonad, nullLiftedValue);
        assertThat(liftedNull).isNotNull();
        assertThat(liftedNull.run()).isNotNull();

        // Test reader with null-returning function
        Function<R, A> nullReturningFunction = r -> null;
        ReaderT<F, R, A> readerNull = ReaderT.reader(outerMonad, nullReturningFunction);
        assertThat(readerNull).isNotNull();
        assertThat(readerNull.run()).isNotNull();

        // Test toString
        assertThat(readerTInstance.toString()).isNotNull();

        // Test equals and hashCode
        Function<R, Kind<F, A>> sameFunction = readerTInstance.run();
        ReaderT<F, R, A> another = ReaderT.of(sameFunction);
        assertThat(readerTInstance).isEqualTo(another);
        assertThat(readerTInstance.hashCode()).isEqualTo(another.hashCode());
    }
}