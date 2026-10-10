// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.util;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.WildcardTypeName;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeVariable;
import org.higherkindedj.optics.Prism;

/**
 * The prism factory method for one permitted subtype of a sealed type.
 *
 * <p>{@code @GeneratePrisms} writes this method into a companion class beside the sealed type, and
 * {@code @ImportOptics} writes the same method into a generated class beside the spec. The two
 * differ only in which messager reports a rejection and which annotation the diagnostic names, so
 * they ask for the method here rather than each holding a copy: the wording of the rejection is
 * asserted by tests on both sides, and two copies would let it drift on one.
 */
public final class SubtypePrismGenerator {

  private SubtypePrismGenerator() {}

  /**
   * Builds the prism factory method for a permitted subtype, or reports why it cannot be written.
   *
   * <p>The prism is written in the subtype's vocabulary: its own type parameters, and the sum type
   * as the subtype's own extends/implements clause instantiates it. Reading the sum type's
   * declaration instead would name variables the method never declares. An inner class of a generic
   * class is named under its enclosing class's arguments as well, as {@link #nameUnder} explains.
   *
   * @param messager the round's messager, for a rejection
   * @param tag the annotation tag naming the caller, for the diagnostic
   * @param sumType the sealed type
   * @param subtype the permitted subtype to focus on
   * @param targetPackage the package the prism is written into
   * @return the factory method, or null when the subtype was rejected and an error reported
   */
  public static MethodSpec prismMethodFor(
      Messager messager,
      String tag,
      TypeElement sumType,
      TypeElement subtype,
      String targetPackage) {

    String methodName = ProcessorUtils.toMethodName(subtype.getSimpleName().toString());
    DeclaredType namedSumType = ProcessorUtils.sumTypeAsNamedBy(sumType, subtype);
    TypeName sourceTypeName = ProcessorUtils.typeNameOf(namedSumType, targetPackage);
    if (rejectsUnboundParameter(messager, tag, sumType, subtype, namedSumType)) {
      return null;
    }

    // Every parameter a use of the subtype supplies, its enclosing classes' first. The method
    // declares those the clause binds, which takes in all of the subtype's own, since a free one
    // was rejected above.
    List<TypeParameterElement> inScope = ProcessorUtils.typeParametersInScope(subtype);
    List<TypeParameterElement> declared =
        inScope.stream()
            .filter(parameter -> ProcessorUtils.mentions(namedSumType, parameter))
            .toList();
    if (rejectsBoundOnAFreeParameter(
        messager, tag, sumType, subtype, namedSumType, inScope, declared)) {
      return null;
    }
    TypeName subTypeName = nameUnder((DeclaredType) subtype.asType(), declared, targetPackage);

    ParameterizedTypeName prismTypeName =
        ParameterizedTypeName.get(ClassName.get(Prism.class), sourceTypeName, subTypeName);

    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder(methodName)
            .addJavadoc(
                "Creates a {@link $T} that focuses on the {@link $T} subtype of the {@link $T} sum"
                    + " type.\n\n"
                    + "@return A non-null {@code Prism<$T, $T>}.",
                Prism.class,
                ClassName.get(subtype),
                ClassName.get(sumType),
                sourceTypeName,
                subTypeName)
            // The prism type is written in the subtype's vocabulary, and the method redeclares the
            // type parameters it names with their bounds.
            .addAnnotations(ProcessorUtils.rawTypesSuppression(namedSumType, declared))
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(prismTypeName);

    for (TypeParameterElement typeParameter : declared) {
      methodBuilder.addTypeVariable(ProcessorUtils.typeVariableOf(typeParameter, targetPackage));
    }

    return methodBuilder
        .addStatement(
            "return $T.of(source -> source instanceof $T ? $T.of(($T) source) : $T.empty(), value"
                + " -> value)",
            Prism.class,
            ClassName.get(subtype),
            Optional.class,
            subTypeName,
            Optional.class)
        .build();
  }

  /**
   * The subtype's type as the prism names it: each parameter in {@code declared} by name, and every
   * other an unbounded wildcard, through each class it is an inner class of.
   *
   * <p>An inner class of a generic class is written under its enclosing class's arguments, and left
   * without them it is raw (JLS 4.8) even where it declares no parameters of its own. A permitted
   * subtype's clause binds an enclosing class's parameter only through the sum type's arguments, as
   * {@code Pinned implements Shape<X>} does, and then the prism declares it: {@code <X>
   * Prism<Shape<X>, Shapes<X>.Pinned>}. Where the clause does not, a value of the sum type carries
   * nothing that says which instantiation of the enclosing class it belongs to, so the prism names
   * what the {@code instanceof} establishes, a {@code Circle} of some {@code Shapes}: {@code
   * Prism<Shape, Shapes<?>.Circle>}. javac proves both casts, so neither draws a warning. An
   * {@code @InstanceOf} target is named the same way, by {@code InstanceOfNarrowing}.
   *
   * @param type the subtype's own type, or one of its enclosing types, as {@code asType()} gives
   *     it, so that every argument is a type variable of the class it belongs to
   * @param declared the type parameters the prism method declares
   * @param targetPackage the package the prism is written into
   * @return the name, with a wildcard wherever the clause binds nothing
   */
  private static TypeName nameUnder(
      DeclaredType type, List<TypeParameterElement> declared, String targetPackage) {
    ClassName rawType = ClassName.get((TypeElement) type.asElement());
    List<TypeName> arguments =
        type.getTypeArguments().stream()
            .map(argument -> (TypeParameterElement) ((TypeVariable) argument).asElement())
            .map(
                parameter ->
                    declared.contains(parameter)
                        ? ProcessorUtils.typeVariableOf(parameter, targetPackage)
                        : WildcardTypeName.subtypeOf(Object.class))
            .toList();
    // A static member, like a top-level type, has no enclosing instance type: javac reports a
    // NoType for it, and the element's own name is the whole of it.
    if (type.getEnclosingType() instanceof DeclaredType enclosing
        && nameUnder(enclosing, declared, targetPackage) instanceof ParameterizedTypeName outer) {
      return outer.nestedClass(rawType.simpleName(), arguments);
    }
    return arguments.isEmpty()
        ? rawType
        : ParameterizedTypeName.get(rawType, arguments.toArray(TypeName[]::new));
  }

  /**
   * Reports a permitted subtype the sum type cannot pin, and returns whether it did.
   *
   * <p>A prism narrows by {@code instanceof}, which tests an erasure. Where the subtype's clause
   * binds every one of its parameters - {@code Circle<T> implements Shape<T>} - the hierarchy pins
   * them and the cast is one javac proves. A parameter the clause leaves free is pinned by nothing,
   * so two callers can read one value at different types and the second gets a {@link
   * ClassCastException} from a call site that compiled without a warning.
   *
   * @param messager the round's messager
   * @param tag the annotation tag, for the diagnostic
   * @param sumType the sealed type
   * @param subtype the permitted subtype
   * @param namedSumType the sum type as the subtype's clause names it
   * @return true when the subtype was rejected and an error reported
   */
  private static boolean rejectsUnboundParameter(
      Messager messager,
      String tag,
      TypeElement sumType,
      TypeElement subtype,
      DeclaredType namedSumType) {

    List<String> unbound =
        subtype.getTypeParameters().stream()
            .filter(parameter -> !ProcessorUtils.mentions(namedSumType, parameter))
            .map(parameter -> parameter.getSimpleName().toString())
            .toList();
    if (unbound.isEmpty()) {
      return false;
    }
    Diagnostics.error(
        messager,
        subtype,
        tag,
        "'"
            + subtype.getSimpleName()
            + "' declares "
            + unbound
            + ", which '"
            + sumType.getSimpleName()
            + "' does not bind.",
        "A prism narrows by instanceof, which tests an erasure, so only what the clause pins is"
            + " checked; a free parameter lets two callers read one value at different types, and"
            + " the second gets a ClassCastException from a call site that compiled cleanly.",
        bindingFix(sumType, namedSumType, unbound));
    return true;
  }

  /**
   * Reports a parameter the prism declares whose bound names an enclosing class's parameter the
   * clause leaves free, and returns whether it did.
   *
   * <p>The free parameter is written as a wildcard, so there is nothing for the bound to name. In
   * {@code Shapes<X>}, {@code Box<Y extends X> implements Shape<Y>} would need {@code <Y extends
   * X>} on a method that declares no {@code X}, and javac refuses {@code Shapes<?>.Box<Y>} for a
   * {@code Y} bounded by anything other than that wildcard's capture.
   *
   * @param messager the round's messager
   * @param tag the annotation tag, for the diagnostic
   * @param sumType the sealed type
   * @param subtype the permitted subtype
   * @param namedSumType the sum type as the subtype's clause names it
   * @param inScope every type parameter a use of the subtype supplies
   * @param declared those the prism method declares
   * @return true when the subtype was rejected and an error reported
   */
  private static boolean rejectsBoundOnAFreeParameter(
      Messager messager,
      String tag,
      TypeElement sumType,
      TypeElement subtype,
      DeclaredType namedSumType,
      List<TypeParameterElement> inScope,
      List<TypeParameterElement> declared) {

    List<TypeParameterElement> free =
        inScope.stream().filter(parameter -> !declared.contains(parameter)).toList();
    for (TypeParameterElement parameter : declared) {
      List<String> named =
          free.stream()
              .filter(
                  other ->
                      parameter.getBounds().stream()
                          .anyMatch(bound -> ProcessorUtils.mentions(bound, other)))
              .map(other -> other.getSimpleName().toString())
              .toList();
      if (!named.isEmpty()) {
        Diagnostics.error(
            messager,
            subtype,
            tag,
            // The bound is rendered as javac reads it: an inner class named in it carries its
            // enclosing class's arguments, which is where an X the source never spelt comes from.
            "The prism for '"
                + subtype.getSimpleName()
                + "' declares "
                + parameter.getSimpleName()
                + " extends "
                + parameter.getBounds().stream()
                    .map(ProcessorUtils::simpleTypeName)
                    .collect(Collectors.joining(" & "))
                + ", and '"
                + sumType.getSimpleName()
                + "' does not bind "
                + named
                + ".",
            "The prism writes a wildcard wherever the clause leaves a parameter free, so "
                + parameter.getSimpleName()
                + "'s bound has nothing to name.",
            bindingFix(sumType, namedSumType, named));
        return true;
      }
    }
    return false;
  }

  /**
   * The fix line both refusals share: bind each parameter left free in the clause, shown as the
   * clause's own arguments with the free ones after them. A raw clause has room for them already,
   * so adding a parameter to the sum type is offered only for those it has no room for.
   *
   * @param sumType the sealed type
   * @param namedSumType the sum type as the subtype's clause names it
   * @param free the names the clause leaves free
   * @return the fix sentence
   */
  private static String bindingFix(
      TypeElement sumType, DeclaredType namedSumType, List<String> free) {
    String arguments =
        Stream.concat(
                namedSumType.getTypeArguments().stream().map(ProcessorUtils::simpleTypeName),
                free.stream())
            .collect(Collectors.joining(", "));
    return "Bind "
        + free
        + " in the clause, as '"
        + sumType.getSimpleName()
        + "<"
        + arguments
        + ">', giving '"
        + sumType.getSimpleName()
        + "' a type parameter for each one it has no room for, or write the prism by hand.";
  }
}
