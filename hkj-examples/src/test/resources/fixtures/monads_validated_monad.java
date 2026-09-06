// Fixture for hkj-book/src/monads/validated_monad.md
//
// One signup form, validated twice: short-circuiting through flatMap and accumulating through
// map3 and the located `fields()` assembly.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.List;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;

record User(String name, String email, Integer age) {}

record UserDetails(String name, String email, String password) {}

record UserDto(String name, String email, String age) {}

record SignupInput(String name, String email, String password) {}

record Order(String id) {}

record Error(String message) {}

class Fixture {

  static final MonadError<ValidatedKind.Witness<List<String>>, List<String>> validatedMonad =
      Instances.validated(Semigroups.list());

  static final Applicative<ValidatedKind.Witness<List<String>>> applicative = validatedMonad;

  static final UserDto dto = new UserDto("Ada", "ada@example.com", "36");

  static final SignupInput input = new SignupInput("Ada", "ada@example.com", "hunter2");

  static final List<Error> errors = List.of();

  static Validated<List<String>, String> validateName(String name) {
    return (name == null || name.isBlank())
        ? Validated.invalid(List.of("Name is required"))
        : Validated.valid(name.trim());
  }

  static Validated<List<String>, String> validateEmail(String email) {
    return Validated.valid(email);
  }

  static Validated<List<String>, Integer> validateAge(int age) {
    return Validated.valid(age);
  }

  static Validated<List<String>, String> validatePassword(String password) {
    return Validated.valid(password);
  }

  static Validated<List<Error>, User> validateUser(SignupInput input) {
    return Validated.valid(new User(input.name(), input.email(), 36));
  }

  static Validated<List<Error>, Order> createOrder(User user) {
    return Validated.valid(new Order("o-1"));
  }

  static ValidationPath<List<Error>, Order> createOrderPath(User user) {
    return Path.valid(new Order("o-1"), Semigroups.list());
  }

  static Validated<NonEmptyList<FieldError>, String> parseName(String name) {
    return Validated.valid(name);
  }

  static Validated<NonEmptyList<FieldError>, String> parseEmail(String email) {
    return Validated.valid(email);
  }

  static Validated<NonEmptyList<FieldError>, Integer> parseAge(String age) {
    return Validated.valid(Integer.parseInt(age));
  }
}
