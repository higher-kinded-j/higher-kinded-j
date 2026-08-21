// Fixture for hkj-book/src/optics/ch1_intro.md
//
// The page's payoff snippet composes generated lenses over a three-level record
// graph; the records live here and the annotation processor generates the
// *Lenses companions during snippet compilation.
//
// NOTE: imports in a fixture serve the snippet this file is spliced into (this
// one also happens to use its import itself). Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import org.higherkindedj.optics.annotations.GenerateLenses;

@GenerateLenses
record Street(String name, int number) {}

@GenerateLenses
record Address(Street street, String city) {}

@GenerateLenses
record User(String name, Address address) {}

class Fixture {
  static final User user =
      new User("Ada", new Address(new Street("Fleet Street", 1), "London"));
}
