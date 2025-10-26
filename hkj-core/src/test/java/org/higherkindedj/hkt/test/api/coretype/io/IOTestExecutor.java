// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.io;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOFunctor;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOKindHelper;
import org.higherkindedj.hkt.io.IOMonad;
import org.higherkindedj.hkt.test.api.coretype.common.BaseCoreTypeTestExecutor;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.util.validation.Operation;

/**
 * Internal executor for IO core type tests.
 *
 * @param <A> The value type
 * @param <B> The mapped type
 */
final class IOTestExecutor<A, B> extends BaseCoreTypeTestExecutor<A, B, IOValidationStage<A, B>> {

  private final IO<A> ioInstance;

  private final boolean includeFactoryMethods;
  private final boolean includeExecution;
  private final boolean includeMap;
  private final boolean includeFlatMap;

  IOTestExecutor(
      Class<?> contextClass,
      IO<A> ioInstance,
      Function<A, B> mapper,
      boolean includeFactoryMethods,
      boolean includeExecution,
      boolean includeMap,
      boolean includeFlatMap,
      boolean includeValidations,
      boolean includeEdgeCases) {
    this(
        contextClass,
        ioInstance,
        mapper,
        includeFactoryMethods,
        includeExecution,
        includeMap,
        includeFlatMap,
        includeValidations,
        includeEdgeCases,
        null);
  }

  IOTestExecutor(
      Class<?> contextClass,
      IO<A> ioInstance,
      Function<A, B> mapper,
      boolean includeFactoryMethods,
      boolean includeExecution,
      boolean includeMap,
      boolean includeFlatMap,
      boolean includeValidations,
      boolean includeEdgeCases,
      IOValidationStage<A, B> validationStage) {

    super(contextClass, mapper, includeValidations, includeEdgeCases, validationStage);

    this.ioInstance = ioInstance;
    this.includeFactoryMethods = includeFactoryMethods;
    this.includeExecution = includeExecution;
    this.includeMap = includeMap;
    this.includeFlatMap = includeFlatMap;
  }

  @Override
  protected void executeOperationTests() {
    if (includeFactoryMethods) testFactoryMethods();
    if (includeExecution) testExecution();
    if (includeMap && hasMapper()) testMap();
    if (includeFlatMap && hasMapper()) testFlatMap();
  }

  @Override
  protected void executeValidationTests() {
    ValidationTestBuilder builder = ValidationTestBuilder.create();

    // Map validations - test through the Functor interface if custom context provided
    if (validationStage != null && validationStage.getMapContext() != null) {
      IOFunctor functor = IOFunctor.INSTANCE;
      Kind<IOKind.Witness, A> kind = IOKindHelper.IO_OP.widen(ioInstance);
      builder.assertMapperNull(() -> functor.map(null, kind), "f", getMapContext(), Operation.MAP);
    } else {
      builder.assertMapperNull(() -> ioInstance.map(null), "f", getMapContext(), Operation.MAP);
    }

    // FlatMap validations - test through the Monad interface if custom context provided
    if (validationStage != null && validationStage.getFlatMapContext() != null) {
      IOMonad monad = IOMonad.INSTANCE;
      Kind<IOKind.Witness, A> kind = IOKindHelper.IO_OP.widen(ioInstance);
      builder.assertFlatMapperNull(
          () -> monad.flatMap(null, kind), "f", getFlatMapContext(), Operation.FLAT_MAP);
    } else {
      builder.assertFlatMapperNull(
          () -> ioInstance.flatMap(null), "f", getFlatMapContext(), Operation.FLAT_MAP);
    }

    // Delay validation
    builder.assertFunctionNull(() -> IO.delay(null), "thunk", contextClass, Operation.DELAY);

    builder.execute();
  }

  @Override
  protected void executeEdgeCaseTests() {
    // Test IO that returns null
    IO<A> nullIO = IO.delay(() -> null);
    assertThat(nullIO.unsafeRunSync()).isNull();

    // Test nested IO execution
    IO<IO<A>> nestedIO = IO.delay(() -> ioInstance);
    IO<A> innerIO = nestedIO.unsafeRunSync();
    assertThat(innerIO).isNotNull();
    assertThat(innerIO.unsafeRunSync()).isNotNull();

    // Test that IO is truly lazy - no execution until unsafeRunSync
    AtomicBoolean sideEffect = new AtomicBoolean(false);
    IO<String> lazyWithSideEffect =
        IO.delay(
            () -> {
              sideEffect.set(true);
              return "executed";
            });

    // Create derived IOs - none should execute yet
    IO<Integer> mapped = lazyWithSideEffect.map(String::length);
    IO<String> flatMapped = lazyWithSideEffect.flatMap(s -> IO.delay(() -> s.toUpperCase()));

    assertThat(sideEffect).isFalse();

    // Only when we call unsafeRunSync should execution happen
    String result = lazyWithSideEffect.unsafeRunSync();
    assertThat(sideEffect).isTrue();
    assertThat(result).isEqualTo("executed");
  }

  private void testFactoryMethods() {
    // Test that delay() creates a valid IO
    IO<String> delayedIO = IO.delay(() -> "test");
    assertThat(delayedIO).isNotNull();

    // Test that the computation is lazy
    AtomicBoolean executed = new AtomicBoolean(false);
    IO<String> lazyIO =
        IO.delay(
            () -> {
              executed.set(true);
              return "lazy";
            });
    assertThat(executed).isFalse();

    // Only executes when unsafeRunSync is called
    String result = lazyIO.unsafeRunSync();
    assertThat(executed).isTrue();
    assertThat(result).isEqualTo("lazy");
  }

  private void testExecution() {
    // Test basic execution
    A result = ioInstance.unsafeRunSync();
    assertThat(result).isNotNull();

    // Test that execution is repeatable
    A result2 = ioInstance.unsafeRunSync();
    assertThat(result2).isNotNull();
  }

  private void testMap() {
    // Test map application
    IO<B> mappedIO = ioInstance.map(mapper);
    assertThat(mappedIO).isNotNull();

    B result = mappedIO.unsafeRunSync();
    assertThat(result).isNotNull();

    // Test map composition
    IO<String> composedIO = ioInstance.map(mapper).map(Object::toString);
    assertThat(composedIO).isNotNull();
    String composedResult = composedIO.unsafeRunSync();
    assertThat(composedResult).isNotNull();

    // Test exception propagation
    RuntimeException testException = new RuntimeException("Test exception: map test");
    Function<A, B> throwingMapper =
        a -> {
          throw testException;
        };
    IO<B> throwingIO = ioInstance.map(throwingMapper);
    assertThatThrownBy(throwingIO::unsafeRunSync).isSameAs(testException);
  }

  private void testFlatMap() {
    Function<A, IO<B>> flatMapper = a -> IO.delay(() -> mapper.apply(a));

    // Test flatMap application
    IO<B> flatMappedIO = ioInstance.flatMap(flatMapper);
    assertThat(flatMappedIO).isNotNull();

    B result = flatMappedIO.unsafeRunSync();
    assertThat(result).isNotNull();

    // Test flatMap chaining
    IO<String> chainedIO =
        ioInstance.flatMap(flatMapper).flatMap(b -> IO.delay(() -> b.toString()));
    assertThat(chainedIO).isNotNull();
    String chainedResult = chainedIO.unsafeRunSync();
    assertThat(chainedResult).isNotNull();

    // Test exception propagation
    RuntimeException testException = new RuntimeException("Test exception: flatMap test");
    Function<A, IO<B>> throwingFlatMapper =
        a -> {
          throw testException;
        };
    IO<B> throwingIO = ioInstance.flatMap(throwingFlatMapper);
    assertThatThrownBy(throwingIO::unsafeRunSync).isSameAs(testException);

    // Test null result validation
    Function<A, IO<B>> nullReturningMapper = a -> null;
    IO<B> nullIO = ioInstance.flatMap(nullReturningMapper);
    assertThatThrownBy(nullIO::unsafeRunSync)
        .isInstanceOf(KindUnwrapException.class)
        .hasMessageContaining(
            "Function f in IO.flatMap returned null when IO expected, which is not allowed");
  }
}
