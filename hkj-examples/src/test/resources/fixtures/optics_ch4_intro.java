// Fixture for hkj-book/src/optics/ch4_intro.md
//
// The page's payoff snippet navigates a company graph with generated
// navigators; the records live here and the annotation processor generates the
// *Focus companions (navigators included) during snippet compilation.
//
// NOTE: imports in a fixture serve the snippet this file is spliced into (this
// one also happens to use its imports itself). Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import java.math.BigDecimal;
import java.util.List;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;

@GenerateLenses
@GenerateFocus(generateNavigators = true)
record Address(String street, String city) {}

@GenerateLenses
@GenerateFocus(generateNavigators = true)
record Employee(String name, BigDecimal salary) {}

@GenerateLenses
@GenerateFocus(generateNavigators = true)
record Department(String name, List<Employee> staff) {}

@GenerateLenses
@GenerateFocus(generateNavigators = true)
record Company(String name, Address headquarters, List<Department> departments) {}

class Fixture {
  static final Company acme =
      new Company(
          "Acme",
          new Address("1 Long Street", "London"),
          List.of(
              new Department(
                  "Engineering",
                  List.of(
                      new Employee("Alice", new BigDecimal("60000")),
                      new Employee("Bob", new BigDecimal("55000")))),
              new Department("Sales", List.of(new Employee("Carol", new BigDecimal("50000"))))));
}
