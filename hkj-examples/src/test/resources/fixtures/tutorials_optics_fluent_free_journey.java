// Fixture for hkj-book/src/tutorials/optics/fluent_free_journey.md
//
// The journey reads a user through the fluent operations and then describes a configuration change
// as a Free program. The two models and the optics over them are declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.fluent.OpticOps;
import org.higherkindedj.optics.free.LoggingOpticInterpreter;
import org.higherkindedj.optics.free.OpticInterpreters;
import org.higherkindedj.optics.free.OpticOpKind;
import org.higherkindedj.optics.free.OpticPrograms;
import org.higherkindedj.optics.free.ValidationOpticInterpreter;
import org.higherkindedj.optics.util.Prisms;
import org.higherkindedj.optics.util.Traversals;

record Role(String name, boolean admin) {

  boolean isAdmin() {
    return admin;
  }
}

record User(String name, List<Role> roles) {}

record Database(String url) {}

record Config(String env, boolean debug, Optional<Database> database) {}

record Data(String payload) {}

record Success(Data data) implements Response {}

sealed interface Response permits Success, Failure {}

record Failure(String message) implements Response {}

class Fixture {

  /**
   * A value the page names but does not build. Snippets are compiled, never run, and a snippet
   * that shows a model shadows the one above, so naming a constructor here would tie the fixture
   * to one shape of it.
   */
  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static final User user = sample();

  static final Lens<User, String> lens = sample();

  static final Traversal<User, Role> rolesTraversal = sample();

  static final Config config = sample();

  static final Lens<Config, String> envLens = sample();

  static final Lens<Config, Boolean> debugLens = sample();
}
