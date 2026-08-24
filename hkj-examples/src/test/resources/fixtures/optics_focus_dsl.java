// Fixture for hkj-book/src/optics/focus_dsl.md
//
// The page's five-minute walkthrough and path-type sections operate on a small
// user/address graph and a company graph; the records live here and the
// annotation processor generates the *Focus and *Lenses companions during
// snippet compilation.
//
// NOTE: imports in a fixture serve the snippet this file is spliced into (this
// one also happens to use its imports itself). Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import java.util.List;
import java.util.Optional;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;

@GenerateLenses
@GenerateFocus(generateNavigators = true)
record Address(String street, String city) {}

@GenerateLenses
@GenerateFocus(generateNavigators = true)
record User(String name, Address address) {}

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
  static final User alice = new User("Alice", new Address("Old Street", "London"));

  static final Employee employee = new Employee("Bob", 41, Optional.of("bob@example.com"));

  static final Department department = new Department("Engineering", List.of(employee));

  static final Company company = new Company("Acme", List.of(department));

  static Employee promote(Employee e) {
    return new Employee(e.name(), e.age(), e.email());
  }
}
