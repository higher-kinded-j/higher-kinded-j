// Fixture for hkj-book/src/monads/vtask_scope.md
//
// The page forks tasks under each of the four joiners: three service calls that must all succeed,
// three mirrors where the first success wins, a race, and a form whose fields validate in
// parallel. The domain the snippets elide is declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.time.Duration;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.ScopeJoiner;
import org.higherkindedj.hkt.vtask.VTask;

record UserData(String value) {}

record Package(String name) {}

record Result(String value) {}

record Data(String value) {}

record SignupInput(String username, String email, String password) {}

record ValidationError(String field, String message) {

  static ValidationError from(Throwable t) {
    return new ValidationError("unknown", t.getMessage());
  }
}

final class AuthService {

  UserData getPermissions(String userId) {
    return new UserData("admin");
  }
}

final class ProfileService {

  UserData getProfile(String userId) {
    return new UserData("profile");
  }
}

final class PrefsService {

  UserData getPreferences(String userId) {
    return new UserData("prefs");
  }
}

class Fixture {

  static final String id = "u-1";

  static final String userId = "u-1";

  static final List<Integer> ids = List.of(1, 2, 3);

  static final String mirror1 = "https://mirror-1";

  static final String mirror2 = "https://mirror-2";

  static final String mirror3 = "https://mirror-3";

  static final AuthService authService = new AuthService();

  static final ProfileService profileService = new ProfileService();

  static final PrefsService prefsService = new PrefsService();

  static final SignupInput input = new SignupInput("alice", "alice@example.com", "s3cret!");

  static String fetchUser(String id) {
    return "Alice";
  }

  static String fetchProfile(String id) {
    return "profile";
  }

  static String fetchPreferences(String id) {
    return "prefs";
  }

  static String fetchFromServerA() {
    return "a";
  }

  static String fetchFromServerB() {
    return "b";
  }

  static String fetchFromServerC() {
    return "c";
  }

  static VTask<Data> riskyFastPath() {
    return VTask.succeed(new Data("fast"));
  }

  static VTask<Data> reliableSlowPath() {
    return VTask.succeed(new Data("slow"));
  }

  static VTask<String> slowTask1() {
    return VTask.succeed("one");
  }

  static VTask<String> slowTask2() {
    return VTask.succeed("two");
  }

  static VTask<String> task1() {
    return VTask.succeed("one");
  }

  static VTask<String> task2() {
    return VTask.succeed("two");
  }

  static Integer processId(int id) {
    return id * 2;
  }

  static Package fetchFrom(String mirror) {
    return new Package(mirror);
  }

  static Result fastButRisky() {
    return new Result("fast");
  }

  static Result slowButSafe() {
    return new Result("safe");
  }

  static String validateUsername(String username) {
    return username;
  }

  static String validateEmail(String email) {
    return email;
  }

  static String validatePassword(String password) {
    return password;
  }
}
