// Fixture for hkj-book/src/glossary/effect-paths.md
//
// `ConsoleOp`, `DbOp` and the `@ComposeEffects` record are REAL declarations here, so the processor
// generates ConsoleOpKind / ConsoleOpOps / ConsoleOpInterpreter and AppEffectsSupport, and the
// page's snippets name the genuine article. A snippet that declares one of them for itself shadows
// this copy, and the processor generates from that.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.io;

import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.Function;
import org.higherkindedj.example.payment.effect.FraudCheckOp;
import org.higherkindedj.example.payment.effect.LedgerOp;
import org.higherkindedj.example.payment.effect.NotificationOp;
import org.higherkindedj.example.payment.effect.PaymentGatewayOp;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.hkt.effect.annotation.ComposeEffects;
import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.eitherf.EitherFKind;
import org.higherkindedj.hkt.eitherf.Interpreters;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOKindHelper;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.annotations.GenerateFocus;

record Error(String message) {}

record UserError(String message) {}

@GenerateFocus
record Address(String city, String postcode) {}

@GenerateFocus
record User(String id, String name, Address address) {}

record Config(String name) {}

record Metrics(String value) {}

record Report(List<Metrics> metrics) {}

record Department(String id) {}

@GenerateFocus
record Company(String name, List<Department> departments) {}

record Data(String body) {}

interface UserService {
  EitherPath<Error, User> findById(String id);
}

/** The reader's own algebras, declared for real so the processor generates their support. */
@EffectAlgebra
sealed interface ConsoleOp<A> permits ConsoleOp.ReadLine, ConsoleOp.PrintLine {

  <B> ConsoleOp<B> mapK(Function<? super A, ? extends B> f);

  record ReadLine<A>(Function<String, A> k) implements ConsoleOp<A> {
    @Override
    public <B> ConsoleOp<B> mapK(Function<? super A, ? extends B> f) {
      return new ReadLine<>(k.andThen(f));
    }
  }

  record PrintLine<A>(String message, Function<Unit, A> k) implements ConsoleOp<A> {
    @Override
    public <B> ConsoleOp<B> mapK(Function<? super A, ? extends B> f) {
      return new PrintLine<>(message, k.andThen(f));
    }
  }
}

@EffectAlgebra
sealed interface DbOp<A> permits DbOp.Save {

  <B> DbOp<B> mapK(Function<? super A, ? extends B> f);

  record Save<A>(String value, Function<String, A> k) implements DbOp<A> {
    @Override
    public <B> DbOp<B> mapK(Function<? super A, ? extends B> f) {
      return new Save<>(value, k.andThen(f));
    }
  }
}

@ComposeEffects
record AppEffects(Class<ConsoleOp<?>> console, Class<DbOp<?>> db) {}

class Fixture {

  static final Scanner scanner = new Scanner(System.in);

  static final UserService userService = sample();

  static final String userId = "u-1";

  static final String id = "u-1";

  static final String input = "{}";

  // The composed console+db witness, spelled out because Java has no alias for it.
  static final Free<
          EitherFKind.Witness<ConsoleOpKind.Witness, DbOpKind.Witness>, String>
      program = sample();

  static final ConsoleOpOps.Bound<
          EitherFKind.Witness<ConsoleOpKind.Witness, DbOpKind.Witness>>
      console = sample();

  static final String path = "/tmp/data";

  static final Natural<ConsoleOpKind.Witness, IOKind.Witness> consoleInterp = sample();

  static final Natural<DbOpKind.Witness, IOKind.Witness> dbInterp = sample();

  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static Either<Error, User> findUser(String id) {
    return Either.left(new Error("not found"));
  }

  static Maybe<Config> loadConfig() {
    return Maybe.just(new Config("app"));
  }

  static Data parseJson(String input) {
    return new Data(input);
  }

  static String readFile(String path) {
    return "";
  }

  static EitherPath<Error, String> validateName(String name) {
    return Path.right(name);
  }

  static Either<Error, Company> loadCompany(String id) {
    return Either.left(new Error("not found"));
  }

  static EitherPath<Error, List<Metrics>> loadMetrics(List<Department> departments) {
    return Path.left(new Error("not found"));
  }

  static Report generateReport(List<Metrics> metrics) {
    return new Report(metrics);
  }
}
