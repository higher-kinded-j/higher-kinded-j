// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client.processor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Reads the methods a client interface must implement, as the client inherits them, and answers
 * questions about how the client reaches each one. This is javax.lang.model reading alone; the
 * processor decides what an answer means for the client and reports it.
 */
final class ClientMethods {

  private ClientMethods() {
    // Utility class - prevent instantiation
  }

  /**
   * What an {@code extends} clause binds a type variable to, and the interface the clause belongs
   * to.
   */
  private record Binding(TypeElement writer, TypeMirror argument) {}

  /**
   * The abstract methods the generated client must implement, including those inherited from
   * super-interfaces, so a client interface that {@code extends} a shared base still generates a
   * complete native interface and facade. Each is read as a member of the client, so a generic
   * base's type variables come back as the type arguments the client gives it. {@code
   * default}/{@code static}/{@link Object} methods are excluded.
   *
   * <p>Each entry holds every declaration of one method the client implements, grouped by erased
   * signature: {@code getAllMembers} does not collapse the same method declared independently in
   * two unrelated super-interfaces (a diamond), nor two methods of one generic interface that the
   * client's type arguments make the same, and the client implements each only once. The
   * declaration {@code getAllMembers} lists first comes first.
   */
  static List<List<ClientMethod>> inheritedBy(TypeElement client, Elements elements, Types types) {
    DeclaredType owner = (DeclaredType) client.asType();
    // getAllMembers has no guaranteed order across compilers/runs; key the groups by a stable key
    // so the generated sources are deterministic (reproducible builds, no spurious diffs / cache
    // misses).
    Map<String, List<ClientMethod>> groups = new TreeMap<>();
    for (Element member : elements.getAllMembers(client)) {
      if (member.getKind() == ElementKind.METHOD
          && member.getModifiers().contains(Modifier.ABSTRACT)
          && !isObjectMethod(member)) {
        ExecutableElement method = (ExecutableElement) member;
        TypeElement declaring = (TypeElement) method.getEnclosingElement();
        // javac erases a member read through a raw type only where its own interface is generic:
        // a non-generic base reached through a raw interface in between keeps its types.
        Optional<ClientMethod.RawClause> rawClause =
            declaring.getTypeParameters().isEmpty()
                ? Optional.empty()
                : rawClauseTo(client, declaring, types, new HashSet<>());
        // Total: asMemberOf answers with an ExecutableType for a method, and every member here is
        // one the client has, so it can always be read as one of the client's.
        ExecutableType signature =
            rawClause.isPresent()
                ? (ExecutableType) method.asType()
                : (ExecutableType) types.asMemberOf(owner, method);
        ClientMethod clientMethod = new ClientMethod(method, signature, owner, rawClause);
        groups
            .computeIfAbsent(erasedSignature(clientMethod, types), key -> new ArrayList<>())
            .add(clientMethod);
      }
    }
    return groups.values().stream().map(List::copyOf).toList();
  }

  /**
   * A name + erased-parameter-types key, so diamond-inherited duplicates collapse to one method.
   * The parameter types are the client's: {@code get(ID)} in a {@code Repo<ID>} the client extends
   * as {@code Repo<String>} is the same method as another base's {@code get(String)}, though the
   * two declarations erase differently.
   */
  private static String erasedSignature(ClientMethod method, Types types) {
    StringBuilder key = new StringBuilder(method.element().getSimpleName().toString()).append('(');
    for (ClientMethod.Parameter parameter : method.parameters()) {
      key.append(erasureOf(parameter, types)).append(',');
    }
    return key.append(')').toString();
  }

  /**
   * A parameter's erasure as the client has the method.
   *
   * <p>A type variable of the method's own erases to its leftmost bound, but javac rebuilds a bound
   * of interfaces alone with {@code Object} in front when it substitutes inside it, and the rebuilt
   * bound erases to {@code Object}. The leftmost bound is found as {@link TypeNames} pairs bounds,
   * from the end, so {@code <X extends Comparable<T> & Serializable>} still erases to {@code
   * Comparable}.
   */
  static TypeMirror erasureOf(ClientMethod.Parameter parameter, Types types) {
    return erasure(parameter.type(), types);
  }

  private static TypeMirror erasure(TypeMirror type, Types types) {
    if (type.getKind() == TypeKind.TYPEVAR && declaredByMethod((TypeVariable) type)) {
      TypeVariable variable = (TypeVariable) type;
      TypeMirror upperBound = variable.getUpperBound();
      List<? extends TypeMirror> bounds =
          upperBound.getKind() == TypeKind.INTERSECTION
              ? ((IntersectionType) upperBound).getBounds()
              : List.of(upperBound);
      int declared = ((TypeParameterElement) variable.asElement()).getBounds().size();
      return types.erasure(bounds.get(Math.max(bounds.size() - declared, 0)));
    }
    if (type.getKind() == TypeKind.ARRAY) {
      return types.getArrayType(erasure(((ArrayType) type).getComponentType(), types));
    }
    return types.erasure(type);
  }

  private static boolean isObjectMethod(Element method) {
    return method.getEnclosingElement() instanceof TypeElement type
        && type.getQualifiedName().contentEquals("java.lang.Object");
  }

  /**
   * The first generic interface the client names without type arguments on its way to the interface
   * that declares an inherited method, if it names one, with the interface whose clause names it
   * so. A client's own methods are reached through nothing. Each interface is searched once, so a
   * hierarchy of diamonds costs one visit per interface.
   */
  private static Optional<ClientMethod.RawClause> rawClauseTo(
      TypeElement from, TypeElement declaring, Types types, Set<TypeElement> visited) {
    if (!visited.add(from)) {
      return Optional.empty();
    }
    TypeMirror target = types.erasure(declaring.asType());
    for (TypeMirror parent : from.getInterfaces()) {
      if (!types.isSubtype(types.erasure(parent), target)) {
        continue;
      }
      TypeElement extended = (TypeElement) ((DeclaredType) parent).asElement();
      if (isRaw(parent)) {
        return Optional.of(new ClientMethod.RawClause(from, extended));
      }
      Optional<ClientMethod.RawClause> deeper = rawClauseTo(extended, declaring, types, visited);
      if (deeper.isPresent()) {
        return deeper;
      }
    }
    return Optional.empty();
  }

  /** Whether a type names a generic class or interface without its type arguments. */
  private static boolean isRaw(TypeMirror type) {
    return type.getKind() == TypeKind.DECLARED
        && ((DeclaredType) type).getTypeArguments().isEmpty()
        && !((TypeElement) ((DeclaredType) type).asElement()).getTypeParameters().isEmpty();
  }

  /**
   * The interface that wrote the type at one position of a method's return type.
   *
   * <p>Where the method's declaration names a type there, the method's own interface wrote it.
   * Where it names a type variable, the {@code extends} clause that binds the variable wrote what
   * stands in its place, and a clause that binds it to a variable of its own interface hands the
   * question on to the clause that binds that one. A client that binds a base's {@code E} to a type
   * it misspells wrote that type in source, where javac reports it, though the base and its
   * declaration were read from a class file.
   *
   * @param path the type-argument index at each level from the return type to the position,
   *     outermost first
   */
  static TypeElement writerOf(ClientMethod method, List<Integer> path) {
    return writerOf(
        method.element().getReturnType(),
        path,
        method.declaring(),
        bindingsOf((TypeElement) method.owner().asElement()));
  }

  private static TypeElement writerOf(
      TypeMirror type, List<Integer> path, TypeElement writer, Map<Element, Binding> bindings) {
    if (type.getKind() == TypeKind.TYPEVAR) {
      Binding binding = bindings.get(((TypeVariable) type).asElement());
      return binding == null
          ? writer
          : writerOf(binding.argument(), path, binding.writer(), bindings);
    }
    if (path.isEmpty() || type.getKind() != TypeKind.DECLARED) {
      return writer;
    }
    List<? extends TypeMirror> arguments = ((DeclaredType) type).getTypeArguments();
    return path.getFirst() < arguments.size()
        ? writerOf(arguments.get(path.getFirst()), path.subList(1, path.size()), writer, bindings)
        : writer;
  }

  /**
   * What each {@code extends} clause among the client's interfaces, and theirs in turn, binds each
   * type parameter to, the first binding of a parameter kept. A raw clause binds nothing.
   *
   * <p>A clause names only the variables of the interface it belongs to, which a clause nearer the
   * client binds in turn, so following the bindings from any variable ends; {@link #writerOf} needs
   * no guard. The naming walk in {@link TypeNames} reads the same clauses and keeps the guard
   * against a cycle that its original in {@code hkj-processor} has, where a spec's clauses can bind
   * each way round.
   */
  private static Map<Element, Binding> bindingsOf(TypeElement client) {
    Map<Element, Binding> bindings = new HashMap<>();
    bindClauses(client, new HashSet<>(), bindings);
    return Map.copyOf(bindings);
  }

  private static void bindClauses(
      TypeElement type, Set<TypeElement> visited, Map<Element, Binding> bindings) {
    if (!visited.add(type)) {
      return;
    }
    for (TypeMirror parent : type.getInterfaces()) {
      DeclaredType clause = (DeclaredType) parent;
      TypeElement extended = (TypeElement) clause.asElement();
      List<? extends TypeParameterElement> parameters = extended.getTypeParameters();
      List<? extends TypeMirror> arguments = clause.getTypeArguments();
      if (arguments.size() == parameters.size()) {
        for (int index = 0; index < parameters.size(); index++) {
          bindings.putIfAbsent(parameters.get(index), new Binding(type, arguments.get(index)));
        }
      }
      bindClauses(extended, visited, bindings);
    }
  }

  /**
   * The first type variable of an interface that a method's signature names. Read through a raw
   * type, the signature is the declaration, and nothing binds such a variable.
   */
  static Optional<TypeVariable> interfaceVariableIn(ClientMethod method) {
    return method.written().stream()
        .flatMap(ClientMethods::typeVariablesIn)
        .filter(variable -> !declaredByMethod(variable))
        .findFirst();
  }

  /**
   * The client's type parameter that one of the method's own hides, where the method's signature
   * names the client's: the generated client declares both in one scope, where the method's wins. A
   * method that hides a name its signature never uses restates as it is.
   */
  static Optional<TypeParameterElement> hiddenClientParameter(
      ClientMethod method, TypeElement client) {
    List<TypeVariable> named =
        method.written().stream().flatMap(ClientMethods::typeVariablesIn).toList();
    return method.signature().getTypeVariables().stream()
        .map(own -> own.asElement().getSimpleName())
        .flatMap(
            name ->
                client.getTypeParameters().stream()
                    .filter(parameter -> parameter.getSimpleName().contentEquals(name)))
        .filter(
            parameter ->
                named.stream().anyMatch(variable -> variable.asElement().equals(parameter)))
        .map(parameter -> (TypeParameterElement) parameter)
        .findFirst();
  }

  /**
   * Every type variable a type names, at any depth: in its type arguments, its enclosing type, an
   * array's component, a wildcard's bounds and an intersection's arms. A variable's own bounds are
   * not followed, as they belong to its declaration, and a recursive bound such as {@code <X
   * extends Comparable<X>>} would lead back to it.
   */
  private static Stream<TypeVariable> typeVariablesIn(TypeMirror type) {
    return switch (type.getKind()) {
      case TYPEVAR -> Stream.of((TypeVariable) type);
      case DECLARED -> {
        DeclaredType declared = (DeclaredType) type;
        yield Stream.concat(
                Stream.of(declared.getEnclosingType()), declared.getTypeArguments().stream())
            .flatMap(ClientMethods::typeVariablesIn);
      }
      case ARRAY -> typeVariablesIn(((ArrayType) type).getComponentType());
      case WILDCARD -> {
        WildcardType wildcard = (WildcardType) type;
        yield Stream.of(wildcard.getExtendsBound(), wildcard.getSuperBound())
            .filter(Objects::nonNull)
            .flatMap(ClientMethods::typeVariablesIn);
      }
      case INTERSECTION ->
          ((IntersectionType) type).getBounds().stream().flatMap(ClientMethods::typeVariablesIn);
      default -> Stream.empty();
    };
  }

  /** Whether a type variable is one a method declares, rather than a class or an interface. */
  private static boolean declaredByMethod(TypeVariable variable) {
    return ((TypeParameterElement) variable.asElement()).getGenericElement().getKind()
        == ElementKind.METHOD;
  }
}
