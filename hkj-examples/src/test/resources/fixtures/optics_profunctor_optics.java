// Fixture for hkj-book/src/optics/profunctor_optics.md
//
// The page's verified snippets adapt optics across an internal Employee/Person
// model, an external EmployeeDto wire shape, and a UserId wrapper; the records
// and the conversion pair live here, and the annotation processor generates
// the *Lenses companions during snippet compilation.
//
// NOTE: imports in a fixture serve the snippet this file is spliced into.
// Spotless excludes src/test/resources so an "unused import" cleanup cannot
// break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.id.IdKindHelper.ID;

import java.time.LocalDate;
import java.util.List;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Optic;
import org.higherkindedj.optics.annotations.GenerateLenses;

@GenerateLenses
record Person(String firstName, String lastName, LocalDate birthDate, List<String> skills) {}

@GenerateLenses
record Employee(int id, Person personalInfo, String department) {}

record PersonDto(String fullName, String birthDateString, List<String> interests) {}

record EmployeeDto(int employeeId, PersonDto person, String dept) {}

record UserId(String value) {}

@GenerateLenses
record Account(String name, UserId userId) {}

class Adapters {
  static Employee dtoToEmployee(EmployeeDto dto) {
    String[] parts = dto.person().fullName().split(" ", 2);
    return new Employee(
        dto.employeeId(),
        new Person(
            parts[0],
            parts.length > 1 ? parts[1] : "",
            LocalDate.parse(dto.person().birthDateString()),
            dto.person().interests()),
        dto.dept());
  }

  static EmployeeDto employeeToDto(Employee employee) {
    Person p = employee.personalInfo();
    return new EmployeeDto(
        employee.id(),
        new PersonDto(p.firstName() + " " + p.lastName(), p.birthDate().toString(), p.skills()),
        employee.department());
  }
}

class Fixture {
  static final Employee employee =
      new Employee(
          1, new Person("Ada", "Lovelace", LocalDate.of(1815, 12, 10), List.of("maths")),
          "Engineering");
  static final EmployeeDto dto = Adapters.employeeToDto(employee);
  static final Account account = new Account("main", new UserId("u-42"));
}
