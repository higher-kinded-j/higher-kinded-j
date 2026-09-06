// Fixture for hkj-book/src/effect/path_validation.md
//
// The page validates one signup form twice: once accumulating into a `List<String>` and once into
// the located `NonEmptyList<FieldError>` that `Path.fields()` builds. Those need different
// validators, so the accumulating pair is `validateX` and the located pair is `parseX` - the
// naming the rest of the book uses for the located form.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.util.List;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;

record User(String name, String email, Integer age) {}

record Signup(String name, String email, int age) {}

class Fixture {

  static final Signup input = new Signup("Ada", "ada@example.com", 36);

  static final Validated<String, User> validatedUser =
      Validated.valid(new User("Ada", "ada@example.com", 36));

  // Accumulating into a plain list of messages.

  static ValidationPath<List<String>, String> validateName(String name) {
    return Path.valid(name, Semigroups.list());
  }

  static ValidationPath<List<String>, String> validateEmail(String email) {
    return Path.valid(email, Semigroups.list());
  }

  static ValidationPath<List<String>, Integer> validateAge(int age) {
    return Path.valid(age, Semigroups.list());
  }

  // Accumulating into located field errors, which is what `Path.fields()` collects.

  static ValidationPath<NonEmptyList<FieldError>, String> parseName(String name) {
    return Path.validNel(name);
  }

  static ValidationPath<NonEmptyList<FieldError>, String> parseEmail(String email) {
    return Path.validNel(email);
  }

  static ValidationPath<NonEmptyList<FieldError>, Integer> parseAge(int age) {
    return Path.validNel(age);
  }
}
