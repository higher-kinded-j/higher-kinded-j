// Fixture for hkj-book/src/optics/focus_reference.md
//
// The reference page's patterns, FAQ answers and troubleshooting recipes all
// operate on a small org chart; the records live here and the annotation
// processor generates the *Focus and *Lenses companions during snippet
// compilation.
//
// NOTE: imports in a fixture serve the snippet this file is spliced into (this
// one also happens to use its imports itself). Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.higherkindedj.example.book.optics.cast.Address;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.fluent.OpticOps;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.free.OpticInterpreters;
import org.higherkindedj.optics.free.OpticOpKind;
import org.higherkindedj.optics.free.OpticPrograms;
import org.higherkindedj.optics.util.Traversals;
import org.higherkindedj.optics.each.EachInstances;
import org.higherkindedj.optics.focus.FocusPaths;

@GenerateLenses
@GenerateFocus
record OrgMember(String name, int age, Optional<String> email, Integer salary) {}

@GenerateLenses
@GenerateFocus
record OrgUnit(String name, List<OrgMember> members) {}

@GenerateLenses
@GenerateFocus
record OrgChart(String name, List<OrgUnit> units) {}

// The records the generated-code settings point at, beside the chapter cast's Address
@GenerateFocus
record Settings(boolean darkMode) {}

record Company(String name, Address address) {}

record Employee(String name, Company company) {}

@GenerateLenses
@GenerateFocus
record Department(String name, List<Employee> staff) {}

@GenerateFocus
record Division(String name, Department department) {}

// A Kind field behind a hand-written lens, for the fromTraverse recipe
record Role(String name) {}

record Operator(String name, Kind<ListKind.Witness, Role> roles) {}

class Fixture {
  static final OrgMember alice =
      new OrgMember("Alice", 41, Optional.of("alice@acme.test"), 52000);

  static final OrgMember bob = new OrgMember("Bob", 17, Optional.empty(), 18000);

  static final OrgUnit unit = new OrgUnit("Engineering", List.of(alice, bob));

  static final OrgChart company = new OrgChart("Acme", List.of(unit));

  static final List<OrgChart> companies = List.of(company);

  static final Operator user = new Operator("Ada", LIST.widen(List.of(new Role("admin"))));

  static final FocusPath<Operator, Kind<ListKind.Witness, Role>> rolesPath =
      FocusPath.of(Lens.of(Operator::roles, (operator, roles) -> new Operator(operator.name(), roles)));

  static Validated<String, Integer> validateAge(int age) {
    return age >= 18 && age <= 100 ? Validated.valid(age) : Validated.invalid("Invalid age: " + age);
  }

  static Kind<MaybeKind.Witness, String> checkEmail(String email) {
    return MaybeKindHelper.MAYBE.widen(
        email.contains("@") ? Maybe.just(email) : Maybe.nothing());
  }
}
