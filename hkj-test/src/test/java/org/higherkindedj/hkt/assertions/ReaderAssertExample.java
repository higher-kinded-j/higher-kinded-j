// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.assertions.ReaderAssert.assertThatReader;
import static org.higherkindedj.hkt.reader.ReaderKindHelper.READER;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.reader.Reader;
import org.higherkindedj.hkt.reader.ReaderKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Showcase for {@link org.higherkindedj.hkt.assertions.ReaderAssert}. */
@DisplayName("ReaderAssert showcase")
class ReaderAssertExample {

  /** A toy environment for the showcase. */
  record Config(String prefix, int limit) {}

  @Test
  @DisplayName("whenRunWith() supplies the environment and produces() asserts the result")
  void readsConfig() {
    Reader<Config, String> describe =
        Reader.of(cfg -> cfg.prefix() + "(limit=" + cfg.limit() + ")");

    assertThatReader(describe).whenRunWith(new Config("max", 100)).produces("max(limit=100)");
  }

  @Test
  @DisplayName("isPureWhenRunWith() asserts the reader ignores its environment")
  void constantReader() {
    Reader<Config, Integer> always7 = Reader.of(cfg -> 7);

    assertThatReader(always7).isPureWhenRunWith(new Config("ignored", -1));
  }

  @Test
  @DisplayName("isConstantFor() asserts both environments produce the same value")
  void constantAcrossEnvironments() {
    Reader<Config, Integer> always7 = Reader.of(cfg -> 7);

    assertThatReader(always7).isConstantFor(new Config("a", 1), new Config("b", 2));
  }

  @Test
  @DisplayName("Accepts Kind<ReaderKind.Witness<R>, A> directly without manual narrowing")
  void acceptsKindDirectly() {
    Reader<Config, Integer> reader = Reader.of(Config::limit);
    Kind<ReaderKind.Witness<Config>, Integer> kind = READER.widen(reader);

    assertThatReader(kind).whenRunWith(new Config("x", 99)).produces(99);
  }
}
