// Fixture for hkj-book/src/effect/path_vstream.md
//
// The page catalogues VStreamPath's constructors, combinators and terminal operations. Most of it
// is self-contained; the paginated-unfold example needs a page source, which lives here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.effect.ListPath;
import org.higherkindedj.hkt.effect.NonDetPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.StreamPath;
import org.higherkindedj.hkt.effect.VStreamPath;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Each;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.util.Traversals;

record Page(int number, List<String> items) {}

record User(String name, int age, String email, boolean active) {

  boolean isActive() {
    return active;
  }
}

record UserPage(List<User> items) {

  boolean isEmpty() {
    return items.isEmpty();
  }
}

class Fixture {

  static final int lastPage = 5;

  static final VStream<String> myVStream = VStream.of("a", "b", "c");

  static Page fetchPage(int pageNum) {
    return new Page(pageNum, List.of());
  }

  static final VStream<User> userStream =
      VStream.of(new User("Alice", 30, "alice@example.test", true));

  static final FocusPath<User, String> userNameLens =
      FocusPath.of(Lens.of(User::name, (u, n) -> new User(n, u.age(), u.email(), u.active())));

  static final FocusPath<User, String> userEmailLens =
      FocusPath.of(Lens.of(User::email, (u, e) -> new User(u.name(), u.age(), e, u.active())));

  static final Each<List<String>, String> listEach = new ListEach();

  static final UserService userService = new UserService();

  static final class UserService {

    UserPage listUsers(int page) {
      return new UserPage(List.of());
    }
  }

  /** The `Each` the fromEach example traverses with. */
  static final class ListEach implements Each<List<String>, String> {

    @Override
    public Traversal<List<String>, String> each() {
      return Traversals.forList();
    }
  }
}
