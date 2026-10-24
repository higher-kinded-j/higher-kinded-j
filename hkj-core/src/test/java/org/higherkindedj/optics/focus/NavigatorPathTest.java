// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.focus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IdPath;
import org.higherkindedj.hkt.effect.ListPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.NonDetPath;
import org.higherkindedj.hkt.effect.OptionalPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.StreamPath;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.hkt.effect.VStreamPath;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.each.EachInstances;
import org.higherkindedj.optics.util.Affines;
import org.higherkindedj.optics.util.Traversals;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The bases {@code @GenerateFocus} navigators extend: a navigator is the path it wraps, so every
 * path method gives what the wrapped path's own does, and none can be overridden.
 */
@DisplayName("Navigator path bases")
@SuppressWarnings("removal") // exercises the deprecated shortcuts until their removal
class NavigatorPathTest {

  record Basket(
      String name,
      List<String> items,
      Optional<String> note,
      Map<String, Integer> stock,
      @Nullable String nick,
      Kind<ListKind.Witness, String> kinds) {}

  private static final Basket BASKET =
      new Basket(
          "basket",
          List.of("apple", "pear", "fig"),
          Optional.of("fragile"),
          Map.of("apple", 3),
          "b",
          LIST.widen(List.of("x", "y")));

  /** A basket where every focus a case reads is absent, empty or null. */
  private static final Basket EMPTY =
      new Basket("", List.of(), Optional.empty(), Map.of(), null, LIST.widen(List.of()));

  /** A navigator of each tier, as {@code @GenerateFocus} generates them, without its methods. */
  static final class FocusNavigator<S, A> extends NavigatorFocusPath<S, A> {
    FocusNavigator(FocusPath<S, A> path) {
      super(path);
    }
  }

  static final class OtherFocusNavigator<S, A> extends NavigatorFocusPath<S, A> {
    OtherFocusNavigator(FocusPath<S, A> path) {
      super(path);
    }
  }

  static final class AffineNavigator<S, A> extends NavigatorAffinePath<S, A> {
    AffineNavigator(AffinePath<S, A> path) {
      super(path);
    }
  }

  static final class OtherAffineNavigator<S, A> extends NavigatorAffinePath<S, A> {
    OtherAffineNavigator(AffinePath<S, A> path) {
      super(path);
    }
  }

  static final class TraversalNavigator<S, A> extends NavigatorTraversalPath<S, A> {
    TraversalNavigator(TraversalPath<S, A> path) {
      super(path);
    }
  }

  static final class OtherTraversalNavigator<S, A> extends NavigatorTraversalPath<S, A> {
    OtherTraversalNavigator(TraversalPath<S, A> path) {
      super(path);
    }
  }

  // ===== The paths a case reads, plain or navigated =====

  private static final FocusPath<Basket, String> NAME =
      FocusPath.of(
          Lens.of(
              Basket::name,
              (b, v) -> new Basket(v, b.items(), b.note(), b.stock(), b.nick(), b.kinds())),
          "name");
  private static final FocusPath<Basket, List<String>> ITEMS =
      FocusPath.of(
          Lens.of(
              Basket::items,
              (b, v) -> new Basket(b.name(), v, b.note(), b.stock(), b.nick(), b.kinds())),
          "items");
  private static final FocusPath<Basket, Optional<String>> NOTE =
      FocusPath.of(
          Lens.of(
              Basket::note,
              (b, v) -> new Basket(b.name(), b.items(), v, b.stock(), b.nick(), b.kinds())),
          "note");
  private static final FocusPath<Basket, Map<String, Integer>> STOCK =
      FocusPath.of(
          Lens.of(
              Basket::stock,
              (b, v) -> new Basket(b.name(), b.items(), b.note(), v, b.nick(), b.kinds())),
          "stock");
  private static final FocusPath<Basket, @Nullable String> NICK =
      FocusPath.of(
          Lens.of(
              Basket::nick,
              (b, v) -> new Basket(b.name(), b.items(), b.note(), b.stock(), v, b.kinds())),
          "nick");
  private static final FocusPath<Basket, Kind<ListKind.Witness, String>> KINDS =
      FocusPath.of(
          Lens.of(
              Basket::kinds,
              (b, v) -> new Basket(b.name(), b.items(), b.note(), b.stock(), b.nick(), v)),
          "kinds");

  /** One set of paths over {@link Basket}, either as they are or wrapped in navigators. */
  record Paths(
      FocusPath<Basket, String> name,
      FocusPath<Basket, List<String>> items,
      FocusPath<Basket, Optional<String>> note,
      FocusPath<Basket, Map<String, Integer>> stock,
      FocusPath<Basket, @Nullable String> nick,
      FocusPath<Basket, Kind<ListKind.Witness, String>> kinds,
      AffinePath<Basket, String> someNote,
      AffinePath<Basket, List<String>> maybeItems,
      AffinePath<Basket, Optional<String>> maybeNote,
      AffinePath<Basket, Map<String, Integer>> maybeStock,
      AffinePath<Basket, @Nullable String> maybeNick,
      AffinePath<Basket, Kind<ListKind.Witness, String>> maybeKinds,
      TraversalPath<Basket, String> eachItem,
      TraversalPath<Basket, List<String>> allItems,
      TraversalPath<Basket, Optional<String>> allNotes,
      TraversalPath<Basket, Map<String, Integer>> allStock,
      TraversalPath<Basket, Integer> eachCount,
      TraversalPath<Basket, Kind<ListKind.Witness, String>> allKinds) {

    static Paths plain() {
      return new Paths(
          NAME,
          ITEMS,
          NOTE,
          STOCK,
          NICK,
          KINDS,
          NOTE.some(),
          ITEMS.asAffine(),
          NOTE.asAffine(),
          STOCK.asAffine(),
          NICK.asAffine(),
          KINDS.asAffine(),
          ITEMS.each(),
          ITEMS.asTraversal(),
          NOTE.asTraversal(),
          STOCK.asTraversal(),
          STOCK.each(EachInstances.mapValuesEach()),
          KINDS.asTraversal());
    }

    /** The same paths, each wrapped in a navigator of its tier. */
    Paths navigated() {
      return new Paths(
          new FocusNavigator<>(name),
          new FocusNavigator<>(items),
          new FocusNavigator<>(note),
          new FocusNavigator<>(stock),
          new FocusNavigator<>(nick),
          new FocusNavigator<>(kinds),
          new AffineNavigator<>(someNote),
          new AffineNavigator<>(maybeItems),
          new AffineNavigator<>(maybeNote),
          new AffineNavigator<>(maybeStock),
          new AffineNavigator<>(maybeNick),
          new AffineNavigator<>(maybeKinds),
          new TraversalNavigator<>(eachItem),
          new TraversalNavigator<>(allItems),
          new TraversalNavigator<>(allNotes),
          new TraversalNavigator<>(allStock),
          new TraversalNavigator<>(eachCount),
          new TraversalNavigator<>(allKinds));
    }
  }

  // ===== Optics a case composes with, over a String focus =====

  private static final Lens<String, String> LENS = Lens.of(s -> s + "!", (s, v) -> v);
  private static final Iso<String, String> ISO = Iso.of(String::toUpperCase, s -> s);
  private static final Prism<String, String> PRISM = Prism.of(Optional::of, s -> s);
  private static final Affine<String, String> AFFINE = Affine.of(Optional::of, (s, v) -> v);
  private static final FocusPath<String, String> FOCUS_PATH = FocusPath.of(LENS, "bang");
  private static final AffinePath<String, String> AFFINE_PATH = FOCUS_PATH.asAffine();
  private static final TraversalPath<String, String> TRAVERSAL_PATH = FOCUS_PATH.asTraversal();
  private static final Traversal<String, String> TRAVERSAL = TRAVERSAL_PATH.toTraversal();

  /**
   * A call made on the plain paths and on the navigated ones, over a basket. An overloaded method
   * names its parameter types, as {@code via(Lens)}; any other names itself alone.
   */
  record Case(String method, BiFunction<Paths, Basket, Object> call) {
    @Override
    public String toString() {
      return method;
    }
  }

  private static Case call(String method, BiFunction<Paths, Basket, Object> call) {
    return new Case(method, call);
  }

  /** What a traced path reads, with what its observer saw on the way. */
  private static Object traced(Function<List<Object>, Object> trace, Basket basket) {
    List<Object> seen = new ArrayList<>();
    Object read = observe(trace.apply(seen), basket);
    return Arrays.asList(read, seen);
  }

  private static List<Case> focusCases() {
    return List.of(
        call("segments", (p, b) -> p.name().segments()),
        call("pathString", (p, b) -> p.name().pathString()),
        call("get", (p, b) -> p.name().get(b)),
        call("set", (p, b) -> p.name().set("box", b)),
        call("modify", (p, b) -> p.name().modify(String::toUpperCase, b)),
        call(
            "modifyF",
            (p, b) ->
                ID.narrow(p.name().modifyF(s -> ID.widen(Id.of(s + "?")), b, IdMonad.instance()))
                    .value()),
        call("via(Lens)", (p, b) -> p.name().via(LENS)),
        call("via(FocusPath)", (p, b) -> p.name().via(FOCUS_PATH)),
        call("via(AffinePath)", (p, b) -> p.name().via(AFFINE_PATH)),
        call("via(TraversalPath)", (p, b) -> p.name().via(TRAVERSAL_PATH)),
        call("via(Iso)", (p, b) -> p.name().via(ISO)),
        call("via(Prism)", (p, b) -> p.name().via(PRISM)),
        call("via(Affine)", (p, b) -> p.name().via(AFFINE)),
        call("via(Traversal)", (p, b) -> p.name().via(TRAVERSAL)),
        call("then(Lens)", (p, b) -> p.name().then(LENS)),
        call("then(FocusPath)", (p, b) -> p.name().then(FOCUS_PATH)),
        call("then(Iso)", (p, b) -> p.name().then(ISO)),
        call("then(Prism)", (p, b) -> p.name().then(PRISM)),
        call("then(Affine)", (p, b) -> p.name().then(AFFINE)),
        call("then(AffinePath)", (p, b) -> p.name().then(AFFINE_PATH)),
        call("then(Traversal)", (p, b) -> p.name().then(TRAVERSAL)),
        call("then(TraversalPath)", (p, b) -> p.name().then(TRAVERSAL_PATH)),
        call("each()", (p, b) -> p.items().each()),
        call("each(Each)", (p, b) -> p.items().each(EachInstances.listEach())),
        call("at", (p, b) -> p.items().at(1)),
        call("atKey", (p, b) -> p.stock().atKey("apple")),
        call("some()", (p, b) -> p.note().some()),
        call("some(Affine)", (p, b) -> p.note().some(Affines.some())),
        call("nullable", (p, b) -> p.nick().nullable()),
        call("traverseOver", (p, b) -> p.kinds().traverseOver(ListTraverse.INSTANCE)),
        call("cons", (p, b) -> p.items().cons()),
        call("headTail", (p, b) -> p.items().headTail()),
        call("snoc", (p, b) -> p.items().snoc()),
        call("initLast", (p, b) -> p.items().initLast()),
        call("head", (p, b) -> p.items().head()),
        call("last", (p, b) -> p.items().last()),
        call("tail", (p, b) -> p.items().tail()),
        call("init", (p, b) -> p.items().init()),
        call("toLens", (p, b) -> p.name().toLens()),
        call("asAffine", (p, b) -> p.name().asAffine()),
        call("asTraversal", (p, b) -> p.name().asTraversal()),
        call("asFold", (p, b) -> p.name().asFold()),
        call("traced", (p, b) -> traced(seen -> p.name().traced((s, value) -> seen.add(value)), b)),
        call("traverseWith", (p, b) -> p.name().traverseWith(s -> Path.vtaskPure(s.length()), b)),
        call("toMaybePath", (p, b) -> p.name().toMaybePath(b)),
        call("toEitherPath(Object)", (p, b) -> p.name().toEitherPath(b)),
        call("toEitherPath(Object,Object)", (p, b) -> p.nick().toEitherPath(b, "absent")),
        call("toEitherPath(Object,Supplier)", (p, b) -> p.nick().toEitherPath(b, () -> "absent")),
        call("toTryPath(Object)", (p, b) -> p.name().toTryPath(b)),
        call(
            "toTryPath(Object,Supplier)",
            (p, b) -> p.nick().toTryPath(b, IllegalStateException::new)),
        call("toIdPath", (p, b) -> p.name().toIdPath(b)));
  }

  private static List<Case> affineCases() {
    return List.of(
        call("segments", (p, b) -> p.someNote().segments()),
        call("pathString", (p, b) -> p.someNote().pathString()),
        call("getOptional", (p, b) -> p.someNote().getOptional(b)),
        call("set", (p, b) -> p.someNote().set("sturdy", b)),
        call("modify", (p, b) -> p.someNote().modify(String::toUpperCase, b)),
        call("matches", (p, b) -> p.someNote().matches(b)),
        call("getOrElse", (p, b) -> p.someNote().getOrElse("none", b)),
        call("mapOptional", (p, b) -> p.someNote().mapOptional(String::length, b)),
        call(
            "modifyF",
            (p, b) ->
                ID.narrow(
                        p.someNote().modifyF(s -> ID.widen(Id.of(s + "?")), b, IdMonad.instance()))
                    .value()),
        call("via(Lens)", (p, b) -> p.someNote().via(LENS)),
        call("via(FocusPath)", (p, b) -> p.someNote().via(FOCUS_PATH)),
        call("via(AffinePath)", (p, b) -> p.someNote().via(AFFINE_PATH)),
        call("via(TraversalPath)", (p, b) -> p.someNote().via(TRAVERSAL_PATH)),
        call("via(Prism)", (p, b) -> p.someNote().via(PRISM)),
        call("via(Affine)", (p, b) -> p.someNote().via(AFFINE)),
        call("via(Iso)", (p, b) -> p.someNote().via(ISO)),
        call("via(Traversal)", (p, b) -> p.someNote().via(TRAVERSAL)),
        call("then(Lens)", (p, b) -> p.someNote().then(LENS)),
        call("then(FocusPath)", (p, b) -> p.someNote().then(FOCUS_PATH)),
        call("then(Prism)", (p, b) -> p.someNote().then(PRISM)),
        call("then(Affine)", (p, b) -> p.someNote().then(AFFINE)),
        call("then(AffinePath)", (p, b) -> p.someNote().then(AFFINE_PATH)),
        call("then(Iso)", (p, b) -> p.someNote().then(ISO)),
        call("then(Traversal)", (p, b) -> p.someNote().then(TRAVERSAL)),
        call("then(TraversalPath)", (p, b) -> p.someNote().then(TRAVERSAL_PATH)),
        call("each()", (p, b) -> p.maybeItems().each()),
        call("each(Each)", (p, b) -> p.maybeItems().each(EachInstances.listEach())),
        call("at", (p, b) -> p.maybeItems().at(1)),
        call("atKey", (p, b) -> p.maybeStock().atKey("apple")),
        call("some()", (p, b) -> p.maybeNote().some()),
        call("some(Affine)", (p, b) -> p.maybeNote().some(Affines.some())),
        call("nullable", (p, b) -> p.maybeNick().nullable()),
        call("traverseOver", (p, b) -> p.maybeKinds().traverseOver(ListTraverse.INSTANCE)),
        call("cons", (p, b) -> p.maybeItems().cons()),
        call("headTail", (p, b) -> p.maybeItems().headTail()),
        call("snoc", (p, b) -> p.maybeItems().snoc()),
        call("initLast", (p, b) -> p.maybeItems().initLast()),
        call("head", (p, b) -> p.maybeItems().head()),
        call("last", (p, b) -> p.maybeItems().last()),
        call("tail", (p, b) -> p.maybeItems().tail()),
        call("init", (p, b) -> p.maybeItems().init()),
        call("toAffine", (p, b) -> p.someNote().toAffine()),
        call("asTraversal", (p, b) -> p.someNote().asTraversal()),
        call("asFold", (p, b) -> p.someNote().asFold()),
        call(
            "traced",
            (p, b) -> traced(seen -> p.someNote().traced((s, value) -> seen.add(value)), b)),
        call(
            "traverseWith",
            (p, b) -> p.someNote().traverseWith(s -> Path.vtaskPure(s.length()), b)),
        call("toMaybePath", (p, b) -> p.someNote().toMaybePath(b)),
        call("toEitherPath(Object,Object)", (p, b) -> p.someNote().toEitherPath(b, "absent")),
        call(
            "toEitherPath(Object,Supplier)",
            (p, b) -> p.someNote().toEitherPath(b, () -> "absent")),
        call("toTryPath", (p, b) -> p.someNote().toTryPath(b, IllegalStateException::new)),
        call("toOptionalPath", (p, b) -> p.someNote().toOptionalPath(b)));
  }

  private static List<Case> traversalCases() {
    return List.of(
        call("segments", (p, b) -> p.eachItem().segments()),
        call("pathString", (p, b) -> p.eachItem().pathString()),
        call("getAll", (p, b) -> p.eachItem().getAll(b)),
        call("preview", (p, b) -> p.eachItem().preview(b)),
        call("setAll", (p, b) -> p.eachItem().setAll("plum", b)),
        call("modifyAll", (p, b) -> p.eachItem().modifyAll(String::toUpperCase, b)),
        call("count", (p, b) -> p.eachItem().count(b)),
        call("isEmpty", (p, b) -> p.eachItem().isEmpty(b)),
        call("exists", (p, b) -> p.eachItem().exists(s -> s.startsWith("p"), b)),
        call("all", (p, b) -> p.eachItem().all(s -> s.length() > 2, b)),
        call("find", (p, b) -> p.eachItem().find(s -> s.startsWith("f"), b)),
        call("filter", (p, b) -> p.eachItem().filter(s -> s.startsWith("p"))),
        call("via(Lens)", (p, b) -> p.eachItem().via(LENS)),
        call("via(FocusPath)", (p, b) -> p.eachItem().via(FOCUS_PATH)),
        call("via(AffinePath)", (p, b) -> p.eachItem().via(AFFINE_PATH)),
        call("via(TraversalPath)", (p, b) -> p.eachItem().via(TRAVERSAL_PATH)),
        call("via(Prism)", (p, b) -> p.eachItem().via(PRISM)),
        call("via(Affine)", (p, b) -> p.eachItem().via(AFFINE)),
        call("via(Traversal)", (p, b) -> p.eachItem().via(TRAVERSAL)),
        call("via(Iso)", (p, b) -> p.eachItem().via(ISO)),
        call("then(Lens)", (p, b) -> p.eachItem().then(LENS)),
        call("then(FocusPath)", (p, b) -> p.eachItem().then(FOCUS_PATH)),
        call("then(Prism)", (p, b) -> p.eachItem().then(PRISM)),
        call("then(Affine)", (p, b) -> p.eachItem().then(AFFINE)),
        call("then(AffinePath)", (p, b) -> p.eachItem().then(AFFINE_PATH)),
        call("then(Traversal)", (p, b) -> p.eachItem().then(TRAVERSAL)),
        call("then(TraversalPath)", (p, b) -> p.eachItem().then(TRAVERSAL_PATH)),
        call("then(Iso)", (p, b) -> p.eachItem().then(ISO)),
        call(
            "modifyWhen",
            (p, b) -> p.eachItem().modifyWhen(s -> s.startsWith("p"), String::toUpperCase, b)),
        call(
            "modifyF",
            (p, b) ->
                ID.narrow(
                        p.eachItem().modifyF(s -> ID.widen(Id.of(s + "?")), b, IdMonad.instance()))
                    .value()),
        call("foldMap", (p, b) -> p.eachItem().foldMap(Monoids.string(), s -> s, b)),
        call("fold", (p, b) -> p.eachCount().fold(Monoids.integerAddition(), b)),
        call("each()", (p, b) -> p.allItems().each()),
        call("each(Each)", (p, b) -> p.allItems().each(EachInstances.listEach())),
        call("at", (p, b) -> p.allItems().at(1)),
        call("atKey", (p, b) -> p.allStock().atKey("apple")),
        call("some()", (p, b) -> p.allNotes().some()),
        call("some(Affine)", (p, b) -> p.allNotes().some(Affines.some())),
        call("traverseOver", (p, b) -> p.allKinds().traverseOver(ListTraverse.INSTANCE)),
        call("cons", (p, b) -> p.allItems().cons()),
        call("headTail", (p, b) -> p.allItems().headTail()),
        call("snoc", (p, b) -> p.allItems().snoc()),
        call("initLast", (p, b) -> p.allItems().initLast()),
        call("head", (p, b) -> p.allItems().head()),
        call("last", (p, b) -> p.allItems().last()),
        call("tail", (p, b) -> p.allItems().tail()),
        call("init", (p, b) -> p.allItems().init()),
        call("headOption", (p, b) -> p.eachItem().headOption()),
        call("toTraversal", (p, b) -> p.eachItem().toTraversal()),
        call("asFold", (p, b) -> p.eachItem().asFold()),
        call(
            "traced",
            (p, b) -> traced(seen -> p.eachItem().traced((s, values) -> seen.add(values)), b)),
        call(
            "traverseWith",
            (p, b) -> p.eachItem().traverseWith(s -> Path.vtaskPure(s.length()), b)),
        call("toListPath", (p, b) -> p.eachItem().toListPath(b)),
        call("toNonDetPath", (p, b) -> p.eachItem().toNonDetPath(b)),
        call("toStreamPath", (p, b) -> p.eachItem().toStreamPath(b)),
        call("toVStreamPath", (p, b) -> p.eachItem().toVStreamPath(b)),
        call("toMaybePath", (p, b) -> p.eachItem().toMaybePath(b)));
  }

  /**
   * What a call's result shows from outside: a path's labels and what it reads from the basket, an
   * optic's reading, an effect's outcome, or the value itself. Every path and optic a case returns
   * is over the basket, which a wildcard hides from the switch. A focus may be null, so the pairs
   * are lists that hold one.
   */
  @SuppressWarnings("unchecked")
  private static Object observe(Object result, Basket basket) {
    return switch (result) {
      case FocusPath<?, ?> path ->
          Arrays.asList(path.segments(), ((FocusPath<Basket, ?>) path).get(basket));
      case AffinePath<?, ?> path ->
          Arrays.asList(path.segments(), ((AffinePath<Basket, ?>) path).getOptional(basket));
      case TraversalPath<?, ?> path ->
          Arrays.asList(path.segments(), ((TraversalPath<Basket, ?>) path).getAll(basket));
      case Lens<?, ?> lens -> ((Lens<Basket, ?>) lens).get(basket);
      case Affine<?, ?> affine -> ((Affine<Basket, ?>) affine).getOptional(basket);
      case Traversal<?, ?> traversal ->
          Traversals.getAll((Traversal<Basket, Object>) traversal, basket);
      case Fold<?, ?> fold -> ((Fold<Basket, ?>) fold).getAll(basket);
      case MaybePath<?> maybe -> maybe.run();
      case EitherPath<?, ?> either -> either.run();
      case TryPath<?> attempt ->
          switch (attempt.run()) {
            // a failure holds a fresh exception each run, so it is compared by class
            case Try.Success<?> success -> Arrays.asList("success", success.value());
            case Try.Failure<?> failure -> List.of("failure", failure.cause().getClass());
          };
      case IdPath<?> id -> id.get();
      case OptionalPath<?> optional -> optional.run();
      case ListPath<?> list -> list.run();
      case NonDetPath<?> nonDet -> nonDet.run();
      case StreamPath<?> stream -> stream.toList();
      case VStreamPath<?> stream -> stream.toList().unsafeRun();
      case VTaskPath<?> task -> task.unsafeRun();
      case null -> null;
      default -> result;
    };
  }

  /** A case's observed outcome over a basket, or the exception it throws, by class. */
  private static Object outcome(Case c, Paths paths, Basket basket) {
    try {
      return observe(c.call().apply(paths, basket), basket);
    } catch (RuntimeException e) {
      return List.of("throws", e.getClass().getName());
    }
  }

  private static Stream<Arguments> everyCall() {
    return Stream.of(
            focusCases().stream().map(c -> Arguments.of("FocusPath", c)),
            affineCases().stream().map(c -> Arguments.of("AffinePath", c)),
            traversalCases().stream().map(c -> Arguments.of("TraversalPath", c)))
        .flatMap(s -> s);
  }

  @ParameterizedTest(name = "{0}.{1}")
  @MethodSource("everyCall")
  @DisplayName("a navigator gives what the path it wraps gives, over a full and an empty basket")
  void aNavigatorGivesWhatItsPathGives(String tier, Case c) {
    Paths plain = Paths.plain();
    for (Basket basket : List.of(BASKET, EMPTY)) {
      assertThat(outcome(c, plain.navigated(), basket))
          .as("%s.%s through a navigator, over %s", tier, c.method(), basket)
          .isEqualTo(outcome(c, plain, basket));
    }
  }

  private static Stream<Arguments> bases() {
    return Stream.of(
        Arguments.of(FocusPath.class, NavigatorFocusPath.class, focusCases()),
        Arguments.of(AffinePath.class, NavigatorAffinePath.class, affineCases()),
        Arguments.of(TraversalPath.class, NavigatorTraversalPath.class, traversalCases()));
  }

  /** A method as a case names it: with its parameter types where the name is overloaded. */
  private static String key(Method method, Map<String, Long> overloads) {
    return overloads.get(method.getName()) == 1
        ? method.getName()
        : method.getName()
            + Arrays.stream(method.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(",", "(", ")"));
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("bases")
  @DisplayName("every method the base declares is final, and a case calls each path method once")
  void everyPathMethodIsFinalAndCovered(Class<?> path, Class<?> base, List<Case> cases)
      throws NoSuchMethodException {
    List<Method> methods =
        Arrays.stream(path.getMethods())
            .filter(method -> !Modifier.isStatic(method.getModifiers()))
            .toList();
    for (Method method : methods) {
      base.getDeclaredMethod(method.getName(), method.getParameterTypes());
    }
    for (Method declared : base.getDeclaredMethods()) {
      if (Modifier.isPublic(declared.getModifiers()) && !declared.isSynthetic()) {
        assertThat(Modifier.isFinal(declared.getModifiers())).as("%s is final", declared).isTrue();
      }
    }
    Map<String, Long> overloads =
        methods.stream().collect(Collectors.groupingBy(Method::getName, Collectors.counting()));
    List<String> keys = cases.stream().map(Case::method).toList();
    assertThat(keys).as("each case names a different method").doesNotHaveDuplicates();
    assertThat(keys)
        .as("a case for each method of %s, overloads included", path.getSimpleName())
        .containsExactlyInAnyOrderElementsOf(
            methods.stream().map(method -> key(method, overloads)).toList());
  }

  private static Stream<Arguments> noArgumentNames() {
    Set<String> shared =
        Set.of(
            "segments",
            "pathString",
            "each",
            "some",
            "cons",
            "headTail",
            "snoc",
            "initLast",
            "head",
            "last",
            "tail",
            "init",
            "asFold",
            "toPath");
    return Stream.of(
        Arguments.of(
            NavigatorFocusPath.class,
            union(shared, Set.of("nullable", "toLens", "asAffine", "asTraversal"))),
        Arguments.of(
            NavigatorAffinePath.class,
            union(shared, Set.of("nullable", "toAffine", "asTraversal"))),
        Arguments.of(
            NavigatorTraversalPath.class, union(shared, Set.of("headOption", "toTraversal"))));
  }

  private static Set<String> union(Set<String> a, Set<String> b) {
    return Stream.concat(a.stream(), b.stream()).collect(Collectors.toSet());
  }

  /**
   * A field of a record a navigator reaches cannot take the name of one of the navigator's
   * no-argument methods, so adding one to a path type is a change for such records, and is made on
   * purpose here. The names a record component may not take anyway, such as {@code hashCode}, are
   * left out.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("noArgumentNames")
  @DisplayName("the no-argument method names a navigated record's field cannot take are pinned")
  void noArgumentNamesArePinned(Class<?> base, Set<String> expected) {
    Set<String> forbiddenToRecords =
        Set.of("hashCode", "toString", "getClass", "notify", "notifyAll", "wait");
    assertThat(
            Arrays.stream(base.getMethods())
                .filter(method -> method.getParameterCount() == 0)
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .map(Method::getName)
                .filter(name -> !forbiddenToRecords.contains(name))
                .collect(Collectors.toSet()))
        .containsExactlyInAnyOrderElementsOf(expected);
  }

  @Nested
  @DisplayName("Identity")
  class Identity {

    @Test
    @DisplayName("toPath hands back the wrapped path, at every tier")
    void toPathHandsBackTheWrappedPath() {
      Paths plain = Paths.plain();
      assertThat(new FocusNavigator<>(plain.name()).toPath()).isSameAs(plain.name());
      assertThat(new AffineNavigator<>(plain.someNote()).toPath()).isSameAs(plain.someNote());
      assertThat(new TraversalNavigator<>(plain.eachItem()).toPath()).isSameAs(plain.eachItem());
    }

    @Test
    @DisplayName("a navigator equals one of the same class over an equal path, and nothing else")
    void equality() {
      Paths plain = Paths.plain();
      FocusNavigator<Basket, String> focus = new FocusNavigator<>(plain.name());
      AffineNavigator<Basket, String> affine = new AffineNavigator<>(plain.someNote());
      TraversalNavigator<Basket, String> traversal = new TraversalNavigator<>(plain.eachItem());

      assertThat(focus)
          .isEqualTo(new FocusNavigator<>(plain.name()))
          .isNotEqualTo(new OtherFocusNavigator<>(plain.name()))
          .isNotEqualTo(new FocusNavigator<>(plain.items()))
          .isNotEqualTo(plain.name())
          .hasSameHashCodeAs(plain.name());
      assertThat(affine)
          .isEqualTo(new AffineNavigator<>(plain.someNote()))
          .isNotEqualTo(new OtherAffineNavigator<>(plain.someNote()))
          .isNotEqualTo(new AffineNavigator<>(plain.maybeItems()))
          .isNotEqualTo(plain.someNote())
          .hasSameHashCodeAs(plain.someNote());
      assertThat(traversal)
          .isEqualTo(new TraversalNavigator<>(plain.eachItem()))
          .isNotEqualTo(new OtherTraversalNavigator<>(plain.eachItem()))
          .isNotEqualTo(new TraversalNavigator<>(plain.allItems()))
          .isNotEqualTo(plain.eachItem())
          .hasSameHashCodeAs(plain.eachItem());
    }

    @Test
    @DisplayName("a navigator prints as the path it wraps")
    void printsAsItsPath() {
      Paths plain = Paths.plain();
      assertThat(new FocusNavigator<>(plain.name())).hasToString(plain.name().toString());
      assertThat(new AffineNavigator<>(plain.someNote())).hasToString(plain.someNote().toString());
      assertThat(new TraversalNavigator<>(plain.eachItem()))
          .hasToString(plain.eachItem().toString());
    }

    @Test
    @DisplayName("a navigator needs a path to wrap")
    void rejectsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> new FocusNavigator<Basket, String>(null))
          .withMessage("path must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> new AffineNavigator<Basket, String>(null))
          .withMessage("path must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> new TraversalNavigator<Basket, String>(null))
          .withMessage("path must not be null");
    }
  }
}
