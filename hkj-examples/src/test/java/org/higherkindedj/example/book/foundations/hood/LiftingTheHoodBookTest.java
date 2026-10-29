// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.hood;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.lazy.LazyKindHelper.LAZY;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.stream.StreamKindHelper.STREAM;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.higherkindedj.example.book.foundations.encoding.EncodingBook;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either.EitherMonad;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.lazy.Lazy;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Holds the claims the Lifting the Hood page makes about the round trip in {@link EncodingBook}:
 * how the call reaches {@code EitherMonad}, and which steps allocate.
 */
@DisplayName("Lifting the Hood: one round trip, call by call")
class LiftingTheHoodBookTest {

  @Test
  @DisplayName("the instance is a value: Instances.monad(either()) hands back the one EitherMonad")
  void theInstanceIsAValue() {
    Monad<EitherKind.Witness<String>> monad = Instances.monad(either());

    assertThat(monad)
        .isInstanceOf(EitherMonad.class)
        .isSameAs(Instances.<EitherKind.Witness<String>>monad(either()));
  }

  @Test
  @DisplayName("javac compiles the round trip's flatMap to invokeinterface Monad.flatMap")
  void flatMapIsAnInterfaceCall() throws IOException {
    byte[] bytes;
    try (InputStream in = EncodingBook.class.getResourceAsStream("EncodingBook.class")) {
      bytes = in.readAllBytes();
    }

    List<InvokeInstruction> flatMapCalls =
        ClassFile.of().parse(bytes).methods().stream()
            .flatMap(method -> method.code().stream())
            .flatMap(code -> code.elementStream())
            .filter(InvokeInstruction.class::isInstance)
            .map(InvokeInstruction.class::cast)
            .filter(call -> call.name().equalsString("flatMap"))
            .toList();

    assertThat(flatMapCalls)
        .anySatisfy(
            call -> {
              assertThat(call.opcode()).isEqualTo(Opcode.INVOKEINTERFACE);
              assertThat(call.owner().asInternalName()).isEqualTo("org/higherkindedj/hkt/Monad");
            })
        .noneSatisfy(
            call ->
                assertThat(call.owner().asInternalName())
                    .isEqualTo("org/higherkindedj/hkt/either/EitherMonad"));
  }

  @Test
  @DisplayName("a library type is already a Kind, so widen hands back the same object")
  void libraryTypesWidenToThemselves() {
    Maybe<Integer> maybe = Maybe.just(1);
    Either<String, Integer> either = Either.right(1);
    Try<Integer> attempt = Try.success(1);
    Validated<String, Integer> validated = Validated.valid(1);
    IO<Integer> io = IO.delay(() -> 1);
    Id<Integer> id = Id.of(1);

    assertThat(MAYBE.widen(maybe)).isSameAs(maybe);
    assertThat(EITHER.widen(either)).isSameAs(either);
    assertThat(TRY.widen(attempt)).isSameAs(attempt);
    assertThat(VALIDATED.widen(validated)).isSameAs(validated);
    assertThat(IO_OP.widen(io)).isSameAs(io);
    assertThat(ID.widen(id)).isSameAs(id);
  }

  @Test
  @DisplayName("a JDK type, or a Lazy, widens into a new holder, and narrow reads it back out")
  void jdkTypesAndLazyWidenIntoAHolder() {
    List<Integer> list = List.of(1);
    Optional<Integer> optional = Optional.of(1);
    Stream<Integer> stream = Stream.of(1);
    CompletableFuture<Integer> future = CompletableFuture.completedFuture(1);
    Lazy<Integer> lazy = Lazy.now(1);

    assertThat(LIST.widen(list)).isInstanceOf(Record.class).isNotSameAs(LIST.widen(list));
    assertThat(OPTIONAL.widen(optional)).isInstanceOf(Record.class);
    assertThat(STREAM.widen(stream)).isInstanceOf(Record.class);
    assertThat(FUTURE.widen(future)).isInstanceOf(Record.class);
    assertThat(LAZY.widen(lazy)).isInstanceOf(Record.class);

    assertThat(LIST.narrow(LIST.widen(list))).isSameAs(list);
    assertThat(OPTIONAL.narrow(OPTIONAL.widen(optional))).isSameAs(optional);
    assertThat(STREAM.narrow(STREAM.widen(stream))).isSameAs(stream);
    assertThat(FUTURE.narrow(FUTURE.widen(future))).isSameAs(future);
    assertThat(LAZY.narrow(LAZY.widen(lazy))).isSameAs(lazy);
  }

  @Test
  @DisplayName("on a Left, the function never runs and the same Left comes back")
  void aLeftPassesThroughUntouched() {
    Monad<EitherKind.Witness<String>> monad = Instances.monad(either());
    Kind<EitherKind.Witness<String>, Integer> left = EITHER.widen(Either.left("not found"));
    AtomicInteger calls = new AtomicInteger();

    Kind<EitherKind.Witness<String>, String> result =
        monad.flatMap(
            i -> {
              calls.incrementAndGet();
              return EITHER.widen(Either.right("v=" + i));
            },
            left);

    assertThat(result).isSameAs(left);
    assertThat(calls).hasValue(0);
  }

  @Test
  @DisplayName("Optional's flatMap widens its result into a new holder, where Either's needs none")
  void optionalFlatMapWidensItsResult() {
    Monad<OptionalKind.Witness> monad = Instances.monad(optional());
    Kind<OptionalKind.Witness, Integer> empty = OPTIONAL.widen(Optional.empty());

    Kind<OptionalKind.Witness, String> result =
        monad.flatMap(i -> OPTIONAL.widen(Optional.of("v=" + i)), empty);

    assertThat(result).isNotSameAs(empty);
    assertThat(OPTIONAL.narrow(result)).isSameAs(OPTIONAL.narrow(empty));
  }
}
