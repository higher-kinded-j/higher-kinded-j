// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.reader;

import static org.assertj.core.api.Assertions.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.reader.Reader;
import org.higherkindedj.hkt.reader.ReaderFunctor;
import org.higherkindedj.hkt.reader.ReaderKind;
import org.higherkindedj.hkt.reader.ReaderKindHelper;
import org.higherkindedj.hkt.reader.ReaderMonad;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.util.validation.Operation;

/**
 * Internal executor for Reader core type tests.
 *
 * @param <R> The environment type
 * @param <A> The value type
 * @param <B> The mapped type
 */
final class ReaderTestExecutor<R, A, B> {
  private final Class<?> contextClass;
  private final Reader<R, A> readerInstance;
  private final R environment;
  private final Function<A, B> mapper;

  private final boolean includeFactoryMethods;
  private final boolean includeRun;
  private final boolean includeMap;
  private final boolean includeFlatMap;
  private final boolean includeValidations;
  private final boolean includeEdgeCases;

  private final ReaderValidationStage<R, A, B> validationStage;

  ReaderTestExecutor(
      Class<?> contextClass,
      Reader<R, A> readerInstance,
      R environment,
      Function<A, B> mapper,
      boolean includeFactoryMethods,
      boolean includeRun,
      boolean includeMap,
      boolean includeFlatMap,
      boolean includeValidations,
      boolean includeEdgeCases) {
    this(
        contextClass,
        readerInstance,
        environment,
        mapper,
        includeFactoryMethods,
        includeRun,
        includeMap,
        includeFlatMap,
        includeValidations,
        includeEdgeCases,
        null);
  }

  ReaderTestExecutor(
      Class<?> contextClass,
      Reader<R, A> readerInstance,
      R environment,
      Function<A, B> mapper,
      boolean includeFactoryMethods,
      boolean includeRun,
      boolean includeMap,
      boolean includeFlatMap,
      boolean includeValidations,
      boolean includeEdgeCases,
      ReaderValidationStage<R, A, B> validationStage) {

    this.contextClass = contextClass;
    this.readerInstance = readerInstance;
    this.environment = environment;
    this.mapper = mapper;
    this.includeFactoryMethods = includeFactoryMethods;
    this.includeRun = includeRun;
    this.includeMap = includeMap;
    this.includeFlatMap = includeFlatMap;
    this.includeValidations = includeValidations;
    this.includeEdgeCases = includeEdgeCases;
    this.validationStage = validationStage;
  }

  void executeAll() {
    if (includeFactoryMethods) testFactoryMethods();
    if (includeRun) testRun();
    if (includeMap && mapper != null) testMap();
    if (includeFlatMap && mapper != null) testFlatMap();
    if (includeValidations) testValidations();
    if (includeEdgeCases) testEdgeCases();
  }

  private void testFactoryMethods() {
    // Test Reader.of creates correct instance
    Reader<R, A> ofReader = Reader.of(r -> readerInstance.run(r));
    assertThat(ofReader).isNotNull();

    // Test Reader.constant
    Reader<R, String> constantReader = Reader.constant("test");
    assertThat(constantReader.run(environment)).isEqualTo("test");

    // Test Reader.ask
    Reader<R, R> askReader = Reader.ask();
    assertThat(askReader.run(environment)).isSameAs(environment);
  }

  private void testRun() {
    // Test run returns expected value
    A result = readerInstance.run(environment);
    assertThat(result).isNotNull();

    // Test run is consistent
    A result2 = readerInstance.run(environment);
    assertThat(result2).isEqualTo(result);
  }

  private void testMap() {
    // Test map application
    Reader<R, B> mappedReader = readerInstance.map(mapper);
    assertThat(mappedReader).isNotNull();

    B result = mappedReader.run(environment);
    assertThat(result).isNotNull();

    // Test map composition
    Reader<R, String> composedReader = readerInstance.map(mapper).map(Object::toString);
    assertThat(composedReader).isNotNull();
    String composedResult = composedReader.run(environment);
    assertThat(composedResult).isNotNull();

    // Test exception propagation
    RuntimeException testException = new RuntimeException("Test exception: map test");
    Function<A, B> throwingMapper =
        a -> {
          throw testException;
        };
    Reader<R, B> throwingReader = readerInstance.map(throwingMapper);
    assertThatThrownBy(() -> throwingReader.run(environment)).isSameAs(testException);
  }

  private void testFlatMap() {
    Function<A, Reader<R, B>> flatMapper = a -> Reader.of(r -> mapper.apply(a));

    // Test flatMap application
    Reader<R, B> flatMappedReader = readerInstance.flatMap(flatMapper);
    assertThat(flatMappedReader).isNotNull();

    B result = flatMappedReader.run(environment);
    assertThat(result).isNotNull();

    // Test flatMap chaining
    Reader<R, String> chainedReader =
        readerInstance.flatMap(flatMapper).flatMap(b -> Reader.of(r -> b.toString()));
    assertThat(chainedReader).isNotNull();
    String chainedResult = chainedReader.run(environment);
    assertThat(chainedResult).isNotNull();

    // Test exception propagation
    RuntimeException testException = new RuntimeException("Test exception: flatMap test");
    Function<A, Reader<R, B>> throwingFlatMapper =
        a -> {
          throw testException;
        };
    Reader<R, B> throwingReader = readerInstance.flatMap(throwingFlatMapper);
    assertThatThrownBy(() -> throwingReader.run(environment)).isSameAs(testException);

    // Test null result validation
    Function<A, Reader<R, B>> nullReturningMapper = a -> null;
    Reader<R, B> nullReader = readerInstance.flatMap(nullReturningMapper);
    assertThatThrownBy(() -> nullReader.run(environment))
        .isInstanceOf(KindUnwrapException.class)
        .hasMessageContaining("Function f in Reader.flatMap returned null when Reader expected, which is not allowed");
  }

  void testValidations() {
    // Determine which class context to use for map
    Class<?> mapContext =
        (validationStage != null && validationStage.getMapContext() != null)
            ? validationStage.getMapContext()
            : contextClass;

    // Determine which class context to use for flatMap
    Class<?> flatMapContext =
        (validationStage != null && validationStage.getFlatMapContext() != null)
            ? validationStage.getFlatMapContext()
            : contextClass;

    ValidationTestBuilder builder = ValidationTestBuilder.create();

    // Map validations - test through the Functor interface if custom context provided
    if (validationStage != null && validationStage.getMapContext() != null) {
      ReaderFunctor<R> functor = new ReaderFunctor<>();
      Kind<ReaderKind.Witness<R>, A> kind = ReaderKindHelper.READER.widen(readerInstance);
      builder.assertMapperNull(() -> functor.map(null, kind), "f", mapContext, Operation.MAP);
    } else {
      builder.assertMapperNull(() -> readerInstance.map(null), "f", mapContext, Operation.MAP);
    }

    // FlatMap validations - test through the Monad interface if custom context provided
    if (validationStage != null && validationStage.getFlatMapContext() != null) {
      ReaderMonad<R> monad = ReaderMonad.instance();
      Kind<ReaderKind.Witness<R>, A> kind = ReaderKindHelper.READER.widen(readerInstance);
      builder.assertFlatMapperNull(
          () -> monad.flatMap(null, kind), "f", flatMapContext, Operation.FLAT_MAP);
    } else {
      builder.assertFlatMapperNull(
          () -> readerInstance.flatMap(null), "f", flatMapContext, Operation.FLAT_MAP);
    }

    // Of validation
    builder.assertFunctionNull(() -> Reader.of(null), "runFunction", contextClass, Operation.OF);

    builder.execute();
  }

  private void testEdgeCases() {
    // Test with null environment (if R allows nulls)
    // Note: This depends on the specific Reader implementation and environment type

    // Test ask identity
    Reader<R, R> askReader = Reader.ask();
    assertThat(askReader.run(environment)).isSameAs(environment);

    // Test constant ignores environment
    Reader<R, String> constantReader = Reader.constant("fixed");
    R differentEnv = environment; // Using same for simplicity
    assertThat(constantReader.run(differentEnv)).isEqualTo("fixed");

    // Test map preserves environment threading
    Reader<R, String> mappedAsk = Reader.<R>ask().map(Object::toString);
    assertThat(mappedAsk.run(environment)).isNotNull();
  }
}
