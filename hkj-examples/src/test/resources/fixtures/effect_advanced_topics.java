// Fixture for hkj-book/src/effect/advanced_topics.md
//
// The page covers trampolining, Free and FreeAp DSLs, resource management, parallelism and
// retry. The two DSLs are real here rather than sketches: their witnesses were named without the
// algebras ever being `Kind`s, which is what let them drift.
//
// `bracket` takes a plain Supplier, Function and Consumer, so the JDK's checked file exceptions
// are wrapped by the small helpers below. The page says so where it uses them; the full
// explanation is on path_io.md.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.io;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.CompletableFuturePath;
import org.higherkindedj.hkt.effect.FreeApPath;
import org.higherkindedj.hkt.effect.FreePath;
import org.higherkindedj.hkt.effect.GenericPath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.LazyPath;
import org.higherkindedj.hkt.effect.NaturalTransformation;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.PathOps;
import org.higherkindedj.hkt.effect.TrampolinePath;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOKindHelper;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.resilience.RetryExhaustedException;
import org.higherkindedj.hkt.resilience.RetryPolicy;

// ---- the console DSL ---------------------------------------------------------------------------

sealed interface ConsoleOp<A> extends Kind<ConsoleOp.Witness, A> permits PrintLine, ReadLine {
  interface Witness extends WitnessArity<TypeArity.Unary> {}
}

record PrintLine<A>(String message, A next) implements ConsoleOp<A> {}

record ReadLine<A>(Function<String, A> cont) implements ConsoleOp<A> {}

// ---- the validation DSL ------------------------------------------------------------------------

sealed interface ValidationOp<A> extends Kind<ValidationOp.Witness, A> permits ValidateField {
  interface Witness extends WitnessArity<TypeArity.Unary> {}
}

record ValidateField<A>(String field, Predicate<String> check, A onSuccess)
    implements ValidationOp<A> {}

record User(String name, String email, int age) {}

// ---- the domain the resource and parallel sections move around ----------------------------------

record Config(String name) {}

record Data(String value) {

  static Data empty() {
    return new Data("");
  }
}

record Result(String value) {}

record Response(int status) {}

record Metrics(int count) {}

record Alerts(int count) {}

record Dashboard(Metrics metrics, Alerts alerts, List<User> users) {}

record Sales(long pence) {}

record Inventory(int count) {}

record Customers(int count) {}

record Trends(String summary) {}

record Report(Sales sales, Inventory inventory, Customers customers, Trends trends) {}

record Row(String value) {}

final class HttpException extends RuntimeException {

  private final int statusCode;

  HttpException(int statusCode) {
    super("HTTP " + statusCode);
    this.statusCode = statusCode;
  }

  int statusCode() {
    return statusCode;
  }
}

final class Logger {

  void info(String format, Object... arguments) {}

  void warn(String format, Object... arguments) {}

  void error(String format, Object... arguments) {}
}

final class HttpClient {

  Response get(String url) {
    return new Response(200);
  }
}

final class Source {

  Data fetch() {
    return new Data("source");
  }
}

final class UserService {

  User fetch(String id) {
    return new User(id, id + "@example.test", 36);
  }
}

final class ResultSet implements AutoCloseable {

  Row next() {
    return new Row("row");
  }

  public void close() {}
}

final class Statement implements AutoCloseable {

  ResultSet executeQuery() {
    return new ResultSet();
  }

  public void close() {}
}

final class Connection implements AutoCloseable {

  Statement prepareStatement(String sql) {
    return new Statement();
  }

  Result execute(String query) {
    return new Result(query);
  }

  public void close() {}
}

class Fixture {

  static final Scanner scanner = new Scanner(System.in);

  static final Monad<IOKind.Witness> ioMonad = Instances.monad(io());

  static final TrampolinePath<Integer> computation = TrampolinePath.done(42);

  static final File path = new File("data.txt");

  static final String sql = "select 1";

  static final String query = "select 1";

  static final String url = "https://example.test/api";

  static final Logger log = new Logger();

  static final HttpClient httpClient = new HttpClient();

  static final Source primarySource = new Source();

  static final Source backupSource = new Source();

  static final UserService userService = new UserService();

  static final Data fallbackValue = Data.empty();

  static final List<String> userIds = List.of("u-1", "u-2");

  static final List<CompletableFuturePath<Data>> futures =
      List.of(Path.futureCompleted(new Data("a")));

  // ---- the DSLs ---------------------------------------------------------------------------------

  static final Functor<ConsoleOp.Witness> consoleFunctor =
      new Functor<>() {
        @Override
        public <A, B> Kind<ConsoleOp.Witness, B> map(
            Function<? super A, ? extends B> f, Kind<ConsoleOp.Witness, A> fa) {
          return switch ((ConsoleOp<A>) fa) {
            case PrintLine<A>(String message, A next) -> new PrintLine<B>(message, f.apply(next));
            case ReadLine<A>(Function<String, A> cont) ->
                new ReadLine<B>(s -> f.apply(cont.apply(s)));
          };
        }
      };

  static final Functor<ValidationOp.Witness> valFunctor =
      new Functor<>() {
        @Override
        public <A, B> Kind<ValidationOp.Witness, B> map(
            Function<? super A, ? extends B> f, Kind<ValidationOp.Witness, A> fa) {
          return switch ((ValidationOp<A>) fa) {
            case ValidateField<A>(String field, Predicate<String> check, A onSuccess) ->
                new ValidateField<B>(field, check, f.apply(onSuccess));
          };
        }
      };

  // ---- the console program the interpreters run ------------------------------------------------

  FreePath<ConsoleOp.Witness, Unit> print(String msg) {
    return FreePath.liftF(new PrintLine<>(msg, Unit.INSTANCE), consoleFunctor);
  }

  FreePath<ConsoleOp.Witness, String> readLine() {
    return FreePath.liftF(new ReadLine<>(s -> s), consoleFunctor);
  }

  final FreePath<ConsoleOp.Witness, String> greet =
      print("What's your name?").then(this::readLine);

  // ---- resource helpers, which absorb the JDK's checked exceptions --------------------------------

  static InputStream openStream(String name) {
    return new ByteArrayInputStream(new byte[0]);
  }

  static String readAll(InputStream stream) {
    try {
      return new String(stream.readAllBytes());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static java.io.BufferedReader openReader(File file) {
    return new java.io.BufferedReader(new StringReader(""));
  }

  static void closeQuietly(AutoCloseable closeable) {
    try {
      closeable.close();
    } catch (Exception e) {
      // Nothing useful to do while unwinding.
    }
  }

  static Config parseConfig(InputStream stream) {
    return new Config("parsed");
  }

  static Connection acquireConnection() {
    return new Connection();
  }

  static List<Row> processResults(ResultSet resultSet) {
    return List.of(resultSet.next());
  }

  static void cleanup() {}

  static IOPath<Result> fetchData() {
    return Path.io(() -> new Result("data"));
  }

  static IOPath<Result> process() {
    return Path.io(() -> new Result("processed"));
  }

  // ---- parallel composition -------------------------------------------------------------------

  static IOPath<Metrics> fetchMetrics() {
    return Path.io(() -> new Metrics(1));
  }

  static IOPath<Alerts> fetchAlerts() {
    return Path.io(() -> new Alerts(0));
  }

  static IOPath<List<User>> fetchUsers() {
    return Path.io(List::of);
  }

  static IOPath<Sales> fetchSales() {
    return Path.io(() -> new Sales(0));
  }

  static IOPath<Inventory> fetchInventory() {
    return Path.io(() -> new Inventory(0));
  }

  static IOPath<Customers> fetchCustomers() {
    return Path.io(() -> new Customers(0));
  }

  static IOPath<Trends> fetchTrends() {
    return Path.io(() -> new Trends("flat"));
  }

  static Config fetchFromPrimary() {
    return new Config("primary");
  }

  static Config fetchFromBackup() {
    return new Config("backup");
  }

  static IOPath<Response> fetchFromRegionA() {
    return Path.io(() -> new Response(200));
  }

  static IOPath<Response> fetchFromRegionB() {
    return Path.io(() -> new Response(200));
  }

  static IOPath<Response> fetchFromRegionC() {
    return Path.io(() -> new Response(200));
  }
}
