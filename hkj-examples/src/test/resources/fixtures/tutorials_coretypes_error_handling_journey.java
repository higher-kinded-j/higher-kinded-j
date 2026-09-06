// Fixture for hkj-book/src/tutorials/coretypes/error_handling_journey.md
//
// The troubleshooting entries show one accumulating validation and one wrapped parse; the field
// validators and the input are declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.List;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedMonad;

record User(String name, String email, Integer age) {}

class Fixture {

  static final String name = "Ada";

  static final String email = "ada@example.test";

  static final Integer age = 36;

  static final String input = "42";

  static Validated<List<String>, String> validateName(String name) {
    return Validated.valid(name);
  }

  static Validated<List<String>, String> validateEmail(String email) {
    return Validated.valid(email);
  }

  static Validated<List<String>, Integer> validateAge(Integer age) {
    return Validated.valid(age);
  }
}
