// Fixture for hkj-book/src/transformers/transformer_axes.md
//
// The page names four axes and shows one snippet per axis. The domain each axis moves through is
// declared here; `Customer`, `Address` and `AppEnv` carry `@GenerateFocus` so the zoom and magnify
// snippets can name the FocusPaths the processor emits.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.instances.Witnesses.io;
import static org.higherkindedj.hkt.instances.Witnesses.vtask;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.vtask.VTaskKindHelper.VTASK;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.ReaderPath;
import org.higherkindedj.hkt.expression.ForState;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.state_t.StateT;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.hkt.vtask.VTaskKind;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.focus.FocusPath;

record User(String id, String name) {}

record UserId(String value) {}

record Result(String value) {}

record AppState(int step) {}

record DbConfig(String url) {}

record AuthConfig(String realm) {}

@GenerateFocus
record Address(String street, String city, String zip) {}

@GenerateFocus
record Customer(String name, Address address, int loyaltyPoints) {}

@GenerateFocus
record AppEnv(DbConfig db, AuthConfig auth, String tenant) {}

final class UserRepo {

  ReaderPath<AppEnv, User> loadUser(String id) {
    return Path.asks(env -> new User(id, "Alice"));
  }
}

class Fixture {

  static final String id = "user-1";

  static final UserRepo userRepo = new UserRepo();

  static final Monad<IdKind.Witness> idMonad = Instances.monad(id());

  static final Monad<VTaskKind.Witness> vtaskMonad = Instances.monad(vtask());

  static final Customer customer =
      new Customer("Alice", new Address("123 Elm St", "London", "SW1A-1AA"), 100);

  static final AppEnv currentRequestEnv =
      new AppEnv(new DbConfig("jdbc:postgresql://db"), new AuthConfig("main"), "tenant-1");

  static final ReaderPath<DbConfig, User> loadUser = Path.asks(cfg -> new User("user-1", "Alice"));

  static final StateT<AppState, IOKind.Witness, Result> ioWorkflow =
      StateT.create(
          s ->
              IO_OP.widen(
                  org.higherkindedj.hkt.io.IO.delay(
                      () -> StateTuple.of(new AppState(s.step() + 1), new Result("done")))),
          Instances.monad(io()));

  static final Natural<IOKind.Witness, VTaskKind.Witness> ioToVTask =
      new Natural<>() {
        @Override
        public <A> Kind<VTaskKind.Witness, A> apply(Kind<IOKind.Witness, A> fa) {
          return VTASK.widen(VTask.delay(() -> IO_OP.narrow(fa).unsafeRunSync()));
        }
      };

  static final Lens<Address, String> streetLens =
      Lens.of(Address::street, (a, street) -> new Address(street, a.city(), a.zip()));

  static final Lens<Address, String> zipLens =
      Lens.of(Address::zip, (a, zip) -> new Address(a.street(), a.city(), zip));

  static final Lens<Customer, Integer> loyaltyLens =
      Lens.of(
          Customer::loyaltyPoints,
          (c, points) -> new Customer(c.name(), c.address(), points));
}
