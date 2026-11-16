// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeVariableName;
import com.palantir.javapoet.WildcardTypeName;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * The copy an identity leg hands over in place of the container it read, so that the wire and the
 * domain never share one: a caller that mutates a wire's list after {@code parse}, or a built
 * wire's list after {@code build}, changes only its own. The mapping tiers and the merge share it,
 * as they share the {@link NullScan}, so every identity leg copies alike.
 *
 * <p>A copy keeps the declared type, so a level copies only where a fresh container of that type
 * can be made without knowing more than the type says: exactly {@code List}, {@code Set}, {@code
 * Collection} or {@code Map}, an array (of references or of a primitive), or an {@code Optional}
 * whose value is itself such a level. {@code shape} is the outermost level, {@code inner} the copy
 * each of its elements (or values) takes in turn, and {@code element} the type those elements have,
 * which the lambda carrying {@code inner} takes its parameter as.
 *
 * <p>Every copy has the shape an element-lifted leg's bulk forms hand back: a fresh container in
 * the source's order, unmodifiable unless it is an {@code IdentityHashMap}, a set as a set and any
 * other collection as a list, and a map with one entry for each entry of the source, under the
 * source's own key. Unlike those bulk forms, it carries what the source holds, a {@code null}
 * element included, and a {@code null} container copies to {@code null}, so a copy never decides
 * anything the null scan or the read's guard answers for, and {@code build} stays total but for a
 * map no copy can keep, below. The helpers are generated into each Impl, as the null scan's are,
 * rather than called from the library: no library form copies without refusing a {@code null}, and
 * the library's own map rule is not API.
 *
 * <p>A collection copy compares the way a list or set does: a sorted or comparator-keyed source
 * ({@code TreeSet}) keeps its order but not its comparator, and a source that compares by identity
 * (an {@code ArrayDeque} in a {@code Collection}) compares by its elements. A map copy keeps every
 * key apart as the library's map optics do: an {@code IdentityHashMap} copies to an {@code
 * IdentityHashMap}, by {@code clone()} so that its order holds (a subclass into a plain one, so
 * none of its own code runs on the copy), and any other map to a {@code LinkedHashMap}, which keeps
 * a {@code TreeMap}'s order but not its comparator. The {@code IdentityHashMap} is handed over as
 * itself, not as an unmodifiable view, since a view of one is not an {@code IdentityHashMap}, and
 * it compares as one does, by the same key and value objects. A map holding two keys that {@code
 * equals} cannot tell apart would lose an entry there, so the copy throws an {@code
 * IllegalArgumentException} naming both, on {@code parse} as on {@code build}: it is a structurally
 * broken map rather than a wrong value. Map keys are structural, and never copied inside.
 *
 * <p>An array copies by {@code clone()}, which keeps its runtime component type, and that can be
 * narrower than the declared one (an {@code ArrayList[]} behind a {@code List<String>[]}), so only
 * a row that is itself an array, whose clone keeps its own class, is copied inside it.
 *
 * <p>Each helper says what it does with a {@code null} in the annotations {@link NullnessMarks}
 * writes, so that a nullness checker run over the Impl holds a copy to the read it was made from. A
 * helper is typed by the container it copies ({@code <C extends @Nullable List<E>> C}): a read
 * declared {@code @Nullable} copies to a value declared so too, and any other read to one declared
 * non-null, and an element type may be nullable ({@code E extends @Nullable Object}). Java bounds
 * no type variable by an array type, so no helper can type an array that way: an array copies
 * through the one guard {@code hkj$copyOrNull}, which hands a {@code null} back and a present array
 * to its copy, its clone or, for an array of arrays, the nested helper.
 *
 * <p>A helper's {@code C} is inferred from the read it is handed, so that read must be typed as
 * exactly the container the copy was made for: a copy is never of a subtype, and the helper hands
 * it back as {@code C}. Every read is: a record accessor, a merge source's accessor, a bean getter,
 * which the property takes its type from, and a library call declared to return the container. The
 * one getter that answers a subtype, a protobuf repeated string field's {@code ProtocolStringList},
 * is read cast up to its field's type ({@link WireShape.MessageField#widened}).
 *
 * <p>Any other type has no copy: a subtype such as {@code ArrayList}, {@code TreeSet} or {@code
 * LinkedHashMap}, any other interface ({@code Deque}, {@code SortedSet}), a type variable, or an
 * element declared through a wildcard. The copy stops there, and that level and everything inside
 * it are handed over as they are. So are the elements of a level that may be a set when copying one
 * could make two of them equal, which is any element holding a {@code Collection}, however deeply:
 * that copies into a list, which compares by its elements where an {@code ArrayDeque} compares by
 * identity. A raw container copies, but its elements, which name no type, are not copied inside.
 *
 * <p>The levels a copy rebuilds are also where its type may differ from the read's ({@link
 * #retyping}): a rebuilt level belongs to the side it is handed to alone, so a copy of a {@code
 * List<String>} may be typed as the {@code List<@Nullable String>} a site declares, and a level
 * handed over as it is may not.
 *
 * <p>The walk always ends: each level's element is a proper part of the type the level was given.
 */
record ContainerCopy(ContainerCopy.Shape shape, ContainerCopy inner, TypeMirror element) {

  private static final String HELPER = "hkj$copyOf";
  private static final String MAP_COPY = "hkj$mapCopy";
  private static final String OR_NULL = "hkj$copyOrNull";
  private static final ClassName FUNCTION = ClassName.get("java.util.function", "Function");
  private static final ClassName COLLECTIONS = ClassName.get("java.util", "Collections");
  private static final ClassName ARRAY_LIST = ClassName.get("java.util", "ArrayList");
  private static final ClassName LINKED_HASH_SET = ClassName.get("java.util", "LinkedHashSet");
  private static final ClassName LINKED_HASH_MAP = ClassName.get("java.util", "LinkedHashMap");
  private static final ClassName IDENTITY_HASH_MAP = ClassName.get("java.util", "IdentityHashMap");
  private static final ClassName SET = ClassName.get("java.util", "Set");
  private static final TypeName ANY = WildcardTypeName.subtypeOf(Object.class);

  /** One container level, the type it is recognised by, and which of its arguments are elements. */
  enum Shape {
    /** Exactly {@code List}: an unmodifiable list. */
    LIST(ClassName.get("java.util", "List"), 0),
    /** Exactly {@code Set}: an unmodifiable set in the source's order. */
    SET(ClassName.get("java.util", "Set"), 0),
    /** Exactly {@code Collection}: a set copies as a set, anything else as a list. */
    COLLECTION(ClassName.get("java.util", "Collection"), 0),
    /**
     * Exactly {@code Map}: a map in the source's order, every key kept apart, unmodifiable unless
     * it is an {@code IdentityHashMap}.
     */
    MAP(ClassName.get("java.util", "Map"), 1),
    /** An array of references or of a primitive, by {@code clone()}. */
    ARRAY(null, 0),
    /** Exactly {@code Optional} of a copied level: the present value's copy. */
    OPTIONAL(ClassName.get("java.util", "Optional"), 0);

    private final ClassName container;
    private final int argument;

    Shape(ClassName container, int argument) {
      this.container = container;
      this.argument = argument;
    }
  }

  /** The declared containers a type is matched against, by exact name. */
  private static final List<Shape> DECLARED =
      List.of(Shape.LIST, Shape.SET, Shape.COLLECTION, Shape.MAP, Shape.OPTIONAL);

  /**
   * The copy a value of this type takes, or null when the type has none (see the class javadoc).
   * Kind tests, not {@code instanceof}: javac's intersection type implements {@code DeclaredType}.
   *
   * @param type the declared type of the value an identity leg hands over
   * @return the copy, or null
   */
  static ContainerCopy of(TypeMirror type) {
    return switch (type.getKind()) {
      case ARRAY -> {
        TypeMirror component = ((ArrayType) type).getComponentType();
        // An array's copy is a clone, which keeps the source's runtime component type, and that can
        // be narrower than the declared one (an ArrayList[] behind a List<String>[]). Only a row
        // that is an array itself is sure to fit back in, since its own clone keeps its class.
        yield new ContainerCopy(
            Shape.ARRAY, component.getKind() == TypeKind.ARRAY ? of(component) : null, component);
      }
      case DECLARED -> declared((DeclaredType) type);
      default -> null;
    };
  }

  private static ContainerCopy declared(DeclaredType type) {
    String name = ((TypeElement) type.asElement()).getQualifiedName().toString();
    for (Shape shape : DECLARED) {
      if (shape.container.canonicalName().equals(name)) {
        List<? extends TypeMirror> arguments = type.getTypeArguments();
        // A raw container names no element, so nothing inside it is copied.
        TypeMirror element = arguments.isEmpty() ? null : arguments.get(shape.argument);
        ContainerCopy inner = element == null ? null : of(element);
        // An Optional is immutable: it is a level only when its value is one.
        if (shape == Shape.OPTIONAL && inner == null) {
          return null;
        }
        // A level that may be a set hands its elements over as they are where copying one could
        // make two of them equal, which would collapse them into a single element.
        boolean mayCollapse =
            (shape == Shape.SET || shape == Shape.COLLECTION) && inner != null && inner.mayMerge();
        return new ContainerCopy(shape, mayCollapse ? null : inner, element);
      }
    }
    return null;
  }

  /**
   * Whether the outermost level is a raw container, whose copy is made through its wildcard view
   * and so has a captured type rather than the raw one the value declares.
   */
  boolean raw() {
    return element == null;
  }

  /**
   * The element types this copy's lambdas take their parameters as, level by level: javac infers
   * them, so a raw type among them is a warning the Impl member holding the copy answers for.
   */
  Stream<TypeMirror> inferred() {
    return inner == null ? Stream.empty() : Stream.concat(Stream.of(element), inner.inferred());
  }

  /**
   * How a copy of one type can be typed as another of the same shape, which declares some position
   * inside it nullable that the copy's does not, or the other way round.
   */
  enum Retyping {
    /**
     * No position widens: the copy is the other type as it stands, or narrower where it differs.
     */
    AS_IS,
    /** Some position widens and none narrows, so the copy is cast to the wider type. */
    CAST,
    /**
     * Neither: a position that differs is one the copy shares, or one widens beside one that
     * narrows.
     */
    NONE
  }

  /**
   * How a copy of {@code from} can be typed as {@code to}. Every position at which the two types
   * declare a different nullness must be one {@code copy} makes fresh: an element, key or value of
   * a level the copy rebuilds, or a wildcard's bound there. A fresh level belongs to the side it is
   * handed to alone, so a wider type lets nothing the other side holds take a {@code null}, and a
   * narrower one is checked before it is read. Below a level handed over as it is, or inside a
   * map's key, which no copy rebuilds, the two sides share the container, and either side could put
   * a {@code null} into what the other declares holds none. Neither is an array's component below
   * the top, whose nullness the null scan's element lambda does not keep. Two types of different
   * kinds, as a bridged wildcard and the wire's container are, pair nowhere, and positions are
   * paired by index.
   *
   * @param copy the copy of {@code from}, or null where it has none
   * @param from the type copied
   * @param to the type the copy is to be typed as
   * @return how the copy can be typed as {@code to}
   */
  static Retyping retyping(ContainerCopy copy, TypeMirror from, TypeMirror to) {
    List<Position> positions = positions(true, true, copy, from, to).toList();
    if (!positions.stream().allMatch(Position::sound)) {
      return Retyping.NONE;
    }
    if (positions.stream().noneMatch(Position::widened)) {
      return Retyping.AS_IS;
    }
    return positions.stream().anyMatch(Position::narrowed) ? Retyping.NONE : Retyping.CAST;
  }

  /**
   * One position inside a type, read on both sides: whether each declares it nullable, and whether
   * the level holding it is fresh.
   */
  private record Position(boolean from, boolean to, boolean fresh) {

    /** Shapes that do not pair: a position nothing makes fresh, declared nullable on one side. */
    static final Position UNPAIRED = new Position(false, true, false);

    boolean widened() {
      return to && !from;
    }

    boolean narrowed() {
      return from && !to;
    }

    boolean sound() {
      return fresh || from == to;
    }
  }

  /**
   * The positions inside {@code from} and {@code to}, paired: {@code fresh} says whether the level
   * holding the two types is rebuilt, {@code top} whether the two are the copy's own types, and
   * {@code copy} is their own copy, or null. A wildcard's bound stands where the wildcard does; any
   * other type's arguments are fresh where the type is a level the copy rebuilds, an array below
   * the top aside, and only the level's element is copied further inside.
   */
  private static Stream<Position> positions(
      boolean fresh, boolean top, ContainerCopy copy, TypeMirror from, TypeMirror to) {
    if (from.getKind() != to.getKind()) {
      return Stream.of(Position.UNPAIRED);
    }
    List<? extends TypeMirror> froms = NullableAnnotations.inside(from).toList();
    List<? extends TypeMirror> tos = NullableAnnotations.inside(to).toList();
    boolean wildcard = from.getKind() == TypeKind.WILDCARD;
    boolean held = wildcard ? fresh : copy != null && (top || from.getKind() != TypeKind.ARRAY);
    return IntStream.range(0, Math.min(froms.size(), tos.size()))
        .boxed()
        .flatMap(
            i ->
                Stream.concat(
                    Stream.of(
                        new Position(
                            NullableAnnotations.nullableHere(froms.get(i)),
                            NullableAnnotations.nullableHere(tos.get(i)),
                            held)),
                    positions(
                        held,
                        false,
                        wildcard
                            ? copy
                            : copy != null && i == copy.shape.argument ? copy.inner : null,
                        froms.get(i),
                        tos.get(i))));
  }

  /**
   * {@code value} as an identity leg hands it over: through {@code copy}, or as it is where the
   * value's type has no copy.
   *
   * @param copy the copy the leg takes, or null
   * @param value the read the leg makes
   * @param taken the names the copy's element lambdas must keep clear of
   * @return the value to hand over
   */
  static CodeBlock through(ContainerCopy copy, CodeBlock value, Set<String> taken) {
    return copy == null ? value : copy.on(value, taken);
  }

  /**
   * Whether copying a value of this type can make it equal to one it differed from. A {@code
   * Collection} that is neither a list nor a set copies into a list, which compares by its elements
   * where the source may compare by identity (an {@code ArrayDeque}), so two such values become
   * equal; and a level holding one passes that on, since its own equality is its elements'. An
   * array never does: it compares by identity, each clone is a value of its own, and the only level
   * inside one is another array.
   */
  private boolean mayMerge() {
    return shape == Shape.COLLECTION || (inner != null && inner.mayMerge());
  }

  /** Whether the copy reaches inside this level's elements, not just the level itself. */
  boolean nested() {
    return inner != null;
  }

  /**
   * The copy of {@code value}: this level's helper, with the next level's copy riding along as a
   * lambda, its parameter clear of every name in {@code taken}.
   */
  private CodeBlock on(CodeBlock value, Set<String> taken) {
    return on(value, taken, 0);
  }

  private CodeBlock on(CodeBlock value, Set<String> taken, int depth) {
    // No helper can type an array by the array itself, so the guard hands a null one back.
    if (shape == Shape.ARRAY) {
      return CodeBlock.of("$L($L, $L)", OR_NULL, value, arrayCopy(taken, depth + 1));
    }
    if (inner != null) {
      return CodeBlock.of("$L($L, $L)", HELPER, value, inner.asLambda(taken, depth + 1));
    }
    // A raw container reaches its helper through the wildcard view of its type, which it converts
    // to without a warning; the helper's own parameter would ask for an unchecked conversion.
    return raw()
        ? CodeBlock.of("$L(($T) $L)", HELPER, wildcardView(shape), value)
        : CodeBlock.of("$L($L)", HELPER, value);
  }

  /** The parameter is named for its depth ({@code e}, then {@code e2}, {@code e3}). */
  private CodeBlock asLambda(Set<String> taken, int depth) {
    Set<String> scope = new HashSet<>(taken);
    String element = ProcessorUtils.freeName(depth == 1 ? "e" : "e" + depth, scope);
    return CodeBlock.of("$L -> $L", element, on(CodeBlock.of("$L", element), scope, depth));
  }

  /**
   * The copy the guard hands a present array to: its clone, or, where its rows are arrays too, the
   * nested helper, which copies each row through the guard in turn.
   */
  private CodeBlock arrayCopy(Set<String> taken, int depth) {
    Set<String> scope = new HashSet<>(taken);
    String array = ProcessorUtils.freeName(depth == 1 ? "e" : "e" + depth, scope);
    return inner == null
        ? CodeBlock.of("$L -> $L.clone()", array, array)
        : CodeBlock.of("$L -> $L($L, $L)", array, HELPER, array, inner.asLambda(scope, depth + 1));
  }

  private static TypeName wildcardView(Shape shape) {
    return shape == Shape.MAP
        ? ParameterizedTypeName.get(shape.container, ANY, ANY)
        : ParameterizedTypeName.get(shape.container, ANY);
  }

  /**
   * Each level's helper, as the copy calls it: the one-level form or the one taking a lambda. A
   * one-level array has no helper of its own: its copy is a clone, which the guard makes.
   */
  private record Form(Shape shape, boolean nested) {}

  private Stream<Form> forms() {
    Form form = new Form(shape, inner != null);
    return inner == null ? Stream.of(form) : Stream.concat(Stream.of(form), inner.forms());
  }

  /** The one order helpers are declared in, so that every Impl declares them alike. */
  private static final Comparator<Form> ORDER =
      Comparator.comparing(Form::shape).thenComparing(Form::nested);

  /**
   * The helpers the given copies call, each once, in one fixed order: the map copy both map helpers
   * share comes after them, where either is called, and the guard every array copies through comes
   * last, where one is copied.
   *
   * @param copies the copies an Impl's legs make
   * @param marks the nullness annotations the helpers are written with
   * @return the helper methods to declare
   */
  static List<MethodSpec> helpers(Stream<ContainerCopy> copies, NullnessMarks marks) {
    Set<Form> used = copies.flatMap(ContainerCopy::forms).collect(Collectors.toSet());
    List<MethodSpec> helpers = new ArrayList<>();
    used.stream()
        .filter(form -> form.shape() != Shape.ARRAY || form.nested())
        .sorted(ORDER)
        .map(form -> helper(form, marks))
        .forEach(helpers::add);
    if (used.stream().anyMatch(form -> form.shape() == Shape.MAP)) {
      helpers.add(mapCopyHelper(marks));
    }
    if (used.stream().anyMatch(form -> form.shape() == Shape.ARRAY)) {
      helpers.add(orNullHelper(marks));
    }
    return List.copyOf(helpers);
  }

  private static MethodSpec helper(Form form, NullnessMarks marks) {
    return switch (form.shape()) {
      case LIST -> form.nested() ? nestedListHelper(marks) : listHelper(marks);
      case SET -> form.nested() ? nestedSetHelper(marks) : setHelper(marks);
      case COLLECTION -> form.nested() ? nestedCollectionHelper(marks) : collectionHelper(marks);
      case MAP -> form.nested() ? nestedMapHelper(marks) : mapHelper(marks);
      // Only an array of arrays has a helper: a one-level array is cloned where the guard runs.
      case ARRAY -> nestedArrayHelper(marks);
      // An Optional is a level only when its value is one, so it only ever takes the lambda.
      case OPTIONAL -> optionalHelper(marks);
    };
  }

  // The helper bodies. Each copies a null argument to null, an array's through the guard, and
  // carries a null element as it is.

  private static MethodSpec.Builder helperBuilder(
      TypeName type, String javadoc, TypeVariableName... variables) {
    return helperBuilder(HELPER, type, javadoc, variables);
  }

  private static MethodSpec.Builder helperBuilder(
      String name, TypeName type, String javadoc, TypeVariableName... variables) {
    return MethodSpec.methodBuilder(name)
        .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
        .addTypeVariables(List.of(variables))
        .returns(type)
        .addJavadoc(javadoc);
  }

  /**
   * A form typed by the container it copies, {@code C}, which is exactly the shape's container over
   * {@code arguments}: its return type, and the parameter it copies, with the {@code each} lambda a
   * nested form also takes, over the {@code nested} element type. A null copies to null and a
   * present container to a present one, so the copy is declared as the read it was made from.
   */
  private static MethodSpec.Builder containerBuilder(
      Shape shape,
      String javadoc,
      NullnessMarks marks,
      TypeVariableName nested,
      TypeVariableName... arguments) {
    TypeVariableName c =
        TypeVariableName.get(
            "C", marks.nullable(ParameterizedTypeName.get(shape.container, arguments)));
    List<TypeVariableName> variables = new ArrayList<>(List.of(arguments));
    variables.add(c);
    // An Optional holds one value, where every other container holds values.
    String parameter = shape == Shape.OPTIONAL ? "value" : "values";
    MethodSpec.Builder builder =
        helperBuilder(c, javadoc, variables.toArray(TypeVariableName[]::new))
            .addParameter(c, parameter);
    if (nested != null) {
      builder.addParameter(elementCopy(nested), "each");
    }
    return builder.addCode(nullToNull(parameter));
  }

  /** A collection form over {@code E}, whose elements may be null. */
  private static MethodSpec.Builder collectionBuilder(
      Shape shape, String javadoc, NullnessMarks marks, boolean nested) {
    TypeVariableName e = marks.nullableBounded("E");
    return containerBuilder(shape, javadoc, marks, nested ? e : null, e);
  }

  /**
   * The {@code Function} a nested form takes each element's own copy as. It is not a wildcard type,
   * so that the lambda's parameter is the element type as the call declares it, a nullable one
   * included.
   */
  private static TypeName elementCopy(TypeName element) {
    return ParameterizedTypeName.get(FUNCTION, element, element);
  }

  private static CodeBlock nullToNull(String argument) {
    return CodeBlock.builder()
        .beginControlFlow("if ($L == null)", argument)
        .addStatement("return $L", argument)
        .endControlFlow()
        .build();
  }

  /**
   * Hands {@code made}, of the container type the helper copies, back as {@code C}, the type of the
   * read it was made from: they are one type, since each read is typed as exactly that container
   * (the class javadoc says how).
   */
  private static CodeBlock handedBackAs(CodeBlock made) {
    return CodeBlock.builder()
        .add(
            "@$T($S) // C is the copied container itself: a read typed as a subtype is cast up\n",
            SuppressWarnings.class,
            "unchecked")
        .addStatement("C copy = (C) $L", made)
        .addStatement("return copy")
        .build();
  }

  /**
   * Fills {@code copied}, declared to hold nullable elements and made by {@code empty}, with each
   * element of {@code values}, a present one through its own copy.
   */
  private static CodeBlock copiedInto(Shape shape, NullnessMarks marks, CodeBlock empty) {
    TypeVariableName e = TypeVariableName.get("E");
    return CodeBlock.builder()
        .addStatement(
            "$T copied = $L", ParameterizedTypeName.get(shape.container, marks.nullable(e)), empty)
        .beginControlFlow("for ($T value : values)", e)
        .addStatement("copied.add(value == null ? null : each.apply(value))")
        .endControlFlow()
        .build();
  }

  private static CodeBlock listCopy(NullnessMarks marks) {
    return copiedInto(Shape.LIST, marks, CodeBlock.of("new $T<>(values.size())", ARRAY_LIST));
  }

  private static CodeBlock setCopy(NullnessMarks marks) {
    return copiedInto(
        Shape.SET, marks, CodeBlock.of("$T.newLinkedHashSet(values.size())", LINKED_HASH_SET));
  }

  private static MethodSpec listHelper(NullnessMarks marks) {
    return collectionBuilder(
            Shape.LIST,
            "Copies a same-typed list so the two sides never share it: an unmodifiable list"
                + " in the same order, null elements included; a null list copies to null.\n",
            marks,
            false)
        .addCode(
            handedBackAs(
                CodeBlock.of("$T.unmodifiableList(new $T<>(values))", COLLECTIONS, ARRAY_LIST)))
        .build();
  }

  private static MethodSpec nestedListHelper(NullnessMarks marks) {
    return collectionBuilder(
            Shape.LIST,
            "Copies a same-typed list of containers so the two sides never share it: an"
                + " unmodifiable list in the same order, each present element copied in turn and"
                + " null elements included; a null list copies to null.\n",
            marks,
            true)
        .addCode(listCopy(marks))
        .addCode(handedBackAs(CodeBlock.of("$T.unmodifiableList(copied)", COLLECTIONS)))
        .build();
  }

  private static MethodSpec setHelper(NullnessMarks marks) {
    return collectionBuilder(
            Shape.SET,
            "Copies a same-typed set so the two sides never share it: an unmodifiable set in"
                + " the same iteration order, a null element included; a null set copies to"
                + " null.\n",
            marks,
            false)
        .addCode(
            handedBackAs(
                CodeBlock.of("$T.unmodifiableSet(new $T<>(values))", COLLECTIONS, LINKED_HASH_SET)))
        .build();
  }

  private static MethodSpec nestedSetHelper(NullnessMarks marks) {
    return collectionBuilder(
            Shape.SET,
            "Copies a same-typed set of containers so the two sides never share it: an"
                + " unmodifiable set in the same iteration order, each present element copied in"
                + " turn and a null element included; a null set copies to null.\n",
            marks,
            true)
        .addCode(setCopy(marks))
        .addCode(handedBackAs(CodeBlock.of("$T.unmodifiableSet(copied)", COLLECTIONS)))
        .build();
  }

  private static MethodSpec collectionHelper(NullnessMarks marks) {
    return collectionBuilder(
            Shape.COLLECTION,
            "Copies a same-typed collection so the two sides never share it: a set as an"
                + " unmodifiable set in the same iteration order, anything else as an unmodifiable"
                + " list in the same order, null elements included; a null collection copies to"
                + " null.\n",
            marks,
            false)
        .addCode(
            handedBackAs(
                CodeBlock.of(
                    "(values instanceof $T<?> ? $T.unmodifiableSet(new $T<>(values))"
                        + " : $T.unmodifiableList(new $T<>(values)))",
                    SET,
                    COLLECTIONS,
                    LINKED_HASH_SET,
                    COLLECTIONS,
                    ARRAY_LIST)))
        .build();
  }

  private static MethodSpec nestedCollectionHelper(NullnessMarks marks) {
    return collectionBuilder(
            Shape.COLLECTION,
            "Copies a same-typed collection of containers so the two sides never share it:"
                + " a set as an unmodifiable set in the same iteration order, anything else as an"
                + " unmodifiable list in the same order, each present element copied in turn and"
                + " null elements included; a null collection copies to null.\n",
            marks,
            true)
        .beginControlFlow("if (values instanceof $T<?>)", SET)
        .addCode(setCopy(marks))
        .addCode(handedBackAs(CodeBlock.of("$T.unmodifiableSet(copied)", COLLECTIONS)))
        .endControlFlow()
        .addCode(listCopy(marks))
        .addCode(handedBackAs(CodeBlock.of("$T.unmodifiableList(copied)", COLLECTIONS)))
        .build();
  }

  /** A map form over {@code K} and {@code V}, whose keys and values may be null. */
  private static MethodSpec.Builder mapBuilder(
      String javadoc, NullnessMarks marks, boolean nested) {
    TypeVariableName k = marks.nullableBounded("K");
    TypeVariableName v = marks.nullableBounded("V");
    return containerBuilder(Shape.MAP, javadoc, marks, nested ? v : null, k, v);
  }

  /**
   * Hands {@code copied} back: an {@code IdentityHashMap} as itself, since a read-only view of one
   * is not an {@code IdentityHashMap}, and the next copy made from the view would look its keys up
   * by {@code equals}; any other map as an unmodifiable view.
   */
  private static CodeBlock handBack() {
    return handedBackAs(
        CodeBlock.of(
            "(copied instanceof $T<?, ?> ? copied : $T.unmodifiableMap(copied))",
            IDENTITY_HASH_MAP,
            COLLECTIONS));
  }

  /** The map copy a map form fills, its keys and values declared to hold a null. */
  private static CodeBlock mapCopied(NullnessMarks marks) {
    return CodeBlock.of("$T copied = $L(values);\n", heldMap(marks), MAP_COPY);
  }

  /**
   * A map whose keys and values may be null, as a fresh copy is declared to hold them: an element
   * type that may be nullable can be written into a map only if the map is declared to hold one.
   */
  private static TypeName heldMap(NullnessMarks marks) {
    return ParameterizedTypeName.get(Shape.MAP.container, marks.nullable(K), marks.nullable(V));
  }

  private static MethodSpec mapHelper(NullnessMarks marks) {
    return mapBuilder(
            "Copies a same-typed map so the two sides never share it: a new map in the same entry"
                + " order, every key kept apart as the source keeps it and null values included,"
                + " unmodifiable unless it is an IdentityHashMap of its own; a null map copies to"
                + " null.\n",
            marks,
            false)
        .addCode(mapCopied(marks))
        .addCode(handBack())
        .build();
  }

  private static MethodSpec nestedMapHelper(NullnessMarks marks) {
    return mapBuilder(
            "Copies a same-typed map of containers so the two sides never share it: a new map in"
                + " the same entry order, every key kept apart as the source keeps it, each present"
                + " value copied in turn and null values included, unmodifiable unless it is an"
                + " IdentityHashMap of its own; a null map copies to null.\n",
            marks,
            true)
        .addCode(mapCopied(marks))
        // Replacing a value moves no entry, in a LinkedHashMap or an IdentityHashMap alike.
        .addStatement("copied.replaceAll((key, value) -> value == null ? null : each.apply(value))")
        .addCode(handBack())
        .build();
  }

  /**
   * The new, mutable map both map helpers fill: every key of the source kept apart as the source
   * keeps it, or a refusal naming the two keys a {@code LinkedHashMap} would merge. The refusal
   * names the remedies the copy offers: the {@code IdentityHashMap} itself, where the source is a
   * view of one, or the map's own type declared, which is handed over as it is. The pattern binds
   * an {@code IdentityHashMap}, so that copying a subclass passes one to its constructor, and only
   * the clone's cast answers for a suppression. The copy is declared to hold null keys and values,
   * as the source may, and each map form hands it back as the map its call declares.
   */
  private static MethodSpec mapCopyHelper(NullnessMarks marks) {
    TypeVariableName k = marks.nullableBounded("K");
    TypeVariableName v = marks.nullableBounded("V");
    TypeName map = heldMap(marks);
    return helperBuilder(
            MAP_COPY,
            map,
            "A new, mutable map with every entry of a same-typed map, in its entry order: an"
                + " IdentityHashMap for an IdentityHashMap, so keys that are equal but not the same"
                + " object stay apart, and a LinkedHashMap for any other map, which refuses a map"
                + " holding two keys that equals() cannot tell apart rather than lose an entry.\n",
            k,
            v)
        .addParameter(
            ParameterizedTypeName.get(
                Shape.MAP.container, WildcardTypeName.subtypeOf(K), WildcardTypeName.subtypeOf(V)),
            "values")
        .beginControlFlow(
            "if (values instanceof $T<? extends $T, ? extends $T> identity)",
            IDENTITY_HASH_MAP,
            K,
            V)
        .beginControlFlow("if (identity.getClass() != $T.class)", IDENTITY_HASH_MAP)
        .addCode(
            "// A subclass is copied into a plain one, so none of its code runs on the copy.\n")
        .addStatement("return new $T<>(identity)", IDENTITY_HASH_MAP)
        .endControlFlow()
        .addCode(
            "@$T($S) // a fresh clone nothing else holds, so it is safe to view as the copy\n",
            SuppressWarnings.class,
            "unchecked")
        .addStatement("$T clone = ($T) identity.clone()", map, map)
        .addStatement("return clone")
        .endControlFlow()
        .addStatement("$T copy = $T.newLinkedHashMap(values.size())", map, LINKED_HASH_MAP)
        .beginControlFlow(
            "for ($T entry : values.entrySet())",
            ParameterizedTypeName.get(
                Shape.MAP.container.nestedClass("Entry"),
                WildcardTypeName.subtypeOf(K),
                WildcardTypeName.subtypeOf(V)))
        .addStatement("int before = copy.size()")
        .addStatement("copy.put(entry.getKey(), entry.getValue())")
        .beginControlFlow("if (copy.size() == before)")
        .addCode("// The copy kept the earlier of the two keys, the one equal to this key.\n")
        .addStatement(
            "$T<$T> kept = new $T<>(copy.keySet())", List.class, marks.nullable(K), ARRAY_LIST)
        .addStatement(
            "throw new $T($S + values.getClass().getName() + $S"
                + " + kept.get(kept.indexOf(entry.getKey())) + $S + entry.getKey() + $S)",
            IllegalArgumentException.class,
            "A generated Impl cannot copy this ",
            " without losing an entry: its keys ",
            " and ",
            " are equal by equals(), and the copy looks keys up by equals(). If this is a view of"
                + " an IdentityHashMap, hold the IdentityHashMap itself; otherwise declare the"
                + " map's own type, which is handed over as it is. A key changed after it went into"
                + " the map also causes this.")
        .endControlFlow()
        .endControlFlow()
        .addStatement("return copy")
        .build();
  }

  private static final TypeVariableName K = TypeVariableName.get("K");
  private static final TypeVariableName V = TypeVariableName.get("V");

  /**
   * Copies an array of arrays, each row through the copy it takes, which is the guard's, so a null
   * row is handed back as it is. The array itself is never null here: the guard calls this only
   * with a present one.
   */
  private static MethodSpec nestedArrayHelper(NullnessMarks marks) {
    TypeVariableName e = marks.nullableBounded("E");
    return helperBuilder(
            ArrayTypeName.of(e),
            "Copies a same-typed array of arrays so the two sides never share it: a clone, each"
                + " row copied in turn, a null row by handing it back.\n",
            e)
        .addParameter(ArrayTypeName.of(e), "values")
        .addParameter(elementCopy(e), "each")
        .addStatement("$T copy = values.clone()", ArrayTypeName.of(e))
        .beginControlFlow("for (int i = 0; i < copy.length; i++)")
        .addStatement("copy[i] = each.apply(copy[i])")
        .endControlFlow()
        .addStatement("return copy")
        .build();
  }

  /**
   * The guard an array copies through: a null is handed back, and a present array goes to {@code
   * copy}, which is told it is present. Typed by the value it guards, so the copy is declared as
   * the read it was made from.
   */
  private static MethodSpec orNullHelper(NullnessMarks marks) {
    TypeVariableName a = marks.nullableBounded("A");
    TypeName present = marks.nonNull(TypeVariableName.get("A"));
    return helperBuilder(
            OR_NULL,
            a,
            "Copies an array that may be null, which no helper can type by the array itself: a"
                + " null is handed back, and a present array is copied.\n",
            a)
        .addParameter(a, "value")
        .addParameter(ParameterizedTypeName.get(FUNCTION, present, present), "copy")
        .addStatement("return value == null ? value : copy.apply(value)")
        .build();
  }

  private static MethodSpec optionalHelper(NullnessMarks marks) {
    // An Optional never holds a null, so its value type keeps the non-null bound.
    TypeVariableName e = TypeVariableName.get("E");
    return containerBuilder(
            Shape.OPTIONAL,
            "Copies a same-typed Optional of a container so the two sides never share the"
                + " container: the present value's copy; a null Optional copies to null.\n",
            marks,
            e,
            e)
        .addCode(handedBackAs(CodeBlock.of("value.map(each)")))
        .build();
  }
}
