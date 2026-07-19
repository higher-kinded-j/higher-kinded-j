// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.UpdateSpec;
import org.higherkindedj.optics.edit.Edits;
import org.higherkindedj.optics.validated.ValidatedPrism;

/**
 * A small end-to-end story for sparse PATCH write-back (issue #645): applying a partial update from
 * a bean-shaped request DTO to an immutable domain record.
 *
 * <p>A REST {@code PATCH} body carries only the fields the client wants to change; every other
 * property of the bound request object arrives {@code null}, meaning <em>not provided, leave
 * unchanged</em>. That is the opposite of a {@link
 * org.higherkindedj.optics.annotations.MappingSpec} bean parse, where a {@code null} required
 * property is broken data. Because the two meanings of {@code null} are a property of the DTO's
 * contract, sparseness is an explicit opt-in: the spec extends {@link UpdateSpec} instead of {@code
 * MappingSpec}.
 *
 * <p>The generated {@code UpdateSpecExampleUserPatchMappingImpl} exposes a single method, {@code
 * updateFrom(UserPatchRequest) : Edits.Accumulated<User>} — no {@code build}, {@code parse} or
 * {@code as*} tier. It folds the present (non-null) properties into an {@link
 * org.higherkindedj.hkt.Update}:
 *
 * <ul>
 *   <li><b>Present and valid</b> — the field is set, or parsed through its leaf, and folded in.
 *   <li><b>Present and invalid</b> — a located {@code FieldError}, accumulating as usual:
 *       sparseness never weakens validation of what was sent.
 *   <li><b>Absent (null)</b> — skipped; the domain's current value survives.
 * </ul>
 *
 * <p>{@code age} is a wrapper {@code Integer} on the request so the scalar too can be absent; a
 * primitive property could never carry the null-as-absent signal and is rejected at compile time.
 * {@code UpdateSpecExampleTest} law-checks the mapping through the published {@code MappingLaws}
 * harness ({@code hkj-test} is test-scope, so the laws live in a test, not here).
 */
public final class UpdateSpecExample {

  public record EmailAddress(String value) {}

  /** The immutable domain record a PATCH is applied onto. */
  public record User(String name, EmailAddress email, int age) {}

  /** A PATCH request bean: reference-typed getters/setters, a wrapper {@code Integer} for age. */
  public static final class UserPatchRequest {
    private String name;
    private String email;
    private Integer age;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getEmail() {
      return email;
    }

    public void setEmail(String email) {
      this.email = email;
    }

    public Integer getAge() {
      return age;
    }

    public void setAge(Integer age) {
      this.age = age;
    }
  }

  @GenerateMapping
  public interface UserPatchMapping extends UpdateSpec<User, UserPatchRequest> {
    default ValidatedPrism<String, EmailAddress> email() {
      return emailPrism();
    }
  }

  static ValidatedPrism<String, EmailAddress> emailPrism() {
    return ValidatedPrism.of(
        raw ->
            raw.contains("@")
                ? Validated.validNel(new EmailAddress(raw))
                : Validated.invalidNel(FieldError.of("not an email address")),
        EmailAddress::value);
  }

  /**
   * Applies a PATCH request onto the current user: the present fields are folded in and validated,
   * the absent ones keep their current value. A present-but-invalid field fails the whole patch,
   * located — the endpoint would answer {@code 400} with the errors rather than persist a partial
   * write.
   */
  public static Validated<NonEmptyList<FieldError>, User> applyPatch(
      User current, UserPatchRequest request) {
    Edits.Accumulated<User> patch =
        UpdateSpecExampleUserPatchMappingImpl.INSTANCE.updateFrom(request);
    return patch.apply(current);
  }

  public static void main(String[] args) {
    User current = new User("Ada", new EmailAddress("ada@corp.example"), 36);

    System.out.println("=== Present fields are applied, absent ones survive ===");
    UserPatchRequest rename = new UserPatchRequest();
    rename.setName("Ada Lovelace"); // email and age left unset (null)
    System.out.println("Rename only:     " + applyPatch(current, rename));
    System.out.println(
        "Expected: Valid(User[name=Ada Lovelace, email=ada@corp.example, age=36]) — only the name"
            + " changed\n");

    System.out.println("=== An all-absent request is the identity update ===");
    System.out.println("Empty patch:     " + applyPatch(current, new UserPatchRequest()));
    System.out.println("Expected: Valid(the unchanged user)\n");

    System.out.println("=== A present invalid field fails, located ===");
    UserPatchRequest bad = new UserPatchRequest();
    bad.setEmail("not-an-email");
    System.out.println("Bad email:       " + applyPatch(current, bad));
    System.out.println("Expected: Invalid([email: not an email address]) — nothing is written");
  }

  private UpdateSpecExample() {}
}
