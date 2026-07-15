// Fixture for .claude/skills/hkj-bridge/reference/capstone-example.md
//
// The page is one worked example told twice: imperative, then as a Focus/Path pipeline. Both halves
// are real code, so both are compiled here. What the page elides is only the plumbing it has already
// shown further up (the four records, the error type, the two helpers), which is exactly what a
// fixture is for -- and it means the pipeline's `.focus(affine, error)` / `.focus(focusPath)` overloads
// are checked against the real signatures rather than a reader's memory of them.
//
// The helpers are INSTANCE methods: the page writes `findDepartment` without `static`, and the snippet
// that re-declares it becomes a member of a class that extends this one. A static method here could not
// be overridden by an instance method there.
//
// NOTE: the imports below look unused *here*. They are for the snippet this file is spliced into.
// That is why spotless excludes src/test/resources/fixtures (see build.gradle.kts).

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;

@GenerateLenses
@GenerateFocus
record Company(String name, List<Department> departments) {}

@GenerateLenses
@GenerateFocus
record Department(String name, Optional<Employee> manager, List<Employee> staff) {}

@GenerateLenses
@GenerateFocus
record Employee(String name, ContactInfo contact) {}

@GenerateLenses
@GenerateFocus
record ContactInfo(String phone, Optional<String> email) {}

sealed interface DirectoryError {
  record DepartmentNotFound(String name) implements DirectoryError {}

  record NoManager(String department) implements DirectoryError {}

  record InvalidEmail(String email, String reason) implements DirectoryError {}
}

class Fixture {

  static Company company;
  static ContactInfo updatedContact;
  static String deptName = "Engineering";
  static String newEmail = "manager@example.com";

  EitherPath<DirectoryError, Department> findDepartment(Company company, String name) {
    return company.departments().stream()
        .filter(d -> d.name().equals(name))
        .findFirst()
        .map(d -> Path.<DirectoryError, Department>right(d))
        .orElse(Path.left(new DirectoryError.DepartmentNotFound(name)));
  }

  EitherPath<DirectoryError, String> validateEmail(String email) {
    if (email.contains("@") && email.length() >= 5) {
      return Path.right(email);
    }
    return Path.left(new DirectoryError.InvalidEmail(email, "Must contain @ and be >= 5 chars"));
  }
}
