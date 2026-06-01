// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.contract;

import java.util.List;
import org.higherkindedj.hkt.maybe.Maybe;

/**
 * Shared terminal-{@code Builder} plumbing for the per-family {@link TypeClassContract} contracts.
 *
 * <p>Each contract's {@code Builder} extends this and supplies the family-specific {@link
 * #checks(List)}; the category selection ({@link #verify()} / {@link #verifyOnly}), the engine run
 * label, and the optional-law guard ({@link #requireLaws}) live here so they are defined once and
 * stay consistent across all algebras.
 */
abstract class AbstractContractBuilder {

  private final String family;
  private final Class<?> contextClass;

  AbstractContractBuilder(String family, Class<?> contextClass) {
    this.family = family;
    this.contextClass = contextClass;
  }

  /** Runs every category: operations, validations, exceptions and (where applicable) laws. */
  public final void verify() {
    ContractEngine.run(label(), checks(List.of(Category.values())));
  }

  /** Runs only the selected categories. */
  public final void verifyOnly(Category... categories) {
    ContractEngine.run(label(), checks(List.of(categories)));
  }

  /** The family-specific checks for the selected categories. */
  protected abstract List<Check> checks(List<Category> selected);

  /** Label for engine output, e.g. {@code "Functor[MaybeFunctor]"}. */
  protected final String label() {
    return family + "[" + contextClass.getSimpleName() + "]";
  }

  /**
   * Resolves an optional law-configuration value, throwing a helpful {@link IllegalStateException}
   * (naming the {@code setter} that supplies it) when {@link Category#LAWS} is exercised without
   * it.
   */
  protected final <T> T requireLaws(Maybe<T> configured, String setter) {
    return configured.orElseGet(
        () -> {
          throw new IllegalStateException(label() + ": law checks require ." + setter + "(...)");
        });
  }
}
