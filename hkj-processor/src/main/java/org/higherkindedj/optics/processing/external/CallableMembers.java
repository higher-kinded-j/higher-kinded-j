// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.higherkindedj.optics.processing.util.Diagnostics;
import org.higherkindedj.optics.processing.util.NamingConvention;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * The methods of a type a generated optic can call, whether it can name the type or write a call to
 * its constructor at all, and how a diagnostic names and offers those methods.
 *
 * <p>A generated class calls only what it can reach from the package it is generated into, so the
 * copy strategies' checks read a type's methods through {@link #of}, or hold each to {@link
 * #callable}, and read through the accessor a call binds, {@link #accessorNamed}, as javac chooses
 * it. A refusal names a method as the type it is called on reads it, {@link #signatureOn}, and
 * offers neither a static method, nor one of {@code Object}'s, nor one whose call would warn,
 * {@link #offerable}; a missing accessor is reported here, {@link #reportMissingAccessor}. {@link
 * #constructorUnwritable} says where no constructor call can rebuild a type at all.
 *
 * <p>Split from {@link CopyStrategyChecks}, so that its checks, {@link SetterChecks} and {@link
 * BuilderChainChecks} read a type's methods alike. {@link ConstructorOrderChecks}, {@link
 * CollectionClear} and {@link RefusalWording} use its static readings.
 */
final class CallableMembers {

  private final Types typeUtils;
  private final Elements elementUtils;
  private final Messager messager;

  /**
   * Creates the reading over a round's utilities.
   *
   * @param typeUtils the round's type utilities; must not be null
   * @param elementUtils the round's element utilities; must not be null
   * @param messager where a missing accessor is reported; must not be null
   */
  CallableMembers(Types typeUtils, Elements elementUtils, Messager messager) {
    this.typeUtils = typeUtils;
    this.elementUtils = elementUtils;
    this.messager = messager;
  }

  /**
   * The methods of a type the generated class can call, in the order {@code getAllMembers} reads
   * them.
   */
  List<ExecutableElement> of(TypeElement type, String targetPackage) {
    return ElementFilter.methodsIn(elementUtils.getAllMembers(type)).stream()
        .filter(member -> callable(member, targetPackage))
        .toList();
  }

  /** The constructors of a type the generated class can call. */
  List<ExecutableElement> constructorsOf(TypeElement type, String targetPackage) {
    return ElementFilter.constructorsIn(type.getEnclosedElements()).stream()
        .filter(constructor -> callable(constructor, targetPackage))
        .toList();
  }

  /**
   * Whether the generated class can call a member of a type, a method or a constructor.
   *
   * <p>The call names no type but the one it is made on, so it is the member's own access that
   * decides, not where it was declared: a {@code public} method a package-private class declares is
   * called through the public type that inherits it, as javac calls it. {@code protected} counts as
   * package access, since a generated companion extends nothing.
   */
  boolean callable(ExecutableElement member, String targetPackage) {
    Set<Modifier> modifiers = member.getModifiers();
    if (modifiers.contains(Modifier.PRIVATE)) {
      return false;
    }
    return modifiers.contains(Modifier.PUBLIC)
        || elementUtils.getPackageOf(member).getQualifiedName().contentEquals(targetPackage);
  }

  /**
   * The zero-parameter instance method named {@code name} that a call made on {@code receiver}
   * binds, or null where the type has none. It is the one javac calls, as {@link CallBinding}
   * repeats the choice: a concrete method beside the interface declarations it implements, as a
   * class's {@code String name()} beside a {@code CharSequence name()}; and among abstract or
   * default ones alone, as an abstract class inherits both of those from two interfaces, the one
   * whose return is most specific. What it reads is what the call passes on.
   */
  static ExecutableElement accessorNamed(
      Types types, DeclaredType receiver, List<ExecutableElement> members, String name) {
    // Only these can bind a call that passes nothing while there is one: javac weighs a
    // variable-arity method only where none is, and a static one cannot share their signature.
    List<ExecutableElement> named =
        members.stream()
            .filter(
                member ->
                    member.getSimpleName().contentEquals(name)
                        && member.getParameters().isEmpty()
                        && !member.getModifiers().contains(Modifier.STATIC))
            .toList();
    if (named.isEmpty()) {
      return null;
    }
    // Where no return is a subtype of every other's, the first is read: returns that are
    // unrelated, which javac reports at the type's declaration, or that only an unchecked
    // conversion or their own type parameters order, which are not compared here.
    return CallBinding.resolve(types, receiver, List.of(), named)
            instanceof CallBinding.Binds(ExecutableElement bound)
        ? bound
        : named.getFirst();
  }

  /**
   * The type the zero-argument method {@code named} hands back, read on {@code receiver}, whether
   * or not the generated class can call it: what it reads decides only how a focus is passed or a
   * remedy is worded, and a getter out of reach is refused on its own. Only a primitive read is
   * kept, and a primitive return cannot sit beside another for one signature, so the first is the
   * one {@link #accessorNamed} would choose.
   */
  Optional<TypeMirror> zeroArgumentReturn(
      DeclaredType receiver, TypeElement receiverElement, String named) {
    return ElementFilter.methodsIn(elementUtils.getAllMembers(receiverElement)).stream()
        .filter(candidate -> candidate.getSimpleName().contentEquals(named))
        .filter(candidate -> candidate.getParameters().isEmpty())
        .map(candidate -> ProcessorUtils.memberTypeOf(typeUtils, receiver, candidate))
        .findFirst();
  }

  /**
   * Returns whether the generated class may name {@code type}.
   *
   * @param type the type the generated cast would name
   * @param targetPackage the package the optics class is generated into
   * @return true if {@code type} is public, or package-private in the generated class's own package
   */
  boolean isVisibleFrom(TypeElement type, String targetPackage) {
    for (Element enclosing = type; enclosing instanceof TypeElement nested; ) {
      if (!nested.getModifiers().contains(Modifier.PUBLIC)) {
        return elementUtils.getPackageOf(type).getQualifiedName().contentEquals(targetPackage);
      }
      enclosing = nested.getEnclosingElement();
    }
    return true;
  }

  /**
   * Whether no constructor call can rebuild a source type: one written with a wildcard type
   * argument, which the call would have to name; an inner class, which needs an enclosing instance;
   * or an abstract class or an interface.
   */
  static boolean constructorUnwritable(DeclaredType sourceType) {
    TypeElement element = (TypeElement) sourceType.asElement();
    return writtenWithAWildcard(sourceType) || innerClass(element) || abstractType(element);
  }

  /** Whether a type is written with a wildcard among its own type arguments. */
  static boolean writtenWithAWildcard(DeclaredType type) {
    return type.getTypeArguments().stream()
        .anyMatch(argument -> argument.getKind() == TypeKind.WILDCARD);
  }

  /**
   * Whether a class carries an enclosing instance: only a member type that is not static does; a
   * nested interface, enum or record is implicitly static.
   */
  static boolean innerClass(TypeElement element) {
    return element.getNestingKind() == NestingKind.MEMBER
        && !element.getModifiers().contains(Modifier.STATIC);
  }

  /** Whether a type cannot be instantiated: an interface is abstract too, as javac reads it. */
  static boolean abstractType(TypeElement element) {
    return element.getModifiers().contains(Modifier.ABSTRACT);
  }

  /** The names of a type's instance methods the generated class can call with no argument. */
  List<String> accessorNames(TypeElement type, String targetPackage) {
    return namesOf(
        of(type, targetPackage).stream()
            .filter(member -> member.getParameters().isEmpty() && offerable(member, type))
            .toList());
  }

  /**
   * Whether a diagnostic may offer {@code member}: an instance method, and none of {@code
   * Object}'s, {@code wait} or a record's {@code hashCode}, which is never what a spec meant. Nor
   * one deprecated, by its annotation or by a javadoc tag a class file keeps, whose call the
   * generated file would warn of, as a build under {@code -Werror} refuses: a protobuf map field's
   * {@code getPoints()}, beside {@code getPointsMap()}.
   */
  boolean offerable(ExecutableElement member, TypeElement type) {
    return !member.getModifiers().contains(Modifier.STATIC)
        && !elementUtils.isDeprecated(member)
        && ElementFilter.methodsIn(
                elementUtils.getTypeElement("java.lang.Object").getEnclosedElements())
            .stream()
            .noneMatch(
                method -> method.equals(member) || elementUtils.overrides(member, method, type));
  }

  /** The names of {@code methods}, once each, sorted. */
  static List<String> namesOf(List<ExecutableElement> methods) {
    return methods.stream()
        .map(member -> member.getSimpleName().toString())
        .distinct()
        .sorted()
        .toList();
  }

  /** A diagnostic's offer of the name likely meant, as a sentence that follows the reason. */
  static String didYouMean(Optional<String> near) {
    return near.map(name -> " Did you mean '" + name + "'?").orElse("");
  }

  /** Whether none of {@code methods} takes one argument. */
  static boolean takesNoSingleArgument(List<ExecutableElement> methods) {
    return methods.stream().noneMatch(member -> member.getParameters().size() == 1);
  }

  /**
   * Reports an accessor a generated optic calls and the type does not have.
   *
   * @param method the annotated lens method, for error reporting
   * @param tag the strategy annotation, which the message names
   * @param owner the type the call is made on
   * @param ownerElement that type's element, whose members are searched
   * @param name the method the annotation names
   * @param purpose what the generated optic calls it for, as a verb phrase
   * @param on what the call is made on, as the reason names it
   * @param fix the remedy, since only some of the strategies carry an attribute to point at
   * @param offered the method to offer before any the name is a near miss of, or empty
   * @param targetPackage the package the optics class is generated into
   */
  void reportMissingAccessor(
      ExecutableElement method,
      String tag,
      TypeMirror owner,
      TypeElement ownerElement,
      String name,
      String purpose,
      String on,
      String fix,
      Optional<String> offered,
      String targetPackage) {
    String ownerName = ProcessorUtils.simpleTypeName(owner);
    Diagnostics.error(
        messager,
        method,
        tag,
        "'"
            + ownerName
            + "' has no method '"
            + name
            + "()' for the generated lens to "
            + purpose
            + ".",
        "The generated lens calls '"
            + name
            + "()' on "
            + on
            + ", so it needs a zero-parameter instance method of that name that the generated"
            + " class in '"
            + targetPackage
            + "' can call."
            + didYouMean(
                offered.or(
                    () ->
                        NamingConvention.ACCESSOR.nearest(
                            name, accessorNames(ownerElement, targetPackage)))),
        fix);
  }

  /**
   * A method as the type it is called on reads it, {@code withId(String)}, telling overloads apart.
   */
  String signatureOn(DeclaredType receiver, ExecutableElement member) {
    List<String> names =
        parametersAsWritten(
            ProcessorUtils.memberOf(typeUtils, receiver, member).getParameterTypes(),
            member.isVarArgs());
    return member.getSimpleName()
        + String.join(", ", names).transform(joined -> "(" + joined + ")");
  }

  /**
   * A method as its type declares it, {@code withX(T)}, for the overloads a call cannot choose
   * between: two that one instantiation makes alike, {@code withX(T)} and {@code withX(String)}
   * over a {@code W<String>}, still read apart.
   */
  static String signatureAsDeclared(ExecutableElement member) {
    return member.getSimpleName()
        + String.join(
                ", ",
                parametersAsWritten(
                    member.getParameters().stream().map(VariableElement::asType).toList(),
                    member.isVarArgs()))
            .transform(joined -> "(" + joined + ")");
  }

  /**
   * Parameter types as their author wrote them, for a diagnostic: the last of a variable-arity
   * method or constructor, an array to the compiler, as {@code T...}.
   */
  static List<String> parametersAsWritten(
      List<? extends TypeMirror> parameters, boolean variableArity) {
    return IntStream.range(0, parameters.size())
        .mapToObj(
            at -> {
              String name = ProcessorUtils.simpleTypeName(parameters.get(at));
              return variableArity && at == parameters.size() - 1
                  ? name.substring(0, name.length() - "[]".length()) + "..."
                  : name;
            })
        .toList();
  }
}
