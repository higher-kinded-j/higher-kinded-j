// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.trampoline;

import static org.higherkindedj.hkt.instances.Witnesses.io;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;

import java.util.List;
import java.util.stream.IntStream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.TrampolinePath;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.trampoline.Trampoline;
import org.higherkindedj.hkt.trampoline.TrampolineUtils;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/monads/trampoline_monad.html">Trampoline</a> page:
 * a recursive sum that overflows the stack, the same sum on a {@code TrampolinePath} and on a
 * {@code Trampoline}, two functions that call each other, and a long list traversed under {@code
 * IO}. The sums use plain numbers rather than the order cast, since the depth of the recursion is
 * the point. The page {@code {{#include}}}s the anchored regions, {@code TrampolineBookTest} holds
 * the claims the page makes, and the book's output gate runs {@link #main} against the comments in
 * the regions.
 */
public final class TrampolineBook {

  private TrampolineBook() {}

  // ANCHOR: naive
  // Each call waits for the deeper call it makes, so each adds a frame to the stack
  static long sum(long n) {
    if (n <= 0) {
      return 0;
    }
    return n + sum(n - 1);
  }

  // ANCHOR_END: naive

  // ANCHOR: path
  // The same sum, held as steps: defer the deeper call, then map its result
  static TrampolinePath<Long> sumPath(long n) {
    if (n <= 0) {
      return Path.trampolineDone(0L);
    }
    return Path.trampolineDefer(() -> sumPath(n - 1)).map(deeper -> n + deeper);
  }

  // ANCHOR_END: path

  // ANCHOR: tail
  // A tail call: the running total travels with the call, so only defer is needed
  static Trampoline<Long> sumTail(long n, long total) {
    if (n <= 0) {
      return Trampoline.done(total);
    }
    return Trampoline.defer(() -> sumTail(n - 1, total + n));
  }

  // ANCHOR_END: tail

  // ANCHOR: mutual
  // Two functions that call each other, each kept as it is written
  static Trampoline<Boolean> isEven(long n) {
    return n == 0 ? Trampoline.done(true) : Trampoline.defer(() -> isOdd(n - 1));
  }

  static Trampoline<Boolean> isOdd(long n) {
    return n == 0 ? Trampoline.done(false) : Trampoline.defer(() -> isEven(n - 1));
  }

  // ANCHOR_END: mutual

  public static void main(String[] args) {
    // ANCHOR: path_run
    long total = sumPath(1_000_000).run();
    // 500000500000
    // ANCHOR_END: path_run
    System.out.println(total);

    // ANCHOR: tail_run
    long sameTotal = sumTail(1_000_000, 0).run();
    // 500000500000
    // ANCHOR_END: tail_run
    System.out.println(sameTotal);

    // ANCHOR: mutual_run
    boolean even = isEven(1_000_001).run();
    // false
    // ANCHOR_END: mutual_run
    System.out.println(even);

    // ANCHOR: traverse
    List<Integer> lines = IntStream.rangeClosed(1, 100_000).boxed().toList();

    // Combines the IO steps as a balanced tree, so the nesting grows with the log of the length
    Kind<IOKind.Witness, List<String>> labelled =
        TrampolineUtils.traverseListStackSafe(
            lines, n -> IO_OP.widen(IO.delay(() -> "line " + n)), Instances.monad(io()));

    int labels = IO_OP.narrow(labelled).unsafeRunSync().size();
    // 100000
    // ANCHOR_END: traverse
    System.out.println(labels);
  }
}
