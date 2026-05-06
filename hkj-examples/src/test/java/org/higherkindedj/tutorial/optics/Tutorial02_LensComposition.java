// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

import static org.assertj.core.api.Assertions.assertThat;

import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 02: Lens Composition — accessing deeply nested fields.
 *
 * <p>Pain → Promise. Updating a field three levels deep in immutable records is the canonical
 * imperative-Java horror story:
 *
 * <pre>
 *   Person updated = new Person(
 *       p.name(),
 *       p.email(),
 *       new Address(
 *           new Street("New St", p.address().street().number()),
 *           p.address().city()));
 * </pre>
 *
 * <p>Composed lenses turn the same update into a single line:
 *
 * <pre>
 *   var personToStreetName = PersonLenses.address()
 *       .andThen(AddressLenses.street())
 *       .andThen(StreetLenses.name());
 *   Person updated = personToStreetName.set("New St", p);
 * </pre>
 *
 * <p>Java idiom anchor: {@code andThen} is the same composition idea as {@code Function.andThen} —
 * combining small reusable pieces into deeper transformations. Composition is associative, so we
 * can refactor freely.
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
   * Exercise 1: Composing two lenses.
   *
   * <pre>
   *   // Nudge:    andThen plumbs the second lens through the first.
   *   // Strategy: personToCompany.andThen(companyToName)
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 1: andThen composes two lenses")
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
   * Exercise 2: Deep composition across three levels.
   *
   * <pre>
   *   // Nudge:    Three levels of nesting -&gt; three andThen calls.
   *   // Strategy: PersonLenses.company().andThen(CompanyLenses.address()).andThen(AddressLenses.city())
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 2: deep composition across three levels")
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
   * Exercise 3: Modifying through composed lenses.
   *
   * <pre>
   *   // Nudge:    A composed lens has the same modify method as a simple lens.
   *   // Strategy: personToStreet.modify(String::toUpperCase, person)
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 3: modify through a composed lens")
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
   * Exercise 4: Multiple updates with composed lenses.
   *
   * <pre>
   *   // Nudge:    Two nested updates -&gt; two lens.set calls in sequence.
   *   // Strategy: cityLens.set("Capital City", companyNameLens.set("MegaCorp", person))
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 4: multiple nested updates")
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
   * Exercise 5: Reusable composed lenses.
   *
   * <pre>
   *   // Nudge:    Compose appToDb -&gt; dbToConfig -&gt; configToHost / configToPort once each.
   *   // Strategy: appToDb.andThen(dbToConfig).andThen(configToHost)
   *   // Spoiler:  same shape for the port; just swap the leaf lens.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 5: build reusable composed lenses")
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
   * Exercise 6: Lens composition vs generated {@code with*} helpers.
   *
   * <pre>
   *   // Nudge:    Generated companions expose with* methods that mirror the lens.set call.
   *   // Strategy: PersonLenses.withCompany(person,
   *   //              CompanyLenses.withAddress(person.company(),
   *   //                  AddressLenses.withCity(person.company().address(), "New City")))
   *   // Spoiler:  the helper version is fine for one-off updates; the composed lens reuses
   *   //           better.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 6: composed lens vs generated with* helpers")
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
   * Exercise 7: Real-world nested update — billing address city.
   *
   * <pre>
   *   // Nudge:    Three levels: User -&gt; PaymentInfo -&gt; Address -&gt; city.
   *   // Strategy: UserLenses.paymentInfo()
   *   //               .andThen(PaymentInfoLenses.billingAddress())
   *   //               .andThen(AddressLenses.city())
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 7: real-world nested update")
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
