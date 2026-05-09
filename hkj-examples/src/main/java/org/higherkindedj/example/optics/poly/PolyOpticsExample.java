// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.poly;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either.EitherMonad;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedMonad;
import org.higherkindedj.optics.Optic;
import org.higherkindedj.optics.poly.Optics;
import org.higherkindedj.optics.poly.PolyOptics;

/**
 * Demonstrates the polymorphic optics surface in {@link org.higherkindedj.optics.poly}.
 *
 * <p>The example walks through three small scenarios in increasing order of richness:
 *
 * <ol>
 *   <li>Building a polymorphic lens for a generic wrapper.
 *   <li>Building a polymorphic iso for a single-field wrapper and changing its element type.
 *   <li>Composing a monomorphic head ({@code polyLens}) with a typeclass-driven leaf ({@link
 *       Optics#traversed}) to parse a list of strings into a list of integers, accumulating errors
 *       with {@code Validated}.
 * </ol>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run -PmainClass=
 * org.higherkindedj.example.optics.poly.PolyOpticsExample}.
 */
public final class PolyOpticsExample {

  // -------------------------------------------------------------------------
  // Domain model
  // -------------------------------------------------------------------------

  /** A generic single-field wrapper that we can operate on with a polymorphic lens. */
  public record Box<A>(A value) {}

  /** A generic ID wrapper used to demonstrate polyIso changing the underlying type. */
  public record UserId<A>(A raw) {}

  /** A page of rows used to demonstrate composition of monomorphic head + polymorphic leaf. */
  public record Page<A>(int number, List<A> rows) {}

  private PolyOpticsExample() {}

  // -------------------------------------------------------------------------
  // Scenario 1: polyLens over a generic Box
  // -------------------------------------------------------------------------

  static void scenario1_polyLensOverBox() {
    Optic<Box<String>, Box<Integer>, String, Integer> contents =
        PolyOptics.polyLens(Box::value, (b, n) -> new Box<>(n));

    Box<String> stringBox = new Box<>("42");
    Box<Integer> intBox = PolyOptics.modify(contents, Integer::parseInt, stringBox);

    System.out.println("Scenario 1: polyLens over Box<A>");
    System.out.println("  before: " + stringBox);
    System.out.println("  after : " + intBox);
    System.out.println("  get   : " + PolyOptics.get(contents, stringBox));
    System.out.println();
  }

  // -------------------------------------------------------------------------
  // Scenario 2: polyIso over a UserId wrapper
  // -------------------------------------------------------------------------

  static void scenario2_polyIsoOverUserId() {
    Optic<UserId<String>, UserId<Integer>, String, Integer> raw =
        PolyOptics.polyIso(UserId::raw, UserId::new);

    UserId<String> textual = new UserId<>("123");
    UserId<Integer> numeric = PolyOptics.modify(raw, Integer::parseInt, textual);

    System.out.println("Scenario 2: polyIso over UserId<A>");
    System.out.println("  before: " + textual);
    System.out.println("  after : " + numeric);
    System.out.println("  set   : " + PolyOptics.set(raw, 7, textual));
    System.out.println();
  }

  // -------------------------------------------------------------------------
  // Scenario 3: monomorphic head + polymorphic leaf via Optics.traversed
  // -------------------------------------------------------------------------

  static void scenario3_traversedLeafAccumulatesErrors() {
    // Lens-shaped polymorphic head onto Page::rows.
    Optic<Page<String>, Page<Integer>, List<String>, List<Integer>> rowsLens =
        PolyOptics.polyLens(Page::rows, (p, rs) -> new Page<>(p.number(), rs));

    // Bridge plain List <-> Kind<ListKind.Witness, ?> so we can reach Optics.traversed.
    Optic<
            List<String>,
            List<Integer>,
            Kind<ListKind.Witness, String>,
            Kind<ListKind.Witness, Integer>>
        listAsKind = PolyOptics.polyIso(LIST::widen, LIST::narrow);

    // Polymorphic leaf: traversed list elements that may change type.
    Optic<Kind<ListKind.Witness, String>, Kind<ListKind.Witness, Integer>, String, Integer>
        allRows = Optics.traversed(ListTraverse.INSTANCE);

    Optic<Page<String>, Page<Integer>, String, Integer> pageRows =
        rowsLens.andThen(listAsKind).andThen(allRows);

    Function<String, Kind<ValidatedKind.Witness<List<String>>, Integer>> parseValidated =
        s -> {
          try {
            return VALIDATED.widen(Validated.valid(Integer.parseInt(s)));
          } catch (NumberFormatException e) {
            return VALIDATED.widen(Validated.invalid(List.of("bad row: " + s)));
          }
        };

    ValidatedMonad<List<String>> validatedApp = ValidatedMonad.instance(Semigroups.list());

    Page<String> validInput = new Page<>(1, List.of("1", "2", "3"));
    Page<String> mixedInput = new Page<>(2, List.of("1", "x", "3", "y"));

    Kind<ValidatedKind.Witness<List<String>>, Page<Integer>> ok =
        PolyOptics.modifyF(pageRows, parseValidated, validInput, validatedApp);
    Kind<ValidatedKind.Witness<List<String>>, Page<Integer>> mixed =
        PolyOptics.modifyF(pageRows, parseValidated, mixedInput, validatedApp);

    System.out.println("Scenario 3: monomorphic head + polymorphic leaf (Validated)");
    System.out.println("  valid  : " + VALIDATED.narrow(ok));
    System.out.println("  errors : " + VALIDATED.narrow(mixed).getError());
    System.out.println();
  }

  // -------------------------------------------------------------------------
  // Scenario 4: Optics.mapped under Either (a Functor application)
  // -------------------------------------------------------------------------

  static void scenario4_mappedOverEither() {
    Optic<
            Kind<EitherKind.Witness<String>, String>,
            Kind<EitherKind.Witness<String>, Integer>,
            String,
            Integer>
        rightContent = Optics.mapped(EitherMonad.<String>instance());

    Kind<EitherKind.Witness<String>, Integer> right =
        PolyOptics.modify(rightContent, String::length, EITHER.widen(Either.right("hello")));
    Kind<EitherKind.Witness<String>, Integer> left =
        PolyOptics.modify(rightContent, String::length, EITHER.widen(Either.left("missing")));

    System.out.println("Scenario 4: Optics.mapped over Either<String, ?>");
    System.out.println("  right : " + EITHER.narrow(right));
    System.out.println("  left  : " + EITHER.narrow(left));
    System.out.println();
  }

  public static void main(String[] args) {
    System.out.println("== PolyOptics Example ==");
    System.out.println();
    scenario1_polyLensOverBox();
    scenario2_polyIsoOverUserId();
    scenario3_traversedLeafAccumulatesErrors();
    scenario4_mappedOverEither();
  }
}
