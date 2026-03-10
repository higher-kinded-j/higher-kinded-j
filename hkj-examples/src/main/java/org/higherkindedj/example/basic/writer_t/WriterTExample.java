// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.writer_t;

import static org.higherkindedj.hkt.writer_t.WriterTKindHelper.WRITER_T;

import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Pair;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.writer_t.WriterT;
import org.higherkindedj.hkt.writer_t.WriterTKind;
import org.higherkindedj.hkt.writer_t.WriterTMonad;

/**
 * Demonstrates WriterT, the monad transformer for accumulating output alongside computation.
 *
 * <p>WriterT wraps any monad F and adds output accumulation via a Monoid. Every step can append to
 * the log using tell(), and the accumulated output is combined automatically through flatMap
 * chains.
 *
 * <p>See <a href="https://higher-kinded-j.github.io/writert_transformer.html">WriterT
 * Transformer</a>
 */
public class WriterTExample {

  public static void main(String[] args) {
    new WriterTExample().run();
  }

  public void run() {
    var idMonad = IdMonad.instance();
    var writerMonad = new WriterTMonad<IdKind.Witness, List<String>>(idMonad, Monoids.list());

    System.out.println("=== WriterT Example ===\n");

    // --- Example 1: Basic audit trail ---

    System.out.println("--- Audit Trail ---");

    Kind<WriterTKind.Witness<IdKind.Witness, List<String>>, String> auditResult =
        For.from(writerMonad, writerMonad.tell(List.of("Starting order processing")))
            .from(_ -> writerMonad.tell(List.of("Validated payment info")))
            .from(_ -> writerMonad.tell(List.of("Reserved inventory")))
            .yield((_, _, _) -> "order-confirmed");

    var auditPair = unwrap(auditResult);
    System.out.println("Result: " + auditPair.first());
    System.out.println("Log: " + auditPair.second());

    // --- Example 2: Using listen() to observe output ---

    System.out.println("\n--- Listen: Observing Output ---");

    Kind<WriterTKind.Witness<IdKind.Witness, List<String>>, Integer> computation =
        For.from(writerMonad, writerMonad.tell(List.of("step 1")))
            .from(_ -> writerMonad.tell(List.of("step 2")))
            .yield((_, _) -> 42);

    var listened = writerMonad.listen(computation);
    var listenedPair = unwrap(listened);
    Pair<Integer, List<String>> innerPair = listenedPair.first();
    System.out.println("Value: " + innerPair.first());
    System.out.println("Observed output: " + innerPair.second());
    System.out.println("Accumulated output: " + listenedPair.second());

    // --- Example 3: Using censor() to redact sensitive data ---

    System.out.println("\n--- Censor: Redacting Sensitive Data ---");

    Kind<WriterTKind.Witness<IdKind.Witness, List<String>>, String> withSecrets =
        For.from(writerMonad, writerMonad.tell(List.of("User authenticated")))
            .from(_ -> writerMonad.tell(List.of("Token: sk_live_abc123xyz")))
            .from(_ -> writerMonad.tell(List.of("Query executed")))
            .yield((_, _, _) -> "success");

    var censored =
        writerMonad.censor(
            entries ->
                entries.stream()
                    .map(e -> e.startsWith("Token:") ? "Token: [REDACTED]" : e)
                    .toList(),
            withSecrets);

    var censoredPair = unwrap(censored);
    System.out.println("Result: " + censoredPair.first());
    System.out.println("Redacted log: " + censoredPair.second());

    // --- Example 4: Multi-step workflow with accumulated diagnostics ---

    System.out.println("\n--- Multi-step Workflow ---");

    var result = processWithDiagnostics(writerMonad, 100);
    var resultPair = unwrap(result);
    System.out.println("Final value: " + resultPair.first());
    System.out.println("Diagnostics: " + resultPair.second());
  }

  /** A multi-step computation that accumulates diagnostic output. */
  static Kind<WriterTKind.Witness<IdKind.Witness, List<String>>, Integer> processWithDiagnostics(
      WriterTMonad<IdKind.Witness, List<String>> w, int input) {
    return For.from(w, w.tell(List.of("Received input: " + input)))
        .let(_ -> input * 2)
        .from(t -> w.tell(List.of("Doubled: " + t._2())))
        .let(t -> t._2() + 10)
        .from(t -> w.tell(List.of("Added 10: " + t._4())))
        .yield((_, doubled, _, plusTen, _) -> plusTen);
  }

  /** Helper to unwrap a WriterT over Id to its Pair. */
  private <A> Pair<A, List<String>> unwrap(
      Kind<WriterTKind.Witness<IdKind.Witness, List<String>>, A> kind) {
    WriterT<IdKind.Witness, List<String>, A> writerT = WRITER_T.narrow(kind);
    return IdKindHelper.ID.unwrap(writerT.run());
  }
}
