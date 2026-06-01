// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.contract;

import java.util.List;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;

/**
 * Generic runner shared by every type-class contract.
 *
 * <p>Each {@link Check} is run and reified into a list of failure messages — empty when it passes.
 * Those lists accumulate by concatenation under {@link Monoids#list()}, so an empty result means
 * every check passed and a non-empty one carries <em>all</em> failures, not just the first. This is
 * the single place test execution lives; per-family contracts only describe their checks.
 *
 * <p>This class is the imperative shell: it runs side-effecting, throwing checks and turns their
 * outcomes into values. The {@code catch (Throwable)} in {@link #runOne} is deliberate and must not
 * be narrowed to {@code Exception} nor replaced with {@code Try.of} — AssertJ signals failure with
 * {@link AssertionError} (an {@link Error}), which those would let escape uncaught.
 */
final class ContractEngine {

  /**
   * Failure messages accumulate by list concatenation; the empty list means "all checks passed".
   */
  private static final Monoid<List<String>> FAILURES = Monoids.list();

  private ContractEngine() {}

  static void run(String contractName, List<Check> checks) {
    List<String> failures =
        FAILURES.combineAll(checks.stream().map(ContractEngine::runOne).toList());
    if (!failures.isEmpty()) {
      raise(contractName, checks.size(), failures);
    }
  }

  /** Runs one check, reifying a thrown error into a single-element failure list. */
  private static List<String> runOne(Check check) {
    try {
      check.body().run();
      return List.of(); // pass → the Monoid identity (empty)
    } catch (Throwable t) { // not Exception: AssertJ fails with AssertionError, an Error
      return List.of("[" + check.category() + "] " + check.name() + " — " + t);
    }
  }

  private static void raise(String contractName, int total, List<String> failures) {
    throw new AssertionError(
        contractName
            + ": "
            + failures.size()
            + "/"
            + total
            + " checks failed:\n  - "
            + String.join("\n  - ", failures));
  }
}
