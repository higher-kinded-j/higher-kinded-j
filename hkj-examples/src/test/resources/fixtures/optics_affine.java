// Fixture for hkj-book/src/optics/affine.md
//
// The page reaches through optional fields of a configuration and of a user, and shows each model
// where it first needs it. The models are declared here so the later snippets have something to
// name; a snippet that shows one shadows this copy, which is why the values below are `sample()`
// stand-ins rather than constructor calls - a shadowing snippet may declare a different shape.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.util.Affines;
import org.higherkindedj.optics.util.Prisms;
import org.higherkindedj.optics.util.Traversals;
import org.jspecify.annotations.Nullable;

record ConnectionSettings(Optional<Integer> timeout) {}

record DatabaseSettings(String host, int port, Optional<ConnectionSettings> connection) {}

record Config(Optional<DatabaseSettings> database) {

  static final Config DEFAULT = new Config(Optional.empty());
}

record Address(String street, Optional<String> postcode) {}

record User(String name, Optional<Address> address) {}

class UserOptics {

  static final Affine<User, String> STREET =
      Fixture.addressLens.andThen(Fixture.addressPrism).andThen(Fixture.streetLens);

  static final Affine<User, String> POSTCODE =
      Fixture.addressLens
          .andThen(Fixture.addressPrism)
          .andThen(Fixture.postcodeLens)
          .andThen(Fixture.postcodePrism);
}

class Fixture {

  // A value the page names but does not build. Snippets are compiled, not run, and a snippet that
  // shows a model shadows the one above, so naming a constructor here would tie the fixture to one
  // shape of it.
  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static final Config config = sample();

  static final DatabaseSettings defaultSettings = sample();

  static final User user = sample();

  static final List<String> names = List.of("alice", "bob");

  static final Lens<User, Optional<Address>> addressLens = sample();

  static final Prism<Optional<Address>, Address> addressPrism = Prisms.some();

  static final Lens<Address, String> streetLens = sample();

  static final Lens<Address, Optional<String>> postcodeLens = sample();

  static final Prism<Optional<String>, String> postcodePrism = Prisms.some();

  static final Affine<Config, DatabaseSettings> databaseAffine = sample();

  static final Affine<DatabaseSettings, ConnectionSettings> connectionAffine = sample();

  static final Lens<ConnectionSettings, Optional<Integer>> timeoutLens = sample();

  static Optional<Config> loadConfig() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}
