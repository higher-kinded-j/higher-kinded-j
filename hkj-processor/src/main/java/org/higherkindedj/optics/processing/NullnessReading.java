// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.StringJoiner;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
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
 * What each side of a mapping's or a merge's copy says about {@code null}, read where it is
 * written, and how a refusal names a site that rules a {@code null} out.
 *
 * <p>A type is read as its record declares it and as it is instantiated ({@link Written}), with the
 * bound of any type variable it sits inside ({@link Bound}), level by level down the containers a
 * copy reaches ({@link #nullableLevel}): {@code parse} reads the wire into the domain so, {@code
 * build} the domain into the wire, and a plain merge its sources into the target ({@link
 * #nullableElement}). A type argument that crosses a nested spec is read against the spec's own
 * declared pair ({@link #handedArgument}, {@link #builtArgument}). A record component's own site is
 * read where its canonical constructor takes it ({@link #nonNullComponent}), and a refusal names
 * the site, why it rules a {@code null} out and the fix that lets one in ({@link NonNullSite},
 * {@link #nonNullBecause}, {@link #nullableFix}).
 *
 * <p>Nothing here reports: {@link MappingProcessor} and {@link MergeProcessor} decide what to
 * refuse, and this class only reads, as {@link NullScan} and {@link ContainerCopy} only emit.
 */
final class NullnessReading {

  private NullnessReading() {}

  /**
   * A site {@code build} writes a value into that is declared non-null: how a diagnostic names the
   * member (with what it declares) and the declaration that says so, the site's declared type, the
   * type declaring it, and why it takes no {@code null}. A merge's target component is such a site
   * too.
   */
  record NonNullSite(
      String member,
      String declaration,
      TypeMirror type,
      TypeElement declaredBy,
      NullableAnnotations.NonNullReason reason) {}

  /**
   * The record component a canonical constructor writes, when it is declared non-null ({@link
   * NullableAnnotations#nonNullReason}), or null. What the value is handed to is the canonical
   * constructor's parameter, so one an explicit constructor declares nullable takes the null,
   * whatever the component says, as a constructor that defaults it does, and one it declares
   * non-null takes none, where the component says nothing either way.
   */
  static NonNullSite nonNullComponent(
      Types types, TypeElement record, RecordComponentElement component) {
    Optional<VariableElement> parameter = canonicalParameter(types, record, component);
    if (parameter.filter(NullableAnnotations::declaresNullable).isPresent()) {
      return null;
    }
    String spelt = "'" + record.getSimpleName() + "." + component.getSimpleName() + "'";
    return NullableAnnotations.nonNullReason(component)
        .map(
            reason ->
                new NonNullSite(
                    "the record component " + spelt + ", which is declared non-null",
                    spelt,
                    component.asType(),
                    record,
                    reason))
        .or(() -> parameter.flatMap(taken -> nonNullParameter(record, spelt, taken)))
        .orElse(null);
  }

  /**
   * The parameter of {@code record}'s canonical constructor that writes {@code component}, found by
   * its parameter types, since {@code Elements.isCanonicalConstructor} answers false for every
   * constructor of a record read from a class file.
   */
  static Optional<VariableElement> canonicalParameter(
      Types types, TypeElement record, RecordComponentElement component) {
    int index = record.getRecordComponents().indexOf(component);
    return ProcessorUtils.canonicalConstructorOf(types, record)
        .findFirst()
        .map(constructor -> constructor.getParameters().get(index));
  }

  /**
   * A canonical constructor's parameter, as the site of the component it writes, when the parameter
   * is declared non-null and the component says nothing either way: the fix is made on the
   * parameter, so the diagnostic names it.
   */
  private static Optional<NonNullSite> nonNullParameter(
      TypeElement record, String component, VariableElement parameter) {
    return NullableAnnotations.nonNullReason(parameter)
        .map(
            reason ->
                new NonNullSite(
                    "the record component "
                        + component
                        + ", whose canonical constructor's parameter is declared non-null",
                    parameterNamed(record, parameter),
                    parameter.asType(),
                    record,
                    reason));
  }

  /** How a refusal names a canonical constructor's parameter: {@code the parameter 'id' of ...}. */
  static String parameterNamed(TypeElement record, VariableElement parameter) {
    return "the parameter '"
        + parameter.getSimpleName()
        + "' of the canonical constructor "
        + ((ExecutableElement) parameter.getEnclosingElement())
            .getParameters().stream()
                .map(each -> ProcessorUtils.simpleTypeName(each.asType()))
                .collect(Collectors.joining(", ", record.getSimpleName() + "(", ")"));
  }

  /**
   * Why a record component whose type is a type variable with no nullness of its own rules a null
   * out, as {@code written} instantiates it, where neither it nor its canonical constructor's
   * parameter declares it nullable, or empty. The twin of {@link #nonNullComponent} for a component
   * whose nullness its type argument decides: both {@code parse}, for the domain's component, and
   * {@code build}, for the wire's, read it so.
   */
  static Optional<NullableAnnotations.NonNullReason> deferredNonNull(
      Types types, TypeElement record, RecordComponentElement component, Written written) {
    if (!written.deferred()
        || NullableAnnotations.declaresNullable(component)
        || canonicalParameter(types, record, component)
            .filter(NullableAnnotations::declaresNullable)
            .isPresent()) {
      return Optional.empty();
    }
    return written.nonNullReason();
  }

  /**
   * The clause that lets a non-null write site take a {@code null}: mark it {@code Nullable}, or
   * replace the annotation that rules {@code null} out. An array site is told where the annotation
   * goes, since one before the type marks the elements, and a site read from a class file is told
   * the change is made where it is declared, which may not be this build's to make. So is a site
   * the compiler cannot place, since that clause holds of a source site too.
   */
  static String nullableFix(ProcessingEnvironment env, NonNullSite site) {
    String fix =
        switch (site.reason()) {
          case NullableAnnotations.NonNullReason.Annotated annotated ->
              "replace @"
                  + annotated.annotation()
                  + " on "
                  + site.declaration()
                  + " with @Nullable";
          case NullableAnnotations.NonNullReason.Marked _ ->
              "mark "
                  + site.declaration()
                  + " @Nullable"
                  + (site.type().getKind() == TypeKind.ARRAY
                      ? " before its brackets, '"
                          + nullableArray(site.type())
                          + "', since a @Nullable before the type marks the elements"
                      : "");
        };
    return ProcessorUtils.compiledFromSource(env.getElementUtils(), site.declaredBy())
        ? fix
        : fix + " where '" + nestedName(env, site.declaredBy()) + "' is declared, if you build it";
  }

  /**
   * Why a non-null site takes no {@code null}, as the clause after its declaration: the annotation
   * it carries, or the {@code NullMarked} scope it sits in, named by its kind.
   */
  static String nonNullBecause(ProcessingEnvironment env, NonNullSite site) {
    return nonNullBecause(env, site.reason());
  }

  /** {@link #nonNullBecause(ProcessingEnvironment, NonNullSite)}, by the reason alone. */
  static String nonNullBecause(
      ProcessingEnvironment env, NullableAnnotations.NonNullReason reason) {
    return switch (reason) {
      case NullableAnnotations.NonNullReason.Annotated annotated ->
          "is annotated @" + annotated.annotation();
      case NullableAnnotations.NonNullReason.Marked marked ->
          "carries no @Nullable inside @NullMarked "
              + marked.scope().getKind().toString().toLowerCase(Locale.ROOT).replace('_', ' ')
              + " '"
              + switch (marked.scope()) {
                case TypeElement type -> nestedName(env, type);
                case QualifiedNameable qualified -> qualified.getQualifiedName();
                default -> marked.scope().getSimpleName();
              }
              + "'";
    };
  }

  /** An array type with {@code @Nullable} on the array itself: {@code String @Nullable [][]}. */
  private static String nullableArray(TypeMirror array) {
    String spelt = ProcessorUtils.simpleTypeName(array);
    int brackets = spelt.indexOf('[');
    return spelt.substring(0, brackets) + " @Nullable " + spelt.substring(brackets);
  }

  /** A type's name with its enclosing types and no type arguments: {@code Profile.Builder}. */
  private static String nestedName(ProcessingEnvironment env, TypeElement type) {
    return ProcessorUtils.simpleTypeName(env.getTypeUtils().erasure(type.asType()));
  }

  /**
   * Whether a component {@code build} reads is declared as one that may hold {@code null}, read at
   * its accessor, which {@code build} calls ({@link NullableAnnotations#declaresNullable}). javac
   * copies an annotation written on the component to an implicit accessor, unless it targets only
   * fields or record components, and an accessor the record declares itself says what it answers,
   * which may be a value where the field holds {@code null}.
   */
  static boolean readsNullable(RecordComponentElement component) {
    return NullableAnnotations.declaresNullable(component.getAccessor());
  }

  /**
   * Whether a component that a plain merge or a mapping's optic copies as it is may hand on a
   * {@code null}: its declaration says so, at the sites {@link
   * NullableAnnotations#declaresNullable(RecordComponentElement)} reads, the backing field
   * included, or the accessor the copy calls says it may answer one ({@link #readsNullable}). An
   * accessor the record declares itself may say so where the component says nothing, and the copy
   * hands on what it answers.
   *
   * <p>It reads wider than {@link #readsNullable}, which {@code build} reads the domain with, and
   * where an accessor the record declares itself decides alone: a check that refuses a merge or
   * withholds an optic gives a declared {@code null} the benefit of the doubt.
   *
   * @param component the source component a copy reads
   * @return whether the copy may hand on a {@code null}
   */
  static boolean copiesNullable(RecordComponentElement component) {
    return NullableAnnotations.declaresNullable(component) || readsNullable(component);
  }

  /**
   * A type as a record declares it and as it is instantiated, read together, with the scopes the
   * declaration is read in, and {@code arguments}, the scopes where the type arguments it is
   * instantiated with are written. A type variable with no nullness annotation of its own, as
   * {@code T} in {@code record Page<T extends @Nullable Object>(T item)}, stands for its type
   * argument ({@link #deferred}): the instantiated form counts then, read where that argument is
   * written, unless the variable's own bound rules a null out. Any other declaration counts as it
   * is written, {@code @Nullable T} and {@code @NonNull T} among them, read in its own scope. Below
   * any declared type variable, annotated or not, the levels are the argument's ({@link
   * #namesNoLevels}), so they are read where it is written ({@link #argued}), and the variable's
   * own bound, read in its own scope, rules a null out at each of them where it does ({@code
   * bound}, null outside any variable). A variable javac made by capturing a wildcard declares
   * nothing of its own, so it is read as it is written, and says nothing. A declaration whose
   * instantiation cannot be read, such as a bean's inherited setter, has no {@code arguments}, so
   * its variable says nothing beyond its own bound.
   */
  record Written(
      TypeMirror declared,
      TypeMirror instantiated,
      List<Element> scopes,
      List<Element> arguments,
      Bound bound) {

    Written {
      scopes = List.copyOf(scopes);
      arguments = List.copyOf(arguments);
    }

    Written(
        TypeMirror declared,
        TypeMirror instantiated,
        List<Element> scopes,
        List<Element> arguments) {
      this(declared, instantiated, scopes, arguments, null);
    }

    /**
     * This form read through a wildcard's upper bound, as a bridged {@code Optional}'s present
     * value is resolved: {@code ? extends List<Code>} holds a {@code List<Code>}. Itself where it
     * is no {@code ? extends} wildcard.
     */
    Written throughWildcard() {
      return new Written(
          extendsOf(declared).findFirst().orElse(declared),
          extendsOf(instantiated).findFirst().orElse(instantiated),
          scopes,
          arguments,
          bound);
    }

    /** Whether the declaration leaves its nullness to its type argument. */
    boolean deferred() {
      return NullableAnnotations.declaredVariable(declared)
          && !NullableAnnotations.annotatesNullness(declared);
    }

    /**
     * Whether the declaration is a type variable, which names no level of its own, whatever its
     * annotation: an {@code @NonNull M} says the value is present, and its argument, as {@code
     * Map<String, @Nullable String>}, says what it holds.
     */
    private boolean namesNoLevels() {
      return NullableAnnotations.declaredVariable(declared);
    }

    /** Whether this type is part of a type argument, read below a type variable, which wrote it. */
    private boolean argued() {
      return scopes.equals(arguments);
    }

    /**
     * Both forms' element types at {@code level}, as {@link NullScan#writtenElement} reads them;
     * below a type variable ({@link #namesNoLevels}), the argument's alone, read where it is
     * written.
     */
    Written element(NullScan level, Types types, Elements elements) {
      TypeMirror inner = level.writtenElement(instantiated, types, elements);
      Bound within = enclosing().map(b -> b.element(level, types, elements)).orElse(null);
      return namesNoLevels()
          ? new Written(inner, inner, arguments, arguments, within)
          : new Written(
              level.writtenElement(declared, types, elements), inner, scopes, arguments, within);
    }

    /**
     * Both forms' key types at a map {@code level}, as {@link NullScan#writtenKey} reads them, as
     * {@link #element} reads the elements; null where either form names no key, which says nothing
     * either way.
     */
    Written key(NullScan level, Types types, Elements elements) {
      TypeMirror inner = level.writtenKey(instantiated, types, elements);
      if (inner == null) {
        return null;
      }
      Bound within = enclosing().map(b -> b.key(level, types, elements)).orElse(null);
      return namesNoLevels()
          ? new Written(inner, inner, arguments, arguments, within)
          : Optional.ofNullable(level.writtenKey(declared, types, elements))
              .map(outer -> new Written(outer, inner, scopes, arguments, within))
              .orElse(null);
    }

    /**
     * The bound the levels below this one sit inside: this declaration's own, where it is a type
     * variable, read in its scope, or else the one this level sits inside.
     */
    private Optional<Bound> enclosing() {
      return namesNoLevels()
          ? Optional.of(new Bound(declared, declared, scopes))
          : Optional.ofNullable(bound);
    }

    /**
     * Both forms' type arguments, as {@link #element} reads a level's element: below a type
     * variable, which names no arguments of its own, the argument's alone, read where it is
     * written, each inside what the variable's bound gives at its place.
     */
    List<Written> typeArguments() {
      List<? extends TypeMirror> inner = argumentsOf(instantiated);
      List<? extends TypeMirror> outer = argumentsOf(declared);
      return IntStream.range(0, inner.size())
          .mapToObj(
              i -> {
                Bound within = enclosing().map(b -> b.argument(i, instantiated)).orElse(null);
                return outer.size() == inner.size()
                    ? new Written(outer.get(i), inner.get(i), scopes, arguments, within)
                    : new Written(inner.get(i), inner.get(i), arguments, arguments, within);
              })
          .toList();
    }

    /**
     * Whether the form that counts takes a null: it is declared nullable, and nothing rules a null
     * out ({@link #nonNull}).
     */
    boolean takesNull() {
      return nullable() && !nonNull();
    }

    /** Whether the form that counts is declared nullable. */
    boolean nullable() {
      return NullableAnnotations.declaresNullable(counted());
    }

    /**
     * Whether the form that counts rules a null out ({@link
     * NullableAnnotations#rulesOutNullReason}) in a scope it is read in: its own, or, for a
     * deferred declaration, where its argument is written. A deferred variable whose own bound is
     * non-null rules a null out whatever its argument, as {@link #nonNullComponent} reads it for
     * the component itself, and so does the bound of a variable this level sits inside.
     */
    boolean nonNull() {
      return nonNullReason().isPresent();
    }

    /** Why the form that counts rules a null out, as {@link #nonNull} reads it, or empty. */
    Optional<NullableAnnotations.NonNullReason> nonNullReason() {
      return asWritten().or(() -> Optional.ofNullable(bound).flatMap(Bound::nonNullReason));
    }

    /**
     * Whether, where the form that counts rules a null out, the bound of a variable this level sits
     * inside alone does so, not the type as it is written.
     */
    private boolean boundRules() {
      return asWritten().isEmpty();
    }

    /** Why the form that counts rules a null out as it is written, its bound aside, or empty. */
    private Optional<NullableAnnotations.NonNullReason> asWritten() {
      Optional<NullableAnnotations.NonNullReason> own = ownReason();
      return own.isPresent() || !deferred()
          ? own
          : arguments.stream()
              .flatMap(
                  scope -> NullableAnnotations.rulesOutNullReason(instantiated, scope).stream())
              .findFirst();
    }

    /**
     * Why the declaration itself rules a null out, in its own scopes, or empty: for a deferred one,
     * its own bound alone.
     */
    private Optional<NullableAnnotations.NonNullReason> ownReason() {
      return scopes.stream()
          .flatMap(
              scope ->
                  (deferred()
                          ? NullableAnnotations.nonNullReason(declared, scope)
                          : NullableAnnotations.rulesOutNullReason(declared, scope))
                      .stream())
          .findFirst();
    }

    /** Whether what counts here is a type argument, not the declaration. */
    boolean fromArgument() {
      return argued() || deferred() && ownReason().isEmpty();
    }

    /** The form that counts: the argument, for a deferred declaration, or else the declaration. */
    TypeMirror counted() {
      return deferred() ? instantiated : declared;
    }
  }

  /**
   * What a type variable's own bound says of a level inside the variable, read in the scope the
   * variable is declared in: {@code L extends List<String>}, in a {@code @NullMarked} record, holds
   * non-null strings whatever its argument. {@code type} is the bound's type at this level,
   * starting at the variable itself, and {@code variable} the variable. A type variable met inside
   * the bound, as {@code E} in {@code L extends List<E>}, stands for an argument written elsewhere,
   * so it rules a null out only by its own bound or annotation, never by the scope it is read in. A
   * level is read through a type variable's bound, followed while it is one, and through each bound
   * of an intersection in turn, the first that names it deciding.
   */
  record Bound(TypeMirror type, TypeMirror variable, List<Element> scopes) {

    Bound {
      scopes = List.copyOf(scopes);
    }

    /** The bound's element type at {@code level}, or null where the bound names none. */
    private Bound element(NullScan level, Types types, Elements elements) {
      return at(readable(type).map(read -> level.namedElement(read, types, elements)));
    }

    /** The bound's key type at a map {@code level}, or null where the bound names none. */
    private Bound key(NullScan level, Types types, Elements elements) {
      return at(readable(type).map(read -> level.writtenKey(read, types, elements)));
    }

    /**
     * The bound's type argument at {@code index}, where the bound names the class {@code
     * instantiated} is an instance of, with its arguments, or null where it names another class or
     * uses it raw.
     */
    private Bound argument(int index, TypeMirror instantiated) {
      return at(
          readable(type)
              .filter(
                  read ->
                      classOf(read).equals(classOf(instantiated))
                          && argumentsOf(read).size() > index)
              .map(read -> argumentsOf(read).get(index))
              .flatMap(NullnessReading::extendsOf));
    }

    /**
     * Why the bound proves a null absent at this level, in the variable's own scope, or empty: by
     * an annotation, by its scope, or by a type variable's own bound, never by the parametric rule
     * that {@link NullableAnnotations#rulesOutNullReason} adds for a variable in a marked scope.
     */
    Optional<NullableAnnotations.NonNullReason> nonNullReason() {
      return scopes.stream()
          .flatMap(scope -> NullableAnnotations.nonNullReason(type, scope).stream())
          .findFirst();
    }

    /** The first type the bound names at this level, as a bound, or null where it names none. */
    private Bound at(Stream<TypeMirror> named) {
      return named
          .filter(Objects::nonNull)
          .findFirst()
          .map(inner -> new Bound(inner, variable, scopes))
          .orElse(null);
    }
  }

  /**
   * The types a level of {@code type} is read through, in order: a type variable through its bound,
   * followed while it is one, and an intersection through each of its bounds. A bound never holds a
   * wildcard at its own level: one inside it is read as its type argument is ({@link #extendsOf}).
   */
  private static Stream<TypeMirror> readable(TypeMirror type) {
    return switch (type.getKind()) {
      case TYPEVAR -> readable(((TypeVariable) type).getUpperBound());
      case INTERSECTION ->
          ((IntersectionType) type).getBounds().stream().flatMap(NullnessReading::readable);
      default -> Stream.of(type);
    };
  }

  /**
   * A type argument as a level reads it: a wildcard by its {@code extends} bound, and a {@code ?}
   * or {@code ? super} wildcard as nothing, since it rules no {@code null} out.
   */
  private static Stream<TypeMirror> extendsOf(TypeMirror argument) {
    return argument instanceof WildcardType wildcard
        ? Stream.ofNullable(wildcard.getExtendsBound())
        : Stream.of(argument);
  }

  /** The class or interface {@code type} is an instance of, or empty for any other type. */
  private static Optional<Element> classOf(TypeMirror type) {
    return Optional.of(type)
        .filter(DeclaredType.class::isInstance)
        .map(declared -> ((DeclaredType) declared).asElement());
  }

  /**
   * One level a walk passes: its container's shape, and, at a map, whether the walk goes on into
   * its {@code key}s rather than its values. A key that is itself a container is walked as a value
   * is, so a path can pass through keys at any depth: the keys' elements of a {@code
   * Map<List<String>, V>}.
   */
  record Step(NullScan.Shape shape, boolean key) {

    /**
     * The steps of a path that passes through elements and values only, its last at {@code key}.
     */
    static List<Step> of(List<NullScan.Shape> shapes, boolean key) {
      return IntStream.range(0, shapes.size())
          .mapToObj(i -> new Step(shapes.get(i), key && i == shapes.size() - 1))
          .toList();
    }
  }

  /**
   * Where a copy hands on a {@code null} its target rules out: the levels walked to reach it, the
   * last of them a map's keys where the null is a key ({@link #key}), the source's and the target's
   * element or key there, and how many of those levels lie above the target's type argument, where
   * its type argument is what counts ({@code within}): the levels below it are inside the argument.
   * Empty {@code within} where the target's declaration counts.
   */
  record NullableLevel(List<Step> path, Written from, Written to, OptionalInt within) {

    NullableLevel {
      path = List.copyOf(path);
    }

    /** Whether the null is a map's key, not an element or a value. */
    boolean key() {
      return path.getLast().key();
    }

    /** The levels walked inside the target's type argument. */
    private List<Step> inArgument() {
      return path.subList(within.orElseThrow(), path.size());
    }
  }

  /**
   * The first level a copy reaches, walking {@code levels} down from {@code from} and {@code to},
   * where {@code from}'s element, or its map key, is declared nullable and {@code to}'s rules a
   * null out, or empty. Every copy is read so: {@code build} from the domain into the wire, and a
   * plain merge from its sources into the target, through {@link #copiedInto}, and {@code parse}
   * from the wire into the domain, through {@link #ruledOutIn}. Each side reads its own type
   * arguments where they are written. A raw container names no element type, so it says nothing
   * either way, and an {@code Optional} cannot hold a null, so its own level counts for nothing. A
   * map's key that is itself a container is walked into before its values, as {@link NullScan}
   * scans it, since a copy hands it on as it is.
   */
  private static Optional<NullableLevel> nullableLevel(
      Written from, Written to, NullScan levels, Types types, Elements elements) {
    return nullableLevel(from, to, levels, level -> false, types, elements);
  }

  /**
   * {@link #nullableLevel(Written, Written, NullScan, Types, Elements)}, walking on past a level
   * another site takes the null at ({@code excused}).
   */
  private static Optional<NullableLevel> nullableLevel(
      Written from,
      Written to,
      NullScan levels,
      Predicate<NullableLevel> excused,
      Types types,
      Elements elements) {
    // Below a type variable, the levels are its argument's.
    OptionalInt within = to.namesNoLevels() ? OptionalInt.of(0) : OptionalInt.empty();
    return nullableLevel(List.of(), from, to, levels, within, excused, types, elements);
  }

  /**
   * {@link #nullableLevel(Written, Written, NullScan, Predicate, Types, Elements)} below the levels
   * {@code above}, which a map's keys are walked under.
   */
  private static Optional<NullableLevel> nullableLevel(
      List<Step> above,
      Written from,
      Written to,
      NullScan levels,
      OptionalInt within,
      Predicate<NullableLevel> excused,
      Types types,
      Elements elements) {
    List<Step> path = new ArrayList<>(above);
    Written source = from;
    Written target = to;
    OptionalInt argument = within;
    for (NullScan level = levels; level != null; level = level.inner()) {
      // A map's keys are read at its own level, before the sides move on to its values. javac
      // takes Map<?, V> and Map<? extends Object, V> for one type, so one side may name a key
      // the other does not: a key a side does not name says nothing either way.
      Written sourceKey = source.key(level, types, elements);
      Written targetKey = target.key(level, types, elements);
      if (sourceKey != null && targetKey != null) {
        List<Step> toKeys = append(path, new Step(level.shape(), true));
        if (sourceKey.nullable() && targetKey.nonNull()) {
          NullableLevel found = levelAt(toKeys, sourceKey, targetKey, argument);
          if (!excused.test(found)) {
            return Optional.of(found);
          }
        }
        // A key that holds containers is copied as it is too, so its levels are walked as well.
        Optional<NullableLevel> inside =
            level.keys() == null
                ? Optional.empty()
                : nullableLevel(
                    toKeys,
                    sourceKey,
                    targetKey,
                    level.keys(),
                    argument.isEmpty() && targetKey.namesNoLevels()
                        ? OptionalInt.of(toKeys.size())
                        : argument,
                    excused,
                    types,
                    elements);
        if (inside.isPresent()) {
          return inside;
        }
      }
      path.add(new Step(level.shape(), false));
      source = source.element(level, types, elements);
      target = target.element(level, types, elements);
      // A raw container names no element type, so it says nothing either way, and holds no level
      // below it.
      if (level.element() == null) {
        break;
      }
      if (level.shape() != NullScan.Shape.OPTIONAL && source.nullable() && target.nonNull()) {
        NullableLevel found = levelAt(path, source, target, argument);
        if (!excused.test(found)) {
          return Optional.of(found);
        }
      }
      if (argument.isEmpty() && target.namesNoLevels()) {
        argument = OptionalInt.of(path.size());
      }
    }
    return Optional.empty();
  }

  /**
   * The level {@link #nullableLevel} stops at, where {@code target} is a type variable itself, or
   * inside one {@code within} levels down, and counts as its argument says.
   */
  private static NullableLevel levelAt(
      List<Step> path, Written source, Written target, OptionalInt within) {
    OptionalInt argument =
        within.isEmpty() && target.namesNoLevels() ? OptionalInt.of(path.size()) : within;
    return new NullableLevel(
        path, source, target, target.fromArgument() ? argument : OptionalInt.empty());
  }

  /**
   * Where a copy into a record component hands on a {@code null} its site rules out: the level, and
   * whether the site is the canonical constructor's {@code parameter}, which {@code build} calls,
   * rather than the component. The parameter decides at a level where it says anything: one that
   * takes the null there excuses the copy at that level, and one that rules it out refuses it. The
   * component decides where the parameter says nothing, as {@link #nonNullComponent} reads the
   * component itself.
   */
  record Copied(NullableLevel level, boolean toParameter) {}

  /**
   * The first level a copy from {@code from} into a site typed {@code component}, written through
   * {@code parameter} where a record's canonical constructor takes it, hands on a {@code null} the
   * site rules out ({@link Copied}), or empty.
   */
  static Optional<Copied> copiedInto(
      Written from,
      Written component,
      Optional<Written> parameter,
      NullScan levels,
      Types types,
      Elements elements) {
    Predicate<NullableLevel> taken =
        level ->
            parameter
                .flatMap(written -> reached(written, levels, level, types, elements))
                .filter(Written::takesNull)
                .isPresent();
    return nullableLevel(from, component, levels, taken, types, elements)
        .map(level -> new Copied(level, false))
        .or(
            () ->
                parameter
                    .flatMap(written -> nullableLevel(from, written, levels, types, elements))
                    .map(level -> new Copied(level, true)));
  }

  /**
   * Whether a copy from {@code from} into a record component typed {@code component} hands on a
   * {@code null} that the component, or the canonical constructor's {@code parameter}, rules out at
   * a level {@code levels} reaches. {@code parse} reads a wire into the domain so: its null scan
   * refuses what the component rules out, and a total optic that hands the domain such a null calls
   * the constructor, so either refuses it. Unlike {@link #copiedInto}, a parameter that takes the
   * null excuses nothing, since the scan still refuses it.
   */
  static boolean ruledOutIn(
      Written from,
      Written component,
      Optional<Written> parameter,
      NullScan levels,
      Types types,
      Elements elements) {
    return nullableLevel(from, component, levels, types, elements).isPresent()
        || parameter
            .flatMap(written -> nullableLevel(from, written, levels, types, elements))
            .isPresent();
  }

  /**
   * {@code type} at the level {@code level} reached, its element or its key, or empty, following
   * the keys or the values at each map the path passes, as the walk did.
   */
  private static Optional<Written> reached(
      Written type, NullScan levels, NullableLevel level, Types types, Elements elements) {
    Optional<Written> reached = Optional.of(type);
    NullScan scan = levels;
    for (Step step : level.path()) {
      NullScan at = scan;
      reached =
          reached.map(
              written ->
                  step.key()
                      ? written.key(at, types, elements)
                      : written.element(at, types, elements));
      scan = step.key() ? scan.keys() : scan.inner();
    }
    return reached;
  }

  /**
   * The sites a refusal names where a copy's {@code level} reaches a target that rules a null out,
   * inside a site {@code member} names, its declaration spelt {@code declaration} and declared by
   * {@code declaredBy}: the type argument {@code spec} gives, where the argument counts, or else
   * the declaration as written, the level named where it lies below the site's own ({@link
   * #withBound}).
   */
  static List<NonNullSite> rulingSites(
      NullableLevel level,
      String member,
      String declaration,
      TypeElement declaredBy,
      TypeElement spec) {
    String what = typeOf(level.path().getLast());
    Written to = level.to();
    Optional<NullableAnnotations.NonNullReason> reason = to.nonNullReason();
    NonNullSite asWritten;
    if (level.within().isPresent()) {
      List<Step> inArgument = level.inArgument();
      String type = ProcessorUtils.simpleTypeName(to.counted());
      String named =
          switch (inArgument.size()) {
            case 0 -> "the type argument " + type;
            case 1 -> "the " + what + " " + type + " of the type argument";
            default ->
                "the "
                    + what
                    + " "
                    + type
                    + " at the "
                    + parts(inArgument)
                    + " of the type argument";
          };
      asWritten =
          new NonNullSite(
              member,
              named + " the spec gives " + declaration,
              to.counted(),
              spec,
              reason.orElseThrow());
    } else {
      String type = ProcessorUtils.simpleTypeName(to.declared());
      asWritten =
          new NonNullSite(
              member,
              "the "
                  + what
                  + " "
                  + type
                  + (level.path().size() == 1 ? " in " : " at the " + parts(level.path()) + " of ")
                  + declaration,
              to.declared(),
              declaredBy,
              reason.orElseThrow());
    }
    return withBound(to, what, member, declaration, declaredBy, asWritten);
  }

  /**
   * The sites a refusal names where {@code to} rules a null out: {@code asWritten}, where the type
   * as it is written does; the bound of the type variable it sits inside, named as {@code what}
   * inside {@code declaration}, where that alone does; and both, where both do, since the fix must
   * change both before the null is let in.
   */
  static List<NonNullSite> withBound(
      Written to,
      String what,
      String member,
      String declaration,
      TypeElement declaredBy,
      NonNullSite asWritten) {
    Optional<NonNullSite> inBound =
        Optional.ofNullable(to.bound())
            .flatMap(
                bound ->
                    bound
                        .nonNullReason()
                        .map(
                            reason ->
                                new NonNullSite(
                                    member,
                                    "the "
                                        + what
                                        + " "
                                        + ProcessorUtils.simpleTypeName(bound.type())
                                        + " in the bound of the type variable "
                                        + ((TypeVariable) bound.variable())
                                            .asElement()
                                            .getSimpleName()
                                        + " in "
                                        + declaration,
                                    bound.type(),
                                    declaredBy,
                                    reason)));
    return to.boundRules()
        ? List.of(inBound.orElseThrow())
        : Stream.concat(Stream.of(asWritten), inBound.stream()).toList();
  }

  /**
   * The element or map key a plain merge copies from the record component {@code from} into {@code
   * to} nullable, where {@code to}, or the canonical constructor's parameter that writes it, rules
   * a null out, at a level {@code scan} reaches ({@link #copiedInto}): the parts reached, as a
   * refusal names them, and the site of that element. Empty where there is none. {@code from} is
   * read at its accessor, which the merge calls, as {@link #readsNullable} reads a component. A
   * merge's records are never generic.
   */
  static Optional<NullableElement> nullableElement(
      RecordComponentElement from,
      RecordComponentElement to,
      NullScan scan,
      Types types,
      Elements elements) {
    TypeElement record = (TypeElement) to.getEnclosingElement();
    String spelt = "'" + record.getSimpleName() + "." + to.getSimpleName() + "'";
    Optional<VariableElement> parameter = canonicalParameter(types, record, to);
    return copiedInto(
            new Written(
                from.getAccessor().getReturnType(),
                from.getAccessor().getReturnType(),
                List.of(from),
                List.of()),
            new Written(to.asType(), to.asType(), List.of(to), List.of()),
            parameter.map(
                taken -> new Written(taken.asType(), taken.asType(), List.of(taken), List.of())),
            scan,
            types,
            elements)
        .map(
            copied ->
                new NullableElement(
                    parts(copied.level().path()),
                    parts(List.of(copied.level().path().getLast())),
                    copied.level().key(),
                    rulingSites(
                            copied.level(),
                            "the record component " + spelt,
                            copied.toParameter()
                                ? parameterNamed(record, parameter.orElseThrow())
                                : spelt,
                            record,
                            record)
                        .getFirst()));
  }

  /**
   * A nullable element a plain copy hands on, as a refusal names it: the {@code parts} of the
   * container reached, such as {@code values' elements}, the {@code last} of them, whether they are
   * a map's {@code key}s, and the {@code site} of the element there that rules a null out.
   */
  record NullableElement(String parts, String last, boolean key, NonNullSite site) {}

  /**
   * A type argument a domain hands a nested spec nullable ({@code nullable}, as the domain writes
   * it), where the domain the spec declares rules a null out there ({@code ruling}).
   */
  record HandedArgument(Written nullable, Ruling ruling) {}

  /** Why a nested spec's declared domain rules a null out at a type argument. */
  sealed interface Ruling {

    /**
     * The spec's type there, {@code type}, rules it out as it is written, by {@code reason}: the
     * type argument {@code named}, such as {@code the type argument String of One<String>}.
     */
    record Declared(String named, TypeMirror type, NullableAnnotations.NonNullReason reason)
        implements Ruling {}

    /** A type variable with no nullness of its own, whose bound rules it out by {@code reason}. */
    record Bounded(TypeVariable variable, NullableAnnotations.NonNullReason reason)
        implements Ruling {}

    /** A type variable a leaf of the spec converts, whose build takes no null. */
    record Converted(TypeVariable variable, ExecutableElement leaf) implements Ruling {}
  }

  /**
   * The first type argument, at any depth, that {@code from} declares nullable where {@code to},
   * the domain a nested spec declares, read in {@code scopes}, rules a null out ({@link Ruling}). A
   * type variable stands for its whole argument, which its bound may rule out at a level below it,
   * so a level below one is read in the variable's bound, where the bound names the argument's own
   * class. {@code within} names where {@code to} sits, empty for the spec's domain itself.
   */
  static Optional<HandedArgument> handedArgument(
      Written from,
      TypeMirror to,
      String within,
      List<Element> scopes,
      Map<Element, ExecutableElement> converted) {
    List<Written> fromArguments = from.typeArguments();
    List<? extends TypeMirror> toArguments = argumentsOf(to);
    String inside = within.isEmpty() ? " in " + ProcessorUtils.simpleTypeName(to) : within;
    for (int i = 0; i < Math.min(fromArguments.size(), toArguments.size()); i++) {
      Written argument = fromArguments.get(i);
      TypeMirror ruling = toArguments.get(i);
      Optional<Ruling> why =
          argument.nullable() ? ruled(ruling, i, to, within, scopes, converted) : Optional.empty();
      Optional<HandedArgument> found =
          why.map(reason -> new HandedArgument(argument, reason))
              .or(
                  () ->
                      switch (ruling.getKind()) {
                        case DECLARED ->
                            handedArgument(argument, ruling, inside, scopes, converted);
                        // A bound lines up with the argument only where both name one class.
                        case TYPEVAR ->
                            readable(ruling)
                                .filter(
                                    bound ->
                                        classOf(bound).equals(classOf(argument.instantiated())))
                                .flatMap(
                                    bound ->
                                        handedArgument(
                                            argument,
                                            bound,
                                            " in the bound of "
                                                + ((TypeVariable) ruling)
                                                    .asElement()
                                                    .getSimpleName(),
                                            scopes,
                                            converted)
                                            .stream())
                                .findFirst();
                        default -> Optional.empty();
                      });
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }

  /**
   * Why {@code ruling}, a nested spec's type at the type argument {@code index} of {@code parent},
   * read in {@code scopes}, rules a null out, or empty: a type variable a leaf converts, one whose
   * own bound rules it out, or the type as it is written, named inside {@code within}. A wildcard
   * rules out what its {@code extends} bound does, and a {@code ?} or {@code ? super} nothing.
   */
  private static Optional<Ruling> ruled(
      TypeMirror ruling,
      int index,
      TypeMirror parent,
      String within,
      List<Element> scopes,
      Map<Element, ExecutableElement> converted) {
    if (ruling instanceof TypeVariable variable && converted.containsKey(variable.asElement())) {
      return Optional.of(new Ruling.Converted(variable, converted.get(variable.asElement())));
    }
    return extendsOf(ruling)
        .flatMap(
            read ->
                scopes.stream()
                    .flatMap(scope -> NullableAnnotations.nonNullReason(read, scope).stream())
                    .limit(1)
                    .<Ruling>map(
                        reason ->
                            read instanceof TypeVariable variable
                                    && !NullableAnnotations.annotatesNullness(read)
                                ? new Ruling.Bounded(variable, reason)
                                : new Ruling.Declared(
                                    argumentNamed(read, index, parent) + within, read, reason)))
        .findFirst();
  }

  /**
   * A type argument, or an array's element type, as {@code what} says, that a nested spec builds
   * nullable ({@code nullable}), where the wire's site rules a null out there ({@code ruling}),
   * named as {@code named} within its site: the record component, or, where {@code toParameter},
   * the canonical constructor's parameter that writes it.
   */
  record BuiltArgument(
      TypeMirror nullable, Written ruling, String what, String named, boolean toParameter) {}

  /**
   * The first type argument, or array element, at any depth, that a nested spec builds nullable,
   * its wire as it declares it ({@code from}) with its type variables standing for what {@code
   * bindings} gives them, where {@code to}, the wire's site, rules a null out ({@link
   * Written#nonNull}), its own type argument or the bound of a type variable it sits inside. A use
   * annotated for itself, as {@code @Nullable T}, builds what it says whatever its variable stands
   * for. Where a record's canonical constructor writes the site, through {@code parameter}, the
   * parameter decides at a level where it says anything: one that takes the null excuses the site,
   * and one that rules it out refuses it, as {@link #copiedInto} reads a copy. The site is named
   * first where both rule it out.
   */
  static Optional<BuiltArgument> builtArgument(
      TypeMirror from,
      Map<Element, TypeMirror> bindings,
      Written to,
      Optional<Written> parameter,
      Types types,
      Elements elements) {
    List<? extends TypeMirror> fromArguments = argumentsOf(from);
    List<Written> toArguments = to.typeArguments();
    Optional<List<Written>> taking = parameter.map(Written::typeArguments);
    for (int i = 0; i < Math.min(fromArguments.size(), toArguments.size()); i++) {
      int at = i;
      Optional<BuiltArgument> found =
          builtLevel(
              fromArguments.get(i),
              bindings,
              toArguments.get(i),
              taking.map(arguments -> arguments.get(at)),
              "type argument",
              argumentNamed(toArguments.get(i).declared(), i, to.counted()),
              types,
              elements);
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }

  /**
   * {@link #builtArgument} at one level, the {@code what} named {@code named}: {@code declared}, as
   * the nested spec's wire declares it, against {@code ruling}, the site's type there, and {@code
   * taking}, the canonical constructor parameter's; then the levels inside it, a declared type's
   * arguments or an array's elements.
   */
  private static Optional<BuiltArgument> builtLevel(
      TypeMirror declared,
      Map<Element, TypeMirror> bindings,
      Written ruling,
      Optional<Written> taking,
      String what,
      String named,
      Types types,
      Elements elements) {
    TypeMirror built =
        declared instanceof TypeVariable variable
            ? bindings.getOrDefault(variable.asElement(), variable)
            : declared;
    TypeMirror read = NullableAnnotations.annotatesNullness(declared) ? declared : built;
    boolean nullable = NullableAnnotations.declaresNullable(read);
    if (nullable && ruling.nonNull() && taking.filter(Written::takesNull).isEmpty()) {
      return Optional.of(new BuiltArgument(read, ruling, what, named, false));
    }
    Optional<Written> rules = taking.filter(Written::nonNull);
    if (nullable && rules.isPresent()) {
      return Optional.of(new BuiltArgument(read, rules.get(), what, named, true));
    }
    return switch (built.getKind()) {
      case DECLARED -> builtArgument(built, bindings, ruling, taking, types, elements);
      // A primitive array has no level to read.
      case ARRAY ->
          Optional.ofNullable(NullScan.of(ruling.instantiated(), types, elements))
              .flatMap(
                  level -> {
                    Written element = ruling.element(level, types, elements);
                    return builtLevel(
                        ((ArrayType) built).getComponentType(),
                        bindings,
                        element,
                        taking.map(written -> written.element(level, types, elements)),
                        "element type",
                        "the element type "
                            + ProcessorUtils.simpleTypeName(element.declared())
                            + " of "
                            + ProcessorUtils.simpleTypeName(ruling.counted()),
                        types,
                        elements);
                  });
      default -> Optional.empty();
    };
  }

  /**
   * How a refusal names the type argument {@code argument} at {@code index} of {@code parent}: by
   * its place among several, since two may be spelt alike, as in {@code Map<String, String>}.
   */
  private static String argumentNamed(TypeMirror argument, int index, TypeMirror parent) {
    return "the type argument "
        + ProcessorUtils.simpleTypeName(argument)
        + (argumentsOf(parent).size() == 1 ? "" : " at position " + (index + 1))
        + " of "
        + ProcessorUtils.simpleTypeName(parent);
  }

  /** A declared type's type arguments; none for any other kind of type. */
  private static List<? extends TypeMirror> argumentsOf(TypeMirror type) {
    return type.getKind() == TypeKind.DECLARED
        ? ((DeclaredType) type).getTypeArguments()
        : List.of();
  }

  /** {@code path} with {@code last} after it. */
  static <T> List<T> append(List<T> path, T last) {
    return Stream.concat(path.stream(), Stream.of(last)).toList();
  }

  /**
   * The parts of a container a level path reaches, as a refusal names them: {@code elements},
   * {@code values' elements}, {@code value's elements}, {@code keys' elements}. An array's and a
   * collection's are its elements, a {@code Map}'s its values, or its keys where the step goes into
   * them, and an {@code Optional}'s its one value.
   */
  static String parts(List<Step> path) {
    StringJoiner parts = new StringJoiner(" ");
    for (int i = 0; i < path.size(); i++) {
      boolean last = i == path.size() - 1;
      Step step = path.get(i);
      parts.add(
          switch (step.shape()) {
            case MAP -> (step.key() ? "keys" : "values") + (last ? "" : "'");
            case OPTIONAL -> "value's";
            case COLLECTION, ARRAY -> last ? "elements" : "elements'";
          });
    }
    return parts.toString();
  }

  /**
   * {@link #parts(List)} for a path through elements and values only, naming the last level's map
   * keys where {@code key}, not its values.
   */
  static String parts(List<NullScan.Shape> shapes, boolean key) {
    return parts(Step.of(shapes, key));
  }

  /**
   * What a level's parts are typed by, as a fix names it: a {@code Map}'s key type, where the step
   * goes into its keys, or its value type, or else the element type.
   */
  private static String typeOf(Step step) {
    return step.key()
        ? "key type"
        : step.shape() == NullScan.Shape.MAP ? "value type" : "element type";
  }
}
