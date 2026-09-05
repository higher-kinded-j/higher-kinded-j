// Fixture for hkj-book/src/effect/path_either.md
//
// The page catalogues EitherPath's constructors, combinators, error handling and extractors.
// The domain those examples run on lives here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.either.Either;

record User(String name) {}

record Person(String name, Integer age) {}

record Config(String name) {

  static Config defaults() {
    return new Config("defaults");
  }
}

/** The page's untyped error, and the one variant its constructor example names. */
sealed interface Error permits ValidationError, ApiError {}

record ValidationError(String message) implements Error {}

record ApiError(String message) implements Error {

  static DomainError toDomain(ApiError error) {
    return new DomainError(error.message());
  }
}

record DomainError(String message) {}

record ConfigError(String detail) {}

class Fixture {

  static final String input = "raw";

  static final EitherPath<ApiError, User> apiPath =
      Path.either(Either.right(new User("Ada")));

  static final EitherPath<Error, User> path = Path.either(Either.right(new User("Ada")));

  static Either<Error, User> validateUser(String input) {
    return Either.right(new User("Ada"));
  }

  static Either<String, Config> loadConfig() {
    return Either.right(new Config("file"));
  }

  static Either<String, Config> loadBackupConfig() {
    return Either.right(new Config("backup"));
  }
}
