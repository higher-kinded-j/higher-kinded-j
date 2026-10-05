// Fixture for hkj-book/src/optics/ch_intro.md
//
// The page's opening snippet declares its own three records and updates a street
// name through the generated navigators. The records come from the snippet; this
// fixture supplies the imports and the `user` the update reads.
//
// NOTE: imports in a fixture serve the snippet this file is spliced into.
// Spotless excludes src/test/resources so an "unused import" cleanup cannot
// break fixtures (see build.gradle.kts).

import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;

class Fixture {
  static final User user =
      new User("Ada", new Address(new Street("Fleet Street", 1), "London"));
}
