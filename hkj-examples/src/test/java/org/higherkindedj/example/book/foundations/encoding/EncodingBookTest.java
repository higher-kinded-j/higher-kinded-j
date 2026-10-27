// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.encoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.constant.ConstKindHelper.CONST;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.instances.Witnesses.stream;
import static org.higherkindedj.hkt.lazy.LazyKindHelper.LAZY;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.maybe_t.MaybeTKindHelper.MAYBE_T;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.optional_t.OptionalTKindHelper.OPTIONAL_T;
import static org.higherkindedj.hkt.stream.StreamKindHelper.STREAM;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.constant.ConstApplicative;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOMonad;
import org.higherkindedj.hkt.lazy.Lazy;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe_t.MaybeTMonad;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional_t.OptionalTMonad;
import org.higherkindedj.hkt.reader.Reader;
import org.higherkindedj.hkt.state.State;
import org.higherkindedj.hkt.trampoline.Trampoline;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.tuple.Tuple;
import org.higherkindedj.hkt.tuple.Tuple2;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.hkt.writer.Writer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Holds the claims the How the Encoding Works page makes. */
@DisplayName("How the Encoding Works")
class EncodingBookTest {

  @Nested
  @DisplayName("the round trip through Kind")
  class RoundTrip {

    @Test
    @DisplayName("widen, flatMap and narrow give what calling the Either directly gives")
    void sameAnswerAsTheEither() {
      Kind<EitherKind.Witness<String>, String> result =
          Instances.<EitherKind.Witness<String>>monad(either())
              .flatMap(i -> EITHER.widen(Either.right("v=" + i)), EITHER.widen(Either.right(10)));

      Either<String, String> direct =
          Either.<String, Integer>right(10).flatMap(i -> Either.right("v=" + i));
      assertThat(EITHER.narrow(result)).isEqualTo(direct).isEqualTo(Either.right("v=10"));
    }

    @Test
    @DisplayName("a type that is its own Kind widens to the same object, with no holder")
    void ownKindWidensToItself() {
      Either<String, Integer> either = Either.right(10);
      Maybe<Integer> maybe = Maybe.just(10);

      assertThat(EITHER.widen(either)).isSameAs(either);
      assertThat(MAYBE.widen(maybe)).isSameAs(maybe);
      assertThat(MAYBE.narrow(MAYBE.widen(maybe))).isSameAs(maybe);
    }

    @Test
    @DisplayName("a JDK type, and Lazy, widen into a holder, and narrow returns the original")
    void heldTypesWidenIntoAHolder() {
      Optional<String> optional = Optional.of("x");
      Kind<OptionalKind.Witness, String> kind = OPTIONAL.widen(optional);
      Lazy<Integer> lazy = Lazy.now(1);

      assertThat(kind).isNotSameAs(optional).isInstanceOf(OptionalKind.class);
      assertThat(OPTIONAL.narrow(kind)).isSameAs(optional);
      assertThat((Object) LAZY.widen(lazy)).isNotSameAs(lazy);
      assertThat(LAZY.narrow(LAZY.widen(lazy))).isSameAs(lazy);
    }

    @Test
    @DisplayName("narrow throws KindUnwrapException for a null or another container's Kind")
    @SuppressWarnings("unchecked") // a Kind of another type, forced past the compiler
    void narrowRefusesWhatItCannotUnwrap() {
      Kind<EitherKind.Witness<String>, Integer> listInDisguise =
          (Kind<EitherKind.Witness<String>, Integer>) (Kind<?, ?>) LIST.widen(List.of(1));

      assertThatThrownBy(() -> EITHER.narrow(null)).isInstanceOf(KindUnwrapException.class);
      assertThatThrownBy(() -> EITHER.narrow(listInDisguise))
          .isInstanceOf(KindUnwrapException.class);
      assertThat(new KindUnwrapException("x")).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("values, never null")
  class ValuesNeverNull {

    @Test
    @DisplayName("each library type the table names refuses a null value")
    void libraryTypesRefuseNull() {
      assertThatThrownBy(() -> Either.right(null)).isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> Instances.<EitherKind.Witness<String>>monad(either()).of(null))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> Try.success(null)).isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> Validated.valid(null)).isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> EitherOrBoth.right(null)).isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> NonEmptyList.of((String) null))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> IOMonad.INSTANCE.of(null)).isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> VTask.succeed(null)).isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> Lazy.now(null)).isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> Reader.constant(null)).isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> State.pure(null)).isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> Writer.value(Monoids.string(), null))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> Free.pure(null)).isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> Trampoline.done(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName(
        "a map function that returns null: Either throws, Try fails, IO fails when it runs")
    void libraryTypesRefuseANullResult() {
      assertThatThrownBy(() -> Either.right(1).map(n -> null))
          .isInstanceOf(NullPointerException.class);
      assertThat(Try.success(1).map(n -> null).isFailure()).isTrue();
      // the page claims only that Validated fails, not which exception it throws
      assertThatThrownBy(() -> Validated.valid(1).map(n -> null))
          .isInstanceOf(RuntimeException.class);

      IO<Object> io = IO.delay(() -> 1).map(n -> null);
      assertThatThrownBy(io::unsafeRunSync).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Maybe reads null as Nothing, except Maybe.just, which refuses it")
    void maybeReadsNullAsNothing() {
      assertThat(Maybe.fromNullable(null)).isEqualTo(Maybe.nothing());
      assertThat(MAYBE.narrow(Instances.monad(maybe()).of(null))).isEqualTo(Maybe.nothing());
      assertThat(Maybe.just(1).map(n -> null)).isEqualTo(Maybe.nothing());
      assertThatThrownBy(() -> Maybe.just(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Id holds a null value, and a null map result")
    void idHoldsNull() {
      assertThat(Id.of(null).value()).isNull();
      assertThat(ID.narrow(Instances.monad(id()).of(null)).value()).isNull();
      assertThat(Id.of(1).map(n -> null).value()).isNull();
    }
  }

  @Nested
  @DisplayName("where null is read or held")
  class WhereNullIsReadOrHeld {

    @Test
    @DisplayName("the JDK containers' instances read null as empty, or complete with it")
    void jdkContainersReadNullAsEmpty() throws Exception {
      assertThat(OPTIONAL.narrow(Instances.monad(optional()).of(null))).isEmpty();
      assertThat(LIST.narrow(Instances.monad(list()).of(null))).isEmpty();
      assertThat(STREAM.narrow(Instances.monad(stream()).of(null))).isEmpty();
      assertThat(FUTURE.narrow(Instances.monad(completableFuture()).of(null)).get()).isNull();
    }

    @Test
    @DisplayName("MaybeT and OptionalT read null as Maybe and Optional do")
    void transformersFollowTheirInnerType() {
      Maybe<Object> maybe =
          ID.narrow(MAYBE_T.narrow(new MaybeTMonad<>(IdMonad.instance()).of(null)).value()).value();
      Optional<Object> optional =
          ID.narrow(OPTIONAL_T.narrow(new OptionalTMonad<>(IdMonad.instance()).of(null)).value())
              .value();

      assertThat(maybe).isEqualTo(Maybe.nothing());
      assertThat(optional).isEmpty();
    }

    @Test
    @DisplayName("a tuple holds whatever it is given, and Const ignores the value")
    void tuplesHoldAndConstIgnores() {
      Tuple2<Object, Object> pair = Tuple.of(null, null);

      assertThat(pair._1()).isNull();
      assertThat(pair._2()).isNull();
      assertThat(CONST.narrow(new ConstApplicative<>(Monoids.string()).of(null)).value())
          .isEqualTo("");
    }

    @Test
    @DisplayName("a flatMap function that returns null throws KindUnwrapException, in every type")
    void flatMapToNullThrows() {
      assertThatThrownBy(() -> Maybe.just(1).flatMap(n -> null))
          .isInstanceOf(KindUnwrapException.class);
      assertThatThrownBy(() -> Id.of(1).flatMap(n -> null)).isInstanceOf(KindUnwrapException.class);
      assertThatThrownBy(() -> Either.right(1).flatMap(n -> null))
          .isInstanceOf(KindUnwrapException.class);
      assertThatThrownBy(() -> Instances.monad(maybe()).flatMap(n -> null, MAYBE.just(1)))
          .isInstanceOf(KindUnwrapException.class);
    }
  }

  @Nested
  @DisplayName("Unit")
  class UnitValue {

    @Test
    @DisplayName("Unit has exactly one value")
    void exactlyOneValue() {
      assertThat(Unit.values()).containsExactly(Unit.INSTANCE);
    }

    @Test
    @DisplayName("an action run for its effect gives Unit.INSTANCE")
    void actionGivesUnit() {
      assertThat(IO.fromRunnable(() -> {}).unsafeRunSync()).isSameAs(Unit.INSTANCE);
    }

    @Test
    @DisplayName("Maybe's error type is Unit, and raising it gives Nothing")
    void maybeRaisesUnit() {
      assertThat(
              MAYBE.narrow(
                  Instances.<MaybeKind.Witness, Unit>monadError(maybe())
                      .<String>raiseError(Unit.INSTANCE)))
          .isEqualTo(Maybe.nothing());
    }
  }
}
