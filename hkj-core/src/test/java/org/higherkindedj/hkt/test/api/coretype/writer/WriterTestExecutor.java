// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.writer;

import static org.assertj.core.api.Assertions.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.test.api.coretype.common.BaseCoreTypeTestExecutor;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.unit.Unit;
import org.higherkindedj.hkt.util.validation.Operation;
import org.higherkindedj.hkt.writer.Writer;
import org.higherkindedj.hkt.writer.WriterFunctor;
import org.higherkindedj.hkt.writer.WriterKind;
import org.higherkindedj.hkt.writer.WriterKindHelper;
import org.higherkindedj.hkt.writer.WriterMonad;

/**
 * Internal executor for Writer core type tests.
 *
 * @param <W> The log type
 * @param <A> The value type
 * @param <B> The mapped type
 */
final class WriterTestExecutor<W, A, B>
    extends BaseCoreTypeTestExecutor<A, B, WriterValidationStage<W, A, B>> {

  private final Writer<W, A> writerInstance;
  private final Monoid<W> monoid;

  private final boolean includeFactoryMethods;
  private final boolean includeRun;
  private final boolean includeMap;
  private final boolean includeFlatMap;

  WriterTestExecutor(
      Class<?> contextClass,
      Writer<W, A> writerInstance,
      Monoid<W> monoid,
      Function<A, B> mapper,
      boolean includeFactoryMethods,
      boolean includeRun,
      boolean includeMap,
      boolean includeFlatMap,
      boolean includeValidations,
      boolean includeEdgeCases) {
    this(
        contextClass,
        writerInstance,
        monoid,
        mapper,
        includeFactoryMethods,
        includeRun,
        includeMap,
        includeFlatMap,
        includeValidations,
        includeEdgeCases,
        null);
  }

  WriterTestExecutor(
      Class<?> contextClass,
      Writer<W, A> writerInstance,
      Monoid<W> monoid,
      Function<A, B> mapper,
      boolean includeFactoryMethods,
      boolean includeRun,
      boolean includeMap,
      boolean includeFlatMap,
      boolean includeValidations,
      boolean includeEdgeCases,
      WriterValidationStage<W, A, B> validationStage) {

    super(contextClass, mapper, includeValidations, includeEdgeCases, validationStage);

    this.writerInstance = writerInstance;
    this.monoid = monoid;
    this.includeFactoryMethods = includeFactoryMethods;
    this.includeRun = includeRun;
    this.includeMap = includeMap;
    this.includeFlatMap = includeFlatMap;
  }

  @Override
  protected void executeOperationTests() {
    if (includeFactoryMethods) testFactoryMethods();
    if (includeRun) testRun();
    if (includeMap && hasMapper()) testMap();
    if (includeFlatMap && hasMapper()) testFlatMap();
  }

  @Override
  protected void executeValidationTests() {
    ValidationTestBuilder builder = ValidationTestBuilder.create();

    // Map validations - test through the Functor interface if custom context provided
    if (validationStage != null && validationStage.getMapContext() != null) {
      WriterFunctor<W> functor = new WriterFunctor<>();
      Kind<WriterKind.Witness<W>, A> kind = WriterKindHelper.WRITER.widen(writerInstance);
      builder.assertMapperNull(() -> functor.map(null, kind), "f", getMapContext(), Operation.MAP);
    } else {
      builder.assertMapperNull(() -> writerInstance.map(null), "f", getMapContext(), Operation.MAP);
    }

    // FlatMap validations - test through the Monad interface if custom context provided
    if (validationStage != null && validationStage.getFlatMapContext() != null) {
      WriterMonad<W> monad = new WriterMonad<>(monoid);
      Kind<WriterKind.Witness<W>, A> kind = WriterKindHelper.WRITER.widen(writerInstance);
      builder.assertFlatMapperNull(
          () -> monad.flatMap(null, kind), "f", getFlatMapContext(), Operation.FLAT_MAP);
    } else {
      builder.assertFlatMapperNull(
          () -> writerInstance.flatMap(monoid, null), "f", getFlatMapContext(), Operation.FLAT_MAP);
    }

    // FlatMap monoid validation
    builder.assertMonoidNull(
        () -> writerInstance.flatMap(null, a -> writerInstance),
        "monoidW",
        contextClass,
        Operation.FLAT_MAP);

    // Value monoid validation
    builder.assertMonoidNull(
        () -> Writer.value(null, "test"), "monoidW", contextClass, Operation.VALUE);

    builder.execute();

    // Tell validation
    assertThatThrownBy(() -> Writer.tell(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Writer.tell log cannot be null");
  }

  @Override
  protected void executeEdgeCaseTests() {
    // Test log combining with flatMap
    W log1 = writerInstance.log();
    W log2 = monoid.empty();
    W combined = monoid.combine(log1, log2);
    assertThat(combined).isNotNull();

    // Test Writer with null value (if A allows nulls)
    Writer<W, A> nullValueWriter = new Writer<>(monoid.empty(), null);
    assertThat(nullValueWriter.value()).isNull();
    assertThat(nullValueWriter.run()).isNull();

    // Test record methods
    assertThat(writerInstance.log()).isNotNull();
    assertThat(writerInstance.value()).isEqualTo(writerInstance.run());

    // Test that tell produces Unit
    Writer<W, Unit> tellWriter = Writer.tell(log1);
    assertThat(tellWriter.value()).isEqualTo(Unit.INSTANCE);
  }

  private void testFactoryMethods() {
    // Test Writer.value creates correct instance with empty log
    Writer<W, String> valueWriter = Writer.value(monoid, "test");
    assertThat(valueWriter).isNotNull();
    assertThat(valueWriter.log()).isEqualTo(monoid.empty());
    assertThat(valueWriter.value()).isEqualTo("test");

    // Test Writer.tell creates correct instance
    W testLog = writerInstance.log();
    Writer<W, Unit> tellWriter = Writer.tell(testLog);
    assertThat(tellWriter).isNotNull();
    assertThat(tellWriter.log()).isEqualTo(testLog);
    assertThat(tellWriter.value()).isEqualTo(Unit.INSTANCE);
  }

  private void testRun() {
    // Test run returns value
    A result = writerInstance.run();
    assertThat(result).isEqualTo(writerInstance.value());

    // Test exec returns log
    W log = writerInstance.exec();
    assertThat(log).isEqualTo(writerInstance.log());
  }

  private void testMap() {
    // Test map application
    Writer<W, B> mappedWriter = writerInstance.map(mapper);
    assertThat(mappedWriter).isNotNull();
    assertThat(mappedWriter.log()).isEqualTo(writerInstance.log());

    B result = mappedWriter.value();
    assertThat(result).isNotNull();

    // Test map composition
    Writer<W, String> composedWriter = writerInstance.map(mapper).map(Object::toString);
    assertThat(composedWriter).isNotNull();
    assertThat(composedWriter.log()).isEqualTo(writerInstance.log());
    String composedResult = composedWriter.value();
    assertThat(composedResult).isNotNull();

    // Test exception propagation
    RuntimeException testException = new RuntimeException("Test exception: map test");
    Function<A, B> throwingMapper =
        a -> {
          throw testException;
        };
    assertThatThrownBy(() -> writerInstance.map(throwingMapper)).isSameAs(testException);
  }

  private void testFlatMap() {
    Function<A, Writer<W, B>> flatMapper = a -> new Writer<>(monoid.empty(), mapper.apply(a));

    // Test flatMap application
    Writer<W, B> flatMappedWriter = writerInstance.flatMap(monoid, flatMapper);
    assertThat(flatMappedWriter).isNotNull();

    // Log should be combined
    W combinedLog = flatMappedWriter.log();
    assertThat(combinedLog).isNotNull();

    B result = flatMappedWriter.value();
    assertThat(result).isNotNull();

    // Test flatMap chaining
    Writer<W, String> chainedWriter =
        writerInstance
            .flatMap(monoid, flatMapper)
            .flatMap(monoid, b -> new Writer<>(monoid.empty(), b.toString()));
    assertThat(chainedWriter).isNotNull();
    String chainedResult = chainedWriter.value();
    assertThat(chainedResult).isNotNull();

    // Test exception propagation
    RuntimeException testException = new RuntimeException("Test exception: flatMap test");
    Function<A, Writer<W, B>> throwingFlatMapper =
        a -> {
          throw testException;
        };
    assertThatThrownBy(() -> writerInstance.flatMap(monoid, throwingFlatMapper))
        .isSameAs(testException);

    // Test null result validation
    Function<A, Writer<W, B>> nullReturningMapper = a -> null;
    assertThatThrownBy(() -> writerInstance.flatMap(monoid, nullReturningMapper))
        .isInstanceOf(KindUnwrapException.class)
        .hasMessageContaining(
            "Function f in Writer.flatMap returned null when Writer expected, which is not"
                + " allowed");
  }
}
