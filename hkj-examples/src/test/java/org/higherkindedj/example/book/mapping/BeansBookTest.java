// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import org.higherkindedj.optics.laws.MappingLaws;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The law checks behind the book's <a
 * href="https://higher-kinded-j.github.io/latest/mapping/beans.html">Bean-Shaped Wires</a> page.
 * The page {@code {{#include}}}s the anchored regions below, so the snippet it displays is this
 * test, and it is green.
 *
 * <p>{@code hkj-test} is test-scope, which is why the laws live in a test rather than beside the
 * spec.
 */
@DisplayName("the Bean-Shaped Wires page's mappings obey the mapping laws")
class BeansBookTest {

  @Test
  void transferBeanProjectionObeysThePatchLaws() {
    Employee ada = new Employee("Ada", "Research", 36);
    MappingLaws.assertMappingLaws(
        TransferMappingImpl.INSTANCE::patch,
        TransferMappingImpl.INSTANCE::build,
        ada, // the current value
        transfer("Platform"), // parses and changes the domain
        transfer(null)); // an unset property: located failure

    // The located error the page shows, exactly:
    assertThatValidated(TransferMappingImpl.INSTANCE.patch(ada, new TransferBean()))
        .isInvalid()
        .hasFieldErrors("department: must not be null");
  }

  @Test
  void oneDirectionalBeanMappingsObeyTheirLaws() {
    // ANCHOR: one_way_laws
    MappingLaws.assertMappingLaws(
        CustomerViewMappingImpl.INSTANCE.asValidatedParse(),
        new CustomerView("Ada", "ada@example.org"), // parses
        new CustomerView("Bob", "not-an-email")); // located failure

    MappingLaws.assertMappingLaws(
        CustomerRequestMappingImpl.INSTANCE.asValidatedBuild(),
        new Customer("Ada", new EmailAddress("ada@example.org"))); // renders without failing
    // ANCHOR_END: one_way_laws
  }

  private static TransferBean transfer(String department) {
    TransferBean bean = new TransferBean();
    bean.setDepartment(department);
    return bean;
  }
}
