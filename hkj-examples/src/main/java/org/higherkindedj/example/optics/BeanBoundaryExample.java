// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.validated.ValidatedPrism;

/**
 * A small end-to-end story for bean-shaped wire targets (issue #628): mapping a domain record
 * across an I/O boundary where the wire is a <em>bean</em>, not a record.
 *
 * <p>Real boundaries hand you bean-shaped DTOs, not records: a JSON/JAXB/protobuf binder fills a
 * mutable getter/setter request object on the way in, and you build an immutable response (often
 * via a builder) on the way out. {@code @GenerateMapping} maps both directions with the same spec
 * you would write for a record wire; only how the bean is read (getters) and written (setters or a
 * builder) changes.
 *
 * <ul>
 *   <li><b>Inbound</b>: {@code parse(UserRequest)} reads the mutable request through its getters
 *       and accumulates every problem, located by field. An unset property is {@code null}, so each
 *       reference read is null-guarded into a located {@code FieldError} rather than throwing.
 *   <li><b>Outbound</b>: {@code build(User)} fills an immutable {@code UserView} through its {@code
 *       builder()}.
 * </ul>
 *
 * <p>The annotation sits on the spec, never on the DTOs, so the DTOs could be third-party generated
 * types you do not own. {@code BeanBoundaryExampleTest} law-checks both mappings through {@code
 * MappingLaws}.
 */
public final class BeanBoundaryExample {

  public record EmailAddress(String value) {}

  public record User(String name, EmailAddress email) {}

  /** Inbound: a mutable getter/setter request DTO, as a JSON or form binder would populate it. */
  public static final class UserRequest {
    private String name;
    private String email;

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
  }

  @GenerateMapping
  public interface UserRequestMapping extends MappingSpec<User, UserRequest> {
    default ValidatedPrism<String, EmailAddress> email() {
      return emailPrism();
    }
  }

  /** Outbound: an immutable response DTO built through a static {@code builder()}. */
  public static final class UserView {
    private final String name;
    private final String email;

    private UserView(String name, String email) {
      this.name = name;
      this.email = email;
    }

    public String getName() {
      return name;
    }

    public String getEmail() {
      return email;
    }

    public static Builder builder() {
      return new Builder();
    }

    public static final class Builder {
      private String name;
      private String email;

      public Builder name(String name) {
        this.name = name;
        return this;
      }

      public Builder email(String email) {
        this.email = email;
        return this;
      }

      public UserView build() {
        return new UserView(name, email);
      }
    }
  }

  @GenerateMapping
  public interface UserViewMapping extends MappingSpec<User, UserView> {
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

  public static void main(String[] args) {
    System.out.println("=== Inbound: parse a mutable request DTO ===");
    UserRequest good = new UserRequest();
    good.setName("Ada");
    good.setEmail("ada@corp.example");
    System.out.println(
        "Valid request:   " + BeanBoundaryExampleUserRequestMappingImpl.INSTANCE.parse(good));

    UserRequest bad = new UserRequest();
    bad.setEmail("not-an-email"); // name left unset (null)
    System.out.println(
        "Bad request:     " + BeanBoundaryExampleUserRequestMappingImpl.INSTANCE.parse(bad));
    System.out.println(
        "Expected: Valid(User) for the good one; for the bad one, BOTH problems at once - the unset"
            + " name located as \"name\" (must not be null) and the malformed \"email\" - never a"
            + " thrown NullPointerException\n");

    System.out.println("=== Outbound: build an immutable view DTO via its builder ===");
    User ada = new User("Ada", new EmailAddress("ada@corp.example"));
    UserView view = BeanBoundaryExampleUserViewMappingImpl.INSTANCE.build(ada);
    System.out.println(
        "Built view:      UserView[name=" + view.getName() + ", email=" + view.getEmail() + "]");
    System.out.println("Expected: UserView constructed through UserView.builder()");
  }

  private BeanBoundaryExample() {}
}
