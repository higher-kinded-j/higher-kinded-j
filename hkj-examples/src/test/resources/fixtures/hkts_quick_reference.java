// Fixture for hkj-book/src/hkts/quick_reference.md
//
// The page is a table of one-liners, one per type class, so every entry elides the instance and the
// domain it is acting on. Both are supplied here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.func.FunctionKindHelper.FUNCTION;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Profunctor;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.func.FunctionKind;
import org.higherkindedj.hkt.func.FunctionProfunctor;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOSelective;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;

record User(String id, String username, String password) {

  User(String username, String password) {
    this("u-1", username, password);
  }
}

record LoginInput(String username, String password) {}

record Profile(String accountId) {}

record Account(String id) {}

record Order(String id) {}

record MyType(String value) {

  static MyType defaultValue() {
    return new MyType("");
  }

  MyType mergeWith(MyType other) {
    return new MyType(value + other.value());
  }
}

interface Config {
  boolean isDebug();
}

interface Logger {
  void debug(String message);
}

class Fixture {

  static final Functor<OptionalKind.Witness> optionalFunctor = Instances.functor(optional());

  static final Kind<OptionalKind.Witness, String> optionalString = sample();

  static final Applicative<ValidatedKind.Witness<List<String>>> applicative =
      Instances.validated(Semigroups.list());

  static final Applicative<ValidatedKind.Witness<List<String>>> validatedApplicative = applicative;

  static final MonadError<OptionalKind.Witness, Unit> monad = Instances.monadError(optional());

  static final MonadError<EitherKind.Witness<String>, String> monadError =
      Instances.monadError(either());

  static final Kind<EitherKind.Witness<String>, Double> divideOperation = sample();

  static final Foldable<ListKind.Witness> listFoldable = ListTraverse.INSTANCE;

  static final Foldable<ListKind.Witness> foldable = listFoldable;

  static final Traverse<ListKind.Witness> listTraverse = ListTraverse.INSTANCE;

  static final Profunctor<FunctionKind.Witness> profunctor = FunctionProfunctor.INSTANCE;

  static final Config config = sample();

  static final Logger log = sample();

  static final LoginInput input = new LoginInput("ada", "hunter2");

  static final String userId = "u-1";

  static final String productId = "p-1";

  static final Kind<ListKind.Witness, Integer> numbersList = LIST.widen(List.of(1, 2, 3, 4, 5));

  static final Optional<String> optional = Optional.of("test");

  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static Kind<ValidatedKind.Witness<List<String>>, String> validateUsername(String username) {
    return sample();
  }

  static Kind<ValidatedKind.Witness<List<String>>, String> validatePassword(String password) {
    return sample();
  }

  static Kind<OptionalKind.Witness, User> findUser(String id) {
    return sample();
  }

  static Kind<OptionalKind.Witness, Profile> findProfile(String id) {
    return sample();
  }

  static Kind<OptionalKind.Witness, Account> findAccount(String accountId) {
    return sample();
  }

  static Kind<OptionalKind.Witness, String> findProduct(String productId) {
    return sample();
  }

  static Kind<OptionalKind.Witness, Order> validateAndCreateOrder(User user, String product) {
    return sample();
  }

  Kind<ValidatedKind.Witness<List<String>>, Integer> parseInteger(String raw) {
    return sample();
  }
}
