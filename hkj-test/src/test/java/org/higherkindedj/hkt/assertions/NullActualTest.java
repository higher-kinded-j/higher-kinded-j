// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.assertj.core.api.AbstractAssert;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.effect.VResultPath;
import org.higherkindedj.hkt.effect.VStreamPath;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.effect.context.VTaskContext;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.eitherf.EitherF;
import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
import org.higherkindedj.hkt.eitherorboth.EitherOrBothKind;
import org.higherkindedj.hkt.error.ErrorEnvelope;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.lazy.Lazy;
import org.higherkindedj.hkt.lazy.LazyKind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.nonemptylist.NonEmptyListKind;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.reader.Reader;
import org.higherkindedj.hkt.reader.ReaderKind;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.stream.StreamKind;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.trymonad.TryKind;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vstream.VStreamKind;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.hkt.vtask.VTaskKind;
import org.higherkindedj.hkt.writer.Writer;
import org.higherkindedj.hkt.writer.WriterKind;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every {@code assertThat...} entry point accepts a null actual, as AssertJ's own does: {@code
 * isNull()} passes, and every other check fails with an {@link AssertionError} rather than a {@link
 * NullPointerException}.
 */
@DisplayName("A null actual")
class NullActualTest {

  private record Entry(String name, Supplier<AbstractAssert<?, ?>> entry) {
    @Override
    public String toString() {
      return name;
    }
  }

  private static Entry entry(String name, Supplier<AbstractAssert<?, ?>> entry) {
    return new Entry(name, entry);
  }

  static Stream<Entry> entries() {
    return Stream.of(
        entry("Either", () -> EitherAssert.assertThatEither((Either<String, Integer>) null)),
        entry(
            "Either Kind",
            () -> EitherAssert.assertThatEither((Kind<EitherKind.Witness<String>, Integer>) null)),
        entry("EitherF", () -> EitherFAssert.assertThatEitherF((EitherF<?, ?, Integer>) null)),
        entry(
            "EitherOrBoth",
            () -> EitherOrBothAssert.assertThatEitherOrBoth((EitherOrBoth<String, Integer>) null)),
        entry(
            "EitherOrBoth Kind",
            () ->
                EitherOrBothAssert.assertThatEitherOrBoth(
                    (Kind<EitherOrBothKind.Witness<String>, Integer>) null)),
        entry(
            "EitherT",
            () ->
                EitherTAssert.<OptionalKind.Witness, String, Integer>assertThatEitherT(
                    null, k -> Optional.empty())),
        entry(
            "ErrorEnvelope",
            () -> ErrorEnvelopeAssert.assertThatErrorEnvelope((ErrorEnvelope<String>) null)),
        entry("FieldError", () -> FieldErrorAssert.assertThatFieldError((FieldError) null)),
        entry("Free", () -> FreeAssert.assertThatFree((Free<OptionalKind.Witness, Integer>) null)),
        entry("IO", () -> IOAssert.assertThatIO((IO<Integer>) null)),
        entry("IO Kind", () -> IOAssert.assertThatIO((Kind<IOKind.Witness, Integer>) null)),
        entry("Id", () -> IdAssert.assertThatId((Kind<IdKind.Witness, Integer>) null)),
        entry("Lazy", () -> LazyAssert.assertThatLazy((Lazy<Integer>) null)),
        entry("Lazy Kind", () -> LazyAssert.assertThatLazy((Kind<LazyKind.Witness, Integer>) null)),
        entry("List", () -> ListAssert.assertThatList((Kind<ListKind.Witness, Integer>) null)),
        entry("Maybe", () -> MaybeAssert.assertThatMaybe((Maybe<Integer>) null)),
        entry(
            "Maybe Kind",
            () -> MaybeAssert.assertThatMaybe((Kind<MaybeKind.Witness, Integer>) null)),
        entry(
            "MaybeT",
            () ->
                MaybeTAssert.<OptionalKind.Witness, Integer>assertThatMaybeT(
                    null, k -> Optional.empty())),
        entry(
            "NonEmptyList",
            () -> NonEmptyListAssert.assertThatNonEmptyList((NonEmptyList<Integer>) null)),
        entry(
            "NonEmptyList Kind",
            () ->
                NonEmptyListAssert.assertThatNonEmptyList(
                    (Kind<NonEmptyListKind.Witness, Integer>) null)),
        entry(
            "OptionalKind",
            () ->
                OptionalKindAssert.assertThatOptionalKind(
                    (Kind<OptionalKind.Witness, Integer>) null)),
        entry(
            "OptionalT",
            () ->
                OptionalTAssert.<OptionalKind.Witness, Integer>assertThatOptionalT(
                    null, k -> Optional.empty())),
        entry("Reader", () -> ReaderAssert.assertThatReader((Reader<String, Integer>) null)),
        entry(
            "Reader Kind",
            () -> ReaderAssert.assertThatReader((Kind<ReaderKind.Witness<String>, Integer>) null)),
        entry(
            "ReaderT",
            () ->
                ReaderTAssert.<OptionalKind.Witness, String, Integer>assertThatReaderT(
                    null, k -> Optional.empty())),
        entry(
            "StateTuple",
            () -> StateAssert.assertThatStateTuple((StateTuple<String, Integer>) null)),
        entry(
            "StateT",
            () ->
                StateTAssert.<String, OptionalKind.Witness, Integer>assertThatStateT(
                    null, k -> Optional.empty())),
        entry(
            "Stream",
            () -> StreamAssert.assertThatStream((Kind<StreamKind.Witness, Integer>) null)),
        entry("Try", () -> TryAssert.assertThatTry((Try<Integer>) null)),
        entry("Try Kind", () -> TryAssert.assertThatTry((Kind<TryKind.Witness, Integer>) null)),
        entry(
            "VResultPath",
            () -> VResultPathAssert.assertThatVResultPath((VResultPath<String, Integer>) null)),
        entry("VStream", () -> VStreamAssert.assertThatVStream((VStream<Integer>) null)),
        entry(
            "VStream Kind",
            () -> VStreamAssert.assertThatVStream((Kind<VStreamKind.Witness, Integer>) null)),
        entry(
            "VStreamPath",
            () -> VStreamPathAssert.assertThatVStreamPath((VStreamPath<Integer>) null)),
        entry("VTask", () -> VTaskAssert.assertThatVTask((VTask<Integer>) null)),
        entry(
            "VTask Kind",
            () -> VTaskAssert.assertThatVTask((Kind<VTaskKind.Witness, Integer>) null)),
        entry(
            "VTaskContext",
            () -> VTaskContextAssert.assertThatVTaskContext((VTaskContext<Integer>) null)),
        entry("VTaskPath", () -> VTaskPathAssert.assertThatVTaskPath((VTaskPath<Integer>) null)),
        entry(
            "Validated",
            () -> ValidatedAssert.assertThatValidated((Validated<String, Integer>) null)),
        entry(
            "Validated described",
            () ->
                ValidatedAssert.assertThatValidated((Validated<String, Integer>) null, "a value")),
        entry(
            "Validated Kind",
            () ->
                ValidatedAssert.assertThatValidated(
                    (Kind<ValidatedKind.Witness<String>, Integer>) null)),
        entry(
            "WriterT",
            () ->
                WriterTAssert.<OptionalKind.Witness, String, Integer>assertThatWriterT(
                    null, k -> Optional.empty())),
        entry("Writer", () -> WriterAssert.assertThatWriter((Writer<String, Integer>) null)),
        entry(
            "Writer Kind",
            () -> WriterAssert.assertThatWriter((Kind<WriterKind.Witness<String>, Integer>) null)));
  }

  @Test
  @DisplayName("is listed here, and marked @Nullable, for every public assertThat... method")
  void everyEntryPointIsListed() throws Exception {
    Path classes =
        Path.of(EitherAssert.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    String pkg = EitherAssert.class.getPackageName();
    List<Method> entryPoints = new ArrayList<>();
    try (Stream<Path> files = Files.list(classes.resolve(pkg.replace('.', '/')))) {
      for (Path file : files.toList()) {
        String name = file.getFileName().toString();
        if (name.endsWith("Assert.class")) {
          for (Method method : Class.forName(pkg + "." + name.replace(".class", "")).getMethods()) {
            if (Modifier.isStatic(method.getModifiers())
                && method.getName().startsWith("assertThat")) {
              entryPoints.add(method);
            }
          }
        }
      }
    }

    assertThat(entryPoints)
        .allSatisfy(
            method ->
                assertThat(
                        method
                            .getParameters()[0]
                            .getAnnotatedType()
                            .isAnnotationPresent(Nullable.class))
                    .as("%s takes a @Nullable actual", method)
                    .isTrue());
    assertThat(entries().count()).isEqualTo(entryPoints.size());
  }

  @ParameterizedTest(name = "assertThat{0}(null).isNull() passes")
  @MethodSource("entries")
  @DisplayName("is accepted by the entry point and isNull() passes")
  void isNullPasses(Entry entry) {
    entry.entry().get().isNull();
  }

  @ParameterizedTest(name = "assertThat{0}(null).isNotNull() fails")
  @MethodSource("entries")
  @DisplayName("fails isNotNull() with an AssertionError")
  void isNotNullFails(Entry entry) {
    assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> entry.entry().get().isNotNull());
  }
}
