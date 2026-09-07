// Fixture for hkj-book/src/monads/free_applicative.md
//
// The page fetches a user, their posts and their notifications, first sequentially through Free
// and then independently through FreeAp, and finally validates a signup form. Both DSLs and their
// HKT bridges are declared here so a snippet showing one operation still has the rest around it; a
// snippet that declares its own copy shadows this one.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.constant.ConstKindHelper.CONST;
import static org.higherkindedj.hkt.free_ap.FreeApKindHelper.FREE_AP;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;
import static org.higherkindedj.hkt.instances.Witnesses.io;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.constant.Const;
import org.higherkindedj.hkt.constant.ConstApplicative;
import org.higherkindedj.hkt.constant.ConstKind;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.free_ap.FreeAp;
import org.higherkindedj.hkt.free_ap.FreeApApplicative;
import org.higherkindedj.hkt.free_ap.FreeApKind;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;

record User(int id, String name, String email) {}

record Post(int id, String title) {}

record Notification(int id, String message) {}

record UserProfile(User user, List<Post> posts) {}

record Dashboard(User user, List<Post> posts, List<Notification> notifications) {}

record Pair<A, B>(A first, B second) {}

record ValidatedUser(String name, String email, int age) {}

sealed interface DbOp<A> {
  record GetUser(int id) implements DbOp<User> {}

  record GetPosts(int userId) implements DbOp<List<Post>> {}

  record GetNotifications(int userId) implements DbOp<List<Notification>> {}
}

interface DbOpKind<A> extends Kind<DbOpKind.Witness, A> {
  final class Witness implements WitnessArity<TypeArity.Unary> {
    private Witness() {}
  }
}

enum DbOpKindHelper {
  DB_OP;

  record Holder<A>(DbOp<A> op) implements DbOpKind<A> {}

  public <A> Kind<DbOpKind.Witness, A> widen(DbOp<A> op) {
    return new Holder<>(op);
  }

  @SuppressWarnings("unchecked") // the holder is the only implementation
  public <A> DbOp<A> narrow(Kind<DbOpKind.Witness, A> kind) {
    return ((Holder<A>) kind).op();
  }
}

/** The Free monad half of the comparison needs a Functor; the FreeAp half does not. */
class DbOpFunctor implements Functor<DbOpKind.Witness> {
  @Override
  @SuppressWarnings("unchecked") // simplified DSL: map returns the operation unchanged
  public <A, B> Kind<DbOpKind.Witness, B> map(
      Function<? super A, ? extends B> f, Kind<DbOpKind.Witness, A> fa) {
    return (Kind<DbOpKind.Witness, B>) fa;
  }
}

sealed interface ValidationOp<A> {
  record ValidateEmail(String email) implements ValidationOp<String> {}

  record ValidateAge(int age) implements ValidationOp<Integer> {}

  record ValidateName(String name) implements ValidationOp<String> {}
}

interface ValidationOpKind<A> extends Kind<ValidationOpKind.Witness, A> {
  final class Witness implements WitnessArity<TypeArity.Unary> {
    private Witness() {}
  }
}

enum ValidationOpKindHelper {
  VALIDATION_OP;

  record Holder<A>(ValidationOp<A> op) implements ValidationOpKind<A> {}

  public <A> Kind<ValidationOpKind.Witness, A> widen(ValidationOp<A> op) {
    return new Holder<>(op);
  }

  @SuppressWarnings("unchecked") // the holder is the only implementation
  public <A> ValidationOp<A> narrow(Kind<ValidationOpKind.Witness, A> kind) {
    return ((Holder<A>) kind).op();
  }
}

final class Database {

  User findUser(int id) {
    return new User(id, "Alice", "alice@example.com");
  }

  List<Post> findPosts(int userId) {
    return List.of(new Post(1, "Hello World"));
  }

  List<Notification> findNotifications(int userId) {
    return List.of(new Notification(1, "Welcome"));
  }
}

class Fixture {

  static final int userId = 1;

  static final Database database = new Database();

  static final DbOpKindHelper DB_OP = DbOpKindHelper.DB_OP;

  static final ValidationOpKindHelper VALIDATION_OP = ValidationOpKindHelper.VALIDATION_OP;

  static final DbOpFunctor dbOpFunctor = new DbOpFunctor();

  static final Applicative<IOKind.Witness> ioApplicative = Instances.monad(io());

  static final Applicative<CompletableFutureKind.Witness> cfApplicative =
      Instances.monadError(completableFuture());

  static final Applicative<ConstKind.Witness<Integer>> constApplicative =
      new ConstApplicative<>(Monoids.integerAddition());

  static final Applicative<ValidatedKind.Witness<List<String>>> validatedApplicative =
      Instances.validated(Semigroups.list());

  static final FreeAp<DbOpKind.Witness, User> userFetch =
      FreeAp.lift(DbOpKindHelper.DB_OP.widen(new DbOp.GetUser(1)));

  static final FreeAp<DbOpKind.Witness, List<Post>> postsFetch =
      FreeAp.lift(DbOpKindHelper.DB_OP.widen(new DbOp.GetPosts(1)));

  static Free<DbOpKind.Witness, User> getUser(int id) {
    return Free.liftF(DbOpKindHelper.DB_OP.widen(new DbOp.GetUser(id)), new DbOpFunctor());
  }

  static Free<DbOpKind.Witness, List<Post>> getPosts(int id) {
    return Free.liftF(DbOpKindHelper.DB_OP.widen(new DbOp.GetPosts(id)), new DbOpFunctor());
  }

  static FreeAp<DbOpKind.Witness, Dashboard> dashboardProgram(int id) {
    FreeApApplicative<DbOpKind.Witness> applicative = FreeApApplicative.instance();
    return FREE_AP.narrow(
        applicative.map3(
            FREE_AP.widen(FreeAp.lift(DbOpKindHelper.DB_OP.widen(new DbOp.GetUser(id)))),
            FREE_AP.widen(FreeAp.lift(DbOpKindHelper.DB_OP.widen(new DbOp.GetPosts(id)))),
            FREE_AP.widen(
                FreeAp.lift(DbOpKindHelper.DB_OP.widen(new DbOp.GetNotifications(id)))),
            Dashboard::new));
  }

  // Not static: a snippet showing this method declares an instance method, which cannot hide a
  // static one.
  FreeAp<ValidationOpKind.Witness, ValidatedUser> validateUser(
      String name, String email, int age) {
    return FreeAp.lift(ValidationOpKindHelper.VALIDATION_OP.widen(new ValidationOp.ValidateName(name)))
        .map2(
            FreeAp.lift(
                    ValidationOpKindHelper.VALIDATION_OP.widen(
                        new ValidationOp.ValidateEmail(email)))
                .map2(
                    FreeAp.lift(
                        ValidationOpKindHelper.VALIDATION_OP.widen(
                            new ValidationOp.ValidateAge(age))),
                    (e, a) -> new Pair<>(e, a)),
            (n, pair) -> new ValidatedUser(n, pair.first(), pair.second()));
  }
}
