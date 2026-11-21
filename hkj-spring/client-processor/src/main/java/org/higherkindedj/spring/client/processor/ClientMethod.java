// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client.processor;

import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeVariableName;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;

/**
 * A method the client must implement, as the client inherits it.
 *
 * <p>The declaration gives the method's name, its parameters' names and every annotation. Its types
 * are read as a member of the client, with each type variable of an interface the client extends
 * replaced by the type argument the client gives it: a client extending {@code Repo<UserDto>}
 * implements a {@code get} that returns {@code EitherPath<ApiErr, UserDto>}, not the {@code
 * EitherPath<ApiErr, T>} that {@code Repo} declares. A type variable of the method's own is not the
 * client's to bind and stays a variable, though a bound of it that names one of {@code Repo}'s is
 * read with the client's argument in its place.
 *
 * <p>A client that reaches the method's interface through a raw type has its signature read as the
 * method declares it. javac would read it erased, losing even the types the declaration writes out
 * in full; the processor refuses the method if the declaration names a type variable the raw type
 * leaves unbound.
 *
 * @param element the method as its interface declares it
 * @param signature the method's types as a member of the client, or as the method declares them
 *     where the client reaches it through a raw type
 * @param owner the client interface's type, the instantiation {@code signature} was read under
 * @param rawClause the raw type the client reaches the method's interface through, if it does
 */
record ClientMethod(
    ExecutableElement element,
    ExecutableType signature,
    DeclaredType owner,
    Optional<ClientMethod.RawClause> rawClause) {

  /**
   * A generic interface named without its type arguments on the way from the client to a method's
   * interface.
   *
   * @param writer the interface whose {@code extends} clause names it so
   * @param raw the interface named raw
   */
  record RawClause(TypeElement writer, TypeElement raw) {}

  /**
   * One parameter of a method: its declaration, for its name and annotations, and its type as the
   * client has it.
   */
  record Parameter(VariableElement element, TypeMirror type, DeclaredType owner) {

    String name() {
      return element.getSimpleName().toString();
    }

    /** The type's name, keeping the annotations the declaration wrote on what it binds. */
    TypeName typeName(String targetPackage) {
      return TypeNames.typeNameOf(type, element.asType(), owner, targetPackage);
    }
  }

  /** The interface that declares the method. */
  TypeElement declaring() {
    return (TypeElement) element.getEnclosingElement();
  }

  TypeMirror returnType() {
    return signature.getReturnType();
  }

  /** The return type's name, keeping the annotations the declaration wrote on what it binds. */
  TypeName returnTypeName(String targetPackage) {
    return TypeNames.typeNameOf(returnType(), element.getReturnType(), owner, targetPackage);
  }

  List<Parameter> parameters() {
    List<? extends VariableElement> declared = element.getParameters();
    List<? extends TypeMirror> types = signature.getParameterTypes();
    return IntStream.range(0, declared.size())
        .mapToObj(index -> new Parameter(declared.get(index), types.get(index), owner))
        .toList();
  }

  /**
   * The method's own type parameters, each bound read as the client has it: {@code <X extends T>}
   * on a {@code Repo<T>} the client extends as {@code Repo<UserDto>} is {@code <X extends
   * UserDto>}.
   */
  List<TypeVariableName> typeVariableNames(String targetPackage) {
    List<? extends TypeVariable> variables = signature.getTypeVariables();
    List<? extends TypeParameterElement> declared = element.getTypeParameters();
    return IntStream.range(0, variables.size())
        .mapToObj(
            index ->
                TypeNames.typeVariableOf(
                    variables.get(index), declared.get(index), owner, targetPackage))
        .toList();
  }

  /**
   * Every type the signature writes: its return type, its parameter types, and the bounds of the
   * method's own type parameters.
   */
  List<TypeMirror> written() {
    return Stream.concat(
            Stream.concat(
                Stream.of(signature.getReturnType()), signature.getParameterTypes().stream()),
            signature.getTypeVariables().stream().map(TypeVariable::getUpperBound))
        .toList();
  }
}
