// Fixture for hkj-book/src/effect/effect_contexts.md
//
// The chapter's entry point contrasts a raw monad transformer with the four Effect Contexts over
// one user/profile/order sketch. The services and domain behind it live here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.io;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;

import java.time.Duration;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.context.ConfigContext;
import org.higherkindedj.hkt.effect.context.MutableContext;
import org.higherkindedj.hkt.effect.context.VTaskContext;
import org.higherkindedj.hkt.either_t.EitherT;
import org.higherkindedj.hkt.either_t.EitherTKind;
import org.higherkindedj.hkt.either_t.EitherTMonad;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.effect.context.ErrorContext;
import org.higherkindedj.hkt.effect.context.OptionalContext;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.io.IOKind;
import org.jspecify.annotations.Nullable;

record User(String id, String profileId) {}

record Profile(String id) {

  static Profile defaultProfile() {
    return new Profile("default");
  }
}

record Counter(int value) {

  Counter increment() {
    return new Counter(value + 1);
  }
}

record Cart(String id) {}

record Order(String id) {

  static Order failed(String reason) {
    return new Order("failed: " + reason);
  }
}

record Config(String name) {

  static Config defaults() {
    return new Config("defaults");
  }
}

record ServiceConfig(String reportFormat, int retentionDays) {}

record Report(String body) {}

record ApiError(String message) {

  static ApiError fromException(Throwable cause) {
    return new ApiError(String.valueOf(cause.getMessage()));
  }
}

class Fixture {

  static final String userId = "u-1";

  static final User user = new User("u-1", "p-1");

  static Config loadFromPrimary() {
    return new Config("primary");
  }

  static Config loadFromSecondary() {
    return new Config("secondary");
  }

  static final UserService userService = new UserService();

  static final ProfileService profileService = new ProfileService();

  static final CartService cartService = new CartService();

  static final OrderService orderService = new OrderService();

  static final ReportService reportService = new ReportService();

  static final Cache cache = new Cache();

  static final Database database = new Database();

  static final class UserService {

    User fetch(String id) {
      return new User(id, "p-1");
    }
  }

  static final class ProfileService {

    Profile fetch(String profileId) {
      return new Profile(profileId);
    }
  }

  static final class CartService {

    Cart getCart(String userId) {
      return new Cart("c-1");
    }
  }

  static final class OrderService {

    Order createOrder(Cart cart) {
      return new Order("o-1");
    }
  }

  static final class ReportService {

    Report generate(String format) {
      return new Report("report in " + format);
    }
  }

  static final class Cache {

    @Nullable Config get(String key) {
      return new Config("cached");
    }
  }

  static final class Database {

    @Nullable Config loadConfig() {
      return new Config("db");
    }
  }
}
