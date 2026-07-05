// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.validated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.util.Optional;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.laws.ValidatedPrismLaws;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ValidatedPrism - a Prism whose match accumulates reasons")
class ValidatedPrismTest {

  record Email(String value) {}

  record CorpUser(String name) {}

  private static Validated<NonEmptyList<FieldError>, Email> parseEmail(String raw) {
    return raw.contains("@")
        ? Validated.validNel(new Email(raw))
        : Validated.invalidNel(FieldError.of("not an email address"));
  }

  private static final ValidatedPrism<String, Email> EMAIL =
      ValidatedPrism.of(ValidatedPrismTest::parseEmail, Email::value);

  private static final ValidatedPrism<Email, CorpUser> CORP =
      ValidatedPrism.of(
          email ->
              email.value().endsWith("@corp.example")
                  ? Validated.validNel(new CorpUser(email.value().split("@")[0]))
                  : Validated.invalidNel(FieldError.of("not a corp address")),
          user -> new Email(user.name() + "@corp.example"));

  @Nested
  @DisplayName("Core surface")
  class CoreSurface {

    @Test
    @DisplayName("parse reports located reasons; build is total; the laws hold")
    void parseBuildAndLaws() {
      assertThatValidated(EMAIL.parse("ada@corp.example"))
          .isValid()
          .hasValue(new Email("ada@corp.example"));
      assertThat(EMAIL.build(new Email("x@y"))).isEqualTo("x@y");

      Validated<NonEmptyList<FieldError>, Email> failed = EMAIL.parse("nope");
      assertThatValidated(failed).isInvalid();
      assertThat(failed.getError().head()).hasToString("not an email address");

      ValidatedPrismLaws.assertValidatedPrismLaws(EMAIL, "ada@corp.example", "nope");
      ValidatedPrismLaws.assertValidatedPrismLaws(
          CORP, new Email("ada@corp.example"), new Email("ada@other.example"));
    }

    @Test
    @DisplayName("parsePath mirrors parse on the railway")
    void parsePathMirrorsParse() {
      assertThat(EMAIL.parsePath("a@b").run()).isEqualTo(EMAIL.parse("a@b"));
      assertThat(EMAIL.parsePath("nope").run()).isEqualTo(EMAIL.parse("nope"));
    }

    @Test
    @DisplayName("the Of leaf guards its inputs and outputs")
    void leafGuards() {
      assertThatNullPointerException()
          .isThrownBy(() -> ValidatedPrism.of(null, Email::value))
          .withMessage("parse must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> ValidatedPrism.of(ValidatedPrismTest::parseEmail, null))
          .withMessage("build must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.parse(null))
          .withMessage("source must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.build(null))
          .withMessage("value must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> ValidatedPrism.<String, Email>of(s -> null, Email::value).parse("x"))
          .withMessage("parse must not return null");

      ValidatedPrism.Of<String, Email> leaf =
          new ValidatedPrism.Of<>(ValidatedPrismTest::parseEmail, Email::value);
      assertThat(leaf.parseFn()).isNotNull();
      assertThat(leaf.buildFn()).isNotNull();
    }
  }

  @Nested
  @DisplayName("Composition - only build-preserving cells, short-circuiting on nesting")
  class Composition {

    @Test
    @DisplayName("andThen(ValidatedPrism) parses outer then inner, and builds back through both")
    void andThenValidatedPrism() {
      ValidatedPrism<String, CorpUser> corpEmail = EMAIL.andThen(CORP);

      assertThatValidated(corpEmail.parse("ada@corp.example"))
          .isValid()
          .hasValue(new CorpUser("ada"));
      assertThat(corpEmail.build(new CorpUser("ada"))).isEqualTo("ada@corp.example");
      ValidatedPrismLaws.assertValidatedPrismLaws(
          corpEmail, "ada@corp.example", "ada@other.example");
    }

    @Test
    @DisplayName("nesting short-circuits: the inner parse never sees an outer failure")
    void nestingShortCircuits() {
      ValidatedPrism<Email, CorpUser> exploding =
          ValidatedPrism.of(
              email -> {
                throw new AssertionError("inner parse must not run after an outer failure");
              },
              user -> new Email(user.name()));

      Validated<NonEmptyList<FieldError>, CorpUser> outerFailure =
          EMAIL.andThen(exploding).parse("nope");

      assertThatValidated(outerFailure).isInvalid();
      assertThat(outerFailure.getError().head()).hasToString("not an email address");
    }

    @Test
    @DisplayName("andThen(Iso) maps the parse and round-trips the build")
    void andThenIso() {
      Iso<Email, String> unwrap = Iso.of(Email::value, Email::new);
      ValidatedPrism<String, String> asText = EMAIL.andThen(unwrap);

      assertThatValidated(asText.parse("a@b")).isValid().hasValue("a@b");
      assertThat(asText.build("a@b")).isEqualTo("a@b");
      ValidatedPrismLaws.assertValidatedPrismLaws(asText, "a@b", "nope");
    }

    @Test
    @DisplayName("andThen(Prism, reason) supplies the reason a plain prism cannot express")
    void andThenPrismWithReason() {
      Prism<Email, CorpUser> corpPrism =
          Prism.of(
              email ->
                  email.value().endsWith("@corp.example")
                      ? Optional.of(new CorpUser(email.value().split("@")[0]))
                      : Optional.empty(),
              user -> new Email(user.name() + "@corp.example"));

      ValidatedPrism<String, CorpUser> composed =
          EMAIL.andThen(corpPrism, FieldError.of("not a corp address"));

      assertThatValidated(composed.parse("ada@corp.example"))
          .isValid()
          .hasValue(new CorpUser("ada"));
      Validated<NonEmptyList<FieldError>, CorpUser> refused = composed.parse("ada@other.example");
      assertThatValidated(refused).isInvalid();
      assertThat(refused.getError().head()).hasToString("not a corp address");
      assertThat(composed.build(new CorpUser("ada"))).isEqualTo("ada@corp.example");
    }

    @Test
    @DisplayName("composition factories eagerly reject nulls")
    void compositionGuards() {
      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.andThen((ValidatedPrism<Email, CorpUser>) null))
          .withMessage("other must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.andThen((Iso<Email, String>) null))
          .withMessage("iso must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.andThen(null, FieldError.of("r")))
          .withMessage("prism must not be null");
    }
  }

  @Nested
  @DisplayName("Conversions to and from the existing lattice")
  class Conversions {

    @Test
    @DisplayName("fromIso never fails; fromPrism supplies the missing reason")
    void lifts() {
      ValidatedPrism<String, Email> viaIso =
          ValidatedPrism.fromIso(Iso.of(Email::new, Email::value));
      assertThatValidated(viaIso.parse("anything")).isValid().hasValue(new Email("anything"));
      ValidatedPrismLaws.assertParseBuild(viaIso, new Email("x"));

      Prism<String, Email> plain =
          Prism.of(
              s -> s.contains("@") ? Optional.of(new Email(s)) : Optional.empty(), Email::value);
      ValidatedPrism<String, Email> lifted =
          ValidatedPrism.fromPrism(plain, FieldError.of("not an email address"));
      ValidatedPrismLaws.assertValidatedPrismLaws(lifted, "a@b", "nope");
      assertThat(lifted.parse("nope").getError().head()).hasToString("not an email address");
    }

    @Test
    @DisplayName(
        "toPrism and toAffine forget the reasons; the affine set skips non-parsing sources")
    void forgets() {
      Prism<String, Email> forgotten = EMAIL.toPrism();
      assertThat(forgotten.getOptional("a@b")).contains(new Email("a@b"));
      assertThat(forgotten.getOptional("nope")).isEmpty();
      assertThat(forgotten.build(new Email("a@b"))).isEqualTo("a@b");

      Affine<String, Email> affine = EMAIL.toAffine();
      assertThat(affine.getOptional("a@b")).contains(new Email("a@b"));
      assertThat(affine.getOptional("nope")).isEmpty();
      assertThat(affine.set(new Email("x@y"), "a@b")).isEqualTo("x@y");
      assertThat(affine.set(new Email("x@y"), "nope")).isEqualTo("nope");
      assertThatNullPointerException()
          .isThrownBy(() -> affine.set(null, "nope"))
          .withMessage("value must not be null");
    }
  }
}
