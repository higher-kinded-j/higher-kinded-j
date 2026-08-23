// Fixture for hkj-book/src/optics/ch5_intro.md
//
// The page's payoff snippet composes a Lens, a Prism, a Traversal and a Lens
// into one path from a Form down to every permission name, then runs an
// accumulating validation over it. The records live here and the annotation
// processor generates the *Lenses, *Prisms and *Traversals companions during
// snippet compilation.
//
// NOTE: imports in a fixture serve the snippet this file is spliced into.
// Spotless excludes src/test/resources so an "unused import" cleanup cannot
// break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.List;
import java.util.Set;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.annotations.GenerateTraversals;

@GenerateLenses
record Permission(String name) {}

@GeneratePrisms
sealed interface Principal permits User, Guest {}

@GenerateLenses
@GenerateTraversals
record User(String username, List<Permission> permissions) implements Principal {}

record Guest() implements Principal {}

@GenerateLenses
record Form(int formId, Principal principal) {}

class Fixture {

  static final Set<String> ALLOWED = Set.of("PERM_READ", "PERM_WRITE", "PERM_DELETE");

  static final Form form =
      new Form(
          42,
          new User(
              "alice",
              List.of(new Permission("PERM_READ"), new Permission("PERM_FLY"))));

  static Kind<ValidatedKind.Witness<String>, String> validatePermission(String name) {
    return ALLOWED.contains(name)
        ? VALIDATED.widen(Validated.valid(name))
        : VALIDATED.widen(Validated.invalid("Invalid permission: " + name));
  }
}
