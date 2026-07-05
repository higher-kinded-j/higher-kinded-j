// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import static org.higherkindedj.optics.edit.Edit.modify;
import static org.higherkindedj.optics.edit.Edit.modifyIfPresent;
import static org.higherkindedj.optics.edit.Edit.parseIfPresent;
import static org.higherkindedj.optics.edit.Edit.setIfPresent;

import org.higherkindedj.hkt.Update;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.edit.Edits;
import org.higherkindedj.optics.focus.FocusPath;
import org.jspecify.annotations.Nullable;

/**
 * Demonstrates the {@code Edits} builder: N independent edits at different optic paths folded into
 * one reusable operation — including the sparse, all-errors-at-once REST {@code PATCH} shape.
 *
 * <p>Key concepts demonstrated:
 *
 * <ul>
 *   <li>{@code Edits.combine}: pure edits folded into one reusable {@code Update} (compile-time
 *       purity — a fallible edit does not fit)
 *   <li>Sparse updates: {@code …IfPresent} treats {@code null} as absent, contributing the identity
 *       update, so nullable DTO fields land one-to-one with no {@code if} ceremony
 *   <li>{@code Edits.accumulate}: every incoming value validated independently, every bad field
 *       reported at once as located {@code FieldError}s
 *   <li>Reuse: one accumulated patch, validated once, applied to many sources
 * </ul>
 */
public final class EditsExample {

  @GenerateLenses
  public record EditOrder(String email, String sku, int quantity) {}

  /** A sparse PATCH request: {@code null} means "no change requested". */
  public record PatchRequest(
      @Nullable String email, @Nullable String sku, @Nullable Integer qtyDelta) {}

  private static final FocusPath<EditOrder, String> EMAIL = FocusPath.of(EditOrderLenses.email());
  private static final FocusPath<EditOrder, String> SKU = FocusPath.of(EditOrderLenses.sku());
  private static final FocusPath<EditOrder, Integer> QUANTITY =
      FocusPath.of(EditOrderLenses.quantity());

  public static void main(String[] args) {
    demonstratePureMultiEdit();
    demonstrateSparsePatch();
    demonstrateAllErrorsAtOnce();
    demonstrateReusablePatch();
  }

  /**
   * Demonstrates folding pure edits into one named, reusable {@link Update}.
   *
   * <p>Each edit pairs an optic path with a write; {@code combine} folds them left to right via the
   * {@code Monoids.update()} monoid. Only pure edits fit — a fallible edit is rejected at compile
   * time.
   */
  private static void demonstratePureMultiEdit() {
    System.out.println("=== Pure Multi-Edit Example ===");

    Update<EditOrder> tidy =
        Edits.combine(modify(EMAIL, String::toLowerCase), modify(SKU, String::trim));

    EditOrder orderA = new EditOrder("Alice@Example.COM", " ab-123 ", 2);
    EditOrder orderB = new EditOrder("BOB@example.com", " cd-456", 1);

    System.out.println("Tidied A: " + tidy.apply(orderA));
    System.out.println("Tidied B: " + tidy.apply(orderB));
    System.out.println("Expected: both orders tidied by the same reusable update\n");
  }

  /**
   * Demonstrates a sparse PATCH: only the fields present on the request change.
   *
   * <p>{@code setIfPresent}/{@code modifyIfPresent}/{@code parseIfPresent} treat {@code null} as
   * absent — the edit contributes the identity update and, for {@code parseIfPresent}, the parser
   * is not even invoked.
   */
  private static void demonstrateSparsePatch() {
    System.out.println("=== Sparse PATCH Example ===");

    // Only qtyDelta is present; email and sku are absent.
    PatchRequest request = new PatchRequest(null, null, 3);
    EditOrder order = new EditOrder("carol@example.com", "gh-012", 5);

    Validated<NonEmptyList<FieldError>, EditOrder> result = patch(request, order);

    System.out.println("Patched: " + result);
    System.out.println("Expected: Valid - only quantity changed (5 + 3 = 8)\n");
  }

  /**
   * Demonstrates all-errors-at-once validation: every bad field is reported, located by its {@code
   * .at(label)} tag, in edit order.
   */
  private static void demonstrateAllErrorsAtOnce() {
    System.out.println("=== All Errors At Once Example ===");

    // Both email and sku are present and both are invalid.
    PatchRequest request = new PatchRequest("not-an-email", "x", null);
    EditOrder order = new EditOrder("carol@example.com", "gh-012", 5);

    Validated<NonEmptyList<FieldError>, EditOrder> result = patch(request, order);

    System.out.println("Patched: " + result);
    System.out.println("Expected: Invalid listing BOTH \"email: ...\" and \"sku: ...\"\n");
  }

  /**
   * Demonstrates that an accumulated patch validates once and applies to many sources — and that
   * {@code applyPath} is the {@code ValidationPath} twin for railway-style pipelines.
   */
  private static void demonstrateReusablePatch() {
    System.out.println("=== Reusable Patch Example ===");

    Edits.Accumulated<EditOrder> renumber =
        Edits.accumulate(parseIfPresent(SKU, "WH-9 ", EditsExample::parseSku).at("sku"));

    EditOrder first = new EditOrder("a@example.com", "old-1", 1);
    EditOrder second = new EditOrder("b@example.com", "old-2", 2);

    System.out.println("First:  " + renumber.apply(first));
    System.out.println("Second: " + renumber.apply(second));
    System.out.println("On the railway: " + renumber.applyPath(first).run());
    System.out.println("Expected: the same validated patch re-applied to both orders");
  }

  /** Applies a sparse PATCH request: absent fields contribute the identity update. */
  private static Validated<NonEmptyList<FieldError>, EditOrder> patch(
      PatchRequest request, EditOrder order) {
    return Edits.accumulate(
            parseIfPresent(EMAIL, request.email(), EditsExample::parseEmail).at("email"),
            parseIfPresent(SKU, request.sku(), EditsExample::parseSku).at("sku"),
            modifyIfPresent(QUANTITY, request.qtyDelta(), (delta, qty) -> qty + delta),
            setIfPresent(EMAIL, (String) null)) // a fully absent slot: contributes nothing
        .apply(order);
  }

  private static Validated<NonEmptyList<FieldError>, String> parseEmail(String raw) {
    return raw.contains("@")
        ? Validated.validNel(raw.trim().toLowerCase())
        : Validated.invalidNel(FieldError.of("not an email address"));
  }

  private static Validated<NonEmptyList<FieldError>, String> parseSku(String raw) {
    String trimmed = raw.trim();
    return trimmed.length() >= 4
        ? Validated.validNel(trimmed.toUpperCase())
        : Validated.invalidNel(FieldError.of("SKU must have at least 4 characters"));
  }

  private EditsExample() {}
}
