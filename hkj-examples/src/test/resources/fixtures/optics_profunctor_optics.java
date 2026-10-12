// Fixture for hkj-book/src/optics/profunctor_optics.md
//
// The page's verified snippets adapt optics across an internal StaffRecord/PersonalDetails
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
record PersonalDetails(String firstName, String lastName, LocalDate birthDate, List<String> hobbies) {}

@GenerateLenses
record StaffRecord(int id, PersonalDetails personalInfo, String department) {}

record PersonDto(String fullName, String birthDateString, List<String> interests) {}

record EmployeeDto(int employeeId, PersonDto person, String dept) {}

record UserId(String value) {}

@GenerateLenses
record UserAccount(UserId id, String owner) {}

class Adapters {
  static StaffRecord dtoToEmployee(EmployeeDto dto) {
    String[] parts = dto.person().fullName().split(" ", 2);
    return new StaffRecord(
        dto.employeeId(),
        new PersonalDetails(
            parts[0],
            parts.length > 1 ? parts[1] : "",
            LocalDate.parse(dto.person().birthDateString()),
            dto.person().interests()),
        dto.dept());
  }

  static EmployeeDto employeeToDto(StaffRecord employee) {
    PersonalDetails p = employee.personalInfo();
    return new EmployeeDto(
        employee.id(),
        new PersonDto(p.firstName() + " " + p.lastName(), p.birthDate().toString(), p.hobbies()),
        employee.department());
  }
}

class Fixture {
  static final StaffRecord employee =
      new StaffRecord(
          1, new PersonalDetails("Ada", "Lovelace", LocalDate.of(1815, 12, 10), List.of("maths")),
          "Engineering");
  static final EmployeeDto dto = Adapters.employeeToDto(employee);
  static final UserAccount account = new UserAccount(new UserId("u-42"), "main");
}
