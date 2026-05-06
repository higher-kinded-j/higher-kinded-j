// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.util.Affines;
import org.higherkindedj.optics.util.Prisms;
import org.junit.jupiter.api.Test;

/**
 * Solution for Tutorial04 AffineBasics — teaching-solution format.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions established by
 * the Foundations journey: read the working code first, then the commentary on <em>why</em> the
 * chosen form is idiomatic. The complete-with-commentary template (Why this is idiomatic /
 * Alternative / Common wrong attempt on every exercise) lives in the Foundations solutions
 * coretypes/Tutorial01_KindBasics_Solution.java as the canonical reference.
 *
 * <p>The exercise bodies below are correct working code. Per-exercise teaching commentary is being
 * rolled out across the chapter; if this file does not yet have it, treat the reference code as the
 * answer and consult the pilot solution for the format guide.
 *
 * <p>For the chapter-level guidance on how to learn from a solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
 */
public class Tutorial04_AffineBasics_Solution {

  // Domain model with optional fields
  record UserProfile(String username, Optional<ContactInfo> contact) {}

  record ContactInfo(String email, Optional<String> phone) {}

  // Configuration with optional sections
  record AppConfig(String appName, Optional<DatabaseConfig> database) {}

  record DatabaseConfig(String host, int port) {}

  /**
   * Why this is idiomatic: {@code Affines.some()} is the named bridge from {@code Optional} into
   * the optic API. {@code getOptional} on the resulting {@code Affine} is the partial read
   * operation — you get either a present value or the empty result, never a thrown exception.
   *
   * <p>Alternative: keep the {@code Optional} and call {@code .map(...)} directly. Equivalent for
   * one-step access; lift to {@code Affine} as soon as the optional needs to participate in a
   * longer optic chain.
   *
   * <p>Common wrong attempt: assume {@code Affines.some()} unwraps the value to a plain {@code
   * String}. The result is still an {@code Optional} — affines preserve the partiality because the
   * value may be absent; the caller decides what to do with the empty case.
   */
  @Test
  void exercise1_usingSomeAffine() {
    Affine<Optional<String>, String> someAffine = Affines.some();

    Optional<String> present = Optional.of("hello");
    Optional<String> empty = Optional.empty();

    // SOLUTION: Use someAffine.getOptional() to extract the value
    Optional<String> extracted = someAffine.getOptional(present);

    assertThat(extracted).isPresent();
    assertThat(extracted.get()).isEqualTo("hello");

    // Affine returns empty when the Optional is empty
    Optional<String> notPresent = someAffine.getOptional(empty);
    assertThat(notPresent).isEmpty();
  }

  /**
   * Why this is idiomatic: {@code Affine.set} is total in the write direction — even an empty
   * {@code Optional} becomes {@code Optional.of("world")}. Affines, like prisms, write totally even
   * when reads are partial.
   *
   * <p>Alternative: {@code Optional.of("world")}. Same outcome; reach for the affine when the
   * setter needs to compose with the rest of an optic pipeline (e.g. lens-then-affine into a deep
   * optional field).
   *
   * <p>Common wrong attempt: expect {@code set} to refuse to write when the original is empty (some
   * libraries' "modify" semantics). {@code Affine.set} replaces; use {@code modify} when you only
   * want to transform an existing value.
   */
  @Test
  void exercise2_settingWithAffine() {
    Affine<Optional<String>, String> someAffine = Affines.some();

    Optional<String> empty = Optional.empty();

    // SOLUTION: Use someAffine.set() to set the value
    Optional<String> updated = someAffine.set("world", empty);

    assertThat(updated).isPresent();
    assertThat(updated.get()).isEqualTo("world");
  }

  /**
   * Why this is idiomatic: {@code lens.andThen(prism)} produces an {@code Affine} — the lens
   * focuses totally on the {@code Optional} field, the prism focuses partially on its content. The
   * compiler picks the correct return type from the optic algebra.
   *
   * <p>Alternative: hand-write a getter ({@code config.database().map(DatabaseConfig::host)}). Same
   * answer in this small case; the named affine is what other optic combinators ({@code modify},
   * {@code set}, {@code matches}) attach to.
   *
   * <p>Common wrong attempt: try to compose the prism on the left ({@code somePrism.andThen
   * (databaseLens)}). The types don't line up — the prism's source is the {@code Optional}, the
   * lens's source is the outer {@code AppConfig}. {@code andThen} flows outer-to-inner.
   */
  @Test
  void exercise3_lensAndPrismComposition() {
    // Lens to access the optional database field
    Lens<AppConfig, Optional<DatabaseConfig>> databaseLens =
        Lens.of(AppConfig::database, (config, db) -> new AppConfig(config.appName(), db));

    // Prism to extract the value from Optional
    Prism<Optional<DatabaseConfig>, DatabaseConfig> somePrism = Prisms.some();

    // SOLUTION: Compose the lens with the prism using andThen
    Affine<AppConfig, DatabaseConfig> databaseAffine = databaseLens.andThen(somePrism);

    AppConfig configWithDb =
        new AppConfig("MyApp", Optional.of(new DatabaseConfig("localhost", 5432)));
    AppConfig configWithoutDb = new AppConfig("MyApp", Optional.empty());

    // Test getOptional on config with database
    Optional<DatabaseConfig> db1 = databaseAffine.getOptional(configWithDb);
    assertThat(db1).isPresent();
    assertThat(db1.get().host()).isEqualTo("localhost");

    // Test getOptional on config without database
    Optional<DatabaseConfig> db2 = databaseAffine.getOptional(configWithoutDb);
    assertThat(db2).isEmpty();
  }

  /**
   * Why this is idiomatic: {@code matches} answers the boolean question without allocating an
   * {@code Optional} for the value. It is the affine's predicate-style accessor.
   *
   * <p>Alternative: {@code someAffine.getOptional(s).isPresent()}. Same answer; reach for {@code
   * matches} when you only need the boolean and want to avoid the extra wrapper.
   *
   * <p>Common wrong attempt: write {@code .matches(present) == true} (or worse, {@code
   * Boolean.TRUE.equals(...)}). The boolean comparison is gratuitous — the predicate itself is the
   * answer.
   */
  @Test
  void exercise4_usingMatches() {
    Affine<Optional<String>, String> someAffine = Affines.some();

    Optional<String> present = Optional.of("value");
    Optional<String> empty = Optional.empty();

    // SOLUTION: Use someAffine.matches() to check presence
    boolean hasValue = someAffine.matches(present);
    boolean isEmpty = !someAffine.matches(empty);

    assertThat(hasValue).isTrue();
    assertThat(isEmpty).isTrue();
  }

  /**
   * Why this is idiomatic: {@code getOrElse(default, source)} collapses the partial read to a total
   * value in one call. It is the affine's answer to "give me the focus or this fallback".
   *
   * <p>Alternative: {@code someAffine.getOptional(present).orElse("default")}. Identical runtime
   * behaviour; the {@code getOrElse} spelling keeps the affine in the foreground and pairs nicely
   * with the rest of the optic vocabulary.
   *
   * <p>Common wrong attempt: a {@code String} sentinel like {@code null} or {@code ""} as the
   * default. Both collide with valid focused values and force every caller into a re-check. Pick a
   * default that cannot collide, or change the return type.
   */
  @Test
  void exercise5_usingGetOrElse() {
    Affine<Optional<String>, String> someAffine = Affines.some();

    Optional<String> present = Optional.of("configured");
    Optional<String> empty = Optional.empty();

    // SOLUTION: Use someAffine.getOrElse() with a default value
    String value1 = someAffine.getOrElse("default", present);
    String value2 = someAffine.getOrElse("default", empty);

    assertThat(value1).isEqualTo("configured");
    assertThat(value2).isEqualTo("default");
  }

  /**
   * Why this is idiomatic: {@code modify} on an affine is "transform the focus if it is there,
   * otherwise pass through". The empty case stays empty, the present case gets the function — one
   * call expresses both branches.
   *
   * <p>Alternative: {@code present.map(String::toUpperCase)}. Equivalent for a plain {@code
   * Optional}; the {@code Affine.modify} form generalises to deeper structures ({@code
   * Lens}-then-{@code Affine}) without changing shape.
   *
   * <p>Common wrong attempt: {@code modify} with a function that throws on the empty case ({@code s
   * -> s.toUpperCase()} where {@code s} could be {@code null}). The affine never invokes the
   * function on the empty branch, so the worry is only valid if you handed it a present {@code
   * Optional} containing {@code null} — wrap with {@code Optional.ofNullable}.
   */
  @Test
  void exercise6_modifyingWithAffine() {
    Affine<Optional<String>, String> someAffine = Affines.some();

    Optional<String> present = Optional.of("hello");
    Optional<String> empty = Optional.empty();

    // SOLUTION: Use someAffine.modify() with a transformation function
    Optional<String> uppercased = someAffine.modify(String::toUpperCase, present);

    assertThat(uppercased).isPresent();
    assertThat(uppercased.get()).isEqualTo("HELLO");

    // Modify on empty returns empty unchanged
    Optional<String> stillEmpty = someAffine.modify(String::toUpperCase, empty);
    assertThat(stillEmpty).isEmpty();
  }

  /**
   * Why this is idiomatic: alternate lens and prism through every layer ({@code lens.andThen
   * (prism)}) — each lens handles a total field, each prism handles an {@code Optional} unwrap. The
   * resulting {@code Affine<UserProfile, String>} reads "user to phone" without caring how many
   * layers of optionality are between.
   *
   * <p>Alternative: a chain of {@code .flatMap}s on the inner {@code Optional}s ({@code
   * user.contact().flatMap(ContactInfo::phone)}). The vanilla form is fine for reads; once a deep
   * <em>update</em> is needed, the affine chain rewrites the entire path with one {@code set}.
   *
   * <p>Common wrong attempt: skip a prism between two optional layers ({@code contactLens
   * .andThen(phoneLens)} on the {@code Optional<ContactInfo>}). The types reject the composition
   * because the lens expects a concrete {@code ContactInfo}, not an {@code Optional}. Each {@code
   * Optional} layer needs its own {@code Prisms.some()}.
   */
  @Test
  void exercise7_chainingAffines() {
    // Lenses for the record fields
    Lens<UserProfile, Optional<ContactInfo>> contactLens =
        Lens.of(UserProfile::contact, (u, c) -> new UserProfile(u.username(), c));

    Lens<ContactInfo, Optional<String>> phoneLens =
        Lens.of(ContactInfo::phone, (c, p) -> new ContactInfo(c.email(), p));

    // Prism to extract from Optional
    Prism<Optional<ContactInfo>, ContactInfo> contactPrism = Prisms.some();
    Prism<Optional<String>, String> phonePrism = Prisms.some();

    // SOLUTION: Chain the lenses and prisms together
    // contactLens >>> contactPrism = Affine<UserProfile, ContactInfo>
    // ... >>> phoneLens = Affine<UserProfile, Optional<String>>
    // ... >>> phonePrism = Affine<UserProfile, String>
    Affine<UserProfile, String> userToPhone =
        contactLens.andThen(contactPrism).andThen(phoneLens).andThen(phonePrism);

    UserProfile userWithPhone =
        new UserProfile(
            "alice", Optional.of(new ContactInfo("alice@example.com", Optional.of("555-1234"))));

    UserProfile userWithoutPhone =
        new UserProfile("bob", Optional.of(new ContactInfo("bob@example.com", Optional.empty())));

    UserProfile userWithoutContact = new UserProfile("charlie", Optional.empty());

    // Test deep access
    Optional<String> phone1 = userToPhone.getOptional(userWithPhone);
    assertThat(phone1).contains("555-1234");

    Optional<String> phone2 = userToPhone.getOptional(userWithoutPhone);
    assertThat(phone2).isEmpty();

    Optional<String> phone3 = userToPhone.getOptional(userWithoutContact);
    assertThat(phone3).isEmpty();

    // Test deep update
    UserProfile updated = userToPhone.set("555-9999", userWithPhone);
    assertThat(userToPhone.getOptional(updated)).contains("555-9999");
  }
}
