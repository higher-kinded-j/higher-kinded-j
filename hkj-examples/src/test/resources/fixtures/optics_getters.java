// Fixture for hkj-book/src/optics/getters.md
//
// The page reads through one people-and-companies model, and later swaps in a smaller Person for
// the null-safety section. Both shapes are the page's own; the fixture declares the one every
// snippet that does not show a model reads through.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.optics.extensions.GetterExtensions.getMaybe;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Getter;
import org.higherkindedj.optics.annotations.GenerateGetters;

@GenerateGetters
record Address(String street, String city, String zipCode, String country) {}

@GenerateGetters
record Person(String firstName, String lastName, int age, Address address) {}

record Company(String name, Person ceo, List<Person> employees, Address headquarters) {}

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
}
