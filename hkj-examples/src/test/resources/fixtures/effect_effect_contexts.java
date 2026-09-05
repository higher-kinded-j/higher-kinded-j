// Fixture for hkj-book/src/effect/effect_contexts.md
//
// The chapter's entry point contrasts a raw monad transformer with the four Effect Contexts over
// one user/profile/order sketch. The services and domain behind it live here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import org.higherkindedj.hkt.effect.context.ConfigContext;
import org.higherkindedj.hkt.effect.context.ErrorContext;
import org.higherkindedj.hkt.effect.context.OptionalContext;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.io.IOKind;
import org.jspecify.annotations.Nullable;

record User(String id, String profileId) {}

record Profile(String id) {}

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
