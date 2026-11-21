// Fixture for hkj-book/src/release-history/v0_5_0.md
//
// The page opens with the same "New in 0.5.0" band as the home page, whose lead card navigates a
// generated Focus. The records below are the ones home.java declares for it.

import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;

@GenerateLenses
@GenerateFocus
record Street(String name, int number) {}

@GenerateLenses
@GenerateFocus
record Address(Street street, String postcode) {}

@GenerateLenses
@GenerateFocus
record User(String name, Address address) {}

class Fixture {

  static final User user = new User("Ada", new Address(new Street("Old Street", 1), "EC1V 9NR"));
}
