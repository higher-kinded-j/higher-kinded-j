// Fixture for hkj-book/src/tutorials/effect/effect_cheatsheet.md
//
// The cheatsheet is mostly notation; the two blocks that are code run a context to its answer and
// declare a service the path bridge generates from. Their types are declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.util.Optional;
import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
import org.higherkindedj.hkt.effect.annotation.PathVia;
import org.higherkindedj.hkt.effect.context.ConfigContext;
import org.higherkindedj.hkt.effect.context.ErrorContext;
import org.higherkindedj.hkt.effect.context.MutableContext;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.state.StateTuple;

record User(Long id, String name) {}

record CreateUserRequest(String name) {}

record AppError(String message) {}

record Config(String env) {}

class Fixture {

  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static final ErrorContext<?, AppError, User> errorCtx = sample();

  static final ConfigContext<?, Config, User> configCtx = sample();

  static final MutableContext<?, Integer, User> mutableCtx = sample();

  static final Config config = new Config("prod");

  static final Integer initial = 0;
}
