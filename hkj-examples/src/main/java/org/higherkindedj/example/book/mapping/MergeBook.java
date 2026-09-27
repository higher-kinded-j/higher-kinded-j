// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.annotations.GenerateMerge;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/mapping/merge_envelopes.html">Merge and Error
 * Envelopes</a> page.
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
public final class MergeBook {

  private MergeBook() {}

  public static void main(String[] args) {
    // ANCHOR: merge_usage
    Dashboard dashboard =
        DashboardAssemblyImpl.INSTANCE.assemble(
            new Customer("Ada", new EmailAddress("ada@corp.example")),
            new Account("GB29-XXXX", 4200),
            new Settings(true));
    // Dashboard[name=Ada, iban=GB29-XXXX, darkMode=true]
    // ANCHOR_END: merge_usage
    System.out.println(dashboard);

    // ANCHOR: nested_merge_usage
    Validated<NonEmptyList<FieldError>, CaseCard> card =
        CaseCardAssemblyImpl.INSTANCE.assemble(
            new Employee("Ada", "Support", 36), new CaseForm(new CustomerDto("Bob", "nope")));
    // Invalid(NonEmptyList[customer.email: not an email address])
    // ANCHOR_END: nested_merge_usage
    System.out.println(card);
  }
}

// ANCHOR: merge_spec
// the Customer is the chapter's own, from Record Mapping Basics
record Account(String iban, int balance) {}

record Settings(boolean darkMode) {}

record Dashboard(String name, String iban, boolean darkMode) {}

@GenerateMerge
interface DashboardAssembly {
  Dashboard assemble(Customer customer, Account account, Settings settings);
}

// ANCHOR_END: merge_spec

// ANCHOR: nested_merge_spec
// A support case: the agent who opens it, an Employee as on What Your Spec Generates, and the
// customer a form describes.
record CaseForm(CustomerDto customer) {} // the wire side

record CaseCard(String name, Customer customer) {} // the domain side

@GenerateMerge
interface CaseCardAssembly {
  // CaseCard.name fills from the agent's name; CaseCard.customer from CaseForm.customer, through
  // CustomerMapping, which can fail.
  Validated<NonEmptyList<FieldError>, CaseCard> assemble(Employee agent, CaseForm form);
}
// ANCHOR_END: nested_merge_spec
