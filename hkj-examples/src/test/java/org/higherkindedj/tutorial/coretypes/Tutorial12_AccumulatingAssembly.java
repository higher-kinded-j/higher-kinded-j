// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.coretypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.EitherOrBothAssert.assertThatEitherOrBoth;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.util.List;
import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 12: Accumulating assembly — building a record from N validated fields.
 *
 * <p>Pain → Promise. Tutorial 03 taught {@code map3} for combining independent validations, and it
 * left two itches unscratched. First, the arity wall: {@code map5} is the end of the road, and the
 * {@code Kind} ceremony grows with every field. Second, the errors come back as a flat list with no
 * idea <em>which field</em> each one belongs to:
 *
 * <pre>
 *   // All we know is that something, somewhere, was wrong:
 *   Invalid([must not be blank, not a number])
 * </pre>
 *
 * <p>The staged assembly builder removes both. {@code Validated.fields()} assembles any number of
 * fields (up to 16 per level; nest a sub-record for more), needs no {@code Semigroup} argument and
 * no {@code Kind}, and each {@code field(label, value)} call tags its slot so every error carries
 * its path — {@code "email: not an address"}, {@code "address.zip: not a postcode"}. Errors always
 * emerge in field-declaration order.
 *
 * <p>Java idiom anchor.
 *
 * <ul>
 *   <li>Bean Validation's {@code ConstraintViolation#getPropertyPath()} is the located-error idea:
 *       every violation knows which property it belongs to. {@code FieldError} makes that a plain,
 *       composable value.
 *   <li>A form that re-renders with a message beside each bad input is this tutorial's output
 *       shape: all errors at once, each with its field.
 * </ul>
 *
 * <p>What we will do here:
 *
 * <ol>
 *   <li>Assemble a record from three valid fields with {@code fields()...apply(Ctor::new)}.
 *   <li>Watch a mixed-validity assembly report <em>every</em> bad field, located, in declaration
 *       order.
 *   <li>Use the generic {@code accumulate()} flavour when the error type is our own.
 *   <li>Nest a sub-assembly and watch the outer label prefix the inner paths.
 *   <li>Assemble tolerantly with {@code EitherOrBoth.accumulate()}: warnings gather, the value
 *       keeps flowing.
 *   <li>Practise the diagnostic: {@code and()} versus {@code field()} — where did my label go?
 * </ol>
 *
 * <p>For the full story see <a
 * href="../../../../../../../../../hkj-book/src/monads/validated_assembly.md">Accumulating
 * Assembly</a> in the Core Types chapter.
 */
@DisplayName("Tutorial 12: Accumulating Assembly")
public class Tutorial12_AccumulatingAssembly {

  /** Helper for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // ─── Shared domain: a signup form and its leaf validators ──────────────────

  record User(String name, String email, int age) {}

  /** A leaf validator creates an unlabelled error; the assembly attaches the location. */
  private static Validated<NonEmptyList<FieldError>, String> parseName(String raw) {
    return raw == null || raw.isBlank()
        ? Validated.invalidNel(FieldError.of("must not be blank"))
        : Validated.validNel(raw.strip());
  }

  private static Validated<NonEmptyList<FieldError>, String> parseEmail(String raw) {
    return raw != null && raw.contains("@")
        ? Validated.validNel(raw)
        : Validated.invalidNel(FieldError.of("not an address"));
  }

  private static Validated<NonEmptyList<FieldError>, Integer> parseAge(String raw) {
    try {
      return Validated.validNel(Integer.parseInt(raw));
    } catch (NumberFormatException _) {
      return Validated.invalidNel(FieldError.of("not a number"));
    }
  }

  /** Renders each error as {@code "path: message"} so ordering and location are easy to assert. */
  private static List<String> errorStrings(Validated<NonEmptyList<FieldError>, ?> result) {
    return result.fold(nel -> nel.map(FieldError::toString).toJavaList(), _ -> List.<String>of());
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 1: A first assembly
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 1: Assemble a {@code User} from three valid fields.
   *
   * <p>{@code Validated.fields()} opens the builder; each {@code field(label, value)} adds one
   * validated field; {@code apply(...)} finishes with a constructor reference of exactly the
   * accumulated arity. No {@code Semigroup}, no {@code Kind}, no arity wall.
   *
   * <p>Task: assemble {@code new User("Ada", "ada@example.com", 36)} from the three parsed fields.
   *
   * <pre>
   *   // Nudge:    fields() then one field(label, value) per slot, then apply.
   *   // Strategy: Validated.fields().field("name", name).field("email", email)
   *   //               .field("age", age).apply(User::new)
   *   // Spoiler:  exactly the strategy line.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 1: fields()...apply(User::new) assembles three valid fields")
  void exercise1_firstAssembly() {
    Validated<NonEmptyList<FieldError>, String> name = parseName("Ada");
    Validated<NonEmptyList<FieldError>, String> email = parseEmail("ada@example.com");
    Validated<NonEmptyList<FieldError>, Integer> age = parseAge("36");

    Validated<NonEmptyList<FieldError>, User> result = answerRequired();

    assertThatValidated(result).isValid().hasValue(new User("Ada", "ada@example.com", 36));
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 2: Every bad field, located, in declaration order
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 2: Mixed validity.
   *
   * <p>Nothing short-circuits: a bad name and a bad age are <em>both</em> reported even though the
   * email in between is fine, and they arrive in the order the fields were declared. That guarantee
   * is what makes a form response (or an HTTP 422 body) deterministic.
   *
   * <p>Task: assemble from a blank name, a good email, and an unparseable age; then fill in the
   * expected rendered errors.
   *
   * <pre>
   *   // Nudge:    Same chain as exercise 1; only the inputs changed. Each rendered error is
   *   //           "label: message".
   *   // Strategy: The bad fields are name and age; their labels are the field() labels.
   *   // Spoiler:  List.of("name: must not be blank", "age: not a number")
   * </pre>
   */
  @Test
  @DisplayName("Exercise 2: mixed validity reports every bad field in declaration order")
  void exercise2_allErrorsInOrder() {
    Validated<NonEmptyList<FieldError>, String> name = parseName("   ");
    Validated<NonEmptyList<FieldError>, String> email = parseEmail("ada@example.com");
    Validated<NonEmptyList<FieldError>, Integer> age = parseAge("unknown");

    Validated<NonEmptyList<FieldError>, User> result = answerRequired();
    List<String> expected = answerRequired();

    assertThatValidated(result).isInvalid();
    assertThat(errorStrings(result)).isEqualTo(expected);
    assertThat(expected).hasSize(2);
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 3: The generic flavour — accumulate()
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 3: {@code accumulate()} for our own error type.
   *
   * <p>When the error type is not {@code FieldError}, {@code Validated.accumulate()} gives the same
   * open-arity assembly for any payload, carried as {@code NonEmptyList<X>}. Fields join with
   * {@code and(value)}; there are no labels because the payload is whatever we choose.
   *
   * <p>Task: assemble {@code Settings} from the host and port below.
   *
   * <pre>
   *   // Nudge:    accumulate() then and(value) per field, then apply.
   *   // Strategy: Validated.accumulate().and(host).and(port).apply(Settings::new)
   *   // Spoiler:  exactly the strategy line.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 3: accumulate() assembles with any error payload")
  void exercise3_genericAccumulate() {
    record Settings(String host, int port) {}

    Validated<NonEmptyList<String>, String> host = Validated.validNel("localhost");
    Validated<NonEmptyList<String>, Integer> port = Validated.invalidNel("port out of range");

    Validated<NonEmptyList<String>, Settings> result = answerRequired();

    assertThatValidated(result).isInvalid();
    List<String> errors = result.fold(NonEmptyList::toJavaList, _ -> List.<String>of());
    assertThat(errors).containsExactly("port out of range");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 4: Nesting — the outer label prefixes the inner paths
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 4: Nested assemblies.
   *
   * <p>{@code FieldError.at} <em>prepends</em>, so assembling a sub-record under an outer label
   * prefixes all its inner paths: the {@code "zip"} error becomes {@code "address.zip"}. Deep
   * domain models keep precise error locations for free.
   *
   * <p>Task: assemble a {@code Customer} from a valid name and the (failing) address assembly
   * below, labelling the address slot {@code "address"}.
   *
   * <pre>
   *   // Nudge:    A sub-assembly result is just a Validated; feed it to field() like any other.
   *   // Strategy: Validated.fields().field("name", ...).field("address", address)
   *   //               .apply(Customer::new)
   *   // Spoiler:  exactly the strategy line, with parseName("Ada") for the name.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 4: nesting prefixes inner paths (address.zip)")
  void exercise4_nestedPaths() {
    record Address(String street, String zip) {}
    record Customer(String name, Address address) {}

    Validated<NonEmptyList<FieldError>, Address> address =
        Validated.fields()
            .field("street", Validated.<FieldError, String>validNel("Main St"))
            .field("zip", Validated.<FieldError, String>invalidNel(FieldError.of("not a postcode")))
            .apply(Address::new);

    Validated<NonEmptyList<FieldError>, Customer> result = answerRequired();

    assertThatValidated(result).isInvalid();
    assertThat(errorStrings(result)).containsExactly("address.zip: not a postcode");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 5: Tolerant assembly with EitherOrBoth
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 5: Warnings gather; the value keeps flowing.
   *
   * <p>{@code EitherOrBoth.accumulate()} is the tolerant twin: a {@code Both} contributes its
   * warning <em>and</em> its value, so lenient parses still produce a usable result. Only a {@code
   * Left} withholds the value (and even then, every warning is kept).
   *
   * <p>Task: assemble {@code Config} from the two lenient parses below.
   *
   * <pre>
   *   // Nudge:    Same accumulate()/and()/apply shape as exercise 3, on EitherOrBoth.
   *   // Strategy: EitherOrBoth.accumulate().and(port).and(timeout).apply(Config::new)
   *   // Spoiler:  exactly the strategy line.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 5: EitherOrBoth.accumulate() keeps the value flowing")
  void exercise5_tolerantAssembly() {
    record Config(int port, int timeout) {}

    EitherOrBoth<NonEmptyList<String>, Integer> port =
        EitherOrBoth.both(NonEmptyList.single("port defaulted"), 8080);
    EitherOrBoth<NonEmptyList<String>, Integer> timeout = EitherOrBoth.right(30);

    EitherOrBoth<NonEmptyList<String>, Config> result = answerRequired();

    assertThatEitherOrBoth(result)
        .isBoth()
        .hasBothSatisfying(
            (warnings, config) -> {
              assertThat(warnings.toJavaList()).containsExactly("port defaulted");
              assertThat(config).isEqualTo(new Config(8080, 30));
            });
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Diagnostic: Things People Get Wrong
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Diagnostic: where did my label go?
   *
   * <p>{@code fields()} offers both {@code field(label, value)} and an unlabelled {@code
   * and(value)}. The unlabelled form compiles and accumulates correctly, but the error keeps an
   * empty path, so it renders as a bare message. A form cannot say <em>which</em> input to fix:
   *
   * <pre>
   *   // The buggy version someone wrote:
   *   Validated.fields()
   *       .field("name", parseName("Ada"))
   *       .and(parseEmail("oops"))          // compiles; the label is never attached
   *       .apply(Pair::new);
   *   // renders: "not an address"          — which field was that?
   * </pre>
   *
   * <p>Rule of thumb: inside {@code fields()}, reach for {@code field(label, value)} for leaf
   * validators. The unlabelled {@code and(value)} exists for values whose errors already carry
   * their paths (a pre-labelled sub-assembly that must not be re-prefixed) and for genuinely
   * unattributable errors — not for leaves.
   *
   * <p>Task: write the corrected assembly so the email error is located.
   *
   * <pre>
   *   // Nudge:    Swap the and(...) for a labelled field(...).
   *   // Strategy: .field("email", parseEmail("oops"))
   *   // Spoiler:  Validated.fields().field("name", parseName("Ada"))
   *   //               .field("email", parseEmail("oops")).apply(Pair::new)
   * </pre>
   */
  @Test
  @DisplayName("Diagnostic: and() drops the location; field() keeps it")
  void diagnostic_droppedLabel() {
    record Pair(String name, String email) {}

    Validated<NonEmptyList<FieldError>, Pair> corrected = answerRequired();

    assertThatValidated(corrected).isInvalid();
    assertThat(errorStrings(corrected)).containsExactly("email: not an address");
  }

  /*
   * Where to next?
   *   • @GenerateAssembly — annotate a record you own and the processor generates this whole
   *     chain for you: named methods per component, auto-labels, no arity ceiling.
   *   • Accumulating Assembly (Core Types chapter) — the full story: three carriers
   *     (Validated, ValidationPath, EitherOrBoth), the declaration-order contract, and
   *     when to reach for zipWithAccum or mapN instead.
   *   • Tutorial 03 — the mapN family remains the right tool inside Kind-generic code.
   *   • Effect journey — ValidationPath carries the same assembly (Path.fields()) on the
   *     railway, composing onward with via and recover.
   *   • Tutorial 25 (optics) — the leaf-side twin: a ValidatedPrism is the named home for the
   *     per-field parsers this tutorial feeds to fields()/accumulate().
   *   • Tutorial 24 (optics) — the update-side twin: Edits.accumulate applies N validated
   *     edits to an EXISTING value (sparse PATCH), with the same located, all-at-once errors.
   */
}
