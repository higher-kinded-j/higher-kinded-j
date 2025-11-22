// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

import static org.assertj.core.api.Assertions.assertThat;

import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.junit.jupiter.api.Test;

/**
 * SOLUTION for Tutorial 02: Lens Composition - Accessing Nested Structures
 *
 * <p>This file contains complete, working solutions for all exercises in
 * Tutorial02_LensComposition.
 */
public class Tutorial02_LensComposition_Solution {

  @GenerateLenses
  record Address(String street, String city, String zipCode) {}

  @GenerateLenses
  record Company(String name, Address address) {}

  @GenerateLenses
  record Person(String name, int age, Company company) {}

  // Manual lenses (annotation processor will generate these in real projects)
  static class PersonLenses {
    public static Lens<Person, Company> company() {
      return Lens.of(Person::company, (p, newCompany) -> new Person(p.name(), p.age(), newCompany));
    }
  }

  static class CompanyLenses {
    public static Lens<Company, String> name() {
      return Lens.of(Company::name, (c, newName) -> new Company(newName, c.address()));
    }

    public static Lens<Company, Address> address() {
      return Lens.of(Company::address, (c, newAddress) -> new Company(c.name(), newAddress));
    }
  }

  static class AddressLenses {
    public static Lens<Address, String> street() {
      return Lens.of(
          Address::street, (a, newStreet) -> new Address(newStreet, a.city(), a.zipCode()));
    }

    public static Lens<Address, String> city() {
      return Lens.of(Address::city, (a, newCity) -> new Address(a.street(), newCity, a.zipCode()));
    }
  }

  /**
   * Exercise 1: Composing two lenses
   *
   * <p>SOLUTION: Use andThen to chain lenses together
   */
  @Test
  void exercise1_composingTwoLenses() {
    Person person =
        new Person(
            "Alice",
            30,
            new Company("Acme Corp", new Address("123 Main St", "Springfield", "12345")));

    Lens<Person, Company> personToCompany = PersonLenses.company();
    Lens<Company, String> companyToName = CompanyLenses.name();

    // SOLUTION: Compose with andThen
    Lens<Person, String> personToCompanyName = personToCompany.andThen(companyToName);

    String companyName = personToCompanyName.get(person);
    assertThat(companyName).isEqualTo("Acme Corp");

    Person updated = personToCompanyName.set("TechCorp", person);
    assertThat(updated.company().name()).isEqualTo("TechCorp");
  }

  /**
   * Exercise 2: Deep composition (three levels)
   *
   * <p>SOLUTION: Chain multiple andThen calls for deep nesting
   */
  @Test
  void exercise2_deepComposition() {
    Person person =
        new Person(
            "Alice",
            30,
            new Company("Acme Corp", new Address("123 Main St", "Springfield", "12345")));

    // SOLUTION: Chain three lenses together
    Lens<Person, String> personToCity =
        PersonLenses.company().andThen(CompanyLenses.address()).andThen(AddressLenses.city());

    String city = personToCity.get(person);
    assertThat(city).isEqualTo("Springfield");

    Person updated = personToCity.set("Shelbyville", person);
    assertThat(updated.company().address().city()).isEqualTo("Shelbyville");
    // Verify everything else is unchanged
    assertThat(updated.name()).isEqualTo("Alice");
    assertThat(updated.company().name()).isEqualTo("Acme Corp");
    assertThat(updated.company().address().street()).isEqualTo("123 Main St");
  }

  /**
   * Exercise 3: Modifying through composed lenses
   *
   * <p>SOLUTION: Use modify on a composed lens
   */
  @Test
  void exercise3_modifyThroughComposition() {
    Person person =
        new Person(
            "Alice",
            30,
            new Company("Acme Corp", new Address("123 Main St", "Springfield", "12345")));

    Lens<Person, String> personToStreet =
        PersonLenses.company().andThen(CompanyLenses.address()).andThen(AddressLenses.street());

    // SOLUTION: modify works on composed lenses just like simple ones
    Person updated = personToStreet.modify(String::toUpperCase, person);

    assertThat(updated.company().address().street()).isEqualTo("123 MAIN ST");
  }

  /**
   * Exercise 4: Multiple updates with composed lenses
   *
   * <p>SOLUTION: Chain multiple lens operations by passing results
   */
  @Test
  void exercise4_multipleNestedUpdates() {
    Person person =
        new Person(
            "Alice",
            30,
            new Company("Acme Corp", new Address("123 Main St", "Springfield", "12345")));

    Lens<Person, String> companyNameLens = PersonLenses.company().andThen(CompanyLenses.name());
    Lens<Person, String> cityLens =
        PersonLenses.company().andThen(CompanyLenses.address()).andThen(AddressLenses.city());

    // SOLUTION: Apply first lens, then apply second lens to the result
    Person updated = cityLens.set("Capital City", companyNameLens.set("MegaCorp", person));

    assertThat(updated.company().name()).isEqualTo("MegaCorp");
    assertThat(updated.company().address().city()).isEqualTo("Capital City");
  }

  /**
   * Exercise 5: Reusable composed lenses
   *
   * <p>SOLUTION: Build up composed lenses by chaining andThen calls
   */
  @Test
  void exercise5_reusableComposedLenses() {
    @GenerateLenses
    record Config(String host, int port) {}

    @GenerateLenses
    record Database(String name, Config config) {}

    @GenerateLenses
    record Application(String name, Database database) {}

    // Manual lens implementations
    class ConfigLenses {
      public static Lens<Config, String> host() {
        return Lens.of(Config::host, (c, newHost) -> new Config(newHost, c.port()));
      }

      public static Lens<Config, Integer> port() {
        return Lens.of(Config::port, (c, newPort) -> new Config(c.host(), newPort));
      }
    }

    class DatabaseLenses {
      public static Lens<Database, Config> config() {
        return Lens.of(Database::config, (d, newConfig) -> new Database(d.name(), newConfig));
      }
    }

    class ApplicationLenses {
      public static Lens<Application, Database> database() {
        return Lens.of(Application::database, (a, newDb) -> new Application(a.name(), newDb));
      }
    }

    // Define reusable composed lenses
    Lens<Application, Database> appToDb = ApplicationLenses.database();
    Lens<Database, Config> dbToConfig = DatabaseLenses.config();
    Lens<Config, String> configToHost = ConfigLenses.host();
    Lens<Config, Integer> configToPort = ConfigLenses.port();

    // SOLUTION: Compose three lenses for host access
    Lens<Application, String> appToHost = appToDb.andThen(dbToConfig).andThen(configToHost);

    // SOLUTION: Compose three lenses for port access
    Lens<Application, Integer> appToPort = appToDb.andThen(dbToConfig).andThen(configToPort);

    Application app = new Application("MyApp", new Database("mydb", new Config("localhost", 5432)));

    assertThat(appToHost.get(app)).isEqualTo("localhost");
    assertThat(appToPort.get(app)).isEqualTo(5432);

    Application updated = appToHost.set("192.168.1.100", app);
    assertThat(updated.database().config().host()).isEqualTo("192.168.1.100");
  }

  /**
   * Exercise 6: Lens composition with helper methods
   *
   * <p>SOLUTION: with* methods provide nested update helpers (Note: Implementation depends on
   * generated code)
   */
  @Test
  void exercise6_helperMethods() {
    Person person =
        new Person(
            "Alice",
            30,
            new Company("Acme Corp", new Address("123 Main St", "Springfield", "12345")));

    // Using composed lens
    Lens<Person, String> cityLens =
        PersonLenses.company().andThen(CompanyLenses.address()).andThen(AddressLenses.city());
    Person updated1 = cityLens.set("New City", person);

    // SOLUTION: Using with* helper methods (nested update pattern)
    // Note: The exact API depends on what @GenerateLenses generates
    // Common pattern: withFieldName(source, newValue)
    Person updated2 =
        new Person(
            person.name(),
            person.age(),
            new Company(
                person.company().name(),
                new Address(
                    person.company().address().street(),
                    "New City",
                    person.company().address().zipCode())));

    assertThat(updated1.company().address().city()).isEqualTo("New City");
    assertThat(updated2.company().address().city()).isEqualTo("New City");
  }

  /**
   * Exercise 7: Complex real-world scenario
   *
   * <p>SOLUTION: Chain lenses to access deeply nested billing address city
   */
  @Test
  void exercise7_realWorldScenario() {
    @GenerateLenses
    record Address(String street, String city) {}

    @GenerateLenses
    record PaymentInfo(String cardNumber, Address billingAddress) {}

    @GenerateLenses
    record User(String id, String name, PaymentInfo paymentInfo) {}

    // Manual lens implementations (annotation processor would generate these classes)
    class AddressLenses {
      public static Lens<Address, String> city() {
        return Lens.of(Address::city, (a, newCity) -> new Address(a.street(), newCity));
      }
    }

    class PaymentInfoLenses {
      public static Lens<PaymentInfo, Address> billingAddress() {
        return Lens.of(
            PaymentInfo::billingAddress, (p, newAddr) -> new PaymentInfo(p.cardNumber(), newAddr));
      }
    }

    class UserLenses {
      public static Lens<User, PaymentInfo> paymentInfo() {
        return Lens.of(
            User::paymentInfo, (u, newPayment) -> new User(u.id(), u.name(), newPayment));
      }
    }

    User user =
        new User(
            "user123",
            "Alice",
            new PaymentInfo("1234-5678", new Address("456 Oak St", "Springfield")));

    // SOLUTION: Compose three lenses to navigate User -> PaymentInfo -> Address -> city
    Lens<User, String> billingCityLens =
        UserLenses.paymentInfo()
            .andThen(PaymentInfoLenses.billingAddress())
            .andThen(AddressLenses.city());

    User updated = billingCityLens.set("Capital City", user);

    assertThat(updated.paymentInfo().billingAddress().city()).isEqualTo("Capital City");
    assertThat(updated.id()).isEqualTo("user123"); // Everything else unchanged
    assertThat(updated.paymentInfo().cardNumber()).isEqualTo("1234-5678");
  }

  /**
   * Congratulations! You've completed Tutorial 02: Lens Composition
   *
   * <p>Key takeaways from the solutions: ✓ andThen composes lenses A -> B and B -> C into A -> C ✓
   * Composition works for any depth of nesting ✓ Composed lenses can be saved and reused ✓ All lens
   * operations (get, set, modify) work on composed lenses ✓ Composition eliminates nested record
   * construction boilerplate
   *
   * <p>Next: Tutorial 03 - Prism Basics
   */
}
