// Fixture for hkj-book/src/monads/vtask_monad.md
//
// The page fetches a user, their profile and a configuration, and races a few servers. The domain
// the snippets elide for readability is declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.vtask;
import static org.higherkindedj.hkt.vtask.VTaskKindHelper.VTASK;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.Par;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.hkt.vtask.VTaskExecutionException;
import org.higherkindedj.hkt.vtask.VTaskKind;

record User(String id, String name) {}

final class Profile {

  private final String displayName;

  Profile(String displayName) {
    this.displayName = displayName;
  }

  String getDisplayName() {
    return displayName;
  }
}

record UserProfile(User user, Profile profile) {}

record Data(String value) {}

record Config(String name) {

  static Config defaultConfig() {
    return new Config("default");
  }
}

final class ConfigException extends RuntimeException {

  ConfigException(String message, Throwable cause) {
    super(message, cause);
  }
}

final class HttpClient {

  String get(String url) throws IOException {
    return "{}";
  }
}

final class UserService {

  User getById(String id) {
    return new User(id, "Alice");
  }
}

final class ProfileService {

  Profile getForUser(User user) {
    return new Profile(user.name());
  }
}

final class ConfigService {

  Config load() {
    return new Config("app");
  }
}

class Fixture {

  static final HttpClient httpClient = new HttpClient();

  static final Path path = Path.of("build.gradle.kts");

  static final String userId = "u-1";

  static final int id = 1;

  static final boolean someCondition = true;

  static final UserService userService = new UserService();

  static final ProfileService profileService = new ProfileService();

  static final ConfigService configService = new ConfigService();

  static final VTask<User> fetchUser = VTask.of(() -> userService.getById("u-1"));

  static Config loadFallbackConfig() {
    return new Config("fallback");
  }

  static Data fetchData() {
    return new Data("payload");
  }

  static User fetchUser(int userId) {
    return new User(String.valueOf(userId), "Alice");
  }

  static Profile fetchProfile(int userId) {
    return new Profile("Alice");
  }

  static Integer compute(int n) {
    return n * n;
  }

  static String fetchFromServer1() {
    return "one";
  }

  static String fetchFromServer2() {
    return "two";
  }

  static String fetchFromServer3() {
    return "three";
  }
}
