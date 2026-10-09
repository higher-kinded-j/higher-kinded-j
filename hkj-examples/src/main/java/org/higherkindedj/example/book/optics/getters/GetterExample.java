// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.getters;

// ANCHOR: imports
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.example.book.optics.cast.Address;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Getter;

// ANCHOR_END: imports

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/getters.html">Getters</a> page, as its
 * complete, runnable example. The page {@code {{#include}}}s the anchored region, and shows what
 * {@code main} prints from a golden file the book's output gate holds to it.
 */
// ANCHOR: getter_example
public class GetterExample {

  public record Person(String firstName, String lastName, int age, Address address) {}

  public record Company(String name, Person ceo, List<Person> employees, Address headquarters) {}

  public static void main(String[] args) {
    // Create sample data
    Address ceoAddress = new Address("123 Executive Blvd", "London", "EC1A 1BB");
    Person ceo = new Person("Jane", "Smith", 45, ceoAddress);

    List<Person> employees =
        List.of(
            new Person("John", "Doe", 30, new Address("456 Oak St", "Manchester", "M1 1AE")),
            new Person("Alice", "Johnson", 28, new Address("789 Elm Ave", "Birmingham", "B1 1BB")),
            new Person("Bob", "Williams", 35, new Address("321 Pine Rd", "Leeds", "LS1 4AP")));

    Address hqAddress = new Address("1000 Corporate Way", "London", "EC2A 4NE");
    Company company = new Company("TechCorp", ceo, employees, hqAddress);

    // === Basic Getters ===
    Getter<Person, String> fullName = Getter.of(p -> p.firstName() + " " + p.lastName());
    Getter<Person, Integer> age = Getter.of(Person::age);

    System.out.println("CEO: " + fullName.get(ceo));
    System.out.println("CEO Age: " + age.get(ceo));

    // === Computed Values ===
    Getter<Person, String> initials =
        Getter.of(p -> p.firstName().charAt(0) + "." + p.lastName().charAt(0) + ".");
    Getter<Person, String> email =
        Getter.of(
            p -> p.firstName().toLowerCase() + "." + p.lastName().toLowerCase() + "@techcorp.com");

    System.out.println("CEO Initials: " + initials.get(ceo));
    System.out.println("CEO Email: " + email.get(ceo));

    // === Composition ===
    Getter<Person, Address> addressGetter = Getter.of(Person::address);
    Getter<Address, String> cityGetter = Getter.of(Address::city);
    Getter<Company, Person> ceoGetter = Getter.of(Company::ceo);

    Getter<Person, String> personCity = addressGetter.andThen(cityGetter);
    Getter<Company, String> companyCeoCity = ceoGetter.andThen(personCity);

    System.out.println("CEO City: " + personCity.get(ceo));
    System.out.println("Company CEO City: " + companyCeoCity.get(company));

    // === Getter as Fold ===
    Optional<Integer> ceoAge = age.preview(ceo);
    boolean isExperienced = age.exists(a -> a > 40, ceo);
    int ageCount = age.length(ceo); // Always 1 for Getter

    System.out.println("CEO Age (Optional): " + ceoAge);
    System.out.println("CEO is Experienced: " + isExperienced);
    System.out.println("Age Count: " + ageCount);

    // === Employee Analysis ===
    Fold<List<Person>, Person> listFold = Fold.of(list -> list);

    List<String> employeeNames = listFold.andThen(fullName.asFold()).getAll(employees);
    System.out.println("Employee Names: " + employeeNames);

    List<String> employeeEmails = listFold.andThen(email.asFold()).getAll(employees);
    System.out.println("Employee Emails: " + employeeEmails);

    // Calculate average age
    int totalAge =
        listFold.andThen(age.asFold()).foldMap(sumMonoid(), Function.identity(), employees);
    double avgAge = (double) totalAge / employees.size();
    System.out.println("Average Employee Age: " + String.format("%.1f", avgAge));

    // Check if all are in London
    boolean allInLondon =
        listFold
            .andThen(addressGetter.asFold())
            .andThen(cityGetter.asFold())
            .all(c -> c.equals("London"), employees);
    System.out.println("All Employees in London: " + allInLondon);
  }

  private static Monoid<Integer> sumMonoid() {
    return new Monoid<>() {
      @Override
      public Integer empty() {
        return 0;
      }

      @Override
      public Integer combine(Integer a, Integer b) {
        return a + b;
      }
    };
  }
}
// ANCHOR_END: getter_example
