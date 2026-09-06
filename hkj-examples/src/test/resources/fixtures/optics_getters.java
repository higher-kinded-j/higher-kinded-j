// Fixture for hkj-book/src/optics/getters.md
//
// The page reads through one people-and-companies model, and later swaps in a smaller Person for
// the null-safety section. Both shapes are the page's own; the fixture declares the one every
// snippet that does not show a model reads through.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.optics.extensions.GetterExtensions.getMaybe;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Getter;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateGetters;

@GenerateGetters
record Address(String street, String city, String zipCode, String country) {}

@GenerateGetters
record Person(String firstName, String lastName, int age, Address address) {}

record Company(String name, Person ceo, List<Person> employees, Address headquarters) {}

record Product(String name, double price) {}

record Order(String id, List<Product> items) {}

record Profile(String displayName) {}

record User(Profile profile) {}

record ApiResponse(User user) {}

record NullableRecord(String value) {}

class Fixture {

  static final Address address = new Address("123 Main St", "London", "NW1", "UK");

  static final Address knownAddress = address;

  static final Person person = new Person("Jane", "Smith", 45, address);

  static final Person ceo = person;

  static final List<Person> employees = List.of(person);

  static final Address headquarters = address;

  static final Company company = new Company("Initech", ceo, employees, headquarters);

  static final Getter<Person, Address> addressGetter = Getter.of(Person::address);

  static final Getter<Address, String> cityGetter = Getter.of(Address::city);

  static final Getter<Person, String> nameGetter = Getter.of(Person::firstName);

  static final Getter<Company, Person> ceoGetter = Getter.of(Company::ceo);

  static final Getter<Address, String> countryGetter = Getter.of(Address::country);

  static final Getter<Person, String> fullName =
      Getter.of(p -> p.firstName() + " " + p.lastName());

  static final Address hqAddress = address;

  static final Person person1 = person;

  static final Person person2 = person;

  static final Order order = new Order("ORD-1", List.of(new Product("Widget", 9.99)));

  static final ApiResponse response = new ApiResponse(new User(new Profile("Jane")));

  static Monoid<Integer> sumMonoid() {
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
