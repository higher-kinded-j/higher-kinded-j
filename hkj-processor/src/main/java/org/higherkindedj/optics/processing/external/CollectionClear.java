// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.higherkindedj.optics.processing.util.ProcessorUtils;
import org.higherkindedj.optics.processing.util.ProtobufCollection;

/**
 * The clear method a {@code @ViaBuilder} lens calls before its setter, where the setter adds to a
 * collection the builder holds rather than replacing it. {@code toBuilder()} copies the collection
 * into the builder, so a lens that set through such a setter alone would keep the old elements
 * beside the new. Called first, the clear method makes the set replace them.
 *
 * <p>Two kinds of setter add so, each told by its shape:
 *
 * <ul>
 *   <li>a Lombok {@code @Singular} collection's, {@code tags(Collection<? extends String>)}, which
 *       {@linkplain ProcessorUtils#takesAnyCollection takes any collection} of its elements, beside
 *       a clear method {@linkplain ProcessorUtils#singularClearNames named} after the property the
 *       setter spells, bare or after Lombok's {@code setterPrefix};
 *   <li>a protobuf-java message builder's {@code addAllTags(Iterable)} or {@code
 *       putAllPoints(Map)}, a repeated or a map field's, beside the {@code clearTags()} or {@code
 *       clearPoints()} protobuf writes for every field.
 * </ul>
 *
 * <p>Either way the clear method takes no argument, and the chain calls it only where it hands the
 * builder back. A setter that replaces what the builder holds loses nothing to the clear method
 * called before it, so long as the builder holds a copy of its own, as Lombok's and protobuf's do.
 */
final class CollectionClear {

  private CollectionClear() {}

  /**
   * The clear method the setter comes with, where it adds to a collection: one that adds no warning
   * or exception to the generated call, none deprecated past the setter and none it would have to
   * catch. Whether it hands the builder back is the caller's to read, as the chain reads each of
   * its steps.
   *
   * @param types the round's type utilities
   * @param elements the round's element utilities
   * @param builderType the builder the setter is called on, as the call reaches it
   * @param members the builder's members the generated class can call
   * @param setterName the setter's name
   * @param setter the method the setter's call binds
   * @param otherProperties the properties the source type reads through accessors other than the
   *     lens's own, each capitalised as a clear method spells it: a clear method named after one of
   *     them empties another field
   * @return the clear method, or empty where the setter adds to no collection or has no clear
   *     method the call can make
   */
  static Optional<ExecutableElement> of(
      Types types,
      Elements elements,
      DeclaredType builderType,
      List<ExecutableElement> members,
      String setterName,
      ExecutableElement setter,
      Set<String> otherProperties) {
    DeclaredType called = (DeclaredType) types.capture(builderType);
    TypeMirror parameter = parameterOf(types, called, setter);
    // A protobuf message's builder is read by protobuf's names alone, so that 'addAllTags' clears
    // the field 'tags' even where a field 'all_tags' has a clear method too; any other builder by
    // Lombok's, whose setterPrefix 'add' spells a @Singular 'allTags' setter 'addAllTags' as well,
    // where a builder spelt as FreeBuilder spells one adds a whole 'tags' so.
    Stream<String> names =
        ProcessorUtils.isSubtypeOfNamed(
                types, elements, builderType, ProtobufCollection.MESSAGE_BUILDER)
            ? protobufClears(types, elements, members, called, setterName, parameter)
            : ProcessorUtils.takesAnyCollection(types, elements, parameter)
                ? singularClears(types, elements, members, setterName, setter, called)
                    .filter(
                        name ->
                            !otherProperties.contains(
                                ProcessorUtils.capitalise(name.substring(CLEAR.length()))))
                : Stream.empty();
    return names
        .map(name -> CallableMembers.accessorNamed(types, builderType, members, name))
        .filter(Objects::nonNull)
        .findFirst()
        .filter(clear -> deprecation(elements, clear) <= deprecation(elements, setter))
        .filter(clear -> clear.getThrownTypes().isEmpty());
  }

  private static final String CLEAR = "clear";

  /**
   * The name of the clear method protobuf-java writes beside a repeated or map field's adder,
   * {@code clearTags()} beside {@code addAllTags(Iterable)}: {@code clear} and the field's name as
   * the adder spells it. An open enum's adder of the numbers it holds spells the field with {@code
   * Value} after it, {@code addAllKindsValue(Iterable<Integer>)} beside {@code clearKinds()}; it is
   * read so only where the field's adder of the enum's constants is there too, since a field of its
   * own may be named {@code kinds_value}. A field's single adder, {@code addAllTags(String)} for a
   * field named {@code all_tags}, takes no container, and has none.
   */
  private static Stream<String> protobufClears(
      Types types,
      Elements elements,
      List<ExecutableElement> members,
      DeclaredType called,
      String setterName,
      TypeMirror parameter) {
    return Stream.of(ProtobufCollection.values())
        .filter(kind -> setterName.matches(Pattern.quote(kind.adderPrefix()) + "\\p{Lu}.*"))
        .filter(kind -> kind.takesWhole(types, elements, parameter))
        .map(
            kind -> {
              String field = setterName.substring(kind.adderPrefix().length());
              String stem = field.replaceFirst("Value$", "");
              boolean enumNumbers =
                  !stem.equals(field)
                      && members.stream()
                          .filter(member -> member.getParameters().size() == 1)
                          .filter(
                              member ->
                                  member.getSimpleName().contentEquals(kind.adderPrefix() + stem))
                          .anyMatch(
                              member ->
                                  holdsEnumConstants(
                                      types, elements, parameterOf(types, called, member)));
              return CLEAR + (enumNumbers ? stem : field);
            });
  }

  /**
   * Whether a container parameter holds a protobuf enum's constants: its last type argument, or
   * that argument's upper bound, is an {@code EnumLite}.
   */
  private static boolean holdsEnumConstants(Types types, Elements elements, TypeMirror parameter) {
    List<? extends TypeMirror> arguments = ((DeclaredType) parameter).getTypeArguments();
    TypeMirror held = arguments.getLast();
    TypeMirror element =
        held.getKind() == TypeKind.WILDCARD
            ? Objects.requireNonNullElse(
                ((WildcardType) held).getExtendsBound(),
                elements.getTypeElement("java.lang.Object").asType())
            : held;
    return ProcessorUtils.isSubtypeOfNamed(
        types, elements, element, "com.google.protobuf.Internal.EnumLite");
  }

  /**
   * The names a Lombok {@code @Singular} collection's clear method may have, for a setter that
   * takes any collection of its elements.
   */
  private static Stream<String> singularClears(
      Types types,
      Elements elements,
      List<ExecutableElement> members,
      String setterName,
      ExecutableElement setter,
      DeclaredType called) {
    // The setter spells the property bare, or after a setterPrefix of lower-case letters,
    // 'withTags' for 'tags'. The bare reading is tried first, so a property of its own named
    // 'withTags' keeps its own clear method. A prefixed reading is taken only where the prefix is
    // one the builder writes its other methods with, as Lombok writes the adder 'withTag' beside
    // the setter, so a compound name such as 'recentTags' is never read as one; and it is dropped
    // where a bare setter of that property owns the clear method, as 'shapes' does beside
    // 'bigShapes'.
    String stripped = setterName.replaceFirst("^\\p{Ll}+(?=\\p{Lu})", "");
    String prefix = setterName.substring(0, setterName.length() - stripped.length());
    Stream<String> properties =
        !prefix.isEmpty()
                && !ownedByABareSetter(types, elements, members, setter, stripped, called)
                && writesAnotherAfter(members, setter, prefix)
            ? Stream.of(setterName, stripped)
            : Stream.of(setterName);
    return properties.flatMap(property -> ProcessorUtils.singularClearNames(property).stream());
  }

  /**
   * Whether a builder method other than {@code setter} takes any collection under the property
   * {@code stripped} names spelt bare: the setter that owns the property's clear method.
   */
  private static boolean ownedByABareSetter(
      Types types,
      Elements elements,
      List<ExecutableElement> members,
      ExecutableElement setter,
      String stripped,
      DeclaredType called) {
    String property = ProcessorUtils.capitalise(stripped);
    return members.stream()
        .filter(member -> member.getParameters().size() == 1)
        .filter(member -> !member.equals(setter))
        .filter(
            member -> ProcessorUtils.capitalise(member.getSimpleName().toString()).equals(property))
        .anyMatch(
            member ->
                ProcessorUtils.takesAnyCollection(
                    types, elements, parameterOf(types, called, member)));
  }

  /**
   * Whether the builder spells a method other than {@code setter} after {@code prefix}, as a
   * setterPrefix spells every builder method Lombok writes: the setter's adder among them.
   */
  private static boolean writesAnotherAfter(
      List<ExecutableElement> members, ExecutableElement setter, String prefix) {
    String spelling = Pattern.quote(prefix) + "\\p{Lu}.*";
    return members.stream()
        .filter(member -> !member.equals(setter))
        .anyMatch(member -> member.getSimpleName().toString().matches(spelling));
  }

  /**
   * How far a member is deprecated, as javac reads it, by its annotation or by a javadoc tag a
   * class file keeps: 0 not, 1 deprecated, 2 deprecated for removal, which only the annotation
   * says.
   */
  private static int deprecation(Elements elements, Element member) {
    Deprecated deprecated = member.getAnnotation(Deprecated.class);
    return !elements.isDeprecated(member)
        ? 0
        : deprecated != null && deprecated.forRemoval() ? 2 : 1;
  }

  /** A one-parameter method's parameter, as a call on {@code called} reads it. */
  private static TypeMirror parameterOf(
      Types types, DeclaredType called, ExecutableElement method) {
    return ProcessorUtils.memberOf(types, called, method).getParameterTypes().getFirst();
  }
}
