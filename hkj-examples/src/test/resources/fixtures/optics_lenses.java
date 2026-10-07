// Fixture for hkj-book/src/optics/lenses.md
//
// The page builds one nested model - employee, company, address - and then composes lenses through
// it. The records are declared here with `@GenerateLenses`, so the page's snippets name genuinely
// generated optics; a snippet that shows the declarations shadows this copy.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.util.Prisms;

@GenerateLenses
record Address(String street, String city) {}

@GenerateLenses
record Company(String name, Address address) {}

@GenerateLenses
record Employee(String name, Company company) {}

record Settings(String theme) {}

@GenerateLenses
record User(Optional<Settings> settings) {}

class Fixture {

  static final Address initialAddress = new Address("123 Fake St", "Anytown");

  static final Company initialCompany = new Company("Initech Inc.", initialAddress);

  static final Employee employee = new Employee("Alice", initialCompany);

  static final Employee initialEmployee = employee;

  static final Employee emp1 = employee;

  static final Employee emp2 = employee;

  // Written by hand rather than taken from the generated companions: the page shows the model
  // once WITHOUT `@GenerateLenses` before adding it, and in that snippet's unit there is no
  // companion to take them from.
  static final Lens<Employee, Company> employeeToCompany =
      Lens.of(Employee::company, (e, v) -> new Employee(e.name(), v));

  static final Lens<Company, Address> companyToAddress =
      Lens.of(Company::address, (c, v) -> new Company(c.name(), v));

  static final Lens<Address, String> addressToStreet =
      Lens.of(Address::street, (a, v) -> new Address(v, a.city()));

  static final Lens<Employee, String> employeeToStreet =
      employeeToCompany.andThen(companyToAddress).andThen(addressToStreet);

  // The applicative the effectful-update section hands to modifyF: it joins error strings.
  static final Applicative<ValidatedKind.Witness<String>> validatedApplicative =
      Instances.validated(Semigroups.string("; "));
}
