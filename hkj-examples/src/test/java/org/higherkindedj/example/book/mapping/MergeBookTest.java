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
    // ANCHOR: check_plain_null
    Dashboard dashboard =
        DashboardAssemblyImpl.INSTANCE.assemble(
            new User(null, "ada@corp.example"), new Account("GB29-XXXX", 4200), new Settings(true));

    assertThat(dashboard).isEqualTo(new Dashboard(null, "GB29-XXXX", true)); // no check, no error
    // ANCHOR_END: check_plain_null
  }

  @Test
  @DisplayName("only a context that rejects null fails its companion: at first use, and after")
  void onlyAContextThatRejectsNullFailsItsCompanion() {
    // ANCHOR: check_contexts
    assertThat(ChargebackErrors.chargebackOpened("ORD-1").envelope().context())
        .isEqualTo(new ChargebackErrorContext(null)); // accepts the all-absent context

    assertThatThrownBy(() -> RefundErrors.refundWindowClosed("ORD-1")) // the first use
        .isInstanceOf(ExceptionInInitializerError.class)
        .cause()
        .hasMessage("traceId");
    assertThatThrownBy(() -> RefundErrors.refundWindowClosed("ORD-2")) // and every use after
        .isInstanceOf(NoClassDefFoundError.class);
    // ANCHOR_END: check_contexts
  }
}
