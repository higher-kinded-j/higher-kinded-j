// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.vstream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.example.book.foundations.vstream.VStreamLinesBook.lines;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.higherkindedj.hkt.vstream.VStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Holds the claims the VStream Resource-Safe Streaming page makes about reading a file. That the
 * reader is closed however a run ends is held by {@code VStreamResourcesBookTest}, for any bracket.
 */
@DisplayName("VStream: a file, a line at a time")
class VStreamLinesBookTest {

  @Test
  @DisplayName("lines gives each line of the file, and take stops at its count")
  void readsLines(@TempDir Path directory) throws IOException {
    Path file = directory.resolve("orders.csv");
    Files.write(file, List.of("Ada,PAID", "Grace,NEW", "Alan,PAID"));

    assertThat(lines(file).toList().run()).containsExactly("Ada,PAID", "Grace,NEW", "Alan,PAID");
    assertThat(lines(file).take(2).toList().run()).containsExactly("Ada,PAID", "Grace,NEW");
  }

  @Test
  @DisplayName("each run opens the file afresh, so a stream can be read twice")
  void eachRunReadsAfresh(@TempDir Path directory) throws IOException {
    Path file = directory.resolve("orders.csv");
    Files.write(file, List.of("Ada,PAID", "Grace,NEW"));
    VStream<String> stream = lines(file);

    assertThat(stream.toList().run()).hasSize(2);
    assertThat(stream.toList().run()).hasSize(2);
  }
}
