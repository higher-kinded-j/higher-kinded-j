// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.vstream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.example.book.foundations.vstream.VStreamLinesBook.lines;
import static org.higherkindedj.hkt.assertions.TryAssert.assertThatTry;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vstream.VStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Holds the claims the VStream Resource-Safe Streaming page makes about reading a file. Whether the
 * reader is closed is read from the files the process has open, which Linux lists in {@code
 * /proc/self/fd}, so those tests are skipped where it does not.
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

    assertThat(stream.toList().run()).containsExactly("Ada,PAID", "Grace,NEW");
    Files.write(file, List.of("Alan,PAID", "Grace,NEW"));
    assertThat(stream.toList().run()).containsExactly("Alan,PAID", "Grace,NEW");
  }

  @Test
  @DisplayName("the reader is open while the lines are read, and closed at the end of the file")
  void closesAtTheEnd(@TempDir Path directory) throws IOException {
    Path file = directory.resolve("orders.csv");
    Files.write(file, List.of("Ada,PAID", "Grace,NEW", "Alan,PAID"));
    List<Long> openWhileRead = new CopyOnWriteArrayList<>();
    // Also skips the test where the open files cannot be read, before the stream runs
    assertThat(openHandles(file)).isZero();

    lines(file).peek(_ -> openWhileRead.add(openHandles(file))).toList().run();

    assertThat(openWhileRead).containsExactly(1L, 1L, 1L);
    assertThat(openHandles(file)).isZero();
  }

  @Test
  @DisplayName("take closes the reader once it has its lines")
  void takeCloses(@TempDir Path directory) throws IOException {
    Path file = directory.resolve("orders.csv");
    Files.write(file, List.of("Ada,PAID", "Grace,NEW", "Alan,PAID"));
    List<Long> openWhileRead = new CopyOnWriteArrayList<>();
    // Also skips the test where the open files cannot be read, before the stream runs
    assertThat(openHandles(file)).isZero();

    lines(file).peek(_ -> openWhileRead.add(openHandles(file))).take(2).toList().run();

    assertThat(openWhileRead).containsExactly(1L, 1L);
    assertThat(openHandles(file)).isZero();
  }

  @Test
  @DisplayName("a read that fails closes the reader, then fails with the read's exception")
  void aFailedReadCloses(@TempDir Path directory) throws IOException {
    Path file = directory.resolve("orders.csv");
    // 0xFF is not UTF-8, so the reader cannot decode the file
    Files.write(file, new byte[] {'A', 'd', 'a', '\n', (byte) 0xFF, '\n'});
    assertThat(openHandles(file)).isZero();

    Try<List<String>> read = lines(file).toList().runSafe();

    assertThat(read)
        .isInstanceOfSatisfying(
            Try.Failure.class,
            failure -> assertThat(failure.cause()).isInstanceOf(CharacterCodingException.class));
    assertThat(openHandles(file)).isZero();
  }

  @Test
  @DisplayName(
      "fromStream(Files.lines(file)) opens the file as it is built, and closes it when a run is"
          + " over")
  void fromStreamClosesTheFile(@TempDir Path directory) throws IOException {
    Path file = directory.resolve("orders.csv");
    Files.write(file, List.of("Ada,PAID", "Grace,NEW", "Alan,PAID"));
    // Also skips the test where the open files cannot be read, before the stream is built
    assertThat(openHandles(file)).isZero();

    VStream<String> stream = VStream.fromStream(Files.lines(file));
    long openOnceBuilt = openHandles(file);
    List<String> firstRun = stream.take(1).toList().run();

    assertThat(openOnceBuilt).isEqualTo(1L);
    assertThat(firstRun).containsExactly("Ada,PAID");
    assertThat(openHandles(file)).isZero();
  }

  @Test
  @DisplayName(
      "a run of fromStream(Files.lines(file)) after one that stopped early gives the lines already"
          + " buffered, then fails with UncheckedIOException")
  void laterRunReadsTheClosedFile(@TempDir Path directory) throws IOException {
    Path file = directory.resolve("orders.csv");
    // Far more lines than Files.lines buffers, so the later run reaches the closed file
    Files.write(file, IntStream.range(0, 100_000).mapToObj(n -> "order-" + n).toList());
    VStream<String> stream = VStream.fromStream(Files.lines(file));
    List<String> laterLines = new CopyOnWriteArrayList<>();

    List<String> firstRun = stream.take(1).toList().run();
    Try<List<String>> laterRun = stream.peek(laterLines::add).toList().runSafe();

    assertThat(firstRun).containsExactly("order-0");
    assertThatTry(laterRun).hasExceptionOfType(UncheckedIOException.class);
    assertThat(laterLines).isNotEmpty().hasSizeLessThan(99_999).startsWith("order-1");
  }

  @Test
  @DisplayName(
      "fromIterator over the lines' iterator reads on across runs, and leaves closing to you")
  void fromIteratorReadsOnAcrossRuns(@TempDir Path directory) throws IOException {
    Path file = directory.resolve("orders.csv");
    Files.write(file, IntStream.range(0, 100_000).mapToObj(n -> "order-" + n).toList());

    try (Stream<String> javaLines = Files.lines(file)) {
      VStream<String> stream = VStream.fromIterator(javaLines.iterator());

      assertThat(stream.take(1).toList().run()).containsExactly("order-0");
      List<String> laterRun = stream.toList().run();
      assertThat(laterRun).hasSize(99_999).startsWith("order-1");
    }
  }

  @Test
  @DisplayName(
      "a run of fromStream(Files.lines(file)) on an interrupted thread leaves the file open, and"
          + " closing the stream closes it")
  void interruptedRunLeavesTheFileOpen(@TempDir Path directory) throws IOException {
    Path file = directory.resolve("orders.csv");
    Files.write(file, List.of("Ada,PAID", "Grace,NEW", "Alan,PAID"));
    // Also skips the test where the open files cannot be read, before the stream is built
    assertThat(openHandles(file)).isZero();
    VStream<String> stream = VStream.fromStream(Files.lines(file));

    Try<List<String>> interrupted;
    Thread.currentThread().interrupt();
    try {
      interrupted = stream.toList().runSafe();
    } finally {
      Thread.interrupted();
    }
    long openAfterTheRun = openHandles(file);
    stream.close().run();

    assertThatTry(interrupted).hasExceptionOfType(InterruptedException.class);
    assertThat(openAfterTheRun).isEqualTo(1L);
    assertThat(openHandles(file)).isZero();
  }

  /** How many of this process's open files are the given file, as Linux lists them. */
  private static long openHandles(Path file) {
    Path descriptors = Path.of("/proc/self/fd");
    assumeTrue(Files.isDirectory(descriptors), "the open files are read from /proc/self/fd");
    try (Stream<Path> each = Files.list(descriptors)) {
      Path real = file.toRealPath();
      return each.filter(descriptor -> names(descriptor, real)).count();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static boolean names(Path descriptor, Path file) {
    try {
      return Files.readSymbolicLink(descriptor).equals(file);
    } catch (IOException e) {
      // A descriptor closed since the listing names nothing
      return false;
    }
  }
}
