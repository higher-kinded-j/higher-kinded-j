// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * Which {@code @ImportOptics} importers wait for a later round.
 *
 * <p>Another annotation processor may write, in the round an importer first appears, a type the
 * importer names: a class it lists, the source type of a spec, or a type either of those declares.
 * Such a type does not resolve until the next round. Read now, the importer would import nothing
 * for it, or generate a class naming a type that class cannot see. So an importer waits while a
 * type the generated code would name, as declared in source, does not resolve, and is looked at
 * again in the next round.
 *
 * <p>Only source declarations are asked. A class file naming a type missing from the classpath
 * would wait for a type that never comes, and javac reports nothing about it; a source declaration
 * that never resolves is javac's own error. Waiting on more than the generated code names therefore
 * delays an importer, and never loses one.
 */
final class WaitingImporters {

  private static final String CLASS_FQN = "java.lang.Class";

  private WaitingImporters() {}

  /**
   * An importer as the rounds hold it: a package or a type, by module and qualified name, since two
   * modules compiled together may declare the same name. It is looked up afresh each round.
   */
  record Key(boolean isPackage, String module, String name) {

    static Key of(Elements elements, Element importer) {
      return new Key(
          importer.getKind() == ElementKind.PACKAGE,
          elements.getModuleOf(importer).getQualifiedName().toString(),
          // An @ImportOptics element is a package or a type, and both are qualified names.
          ((QualifiedNameable) importer).getQualifiedName().toString());
    }

    Element in(Elements elements) {
      Set<? extends Element> named =
          isPackage ? elements.getAllPackageElements(name) : elements.getAllTypeElements(name);
      return named.stream()
          .filter(element -> elements.getModuleOf(element).getQualifiedName().contentEquals(module))
          .findFirst()
          .orElseThrow();
    }
  }

  /**
   * Whether an importer names a type that does not resolve yet: a literal in its class list, a type
   * a listed class declares, or, for a class or interface importer, a type it declares itself.
   *
   * @param elements the element utilities of the compilation
   * @param types the type utilities of the compilation
   * @param importer the element carrying {@code @ImportOptics}
   * @param listed the class literals its annotation lists
   * @return true where the importer should wait for a later round
   */
  static boolean waits(
      Elements elements, Types types, Element importer, List<AnnotationValue> listed) {
    return listed.stream().anyMatch(entry -> listsUnresolved(elements, types, entry))
        || importer instanceof TypeElement type && declaresUnresolved(type, false);
  }

  /**
   * Whether a class-list entry does not resolve, or names a class declared in source whose own
   * declaration does not. A companion writes out the types its class declares, its components,
   * subtypes and wither parameters, so each of them has to resolve first.
   */
  private static boolean listsUnresolved(Elements elements, Types types, AnnotationValue entry) {
    // javac hands over a literal it cannot resolve as something other than a type.
    return !(entry.getValue() instanceof TypeMirror type)
        || types.asElement(type) instanceof TypeElement listed
            && ProcessorUtils.compiledFromSource(elements, listed)
            && declaresUnresolved(listed, true);
  }

  /**
   * Whether a declaration names a type that does not resolve: in its supertypes, the bounds of its
   * type parameters, or its members' signatures. A listed class is read for every member. An
   * importer is read for its abstract methods only, the optics a spec declares, together with the
   * class literals their annotations carry: a spec's static methods may name the class generated
   * from it, which cannot resolve before it is generated.
   *
   * @param type the declaration to read
   * @param everyMember true to read every member, false for the abstract methods alone
   */
  private static boolean declaresUnresolved(TypeElement type, boolean everyMember) {
    List<? extends Element> members =
        type.getEnclosedElements().stream()
            .filter(member -> everyMember || member.getModifiers().contains(Modifier.ABSTRACT))
            .toList();
    return Stream.of(
                Stream.of(type.getSuperclass()),
                type.getInterfaces().stream(),
                type.getPermittedSubclasses().stream(),
                type.getTypeParameters().stream()
                    .flatMap(parameter -> parameter.getBounds().stream()),
                members.stream().flatMap(WaitingImporters::signature))
            .<TypeMirror>flatMap(Function.identity())
            .anyMatch(WaitingImporters::unresolved)
        || members.stream().anyMatch(WaitingImporters::carriesUnresolvedClass);
  }

  /** The types a member's declaration names: a method's return and parameters, a field's type. */
  private static Stream<TypeMirror> signature(Element member) {
    return switch (member) {
      case ExecutableElement executable ->
          Stream.concat(
              Stream.of(executable.getReturnType()),
              executable.getParameters().stream().map(Element::asType));
      case VariableElement field -> Stream.of(field.asType());
      default -> Stream.empty();
    };
  }

  /**
   * Whether an annotation on a member carries a class literal that does not resolve, such as an
   * {@code @InstanceOf} naming a subtype another processor writes.
   */
  private static boolean carriesUnresolvedClass(Element member) {
    return member.getAnnotationMirrors().stream()
        .flatMap(annotation -> annotation.getElementValues().entrySet().stream())
        .filter(value -> namesAClass(value.getKey().getReturnType()))
        .anyMatch(value -> !(value.getValue().getValue() instanceof TypeMirror));
  }

  /** Whether an annotation element is declared as a {@code Class}. */
  private static boolean namesAClass(TypeMirror elementType) {
    return elementType instanceof DeclaredType declared
        && ((TypeElement) declared.asElement()).getQualifiedName().contentEquals(CLASS_FQN);
  }

  /**
   * Whether a type, or a type it is built from, does not resolve. A type variable is not opened:
   * its bounds are read where it is declared.
   */
  private static boolean unresolved(TypeMirror type) {
    return switch (type.getKind()) {
      case ERROR -> true;
      case DECLARED -> {
        DeclaredType declared = (DeclaredType) type;
        yield Stream.concat(
                Stream.of(declared.getEnclosingType()), declared.getTypeArguments().stream())
            .anyMatch(WaitingImporters::unresolved);
      }
      case ARRAY -> unresolved(((ArrayType) type).getComponentType());
      case WILDCARD -> {
        WildcardType wildcard = (WildcardType) type;
        yield Stream.of(wildcard.getExtendsBound(), wildcard.getSuperBound())
            .filter(Objects::nonNull)
            .anyMatch(WaitingImporters::unresolved);
      }
      default -> false;
    };
  }
}
