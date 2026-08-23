// Fixture for hkj-book/src/optics/focus_reference.md
//
// The reference page's patterns, FAQ answers and troubleshooting recipes all
// operate on a small company graph; the records live here and the annotation
// processor generates the *Focus and *Lenses companions during snippet
// compilation.
//
// NOTE: imports in a fixture serve the snippet this file is spliced into (this
// one also happens to use its imports itself). Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.maybe;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.hkt.validated.Validated;
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

@GenerateLenses
@GenerateFocus
record Employee(String name, int age, Optional<String> email) {}

@GenerateLenses
@GenerateFocus
record Department(String name, List<Employee> employees) {}

@GenerateLenses
@GenerateFocus
record Company(String name, List<Department> departments) {}

class Fixture {
  static final Employee alice = new Employee("Alice", 41, Optional.of("alice@acme.test"));

  static final Employee bob = new Employee("Bob", 17, Optional.empty());

  static final Department department = new Department("Engineering", List.of(alice, bob));

  static final Company company = new Company("Acme", List.of(department));

  static final List<Company> companies = List.of(company);

  static Validated<String, Integer> validateAge(int age) {
    return age >= 18 && age <= 100 ? Validated.valid(age) : Validated.invalid("Invalid age: " + age);
  }

  static Kind<MaybeKind.Witness, String> checkEmail(String email) {
    return MaybeKindHelper.MAYBE.widen(
        email.contains("@") ? Maybe.just(email) : Maybe.nothing());
  }
}
