// Fixture for hkj-book/src/hkts/hkt_basic_examples.md
//
// The page shows one short example per type, so each elides the monad instance and the value it
// starts from. Both are supplied here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.instances.Witnesses.reader;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.lazy.LazyKindHelper.LAZY;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.reader.ReaderKindHelper.READER;
import static org.higherkindedj.hkt.state.StateKindHelper.STATE;
import static org.higherkindedj.hkt.writer.WriterKindHelper.WRITER;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Profunctor;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.func.FunctionKind;
import org.higherkindedj.hkt.func.FunctionProfunctor;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.lazy.LazyKind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.reader.ReaderKind;
import org.higherkindedj.hkt.state.State;
import org.higherkindedj.hkt.state.StateKind;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.writer.Writer;
import org.higherkindedj.hkt.writer.WriterKind;

record AppConfig(String dbUrl, String apiKey) {}

record AccountState(BigDecimal balance, List<String> history) {

  AccountState withBalance(BigDecimal newBalance) {
    return new AccountState(newBalance, history);
  }
}

class Fixture {

  static final MonadZero<ListKind.Witness> listMonad = Instances.monadZero(list());

  static final MonadError<OptionalKind.Witness, org.higherkindedj.hkt.Unit> optionalMonad =
      Instances.monadError(optional());

  static final MonadError<CompletableFutureKind.Witness, Throwable> futureMonad =
      Instances.monadError(completableFuture());

  static final Monad<ReaderKind.Witness<AppConfig>> readerMonad = Instances.monad(reader());

  static final MonadError<ValidatedKind.Witness<List<String>>, List<String>> validatedMonad =
      Instances.validated(org.higherkindedj.hkt.Semigroups.list());

  static final Monad<WriterKind.Witness<String>> writerMonad =
      Instances.writer(Monoids.string());

  static final Profunctor<FunctionKind.Witness> profunctor = FunctionProfunctor.INSTANCE;

  static final Kind2<FunctionKind.Witness, String, Integer> lengthFunction =
      org.higherkindedj.hkt.func.FunctionKindHelper.FUNCTION.widen(String::length);

  static final Kind<ListKind.Witness, Integer> list1 = LIST.widen(List.of(1, 2, 3));

  static final Kind<ListKind.Witness, Integer> list2 = LIST.widen(List.of(10, 20));

  static final Kind<ListKind.Witness, Integer> listKind = list1;

  static final Kind<OptionalKind.Witness, Integer> optKind = OPTIONAL.widen(Optional.of(7));

  static final Kind<ListKind.Witness, Integer> numbersKind =
      LIST.widen(List.of(1, 2, 3, 4, 5));

  static final Kind<OptionalKind.Witness, String> presentInput =
      OPTIONAL.widen(Optional.of("42"));

  static final Function<Integer, Integer> doubleFn = x -> x * 2;

  static final Kind<CompletableFutureKind.Witness, String> failedFutureKind = sample();

  static final Kind<ReaderKind.Witness<AppConfig>, String> getDbUrl =
      READER.reader(AppConfig::dbUrl);

  static final AppConfig productionConfig = new AppConfig("jdbc:postgresql://prod", "k");

  static final Function<Integer, Kind<WriterKind.Witness<String>, String>>
      multiplyAndLogToString =
          x -> WRITER.widen(Writer.of("Multiplied " + x + "; ", Integer.toString(x * 2)));

  static final Either<String, String> input = Either.right("42");

  static final Try<Integer> tryInput = Try.success(2);

  static final Function<String, Either<String, Integer>> parse =
      raw -> Either.right(Integer.parseInt(raw));

  static final Function<Integer, Either<String, Integer>> checkPositive =
      value -> value > 0 ? Either.right(value) : Either.left("not positive");

  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}
