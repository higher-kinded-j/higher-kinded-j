// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

import static org.assertj.core.api.Assertions.assertThat;

import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 02: Lens Composition - Accessing Nested Structures
 *
 * <p>The real power of lenses comes from composition. You can combine lenses to access deeply
 * nested fields without writing verbose update code.
 *
 * <p>Key Concepts: - andThen: composes two lenses into a deeper lens - Composition is associative:
 * a.andThen(b).andThen(c) == a.andThen(b.andThen(c)) - Accessing nested structures becomes
 * type-safe and concise
 *
 * <p>Before Lenses: new Person(p.name(), p.address().city(), new Address(p.address().street(), "New
 * City"))
 *
 * <p>With Lenses: cityLens.set("New City", person)
 */
public class Tutorial02_LensComposition {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  /*
   * ========================================================================
   * IMPORTANT: Manual Lens Implementation (For Educational Purposes Only)
   * ========================================================================
   *
   * In this tutorial, we manually create lenses to demonstrate composition mechanics.
   * This is ONLY for learning - in real projects, NEVER write these manually!
   *
   * What you should do in real projects:
   * ────────────────────────────────────────────────────────────────────────
   * 1. Annotate your records with @GenerateLenses
   * 2. The annotation processor automatically generates all lenses
   * 3. Use the generated lenses directly (e.g., PersonLenses.company())
   *
   * The manual implementations below simulate what @GenerateLenses creates.
   * Understanding lens composition helps you build powerful nested updates!
   */

  @GenerateLenses
  record Address(String street, String city, String zipCode) {}

  @GenerateLenses
  record Company(String name, Address address) {}

  @GenerateLenses
  record Person(String name, int age, Company company) {}

  // Manual lenses (simulating what @GenerateLenses creates - FOR LEARNING ONLY)
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
   * <p>Use andThen to compose lenses that focus deeper into a structure.
   *
   * <p>Task: Create a lens that goes from Person to Company name
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

    // TODO: Replace null with code that composes the two lenses
    // Hint: personToCompany.andThen(companyToName)
    Lens<Person, String> personToCompanyName = answerRequired();

    String companyName = personToCompanyName.get(person);
    assertThat(companyName).isEqualTo("Acme Corp");

    Person updated = personToCompanyName.set("TechCorp", person);
    assertThat(updated.company().name()).isEqualTo("TechCorp");
  }

  /**
   * Exercise 2: Deep composition (three levels)
   *
   * <p>Compose multiple lenses to access deeply nested fields.
   *
   * <p>Task: Create a lens from Person to the city in their company's address
   */
  @Test
  void exercise2_deepComposition() {
    Person person =
        new Person(
            "Alice",
            30,
            new Company("Acme Corp", new Address("123 Main St", "Springfield", "12345")));

    // TODO: Replace null with composed lenses that go:
    // Person -> Company -> Address -> city
    // Hint: Use PersonLenses.company().andThen(...).andThen(...)
    Lens<Person, String> personToCity = answerRequired();

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
   * <p>Use modify with composed lenses to transform nested values.
   *
   * <p>Task: Transform the street address to uppercase
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

    // TODO: Replace null with code that modifies the street to uppercase
    // Hint: personToStreet.modify(String::toUpperCase, person)
    Person updated = answerRequired();

    assertThat(updated.company().address().street()).isEqualTo("123 MAIN ST");
  }

  /**
   * Exercise 4: Multiple updates with composed lenses
   *
   * <p>Chain multiple lens operations to update different nested fields.
   *
   * <p>Task: Update both the company name and city
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

    // TODO: Replace null with chained lens updates:
    // 1. Set company name to "MegaCorp"
    // 2. Set city to "Capital City"
    Person updated = answerRequired();

    assertThat(updated.company().name()).isEqualTo("MegaCorp");
    assertThat(updated.company().address().city()).isEqualTo("Capital City");
  }

  /**
   * Exercise 5: Reusable composed lenses
   *
   * <p>Composed lenses can be saved and reused, avoiding repetition.
   *
   * <p>Task: Create reusable lenses for common access patterns
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

    // TODO: Replace null with a composed lens for Application -> Database -> Config -> host
    Lens<Application, String> appToHost = answerRequired();

    // TODO: Replace null with a composed lens for Application -> Database -> Config -> port
    Lens<Application, Integer> appToPort = answerRequired();

    Application app = new Application("MyApp", new Database("mydb", new Config("localhost", 5432)));

    assertThat(appToHost.get(app)).isEqualTo("localhost");
    assertThat(appToPort.get(app)).isEqualTo(5432);

    Application updated = appToHost.set("192.168.1.100", app);
    assertThat(updated.database().config().host()).isEqualTo("192.168.1.100");
  }

  /**
   * Exercise 6: Lens composition with helper methods
   *
   * <p>The generated *Lenses classes also provide convenient `with*` methods.
   *
   * <p>Task: Compare lens composition with helper methods
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

    // Using helper methods (more discoverable in IDE)
    // TODO: Replace null with the equivalent update using with* helper methods
    // Hint: PersonLenses.withCompany(person, CompanyLenses.with...)
    Person updated2 = answerRequired();

    assertThat(updated1.company().address().city()).isEqualTo("New City");
    assertThat(updated2.company().address().city()).isEqualTo("New City");
  }

  /**
   * Exercise 7: Complex real-world scenario
   *
   * <p>Let's put it all together with a realistic nested structure.
   *
   * <p>Task: Update a user's billing address city
   */
  @Test
  void exercise7_realWorldScenario() {
    @GenerateLenses
    record Address(String street, String city) {}

    @GenerateLenses
    record PaymentInfo(String cardNumber, Address billingAddress) {}

    @GenerateLenses
    record User(String id, String name, PaymentInfo paymentInfo) {}

    User user =
        new User(
            "user123",
            "Alice",
            new PaymentInfo("1234-5678", new Address("456 Oak St", "Springfield")));

    // TODO: Replace null with a composed lens that accesses:
    // User -> PaymentInfo -> billingAddress -> city
    Lens<User, String> billingCityLens = answerRequired();

    User updated = billingCityLens.set("Capital City", user);

    assertThat(updated.paymentInfo().billingAddress().city()).isEqualTo("Capital City");
    assertThat(updated.id()).isEqualTo("user123"); // Everything else unchanged
    assertThat(updated.paymentInfo().cardNumber()).isEqualTo("1234-5678");
  }

  /**
   * Congratulations! You've completed Tutorial 02: Lens Composition
   *
   * <p>You now understand: ✓ How to compose lenses with andThen ✓ How to access deeply nested
   * structures type-safely ✓ How to modify nested values without verbose copying ✓ How to create
   * reusable composed lenses ✓ How composition eliminates boilerplate in complex updates ✓ How to
   * use generated with* helpers as an alternative
   *
   * <p>Next: Tutorial 03 - Prism Basics
   */
}
