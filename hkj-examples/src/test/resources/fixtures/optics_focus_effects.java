// Fixture for hkj-book/src/optics/focus_effects.md
//
// The page runs effectful modifications, monoid aggregation, Traverse-driven
// navigation and Focus-to-Effect bridging over a small company graph; the
// records live here and the annotation processor generates the *Focus and
// *Lenses companions during snippet compilation.
//
// NOTE: imports in a fixture serve the snippet this file is spliced into (this
// one also happens to use its imports itself). Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.io;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.ListPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.util.Traversals;
import org.jspecify.annotations.Nullable;

@GenerateLenses
@GenerateFocus
record Employee(String name, String email, Optional<String> nickname, int salary) {}

@GenerateLenses
@GenerateFocus
record Agency(String name, List<Employee> employees) {}

@GenerateLenses
record Contact(String name, @Nullable String phone) {}

record Role(String title) {}

record RoleBox(Kind<ListKind.Witness, Role> roles) {}

class Fixture {
  static final Employee alice = new Employee("Alice", "alice@acme.test", Optional.empty(), 60000);

  static final Employee bob = new Employee("Bob", "bob@acme.test", Optional.of("Bobby"), 55000);

  static final Agency agency = new Agency("Acme", List.of(alice, bob));

  static final Contact carol = new Contact("Carol", null);

  static final List<@Nullable String> aliases = Arrays.asList("Ace", null, "Bee");

  static final Lens<RoleBox, Kind<ListKind.Witness, Role>> rolesLens =
      Lens.of(RoleBox::roles, (box, roles) -> new RoleBox(roles));

  static final RoleBox roleBox = new RoleBox(LIST.widen(List.of(new Role("admin"))));

  static Validated<List<String>, String> validateEmail(String email) {
    return email.contains("@")
        ? Validated.valid(email)
        : Validated.invalid(List.of("Invalid email: " + email));
  }

  static Role promote(Role role) {
    return new Role(role.title().toUpperCase());
  }
}
