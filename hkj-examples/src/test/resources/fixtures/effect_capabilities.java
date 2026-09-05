// Fixture for hkj-book/src/effect/capabilities.md
//
// The page quotes each capability interface and then shows a path type using it. The interface
// quotations declare their own types, which shadow the real ones - so this fixture deliberately
// does NOT import `Composable`, `Combinable`, `Chainable` or `Recoverable`.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;

record User(String name, String email, Integer age) {

  static User guest() {
    return new User("guest", "", 0);
  }
}

record UserInput(String name, String email, int age) {}

record Cart(String id) {}

record Total(long pence) {}

record Invoice(String id) {}

record Error(String message) {}

record ConfigError(String message, Error cause) {}

record Config(String name) {

  static Config defaults() {
    return new Config("defaults");
  }
}

/**
 * The capability hierarchy, as the page quotes it. Each quotation declares one interface and
 * extends the one above, so every snippet but the first needs its parents from here; the snippet
 * that declares a given interface shadows the copy below.
 */
interface Composable<A> {

  <B> Composable<B> map(Function<? super A, ? extends B> f);

  Composable<A> peek(Consumer<? super A> action);
}

interface Combinable<A> extends Composable<A> {

  <B, C> Combinable<C> zipWith(
      Combinable<B> other, BiFunction<? super A, ? super B, ? extends C> f);
}

interface Chainable<A> extends Combinable<A> {

  <B> Chainable<B> via(Function<? super A, ? extends Chainable<B>> f);

  <B> Chainable<B> flatMap(Function<? super A, ? extends Chainable<B>> f);

  <B> Chainable<B> then(Supplier<? extends Chainable<B>> next);
}

class Fixture {

  static final String rawAge = "36";


  static final String id = "u-1";

  static final String userId = "u-1";

  static final UserInput input = new UserInput("Ada", "ada@example.com", 36);

  static final Logger log = new Logger();

  static EitherPath<String, String> validateName(String name) {
    return Path.right(name);
  }

  static EitherPath<String, String> validateEmail(String email) {
    return Path.right(email);
  }

  static EitherPath<String, Integer> validateAge(int age) {
    return Path.right(age);
  }

  static Either<Error, User> findUser(String userId) {
    return Either.right(User.guest());
  }

  /** The lookup that answers with absence, as distinct from the one that answers with an error. */
  static Maybe<User> lookupUser(String id) {
    return Maybe.just(User.guest());
  }

  static Either<Error, Cart> getCart(User user) {
    return Either.right(new Cart("c-1"));
  }

  static Either<Error, Total> calculateTotal(Cart cart) {
    return Either.right(new Total(0));
  }

  static Either<Error, Invoice> createInvoice(Total total) {
    return Either.right(new Invoice("i-1"));
  }

  static Either<Error, Config> loadConfig() {
    return Either.right(new Config("app"));
  }

  static Unit initialise() {
    return Unit.INSTANCE;
  }

  static Unit process() {
    return Unit.INSTANCE;
  }

  /** Stands in for whatever logger the reader has. */
  static final class Logger {

    Unit info(String message) {
      return Unit.INSTANCE;
    }
  }
}
