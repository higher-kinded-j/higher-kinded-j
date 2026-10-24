// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

/** Tests for {@link ReplaceUntypedPathShortcutsRecipe}. */
class ReplaceUntypedPathShortcutsRecipeTest implements RewriteTest {

  // The three paths as 0.5.x declares them, each shortcut beside its typed form, with a navigator
  // base that overrides the shortcuts, as the real ones do.
  private static final String PATH_METHODS =
      "   @Deprecated <E> %1$s<S, E> each();"
          + "   <E> TraversalPath<S, E> each(Each<A, E> eachInstance);"
          + "   @Deprecated <E> %2$s<S, E> at(int index);"
          + "   @Deprecated <K, V> %2$s<S, V> atKey(K key);"
          + "   @Deprecated <E> %2$s<S, E> some();"
          + "   <B> %2$s<S, B> some(Affine<A, B> affine);"
          + "   @Deprecated <F, E> TraversalPath<S, E> traverseOver(Traverse<F> traverse);"
          + "   @Deprecated <E> %2$s<S, E> head();"
          + "   @Deprecated <E> %2$s<S, E> last();"
          + "   @Deprecated <E> %2$s<S, List<E>> tail();"
          + "   @Deprecated <E> %2$s<S, List<E>> init();"
          + "   @Deprecated <E> %2$s<S, Pair<E, List<E>>> cons();"
          + "   @Deprecated <E> %2$s<S, Pair<E, List<E>>> headTail();"
          + "   @Deprecated <E> %2$s<S, Pair<List<E>, E>> snoc();"
          + "   @Deprecated <E> %2$s<S, Pair<List<E>, E>> initLast();"
          + "   <B> %2$s<S, B> via(Affine<A, B> affine);"
          + "   <B> %2$s<S, B> via(Prism<A, B> prism);";

  private static final String PATH_IMPORTS =
      "package org.higherkindedj.optics.focus;"
          + " import java.util.List;"
          + " import org.higherkindedj.hkt.Traverse;"
          + " import org.higherkindedj.optics.*;"
          + " import org.higherkindedj.optics.indexed.Pair;";

  private static final String[] HKJ_STUBS = {
    "package org.higherkindedj.hkt; public interface Kind<F, A> {}",
    "package org.higherkindedj.hkt; public interface Traverse<F> {}",
    "package org.higherkindedj.hkt.list; public final class ListKind {"
        + " public static final class Witness {} }",
    "package org.higherkindedj.hkt.list;"
        + " public enum ListTraverse implements org.higherkindedj.hkt.Traverse<ListKind.Witness> {"
        + " INSTANCE }",
    "package org.higherkindedj.hkt.maybe; public final class MaybeKind {"
        + " public static final class Witness {} }",
    "package org.higherkindedj.hkt.maybe;"
        + " public enum MaybeTraverse implements org.higherkindedj.hkt.Traverse<MaybeKind.Witness> {"
        + " INSTANCE }",
    "package org.higherkindedj.optics; public interface Lens<S, A> {}",
    "package org.higherkindedj.optics; public interface Affine<S, A> {}",
    "package org.higherkindedj.optics; public interface Prism<S, A> {}",
    "package org.higherkindedj.optics; public interface Traversal<S, A> {}",
    "package org.higherkindedj.optics; public interface Each<S, A> { Traversal<S, A> each(); }",
    "package org.higherkindedj.optics.indexed; public record Pair<A, B>(A first, B second) {}",
    "package org.higherkindedj.optics.each;"
        + " import java.util.List;"
        + " import org.higherkindedj.hkt.Kind;"
        + " import org.higherkindedj.hkt.Traverse;"
        + " import org.higherkindedj.optics.Each;"
        + " public final class EachInstances {"
        + "   public static <A> Each<List<A>, A> listEach() { return null; }"
        + "   public static <F, A> Each<Kind<F, A>, A> fromTraverse(Traverse<F> traverse) {"
        + "     return null; } }",
    "package org.higherkindedj.optics.focus;"
        + " import java.util.*;"
        + " import org.higherkindedj.optics.*;"
        + " import org.higherkindedj.optics.indexed.Pair;"
        + " public final class FocusPaths {"
        + "   public static <E> Affine<Optional<E>, E> optionalSome() { return null; }"
        + "   public static <E> Affine<List<E>, E> listAt(int index) { return null; }"
        + "   public static <K, V> Affine<Map<K, V>, V> mapAt(K key) { return null; }"
        + "   public static <E> Affine<List<E>, E> listHead() { return null; }"
        + "   public static <E> Affine<List<E>, E> listLast() { return null; }"
        + "   public static <E> Affine<List<E>, List<E>> listTail() { return null; }"
        + "   public static <E> Affine<List<E>, List<E>> listInit() { return null; }"
        + "   public static <E> Prism<List<E>, Pair<E, List<E>>> listCons() { return null; }"
        + "   public static <E> Prism<List<E>, Pair<E, List<E>>> listHeadTail() { return null; }"
        + "   public static <E> Prism<List<E>, Pair<List<E>, E>> listSnoc() { return null; }"
        + "   public static <E> Prism<List<E>, Pair<List<E>, E>> listInitLast() {"
        + "     return null; } }",
    PATH_IMPORTS
        + " public interface FocusPath<S, A> {"
        + "   static <S, A> FocusPath<S, A> of(Lens<S, A> lens) { return null; }"
        + "   <B> FocusPath<S, B> via(Lens<A, B> lens);"
        + PATH_METHODS.formatted("TraversalPath", "AffinePath")
        + " }",
    PATH_IMPORTS
        + " public interface AffinePath<S, A> {"
        + "   <B> AffinePath<S, B> via(Lens<A, B> lens);"
        + PATH_METHODS.formatted("TraversalPath", "AffinePath")
        + " }",
    PATH_IMPORTS
        + " public interface TraversalPath<S, A> {"
        + "   <B> TraversalPath<S, B> via(Lens<A, B> lens);"
        + "   List<A> getAll(S source);"
        + PATH_METHODS.formatted("TraversalPath", "TraversalPath")
        + " }",
    "package org.higherkindedj.optics.focus;"
        + " public abstract class NavigatorFocusPath<S, A> implements FocusPath<S, A> {"
        + "   @Deprecated @Override public final <E> TraversalPath<S, E> each() { return null; }"
        + "   @Deprecated @Override public final <E> AffinePath<S, E> last() { return null; } }",
  };

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipe(new ReplaceUntypedPathShortcutsRecipe())
        .parser(JavaParser.fromJavaVersion().dependsOn(HKJ_STUBS));
  }

  @Test
  @DisplayName("each() and some() take their typed instances, and the imports are added")
  void eachAndSomeTakeTheirInstances() {
    rewriteRun(
        java(
            """
            package com.example;

            import java.util.List;
            import java.util.Optional;
            import org.higherkindedj.optics.Lens;
            import org.higherkindedj.optics.focus.AffinePath;
            import org.higherkindedj.optics.focus.FocusPath;
            import org.higherkindedj.optics.focus.TraversalPath;

            class Usage {
                TraversalPath<String, Integer> all(Lens<String, List<Integer>> lens) {
                    return FocusPath.of(lens).each();
                }

                AffinePath<String, Integer> present(Lens<String, Optional<Integer>> lens) {
                    return FocusPath.of(lens).some();
                }
            }
            """,
            """
            package com.example;

            import java.util.List;
            import java.util.Optional;
            import org.higherkindedj.optics.Lens;
            import org.higherkindedj.optics.each.EachInstances;
            import org.higherkindedj.optics.focus.AffinePath;
            import org.higherkindedj.optics.focus.FocusPath;
            import org.higherkindedj.optics.focus.FocusPaths;
            import org.higherkindedj.optics.focus.TraversalPath;

            class Usage {
                TraversalPath<String, Integer> all(Lens<String, List<Integer>> lens) {
                    return FocusPath.of(lens).each(EachInstances.listEach());
                }

                AffinePath<String, Integer> present(Lens<String, Optional<Integer>> lens) {
                    return FocusPath.of(lens).some(FocusPaths.optionalSome());
                }
            }
            """));
  }

  @Test
  @DisplayName("a chain loses the type witnesses its shortcuts needed")
  void aChainLosesItsWitnesses() {
    rewriteRun(
        java(
            """
            package com.example;

            import java.util.List;
            import java.util.Optional;
            import org.higherkindedj.optics.Lens;
            import org.higherkindedj.optics.focus.FocusPath;
            import org.higherkindedj.optics.focus.TraversalPath;

            class Usage {
                record Dept(Optional<String> manager) {}

                TraversalPath<String, String> managers(
                        Lens<String, List<Dept>> depts, Lens<Dept, Optional<String>> manager) {
                    return FocusPath.of(depts).<Dept>each().via(manager).<String>some();
                }
            }
            """,
            """
            package com.example;

            import java.util.List;
            import java.util.Optional;
            import org.higherkindedj.optics.Lens;
            import org.higherkindedj.optics.each.EachInstances;
            import org.higherkindedj.optics.focus.FocusPath;
            import org.higherkindedj.optics.focus.FocusPaths;
            import org.higherkindedj.optics.focus.TraversalPath;

            class Usage {
                record Dept(Optional<String> manager) {}

                TraversalPath<String, String> managers(
                        Lens<String, List<Dept>> depts, Lens<Dept, Optional<String>> manager) {
                    return FocusPath.of(depts).each(EachInstances.listEach()).via(manager).some(FocusPaths.optionalSome());
                }
            }
            """));
  }

  @Test
  @DisplayName("at and atKey compose the FocusPaths optic for the position, keeping its argument")
  void atAndAtKeyComposeAnOptic() {
    rewriteRun(
        java(
            """
            package com.example;

            import java.util.List;
            import java.util.Map;
            import org.higherkindedj.optics.Lens;
            import org.higherkindedj.optics.focus.AffinePath;
            import org.higherkindedj.optics.focus.FocusPath;

            class Usage {
                AffinePath<String, Integer> first(Lens<String, List<Integer>> lens, int index) {
                    return FocusPath.of(lens).<Integer>at(index /* from the request */);
                }

                AffinePath<String, Integer> price(Lens<String, Map<String, Integer>> lens) {
                    return FocusPath.of(lens).atKey("LAMP");
                }
            }
            """,
            """
            package com.example;

            import java.util.List;
            import java.util.Map;
            import org.higherkindedj.optics.Lens;
            import org.higherkindedj.optics.focus.AffinePath;
            import org.higherkindedj.optics.focus.FocusPath;
            import org.higherkindedj.optics.focus.FocusPaths;

            class Usage {
                AffinePath<String, Integer> first(Lens<String, List<Integer>> lens, int index) {
                    return FocusPath.of(lens).via(FocusPaths.listAt(index /* from the request */));
                }

                AffinePath<String, Integer> price(Lens<String, Map<String, Integer>> lens) {
                    return FocusPath.of(lens).via(FocusPaths.mapAt("LAMP"));
                }
            }
            """));
  }

  @Test
  @DisplayName("traverseOver becomes each with an Each built from the Traverse")
  void traverseOverBecomesEachFromTraverse() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.Kind;
            import org.higherkindedj.hkt.list.ListKind;
            import org.higherkindedj.hkt.list.ListTraverse;
            import org.higherkindedj.optics.focus.FocusPath;
            import org.higherkindedj.optics.focus.TraversalPath;

            class Usage {
                TraversalPath<String, Integer> all(FocusPath<String, Kind<ListKind.Witness, Integer>> path) {
                    return path.<ListKind.Witness, Integer>traverseOver(ListTraverse.INSTANCE);
                }
            }
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.Kind;
            import org.higherkindedj.hkt.list.ListKind;
            import org.higherkindedj.hkt.list.ListTraverse;
            import org.higherkindedj.optics.each.EachInstances;
            import org.higherkindedj.optics.focus.FocusPath;
            import org.higherkindedj.optics.focus.TraversalPath;

            class Usage {
                TraversalPath<String, Integer> all(FocusPath<String, Kind<ListKind.Witness, Integer>> path) {
                    return path.each(EachInstances.fromTraverse(ListTraverse.INSTANCE));
                }
            }
            """));
  }

  @Test
  @DisplayName("each list step composes the FocusPaths list optic of the same name")
  void listStepsComposeTheirOptics() {
    rewriteRun(
        java(
            """
            package com.example;

            import java.util.List;
            import org.higherkindedj.optics.focus.AffinePath;
            import org.higherkindedj.optics.focus.TraversalPath;

            class Usage {
                void steps(AffinePath<String, List<Integer>> one, TraversalPath<String, List<Integer>> many) {
                    one.head();
                    one.last();
                    one.tail();
                    one.init();
                    many.cons();
                    many.snoc();
                    many.headTail();
                    many.initLast();
                }
            }
            """,
            """
            package com.example;

            import java.util.List;
            import org.higherkindedj.optics.focus.AffinePath;
            import org.higherkindedj.optics.focus.FocusPaths;
            import org.higherkindedj.optics.focus.TraversalPath;

            class Usage {
                void steps(AffinePath<String, List<Integer>> one, TraversalPath<String, List<Integer>> many) {
                    one.via(FocusPaths.listHead());
                    one.via(FocusPaths.listLast());
                    one.via(FocusPaths.listTail());
                    one.via(FocusPaths.listInit());
                    many.via(FocusPaths.listCons());
                    many.via(FocusPaths.listSnoc());
                    many.via(FocusPaths.listHeadTail());
                    many.via(FocusPaths.listInitLast());
                }
            }
            """));
  }

  @Test
  @DisplayName("a navigator's shortcut, which overrides the path's, is rewritten too")
  void aNavigatorsShortcutIsRewritten() {
    rewriteRun(
        java(
            """
            package com.example;

            import java.util.List;
            import org.higherkindedj.optics.focus.AffinePath;
            import org.higherkindedj.optics.focus.NavigatorFocusPath;
            import org.higherkindedj.optics.focus.TraversalPath;

            abstract class LinesNavigator extends NavigatorFocusPath<String, List<Integer>> {
                TraversalPath<String, Integer> all() {
                    return each();
                }

                AffinePath<String, Integer> newest() {
                    return this.last();
                }
            }
            """,
            """
            package com.example;

            import java.util.List;

            import org.higherkindedj.optics.each.EachInstances;
            import org.higherkindedj.optics.focus.AffinePath;
            import org.higherkindedj.optics.focus.FocusPaths;
            import org.higherkindedj.optics.focus.NavigatorFocusPath;
            import org.higherkindedj.optics.focus.TraversalPath;

            abstract class LinesNavigator extends NavigatorFocusPath<String, List<Integer>> {
                TraversalPath<String, Integer> all() {
                    return each(EachInstances.listEach());
                }

                AffinePath<String, Integer> newest() {
                    return this.via(FocusPaths.listLast());
                }
            }
            """));
  }

  @Test
  @DisplayName("typed forms, an Each's own each() and another type's last() are left alone")
  void typedFormsAndOtherMethodsAreLeftAlone() {
    rewriteRun(
        java(
            """
            package com.example;

            import java.util.List;
            import java.util.Optional;
            import org.higherkindedj.optics.Each;
            import org.higherkindedj.optics.Lens;
            import org.higherkindedj.optics.Traversal;
            import org.higherkindedj.optics.each.EachInstances;
            import org.higherkindedj.optics.focus.AffinePath;
            import org.higherkindedj.optics.focus.FocusPath;
            import org.higherkindedj.optics.focus.FocusPaths;
            import org.higherkindedj.optics.focus.TraversalPath;

            class Usage {
                record Shelf(List<String> books) {
                    String last() {
                        return books.getLast();
                    }
                }

                TraversalPath<String, Integer> all(Lens<String, List<Integer>> lens) {
                    return FocusPath.of(lens).each(EachInstances.listEach());
                }

                AffinePath<String, Integer> present(Lens<String, Optional<Integer>> lens) {
                    return FocusPath.of(lens).some(FocusPaths.optionalSome());
                }

                Traversal<List<Integer>, Integer> traversal(Each<List<Integer>, Integer> each) {
                    return each.each();
                }

                String newest(Shelf shelf) {
                    return shelf.last();
                }
            }
            """));
  }

  @Test
  @DisplayName("a path that does not focus on the container exactly is marked, not rewritten")
  void aContainerItDoesNotFitIsMarked() {
    rewriteRun(
        java(
            """
            package com.example;

            import java.util.ArrayList;
            import java.util.List;
            import java.util.Optional;
            import org.higherkindedj.optics.focus.FocusPath;

            class Usage {
                void steps(
                        FocusPath<String, List<? extends Number>> wildcard,
                        FocusPath<String, List> raw,
                        FocusPath<String, ArrayList<Integer>> subtype,
                        FocusPath<String, Optional<?>> unbounded,
                        FocusPath<String, String> other) {
                    wildcard.each();
                    raw.head();
                    subtype.at(0);
                    unbounded.some();
                    other.last();
                }
            }
            """,
            """
            package com.example;

            import java.util.ArrayList;
            import java.util.List;
            import java.util.Optional;
            import org.higherkindedj.optics.focus.FocusPath;

            class Usage {
                void steps(
                        FocusPath<String, List<? extends Number>> wildcard,
                        FocusPath<String, List> raw,
                        FocusPath<String, ArrayList<Integer>> subtype,
                        FocusPath<String, Optional<?>> unbounded,
                        FocusPath<String, String> other) {
                    /*~~(Not rewritten: each() reads a List, and this path focuses on java.util.List<? extends java.lang.Number>, which each(EachInstances.listEach()) does not take; view it as the List exactly through an Iso first)~~>*/wildcard.each();
                    /*~~(Not rewritten: head() reads a List, and this path focuses on java.util.List, which via(FocusPaths.listHead()) does not take; give the List its type arguments)~~>*/raw.head();
                    /*~~(Not rewritten: at(index) reads a List, and this path focuses on java.util.ArrayList<java.lang.Integer>, which via(FocusPaths.listAt(index)) does not take; declare it as a List)~~>*/subtype.at(0);
                    /*~~(Not rewritten: some() reads an Optional, and this path focuses on java.util.Optional<?>, which some(FocusPaths.optionalSome()) does not take; view it as the Optional exactly through an Iso first)~~>*/unbounded.some();
                    /*~~(Not rewritten: last() reads a List, and this path focuses on java.lang.String, which via(FocusPaths.listLast()) does not take; compose the optic for what the path focuses on)~~>*/other.last();
                }
            }
            """));
  }

  @Test
  @DisplayName("a result read with another element type, from a witness or a target, is marked")
  void aResultReadWithAnotherElementIsMarked() {
    rewriteRun(
        java(
            """
            package com.example;

            import java.util.List;
            import org.higherkindedj.optics.focus.FocusPath;
            import org.higherkindedj.optics.focus.TraversalPath;

            class Usage {
                TraversalPath<String, CharSequence> witnessed(FocusPath<String, List<String>> path) {
                    return path.<CharSequence>each();
                }

                TraversalPath<String, CharSequence> wider(FocusPath<String, List<String>> path) {
                    TraversalPath<String, CharSequence> all = path.each();
                    return all;
                }

                List<Object> untargeted(FocusPath<String, List<String>> path) {
                    return path.each().getAll("source");
                }
            }
            """,
            """
            package com.example;

            import java.util.List;
            import org.higherkindedj.optics.focus.FocusPath;
            import org.higherkindedj.optics.focus.TraversalPath;

            class Usage {
                TraversalPath<String, CharSequence> witnessed(FocusPath<String, List<String>> path) {
                    return /*~~(Not rewritten: this each() focuses on java.lang.CharSequence, and each(EachInstances.listEach()) would focus on java.lang.String, read from the path; write the typed form and make the code around it take java.lang.String)~~>*/path.<CharSequence>each();
                }

                TraversalPath<String, CharSequence> wider(FocusPath<String, List<String>> path) {
                    TraversalPath<String, CharSequence> all = /*~~(Not rewritten: this each() focuses on java.lang.CharSequence, and each(EachInstances.listEach()) would focus on java.lang.String, read from the path; write the typed form and make the code around it take java.lang.String)~~>*/path.each();
                    return all;
                }

                List<Object> untargeted(FocusPath<String, List<String>> path) {
                    return /*~~(Not rewritten: this each() focuses on java.lang.Object, and each(EachInstances.listEach()) would focus on java.lang.String, read from the path; write the typed form and make the code around it take java.lang.String)~~>*/path.each().getAll("source");
                }
            }
            """));
  }

  @Test
  @DisplayName("a call whose result nothing reads, or that a shortcut is called on, is rewritten")
  void anUnreadResultIsRewritten() {
    rewriteRun(
        java(
            """
            package com.example;

            import java.util.List;
            import java.util.Map;
            import java.util.Optional;
            import org.higherkindedj.optics.focus.AffinePath;
            import org.higherkindedj.optics.focus.FocusPath;
            import org.higherkindedj.optics.focus.TraversalPath;

            class Usage {
                void discarded(FocusPath<String, List<String>> path) {
                    path.<CharSequence>each();
                }

                TraversalPath<String, String> rows(FocusPath<String, List<List<String>>> grid) {
                    return grid.each().each();
                }

                TraversalPath<String, Integer> present(FocusPath<String, Optional<List<Integer>>> maybe) {
                    return maybe.some().each();
                }

                AffinePath<String, Integer> first(FocusPath<String, Map<String, List<Integer>>> byName) {
                    return byName.atKey("ada").head();
                }
            }
            """,
            """
            package com.example;

            import java.util.List;
            import java.util.Map;
            import java.util.Optional;

            import org.higherkindedj.optics.each.EachInstances;
            import org.higherkindedj.optics.focus.AffinePath;
            import org.higherkindedj.optics.focus.FocusPath;
            import org.higherkindedj.optics.focus.FocusPaths;
            import org.higherkindedj.optics.focus.TraversalPath;

            class Usage {
                void discarded(FocusPath<String, List<String>> path) {
                    path.each(EachInstances.listEach());
                }

                TraversalPath<String, String> rows(FocusPath<String, List<List<String>>> grid) {
                    return grid.each(EachInstances.listEach()).each(EachInstances.listEach());
                }

                TraversalPath<String, Integer> present(FocusPath<String, Optional<List<Integer>>> maybe) {
                    return maybe.some(FocusPaths.optionalSome()).each(EachInstances.listEach());
                }

                AffinePath<String, Integer> first(FocusPath<String, Map<String, List<Integer>>> byName) {
                    return byName.via(FocusPaths.mapAt("ada")).via(FocusPaths.listHead());
                }
            }
            """));
  }

  @Test
  @DisplayName("a wildcard inside the element is part of it, and the call is rewritten")
  void aWildcardInsideTheElementIsRewritten() {
    rewriteRun(
        java(
            """
            package com.example;

            import java.util.List;
            import org.higherkindedj.optics.focus.FocusPath;
            import org.higherkindedj.optics.focus.TraversalPath;

            class Usage {
                TraversalPath<String, List<?>> rows(FocusPath<String, List<List<?>>> grid) {
                    return grid.each();
                }
            }
            """,
            """
            package com.example;

            import java.util.List;

            import org.higherkindedj.optics.each.EachInstances;
            import org.higherkindedj.optics.focus.FocusPath;
            import org.higherkindedj.optics.focus.TraversalPath;

            class Usage {
                TraversalPath<String, List<?>> rows(FocusPath<String, List<List<?>>> grid) {
                    return grid.each(EachInstances.listEach());
                }
            }
            """));
  }

  @Test
  @DisplayName("a key or a Traverse that does not fit the path is marked; one that does is kept")
  void anArgumentThatDoesNotFitIsMarked() {
    rewriteRun(
        java(
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.hkt.Kind;
            import org.higherkindedj.hkt.Traverse;
            import org.higherkindedj.hkt.list.ListKind;
            import org.higherkindedj.hkt.maybe.MaybeTraverse;
            import org.higherkindedj.optics.focus.AffinePath;
            import org.higherkindedj.optics.focus.FocusPath;
            import org.higherkindedj.optics.focus.TraversalPath;

            class Usage {
                AffinePath<String, Integer> any(FocusPath<String, Map<String, Integer>> prices, Object key) {
                    return prices.atKey(key);
                }

                AffinePath<String, String> narrow(FocusPath<String, Map<Long, String>> names) {
                    return names.atKey(7);
                }

                AffinePath<String, String> boxed(FocusPath<String, Map<Integer, String>> names) {
                    return names.atKey(7);
                }

                TraversalPath<String, Integer> other(FocusPath<String, Kind<ListKind.Witness, Integer>> path) {
                    return path.traverseOver(MaybeTraverse.INSTANCE);
                }

                TraversalPath<String, Integer> given(
                        FocusPath<String, Kind<ListKind.Witness, Integer>> path,
                        Traverse<ListKind.Witness> traverse) {
                    return path.traverseOver(traverse);
                }
            }
            """,
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.hkt.Kind;
            import org.higherkindedj.hkt.Traverse;
            import org.higherkindedj.hkt.list.ListKind;
            import org.higherkindedj.hkt.maybe.MaybeTraverse;
            import org.higherkindedj.optics.each.EachInstances;
            import org.higherkindedj.optics.focus.AffinePath;
            import org.higherkindedj.optics.focus.FocusPath;
            import org.higherkindedj.optics.focus.FocusPaths;
            import org.higherkindedj.optics.focus.TraversalPath;

            class Usage {
                AffinePath<String, Integer> any(FocusPath<String, Map<String, Integer>> prices, Object key) {
                    return /*~~(Not rewritten: the key given to atKey(key) is java.lang.Object, and via(FocusPaths.mapAt(key)) takes this map's java.lang.String keys; pass a key of that type)~~>*/prices.atKey(key);
                }

                AffinePath<String, String> narrow(FocusPath<String, Map<Long, String>> names) {
                    return /*~~(Not rewritten: the key given to atKey(key) is int, and via(FocusPaths.mapAt(key)) takes this map's java.lang.Long keys; pass a key of that type)~~>*/names.atKey(7);
                }

                AffinePath<String, String> boxed(FocusPath<String, Map<Integer, String>> names) {
                    return names.via(FocusPaths.mapAt(7));
                }

                TraversalPath<String, Integer> other(FocusPath<String, Kind<ListKind.Witness, Integer>> path) {
                    return /*~~(Not rewritten: the Traverse given to traverseOver(traverse) is for org.higherkindedj.hkt.maybe.MaybeKind.Witness, and this path holds a Kind of org.higherkindedj.hkt.list.ListKind.Witness, which each(EachInstances.fromTraverse(traverse)) needs; pass the Traverse for that witness)~~>*/path.traverseOver(MaybeTraverse.INSTANCE);
                }

                TraversalPath<String, Integer> given(
                        FocusPath<String, Kind<ListKind.Witness, Integer>> path,
                        Traverse<ListKind.Witness> traverse) {
                    return path.each(EachInstances.fromTraverse(traverse));
                }
            }
            """));
  }

  @Test
  @DisplayName("a method reference to a shortcut is marked, not rewritten")
  void aMethodReferenceIsMarked() {
    rewriteRun(
        java(
            """
            package com.example;

            import java.util.List;
            import java.util.function.Function;
            import org.higherkindedj.optics.focus.FocusPath;
            import org.higherkindedj.optics.focus.TraversalPath;

            class Usage {
                Function<FocusPath<String, List<Integer>>, TraversalPath<String, Integer>> all =
                        FocusPath::each;
            }
            """,
            """
            package com.example;

            import java.util.List;
            import java.util.function.Function;
            import org.higherkindedj.optics.focus.FocusPath;
            import org.higherkindedj.optics.focus.TraversalPath;

            class Usage {
                Function<FocusPath<String, List<Integer>>, TraversalPath<String, Integer>> all =
                        /*~~(Not rewritten: a method reference to each(); call each(EachInstances.listEach()) in a lambda instead)~~>*/FocusPath::each;
            }
            """));
  }

  @Test
  @DisplayName("against the real hkj-core and hkj-api, a chain with witnesses is rewritten")
  void rewritesAgainstTheRealLibrary() {
    rewriteRun(
        spec ->
            spec.parser(JavaParser.fromJavaVersion().classpath("hkj-core", "hkj-api", "jspecify")),
        java(
            """
            package com.example;

            import java.util.List;
            import java.util.Optional;
            import org.higherkindedj.optics.Lens;
            import org.higherkindedj.optics.focus.FocusPath;
            import org.higherkindedj.optics.focus.TraversalPath;

            class Usage {
                record Dept(Optional<String> manager) {}

                TraversalPath<String, String> managers(
                        Lens<String, List<Dept>> depts, Lens<Dept, Optional<String>> manager) {
                    return FocusPath.of(depts).<Dept>each().via(manager).<String>some();
                }
            }
            """,
            """
            package com.example;

            import java.util.List;
            import java.util.Optional;
            import org.higherkindedj.optics.Lens;
            import org.higherkindedj.optics.each.EachInstances;
            import org.higherkindedj.optics.focus.FocusPath;
            import org.higherkindedj.optics.focus.FocusPaths;
            import org.higherkindedj.optics.focus.TraversalPath;

            class Usage {
                record Dept(Optional<String> manager) {}

                TraversalPath<String, String> managers(
                        Lens<String, List<Dept>> depts, Lens<Dept, Optional<String>> manager) {
                    return FocusPath.of(depts).each(EachInstances.listEach()).via(manager).some(FocusPaths.optionalSome());
                }
            }
            """));
  }

  @Test
  @DisplayName(
      "against the real hkj-core and hkj-api, a shortcut chain is rewritten, a wider one marked")
  void judgesResultsAgainstTheRealLibrary() {
    rewriteRun(
        spec ->
            spec.parser(JavaParser.fromJavaVersion().classpath("hkj-core", "hkj-api", "jspecify")),
        java(
            """
            package com.example;

            import java.util.List;
            import java.util.Optional;
            import org.higherkindedj.optics.Lens;
            import org.higherkindedj.optics.focus.FocusPath;
            import org.higherkindedj.optics.focus.TraversalPath;

            class Usage {
                TraversalPath<String, Integer> present(Lens<String, Optional<List<Integer>>> lens) {
                    return FocusPath.of(lens).some().each();
                }

                TraversalPath<String, Number> wider(Lens<String, List<Integer>> lens) {
                    return FocusPath.of(lens).each();
                }
            }
            """,
            """
            package com.example;

            import java.util.List;
            import java.util.Optional;
            import org.higherkindedj.optics.Lens;
            import org.higherkindedj.optics.each.EachInstances;
            import org.higherkindedj.optics.focus.FocusPath;
            import org.higherkindedj.optics.focus.FocusPaths;
            import org.higherkindedj.optics.focus.TraversalPath;

            class Usage {
                TraversalPath<String, Integer> present(Lens<String, Optional<List<Integer>>> lens) {
                    return FocusPath.of(lens).some(FocusPaths.optionalSome()).each(EachInstances.listEach());
                }

                TraversalPath<String, Number> wider(Lens<String, List<Integer>> lens) {
                    return /*~~(Not rewritten: this each() focuses on java.lang.Number, and each(EachInstances.listEach()) would focus on java.lang.Integer, read from the path; write the typed form and make the code around it take java.lang.Integer)~~>*/FocusPath.of(lens).each();
                }
            }
            """));
  }
}
