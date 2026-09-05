// Fixture for hkj-book/src/effect/effect_contexts_optional.md
//
// The OptionalContext page walks a cache/database/legacy fallback chain and a configuration
// lookup, in both the Maybe-flavoured and Optional-flavoured Contexts. The sources behind those
// lookups live here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.util.Optional;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.context.ErrorContext;
import org.higherkindedj.hkt.effect.context.JavaOptionalContext;
import org.higherkindedj.hkt.effect.context.OptionalContext;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.jspecify.annotations.Nullable;

record User(String id, String profileId) {}

record Address(String line) {}

record Profile(String id) {}

record Config(String name) {

  static Config hardcodedDefaults() {
    return new Config("hardcoded");
  }
}

record UserError(String message) {}

final class UserNotFoundException extends RuntimeException {

  UserNotFoundException(String id) {
    super("User not found: " + id);
  }
}

class Fixture {

  static final String userId = "u-1";

  static final User user = new User("u-1", "p-1");

  static final Logger log = new Logger();

  static final Cache cache = new Cache();

  static final Database database = new Database();

  static final LegacySystem legacySystem = new LegacySystem();

  static final Repository repository = new Repository();

  static final Repository userRepo = new Repository();

  static final AddressRepository addressRepo = new AddressRepository();

  static final ConfigLoader configLoader = new ConfigLoader();

  static OptionalContext<IOKind.Witness, User> lookupUser(String id) {
    return OptionalContext.some(new User(id, "p-1"));
  }

  static OptionalContext<IOKind.Witness, Profile> lookupProfile(String profileId) {
    return OptionalContext.some(new Profile(profileId));
  }

  static OptionalContext<IOKind.Witness, Profile> enrichProfile(Profile profile) {
    return OptionalContext.some(profile);
  }

  static OptionalContext<IOKind.Witness, Unit> validateExists() {
    return OptionalContext.some(Unit.INSTANCE);
  }

  static OptionalContext<IOKind.Witness, String> fetchData() {
    return OptionalContext.some("data");
  }

  static OptionalContext<IOKind.Witness, String> processResult() {
    return OptionalContext.some("result");
  }

  static OptionalContext<IOKind.Witness, Config> loadConfig() {
    return OptionalContext.some(new Config("app"));
  }

  static @Nullable Config loadFromEnvironment() {
    return new Config("env");
  }

  static @Nullable Config loadFromFile() {
    return new Config("file");
  }

  static @Nullable Config loadFromDefaults() {
    return new Config("defaults");
  }

  static final class Cache {

    @Nullable User get(String id) {
      return new User(id, "p-1");
    }

    @Nullable Integer getCount() {
      return 1;
    }
  }

  static final class Database {

    @Nullable User find(String id) {
      return new User(id, "p-1");
    }
  }

  static final class LegacySystem {

    @Nullable User lookup(String id) {
      return new User(id, "p-1");
    }
  }

  static final class Repository {

    @Nullable User findById(String id) {
      return new User(id, "p-1");
    }
  }

  static final class AddressRepository {

    @Nullable Address findByUserId(String userId) {
      return new Address("1 Old Street");
    }
  }

  /**
   * Two loaders, not one: the Maybe-flavoured Context and the Optional-flavoured one need
   * different return types, and a single `load` cannot have both.
   */
  static final class ConfigLoader {

    Maybe<Config> loadMaybe(String path) {
      return Maybe.just(new Config(path));
    }

    Optional<Config> loadOptional(String path) {
      return Optional.of(new Config(path));
    }
  }

  /** Stands in for whatever logger the reader has. */
  static final class Logger {

    void info(String format, Object... arguments) {}
  }
}
