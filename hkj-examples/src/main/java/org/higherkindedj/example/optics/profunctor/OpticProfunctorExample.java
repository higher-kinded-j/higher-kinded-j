// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.profunctor;

import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Optic;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;

/**
 * A comprehensive example demonstrating profunctor-style adaptations using {@link Optic}
 * operations.
 *
 * <p>While this example demonstrates profunctor concepts through manual lens composition rather
 * than direct profunctor operations, it shows the same powerful patterns for adapting optics to
 * work with different data types and structures. This is particularly powerful for:
 *
 * <ul>
 *   <li><strong>Data Format Adaptation:</strong> Converting between different representations
 *   <li><strong>API Integration:</strong> Adapting internal models to external APIs
 *   <li><strong>Type Safety:</strong> Working with strongly-typed wrappers
 *   <li><strong>Legacy System Integration:</strong> Bridging old and new data structures
 * </ul>
 */
public class OpticProfunctorExample {

  // === Domain Models ===

  @GenerateLenses
  @GenerateTraversals
  public record PersonalDetails(
      String firstName, String lastName, LocalDate birthDate, List<String> hobbies) {}

  @GenerateLenses
  public record PersonDto(String fullName, String birthDateString, List<String> interests) {}

  @GenerateLenses
  public record StaffRecord(int id, PersonalDetails personalInfo, String department) {}

  @GenerateLenses
  public record EmployeeDto(int employeeId, PersonDto person, String dept) {}

  // Wrapper types for demonstration
  public record UserId(String value) {}

  public record UserName(String value) {}

  public record Email(String value) {}

  @GenerateLenses
  public record SiteMember(UserId id, UserName name, Email email, LocalDate createdAt) {}

  public static void main(String[] args) {
    OpticProfunctorExample example = new OpticProfunctorExample();

    System.out.println(
        "=== Optic Profunctor-Style Example: Adapting Optics to Different Types ===\n");

    example.contramapStyleExample();
    example.mapStyleExample();
    example.dimapStyleExample();
    example.realWorldApiAdapterExample();
    example.typeWrapperExample();
  }

  /**
   * Demonstrates contramap-style adaptation: adapting the source type of an optic. This allows you
   * to use an optic designed for one type with a different source type.
   */
  public void contramapStyleExample() {
    System.out.println("--- Contramap-Style: Adapting Source Types ---");

    // Original lens: PersonalDetails -> String (first name)
    Lens<PersonalDetails, String> firstNameLens = PersonalDetailsLenses.firstName();

    // We want to work with StaffRecord objects, but use the PersonalDetails lens
    // Create a new lens that adapts StaffRecord -> PersonalDetails, then applies the original lens
    Lens<StaffRecord, String> employeeFirstNameLens =
        Lens.of(
            employee -> firstNameLens.get(employee.personalInfo()),
            (employee, newName) ->
                new StaffRecord(
                    employee.id(),
                    firstNameLens.set(newName, employee.personalInfo()),
                    employee.department()));

    StaffRecord employee =
        new StaffRecord(
            123,
            new PersonalDetails(
                "Alice", "Johnson", LocalDate.of(1990, 5, 15), List.of("reading", "hiking")),
            "Engineering");

    System.out.println("Original employee: " + employee);

    // Use the adapted lens to modify the first name within the employee
    StaffRecord updatedEmployee = employeeFirstNameLens.modify(String::toUpperCase, employee);

    System.out.println("After contramap-style modification: " + updatedEmployee);
    System.out.println(
        "First name changed from '"
            + employee.personalInfo().firstName()
            + "' to '"
            + updatedEmployee.personalInfo().firstName()
            + "'\n");
  }

  /**
   * Demonstrates map-style adaptation: adapting the target type of an optic. This allows you to
   * transform the result type while keeping the same source.
   */
  public void mapStyleExample() {
    System.out.println("--- Map-Style: Adapting Target Types ---");

    // Original lens: PersonalDetails -> LocalDate (birth date)
    Lens<PersonalDetails, LocalDate> birthDateLens = PersonalDetailsLenses.birthDate();

    // We want to work with formatted date strings instead of LocalDate
    // Create a lens that works with strings by composing operations
    Lens<PersonalDetails, String> birthDateStringLens =
        Lens.of(
            person -> birthDateLens.get(person).format(DateTimeFormatter.ISO_LOCAL_DATE),
            (person, dateString) -> birthDateLens.set(LocalDate.parse(dateString), person));

    PersonalDetails person =
        new PersonalDetails("Bob", "Smith", LocalDate.of(1985, 12, 25), List.of("cooking"));

    System.out.println("Original person: " + person);
    System.out.println("Birth date as LocalDate: " + person.birthDate());

    // The adapted lens works with strings
    String currentDateString = birthDateStringLens.get(person);
    System.out.println("Birth date as formatted string: " + currentDateString);

    // We can modify using the string lens
    PersonalDetails updatedPerson = birthDateStringLens.set("1986-01-01", person);
    System.out.println("After setting new date via string: " + updatedPerson);
    System.out.println();
  }

  /**
   * Demonstrates dimap-style adaptation: adapting both source and target types. This is the most
   * powerful operation, allowing complete type transformations.
   */
  public void dimapStyleExample() {
    System.out.println("--- Dimap-Style: Adapting Both Source and Target Types ---");

    // Original traversal: PersonalDetails -> String (hobbies)
    Traversal<PersonalDetails, String> hobbiesTraversal = PersonalDetailsTraversals.hobbies();

    // We want to work with PersonDto and call them "interests" instead of "hobbies"
    // Create a traversal that handles the conversion manually
    Traversal<PersonDto, String> interestsTraversal =
        new Traversal<PersonDto, String>() {
          @Override
          public <F extends WitnessArity<TypeArity.Unary>> Kind<F, PersonDto> modifyF(
              Function<String, Kind<F, String>> f, PersonDto source, Applicative<F> applicative) {

            // Convert PersonDto -> PersonalDetails
            PersonalDetails person = convertDtoToPerson(source);

            // Apply the original traversal
            Kind<F, PersonalDetails> modifiedPersonF =
                hobbiesTraversal.modifyF(f, person, applicative);

            // Convert back PersonalDetails -> PersonDto
            return applicative.map(this::convertPersonToDto, modifiedPersonF);
          }

          private PersonDto convertPersonToDto(PersonalDetails person) {
            return new PersonDto(
                person.firstName() + " " + person.lastName(),
                person.birthDate().format(DateTimeFormatter.ISO_LOCAL_DATE),
                person.hobbies());
          }
        };

    PersonDto originalDto =
        new PersonDto("Charlie Brown", "1992-08-10", List.of("baseball", "kite flying", "writing"));

    System.out.println("Original DTO: " + originalDto);

    // Use the adapted traversal to modify interests (which are actually hobbies internally)
    PersonDto updatedDto =
        ID.narrow(
                interestsTraversal.modifyF(
                    interest -> Id.of(interest.toUpperCase()), originalDto, Instances.monad(id())))
            .value();

    System.out.println("After dimap-style modification: " + updatedDto);
    System.out.println("All interests converted to uppercase\n");
  }

  /**
   * Real-world example: Creating an API adapter that transforms between internal and external data
   * representations using manual adaptation techniques.
   */
  public void realWorldApiAdapterExample() {
    System.out.println("--- Real-World API Adapter ---");

    // Internal lens for working with StaffRecord objects
    Lens<StaffRecord, String> departmentLens = StaffRecordLenses.department();

    // Create an adapter for external EmployeeDto format
    // This handles the differences: department vs dept, personalInfo vs person
    Lens<EmployeeDto, String> dtoAdapter =
        Lens.of(
            // Getter: EmployeeDto -> String
            dto -> {
              StaffRecord employee =
                  new StaffRecord(dto.employeeId(), convertDtoToPerson(dto.person()), dto.dept());
              return departmentLens.get(employee);
            },
            // Setter: (EmployeeDto, String) -> EmployeeDto
            (dto, newDept) -> {
              StaffRecord employee =
                  new StaffRecord(dto.employeeId(), convertDtoToPerson(dto.person()), dto.dept());
              StaffRecord updatedEmployee = departmentLens.set(newDept, employee);
              return new EmployeeDto(
                  updatedEmployee.id(),
                  convertPersonToDto(updatedEmployee.personalInfo()),
                  updatedEmployee.department());
            });

    EmployeeDto externalEmployee =
        new EmployeeDto(
            456,
            new PersonDto("Diana Prince", "1985-07-22", List.of("archaeology", "languages")),
            "Research");

    System.out.println("External API format: " + externalEmployee);

    // Use the adapter to modify department through the DTO interface
    EmployeeDto promotedEmployee = dtoAdapter.modify(dept -> "Senior " + dept, externalEmployee);

    System.out.println("After promotion: " + promotedEmployee);
    System.out.println(
        "Department: '" + externalEmployee.dept() + "' -> '" + promotedEmployee.dept() + "'\n");
  }

  /**
   * Demonstrates working with strongly-typed wrapper classes using lens operations. This shows how
   * to work with type-safe wrappers effectively.
   */
  public void typeWrapperExample() {
    System.out.println("--- Type-Safe Wrapper Adaptation ---");

    // Working with UserName wrapper type directly through lens operations
    Lens<SiteMember, UserName> userNameLens = SiteMemberLenses.name();

    Function<String, String> titleCase =
        name -> name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();

    SiteMember user =
        new SiteMember(
            new UserId("u-789"),
            new UserName("john doe"),
            new Email("john@example.com"),
            LocalDate.of(2025, 1, 1));

    System.out.println("Original user: " + user);

    // Extract the raw string, apply transformation, and set it back
    String currentName = userNameLens.get(user).value();
    String formattedName = titleCase.apply(currentName);
    SiteMember updatedUser = userNameLens.set(new UserName(formattedName), user);

    System.out.println("After title case: " + updatedUser);
    System.out.println("Name: '" + currentName + "' -> '" + formattedName + "'");

    // Alternative: Use modify to transform the UserName directly
    SiteMember updatedUser2 =
        userNameLens.modify(userName -> new UserName(userName.value().toUpperCase()), user);

    System.out.println("After uppercase: " + updatedUser2);

    // Demonstrate adapter pattern for working with string operations on wrapped types
    Lens<SiteMember, String> stringNameLens =
        Lens.of(
            user2 -> userNameLens.get(user2).value(),
            (user2, newName) -> userNameLens.set(new UserName(newName), user2));

    SiteMember finalUser = stringNameLens.modify(name -> name.replace(" ", "_"), user);
    System.out.println("After string manipulation: " + finalUser);
    System.out.println();
  }

  // === Helper Methods ===

  private PersonalDetails convertDtoToPerson(PersonDto dto) {
    LocalDate birthDate = LocalDate.parse(dto.birthDateString());
    String[] names = dto.fullName().split(" ", 2);
    return new PersonalDetails(
        names.length > 0 ? names[0] : "",
        names.length > 1 ? names[1] : "",
        birthDate,
        dto.interests());
  }

  private PersonDto convertPersonToDto(PersonalDetails person) {
    return new PersonDto(
        person.firstName() + " " + person.lastName(),
        person.birthDate().format(DateTimeFormatter.ISO_LOCAL_DATE),
        person.hobbies());
  }
}
