// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;

/**
 * Rewrites each call to one of a Focus path's untyped shortcuts to its typed form, which uses the
 * same verb with an instance that names the container.
 *
 * <p>The shortcuts are declared on {@code FocusPath}, {@code AffinePath} and {@code TraversalPath},
 * and deprecated in 0.5.0 for removal in 0.6.0. None reads its element type from the focus, so each
 * compiles on any focus. The rewrites are:
 *
 * <ul>
 *   <li>{@code each()} to {@code each(EachInstances.listEach())}
 *   <li>{@code some()} to {@code some(FocusPaths.optionalSome())}
 *   <li>{@code at(index)} to {@code via(FocusPaths.listAt(index))}
 *   <li>{@code atKey(key)} to {@code via(FocusPaths.mapAt(key))}
 *   <li>{@code traverseOver(traverse)} to {@code each(EachInstances.fromTraverse(traverse))}
 *   <li>{@code head()}, {@code last()}, {@code tail()}, {@code init()}, {@code cons()}, {@code
 *       snoc()}, {@code headTail()} and {@code initLast()} to {@code via(FocusPaths.listHead())}
 *       and so on, with the {@code FocusPaths} factory of the same name
 * </ul>
 *
 * <p>A typed form takes its element type from the path, so it fits only a path that focuses on its
 * container exactly, as a {@code List<Role>} or a {@code Map<String, Integer>}, and a type witness
 * written on the shortcut, as in {@code <Role>each()}, is dropped. Where it would not fit, the call
 * is marked instead, with a way to replace it: a raw or wildcard container, a subtype such as
 * {@code ArrayList}, a type variable or another type altogether; a key or a {@code Traverse} that
 * does not fit the path; a result that the code around the call takes with another element type,
 * from a witness or from the type it is assigned to; and a method reference to a shortcut. A call
 * whose result nothing reads, or that another shortcut is called on, is rewritten whatever element
 * type it was given, since the typed form's own compiles there. Every typed form is in 0.4.x too,
 * so the recipe runs on either release.
 */
public class ReplaceUntypedPathShortcutsRecipe extends Recipe {

  /** Creates a new instance of this recipe. */
  public ReplaceUntypedPathShortcutsRecipe() {}

  private static final String FOCUS_PACKAGE = "org.higherkindedj.optics.focus.";
  private static final Set<String> PATHS =
      Set.of(
          FOCUS_PACKAGE + "FocusPath",
          FOCUS_PACKAGE + "AffinePath",
          FOCUS_PACKAGE + "TraversalPath");
  private static final String EACH_INSTANCES = "org.higherkindedj.optics.each.EachInstances";
  private static final String FOCUS_PATHS = FOCUS_PACKAGE + "FocusPaths";
  private static final String EACH = "org.higherkindedj.optics.Each";
  private static final String AFFINE = "org.higherkindedj.optics.Affine";
  private static final String PRISM = "org.higherkindedj.optics.Prism";
  private static final String LIST = "java.util.List";
  private static final String OPTIONAL = "java.util.Optional";
  private static final String MAP = "java.util.Map";
  private static final String KIND = "org.higherkindedj.hkt.Kind";
  private static final String TRAVERSE = "org.higherkindedj.hkt.Traverse";

  /** What a typed form's path focuses on, built from the element its container holds. */
  private enum Result {
    /** The element itself. */
    ELEMENT,
    /** The list the element is in. */
    LIST,
    /** A pair of the first element and the rest of the list. */
    HEAD_AND_TAIL,
    /** A pair of the list but its last element, and that element. */
    INIT_AND_LAST
  }

  /**
   * One shortcut and its typed form: the call {@code verb(instance)}, where the instance is {@code
   * owner.factory(argument)}, fits a path focusing on {@code container}, and gives a path focusing
   * on {@code result}.
   */
  private record Shortcut(
      String name,
      String argument,
      String verb,
      String owner,
      String factory,
      String instanceType,
      String container,
      Result result) {

    List<MethodMatcher> matchers() {
      String parameters =
          switch (argument) {
            case "" -> "()";
            case "index" -> "(int)";
            case "key" -> "(*)";
            default -> "(org.higherkindedj.hkt.Traverse)";
          };
      return PATHS.stream()
          .sorted()
          .map(path -> new MethodMatcher(path + " " + name + parameters, true))
          .toList();
    }

    String call() {
      return name + "(" + argument + ")";
    }

    String typedForm() {
      return verb + "(" + simpleName(owner) + "." + factory + "(" + argument + "))";
    }

    String verbParameter() {
      return switch (instanceType) {
        case EACH -> "eachInstance";
        case AFFINE -> "affine";
        default -> "prism";
      };
    }
  }

  private static final List<Shortcut> SHORTCUTS =
      List.of(
          new Shortcut("each", "", "each", EACH_INSTANCES, "listEach", EACH, LIST, Result.ELEMENT),
          new Shortcut(
              "some", "", "some", FOCUS_PATHS, "optionalSome", AFFINE, OPTIONAL, Result.ELEMENT),
          new Shortcut("at", "index", "via", FOCUS_PATHS, "listAt", AFFINE, LIST, Result.ELEMENT),
          new Shortcut("atKey", "key", "via", FOCUS_PATHS, "mapAt", AFFINE, MAP, Result.ELEMENT),
          new Shortcut(
              "traverseOver",
              "traverse",
              "each",
              EACH_INSTANCES,
              "fromTraverse",
              EACH,
              KIND,
              Result.ELEMENT),
          new Shortcut("head", "", "via", FOCUS_PATHS, "listHead", AFFINE, LIST, Result.ELEMENT),
          new Shortcut("last", "", "via", FOCUS_PATHS, "listLast", AFFINE, LIST, Result.ELEMENT),
          new Shortcut("tail", "", "via", FOCUS_PATHS, "listTail", AFFINE, LIST, Result.LIST),
          new Shortcut("init", "", "via", FOCUS_PATHS, "listInit", AFFINE, LIST, Result.LIST),
          new Shortcut(
              "cons", "", "via", FOCUS_PATHS, "listCons", PRISM, LIST, Result.HEAD_AND_TAIL),
          new Shortcut(
              "snoc", "", "via", FOCUS_PATHS, "listSnoc", PRISM, LIST, Result.INIT_AND_LAST),
          new Shortcut(
              "headTail",
              "",
              "via",
              FOCUS_PATHS,
              "listHeadTail",
              PRISM,
              LIST,
              Result.HEAD_AND_TAIL),
          new Shortcut(
              "initLast",
              "",
              "via",
              FOCUS_PATHS,
              "listInitLast",
              PRISM,
              LIST,
              Result.INIT_AND_LAST));

  private record Matched(MethodMatcher matcher, Shortcut shortcut) {}

  private static final List<Matched> MATCHERS =
      SHORTCUTS.stream()
          .flatMap(shortcut -> shortcut.matchers().stream().map(m -> new Matched(m, shortcut)))
          .toList();

  @Override
  public String getDisplayName() {
    return "Replace a Focus path's untyped shortcuts with their typed forms";
  }

  @Override
  public String getDescription() {
    return "Rewrites each call to a Focus path's `each()`, `some()`, `at(index)`, `atKey(key)`,"
        + " `traverseOver(traverse)`, `head()`, `last()`, `tail()`, `init()`, `cons()`, `snoc()`,"
        + " `headTail()` or `initLast()`, deprecated in 0.5.0 for removal in 0.6.0, to its typed"
        + " form: `each(EachInstances.listEach())`, `some(FocusPaths.optionalSome())`,"
        + " `via(FocusPaths.listAt(index))`, `via(FocusPaths.mapAt(key))`,"
        + " `each(EachInstances.fromTraverse(traverse))`, or `via` with the `FocusPaths` list optic"
        + " of the same name, dropping a type witness the typed form does not need. A call whose"
        + " path does not focus on the container exactly, such as a `List<? extends T>` or an"
        + " `ArrayList`, whose key or `Traverse` does not fit the path, or whose result the code"
        + " around it takes with another element type, and a method reference to a shortcut, are"
        + " marked rather than rewritten, with a way to replace each.";
  }

  @Override
  public Set<String> getTags() {
    return Set.of("higher-kinded-j", "deprecation", "migration");
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaIsoVisitor<>() {

      @Override
      public J.MethodInvocation visitMethodInvocation(
          J.MethodInvocation method, ExecutionContext ctx) {
        // The receiver is visited first, so each shortcut in a chain is rewritten.
        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
        Optional<Shortcut> matched = shortcutOf(mi.getMethodType());
        if (matched.isEmpty()) {
          return mi;
        }
        Shortcut shortcut = matched.get();
        JavaType focus = focusOf(mi, getCursor());
        if (!(focus instanceof JavaType.Parameterized container)
            || !fits(container, shortcut.container())) {
          return SearchResult.found(mi, notTaken(shortcut, focus));
        }
        Optional<String> argumentMisfit = argumentMisfit(mi, shortcut, container);
        if (argumentMisfit.isPresent()) {
          return SearchResult.found(mi, argumentMisfit.get());
        }
        JavaType.Parameterized returned = returnedPath(mi);
        JavaType result = resultOf(shortcut, container, returned);
        boolean resultRead = !resultUnread(method, getCursor());
        if (resultRead
            && (returned == null
                || !TypeUtils.isOfType(returned.getTypeParameters().get(1), result))) {
          return SearchResult.found(mi, otherResult(shortcut, returned, result));
        }
        maybeAddImport(shortcut.owner());
        return typed(
            mi,
            shortcut,
            returned == null || result == null
                ? null
                : returned.withTypeParameters(
                    List.of(returned.getTypeParameters().get(0), result)));
      }

      @Override
      public J.MemberReference visitMemberReference(
          J.MemberReference memberRef, ExecutionContext ctx) {
        J.MemberReference ref = super.visitMemberReference(memberRef, ctx);
        return shortcutOf(ref.getMethodType())
            .map(
                shortcut ->
                    SearchResult.found(
                        ref,
                        "Not rewritten: a method reference to "
                            + shortcut.call()
                            + "; call "
                            + shortcut.typedForm()
                            + " in a lambda instead"))
            .orElse(ref);
      }
    };
  }

  private static Optional<Shortcut> shortcutOf(JavaType.Method method) {
    return MATCHERS.stream()
        .filter(matched -> matched.matcher().matches(method))
        .map(Matched::shortcut)
        .findFirst();
  }

  /**
   * The focus of the path a call is made on: the path's second type argument, read through any
   * subtype of it, such as a navigator, with that subtype's own type arguments put in. Null where
   * no path type can be read.
   */
  private static JavaType focusOf(J.MethodInvocation call, Cursor cursor) {
    JavaType receiver;
    if (call.getSelect() != null) {
      receiver = call.getSelect().getType();
    } else {
      J.ClassDeclaration enclosing = cursor.firstEnclosing(J.ClassDeclaration.class);
      receiver = enclosing == null ? null : enclosing.getType();
    }
    return typeArgumentIn(receiver, PATHS, 1, 0);
  }

  /**
   * The type argument at {@code index} that a type gives one of {@code owners}, read through its
   * supertypes with their type arguments put in. Null where no owner, or no such argument, is
   * reached.
   */
  private static JavaType typeArgumentIn(JavaType type, Set<String> owners, int index, int depth) {
    if (depth > 16 || !(type instanceof JavaType.FullyQualified declared)) {
      return null;
    }
    if (owners.contains(declared.getFullyQualifiedName())) {
      return type instanceof JavaType.Parameterized owner
              && owner.getTypeParameters().size() > index
          ? owner.getTypeParameters().get(index)
          : null;
    }
    Map<String, JavaType> bindings = bindingsOf(type);
    return Stream.concat(
            Stream.ofNullable(declared.getSupertype()), declared.getInterfaces().stream())
        .map(supertype -> typeArgumentIn(substitute(supertype, bindings), owners, index, depth + 1))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  /** The type arguments a parameterised type gives its class's type parameters, by name. */
  private static Map<String, JavaType> bindingsOf(JavaType type) {
    Map<String, JavaType> bindings = new HashMap<>();
    if (type instanceof JavaType.Parameterized parameterized) {
      List<JavaType> formals = parameterized.getType().getTypeParameters();
      List<JavaType> actuals = parameterized.getTypeParameters();
      IntStream.range(0, Math.min(formals.size(), actuals.size()))
          .filter(i -> formals.get(i) instanceof JavaType.GenericTypeVariable)
          .forEach(
              i ->
                  bindings.put(
                      ((JavaType.GenericTypeVariable) formals.get(i)).getName(), actuals.get(i)));
    }
    return bindings;
  }

  private static JavaType substitute(JavaType type, Map<String, JavaType> bindings) {
    if (type instanceof JavaType.GenericTypeVariable variable
        && bindings.containsKey(variable.getName())) {
      return bindings.get(variable.getName());
    }
    if (type instanceof JavaType.Parameterized parameterized) {
      return parameterized.withTypeParameters(
          parameterized.getTypeParameters().stream()
              .map(argument -> substitute(argument, bindings))
              .toList());
    }
    return type;
  }

  /**
   * Whether a focus is the container exactly: its own type arguments are no wildcard. A wildcard
   * deeper in, as in {@code List<List<?>>}, is part of the element, which the typed form takes as
   * it is.
   */
  private static boolean fits(JavaType.Parameterized focus, String container) {
    return container.equals(focus.getFullyQualifiedName())
        && focus.getTypeParameters().stream()
            .noneMatch(
                argument ->
                    argument instanceof JavaType.GenericTypeVariable variable
                        && "?".equals(variable.getName()));
  }

  /** Why a call on a path that does not focus on the shortcut's container is not rewritten. */
  private static String notTaken(Shortcut shortcut, JavaType focus) {
    String container = simpleName(shortcut.container());
    String reads = "Not rewritten: " + shortcut.call() + " reads " + withArticle(container);
    if (focus == null) {
      return reads
          + ", and the recipe cannot read what this path focuses on; write "
          + shortcut.typedForm()
          + " where it focuses on "
          + withArticle(container)
          + " exactly";
    }
    String remedy;
    if (focus instanceof JavaType.FullyQualified declared
        && shortcut.container().equals(declared.getFullyQualifiedName())) {
      remedy =
          focus instanceof JavaType.Parameterized
              ? "view it as the " + container + " exactly through an Iso first"
              : "give the " + container + " its type arguments";
    } else if (TypeUtils.isAssignableTo(shortcut.container(), focus)) {
      remedy = "declare it as " + withArticle(container);
    } else {
      remedy = "compose the optic for what the path focuses on";
    }
    return reads
        + ", and this path focuses on "
        + typeName(focus)
        + ", which "
        + shortcut.typedForm()
        + " does not take; "
        + remedy;
  }

  /**
   * Why the argument a call passes does not fit its path, if it does not: a key that is not the
   * map's key type, or a {@code Traverse} for another witness than the {@code Kind}'s.
   */
  private static Optional<String> argumentMisfit(
      J.MethodInvocation call, Shortcut shortcut, JavaType.Parameterized container) {
    JavaType expected = container.getTypeParameters().getFirst();
    JavaType given = call.getArguments().getFirst().getType();
    return switch (shortcut.argument()) {
      case "key" ->
          TypeUtils.isAssignableTo(expected, given)
              ? Optional.empty()
              : Optional.of(
                  "Not rewritten: the key given to "
                      + shortcut.call()
                      + " is "
                      + typeName(given)
                      + ", and "
                      + shortcut.typedForm()
                      + " takes this map's "
                      + typeName(expected)
                      + " keys; pass a key of that type");
      case "traverse" -> {
        JavaType witness = typeArgumentIn(given, Set.of(TRAVERSE), 0, 0);
        yield TypeUtils.isOfType(witness, expected)
            ? Optional.empty()
            : Optional.of(
                "Not rewritten: the Traverse given to "
                    + shortcut.call()
                    + " is for "
                    + (witness == null ? "a witness the recipe cannot read" : typeName(witness))
                    + ", and this path holds a Kind of "
                    + typeName(expected)
                    + ", which "
                    + shortcut.typedForm()
                    + " needs; pass the Traverse for that witness");
      }
      default -> Optional.empty();
    };
  }

  /** The path type a call returns, as javac inferred it, or null where it cannot be read. */
  private static JavaType.Parameterized returnedPath(J.MethodInvocation call) {
    return call.getMethodType() != null
            && call.getMethodType().getReturnType() instanceof JavaType.Parameterized path
            && path.getTypeParameters().size() == 2
        ? path
        : null;
  }

  /**
   * What the typed form's path focuses on: the container's element, its last type argument, or the
   * list or pair built from it. A pair is built on the one the shortcut returned, so it is null
   * where that cannot be read.
   */
  private static JavaType resultOf(
      Shortcut shortcut, JavaType.Parameterized container, JavaType.Parameterized returned) {
    JavaType element = container.getTypeParameters().getLast();
    return switch (shortcut.result()) {
      case ELEMENT -> element;
      case LIST -> container;
      case HEAD_AND_TAIL -> pairOf(returned, element, container);
      case INIT_AND_LAST -> pairOf(returned, container, element);
    };
  }

  private static JavaType pairOf(JavaType.Parameterized returned, JavaType first, JavaType second) {
    return returned != null
            && returned.getTypeParameters().get(1) instanceof JavaType.Parameterized pair
        ? pair.withTypeParameters(List.of(first, second))
        : null;
  }

  /**
   * Whether nothing reads a call's element type: its result is discarded, or another shortcut is
   * called on it, which compiles on any focus and is judged by the recipe in its own right.
   */
  private static boolean resultUnread(J.MethodInvocation call, Cursor cursor) {
    Object parent = cursor.getParentTreeCursor().getValue();
    return parent instanceof J.Block
        || (parent instanceof J.MethodInvocation outer
            && outer.getSelect() != null
            && outer.getSelect().getId().equals(call.getId())
            && shortcutOf(outer.getMethodType()).isPresent());
  }

  /** Why a call whose result is read with another element type than the typed form's is kept. */
  private static String otherResult(
      Shortcut shortcut, JavaType.Parameterized returned, JavaType result) {
    if (returned == null || result == null) {
      return "Not rewritten: the recipe cannot read what this "
          + shortcut.call()
          + " gives; write "
          + shortcut.typedForm()
          + " and check the code around it";
    }
    return "Not rewritten: this "
        + shortcut.call()
        + " focuses on "
        + typeName(returned.getTypeParameters().get(1))
        + ", and "
        + shortcut.typedForm()
        + " would focus on "
        + typeName(result)
        + ", read from the path; write the typed form and make the code around it take "
        + typeName(result);
  }

  /**
   * The typed form of a call: the verb, without the shortcut's type witnesses, given the instance
   * that takes the shortcut's own arguments, so a comment written beside one stays with it. It
   * returns {@code returned}, the path the typed form gives, so a shortcut called on it reads the
   * focus it will have; null keeps the shortcut's own.
   */
  private static J.MethodInvocation typed(
      J.MethodInvocation call, Shortcut shortcut, JavaType.Parameterized returned) {
    // A matcher matches only a call that has a method type. The types derived from it drop the
    // shortcut's annotations, so a later recipe does not read the typed form as deprecated.
    JavaType.Method shortcutType =
        Objects.requireNonNull(call.getMethodType()).withAnnotations(List.of());
    JavaType.FullyQualified owner = JavaType.ShallowClass.build(shortcut.owner());
    JavaType instanceType = JavaType.ShallowClass.build(shortcut.instanceType());
    JavaType.Method factoryType =
        shortcutType
            .withDeclaringType(owner)
            .withName(shortcut.factory())
            .withReturnType(instanceType)
            .withFlags(EnumSet.of(Flag.Public, Flag.Static));
    JavaType.Method verbType =
        shortcutType
            .withName(shortcut.verb())
            .withParameterNames(List.of(shortcut.verbParameter()))
            .withParameterTypes(List.of(instanceType))
            .withReturnType(returned == null ? shortcutType.getReturnType() : returned);
    J.MethodInvocation instance =
        new J.MethodInvocation(
            Tree.randomId(),
            Space.EMPTY,
            Markers.EMPTY,
            JRightPadded.build(identifier(simpleName(shortcut.owner()), owner)),
            null,
            identifier(shortcut.factory(), factoryType),
            call.getPadding().getArguments().withBefore(Space.EMPTY),
            factoryType);
    JContainer<Expression> arguments =
        JContainer.build(
            call.getPadding().getArguments().getBefore(),
            List.of(JRightPadded.build((Expression) instance)),
            Markers.EMPTY);
    return call.getPadding()
        .withTypeParameters(null)
        .withName(call.getName().withSimpleName(shortcut.verb()).withType(verbType))
        .withMethodType(verbType)
        .getPadding()
        .withArguments(arguments);
  }

  private static J.Identifier identifier(String name, JavaType type) {
    return new J.Identifier(
        Tree.randomId(), Space.EMPTY, Markers.EMPTY, List.of(), name, type, null);
  }

  /** A type as javac writes it in a message, with a nested class after a full stop. */
  private static String typeName(JavaType type) {
    return TypeUtils.toString(type).replace('$', '.');
  }

  private static String withArticle(String name) {
    return (name.equals("Optional") ? "an " : "a ") + name;
  }

  private static String simpleName(String qualifiedName) {
    return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
  }
}
