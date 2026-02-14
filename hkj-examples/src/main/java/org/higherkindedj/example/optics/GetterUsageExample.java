// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.util.*;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Getter;
import org.higherkindedj.optics.annotations.GenerateGetters;

/**
 * Comprehensive example demonstrating Getter optics for read-only value extraction.
 *
 * <p>This example showcases:
 *
 * <ul>
 *   <li>Creating Getters from functions for computed/derived values
 *   <li>Composing Getters for deep access into nested structures
 *   <li>Integrating Getters with Folds for complex queries
 *   <li>Using Getters as a Fold to leverage query operations (exists, all, find, etc.)
 *   <li>Real-world use cases in data transformation and reporting
 * </ul>
 *
 * <p>Getter is ideal when you need to:
 *
 * <ul>
 *   <li>Extract derived/computed values without storing them
 *   <li>Create composable read-only accessors
 *   <li>Build data pipelines for reporting and analytics
 * </ul>
 */
public class GetterUsageExample {

  @GenerateGetters
  public record Person(String firstName, String lastName, int age, Address address) {}

  @GenerateGetters
  public record Address(String street, String city, String zipCode, String country) {}

  @GenerateGetters
  public record Company(String name, Person ceo, List<Person> employees, Address headquarters) {}

  public static void main(String[] args) {
    // Create sample data
    var ceoAddress = new Address("123 Executive Blvd", "New York", "10001", "USA");
    var ceo = new Person("Jane", "Smith", 45, ceoAddress);

    var emp1 = new Person("John", "Doe", 30, new Address("456 Oak St", "Boston", "02101", "USA"));
    var emp2 =
        new Person("Alice", "Johnson", 28, new Address("789 Elm Ave", "Chicago", "60601", "USA"));
    var emp3 =
        new Person("Bob", "Williams", 35, new Address("321 Pine Rd", "Seattle", "98101", "USA"));

    var hqAddress = new Address("1000 Corporate Way", "San Francisco", "94105", "USA");
    var company = new Company("TechCorp", ceo, List.of(emp1, emp2, emp3), hqAddress);

    System.out.println("=== GETTER USAGE EXAMPLE ===\n");

    // --- SCENARIO 1: Basic Getter Operations ---
    System.out.println("--- Scenario 1: Basic Getter Operations ---");

    Getter<Person, String> firstNameGetter = Getter.of(Person::firstName);
    Getter<Person, Integer> ageGetter = Getter.of(Person::age);

    System.out.println("CEO first name: " + firstNameGetter.get(ceo));
    System.out.println("CEO age: " + ageGetter.get(ceo));

    // --- SCENARIO 2: Computed/Derived Values ---
    System.out.println("\n--- Scenario 2: Computed/Derived Values ---");

    Getter<Person, String> fullNameGetter = Getter.of(p -> p.firstName() + " " + p.lastName());
    Getter<Person, String> initials =
        Getter.of(p -> p.firstName().charAt(0) + "." + p.lastName().charAt(0) + ".");
    Getter<Person, Boolean> isAdult = Getter.of(p -> p.age() >= 18);
    Getter<Person, String> ageGroup =
        Getter.of(
            p -> {
              if (p.age() < 30) return "Young Professional";
              if (p.age() < 50) return "Experienced Professional";
              return "Senior Professional";
            });

    System.out.println("CEO full name: " + fullNameGetter.get(ceo));
    System.out.println("CEO initials: " + initials.get(ceo));
    System.out.println("CEO is adult: " + isAdult.get(ceo));
    System.out.println("CEO age group: " + ageGroup.get(ceo));

    // --- SCENARIO 3: Getter Composition ---
    System.out.println("\n--- Scenario 3: Getter Composition ---");

    Getter<Person, Address> addressGetter = Getter.of(Person::address);
    Getter<Address, String> cityGetter = Getter.of(Address::city);
    Getter<Address, String> countryGetter = Getter.of(Address::country);

    Getter<Person, String> personCityGetter = addressGetter.andThen(cityGetter);
    Getter<Person, String> personCountryGetter = addressGetter.andThen(countryGetter);

    System.out.println("CEO city: " + personCityGetter.get(ceo));
    System.out.println("CEO country: " + personCountryGetter.get(ceo));

    // Deep composition
    Getter<Company, Person> ceoGetter = Getter.of(Company::ceo);
    Getter<Company, String> companyCeoNameGetter = ceoGetter.andThen(fullNameGetter);
    Getter<Company, String> companyCeoCityGetter = ceoGetter.andThen(personCityGetter);

    System.out.println("Company CEO: " + companyCeoNameGetter.get(company));
    System.out.println("Company CEO city: " + companyCeoCityGetter.get(company));

    // --- SCENARIO 4: Getter as Fold ---
    System.out.println("\n--- Scenario 4: Getter as Fold (Query Operations) ---");

    // Since Getter extends Fold, we can use Fold operations
    Optional<String> ceoName = fullNameGetter.preview(ceo);
    System.out.println("CEO name (via preview): " + ceoName.orElse("Unknown"));

    List<Integer> ages = ageGetter.getAll(emp1);
    System.out.println("Employee 1 age (as list): " + ages);

    boolean ceoIsExperienced = ageGetter.exists(age -> age > 40, ceo);
    System.out.println("CEO is experienced (age > 40): " + ceoIsExperienced);

    boolean ceoNameLong = fullNameGetter.all(name -> name.length() > 5, ceo);
    System.out.println("CEO name is long (> 5 chars): " + ceoNameLong);

    Optional<String> foundName = fullNameGetter.find(name -> name.contains("Smith"), ceo);
    System.out.println("Found name containing 'Smith': " + foundName.orElse("Not found"));

    // --- SCENARIO 5: Combining with Folds for Collections ---
    System.out.println("\n--- Scenario 5: Combining Getters with Folds ---");

    Fold<Company, Person> employeesFold = Fold.of(Company::employees);
    Fold<Company, String> employeeNames = employeesFold.andThen(fullNameGetter.asFold());

    List<String> allEmployeeNames = employeeNames.getAll(company);
    System.out.println("All employee names: " + allEmployeeNames);

    int totalEmployeeAge =
        employeesFold.andThen(ageGetter.asFold()).foldMap(sumMonoid(), x -> x, company);
    System.out.println("Total employee age: " + totalEmployeeAge);

    double averageAge = (double) totalEmployeeAge / company.employees().size();
    System.out.println("Average employee age: " + String.format("%.1f", averageAge));

    // Check if all employees are from USA
    Fold<Company, String> employeeCountries = employeesFold.andThen(personCountryGetter.asFold());
    boolean allFromUSA = employeeCountries.all(c -> c.equals("USA"), company);
    System.out.println("All employees from USA: " + allFromUSA);

    // Find youngest employee
    Optional<Person> youngest = employeesFold.find(p -> p.age() < 30, company);
    System.out.println(
        "Youngest employee (< 30): " + youngest.map(fullNameGetter::get).orElse("None"));

    // --- SCENARIO 6: Data Transformation Pipeline ---
    System.out.println("\n--- Scenario 6: Data Transformation Pipeline ---");

    Getter<Person, String> emailGetter =
        Getter.of(
            p -> p.firstName().toLowerCase() + "." + p.lastName().toLowerCase() + "@techcorp.com");

    Getter<Person, String> badgeIdGetter =
        Getter.of(
            p ->
                p.lastName().substring(0, Math.min(3, p.lastName().length())).toUpperCase()
                    + String.format("%04d", p.age() * 100));

    System.out.println("Employee emails:");
    for (Person emp : company.employees()) {
      System.out.println("  - " + fullNameGetter.get(emp) + ": " + emailGetter.get(emp));
    }

    System.out.println("\nEmployee badge IDs:");
    for (Person emp : company.employees()) {
      System.out.println("  - " + fullNameGetter.get(emp) + ": " + badgeIdGetter.get(emp));
    }

    // --- SCENARIO 7: Helper Getters ---
    System.out.println("\n--- Scenario 7: Built-in Helper Getters ---");

    Getter<String, String> identity = Getter.identity();
    System.out.println("Identity getter: " + identity.get("Hello World"));

    Getter<String, Integer> constant42 = Getter.constant(42);
    System.out.println("Constant getter: " + constant42.get("anything"));

    Map.Entry<Person, Address> pair = new AbstractMap.SimpleEntry<>(ceo, hqAddress);
    Getter<Map.Entry<Person, Address>, Person> firstGetter = Getter.first();
    Getter<Map.Entry<Person, Address>, Address> secondGetter = Getter.second();

    System.out.println("Pair first (person): " + fullNameGetter.get(firstGetter.get(pair)));
    System.out.println("Pair second (address city): " + cityGetter.get(secondGetter.get(pair)));

    System.out.println("\n=== GETTER USAGE EXAMPLE COMPLETE ===");
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
