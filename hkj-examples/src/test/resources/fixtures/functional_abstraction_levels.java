// Fixture for hkj-book/src/functional/abstraction_levels.md
//
// The page contrasts Applicative, Selective and Monad over one dashboard fetch. The DbOp algebra
// it builds programs from, its HKT bridge, and the values each level composes are declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.io;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.free_ap.FreeAp;
import org.higherkindedj.hkt.free_ap.FreeApAnalyzer;
import org.higherkindedj.hkt.free_ap.SelectiveAnalyzer;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOSelective;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;

record User(String id, String role, boolean isAdmin) {}

record Post(String title) {}

record UserProfile(User user, List<Post> posts) {}

record Dashboard(User user, List<Post> posts) {}

record Config(String name) {}

record Result(String value) {}

sealed interface DbOp<A> {
  record GetUser(String id) implements DbOp<User> {}

  record GetPosts(String userId) implements DbOp<List<Post>> {}

  record DeleteOp(String id) implements DbOp<Unit> {}
}

interface DbOpKind<A> extends Kind<DbOpKind.Witness, A> {
  final class Witness implements WitnessArity<TypeArity.Unary> {
    private Witness() {}
  }
}

enum DbOpHelper {
  DB_OP;

  record Holder<A>(DbOp<A> op) implements DbOpKind<A> {}

  public <A> Kind<DbOpKind.Witness, A> widen(DbOp<A> op) {
    return new Holder<>(op);
  }

  public DbOp<?> narrow(Kind<DbOpKind.Witness, ?> kind) {
    return ((Holder<?>) kind).op();
  }
}

class Fixture {

  static final String userId = "u-1";

  static final String id = "u-1";

  static final String name = "Alice";

  static final String email = "alice@example.com";

  static final int age = 36;

  static final Monad<MaybeKind.Witness> monad = Instances.monadError(maybe());

  static final Selective<IOKind.Witness> selective = IOSelective.INSTANCE;

  static final Applicative<IOKind.Witness> ioApplicative = Instances.monad(io());

  static final Applicative<ValidatedKind.Witness<List<String>>> applicative =
      Instances.validated(Semigroups.list());

  static final Kind<IOKind.Witness, Boolean> isProd =
      IO_OP.widen(IO.delay(() -> true));

  static final Kind<IOKind.Witness, Config> prodConfig =
      IO_OP.widen(IO.delay(() -> new Config("prod")));

  static final Kind<IOKind.Witness, Config> devConfig =
      IO_OP.widen(IO.delay(() -> new Config("dev")));

  static final FreeAp<DbOpKind.Witness, User> userFetch =
      FreeAp.lift(DbOpHelper.DB_OP.widen(new DbOp.GetUser("u-1")));

  static final FreeAp<DbOpKind.Witness, List<Post>> postsFetch =
      FreeAp.lift(DbOpHelper.DB_OP.widen(new DbOp.GetPosts("u-1")));

  static FreeAp<DbOpKind.Witness, User> fetchUser(String id) {
    return userFetch;
  }

  static FreeAp<DbOpKind.Witness, List<Post>> fetchPosts(String id) {
    return postsFetch;
  }

  static FreeAp<DbOpKind.Witness, Dashboard> buildDashboard(String userId) {
    return fetchUser(userId).map2(fetchPosts(userId), Dashboard::new);
  }

  static final FreeAp<DbOpKind.Witness, Dashboard> program = buildDashboard("u-1");

  static Kind<MaybeKind.Witness, User> getUser(String userId) {
    return MAYBE.just(new User("u-1", "ADMIN", true));
  }

  static Kind<MaybeKind.Witness, Result> fetchAdminDashboard(String userId) {
    return MAYBE.just(new Result("admin"));
  }

  static Kind<MaybeKind.Witness, Result> fetchUserDashboard(String userId) {
    return MAYBE.just(new Result("user"));
  }

  static Kind<MaybeKind.Witness, Result> fetchManagerDashboard(String userId) {
    return MAYBE.just(new Result("manager"));
  }

  static Kind<IOKind.Witness, Boolean> featureFlagEnabled(String flag) {
    return IO_OP.widen(IO.delay(() -> true));
  }

  static Kind<IOKind.Witness, Unit> trackEvent(String eventName) {
    return IO_OP.widen(IO.fromRunnable(() -> {}));
  }

  static Validated<List<String>, String> validateName(String name) {
    return Validated.valid(name);
  }

  static Validated<List<String>, String> validateEmail(String email) {
    return Validated.valid(email);
  }

  static Validated<List<String>, Integer> validateAge(int age) {
    return Validated.valid(age);
  }

  static boolean promptUser(String question) {
    return true;
  }
}
