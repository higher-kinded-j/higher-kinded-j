// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.higherkindedj.optics.processing.external.SpecInterfaceAnalyser;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * Which {@code @ImportOptics} importers wait for a later round.
 *
 * <p>Another annotation processor may write, in the round an importer first appears, a type the
 * importer's generated code would name or the processor would read. Such a type does not resolve
 * until the next round. Read now, the importer would import nothing for it, refuse a copy strategy
 * the type does support, or generate a class naming a type that class cannot see. So an importer
 * waits while any of these, as declared in source, names a type that does not resolve, and is
 * looked at again in the next round:
 *
 * <ul>
 *   <li>a class literal it lists;
 *   <li>a listed class, and a spec's source type: supertypes, the bounds of every type parameter in
 *       scope, and the members a companion or a copy strategy reads, which are the ones neither
 *       static nor private; their supertypes declared in source are read the same way;
 *   <li>the importer itself: supertypes, the bounds of its type parameters, and its abstract
 *       methods with the class literals their annotations carry.
 * </ul>
 *
 * <p>Static members are left alone throughout, since one may name the companion the importer
 * generates, which cannot resolve before it is generated. Only source declarations are asked: a
 * class file naming a type missing from the classpath would wait for a type that never comes, and a
 * source declaration that never resolves is javac's own error.
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
   * Whether an importer names a type that does not resolve yet.
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
        || importer instanceof TypeElement type
            && (declaresUnresolved(
                    type, member -> member.getModifiers().contains(Modifier.ABSTRACT))
                || sourceTypeUnresolved(elements, types, type));
  }

  /** Whether a class-list entry does not resolve, or names a class whose declaration does not. */
  private static boolean listsUnresolved(Elements elements, Types types, AnnotationValue entry) {
    // javac hands over a literal it cannot resolve as something other than a type.
    return !(entry.getValue() instanceof TypeMirror type)
        || structureUnresolved(elements, types, type);
  }

  /**
   * Whether a spec's source type, as its {@code OpticsSpec} clause names it, has a declaration that
   * does not resolve. The copy strategies read its members, and inherited ones with them.
   */
  private static boolean sourceTypeUnresolved(Elements elements, Types types, TypeElement type) {
    DeclaredType clause = SpecInterfaceAnalyser.declaredOpticsSpec(type);
    // A raw clause names no source type, and so none to read.
    return clause != null
        && clause.getTypeArguments().stream()
            .anyMatch(argument -> structureUnresolved(elements, types, argument));
  }

  /**
   * Whether the class or interface a type names, where declared in source, or one of its supertypes
   * declared in source, names a type that does not resolve. Each is read once.
   */
  private static boolean structureUnresolved(Elements elements, Types types, TypeMirror type) {
    Deque<TypeElement> pending = new ArrayDeque<>();
    Set<TypeElement> seen = new HashSet<>();
    Stream.of(types.asElement(type))
        .filter(TypeElement.class::isInstance)
        .map(TypeElement.class::cast)
        .filter(element -> ProcessorUtils.compiledFromSource(elements, element))
        .filter(seen::add)
        .forEach(pending::add);
    while (!pending.isEmpty()) {
      TypeElement declaration = pending.pop();
      if (declaresUnresolved(declaration, WaitingImporters::readFromOutside)) {
        return true;
      }
      supertypesOf(declaration)
          .map(supertype -> (TypeElement) supertype.asElement())
          .filter(supertype -> ProcessorUtils.compiledFromSource(elements, supertype))
          .filter(seen::add)
          .forEach(pending::add);
    }
    return false;
  }

  /** A member a companion or a copy strategy reads: one neither static nor private. */
  private static boolean readFromOutside(Element member) {
    Set<Modifier> modifiers = member.getModifiers();
    return !modifiers.contains(Modifier.STATIC) && !modifiers.contains(Modifier.PRIVATE);
  }

  /** A type's direct superclass and super-interfaces, as the declared types they are. */
  private static Stream<DeclaredType> supertypesOf(TypeElement type) {
    return Stream.concat(Stream.of(type.getSuperclass()), type.getInterfaces().stream())
        .filter(DeclaredType.class::isInstance)
        .map(DeclaredType.class::cast);
  }

  /**
   * Whether a declaration names a type that does not resolve: in its supertypes, the bounds of
   * every type parameter in scope, or the signatures of the members {@code read} selects, together
   * with the class literals their annotations carry.
   */
  private static boolean declaresUnresolved(TypeElement type, Predicate<Element> read) {
    List<? extends Element> members = type.getEnclosedElements().stream().filter(read).toList();
    return Stream.of(
                Stream.of(type.getSuperclass()),
                type.getInterfaces().stream(),
                type.getPermittedSubclasses().stream(),
                ProcessorUtils.typeParametersInScope(type).stream()
                    .flatMap(parameter -> parameter.getBounds().stream()),
                ElementFilter.fieldsIn(members).stream().map(Element::asType),
                Stream.concat(
                        ElementFilter.methodsIn(members).stream(),
                        ElementFilter.constructorsIn(members).stream())
                    .flatMap(WaitingImporters::signature))
            .<TypeMirror>flatMap(Function.identity())
            .anyMatch(WaitingImporters::unresolved)
        || members.stream().anyMatch(WaitingImporters::carriesUnresolvedClass);
  }

  /** The types a method or constructor names: its return, parameters and type-parameter bounds. */
  private static Stream<TypeMirror> signature(ExecutableElement executable) {
    return Stream.of(
            Stream.of(executable.getReturnType()),
            executable.getParameters().stream().map(Element::asType),
            executable.getTypeParameters().stream()
                .flatMap(parameter -> parameter.getBounds().stream()))
        .<TypeMirror>flatMap(Function.identity());
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
   * the bounds of each one in scope are read where it is declared.
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
