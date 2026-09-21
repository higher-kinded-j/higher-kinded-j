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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
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
 * <p>Every copy is the one {@code Traversals} rebuilds a traversed container into and the one an
 * element-lifted leg's bulk forms hand back: a fresh, unmodifiable container in the source's order,
 * a set as a set and any other collection as a list. It carries what the source holds, a {@code
 * null} element included, and a {@code null} container copies to {@code null}, so a copy never
 * decides anything the null scan or the read's guard answers for, and {@code build} stays total. A
 * sorted or comparator-keyed source ({@code TreeSet}, {@code TreeMap}) keeps its order but not its
 * comparator. An array copies by {@code clone()}, so it keeps its runtime component type. Map keys
 * are structural, and never copied inside.
 *
 * <p>Any other type has no copy: a subtype such as {@code ArrayList}, {@code TreeSet} or {@code
 * LinkedHashMap}, any other interface ({@code Deque}, {@code SortedSet}), a type variable, or an
 * element declared through a wildcard. The copy stops there, and that level and everything inside
 * it are shared, as they were before any copy. A raw container copies, but its elements, which name
 * no type, are not copied inside.
 *
 * <p>The walk always ends: each level's element is a proper part of the type the level was given.
 */
record ContainerCopy(ContainerCopy.Shape shape, ContainerCopy inner, TypeMirror element) {

  private static final String HELPER = "hkj$copyOf";
  private static final ClassName FUNCTION = ClassName.get("java.util.function", "Function");
  private static final ClassName COLLECTIONS = ClassName.get("java.util", "Collections");
  private static final ClassName ARRAY_LIST = ClassName.get("java.util", "ArrayList");
  private static final ClassName LINKED_HASH_SET = ClassName.get("java.util", "LinkedHashSet");
  private static final ClassName LINKED_HASH_MAP = ClassName.get("java.util", "LinkedHashMap");
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
    /** Exactly {@code Map}: an unmodifiable map in the source's order, keys as they are. */
    MAP(ClassName.get("java.util", "Map"), 1),
    /** A reference array, by {@code clone()}. */
    ARRAY(null, 0),
    /** A primitive array, by {@code clone()}; it holds nothing to copy inside. */
    PRIMITIVE_ARRAY(null, 0),
    /** Exactly {@code Optional} of a copied level: the present value's copy. */
    OPTIONAL(ClassName.get("java.util", "Optional"), 0);

    private final ClassName container;
    private final int argument;

    Shape(ClassName container, int argument) {
      this.container = container;
      this.argument = argument;
    }
  }

  /** The declared containers, in the order a type is tried against them. */
  private static final List<Shape> DECLARED =
      List.of(Shape.LIST, Shape.SET, Shape.COLLECTION, Shape.MAP, Shape.OPTIONAL);

  /**
   * The copy a value of this type takes, or null when the type has none (see the class javadoc).
   * Kind tests, not {@code instanceof}: javac's intersection type implements {@code DeclaredType}.
   *
   * @param type the declared type of the identity-copied value
   * @return the copy, or null
   */
  static ContainerCopy of(TypeMirror type) {
    return switch (type.getKind()) {
      case ARRAY -> {
        TypeMirror component = ((ArrayType) type).getComponentType();
        yield component.getKind().isPrimitive()
            ? new ContainerCopy(Shape.PRIMITIVE_ARRAY, null, component)
            : new ContainerCopy(Shape.ARRAY, of(component), component);
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
        return shape == Shape.OPTIONAL && inner == null
            ? null
            : new ContainerCopy(shape, inner, element);
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
   * The copy of {@code value}: this level's helper, with the next level's copy riding along as a
   * lambda, its parameter clear of every name in {@code taken}.
   */
  CodeBlock on(CodeBlock value, Set<String> taken) {
    return on(value, taken, 0);
  }

  private CodeBlock on(CodeBlock value, Set<String> taken, int depth) {
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

  private static TypeName wildcardView(Shape shape) {
    return shape == Shape.MAP
        ? ParameterizedTypeName.get(shape.container, ANY, ANY)
        : ParameterizedTypeName.get(shape.container, ANY);
  }

  /**
   * Each level's helper, as the copy calls it: the one-level form or the one taking a lambda, and,
   * for a primitive array, its component type, since each primitive array needs its own helper.
   */
  private record Form(Shape shape, boolean nested, TypeName primitive) {}

  private Stream<Form> forms() {
    Form form =
        new Form(
            shape,
            inner != null,
            shape == Shape.PRIMITIVE_ARRAY ? TypeName.get(element).withoutAnnotations() : null);
    return inner == null ? Stream.of(form) : Stream.concat(Stream.of(form), inner.forms());
  }

  /** The one order helpers are declared in, so that every Impl declares them alike. */
  private static final Comparator<Form> ORDER =
      Comparator.comparing(Form::shape)
          .thenComparing(Form::nested)
          .thenComparing(
              Form::primitive, Comparator.nullsFirst(Comparator.comparing(TypeName::toString)));

  /**
   * The helpers the given copies call, each once, in one fixed order.
   *
   * @param copies the copies an Impl's legs make
   * @return the helper methods to declare
   */
  static List<MethodSpec> helpers(Stream<ContainerCopy> copies) {
    Set<Form> used = copies.flatMap(ContainerCopy::forms).collect(Collectors.toSet());
    return used.stream().sorted(ORDER).map(ContainerCopy::helper).toList();
  }

  private static MethodSpec helper(Form form) {
    return switch (form.shape()) {
      case LIST -> form.nested() ? nestedListHelper() : listHelper();
      case SET -> form.nested() ? nestedSetHelper() : setHelper();
      case COLLECTION -> form.nested() ? nestedCollectionHelper() : collectionHelper();
      case MAP -> form.nested() ? nestedMapHelper() : mapHelper();
      case ARRAY -> form.nested() ? nestedArrayHelper() : arrayHelper();
      case PRIMITIVE_ARRAY -> primitiveArrayHelper(form.primitive());
      // An Optional is a level only when its value is one, so it only ever takes the lambda.
      case OPTIONAL -> optionalHelper();
    };
  }

  // The helper bodies. Each copies a null argument to null and carries a null element as it is.

  private static final TypeVariableName E = TypeVariableName.get("E");

  private static MethodSpec.Builder helperBuilder(
      TypeName type, String javadoc, TypeVariableName... variables) {
    return MethodSpec.methodBuilder(HELPER)
        .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
        .addTypeVariables(List.of(variables))
        .returns(type)
        .addJavadoc(javadoc);
  }

  /** A collection form over {@code E}: its return type, and its {@code values} parameter. */
  private static MethodSpec.Builder collectionBuilder(Shape shape, String javadoc) {
    return helperBuilder(ParameterizedTypeName.get(shape.container, E), javadoc, E)
        .addParameter(
            ParameterizedTypeName.get(shape.container, WildcardTypeName.subtypeOf(E)), "values");
  }

  /** The {@code Function} a nested form takes each element's own copy as. */
  private static TypeName elementCopy(TypeName element) {
    return ParameterizedTypeName.get(
        FUNCTION, WildcardTypeName.supertypeOf(element), WildcardTypeName.subtypeOf(element));
  }

  private static CodeBlock nullToNull(String argument) {
    return CodeBlock.builder()
        .beginControlFlow("if ($L == null)", argument)
        .addStatement("return null")
        .endControlFlow()
        .build();
  }

  /**
   * Fills {@code copy}, declared as {@code type} and made by {@code empty}, with each element of
   * {@code values}, a present one through its own copy.
   */
  private static CodeBlock copiedInto(TypeName type, CodeBlock empty) {
    return CodeBlock.builder()
        .addStatement("$T copy = $L", type, empty)
        .beginControlFlow("for ($T value : values)", E)
        .addStatement("copy.add(value == null ? null : each.apply(value))")
        .endControlFlow()
        .build();
  }

  private static CodeBlock listCopy() {
    return copiedInto(
        ParameterizedTypeName.get(Shape.LIST.container, E),
        CodeBlock.of("new $T<>(values.size())", ARRAY_LIST));
  }

  private static CodeBlock setCopy() {
    return copiedInto(
        ParameterizedTypeName.get(Shape.SET.container, E),
        CodeBlock.of("$T.newLinkedHashSet(values.size())", LINKED_HASH_SET));
  }

  private static MethodSpec listHelper() {
    return collectionBuilder(
            Shape.LIST,
            "Copies an identity-copied list so the two sides never share it: an unmodifiable list"
                + " in the same order, null elements included; a null list copies to null.\n")
        .addStatement(
            "return values == null ? null : $T.unmodifiableList(new $T<>(values))",
            COLLECTIONS,
            ARRAY_LIST)
        .build();
  }

  private static MethodSpec nestedListHelper() {
    return collectionBuilder(
            Shape.LIST,
            "Copies an identity-copied list of containers so the two sides never share it: an"
                + " unmodifiable list in the same order, each present element copied in turn and"
                + " null elements included; a null list copies to null.\n")
        .addParameter(elementCopy(E), "each")
        .addCode(nullToNull("values"))
        .addCode(listCopy())
        .addStatement("return $T.unmodifiableList(copy)", COLLECTIONS)
        .build();
  }

  private static MethodSpec setHelper() {
    return collectionBuilder(
            Shape.SET,
            "Copies an identity-copied set so the two sides never share it: an unmodifiable set in"
                + " the same iteration order, a null element included; a null set copies to"
                + " null.\n")
        .addStatement(
            "return values == null ? null : $T.unmodifiableSet(new $T<>(values))",
            COLLECTIONS,
            LINKED_HASH_SET)
        .build();
  }

  private static MethodSpec nestedSetHelper() {
    return collectionBuilder(
            Shape.SET,
            "Copies an identity-copied set of containers so the two sides never share it: an"
                + " unmodifiable set in the same iteration order, each present element copied in"
                + " turn and a null element included; a null set copies to null.\n")
        .addParameter(elementCopy(E), "each")
        .addCode(nullToNull("values"))
        .addCode(setCopy())
        .addStatement("return $T.unmodifiableSet(copy)", COLLECTIONS)
        .build();
  }

  private static MethodSpec collectionHelper() {
    return collectionBuilder(
            Shape.COLLECTION,
            "Copies an identity-copied collection so the two sides never share it: a set as an"
                + " unmodifiable set in the same iteration order, anything else as an unmodifiable"
                + " list in the same order, null elements included; a null collection copies to"
                + " null.\n")
        .addCode(nullToNull("values"))
        .addStatement(
            "return values instanceof $T<?> ? $T.unmodifiableSet(new $T<>(values))"
                + " : $T.unmodifiableList(new $T<>(values))",
            SET,
            COLLECTIONS,
            LINKED_HASH_SET,
            COLLECTIONS,
            ARRAY_LIST)
        .build();
  }

  private static MethodSpec nestedCollectionHelper() {
    return collectionBuilder(
            Shape.COLLECTION,
            "Copies an identity-copied collection of containers so the two sides never share it:"
                + " a set as an unmodifiable set in the same iteration order, anything else as an"
                + " unmodifiable list in the same order, each present element copied in turn and"
                + " null elements included; a null collection copies to null.\n")
        .addParameter(elementCopy(E), "each")
        .addCode(nullToNull("values"))
        .beginControlFlow("if (values instanceof $T<?>)", SET)
        .addCode(setCopy())
        .addStatement("return $T.unmodifiableSet(copy)", COLLECTIONS)
        .endControlFlow()
        .addCode(listCopy())
        .addStatement("return $T.unmodifiableList(copy)", COLLECTIONS)
        .build();
  }

  private static MethodSpec mapHelper() {
    TypeVariableName k = TypeVariableName.get("K");
    TypeVariableName v = TypeVariableName.get("V");
    return helperBuilder(
            ParameterizedTypeName.get(Shape.MAP.container, k, v),
            "Copies an identity-copied map so the two sides never share it: an unmodifiable map"
                + " in the same entry order, null values included; a null map copies to null.\n",
            k,
            v)
        .addParameter(
            ParameterizedTypeName.get(
                Shape.MAP.container, WildcardTypeName.subtypeOf(k), WildcardTypeName.subtypeOf(v)),
            "values")
        .addCode(nullToNull("values"))
        .addStatement("return $T.unmodifiableMap(new $T<>(values))", COLLECTIONS, LINKED_HASH_MAP)
        .build();
  }

  private static MethodSpec nestedMapHelper() {
    TypeVariableName k = TypeVariableName.get("K");
    TypeVariableName v = TypeVariableName.get("V");
    return helperBuilder(
            ParameterizedTypeName.get(Shape.MAP.container, k, v),
            "Copies an identity-copied map of containers so the two sides never share it: an"
                + " unmodifiable map in the same entry order, each present value copied in turn and"
                + " null values included; a null map copies to null.\n",
            k,
            v)
        .addParameter(
            ParameterizedTypeName.get(
                Shape.MAP.container, WildcardTypeName.subtypeOf(k), WildcardTypeName.subtypeOf(v)),
            "values")
        .addParameter(elementCopy(v), "each")
        .addCode(nullToNull("values"))
        .addStatement(
            "$T copy = $T.newLinkedHashMap(values.size())",
            ParameterizedTypeName.get(Shape.MAP.container, k, v),
            LINKED_HASH_MAP)
        .beginControlFlow(
            "for ($T entry : values.entrySet())",
            ParameterizedTypeName.get(
                Shape.MAP.container.nestedClass("Entry"),
                WildcardTypeName.subtypeOf(k),
                WildcardTypeName.subtypeOf(v)))
        .addStatement("$T value = entry.getValue()", v)
        .addStatement("copy.put(entry.getKey(), value == null ? null : each.apply(value))")
        .endControlFlow()
        .addStatement("return $T.unmodifiableMap(copy)", COLLECTIONS)
        .build();
  }

  private static MethodSpec arrayHelper() {
    TypeVariableName e = TypeVariableName.get("E");
    return helperBuilder(
            ArrayTypeName.of(e),
            "Copies an identity-copied array so the two sides never share it: a clone, null"
                + " elements included; a null array copies to null.\n",
            e)
        .addParameter(ArrayTypeName.of(e), "values")
        .addStatement("return values == null ? null : values.clone()")
        .build();
  }

  private static MethodSpec nestedArrayHelper() {
    TypeVariableName e = TypeVariableName.get("E");
    return helperBuilder(
            ArrayTypeName.of(e),
            "Copies an identity-copied array of containers so the two sides never share it: a"
                + " clone, each present element copied in turn and null elements included; a null"
                + " array copies to null.\n",
            e)
        .addParameter(ArrayTypeName.of(e), "values")
        .addParameter(elementCopy(e), "each")
        .addCode(nullToNull("values"))
        .addStatement("$T copy = values.clone()", ArrayTypeName.of(e))
        .beginControlFlow("for (int i = 0; i < copy.length; i++)")
        .beginControlFlow("if (copy[i] != null)")
        .addStatement("copy[i] = each.apply(copy[i])")
        .endControlFlow()
        .endControlFlow()
        .addStatement("return copy")
        .build();
  }

  private static MethodSpec primitiveArrayHelper(TypeName component) {
    return helperBuilder(
            ArrayTypeName.of(component),
            "Copies an identity-copied array so the two sides never share it: a clone; a null"
                + " array copies to null.\n")
        .addParameter(ArrayTypeName.of(component), "values")
        .addStatement("return values == null ? null : values.clone()")
        .build();
  }

  private static MethodSpec optionalHelper() {
    TypeVariableName e = TypeVariableName.get("E");
    return helperBuilder(
            ParameterizedTypeName.get(Shape.OPTIONAL.container, e),
            "Copies an identity-copied Optional of a container so the two sides never share the"
                + " container: the present value's copy; a null Optional copies to null.\n",
            e)
        .addParameter(
            ParameterizedTypeName.get(Shape.OPTIONAL.container, WildcardTypeName.subtypeOf(e)),
            "value")
        .addParameter(elementCopy(e), "each")
        .addStatement("return value == null ? null : value.map(each)")
        .build();
  }
}
