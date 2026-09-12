// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.validated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.time.Month;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
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
    @DisplayName("parseAll accumulates failures across all elements; buildAll is total")
    void parseAllAndBuildAll() {
      assertThatValidated(EMAIL.parseAll(List.of("a@b", "c@d")))
          .isValid()
          .hasValue(List.of(new Email("a@b"), new Email("c@d")));

      ValidatedPrism<String, Email> located =
          ValidatedPrism.of(
              raw ->
                  raw.contains("@")
                      ? Validated.validNel(new Email(raw))
                      : Validated.invalidNel(FieldError.of("bad: " + raw)),
              Email::value);
      Validated<NonEmptyList<FieldError>, List<Email>> failed =
          located.parseAll(List.of("bad-one", "a@b", "bad-two"));
      assertThatValidated(failed).isInvalid();
      // Element failures locate by index, a plain positional segment like a map key.
      assertThat(failed.getError().toJavaList())
          .containsExactly(
              new FieldError(List.of("0"), "bad: bad-one"),
              new FieldError(List.of("2"), "bad: bad-two"));

      assertThat(EMAIL.buildAll(List.of(new Email("a@b"), new Email("c@d"))))
          .containsExactly("a@b", "c@d");
      assertThatValidated(EMAIL.parseAll(List.of())).isValid().hasValue(List.of());
      // The container forms are overloaded, so a bare null needs the type it stands for.
      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.parseAll((List<String>) null))
          .withMessage("sources must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.buildAll((List<Email>) null))
          .withMessage("values must not be null");
      // A null element is a located invalid at its index, never an exception; the
      // build direction stays total-and-throwing.
      Validated<NonEmptyList<FieldError>, List<Email>> nullElement =
          EMAIL.parseAll(Arrays.asList("a@b", null));
      assertThatValidated(nullElement).isInvalid();
      assertThat(nullElement.getError().toJavaList())
          .containsExactly(new FieldError(List.of("1"), "must not be null"));

      // Accumulation continues past a null: failures before AND after it all report, in order.
      Validated<NonEmptyList<FieldError>, List<Email>> mixed =
          located.parseAll(Arrays.asList("a@b", null, "bad-three"));
      assertThatValidated(mixed).isInvalid();
      assertThat(mixed.getError().toJavaList())
          .containsExactly(
              new FieldError(List.of("1"), "must not be null"),
              new FieldError(List.of("2"), "bad: bad-three"));
      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.buildAll(Arrays.asList(new Email("a@b"), null)))
          .withMessage("values[1] must not be null");
    }

    @Test
    @DisplayName("parseValues accumulates failures across all entries, each located by its key")
    void parseValuesAccumulatesByKey() {
      Map<String, String> wire = new LinkedHashMap<>();
      wire.put("work", "a@b");
      wire.put("home", "c@d");
      Validated<NonEmptyList<FieldError>, Map<String, Email>> parsed = EMAIL.parseValues(wire);
      assertThatValidated(parsed).isValid();
      assertThat(parsed.get())
          .containsExactly(entry("work", new Email("a@b")), entry("home", new Email("c@d")));
      assertThatThrownBy(() -> parsed.get().put("x", new Email("x@y")))
          .isInstanceOf(UnsupportedOperationException.class);

      Map<String, String> bad = new LinkedHashMap<>();
      bad.put("work", "bad-one");
      bad.put("home", "a@b");
      bad.put("other", "bad-two");
      Validated<NonEmptyList<FieldError>, Map<String, Email>> failed = EMAIL.parseValues(bad);
      assertThatValidated(failed).isInvalid();
      assertThat(failed.getError().toJavaList())
          .containsExactly(
              new FieldError(List.of("work"), "not an email address"),
              new FieldError(List.of("other"), "not an email address"));

      // Non-string keys locate through their toString rendering.
      Validated<NonEmptyList<FieldError>, Map<Integer, Email>> numbered =
          EMAIL.parseValues(Map.of(7, "nope"));
      assertThatValidated(numbered).isInvalid();
      assertThat(numbered.getError().head())
          .isEqualTo(new FieldError(List.of("7"), "not an email address"));

      assertThatValidated(EMAIL.parseValues(Map.of())).isValid().hasValue(Map.of());
      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.parseValues(null))
          .withMessage("sources must not be null");
      Map<String, String> nullKey = new HashMap<>();
      nullKey.put(null, "a@b");
      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.parseValues(nullKey))
          .withMessage("sources must not contain a null key");
      // A null value is a located invalid under its key, never an exception; a null
      // key stays the caller's error — a structurally broken map, not a wrong value.
      Map<String, String> nullValue = new HashMap<>();
      nullValue.put("work", null);
      Validated<NonEmptyList<FieldError>, Map<String, Email>> locatedNull =
          EMAIL.parseValues(nullValue);
      assertThatValidated(locatedNull).isInvalid();
      assertThat(locatedNull.getError().toJavaList())
          .containsExactly(new FieldError(List.of("work"), "must not be null"));

      // Accumulation continues past a null value: the key-located parse failure still reports.
      Map<String, String> mixed = new LinkedHashMap<>();
      mixed.put("work", null);
      mixed.put("home", "nope");
      Validated<NonEmptyList<FieldError>, Map<String, Email>> mixedValues =
          EMAIL.parseValues(mixed);
      assertThatValidated(mixedValues).isInvalid();
      assertThat(mixedValues.getError().toJavaList())
          .containsExactly(
              new FieldError(List.of("work"), "must not be null"),
              new FieldError(List.of("home"), "not an email address"));
    }

    @Test
    @DisplayName("buildValues is total, preserving entry order and rejecting nulls")
    void buildValuesIsTotal() {
      Map<String, Email> domain = new LinkedHashMap<>();
      domain.put("work", new Email("a@b"));
      domain.put("home", new Email("c@d"));
      Map<String, String> built = EMAIL.buildValues(domain);
      assertThat(built).containsExactly(entry("work", "a@b"), entry("home", "c@d"));
      assertThatThrownBy(() -> built.put("x", "x@y"))
          .isInstanceOf(UnsupportedOperationException.class);

      assertThat(EMAIL.buildValues(Map.of())).isEmpty();
      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.buildValues(null))
          .withMessage("values must not be null");
      Map<String, Email> nullKey = new HashMap<>();
      nullKey.put(null, new Email("a@b"));
      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.buildValues(nullKey))
          .withMessage("values must not contain a null key");
      Map<String, Email> nullValue = new HashMap<>();
      nullValue.put("home", null);
      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.buildValues(nullValue))
          .withMessage("values[home] must not be null");
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
  @DisplayName("Bulk forms over sets, arrays and map keys")
  class WidenedBulkForms {

    /** A prism whose reason names the offending source, so locations are unambiguous. */
    private static final ValidatedPrism<String, Email> LOCATED =
        ValidatedPrism.of(
            raw ->
                raw.contains("@")
                    ? Validated.validNel(new Email(raw))
                    : Validated.invalidNel(FieldError.of("bad: " + raw)),
            Email::value);

    /** Normalises, so two distinct sources can parse to one domain value. */
    private static final ValidatedPrism<String, Email> LOWERCASING =
        ValidatedPrism.of(
            raw -> Validated.validNel(new Email(raw.toLowerCase(Locale.ROOT))), Email::value);

    @Test
    @DisplayName("parseAll(Set) locates each failure by the source element's rendering")
    void parseAllSetLocatesByElement() {
      Set<String> wire = new LinkedHashSet<>(List.of("a@b", "c@d"));
      assertThatValidated(LOCATED.parseAll(wire))
          .isValid()
          .hasValue(new LinkedHashSet<>(List.of(new Email("a@b"), new Email("c@d"))));

      Validated<NonEmptyList<FieldError>, Set<Email>> failed =
          LOCATED.parseAll(new LinkedHashSet<>(List.of("bad-one", "a@b", "bad-two")));
      assertThatValidated(failed).isInvalid();
      // A set has no index, so the element's own rendering is what identifies it.
      assertThat(failed.getError().toJavaList())
          .containsExactly(
              new FieldError(List.of("bad-one"), "bad: bad-one"),
              new FieldError(List.of("bad-two"), "bad: bad-two"));

      assertThatValidated(LOCATED.parseAll(Set.<String>of())).isValid().hasValue(Set.of());
      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.parseAll((Set<String>) null))
          .withMessage("sources must not be null");
    }

    @Test
    @DisplayName(
        "a null set element is unlocated: a set holds at most one, and it has no rendering")
    void parseAllSetNullElementIsUnlocated() {
      Set<String> withNull = new LinkedHashSet<>(List.of("a@b"));
      withNull.add(null);
      Validated<NonEmptyList<FieldError>, Set<Email>> parsed = EMAIL.parseAll(withNull);
      assertThatValidated(parsed).isInvalid();
      assertThat(parsed.getError().toJavaList())
          .containsExactly(new FieldError(List.of(), "must not contain a null element"));

      // Accumulation continues past the null, and the two reasons stay distinguishable from
      // 'must not be null', which says the set itself is absent.
      Set<String> mixed = new LinkedHashSet<>(List.of("bad-one"));
      mixed.add(null);
      assertThat(LOCATED.parseAll(mixed).getError().toJavaList())
          .containsExactly(
              new FieldError(List.of("bad-one"), "bad: bad-one"),
              new FieldError(List.of(), "must not contain a null element"));
    }

    @Test
    @DisplayName("a set collapses silently in both directions: the survivors are equal")
    void setCollapsesSilently() {
      Set<String> wire = new LinkedHashSet<>(List.of("A@B", "a@b"));
      assertThatValidated(LOWERCASING.parseAll(wire)).isValid().hasValue(Set.of(new Email("a@b")));

      ValidatedPrism<String, Email> constant =
          ValidatedPrism.of(raw -> Validated.validNel(new Email(raw)), _ -> "same");
      assertThat(constant.buildAll(new LinkedHashSet<>(List.of(new Email("x"), new Email("y")))))
          .containsExactly("same");
    }

    @Test
    @DisplayName("buildAll(Set) is total, preserves iteration order and rejects nulls")
    void buildAllSetIsTotal() {
      Set<Email> domain = new LinkedHashSet<>(List.of(new Email("c@d"), new Email("a@b")));
      assertThat(EMAIL.buildAll(domain)).containsExactly("c@d", "a@b");
      assertThat(EMAIL.buildAll(Set.<Email>of())).isEmpty();
      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.buildAll((Set<Email>) null))
          .withMessage("values must not be null");
      Set<Email> withNull = new LinkedHashSet<>(List.of(new Email("a@b")));
      withNull.add(null);
      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.buildAll(withNull))
          .withMessage("values must not contain a null element");
    }

    @Test
    @DisplayName("parseAll(array) locates by index, exactly as the list form does")
    void parseAllArrayLocatesByIndex() {
      assertThat(LOCATED.parseAll(new String[] {"a@b", "c@d"}, Email[]::new).get())
          .containsExactly(new Email("a@b"), new Email("c@d"));

      Validated<NonEmptyList<FieldError>, Email[]> failed =
          LOCATED.parseAll(new String[] {"bad-one", "a@b", null}, Email[]::new);
      assertThatValidated(failed).isInvalid();
      assertThat(failed.getError().toJavaList())
          .containsExactly(
              new FieldError(List.of("0"), "bad: bad-one"),
              new FieldError(List.of("2"), "must not be null"));

      assertThat(EMAIL.parseAll(new String[] {}, Email[]::new).get()).isEmpty();
      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.parseAll((String[]) null, Email[]::new))
          .withMessage("sources must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.parseAll(new String[] {"a@b"}, null))
          .withMessage("newArray must not be null");
    }

    @Test
    @DisplayName("buildAll(array) is total and rejects nulls by index")
    void buildAllArrayIsTotal() {
      assertThat(EMAIL.buildAll(new Email[] {new Email("a@b"), new Email("c@d")}, String[]::new))
          .containsExactly("a@b", "c@d");
      assertThat(EMAIL.buildAll(new Email[] {}, String[]::new)).isEmpty();
      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.buildAll((Email[]) null, String[]::new))
          .withMessage("values must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.buildAll(new Email[] {new Email("a@b")}, null))
          .withMessage("newArray must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.buildAll(new Email[] {new Email("a@b"), null}, String[]::new))
          .withMessage("values[1] must not be null");
    }

    @Test
    @DisplayName("parseKeys converts the keys, locating each failure by its source key")
    void parseKeysLocatesBySourceKey() {
      Map<String, Integer> wire = new LinkedHashMap<>();
      wire.put("a@b", 1);
      wire.put("c@d", 2);
      Validated<NonEmptyList<FieldError>, Map<Email, Integer>> parsed = LOCATED.parseKeys(wire);
      assertThatValidated(parsed).isValid();
      assertThat(parsed.get())
          .containsExactly(entry(new Email("a@b"), 1), entry(new Email("c@d"), 2));

      Map<String, Integer> bad = new LinkedHashMap<>();
      bad.put("bad-one", 1);
      bad.put("a@b", 2);
      bad.put("bad-two", 3);
      assertThat(LOCATED.parseKeys(bad).getError().toJavaList())
          .containsExactly(
              new FieldError(List.of("bad-one"), "bad: bad-one"),
              new FieldError(List.of("bad-two"), "bad: bad-two"));

      assertThatValidated(EMAIL.parseKeys(Map.<String, Integer>of())).isValid().hasValue(Map.of());
      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.parseKeys(null))
          .withMessage("sources must not be null");
      Map<String, Integer> nullKey = new HashMap<>();
      nullKey.put(null, 1);
      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.parseKeys(nullKey))
          .withMessage("sources must not contain a null key");
    }

    @Test
    @DisplayName("colliding domain keys are a located failure, not a silent dropped entry")
    void parseKeysRejectsCollidingKeys() {
      Map<String, Integer> wire = new LinkedHashMap<>();
      wire.put("A@B", 1);
      wire.put("a@b", 2);
      Validated<NonEmptyList<FieldError>, Map<Email, Integer>> parsed = LOWERCASING.parseKeys(wire);
      assertThatValidated(parsed).isInvalid();
      assertThat(parsed.getError().toJavaList())
          .containsExactly(new FieldError(List.of("a@b"), "duplicates an earlier key"));

      // A null value is located under its source key too: the doctrine reaches inside a
      // container whether the contents convert or are copied.
      Map<String, Integer> nullValue = new LinkedHashMap<>();
      nullValue.put("a@b", null);
      assertThat(EMAIL.parseKeys(nullValue).getError().toJavaList())
          .containsExactly(new FieldError(List.of("a@b"), "must not be null"));

      // A key that fails AND a null value both report, at the one location.
      Map<String, Integer> both = new LinkedHashMap<>();
      both.put("bad-one", null);
      assertThat(LOCATED.parseKeys(both).getError().toJavaList())
          .containsExactly(
              new FieldError(List.of("bad-one"), "bad: bad-one"),
              new FieldError(List.of("bad-one"), "must not be null"));
    }

    @Test
    @DisplayName("parseEntries converts both sides, accumulating under the source key")
    void parseEntriesConvertsBothSides() {
      Map<String, String> wire = new LinkedHashMap<>();
      wire.put("a@b", "x@y");
      Validated<NonEmptyList<FieldError>, Map<Email, Email>> parsed =
          LOCATED.parseEntries(wire, LOCATED);
      assertThatValidated(parsed).isValid();
      assertThat(parsed.get()).containsExactly(entry(new Email("a@b"), new Email("x@y")));

      Map<String, String> both = new LinkedHashMap<>();
      both.put("bad-key", "bad-value");
      both.put("a@b", null);
      assertThat(LOCATED.parseEntries(both, LOCATED).getError().toJavaList())
          .containsExactly(
              new FieldError(List.of("bad-key"), "bad: bad-key"),
              new FieldError(List.of("bad-key"), "bad: bad-value"),
              new FieldError(List.of("a@b"), "must not be null"));

      Map<String, String> colliding = new LinkedHashMap<>();
      colliding.put("A@B", "x@y");
      colliding.put("a@b", "x@y");
      assertThat(LOWERCASING.parseEntries(colliding, LOCATED).getError().toJavaList())
          .containsExactly(new FieldError(List.of("a@b"), "duplicates an earlier key"));

      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.parseEntries(null, EMAIL))
          .withMessage("sources must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.parseEntries(Map.of("a@b", "x@y"), null))
          .withMessage("valuePrism must not be null");
      Map<String, String> nullKey = new HashMap<>();
      nullKey.put(null, "x@y");
      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.parseEntries(nullKey, EMAIL))
          .withMessage("sources must not contain a null key");
    }

    @Test
    @DisplayName("buildKeys and buildEntries are total, preserving entry order")
    void buildKeysAndEntriesAreTotal() {
      Map<Email, Integer> domain = new LinkedHashMap<>();
      domain.put(new Email("c@d"), 2);
      domain.put(new Email("a@b"), 1);
      assertThat(EMAIL.buildKeys(domain)).containsExactly(entry("c@d", 2), entry("a@b", 1));
      assertThat(EMAIL.buildKeys(Map.<Email, Integer>of())).isEmpty();

      Map<Email, Email> entries = new LinkedHashMap<>();
      entries.put(new Email("a@b"), new Email("x@y"));
      assertThat(EMAIL.buildEntries(entries, EMAIL)).containsExactly(entry("a@b", "x@y"));

      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.buildKeys(null))
          .withMessage("values must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.buildEntries(null, EMAIL))
          .withMessage("values must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.buildEntries(entries, null))
          .withMessage("valuePrism must not be null");

      Map<Email, Integer> nullKey = new HashMap<>();
      nullKey.put(null, 1);
      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.buildKeys(nullKey))
          .withMessage("values must not contain a null key");
      // A pass-through value is still a value: parseKeys rejects a null one, so rendering it
      // would build a wire this same prism refuses to read back.
      Map<Email, Integer> nullValue = new LinkedHashMap<>();
      nullValue.put(new Email("a@b"), null);
      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.buildKeys(nullValue))
          .withMessage("values[Email[value=a@b]] must not be null");
      Map<Email, Email> nullEntryKey = new HashMap<>();
      nullEntryKey.put(null, new Email("x@y"));
      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.buildEntries(nullEntryKey, EMAIL))
          .withMessage("values must not contain a null key");
      Map<Email, Email> nullEntryValue = new LinkedHashMap<>();
      nullEntryValue.put(new Email("a@b"), null);
      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.buildEntries(nullEntryValue, EMAIL))
          .withMessage("values[Email[value=a@b]] must not be null");
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
  @DisplayName("canonical - the section law by construction")
  class CanonicalFactory {

    private final ValidatedPrism<String, UUID> upperUuid =
        ValidatedPrism.canonical(
            "not an uppercase UUID",
            UUID::fromString,
            uuid -> uuid.toString().toUpperCase(Locale.ROOT));

    @Test
    @DisplayName("a custom canon obeys both laws; the leniently-parseable spelling is rejected")
    void customCanonObeysLaws() {
      ValidatedPrismLaws.assertValidatedPrismLaws(
          upperUuid,
          "123E4567-E89B-12D3-A456-426614174000",
          "123e4567-e89b-12d3-a456-426614174000");
      // UUID.fromString would happily parse the lowercase spelling; the guard rejects what the
      // render cannot reproduce, so lowercase is a located rejection, not a normalisation.
      Validated<NonEmptyList<FieldError>, UUID> folded =
          upperUuid.parse("123e4567-e89b-12d3-a456-426614174000");
      assertThatValidated(folded).isInvalid();
      assertThat(folded.getError().head()).hasToString("not an uppercase UUID");
    }

    @Test
    @DisplayName("a throwing parse is the same located rejection, never an exception")
    void throwingParseIsLocated() {
      Validated<NonEmptyList<FieldError>, UUID> garbage = upperUuid.parse("not-a-uuid");
      assertThatValidated(garbage).isInvalid();
      assertThat(garbage.getError().head()).hasToString("not an uppercase UUID");
    }

    @Test
    @DisplayName("a render failing on a value the lenient parse produced is a located rejection")
    void renderFailureIsLocated() {
      ValidatedPrism<String, String> fussyRender =
          ValidatedPrism.canonical(
              "not renderable",
              raw -> raw,
              value -> {
                if (value.isEmpty()) {
                  throw new IllegalStateException("nothing to render");
                }
                return value;
              });
      assertThatValidated(fussyRender.parse("")).isInvalid();
      assertThatValidated(fussyRender.parse("ok")).isValid().hasValue("ok");
    }

    @Test
    @DisplayName("the FieldError overload carries the located reason through unchanged")
    void fieldErrorOverload() {
      FieldError located = FieldError.of("not an uppercase UUID").at("id");
      ValidatedPrism<String, UUID> prism =
          ValidatedPrism.canonical(
              located, UUID::fromString, uuid -> uuid.toString().toUpperCase(Locale.ROOT));
      ValidatedPrismLaws.assertValidatedPrismLaws(
          prism, "123E4567-E89B-12D3-A456-426614174000", "nope");
      assertThat(prism.parse("nope").getError().head()).isEqualTo(located);
    }

    @Test
    @DisplayName("generic in the source type: the guard is render-equals-source, nothing more")
    void genericInSource() {
      ValidatedPrism<Integer, Month> month =
          ValidatedPrism.canonical("not a month number", Month::of, Month::getValue);
      ValidatedPrismLaws.assertValidatedPrismLaws(month, 7, 13);
      assertThat(month.build(Month.JULY)).isEqualTo(7);
    }

    @Test
    @DisplayName("both overloads eagerly reject nulls")
    void nullGuards() {
      Function<String, UUID> parse = UUID::fromString;
      Function<UUID, String> render = UUID::toString;
      assertThatNullPointerException()
          .isThrownBy(() -> ValidatedPrism.canonical((String) null, parse, render))
          .withMessage("message must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> ValidatedPrism.canonical((FieldError) null, parse, render))
          .withMessage("reason must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> ValidatedPrism.canonical("m", null, render))
          .withMessage("parse must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> ValidatedPrism.canonical("m", parse, null))
          .withMessage("render must not be null");
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
