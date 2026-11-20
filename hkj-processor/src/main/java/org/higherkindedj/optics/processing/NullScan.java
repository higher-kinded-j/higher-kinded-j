// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeVariableName;
import com.palantir.javapoet.WildcardTypeName;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
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
 * null element}, as {@code ValidatedParse.parseAll} reports it, and a null key of a map is the
 * unlocated {@code must not contain a null key}, as {@code ValidatedParse.parseValues} reports it,
 * its value unscanned.
 *
 * <p>A map's key that is itself a container is scanned too, as its value is: {@code keys} is the
 * scan each key takes, null where the key holds no container, and {@code key} the type the keys
 * have. A failure inside a key is located under the key's rendering, as {@code
 * ValidatedParse.parseKeys} locates a key that fails its leaf, and an entry's key is scanned before
 * its value. A map whose values a leaf or nested spec converts passes its keys through as they are,
 * so its leg scans them around the bulk form ({@link #aroundParse}).
 *
 * <p>A scan only checks, it never rebuilds: it runs over the copy the leg hands over (see {@link
 * ContainerCopy}), so every helper returns its argument unchanged under the argument's own type
 * ({@code <C extends Collection<?>>}). That is what lets the leg keep whatever the component
 * declares: a subtype stays a subtype, a raw container stays raw without an unchecked conversion,
 * and a wildcard argument is inferred into the leg rather than captured against it. An element's
 * scan is a lambda, so the nested forms bound their element type from above ({@code Collection<?
 * extends E>}), which leaves a wildcard-argument container free to capture once, at the call. The
 * one helper handed a parse's result is the one a map's passed-through keys take beside the values
 * a leaf or nested spec converts ({@link #aroundParse}): it is given what the bulk form answered,
 * hands that back, and scans the keys of the map it holds.
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
record NullScan(
    NullScan.Shape shape, NullScan inner, TypeMirror element, NullScan keys, TypeMirror key) {

  private static final ClassName VALIDATED =
      ClassName.get("org.higherkindedj.hkt.validated", "Validated");
  private static final ClassName FIELD_ERROR =
      ClassName.get("org.higherkindedj.hkt.validated", "FieldError");
  private static final ClassName NEL =
      ClassName.get("org.higherkindedj.hkt.nonemptylist", "NonEmptyList");
  private static final ClassName FUNCTION = ClassName.get("java.util.function", "Function");
  private static final ClassName COLLECTION = ClassName.get("java.util", "Collection");
  private static final ClassName LIST = ClassName.get("java.util", "List");
  private static final ClassName ARRAY_LIST = ClassName.get("java.util", "ArrayList");
  private static final ClassName SET = ClassName.get("java.util", "Set");
  private static final ClassName MAP = ClassName.get("java.util", "Map");
  private static final ClassName OPTIONAL = ClassName.get("java.util", "Optional");
  private static final TypeName FAILURES = ParameterizedTypeName.get(NEL, FIELD_ERROR);
  private static final TypeName ANY = WildcardTypeName.subtypeOf(Object.class);

  /** The type a collection's element can be named through by a bound that is no collection. */
  private static final String ITERABLE = "java.lang.Iterable";

  /** Where {@code Map<K, V>} takes its key among its type arguments. */
  private static final int MAP_KEY = 0;

  /** The helper a map level calls where its keys are scanned too, with or without its values. */
  private static final String KEYED_MAP_HELPER = "hkj$entriesPresent";

  /**
   * The helper a lifted map's keys are scanned through, around the bulk form ({@link
   * #aroundParse}).
   */
  private static final String KEYS_SCANNED_HELPER = "hkj$keysScanned";

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
        // A map's keys are walked before its values add this class, each walk taking the classes
        // above the map alone, so neither stops the other.
        NullScan keys =
            shape == Shape.MAP ? keys(declaration, view, types, elements, followed) : null;
        boolean follow =
            passesThrough(declaration, shape, shape.argument, types, elements)
                || followed.add(declaration);
        NullScan scan = level(shape, argument(view, shape), follow, types, elements, followed);
        return shape == Shape.OPTIONAL && scan.inner() == null
            ? null
            : keys == null ? scan : scan.withKeys(keys, view.getTypeArguments().get(MAP_KEY));
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
    return new NullScan(
        shape, follow ? of(element, types, elements, followed) : null, element, null, null);
  }

  /**
   * The scan a map's keys take, or null where they hold no container, the map is raw, or its class
   * recurs through its own key, as {@code class Index extends HashMap<Index, String>} does below
   * its first level, so an {@code Index} is scanned one key deep. The keys are walked over their
   * own copy of {@code followed}, so the classes they follow do not count against the values' walk.
   */
  private static NullScan keys(
      TypeElement declaration,
      DeclaredType view,
      Types types,
      Elements elements,
      Set<Element> followed) {
    if (view.getTypeArguments().isEmpty()) {
      return null;
    }
    Set<Element> walked = new HashSet<>(followed);
    boolean follow =
        passesThrough(declaration, Shape.MAP, MAP_KEY, types, elements) || walked.add(declaration);
    return follow ? of(view.getTypeArguments().get(MAP_KEY), types, elements, walked) : null;
  }

  /**
   * This map level, its keys scanned by {@code keyScan}, the keys being of type {@code keyType}.
   */
  private NullScan withKeys(NullScan keyScan, TypeMirror keyType) {
    return new NullScan(shape, inner, element, keyScan, keyType);
  }

  /**
   * This map level read for its keys alone: what a map whose values a leaf or nested spec converts
   * hands on as it is. It is a reading for {@link NullnessReading}'s walk, which reads a level with
   * no element type as naming no values, and is never rendered as a scan: its helper would check
   * the values for null, which the bulk form already does.
   *
   * @return this level over its keys alone
   */
  NullScan keysAlone() {
    return new NullScan(shape, null, null, keys, key);
  }

  /**
   * This map level read with its keys left unread inside: what a map whose keys a {@code @MapKey}
   * leaf converts hands on as it is, which is its values. Like {@link #keysAlone}, it is a reading
   * for {@link NullnessReading}'s walk, never rendered. The walk still reads each key itself at the
   * map's level, which says nothing new there, since the key leaf's own check refuses a nullable
   * key first.
   *
   * @return this level over its values alone
   */
  NullScan valuesAlone() {
    return new NullScan(shape, inner, element, null, null);
  }

  /**
   * Whether a container class hands one of its own type arguments on as the argument at {@code
   * index} of its view as {@code shape}, as {@code ArrayList<E>} does its element, rather than
   * fixing it in its declaration. A type variable there belongs to the class or to one enclosing
   * it, and either is bound in the type as written.
   */
  private static boolean passesThrough(
      TypeElement declaration, Shape shape, int index, Types types, Elements elements) {
    return namedArgument(declaration, shape, index, types, elements) instanceof TypeVariable;
  }

  /**
   * The argument a container class passes its view as {@code shape} at {@code index}, in terms of
   * its own type parameters: {@code E} for {@code ArrayList<E>}'s element, {@code String} for a
   * class that fixes it, and null for a class that extends the container raw, or is no such
   * container at all, as a type variable's bound such as {@code Object} need not be. A bound may
   * name a collection's element as an {@code Iterable}'s, as {@code L extends Iterable<String>}
   * does, so a collection level reads one through that view where it has no other.
   */
  private static TypeMirror namedArgument(
      TypeElement declaration, Shape shape, int index, Types types, Elements elements) {
    DeclaredType type = (DeclaredType) declaration.asType();
    DeclaredType view = view(type, shape, types, elements);
    if (view == null && shape == Shape.COLLECTION) {
      view =
          (DeclaredType) ProcessorUtils.supertypeOf(types, type, elements.getTypeElement(ITERABLE));
    }
    return view == null || view.getTypeArguments().isEmpty()
        ? null
        : view.getTypeArguments().get(index);
  }

  /** The type as the given container, under the arguments it is reached by, or null. */
  private static DeclaredType view(DeclaredType type, Shape shape, Types types, Elements elements) {
    return (DeclaredType)
        ProcessorUtils.supertypeOf(types, type, elements.getTypeElement(shape.container));
  }

  /**
   * The type this level's elements are written as inside {@code written}, the type the level scans
   * as a declaration writes it. The scan reads {@link #element} through capture and supertype
   * views, which keep no type annotation, so a {@code TYPE_USE} annotation on an element type, as
   * in {@code List<@Nullable String>}, is read here instead. That is an array's component type, or
   * the element the container class names, with the type's own arguments put in for its type
   * variables, as {@code ArrayList<E>} names {@code E} and {@code ArrayList<List<E>>} names {@code
   * List<E>}, read through its bound where it is a wildcard. Where the class names no element, or
   * the type is used raw or written another way, such as a type variable, it is {@link #element},
   * which is null for a raw container. So is a wildcard with no upper bound, whose elements its
   * type does not describe.
   *
   * @param written the type this level scans, as a declaration writes it
   * @param types the round's type utilities
   * @param elements the round's element utilities
   * @return the type this level's elements are written as
   */
  TypeMirror writtenElement(TypeMirror written, Types types, Elements elements) {
    TypeMirror named = namedElement(written, types, elements);
    return named == null ? element : named;
  }

  /**
   * The type this level's elements are written as inside {@code written}, read as {@link
   * #writtenElement} reads it, but with no fallback: null where {@code written} names none, as a
   * type variable, a raw container, a wildcard with no upper bound, or a type that is no container
   * of this level's kind, such as a type variable's bound {@code Object} over an array, does not.
   *
   * @param written the type, as a declaration writes it
   * @param types the round's type utilities
   * @param elements the round's element utilities
   * @return the type this level's elements are written as, or null
   */
  TypeMirror namedElement(TypeMirror written, Types types, Elements elements) {
    return switch (written.getKind()) {
      case ARRAY -> ((ArrayType) written).getComponentType();
      case DECLARED ->
          shape == Shape.ARRAY
              ? null
              : writtenArgument((DeclaredType) written, shape.argument, types, elements);
      default -> null;
    };
  }

  /**
   * The type a map level's keys are written as inside {@code written}, read as {@link
   * #writtenElement} reads its values: the key the map class names, with the type's own arguments
   * put in for its type variables, read through its bound where it is a wildcard, so {@code
   * Map<@Nullable String, V>} and {@code HashMap<? extends @Nullable String, V>} both name
   * {@code @Nullable String}. Unlike {@link #writtenElement}, it falls back on no key type of the
   * scan's own: where this level is no map, the class names no key, or the type is used raw, is
   * written another way, such as a type variable, or has a wildcard with no upper bound, the result
   * is null, a key that says nothing either way, and whose levels are not read either.
   *
   * @param written the type this level scans, as a declaration writes it
   * @param types the round's type utilities
   * @param elements the round's element utilities
   * @return the type this level's keys are written as, or null
   */
  TypeMirror writtenKey(TypeMirror written, Types types, Elements elements) {
    return shape == Shape.MAP && written.getKind() == TypeKind.DECLARED
        ? writtenArgument((DeclaredType) written, MAP_KEY, types, elements)
        : null;
  }

  /**
   * The argument {@code written}'s class names at {@code index} of this level's container, with
   * {@code written}'s own arguments put in for the class's type variables, or null where it names
   * none or its wildcard has no upper bound.
   */
  private TypeMirror writtenArgument(
      DeclaredType written, int index, Types types, Elements elements) {
    TypeElement declaration = (TypeElement) written.asElement();
    TypeMirror named = namedArgument(declaration, shape, index, types, elements);
    List<? extends TypeMirror> arguments = written.getTypeArguments();
    // A class that extends its container raw names no argument, and a raw use binds no variable.
    if (named == null || arguments.size() != declaration.getTypeParameters().size()) {
      return null;
    }
    Map<Element, TypeMirror> bound = new HashMap<>();
    for (int i = 0; i < arguments.size(); i++) {
      bound.put(declaration.getTypeParameters().get(i), arguments.get(i));
    }
    return ProcessorUtils.resolveWildcard(substituted(named, bound, types));
  }

  /**
   * {@code type} with the arguments {@code bound} gives put in for its type variables, at every
   * depth. javac's own substitution keeps no type annotation, so the type is rebuilt here, which
   * keeps the arguments' own: {@code List<E>} under {@code E = @Nullable String} becomes {@code
   * List<@Nullable String>}. A type with no arguments, and a member type of another, which names
   * its enclosing type too, are kept as they are written.
   */
  private static TypeMirror substituted(
      TypeMirror type, Map<Element, TypeMirror> bound, Types types) {
    return switch (type.getKind()) {
      case TYPEVAR -> bound.getOrDefault(((TypeVariable) type).asElement(), type);
      case DECLARED -> {
        DeclaredType declared = (DeclaredType) type;
        yield declared.getTypeArguments().isEmpty()
                || declared.getEnclosingType().getKind() != TypeKind.NONE
            ? declared
            : types.getDeclaredType(
                (TypeElement) declared.asElement(),
                declared.getTypeArguments().stream()
                    .map(argument -> substituted(argument, bound, types))
                    .toArray(TypeMirror[]::new));
      }
      case ARRAY ->
          types.getArrayType(substituted(((ArrayType) type).getComponentType(), bound, types));
      case WILDCARD -> {
        WildcardType wildcard = (WildcardType) type;
        yield types.getWildcardType(
            substitutedBound(wildcard.getExtendsBound(), bound, types),
            substitutedBound(wildcard.getSuperBound(), bound, types));
      }
      default -> type;
    };
  }

  /** A wildcard's bound with {@code bound}'s arguments put in, or null where it has none. */
  private static TypeMirror substitutedBound(
      TypeMirror wildcardBound, Map<Element, TypeMirror> bound, Types types) {
    return wildcardBound == null ? null : substituted(wildcardBound, bound, types);
  }

  /**
   * The element types this scan's lambdas take their parameters as, level by level: javac infers
   * them, so a raw type among them is a warning the Impl member holding the scan answers for.
   */
  Stream<TypeMirror> inferred() {
    Stream<TypeMirror> values =
        inner == null ? Stream.empty() : Stream.concat(Stream.of(element), inner.inferred());
    return keys == null
        ? values
        : Stream.concat(Stream.concat(Stream.of(key), keys.inferred()), values);
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
    CodeBlock.Builder call = CodeBlock.builder().add("$L($L", form().helper(), value);
    // The two lambdas are siblings, so each may take the same name.
    if (keys != null) {
      call.add(", $L", keys.asLambda(taken, depth + 1));
    }
    if (inner != null) {
      call.add(", $L", inner.asLambda(taken, depth + 1));
    }
    return call.add(")").build();
  }

  /**
   * The parse of a map whose keys pass through as they are: {@code parsed}, the {@code parseValues}
   * of a leaf or a nested spec's parse over {@code value}, with this scan, the keys' own, run over
   * the keys the domain receives: the {@code hkj$keysScanned} helper ({@link #keysScannedHelper}).
   * The parse is the helper's first argument, so it runs before the scan.
   *
   * @param value the map, never null, read once more by the helper
   * @param parsed the bulk form's call over {@code value}
   * @param taken the names the lambda's parameter must stay clear of
   * @return the call
   */
  CodeBlock aroundParse(CodeBlock value, CodeBlock parsed, Set<String> taken) {
    return CodeBlock.of("$L($L, $L, $L)", KEYS_SCANNED_HELPER, parsed, value, asLambda(taken));
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
    return inner == null && keys == null
        ? CodeBlock.of("$T::$L", owner, shape.helper)
        : asLambda(taken);
  }

  /**
   * Each level's helper, as the scan calls it: the one-level form, or the one taking a lambda over
   * its elements ({@code nested}), and, for a map, the one taking a lambda over its keys too
   * ({@code keyed}).
   */
  private record Form(Shape shape, boolean nested, boolean keyed) {

    String helper() {
      return keyed ? KEYED_MAP_HELPER : shape.helper;
    }
  }

  private Form form() {
    return new Form(shape, inner != null, keys != null);
  }

  private Stream<Form> forms() {
    return Stream.concat(
        Stream.of(form()),
        Stream.of(keys, inner).filter(Objects::nonNull).flatMap(NullScan::forms));
  }

  /**
   * The helpers the given scans call, each once, in one fixed order so that every Impl declares
   * them alike.
   *
   * @param scans the scans an Impl's legs make
   * @param marks the nullness annotations the helpers are written with
   * @return the helper methods to declare
   */
  static List<MethodSpec> helpers(Stream<NullScan> scans, NullnessMarks marks) {
    Set<Form> used = scans.flatMap(NullScan::forms).collect(Collectors.toSet());
    return Stream.of(Shape.values())
        .flatMap(
            shape ->
                Stream.of(
                    new Form(shape, false, false),
                    new Form(shape, true, false),
                    new Form(shape, false, true),
                    new Form(shape, true, true)))
        .filter(used::contains)
        .map(form -> helper(form, marks))
        .toList();
  }

  private static MethodSpec helper(Form form, NullnessMarks marks) {
    return switch (form.shape()) {
      case COLLECTION -> form.nested() ? nestedCollectionHelper(marks) : collectionHelper(marks);
      case ARRAY -> form.nested() ? nestedArrayHelper(marks) : arrayHelper(marks);
      case MAP ->
          form.keyed()
              ? keyedMapHelper(form.nested(), marks)
              : form.nested() ? nestedMapHelper(marks) : mapHelper(marks);
      // An Optional is a level only when its value is one, so it only ever takes the lambda.
      case OPTIONAL -> optionalHelper(marks);
    };
  }

  // The helper bodies. Each is total over a null argument ("must not be null", labelled by the
  // caller), so it takes one declared @Nullable, and accumulates its failures in encounter order,
  // as ValidatedParse's bulk forms do. An element type may be nullable, since a null element is
  // what the scan reports.

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

  /**
   * A null key's failure: unlocated, since a null has no rendering to locate by, and a map holds at
   * most one, as a set's null element is.
   */
  private static CodeBlock nullKey() {
    return CodeBlock.of("$T.of($S)", FIELD_ERROR, "must not contain a null key");
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

  /**
   * The failures are collected in one list and built into a {@code NonEmptyList} once, on return,
   * as the bulk forms collect theirs: concatenating element by element would copy every earlier
   * failure at each step.
   */
  private static CodeBlock declareFailures() {
    return CodeBlock.of("$T<$T> failures = new $T<>()", LIST, FIELD_ERROR, ARRAY_LIST);
  }

  private static CodeBlock accumulate() {
    return CodeBlock.of("failures.addAll(located.toJavaList())");
  }

  private static CodeBlock returnScanned(String argument) {
    return CodeBlock.of(
        "return failures.isEmpty() ? $T.valid($L) : $T.invalid($T.of(failures.getFirst(),"
            + " failures.subList(1, failures.size())))",
        VALIDATED,
        argument,
        VALIDATED,
        NEL);
  }

  private static MethodSpec collectionHelper(NullnessMarks marks) {
    TypeVariableName c = TypeVariableName.get("C", ParameterizedTypeName.get(COLLECTION, ANY));
    return helperBuilder(
            Shape.COLLECTION.helper,
            c,
            "Guards an identity-copied collection: a null element is a located invalid at its"
                + " position, accumulating, or, in a set, which has no positions, the unlocated"
                + " {@code must not contain a null element}.\n",
            c)
        .addParameter(marks.nullable(c), "values")
        .addCode(nullGuard("values"))
        .addStatement("boolean set = values instanceof $T", SET)
        .addStatement("$L", declareFailures())
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

  private static MethodSpec nestedCollectionHelper(NullnessMarks marks) {
    TypeVariableName e = marks.nullableBounded("E");
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
        .addParameter(marks.nullable(c), "values")
        .addParameter(elementScan(e), "each")
        .addCode(nullGuard("values"))
        .addStatement("boolean set = values instanceof $T", SET)
        .addStatement("$L", declareFailures())
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

  private static MethodSpec arrayHelper(NullnessMarks marks) {
    TypeVariableName e = marks.nullableBounded("E");
    return helperBuilder(
            Shape.ARRAY.helper,
            ArrayTypeName.of(e),
            "Guards an identity-copied array: a null element is a located invalid at its index,"
                + " accumulating.\n",
            e)
        // Its elements are declared nullable, since a null element is what it reports, so an array
        // whose elements a declaration marks nullable passes inside a lambda, where a nullness
        // checker infers E without the mark.
        .addParameter(marks.nullable(ArrayTypeName.of(marks.nullable(e))), "values")
        .addCode(nullGuard("values"))
        .addStatement("$L", declareFailures())
        .beginControlFlow("for (int i = 0; i < values.length; i++)")
        .beginControlFlow("if (values[i] == null)")
        .addStatement("$T located = $L", FAILURES, nullAt("String.valueOf(i)"))
        .addStatement("$L", accumulate())
        .endControlFlow()
        .endControlFlow()
        .addStatement("$L", returnScanned("values"))
        .build();
  }

  private static MethodSpec nestedArrayHelper(NullnessMarks marks) {
    TypeVariableName e = marks.nullableBounded("E");
    return helperBuilder(
            Shape.ARRAY.helper,
            ArrayTypeName.of(e),
            "Guards an identity-copied array of containers: a null element is a located invalid at"
                + " its index, and a present element passes its own scan, located under its"
                + " index.\n",
            e)
        .addParameter(marks.nullable(ArrayTypeName.of(e)), "values")
        .addParameter(elementScan(e), "each")
        .addCode(nullGuard("values"))
        .addStatement("$L", declareFailures())
        .beginControlFlow("for (int i = 0; i < values.length; i++)")
        // Read once, so the null check covers the read the scan is handed.
        .addStatement("$T element = values[i]", e)
        .addStatement("$T located", FAILURES)
        .beginControlFlow("if (element == null)")
        .addStatement("located = $L", nullAt("String.valueOf(i)"))
        .nextControlFlow("else")
        .addCode(scannedElement("element", "String.valueOf(i)"))
        .endControlFlow()
        .addStatement("$L", accumulate())
        .endControlFlow()
        .addStatement("$L", returnScanned("values"))
        .build();
  }

  private static MethodSpec mapHelper(NullnessMarks marks) {
    TypeVariableName c = TypeVariableName.get("C", ParameterizedTypeName.get(MAP, ANY, ANY));
    return helperBuilder(
            Shape.MAP.helper,
            c,
            "Guards an identity-copied map: a null value is a located invalid under its key, and a"
                + " null key, which has nothing to locate by, the unlocated {@code must not contain a"
                + " null key}, accumulating.\n",
            c)
        .addParameter(marks.nullable(c), "values")
        .addCode(nullGuard("values"))
        .addStatement("$L", declareFailures())
        .beginControlFlow(
            "for ($T entry : values.entrySet())",
            ParameterizedTypeName.get(MAP.nestedClass("Entry"), ANY, ANY))
        .beginControlFlow("if (entry.getKey() == null)")
        .addStatement("failures.add($L)", nullKey())
        .nextControlFlow("else if (entry.getValue() == null)")
        .addStatement("$T located = $L", FAILURES, nullAt("String.valueOf(entry.getKey())"))
        .addStatement("$L", accumulate())
        .endControlFlow()
        .endControlFlow()
        .addStatement("$L", returnScanned("values"))
        .build();
  }

  private static MethodSpec nestedMapHelper(NullnessMarks marks) {
    TypeVariableName v = marks.nullableBounded("V");
    TypeVariableName c =
        TypeVariableName.get(
            "C", ParameterizedTypeName.get(MAP, ANY, WildcardTypeName.subtypeOf(v)));
    return helperBuilder(
            Shape.MAP.helper,
            c,
            "Guards an identity-copied map of containers: a null value is a located invalid under"
                + " its key, and a present value passes its own scan, located under its key; a null"
                + " key, which has nothing to locate by, is the unlocated {@code must not contain a"
                + " null key}, its value unscanned.\n",
            c,
            v)
        .addParameter(marks.nullable(c), "values")
        .addParameter(elementScan(v), "each")
        .addCode(nullGuard("values"))
        .addStatement("$L", declareFailures())
        .beginControlFlow(
            "for ($T entry : values.entrySet())",
            ParameterizedTypeName.get(MAP.nestedClass("Entry"), ANY, WildcardTypeName.subtypeOf(v)))
        .addStatement("$T key = entry.getKey()", marks.nullable(ClassName.OBJECT))
        .beginControlFlow("if (key == null)")
        .addStatement("failures.add($L)", nullKey())
        .addStatement("continue")
        .endControlFlow()
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

  /**
   * A map's present key passing its own scan: its failures, if any, located under its rendering and
   * added to the accumulated ones. Unlike {@link #scannedElement}, it never {@code continue}s,
   * since the entry's value is still to be looked at.
   */
  private static CodeBlock scannedKey(String key) {
    return CodeBlock.builder()
        .addStatement("$T<$T, ?> scannedKey = keys.apply($L)", VALIDATED, FAILURES, key)
        .beginControlFlow("if (!scannedKey.isValid())")
        .addStatement("String keyAt = String.valueOf($L)", key)
        .addStatement(
            "failures.addAll(scannedKey.getError().map(error -> error.at(keyAt)).toJavaList())")
        .endControlFlow()
        .build();
  }

  /**
   * The map helper whose keys hold containers: a null key, which has nothing to locate by, is the
   * unlocated {@code must not contain a null key}, its value unlooked at; a present key passes its
   * own scan, located under its rendering; and then the value is looked at, as {@link #mapHelper}
   * or, {@code nested}, as {@link #nestedMapHelper} looks at it.
   */
  private static MethodSpec keyedMapHelper(boolean nested, NullnessMarks marks) {
    TypeVariableName k = marks.nullableBounded("K");
    TypeVariableName v = marks.nullableBounded("V");
    TypeName values = nested ? WildcardTypeName.subtypeOf(v) : ANY;
    TypeVariableName c =
        TypeVariableName.get(
            "C", ParameterizedTypeName.get(MAP, WildcardTypeName.subtypeOf(k), values));
    MethodSpec.Builder helper =
        helperBuilder(
            KEYED_MAP_HELPER,
            c,
            "Guards an identity-copied map whose keys are containers: a null key, which has nothing"
                + " to locate by, is the unlocated {@code must not contain a null key}, its value"
                + " unscanned; a present key passes its own scan, its failures located under its"
                + " rendering; and a null value is a located invalid under its key"
                + (nested ? ", while a present one passes its own scan, located under its key" : "")
                + ", accumulating, an entry's key before its value.\n",
            nested ? new TypeVariableName[] {c, k, v} : new TypeVariableName[] {c, k});
    helper.addParameter(marks.nullable(c), "values").addParameter(elementScan(k), "keys");
    if (nested) {
      helper.addParameter(elementScan(v), "each");
    }
    helper
        .addCode(nullGuard("values"))
        .addStatement("$L", declareFailures())
        .beginControlFlow(
            "for ($T entry : values.entrySet())",
            ParameterizedTypeName.get(
                MAP.nestedClass("Entry"), WildcardTypeName.subtypeOf(k), values))
        .addStatement("$T key = entry.getKey()", k)
        .beginControlFlow("if (key == null)")
        .addStatement("failures.add($L)", nullKey())
        .addStatement("continue")
        .endControlFlow()
        .addCode(scannedKey("key"));
    if (nested) {
      helper
          .addStatement("$T value = entry.getValue()", v)
          .addStatement("$T located", FAILURES)
          .beginControlFlow("if (value == null)")
          .addStatement("located = $L", nullAt("String.valueOf(key)"))
          .nextControlFlow("else")
          .addCode(scannedElement("value", "String.valueOf(key)"))
          .endControlFlow()
          .addStatement("$L", accumulate());
    } else {
      helper
          .beginControlFlow("if (entry.getValue() == null)")
          .addStatement("$T located = $L", FAILURES, nullAt("String.valueOf(key)"))
          .addStatement("$L", accumulate())
          .endControlFlow();
    }
    return helper.endControlFlow().addStatement("$L", returnScanned("values")).build();
  }

  /**
   * The {@code Optional} the guard takes, declared {@code @Nullable} like every guarded read, since
   * a JSON binding leaves a missing {@code Optional} component {@code null}. Error Prone's {@code
   * NullableOptional} check, which most builds run, warns at such a declaration, so it is
   * suppressed on this parameter alone.
   */
  private static ParameterSpec optionalGuarded(TypeVariableName optional, NullnessMarks marks) {
    ParameterSpec.Builder parameter = ParameterSpec.builder(marks.nullable(optional), "value");
    if (marks.present()) {
      parameter.addAnnotation(
          AnnotationSpec.builder(SuppressWarnings.class)
              .addMember("value", "$S", "NullableOptional")
              .build());
    }
    return parameter.build();
  }

  private static MethodSpec optionalHelper(NullnessMarks marks) {
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
        .addParameter(optionalGuarded(o, marks))
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

  // The helper a map whose values a leaf or nested spec converts passes its keys through, where
  // they hold containers. Unlike the scans above it is handed a parse's result as well as the map,
  // which the leg's guard has already found present, and merges its failures into that result.

  /**
   * The helper a map whose values a leaf or nested spec converts, and whose keys hold containers,
   * parses through ({@link #aroundParse}): it takes what {@code parseValues} answered, so the bulk
   * form runs first, and then scans each present key of the map that hands back, or, where it
   * refuses the map, each of the map's own. So the keys scanned are the domain's, and a key's
   * failures, located under its rendering, come before every failure the bulk form reports, a null
   * key among them. The bulk form runs whatever the keys hold, so a value's failure is reported
   * with theirs. Its result's key type is the bulk form's, which the leg names where the key holds
   * a nullable type argument, rather than one a nullness checker infers without it.
   *
   * @param marks the nullness annotations the helper is written with
   * @return the helper method to declare
   */
  static MethodSpec keysScannedHelper(NullnessMarks marks) {
    TypeVariableName k = marks.nullableBounded("K");
    TypeVariableName a = TypeVariableName.get("A");
    TypeName parsed =
        ParameterizedTypeName.get(VALIDATED, FAILURES, ParameterizedTypeName.get(MAP, k, a));
    return helperBuilder(
            KEYS_SCANNED_HELPER,
            ParameterizedTypeName.get(MAP, k, a),
            "Scans each present key of the map {@code parsed} holds, or, where {@code parsed}"
                + " refused {@code values}, each of {@code values}' own: keys that pass through as"
                + " they are and are containers. A key's failures are located under its rendering,"
                + " ahead of every failure {@code parsed} reports; with none, {@code parsed} is"
                + " returned as it is.\n",
            k,
            a)
        .addParameter(parsed, "parsed")
        .addParameter(
            ParameterizedTypeName.get(MAP, WildcardTypeName.subtypeOf(marks.nullable(k)), ANY),
            "values")
        .addParameter(elementScan(k), "keys")
        .addStatement(
            "$T scanned = parsed.isValid() ? parsed.get() : values",
            ParameterizedTypeName.get(MAP, WildcardTypeName.subtypeOf(marks.nullable(k)), ANY))
        .addStatement("$L", declareFailures())
        .beginControlFlow("for ($T key : scanned.keySet())", k)
        .beginControlFlow("if (key != null)")
        .addCode(scannedKey("key"))
        .endControlFlow()
        .endControlFlow()
        .beginControlFlow("if (failures.isEmpty())")
        .addStatement("return parsed")
        .endControlFlow()
        .beginControlFlow("if (!parsed.isValid())")
        .addStatement("failures.addAll(parsed.getError().toJavaList())")
        .endControlFlow()
        .addStatement(
            "return $T.invalid($T.of(failures.getFirst(), failures.subList(1, failures.size())))",
            VALIDATED,
            NEL)
        .build();
  }
}
