// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.validated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.FieldErrorAssert.assertThatFieldError;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.util.List;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End-to-end runtime tests for {@code @GenerateAssembly} companions (issue #586): annotation
 * processing, generated staged surface, auto-labelling, declaration order, and nesting.
 */
@DisplayName("@GenerateAssembly end-to-end")
class GeneratedAssemblyTest {

  private static List<String> rendered(Validated<NonEmptyList<FieldError>, ?> result) {
    return result.fold(nel -> nel.map(FieldError::toString).toJavaList(), _ -> List.<String>of());
  }

  @Test
  @DisplayName("All components valid: assemble() invokes the canonical constructor")
  void allValid() {
    Validated<NonEmptyList<FieldError>, SignupUser> user =
        SignupUserAssembly.fields()
            .name(Validated.validNel("Ada"))
            .email(Validated.validNel("ada@example.com"))
            .age(Validated.validNel(36))
            .assemble();

    assertThatValidated(user).isValid().hasValue(new SignupUser("Ada", "ada@example.com", 36));
  }

  @Test
  @DisplayName("Every bad component reported, auto-labelled, in declaration order")
  void allErrorsLocatedInOrder() {
    Validated<NonEmptyList<FieldError>, SignupUser> user =
        SignupUserAssembly.fields()
            .name(Validated.invalidNel(FieldError.of("must not be blank")))
            .email(Validated.validNel("ada@example.com"))
            .age(Validated.invalidNel(FieldError.of("not a number")))
            .assemble();

    assertThatValidated(user).isInvalid();
    assertThat(rendered(user)).containsExactly("name: must not be blank", "age: not a number");
  }

  @Test
  @DisplayName("A sub-companion result nests; the outer component name prefixes inner paths")
  void nestingComposes() {
    Validated<NonEmptyList<FieldError>, PostalAddress> address =
        PostalAddressAssembly.fields()
            .street(Validated.validNel("Main St"))
            .zip(Validated.invalidNel(FieldError.of("not a postcode")))
            .assemble();

    Validated<NonEmptyList<FieldError>, AccountHolder> holder =
        AccountHolderAssembly.fields().name(Validated.validNel("Ada")).address(address).assemble();

    assertThatValidated(holder).isInvalid();
    FieldError sole = holder.fold(NonEmptyList::head, _ -> FieldError.of("unexpectedly valid"));
    assertThatFieldError(sole).hasPath("address.zip").hasMessage("not a postcode");
  }

  @Test
  @DisplayName("Agrees with the hand-written fields() builder on the same inputs")
  void agreesWithHandWrittenBuilder() {
    Validated<NonEmptyList<FieldError>, String> name =
        Validated.invalidNel(FieldError.of("bad name"));
    Validated<NonEmptyList<FieldError>, String> email = Validated.validNel("a@b");
    Validated<NonEmptyList<FieldError>, Integer> age =
        Validated.invalidNel(FieldError.of("bad age"));

    Validated<NonEmptyList<FieldError>, SignupUser> generated =
        SignupUserAssembly.fields().name(name).email(email).age(age).assemble();
    Validated<NonEmptyList<FieldError>, SignupUser> handWritten =
        Validated.fields()
            .field("name", name)
            .field("email", email)
            .field("age", age)
            .apply(SignupUser::new);

    assertThat(generated).isEqualTo(handWritten);
  }
}
