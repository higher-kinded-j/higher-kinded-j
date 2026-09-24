// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import java.util.List;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.annotations.UpdateSpec;
import org.higherkindedj.optics.edit.Edits;
import org.higherkindedj.optics.validated.ValidatedPrism;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/mapping/beans_patch.html">Sparse PATCH</a> page.
 *
 * <p>The book does not paraphrase this file: it {@code {{#include}}}s the anchored regions below,
 * so the page cannot drift from the API, and {@code BookExampleOutputTest} runs {@code main} and
 * holds each output comment to what it prints.
 *
 * <p>The specs are top-level, not nested in the class: a nested spec joins its enclosing simple
 * names, so {@code Shop.CustomerMapping} would generate {@code ShopCustomerMappingImpl}, where the
 * page teaches {@code CustomerMappingImpl}.
 *
 * <p>Types several pages share, such as {@code Customer} and {@code EmailAddress}, are declared in
 * {@link BasicsBook}, beside the page that introduces them.
 */
public final class SparsePatchBook {

  private SparsePatchBook() {}

  public static void main(String[] args) {
    // ANCHOR: update_usage
    Customer current = new Customer("Ada", new EmailAddress("ada@corp.example"));

    ContactPatchBean patch = new ContactPatchBean();
    patch.setName("Ada Lovelace"); // email left null: not provided, keep the current one

    Edits.Accumulated<Customer> update = ContactPatchMappingImpl.INSTANCE.updateFrom(patch);
    Validated<NonEmptyList<FieldError>, Customer> patched = update.apply(current);
    // Valid(Customer[name=Ada Lovelace, email=EmailAddress[value=ada@corp.example]])
    //   <- only the name changed
    // ANCHOR_END: update_usage
    System.out.println(patched);

    // ANCHOR: update_invariant_usage
    PriceBand band = new PriceBand(10, 20);

    PriceBandPatch raise = new PriceBandPatch();
    raise.setFloor(30); // on its own, a floor of 30 is above the ceiling of 20...
    raise.setCeiling(40); // ...but the PATCH ends on 30 to 40
    Validated<NonEmptyList<FieldError>, PriceBand> raised =
        PriceBandPatchMappingImpl.INSTANCE.updateFrom(raise).apply(band);
    // Valid(PriceBand[floor=30, ceiling=40]): the constructor sees only the final values

    PriceBandPatch floorOnly = new PriceBandPatch();
    floorOnly.setFloor(30);
    Validated<NonEmptyList<FieldError>, PriceBand> refused =
        PriceBandPatchMappingImpl.INSTANCE.updateFrom(floorOnly).apply(band);
    // Invalid(NonEmptyList[floor above ceiling]): the constructor's own message, at the root
    // ANCHOR_END: update_invariant_usage
    System.out.println(raised + " / " + refused);

    // ANCHOR: update_container_usage
    Roster roster = new Roster("core", List.of(new PhoneNumber("+44")));

    RosterPatchBean rosterPatch = new RosterPatchBean();
    rosterPatch.setPhones(List.of("+1", "nope")); // a present list replaces wholesale...

    Validated<NonEmptyList<FieldError>, Roster> rosterPatched =
        RosterPatchMappingImpl.INSTANCE.updateFrom(rosterPatch).apply(roster);
    // ...but each element parses through the phones() leaf, located:
    // Invalid(NonEmptyList[phones.1: not a phone number])
    // ANCHOR_END: update_container_usage
    System.out.println(rosterPatched);
  }
}

// ANCHOR: update_spec
// A PATCH request bean. Here null means "not provided, leave unchanged" - the opposite of the bean
// parse above, where null is broken data. That contract is opted into by extending UpdateSpec.
class ContactPatchBean {
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
interface ContactPatchMapping extends UpdateSpec<Customer, ContactPatchBean> {
  // Generates only updateFrom(ContactPatchBean) : Edits.Accumulated<Customer> - no build/parse/as*.
  // A present field is set (email parsed through its leaf, located on failure); an absent (null)
  // one is skipped, so the domain's current value survives.
  default ValidatedPrism<String, EmailAddress> email() {
    return EmailCodecs.EMAIL;
  }
}

// ANCHOR_END: update_spec

// ANCHOR: update_invariant
// A price band whose constructor checks its two fields against each other.
record PriceBand(int floor, int ceiling) {
  PriceBand {
    if (floor > ceiling) {
      throw new IllegalArgumentException("floor above ceiling");
    }
  }
}

class PriceBandPatch {
  private Integer floor;
  private Integer ceiling;

  public Integer getFloor() {
    return floor;
  }

  public void setFloor(Integer floor) {
    this.floor = floor;
  }

  public Integer getCeiling() {
    return ceiling;
  }

  public void setCeiling(Integer ceiling) {
    this.ceiling = ceiling;
  }
}

@GenerateMapping
interface PriceBandPatchMapping extends UpdateSpec<PriceBand, PriceBandPatch> {}

// ANCHOR_END: update_invariant

// ANCHOR: update_container
record PhoneNumber(String value) {}

record Roster(String team, List<PhoneNumber> phones) {}

record RosterDto(String team, List<String> phones) {} // the full tier's wire

// A PATCH bean whose phones property is a whole-list replacement, absent when null.
class RosterPatchBean {
  private String team;
  private List<String> phones;

  public String getTeam() {
    return team;
  }

  public void setTeam(String team) {
    this.team = team;
  }

  public List<String> getPhones() {
    return phones;
  }

  public void setPhones(List<String> phones) {
    this.phones = phones;
  }
}

// ONE element vocabulary: the leaf names the component and parses ELEMENTS.
interface PhoneVocabulary {
  default ValidatedPrism<String, PhoneNumber> phones() {
    return ValidatedPrism.of(
        raw ->
            raw.startsWith("+")
                ? Validated.validNel(new PhoneNumber(raw))
                : Validated.invalidNel(FieldError.of("not a phone number")),
        PhoneNumber::value);
  }
}

@GenerateMapping
interface RosterMapping extends PhoneVocabulary, MappingSpec<Roster, RosterDto> {}

// The full tier lifts the leaf elementwise: a bad element parses as phones.1.

@GenerateMapping
interface RosterPatchMapping extends PhoneVocabulary, UpdateSpec<Roster, RosterPatchBean> {}

// The sparse tier lifts the SAME leaf: a present list replaces wholesale, each element parsed,
// failures located phones.1 - one vocabulary, both tiers.

// ANCHOR_END: update_container
