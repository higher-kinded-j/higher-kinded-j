// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.assertions.WriterAssert.assertThatWriter;
import static org.higherkindedj.hkt.writer.WriterKindHelper.WRITER;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.writer.Writer;
import org.higherkindedj.hkt.writer.WriterKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Showcase for {@link org.higherkindedj.hkt.assertions.WriterAssert}. */
@DisplayName("WriterAssert showcase")
class WriterAssertExample {

  @Test
  @DisplayName("hasValue() and hasLog() inspect both halves of the pair")
  void valueAndLog() {
    Writer<String, Integer> writer = new Writer<>("computed: ", 42);

    assertThatWriter(writer).hasValue(42).hasLog("computed: ");
  }

  @Test
  @DisplayName("logMatches() runs a predicate on the accumulated log")
  void logMatches() {
    Writer<String, Integer> writer = new Writer<>("step1; step2; step3", 7);

    assertThatWriter(writer).logMatches(log -> log.contains("step2"), "log contains 'step2'");
  }

  @Test
  @DisplayName("valueMatches() runs a predicate on the value")
  void valueMatches() {
    Writer<String, Integer> writer = new Writer<>("ok", 100);

    assertThatWriter(writer).valueMatches(v -> v >= 100);
  }

  @Test
  @DisplayName("Accepts Kind<WriterKind.Witness<W>, A> directly without manual narrowing")
  void acceptsKindDirectly() {
    Writer<String, Integer> writer = new Writer<>("log", 99);
    Kind<WriterKind.Witness<String>, Integer> kind = WRITER.widen(writer);

    assertThatWriter(kind).hasValue(99).hasLog("log");
  }
}
