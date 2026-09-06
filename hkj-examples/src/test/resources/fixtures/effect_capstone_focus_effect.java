// Fixture for hkj-book/src/effect/capstone_focus_effect.md
//
// The company directory the whole page updates. The four records carry their annotations here so
// the Focus classes are generated for every snippet, not only the one that shows the
// declarations; the extractor drops this copy when a snippet brings its own, and the processor
// then generates from that.
//
// `findDepartment` and `validateEmail` are declared here because the main pipeline calls them
// before the page shows them, and the later snippets that do show them override these.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

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

  static final Company company = new Company("Acme", List.of());

  static final Department department = new Department("Ops", Optional.empty(), List.of());

  static final String deptName = "Ops";

  static final String newEmail = "ada@example.test";

  EitherPath<DirectoryError, Department> findDepartment(Company company, String name) {
    return company.departments().stream()
        .filter(d -> d.name().equals(name))
        .findFirst()
        .map(d -> Path.<DirectoryError, Department>right(d))
        .orElse(Path.left(new DirectoryError.DepartmentNotFound(name)));
  }

  EitherPath<DirectoryError, String> validateEmail(String email) {
    return Path.right(email);
  }
}
