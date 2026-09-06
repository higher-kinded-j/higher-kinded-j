// Fixture for hkj-book/src/glossary/type-classes.md
//
// The glossary defines a type class and then shows an instance of it at work. Several entries are
// about a shape rather than a type - a DSL's own witness, a validation error channel - so the
// fixture is generic in those, and the wrapper the gate builds inherits the parameters.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.either_t.EitherTKindHelper.EITHER_T;
import static org.higherkindedj.hkt.func.FunctionKindHelper.FUNCTION;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.Profunctor;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.Update;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.coyoneda.Coyoneda;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherBifunctor;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either.EitherKind2;
import org.higherkindedj.hkt.either_t.EitherT;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.free_ap.FreeAp;
import org.higherkindedj.hkt.func.FunctionProfunctor;
import org.higherkindedj.hkt.func.FunctionKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOSelective;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;

/** The page's own error, which shadows `java.lang.Error`. */
record Error(String message) {}

record User(String id, String name) {}

record Post(String id) {}

record UserProfile(User user, List<Post> posts) {}

record Profile(String accountId) {}

record Account(String id) {}

record Order(String email, BigDecimal total) {

  Order withEmail(String email) {
    return new Order(email, total);
  }

  Order withTotal(BigDecimal total) {
    return new Order(email, total);
  }
}

interface Config {
  boolean isDebug();
}

interface Logger {
  void debug(String message);
}

/**
 * Generic in the shapes the page talks about rather than names: {@code E} is a validation error
 * channel, {@code MyDSL} and {@code DbOp} are a reader's own algebra witnesses.
 */
class Fixture<
    E,
    MyDSL extends WitnessArity<TypeArity.Unary>,
    DbOp extends WitnessArity<TypeArity.Unary>> {

  static final BigDecimal DISCOUNT = new BigDecimal("0.9");

  static final Config config = sample();

  static final Logger log = sample();

  static final String userId = "u-1";

  static final String id = "u-1";

  static final Order order = new Order("A@B.test", BigDecimal.TEN);

  static final Monad<IOKind.Witness> ioMonad = Instances.monad(org.higherkindedj.hkt.instances.Witnesses.io());

  static final Functor<ListKind.Witness> listFunctor = Instances.functor(list());

  static final Kind<ListKind.Witness, Integer> list = LIST.widen(List.of(1, 2, 3));

  static final List<Integer> numbers = List.of(1, 2, 3);

  static final MonadError<CompletableFutureKind.Witness, Throwable> cfMonad =
      Instances.monadError(org.higherkindedj.hkt.instances.Witnesses.completableFuture());

  static final Kind<EitherKind.Witness<String>, Double> divideOperation = sample();

  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  Kind<MyDSL, Integer> myInstruction = sample();

  Functor<MyDSL> myDslFunctor = sample();

  Kind<DbOp, User> getUser = sample();

  Kind<DbOp, List<Post>> getPosts = sample();

  static Kind<OptionalKind.Witness, Profile> findProfile(String userId) {
    return sample();
  }

  static Kind<OptionalKind.Witness, String> findAccount(String accountId) {
    return sample();
  }

  static Kind<OptionalKind.Witness, String> findUser(String id) {
    return sample();
  }

  static Kind<OptionalKind.Witness, String> findProduct(String id) {
    return sample();
  }

  static Kind<OptionalKind.Witness, Order> validateAndCreateOrder(String user, String product) {
    return sample();
  }

  static CompletableFuture<Either<Error, User>> fetchUser(String id) {
    return sample();
  }

  static CompletableFuture<Either<Error, Profile>> fetchProfile(User user) {
    return sample();
  }
}
