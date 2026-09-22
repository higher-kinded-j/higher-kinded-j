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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * The null scan an identity-copied value carries: the located-null doctrine taken down every
 * container level its declared type names, so that a {@code null} anywhere inside reaches the
 * domain as a located invalid rather than as {@code Valid}. The mapping tiers and the fallible
 * merge share it, so the two processors cannot answer a null element differently.
 *
 * <p>A level is any {@code Collection}, a {@code Map}'s values, a reference array, or an {@code
 * Optional}'s value, however the type declares it: a subtype ({@code ArrayList}, {@code
 * LinkedHashMap}), a supertype ({@code Collection}), a raw container, a wildcard argument, or a
 * type variable bounded by one. {@code shape} is the outermost level, {@code inner} the scan each
 * of its elements (or values) takes in turn, null where an element is not a container, and {@code
 * element} the type those elements have, which the lambda carrying {@code inner} takes its
 * parameter as. Each level locates a failure the way its container identifies an element: a list,
 * queue or array by position, a map by key, a set by the element's rendering, and an {@code
 * Optional} at the value itself. A null element of a set is the unlocated {@code must not contain a
 * null element}, as {@code ValidatedParse.parseAll} reports it. Map keys are structural: a null key
 * stays the caller's {@code NullPointerException}, and a key is never scanned inside.
 *
 * <p>An identity leg copies, it never rebuilds, so every helper returns its argument unchanged
 * under the argument's own type ({@code <C extends Collection<?>>}). That is what lets the leg keep
 * whatever the component declares: a subtype stays a subtype, a raw container stays raw without an
 * unchecked conversion, and a wildcard argument is inferred into the leg rather than captured
 * against it. An element's scan is a lambda, so the nested forms bound their element type from
 * above ({@code Collection<? extends E>}), which leaves a wildcard-argument container free to
 * capture once, at the call.
 *
 * <p>An {@code Optional} is a level only when its value is itself a container: an {@code
 * Optional<String>} cannot hold a {@code null}, so it needs no scan, and an {@code OPTIONAL} scan
 * always has an {@code inner} one. Other types are not looked inside, not even ones that hold
 * elements: an {@code Iterable}, a {@code Stream}, or the library's own {@code Maybe} and {@code
 * NonEmptyList}.
 *
 * <p>The walk always ends. A container that passes its own type argument through as its element
 * ({@code List<E>}, {@code ArrayList<E>}, {@code HashMap<K, V>}) hands on part of the type it was
 * given, so each step is smaller than the last. One that fixes its element in its own declaration
 * ({@code class Node extends ArrayList<Node>}) can lead back to itself, directly or through a type
 * that grows at every step ({@code Grow<T> extends ArrayList<Grow<List<T>>>}), so such a class is
 * followed once on the walk, and where it recurs its elements are checked for null but not scanned
 * inside. A type variable is followed once for the same reason ({@code T extends List<T>}).
 */
record NullScan(NullScan.Shape shape, NullScan inner, TypeMirror element) {

  private static final ClassName VALIDATED =
      ClassName.get("org.higherkindedj.hkt.validated", "Validated");
  private static final ClassName FIELD_ERROR =
      ClassName.get("org.higherkindedj.hkt.validated", "FieldError");
  private static final ClassName NEL =
      ClassName.get("org.higherkindedj.hkt.nonemptylist", "NonEmptyList");
  private static final ClassName FUNCTION = ClassName.get("java.util.function", "Function");
  private static final ClassName OBJECTS = ClassName.get("java.util", "Objects");
  private static final ClassName COLLECTION = ClassName.get("java.util", "Collection");
  private static final ClassName SET = ClassName.get("java.util", "Set");
  private static final ClassName MAP = ClassName.get("java.util", "Map");
  private static final ClassName OPTIONAL = ClassName.get("java.util", "Optional");
  private static final TypeName FAILURES = ParameterizedTypeName.get(NEL, FIELD_ERROR);
  private static final TypeName ANY = WildcardTypeName.subtypeOf(Object.class);

  /**
   * One container level, the helper that scans it, and, for a declared container, the type it is
   * recognised by and which of that type's arguments its elements have.
   */
  enum Shape {
    /** Any {@code Collection}: by position, or, in a set, by the element's rendering. */
    COLLECTION("hkj$allPresent", "java.util.Collection", 0),
    /** A reference array: by index. */
    ARRAY("hkj$allPresent", null, 0),
    /** A {@code Map}'s values: by key. */
    MAP("hkj$valuesPresent", "java.util.Map", 1),
    /** An {@code Optional} whose value is a container: at the value itself. */
    OPTIONAL("hkj$presentWithin", "java.util.Optional", 0);

    private final String helper;
    private final String container;
    private final int argument;

    Shape(String helper, String container, int argument) {
      this.helper = helper;
      this.container = container;
      this.argument = argument;
    }
  }

  /** The declared containers, in the order a type is tried against them. */
  private static final List<Shape> DECLARED = List.of(Shape.COLLECTION, Shape.MAP, Shape.OPTIONAL);

  /**
   * The scan a value of this type carries, or null when there is nothing inside it to scan.
   *
   * @param type the declared type of the identity-copied value
   * @param types the round's type utilities
   * @param elements the round's element utilities
   * @return the scan, or null
   */
  static NullScan of(TypeMirror type, Types types, Elements elements) {
    return of(type, types, elements, new HashSet<>());
  }

  /**
   * Kind tests, not {@code instanceof}: javac's intersection type implements {@code DeclaredType}.
   * A type variable is read through its bound, the first of several that is a container deciding. A
   * declared type is captured first, so an argument written as a wildcard ({@code Bucket<?>}) is
   * read through the bound its capture has, which takes the type parameter's own bound into account
   * (JLS 5.1.10).
   */
  private static NullScan of(
      TypeMirror type, Types types, Elements elements, Set<Element> followed) {
    if (type == null) {
      return null;
    }
    return switch (type.getKind()) {
      case ARRAY -> {
        TypeMirror component = ((ArrayType) type).getComponentType();
        // A primitive array has no element that could be null.
        yield component.getKind().isPrimitive()
            ? null
            : level(Shape.ARRAY, component, true, types, elements, followed);
      }
      case TYPEVAR ->
          followed.add(((TypeVariable) type).asElement())
              ? of(((TypeVariable) type).getUpperBound(), types, elements, followed)
              : null;
      case INTERSECTION ->
          ((IntersectionType) type)
              .getBounds().stream()
                  .map(bound -> of(bound, types, elements, followed))
                  .filter(Objects::nonNull)
                  .findFirst()
                  .orElse(null);
      case DECLARED -> declared((DeclaredType) type, types, elements, followed);
      default -> null;
    };
  }

  private static NullScan declared(
      DeclaredType type, Types types, Elements elements, Set<Element> followed) {
    DeclaredType captured = (DeclaredType) types.capture(type);
    TypeElement declaration = (TypeElement) captured.asElement();
    for (Shape shape : DECLARED) {
      DeclaredType view = view(captured, shape, types, elements);
      if (view != null) {
        boolean follow =
            passesThrough(declaration, shape, types, elements) || followed.add(declaration);
        NullScan scan = level(shape, argument(view, shape), follow, types, elements, followed);
        return shape == Shape.OPTIONAL && scan.inner() == null ? null : scan;
      }
    }
    return null;
  }

  /** One level over elements of the given type, scanning them in turn where {@code follow}. */
  private static NullScan level(
      Shape shape,
      TypeMirror element,
      boolean follow,
      Types types,
      Elements elements,
      Set<Element> followed) {
    return new NullScan(shape, follow ? of(element, types, elements, followed) : null, element);
  }

  /**
   * Whether a container class hands one of its own type arguments on as its element, as {@code
   * ArrayList<E>} does, rather than fixing the element in its declaration. A type variable there
   * belongs to the class or to one enclosing it, and either is bound in the type as written.
   */
  private static boolean passesThrough(
      TypeElement declaration, Shape shape, Types types, Elements elements) {
    return argument(view((DeclaredType) declaration.asType(), shape, types, elements), shape)
        instanceof TypeVariable;
  }

  /** The type as the given container, under the arguments it is reached by, or null. */
  private static DeclaredType view(DeclaredType type, Shape shape, Types types, Elements elements) {
    return (DeclaredType)
        ProcessorUtils.supertypeOf(types, type, elements.getTypeElement(shape.container));
  }

  /**
   * The element types this scan's lambdas take their parameters as, level by level: javac infers
   * them, so a raw type among them is a warning the Impl member holding the scan answers for.
   */
  Stream<TypeMirror> inferred() {
    return inner == null ? Stream.empty() : Stream.concat(Stream.of(element), inner.inferred());
  }

  /** A container view's element argument; null for a raw container, which names none. */
  private static TypeMirror argument(DeclaredType view, Shape shape) {
    List<? extends TypeMirror> arguments = view.getTypeArguments();
    return arguments.isEmpty() ? null : arguments.get(shape.argument);
  }

  /**
   * The scan over {@code value}: this level's helper, with the next level's scan riding along as a
   * lambda, its parameter clear of every name in {@code taken}.
   */
  CodeBlock on(CodeBlock value, Set<String> taken) {
    return on(value, taken, 0);
  }

  private CodeBlock on(CodeBlock value, Set<String> taken, int depth) {
    return inner == null
        ? CodeBlock.of("$L($L)", shape.helper, value)
        : CodeBlock.of("$L($L, $L)", shape.helper, value, inner.asLambda(taken, depth + 1));
  }

  /** This scan as a lambda over a fresh parameter, clear of every name in {@code taken}. */
  CodeBlock asLambda(Set<String> taken) {
    return asLambda(taken, 1);
  }

  /** The parameter is named for its depth ({@code e}, then {@code e2}, {@code e3}). */
  private CodeBlock asLambda(Set<String> taken, int depth) {
    Set<String> scope = new HashSet<>(taken);
    String element = ProcessorUtils.freeName(depth == 1 ? "e" : "e" + depth, scope);
    return CodeBlock.of("$L -> $L", element, on(CodeBlock.of("$L", element), scope, depth));
  }

  /**
   * This scan as a parser function: a method reference to the helper when the scan is one level
   * deep, otherwise the lambda that carries the levels below it.
   */
  CodeBlock asFunction(ClassName owner, Set<String> taken) {
    return inner == null ? CodeBlock.of("$T::$L", owner, shape.helper) : asLambda(taken);
  }

  /** Each level's helper, as the scan calls it: the one-level form, or the one taking a lambda. */
  private record Form(Shape shape, boolean nested) {}

  private Stream<Form> forms() {
    Form form = new Form(shape, inner != null);
    return inner == null ? Stream.of(form) : Stream.concat(Stream.of(form), inner.forms());
  }

  /**
   * The helpers the given scans call, each once, in one fixed order so that every Impl declares
   * them alike.
   */
  static List<MethodSpec> helpers(Stream<NullScan> scans) {
    Set<Form> used = scans.flatMap(NullScan::forms).collect(Collectors.toSet());
    return Stream.of(Shape.values())
        .flatMap(shape -> Stream.of(new Form(shape, false), new Form(shape, true)))
        .filter(used::contains)
        .map(NullScan::helper)
        .toList();
  }

  private static MethodSpec helper(Form form) {
    return switch (form.shape()) {
      case COLLECTION -> form.nested() ? nestedCollectionHelper() : collectionHelper();
      case ARRAY -> form.nested() ? nestedArrayHelper() : arrayHelper();
      case MAP -> form.nested() ? nestedMapHelper() : mapHelper();
      // An Optional is a level only when its value is one, so it only ever takes the lambda.
      case OPTIONAL -> optionalHelper();
    };
  }

  // The helper bodies. Each is total over a null argument ("must not be null", labelled by the
  // caller) and accumulates its failures in encounter order, as ValidatedParse's bulk forms do.

  private static MethodSpec.Builder helperBuilder(
      String name, TypeName returned, String javadoc, TypeVariableName... variables) {
    return MethodSpec.methodBuilder(name)
        .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
        .addTypeVariables(List.of(variables))
        .returns(ParameterizedTypeName.get(VALIDATED, FAILURES, returned))
        .addJavadoc(javadoc);
  }

  /** The {@code Function} a nested form takes each element's own scan as. */
  private static TypeName elementScan(TypeName element) {
    return ParameterizedTypeName.get(
        FUNCTION,
        WildcardTypeName.supertypeOf(element),
        WildcardTypeName.subtypeOf(ParameterizedTypeName.get(VALIDATED, FAILURES, ANY)));
  }

  private static CodeBlock nullGuard(String argument) {
    return CodeBlock.builder()
        .beginControlFlow("if ($L == null)", argument)
        .addStatement("return $T.invalidNel($T.of($S))", VALIDATED, FIELD_ERROR, "must not be null")
        .endControlFlow()
        .build();
  }

  /** A null element's failure: unlocated in a set, located at {@code index} anywhere else. */
  private static CodeBlock nullElement(String index) {
    return CodeBlock.of(
        "$T.of(set ? $T.of($S) : $T.of($S).at($L))",
        NEL,
        FIELD_ERROR,
        "must not contain a null element",
        FIELD_ERROR,
        "must not be null",
        index);
  }

  private static CodeBlock nullAt(String location) {
    return CodeBlock.of("$T.of($T.of($S).at($L))", NEL, FIELD_ERROR, "must not be null", location);
  }

  /**
   * The failures a present element's own scan reports, located under it; the statement {@code
   * continue}s past an element whose scan passes. The location is rendered only for an element that
   * fails, and once however many failures it holds, as the bulk forms render theirs.
   */
  private static CodeBlock scannedElement(String element, String location) {
    return CodeBlock.builder()
        .addStatement("$T<$T, ?> scanned = each.apply($L)", VALIDATED, FAILURES, element)
        .beginControlFlow("if (scanned.isValid())")
        .addStatement("continue")
        .endControlFlow()
        .addStatement("String at = $L", location)
        .addStatement("located = scanned.getError().map(error -> error.at(at))")
        .build();
  }

  private static CodeBlock accumulate() {
    return CodeBlock.of(
        "failures = failures == null ? located : $T.<$T>semigroup().combine(failures, located)",
        NEL,
        FIELD_ERROR);
  }

  private static CodeBlock returnScanned(String argument) {
    return CodeBlock.of(
        "return failures == null ? $T.valid($L) : $T.invalid(failures)",
        VALIDATED,
        argument,
        VALIDATED);
  }

  private static MethodSpec collectionHelper() {
    TypeVariableName c = TypeVariableName.get("C", ParameterizedTypeName.get(COLLECTION, ANY));
    return helperBuilder(
            Shape.COLLECTION.helper,
            c,
            "Guards an identity-copied collection: a null element is a located invalid at its"
                + " position, accumulating, or, in a set, which has no positions, the unlocated"
                + " {@code must not contain a null element}.\n",
            c)
        .addParameter(c, "values")
        .addCode(nullGuard("values"))
        .addStatement("boolean set = values instanceof $T", SET)
        .addStatement("$T failures = null", FAILURES)
        // iterate rather than index: a collection need not be a list, and get(i) is quadratic on
        // a LinkedList
        .addStatement("int i = 0")
        .beginControlFlow("for ($T element : values)", Object.class)
        .beginControlFlow("if (element == null)")
        .addStatement("$T located = $L", FAILURES, nullElement("String.valueOf(i)"))
        .addStatement("$L", accumulate())
        .endControlFlow()
        .addStatement("i++")
        .endControlFlow()
        .addStatement("$L", returnScanned("values"))
        .build();
  }

  private static MethodSpec nestedCollectionHelper() {
    TypeVariableName e = TypeVariableName.get("E");
    TypeVariableName c =
        TypeVariableName.get(
            "C", ParameterizedTypeName.get(COLLECTION, WildcardTypeName.subtypeOf(e)));
    return helperBuilder(
            Shape.COLLECTION.helper,
            c,
            "Guards an identity-copied collection of containers: a null element is a located"
                + " invalid at its position, or, in a set, the unlocated {@code must not contain a"
                + " null element}, and a present element passes its own scan, its failures located"
                + " under its position, or, in a set, under its rendering.\n",
            c,
            e)
        .addParameter(c, "values")
        .addParameter(elementScan(e), "each")
        .addCode(nullGuard("values"))
        .addStatement("boolean set = values instanceof $T", SET)
        .addStatement("$T failures = null", FAILURES)
        .addStatement("int i = 0")
        .beginControlFlow("for ($T element : values)", e)
        .addStatement("int index = i++")
        .addStatement("$T located", FAILURES)
        .beginControlFlow("if (element == null)")
        .addStatement("located = $L", nullElement("String.valueOf(index)"))
        .nextControlFlow("else")
        .addCode(scannedElement("element", "set ? element.toString() : String.valueOf(index)"))
        .endControlFlow()
        .addStatement("$L", accumulate())
        .endControlFlow()
        .addStatement("$L", returnScanned("values"))
        .build();
  }

  private static MethodSpec arrayHelper() {
    TypeVariableName e = TypeVariableName.get("E");
    return helperBuilder(
            Shape.ARRAY.helper,
            ArrayTypeName.of(e),
            "Guards an identity-copied array: a null element is a located invalid at its index,"
                + " accumulating.\n",
            e)
        .addParameter(ArrayTypeName.of(e), "values")
        .addCode(nullGuard("values"))
        .addStatement("$T failures = null", FAILURES)
        .beginControlFlow("for (int i = 0; i < values.length; i++)")
        .beginControlFlow("if (values[i] == null)")
        .addStatement("$T located = $L", FAILURES, nullAt("String.valueOf(i)"))
        .addStatement("$L", accumulate())
        .endControlFlow()
        .endControlFlow()
        .addStatement("$L", returnScanned("values"))
        .build();
  }

  private static MethodSpec nestedArrayHelper() {
    TypeVariableName e = TypeVariableName.get("E");
    return helperBuilder(
            Shape.ARRAY.helper,
            ArrayTypeName.of(e),
            "Guards an identity-copied array of containers: a null element is a located invalid at"
                + " its index, and a present element passes its own scan, located under its"
                + " index.\n",
            e)
        .addParameter(ArrayTypeName.of(e), "values")
        .addParameter(elementScan(e), "each")
        .addCode(nullGuard("values"))
        .addStatement("$T failures = null", FAILURES)
        .beginControlFlow("for (int i = 0; i < values.length; i++)")
        .addStatement("$T located", FAILURES)
        .beginControlFlow("if (values[i] == null)")
        .addStatement("located = $L", nullAt("String.valueOf(i)"))
        .nextControlFlow("else")
        .addCode(scannedElement("values[i]", "String.valueOf(i)"))
        .endControlFlow()
        .addStatement("$L", accumulate())
        .endControlFlow()
        .addStatement("$L", returnScanned("values"))
        .build();
  }

  private static MethodSpec mapHelper() {
    TypeVariableName c = TypeVariableName.get("C", ParameterizedTypeName.get(MAP, ANY, ANY));
    return helperBuilder(
            Shape.MAP.helper,
            c,
            "Guards an identity-copied map: a null value is a located invalid under its key,"
                + " accumulating; a null key is the caller's NullPointerException.\n",
            c)
        .addParameter(c, "values")
        .addCode(nullGuard("values"))
        .addStatement("$T failures = null", FAILURES)
        .beginControlFlow(
            "for ($T entry : values.entrySet())",
            ParameterizedTypeName.get(MAP.nestedClass("Entry"), ANY, ANY))
        .addStatement("$T.requireNonNull(entry.getKey(), $S)", OBJECTS, "map keys must not be null")
        .beginControlFlow("if (entry.getValue() == null)")
        .addStatement("$T located = $L", FAILURES, nullAt("String.valueOf(entry.getKey())"))
        .addStatement("$L", accumulate())
        .endControlFlow()
        .endControlFlow()
        .addStatement("$L", returnScanned("values"))
        .build();
  }

  private static MethodSpec nestedMapHelper() {
    TypeVariableName v = TypeVariableName.get("V");
    TypeVariableName c =
        TypeVariableName.get(
            "C", ParameterizedTypeName.get(MAP, ANY, WildcardTypeName.subtypeOf(v)));
    return helperBuilder(
            Shape.MAP.helper,
            c,
            "Guards an identity-copied map of containers: a null value is a located invalid under"
                + " its key, and a present value passes its own scan, located under its key; a"
                + " null key is the caller's NullPointerException.\n",
            c,
            v)
        .addParameter(c, "values")
        .addParameter(elementScan(v), "each")
        .addCode(nullGuard("values"))
        .addStatement("$T failures = null", FAILURES)
        .beginControlFlow(
            "for ($T entry : values.entrySet())",
            ParameterizedTypeName.get(MAP.nestedClass("Entry"), ANY, WildcardTypeName.subtypeOf(v)))
        .addStatement(
            "Object key = $T.requireNonNull(entry.getKey(), $S)",
            OBJECTS,
            "map keys must not be null")
        .addStatement("$T value = entry.getValue()", v)
        .addStatement("$T located", FAILURES)
        .beginControlFlow("if (value == null)")
        .addStatement("located = $L", nullAt("String.valueOf(key)"))
        .nextControlFlow("else")
        .addCode(scannedElement("value", "String.valueOf(key)"))
        .endControlFlow()
        .addStatement("$L", accumulate())
        .endControlFlow()
        .addStatement("$L", returnScanned("values"))
        .build();
  }

  private static MethodSpec optionalHelper() {
    TypeVariableName e = TypeVariableName.get("E");
    TypeVariableName o =
        TypeVariableName.get(
            "O", ParameterizedTypeName.get(OPTIONAL, WildcardTypeName.subtypeOf(e)));
    return helperBuilder(
            Shape.OPTIONAL.helper,
            o,
            "Guards an identity-copied Optional of a container: an empty Optional is valid, and a"
                + " present value passes its own scan, located at the Optional itself, which"
                + " holds only the one value.\n",
            o,
            e)
        .addParameter(o, "value")
        .addParameter(elementScan(e), "each")
        .addCode(nullGuard("value"))
        .beginControlFlow("if (value.isEmpty())")
        .addStatement("return $T.valid(value)", VALIDATED)
        .endControlFlow()
        .addStatement("$T<$T, ?> scanned = each.apply(value.get())", VALIDATED, FAILURES)
        .addStatement(
            "return scanned.isValid() ? $T.valid(value) : $T.invalid(scanned.getError())",
            VALIDATED,
            VALIDATED)
        .build();
  }
}
