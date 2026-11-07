// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.vstream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * The file example on the book's <a
 * href="https://higher-kinded-j.github.io/latest/monads/vstream_resources.html">Resource-Safe
 * Streaming</a> page: a file read a line at a time, its reader opened when a run starts and closed
 * however the run ends. It stands apart from {@code VStreamResourcesBook}, whose {@code Path} is
 * the Effect Path factory, not {@code java.nio.file.Path}. {@code VStreamResourcesBookTest} holds
 * the claims, and the book's output gate runs {@link #main} against the comments in the region.
 */
public final class VStreamLinesBook {

  private VStreamLinesBook() {}

  // ANCHOR: lines
  // Each line of a file. The reader is opened when a run starts, and closed when the run
  // reaches the end of the file, fails, or stops early
  static VStream<String> lines(Path file) {
    return VStream.bracket(
        VTask.of(() -> Files.newBufferedReader(file)),
        reader ->
            VStream.unfold(
                reader,
                current ->
                    VTask.of(
                        () ->
                            Optional.ofNullable(current.readLine())
                                .map(line -> new VStream.Seed<>(line, current)))),
        VStreamResourcesBook::closing);
  }

  // ANCHOR_END: lines

  public static void main(String[] args) throws IOException {
    Path ordersCsv = Files.createTempFile("orders", ".csv");
    try {
      // ANCHOR: lines_used
      // A file of three lines: Ada,PAID then Grace,NEW then Alan,PAID
      Files.write(ordersCsv, List.of("Ada,PAID", "Grace,NEW", "Alan,PAID"));
      List<String> firstTwo = lines(ordersCsv).take(2).toList().run();
      // [Ada,PAID, Grace,NEW]
      // ANCHOR_END: lines_used
      System.out.println(firstTwo);
    } finally {
      Files.delete(ordersCsv);
    }
  }
}
