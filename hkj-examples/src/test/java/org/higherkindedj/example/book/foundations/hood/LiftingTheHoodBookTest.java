// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.hood;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.example.book.foundations.encoding.EncodingBook;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either.EitherMonad;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Holds the claims the Lifting the Hood page can test about the round trip in {@link EncodingBook}:
 * how the call reaches {@code EitherMonad}, and which steps build a new object.
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
    try (InputStream in =
        Objects.requireNonNull(EncodingBook.class.getResourceAsStream("EncodingBook.class"))) {
      bytes = in.readAllBytes();
    }

    // The round trip is EncodingBook's main, so only its calls are read
    List<InvokeInstruction> flatMapCalls =
        ClassFile.of().parse(bytes).methods().stream()
            .filter(method -> method.methodName().equalsString("main"))
            .flatMap(method -> method.code().stream())
            .flatMap(code -> code.elementStream())
            .filter(InvokeInstruction.class::isInstance)
            .map(InvokeInstruction.class::cast)
            .filter(call -> call.name().equalsString("flatMap"))
            .toList();

    assertThat(flatMapCalls)
        .isNotEmpty()
        .allSatisfy(
            call -> {
              assertThat(call.opcode()).isEqualTo(Opcode.INVOKEINTERFACE);
              assertThat(call.owner().asInternalName()).isEqualTo("org/higherkindedj/hkt/Monad");
            });
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
  @DisplayName("Either's flatMap hands back the Kind the function built; Optional's adds a holder")
  void onlyOptionalWidensTheResult() {
    Monad<EitherKind.Witness<String>> eitherMonad = Instances.monad(either());
    Kind<EitherKind.Witness<String>, String> built = EITHER.widen(Either.right("v=10"));

    Kind<EitherKind.Witness<String>, String> fromEither =
        eitherMonad.flatMap(i -> built, EITHER.widen(Either.right(10)));

    Monad<OptionalKind.Witness> optionalMonad = Instances.monad(optional());
    Kind<OptionalKind.Witness, Integer> empty = OPTIONAL.widen(Optional.empty());

    Kind<OptionalKind.Witness, String> fromOptional =
        optionalMonad.flatMap(i -> OPTIONAL.widen(Optional.of("v=" + i)), empty);

    assertThat(fromEither).isSameAs(built);
    assertThat(fromOptional).isNotSameAs(empty);
    assertThat(OPTIONAL.narrow(fromOptional)).isSameAs(OPTIONAL.narrow(empty));
  }
}
