// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The answers behind the book's <a
 * href="https://higher-kinded-j.github.io/latest/mapping/merge_envelopes.html">Merge and Error
 * Envelopes</a> page. The page {@code {{#include}}}s the anchored regions below, so each answer it
 * shows is this test, and it is green.
 */
@DisplayName("the Merge and Error Envelopes page's answers hold")
class MergeBookTest {

  @Test
  @DisplayName("a plain-return merge passes a null source component straight through")
  void aPlainReturnMergePassesNullThrough() {
    Dashboard dashboard =
        DashboardAssemblyImpl.INSTANCE.assemble(
            new User(null, "ada@corp.example"), new Account("GB29-XXXX", 4200), new Settings(true));

    assertThat(dashboard).isEqualTo(new Dashboard(null, "GB29-XXXX", true)); // no check, no error
  }

  @Test
  @DisplayName("a context that rejects null fails the companion on its first use, not at compile")
  void aNullRejectingContextFailsTheCompanionOnFirstUse() {
    // ANCHOR: check_strict_context
    assertThatThrownBy(() -> RefundErrors.refundWindowClosed("ORD-1"))
        .isInstanceOf(ExceptionInInitializerError.class) // the all-absent context threw
        .cause()
        .isInstanceOf(NullPointerException.class)
        .hasMessage("traceId");
    // ANCHOR_END: check_strict_context
  }
}
