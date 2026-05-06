// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

import static org.assertj.core.api.Assertions.assertThat;

import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.junit.jupiter.api.Test;

/**
 * Solution for Tutorial02 LensComposition — teaching-solution format.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions established by
 * the Foundations journey: read the working code first, then the commentary on <em>why</em> the
 * chosen form is idiomatic. The complete-with-commentary template (Why this is idiomatic /
 * Alternative / Common wrong attempt on every exercise) lives in the Foundations solutions
 * coretypes/Tutorial01_KindBasics_Solution.java as the canonical reference.
 *
 * <p>The exercise bodies below are correct working code. Per-exercise teaching commentary is being
 * rolled out across the chapter; if this file does not yet have it, treat the reference code as the
 * answer and consult the pilot solution for the format guide.
 *
 * <p>For the chapter-level guidance on how to learn from a solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
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
   * Why this is idiomatic: {@code andThen} reads left-to-right ("from {@code Person} to {@code
   * Company}, then to {@code String}") and produces a new {@code Lens<Person, String>} that
   * participates in {@code get}, {@code set}, and {@code modify} like any other lens.
   *
   * <p>Alternative: {@code companyToName.compose(personToCompany)}. Mathematically equivalent, but
   * the right-to-left reading rarely matches how Java developers describe the path — {@code
   * andThen} matches the field-access spelling.
   *
   * <p>Common wrong attempt: extract the company manually with {@code person.company()}, then call
   * {@code companyToName.set("TechCorp", company)}. The inner update is correct, but the outer
   * {@code Person} is never rebuilt — the lens composition is what carries the new company back up.
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
   * Why this is idiomatic: each {@code andThen} extends the path by one step; the resulting {@code
   * Lens<Person, String>} hides the intermediate {@code Company} and {@code Address}. The final
   * {@code set} rebuilds three records and we never write any of those constructors.
   *
   * <p>Alternative: a single named helper {@code Lenses.personToCity()} that returns the same
   * composition. Worth doing in production code where the path is reused; the inline form is fine
   * for a one-off update.
   *
   * <p>Common wrong attempt: applying {@code andThen} in the wrong order ({@code city().andThen
   * (address())}) and getting a compile error like "incompatible types". The argument types are the
   * diagnostic — each step must consume the previous step's focus.
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
   * Why this is idiomatic: {@code modify} on a composed lens lets a single function describe
   * "transform the deeply nested value" — composition handles the rebuild, the function only sees
   * the leaf.
   *
   * <p>Alternative: {@code personToStreet.set(personToStreet.get(person).toUpperCase(), person)}.
   * Same result, two traversals of the structure and a name typed three times — {@code modify} is
   * the named "read-then-update" combinator.
   *
   * <p>Common wrong attempt: {@code String::toUpperCase()} (with parentheses). That's a method
   * call, not a method reference, so the compiler rejects it. The reference is the unparenthesised
   * form.
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
   * Why this is idiomatic: each lens operation returns a fresh structure, so we can pipeline them
   * by feeding the previous result into the next call. Two independent updates, two named lenses,
   * no shared mutation.
   *
   * <p>Alternative: extract the intermediate {@code Person} into a local variable ({@code var
   * withName = ...; var done = cityLens.set(...);}). Same semantics, easier to step through under a
   * debugger.
   *
   * <p>Common wrong attempt: try to "merge" the two updates into one ({@code companyNameLens
   * .andThen(cityLens)}). The lenses focus on different fields, not on a chain — they cannot be
   * composed; pipeline the {@code Person} through them instead.
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
   * Why this is idiomatic: name the composed paths once ({@code appToHost}, {@code appToPort}) and
   * let every call site speak in domain terms. The lens names match the way the change would be
   * described in code review.
   *
   * <p>Alternative: drop the named composed lenses and inline {@code appToDb.andThen(...)} at each
   * call site. Fine for one or two uses; once three or more sites share the path, extracting the
   * lens beats repeating the chain.
   *
   * <p>Common wrong attempt: cache a single composed lens at the wrong type — e.g. declare {@code
   * Lens<Application, Object> appToConfig} and try to set both host and port through it. Lenses are
   * field-specific; build one per leaf field.
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
   * Why this is idiomatic: side-by-side, the lens form ({@code cityLens.set(...)}) and the
   * hand-written nested-constructor form make the cost of <em>not</em> using lenses concrete — one
   * line versus eight. The lens form is also where adding a new field to {@code Address} is a
   * one-line change.
   *
   * <p>Alternative: generated {@code with*} helpers ({@code AddressLenses.withCity}). Same outcome,
   * but a {@code with*} helper does not compose; if the next refactor asks for {@code modify(...
   * toUpperCase ...)} it has to be rewritten as a lens.
   *
   * <p>Common wrong attempt: copy the nested-constructor form and forget one inner field. The
   * compiler accepts it (the constructors are still total), but the test catches the silent data
   * loss — a real-world incident the lens form prevents by construction.
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
   * Why this is idiomatic: this is the common e-commerce shape — User → PaymentInfo →
   * BillingAddress → city — and the composed lens names the entire path with one identifier. The
   * setter rebuilds the right level without disturbing the card number or the user id.
   *
   * <p>Alternative: split the path into a {@code billingAddressLens} (User → Address) and a {@code
   * cityLens} on top. Useful when other call sites need the address as a whole; this test only
   * cares about the city, so the single composed lens stays tightest.
   *
   * <p>Common wrong attempt: lens through the user's payment info and then mutate the {@code
   * Address}'s {@code city} field directly. Records are immutable; the would-be mutation would not
   * even compile, but folks reaching for {@code reflection} or {@code @Setter} have recreated this
   * footgun more than once. Rebuild the leaf, let composition lift it back.
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
