// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.util;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeVariableName;
import com.palantir.javapoet.WildcardTypeName;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
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
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;

/**
 * Shared utility methods for annotation processors in the optics module.
 *
 * <p>The helpers here read the type model and derive names. They live together so that a subtlety
 * settled for one processor is settled for all of them.
 */
public final class ProcessorUtils {

  private ProcessorUtils() {
    // Utility class - prevent instantiation
  }

  /**
   * Resolves a wildcard type to its effective type for focus extraction.
   *
   * <ul>
   *   <li>{@code ? extends T} → {@code T} (upper bound)
   *   <li>{@code ? super T} → {@code null} (caller should treat as Object)
   *   <li>{@code ?} (unbounded) → {@code null} (caller should treat as Object)
   * </ul>
   *
   * <p>If the type is not a wildcard, it is returned unchanged.
   *
   * @param type the type to resolve
   * @return the resolved type, or null if the wildcard should be treated as Object
   * @since 0.4.0
   */
  public static TypeMirror resolveWildcard(TypeMirror type) {
    if (type instanceof WildcardType wildcard) {
      TypeMirror extendsBound = wildcard.getExtendsBound();
      if (extendsBound != null) {
        return extendsBound;
      }
      // ? super T or unbounded ? — caller should use Object
      return null;
    }
    return type;
  }

  /**
   * The type a type argument stands for, boxed: the argument itself, the bound of an {@code ?
   * extends} wildcard, or {@code Object} for an unbounded or super-bounded wildcard, which stands
   * for no type of its own.
   *
   * <p>A generated method focuses on this type, and writes it out wherever an explicit type
   * argument is needed, because the wildcard the declaration wrote can be neither.
   *
   * @param typeArgument a type argument as written
   * @return the type it stands for, boxed
   * @since 0.4.11
   */
  public static TypeName resolvedTypeNameOf(TypeMirror typeArgument) {
    TypeMirror resolved = resolveWildcard(typeArgument);
    return resolved == null ? ClassName.get(Object.class) : typeNameOf(resolved).box();
  }

  /**
   * The supertype of {@code type} declared by {@code target}, instantiated with the type arguments
   * it is reached by.
   *
   * <p>The instantiation is the point: {@code Box<X> extends Base<X>} reached from {@code Box<U>}
   * answers {@code Base<U>}, which is the type a generated cast has to name and the type a match
   * against the source has to be made against. {@code type} itself counts as a match, so a caller
   * asking for a type's own element gets it back under its own arguments.
   *
   * @param typeUtils the round's type utilities; must not be null
   * @param type the declared type to search from; must not be null
   * @param target the declaring element to look for; must not be null
   * @return the instantiated supertype, or null when {@code target} does not declare one
   * @since 0.4.10
   */
  public static TypeMirror supertypeOf(Types typeUtils, TypeMirror type, TypeElement target) {
    Name targetName = target.getQualifiedName();
    Deque<TypeMirror> queue = new ArrayDeque<>();
    Set<String> seen = new HashSet<>();
    queue.add(type);
    while (!queue.isEmpty()) {
      TypeMirror current = queue.poll();
      if (!seen.add(current.toString())) {
        continue;
      }
      // Callers search from a type they have already resolved to a TypeElement, and every
      // supertype of a declared type is itself declared.
      TypeElement element = (TypeElement) ((DeclaredType) current).asElement();
      if (element.getQualifiedName().contentEquals(targetName)) {
        return current;
      }
      queue.addAll(typeUtils.directSupertypes(current));
    }
    return null;
  }

  /**
   * Whether a member, and every type enclosing it, is visible from a generated class's package.
   *
   * <p>Generated code names what it calls; a member it cannot see is a compile error in a file its
   * author never wrote. {@code protected} counts as package access here, because a generated
   * companion extends nothing — being a subclass is never how it reaches anything.
   *
   * @param elements the round's element utilities
   * @param member the member or type to test
   * @param targetPackage the package the generated class is written into
   * @return true when the member and every type enclosing it can be named from there
   * @since 0.4.10
   */
  public static boolean reachableFrom(Elements elements, Element member, String targetPackage) {
    for (Element current = member;
        current.getKind() != ElementKind.PACKAGE;
        current = current.getEnclosingElement()) {
      Set<Modifier> modifiers = current.getModifiers();
      if (modifiers.contains(Modifier.PRIVATE)) {
        return false;
      }
      if (!modifiers.contains(Modifier.PUBLIC)
          && !elements.getPackageOf(current).getQualifiedName().contentEquals(targetPackage)) {
        return false;
      }
    }
    return true;
  }

  /**
   * The first type named anywhere inside {@code type} that a generated class in {@code
   * targetPackage} could not name, or null when every one of them is reachable.
   *
   * <p>Written out, a type names more than its own head: {@code Iso<Box<Secret>, String>} names
   * {@code Secret}, and a field declaring it does not compile wherever {@code Secret} cannot be
   * seen. So the walk descends through type arguments, array components, wildcard bounds and
   * enclosing types, the same layers a type variable can hide in.
   *
   * @param elements the round's element utilities
   * @param type the type the generated source will write out
   * @param targetPackage the package the generated class is written into
   * @return the first unreachable type element, or null
   * @since 0.4.10
   */
  public static TypeElement firstUnreachableIn(
      Elements elements, TypeMirror type, String targetPackage) {
    switch (type.getKind()) {
      case ARRAY -> {
        return firstUnreachableIn(elements, ((ArrayType) type).getComponentType(), targetPackage);
      }
      case WILDCARD -> {
        WildcardType wildcard = (WildcardType) type;
        for (TypeMirror bound :
            new TypeMirror[] {wildcard.getExtendsBound(), wildcard.getSuperBound()}) {
          if (bound != null) {
            TypeElement unreachable = firstUnreachableIn(elements, bound, targetPackage);
            if (unreachable != null) {
              return unreachable;
            }
          }
        }
        return null;
      }
      case DECLARED -> {
        DeclaredType declared = (DeclaredType) type;
        TypeElement element = (TypeElement) declared.asElement();
        if (!reachableFrom(elements, element, targetPackage)) {
          return element;
        }
        for (TypeMirror argument : declared.getTypeArguments()) {
          TypeElement unreachable = firstUnreachableIn(elements, argument, targetPackage);
          if (unreachable != null) {
            return unreachable;
          }
        }
        return firstUnreachableIn(elements, declared.getEnclosingType(), targetPackage);
      }
      default -> {
        return null;
      }
    }
  }

  /**
   * Whether a type is written raw: a generic element named with no arguments at all.
   *
   * <p>Raw, not merely bare. A non-generic type has no arguments either and is not raw, and the
   * difference matters because a raw supertype erases every member of the type below it, whatever
   * that member declares.
   *
   * @param declared the type as it was written; must not be null
   * @return true when its element declares type parameters and the type supplies none
   * @since 0.4.10
   */
  public static boolean isRaw(DeclaredType declared) {
    return !((TypeElement) declared.asElement()).getTypeParameters().isEmpty()
        && declared.getTypeArguments().isEmpty();
  }

  /**
   * The first raw type named anywhere in {@code type}, or null when none is raw.
   *
   * <p>A generic type written without its arguments, at any depth: a bare {@code List} head, a
   * {@code List} inside an argument or a wildcard bound, a {@code List[]} component. The enclosing
   * link counts too: {@code Outer.Inner} is raw in {@code Outer} even where {@code Inner} declares
   * nothing of its own (JLS 4.8); an absent or static enclosing type is a {@code NoType}, which
   * ends the walk. A caller that copies the type verbatim into generated source asks this first,
   * because every raw name it copies is a {@code [rawtypes]} warning in the file it lands in.
   *
   * <p>An intersection is not walked: a type parameter's bounds are read from {@code getBounds()},
   * which flattens them into one bound per type, never from {@code getUpperBound()}, which hands
   * back a single intersection mirror this answers "no raw" for.
   *
   * @param type the type as it was written
   * @return the element of the first raw type named, or null
   * @since 0.4.11
   */
  public static TypeElement firstRawIn(TypeMirror type) {
    switch (type.getKind()) {
      case ARRAY -> {
        return firstRawIn(((ArrayType) type).getComponentType());
      }
      case WILDCARD -> {
        // A bound is written out with the wildcard that carries it, so `? extends List` puts a
        // raw List in the generated file as surely as a bare one does.
        WildcardType wildcard = (WildcardType) type;
        for (TypeMirror bound :
            new TypeMirror[] {wildcard.getExtendsBound(), wildcard.getSuperBound()}) {
          if (bound != null) {
            TypeElement raw = firstRawIn(bound);
            if (raw != null) {
              return raw;
            }
          }
        }
        return null;
      }
      case DECLARED -> {
        DeclaredType declared = (DeclaredType) type;
        if (isRaw(declared)) {
          return (TypeElement) declared.asElement();
        }
        for (TypeMirror argument : declared.getTypeArguments()) {
          TypeElement raw = firstRawIn(argument);
          if (raw != null) {
            return raw;
          }
        }
        return firstRawIn(declared.getEnclosingType());
      }
      default -> {
        return null;
      }
    }
  }

  /**
   * The annotations a generated member adds for the raw types it writes out or infers:
   * {@code @SuppressWarnings("rawtypes")} when one of its types names a raw type, none otherwise.
   *
   * <p>The raw type is the author's: it warns where they declared it, and their own suppression
   * answers for it there. A generated file is a separate compilation unit that suppression does not
   * reach, so a member restating the type, or holding a lambda whose parameter javac infers to it,
   * answers for it itself. Only {@code rawtypes}: an unchecked operation in generated code is a
   * hole in that code, never the author's to accept.
   *
   * <p>Suppression is the answer where a generated member merely restates a type its author
   * declared. Where the generated code would do more than restate it, inferring an optic instance
   * from it or rebuilding through it unchecked, the shape is refused instead, as
   * {@code @ImportOptics} does for a raw source type. A generator may also refuse for a reason of
   * its own: {@code @PathVia} refuses a raw type in a bridged signature because the bridge builds
   * its Path from the effect's type arguments, while answering for a raw bound on its class.
   *
   * <p>A declaration takes one {@code @SuppressWarnings}, so a member needing a second token for a
   * reason of its own merges both into a single annotation rather than adding another.
   *
   * @param types the types the member may write out or infer; a wider set only widens where the
   *     suppression lands; must not be null
   * @return the suppression when {@link #firstRawIn} finds a raw type in any of them, else no
   *     annotations
   * @since 0.4.11
   */
  public static List<AnnotationSpec> rawTypesSuppression(List<? extends TypeMirror> types) {
    return types.stream().anyMatch(type -> firstRawIn(type) != null)
        ? List.of(
            AnnotationSpec.builder(SuppressWarnings.class)
                .addMember("value", "$S", "rawtypes")
                .build())
        : List.of();
  }

  /**
   * The {@code @SuppressWarnings("rawtypes")} annotations for a generated member that redeclares
   * {@code owner}'s type parameters as well as naming a type of its own: it writes those bounds out
   * too, so a raw bound reaches it even where the type it is generated around names none.
   *
   * <p>Callers pass the type the member is generated around, not the one it finally writes. Which
   * of the two a generator emits, a component or the element it widens to, is that generator's
   * detail, and asking the wider question only widens where the suppression lands.
   *
   * @param written the type the member is generated around; must not be null
   * @param owner the type whose type parameters the member redeclares; must not be null
   * @return the suppression when the type or one of those bounds names a raw type, else no
   *     annotations
   * @since 0.4.11
   */
  public static List<AnnotationSpec> rawTypesSuppression(TypeMirror written, TypeElement owner) {
    return rawTypesSuppression(written, owner.getTypeParameters());
  }

  /**
   * The {@code @SuppressWarnings("rawtypes")} annotations for a generated member that redeclares
   * the given type parameters as well as naming a type of its own. It serves a member that
   * redeclares more than one type's parameters, such as those {@link #typeParametersInScope}
   * returns.
   *
   * @param written the type the member is generated around; must not be null
   * @param redeclared the type parameters the member redeclares, bounds and all; must not be null
   * @return the suppression when the type or one of those bounds names a raw type, else no
   *     annotations
   * @since 0.4.11
   */
  public static List<AnnotationSpec> rawTypesSuppression(
      TypeMirror written, List<? extends TypeParameterElement> redeclared) {
    return rawTypesSuppression(
        Stream.concat(
                Stream.of(written),
                redeclared.stream().flatMap(parameter -> parameter.getBounds().stream()))
            .toList());
  }

  /**
   * The type parameters a use of {@code type} has to supply, outermost first: those of each class
   * it is an inner class of, then its own.
   *
   * <p>An inner class is written under its enclosing class's arguments, {@code Outer<X>.Line}, and
   * left without them it is raw (JLS 4.8) even when it declares no parameters of its own. A static
   * member, like a top-level type, has no enclosing instance type, so it contributes its own
   * parameters alone. The walk follows {@code getEnclosingType}, as {@link #firstRawIn} does.
   *
   * @param type the type a generated member names; must not be null
   * @return the parameters in scope for it, possibly empty; unmodifiable
   * @since 0.4.11
   */
  public static List<TypeParameterElement> typeParametersInScope(TypeElement type) {
    return typeParametersNamedBy((DeclaredType) type.asType());
  }

  private static List<TypeParameterElement> typeParametersNamedBy(DeclaredType declared) {
    TypeMirror enclosing = declared.getEnclosingType();
    Stream<TypeParameterElement> outer =
        enclosing.getKind() == TypeKind.DECLARED
            ? typeParametersNamedBy((DeclaredType) enclosing).stream()
            : Stream.empty();
    return Stream.concat(outer, ((TypeElement) declared.asElement()).getTypeParameters().stream())
        .toList();
  }

  /**
   * Whether a declared type carries an instantiation for {@code asMemberOf} to substitute.
   *
   * <p>Asked before reading a member under a type, because {@code asMemberOf} does the wrong thing
   * at both ends of the question. Where there is nothing to substitute it can still lose what the
   * member declares: under a raw site javac erases every member, so a field typed {@code
   * List<String>} on a raw {@code Holder} comes back as {@code List} and matches no container.
   * Where there is something to substitute, skipping it hands back a variable the caller never
   * wrote.
   *
   * <p>The enclosing chain counts, not just the type's own arguments. A member of {@code
   * Outer<List<String>>.Holder} speaks {@code Outer}'s variables even though {@code Holder}
   * declares none of its own, and a chain that ends without arguments anywhere is either wholly
   * non-generic or raw - in both of which the declaration was already the answer.
   *
   * <p>This asks about the <em>site's own</em> arguments, which is not the same question as whether
   * substitution has anything to do. A member inherited from {@code Emails<Email>} is substituted
   * under a non-generic spec, whose chain carries no arguments at all; guarding that read with this
   * would read the declaration and defeat the substitution. Use it where the member is declared on
   * the site itself.
   *
   * @param type the instantiated type a member is about to be read under; must not be null
   * @return true when some link of its enclosing chain carries type arguments
   * @since 0.4.10
   */
  public static boolean carriesInstantiation(DeclaredType type) {
    TypeMirror current = type;
    while (current instanceof DeclaredType declared) {
      if (!declared.getTypeArguments().isEmpty()) {
        return true;
      }
      current = declared.getEnclosingType();
    }
    return false;
  }

  /**
   * The sum type as one of its permitted subtypes instantiates it.
   *
   * <p>A prism for a subtype is written against the sum type the <em>subtype</em> names, not the
   * sum type's own declaration: {@code GenCircle<T> implements GenShape<T>} focuses {@code
   * GenShape<T>}, while {@code Tagged implements GenShape<String>} focuses {@code GenShape<String>}
   * and needs no parameter of its own.
   *
   * @param sumType the sealed type; must not be null
   * @param subtype the permitted subtype whose clause names it; must not be null
   * @return the sum type as {@code subtype} names it, or the sum type itself when the clause does
   *     not resolve and javac's own error is the one worth reading
   * @since 0.4.10
   */
  public static DeclaredType sumTypeAsNamedBy(TypeElement sumType, TypeElement subtype) {
    // Only the implemented interfaces: a sealed type reached here is an interface, so a subtype
    // that is permitted by it names it there. A superclass could never be the match.
    for (TypeMirror candidate : subtype.getInterfaces()) {
      // Cast, not a pattern: an implements clause yields declared types only - an unresolvable one
      // is an ErrorType, which is a DeclaredType too - so there is no other kind to test for.
      DeclaredType declared = (DeclaredType) candidate;
      if (declared.asElement().equals(sumType)) {
        return declared;
      }
    }
    return (DeclaredType) sumType.asType();
  }

  /**
   * A method's return type as the given owner sees it.
   *
   * @param types the round's type utilities; must not be null
   * @param owner the instantiated type the method is read on; must not be null
   * @param method the method to read; must not be null
   * @return the return type under {@code owner}'s instantiation
   * @since 0.4.10
   */
  public static TypeMirror returnTypeIn(Types types, DeclaredType owner, ExecutableElement method) {
    return memberOf(types, owner, method).getReturnType();
  }

  /**
   * A method's first parameter type as the given owner sees it.
   *
   * @param types the round's type utilities; must not be null
   * @param owner the instantiated type the method is read on; must not be null
   * @param method the method to read, which must take at least one parameter; must not be null
   * @return the first parameter's type under {@code owner}'s instantiation
   * @since 0.4.10
   */
  public static TypeMirror firstParameterTypeIn(
      Types types, DeclaredType owner, ExecutableElement method) {
    return memberOf(types, owner, method).getParameterTypes().getFirst();
  }

  /**
   * Whether a method, read on {@code owner}, hands back an {@code owner}: a wither a generated lens
   * rebuilds through has to, since the lens returns what it returns as the owner.
   *
   * <p>Two shapes do. A return that is {@code owner} or a subtype of it, read under {@code owner}'s
   * instantiation. And a retag, {@code <U> Draft<U> withId(String)} on a {@code Draft<T>}: the
   * owner's class with some arguments replaced by type variables the method declares, which the
   * call infers back to the owner's own. That inference is repeated here. Each such variable is
   * bound to the owner's argument where the return first names it against a concrete argument; a
   * wildcard argument binds nothing, since the call reads it as a range rather than a type. Every
   * bound of each variable then has to admit its argument with all of them in place, since a bound
   * may name another ({@code <A, B extends A>}) or the variable itself ({@code <U extends
   * Comparable<? super U>>}), and the return with them in place has to be the owner. A variable
   * that only a wildcard stands for is left unbound, which refuses a retag javac might still infer.
   * A raw return, one under other arguments ({@code Draft<String>}) and a supertype hand back
   * something else.
   *
   * @param types the round's type utilities; must not be null
   * @param owner the type the method is read on, as the generated code names it; must not be null
   * @param method the method to read; must not be null
   * @return true when its return is an {@code owner}, or infers to one
   * @since 0.4.11
   */
  public static boolean returnsOwner(Types types, DeclaredType owner, ExecutableElement method) {
    return types.isSubtype(returnTypeIn(types, owner, method), owner)
        || retagsOwner(types, owner, method);
  }

  private static boolean retagsOwner(Types types, DeclaredType owner, ExecutableElement method) {
    // The call reads the method on its receiver's captured type, where each of the owner's
    // wildcards is a type of its own, and binds the method's variables against the owner as
    // written, which is what the generated lens hands its result back as.
    TypeMirror returned = returnTypeIn(types, (DeclaredType) types.capture(owner), method);
    // asElement answers null for a primitive or void return, which equals no element.
    if (!owner.asElement().equals(types.asElement(returned))) {
      return false;
    }
    List<? extends TypeMirror> returnedArguments = ((DeclaredType) returned).getTypeArguments();
    List<? extends TypeMirror> ownerArguments = owner.getTypeArguments();
    Map<Element, TypeMirror> inferred = new HashMap<>();
    List<TypeVariable> variables = new ArrayList<>();
    for (int i = 0; i < returnedArguments.size(); i++) {
      TypeMirror argument = returnedArguments.get(i);
      boolean bindable =
          argument.getKind() == TypeKind.TYPEVAR
              && method.getTypeParameters().contains(((TypeVariable) argument).asElement())
              && ownerArguments.get(i).getKind() != TypeKind.WILDCARD;
      if (bindable) {
        TypeVariable variable = (TypeVariable) argument;
        // The first binding stands, as it does for inference; a later place is checked, not bound.
        if (inferred.putIfAbsent(variable.asElement(), ownerArguments.get(i)) == null) {
          variables.add(variable);
        }
      }
    }
    if (inferred.isEmpty()) {
      return false;
    }
    return variables.stream().allMatch(variable -> admits(types, variable, inferred))
        && types.isSubtype(substitute(types, returned, inferred), owner);
  }

  /**
   * Whether every bound of {@code variable} admits the argument it is bound to, read with every
   * inferred variable in place. The bounds are read off the return rather than the declaration, so
   * a bound naming the owner's own parameters is read under the owner's instantiation.
   */
  private static boolean admits(
      Types types, TypeVariable variable, Map<Element, TypeMirror> inferred) {
    TypeMirror argument = inferred.get(variable.asElement());
    return boundsOf(variable).stream()
        .allMatch(bound -> types.isSubtype(argument, substitute(types, bound, inferred)));
  }

  /**
   * {@code type} with each type variable {@code inferred} names replaced by what it stands for.
   *
   * <p>A type that names none of them is handed back as it is, never rebuilt, so a shape the
   * builders would reject, such as a wildcard nested in a wildcard's bound, is left alone. One that
   * names one is a type variable, an array, a wildcard or a declared type, and is rebuilt around
   * its substituted parts, the enclosing type included.
   */
  private static TypeMirror substitute(
      Types types, TypeMirror type, Map<Element, TypeMirror> inferred) {
    if (inferred.keySet().stream().noneMatch(variable -> mentions(type, variable))) {
      return type;
    }
    return switch (type.getKind()) {
      case TYPEVAR -> inferred.get(((TypeVariable) type).asElement());
      case ARRAY ->
          types.getArrayType(substitute(types, ((ArrayType) type).getComponentType(), inferred));
      case WILDCARD -> {
        WildcardType wildcard = (WildcardType) type;
        yield types.getWildcardType(
            substituteBound(types, wildcard.getExtendsBound(), inferred),
            substituteBound(types, wildcard.getSuperBound(), inferred));
      }
      default -> {
        // The one shape left that can name a variable, since a bound's intersection arrives
        // split into its arms.
        DeclaredType declared = (DeclaredType) type;
        TypeMirror[] arguments =
            declared.getTypeArguments().stream()
                .map(argument -> substitute(types, argument, inferred))
                .toArray(TypeMirror[]::new);
        TypeMirror enclosing = substitute(types, declared.getEnclosingType(), inferred);
        TypeElement element = (TypeElement) declared.asElement();
        yield enclosing.getKind() == TypeKind.DECLARED
            ? types.getDeclaredType((DeclaredType) enclosing, element, arguments)
            : types.getDeclaredType(element, arguments);
      }
    };
  }

  private static TypeMirror substituteBound(
      Types types, TypeMirror bound, Map<Element, TypeMirror> inferred) {
    return bound == null ? null : substitute(types, bound, inferred);
  }

  /**
   * The member as the owner sees it.
   *
   * <p>Cast, not a fallback: {@code asMemberOf} answers with an {@link ExecutableType} for an
   * executable member, and a member read as declared where a substitution was wanted is the very
   * defect these helpers close - better to fail than to quietly return it.
   *
   * <p>A member reached through a <em>raw</em> supertype comes back erased, which is what the
   * language says a raw type's members are. Nothing is done to soften that: reading the declaration
   * instead lets analysis pass and leaves the generator emitting a call the erased member cannot
   * take.
   *
   * <p>That is this helper's policy, not the only one in the processor, and the difference is
   * deliberate rather than drift. A caller that must not erase guards the call itself, because what
   * a raw site should produce is the caller's question:
   *
   * <ul>
   *   <li>{@code SpecInterfaceAnalyser.memberTypeOf} guards with {@link #carriesInstantiation} and
   *       reads the declaration under a raw site. Erasing there rejected a container the spec had
   *       written, which is the {@code @ThroughField} regression #738 caught. A raw source type is
   *       now refused at the spec's declaration (#771), so the site the guard reads as declared
   *       today is a non-generic one.
   *   <li>{@code MappingProcessor.componentType} asks whether the record <em>declares</em>
   *       parameters rather than whether the site supplies them, so that a concrete pair never
   *       relies on {@code asMemberOf} accepting a record component. A raw domain cannot reach it:
   *       {@code @GenerateMapping} refuses one at the declaration.
   * </ul>
   *
   * <p>Settled under #740: the three readers are not near-copies to be merged. Consolidating them
   * would have to pick one raw-site answer, and they want different ones.
   *
   * @param types the round's type utilities; must not be null
   * @param owner the instantiated type the member is read on; must not be null
   * @param member the member to read; must not be null
   * @return the member's signature under {@code owner}'s instantiation
   * @since 0.4.10
   */
  public static ExecutableType memberOf(Types types, DeclaredType owner, ExecutableElement member) {
    return (ExecutableType) types.asMemberOf(owner, member);
  }

  /**
   * The bounds a type variable is written with, as whoever reads it sees them.
   *
   * <p>Kind, not {@code instanceof}: an intersection type implements {@link DeclaredType} too, so a
   * pattern would take the first arm of {@code A & B} for the whole bound. Read off a variable
   * rather than a {@link javax.lang.model.element.TypeParameterElement} so that a member read under
   * an instantiation reports the bounds it has <em>there</em> - an inherited {@code <R extends T>}
   * is {@code <R extends String>} under {@code Base<String>}, and {@code T} is a name the reader
   * cannot write.
   *
   * @param variable the type variable to read; must not be null
   * @return its upper bound, or the arms of that bound when it is an intersection
   * @since 0.4.10
   */
  public static List<? extends TypeMirror> boundsOf(TypeVariable variable) {
    TypeMirror upperBound = variable.getUpperBound();
    return upperBound.getKind() == TypeKind.INTERSECTION
        ? ((IntersectionType) upperBound).getBounds()
        : List.of(upperBound);
  }

  /**
   * Whether a type is the given type parameter, or names it at any depth.
   *
   * <p>A parameter can hide in more places than a type argument. {@code Outer<T>.Inner} names
   * {@code T} through its enclosing type, {@code List<? extends T>} through a wildcard bound,
   * {@code T[]} through an array component, and {@code Foo & Bar<T>} through one arm of an
   * intersection. An enclosing type that is absent, or a static member type, is a {@code NoType},
   * which matches nothing and ends the walk.
   *
   * @param type the type to search; must not be null
   *     <p>A type variable is a leaf: this answers whether the variable is named, not what its own
   *     bound goes on to name, so a self-referential type terminates.
   * @param parameter the element of the type parameter to look for; must not be null
   * @return true when {@code type} names {@code parameter}
   * @since 0.4.10
   */
  public static boolean mentions(TypeMirror type, Element parameter) {
    return switch (type) {
      case TypeVariable variable -> variable.asElement().equals(parameter);
      // Before DeclaredType: javac's intersection implements that interface, so the other order
      // sends an intersection down the declared arm, where it reports no arguments and no
      // enclosing type, and every bound it names is missed.
      case IntersectionType intersection ->
          intersection.getBounds().stream().anyMatch(bound -> mentions(bound, parameter));
      case DeclaredType declared ->
          mentions(declared.getEnclosingType(), parameter)
              || declared.getTypeArguments().stream().anyMatch(a -> mentions(a, parameter));
      case ArrayType array -> mentions(array.getComponentType(), parameter);
      case WildcardType wildcard ->
          (wildcard.getExtendsBound() != null && mentions(wildcard.getExtendsBound(), parameter))
              || (wildcard.getSuperBound() != null
                  && mentions(wildcard.getSuperBound(), parameter));
      default -> false;
    };
  }

  /**
   * The name of a type as written, with its type-use annotations kept.
   *
   * <p>{@link TypeName#get(TypeMirror)} rebuilds a name from the element alone and never consults
   * {@link TypeMirror#getAnnotationMirrors()} at any depth, so every type-use annotation on the
   * declaration is dropped on the way into generated source. That is not merely a loss of
   * information: inside a {@code @NullMarked} scope an unannotated type <em>means</em> non-null, so
   * a dropped {@code @Nullable} asserts the opposite of what the author wrote. The scope is the
   * consumer's to set - a {@code package-info} or {@code module-info} carrying {@code @NullMarked}
   * covers generated files in that package as surely as an annotation the generator stamps itself -
   * so a generator cannot know that its output is unconstrained.
   *
   * <p>The walk mirrors javapoet's own, re-attaching each mirror's annotations as it goes, so an
   * annotation is kept wherever it was written: on the type itself, on a type argument at any
   * depth, on an array's component or on the array, and on a wildcard bound. A type read with
   * {@link Types#asMemberOf} has already lost the annotations written on a type variable's use;
   * name one of those with {@link #typeNameOf(TypeMirror, TypeMirror, DeclaredType, String)}.
   *
   * <p>An annotation is kept only where some generated file could write it cleanly. One javac could
   * not resolve, which is one missing from the compile classpath, one private anywhere in its
   * nesting, and one deprecated anywhere in its nesting are left off: the first two fail the build
   * compiling the generated file, and the third draws a warning there that no one can suppress. A
   * package-private one is kept: it can be written from its own package, which is where a generated
   * file lands unless a target package says otherwise. A generator that knows its package names
   * with {@link #typeNameOf(TypeMirror, String)}, which decides exactly.
   *
   * @param type the type to name; must not be null
   * @return its name, annotated as the source annotated it (non-null)
   * @since 0.4.10
   */
  public static TypeName typeNameOf(TypeMirror type) {
    return new Naming(Map.of(), annotation -> writableSomewhere(annotation.getAnnotationType()))
        .name(type, type);
  }

  /**
   * {@link #typeNameOf(TypeMirror)} for a file written into {@code targetPackage}: an annotation is
   * kept only where that package can write it cleanly, resolved, with every type in its nesting
   * public or declared in that package, and none of them private or deprecated.
   *
   * @param type the type to name; must not be null
   * @param targetPackage the package the generated file is written into; must not be null
   * @return its name, annotated as the source annotated it wherever the file can say so (non-null)
   * @since 0.4.11
   */
  public static TypeName typeNameOf(TypeMirror type, String targetPackage) {
    return new Naming(
            Map.of(), annotation -> writableFrom(annotation.getAnnotationType(), targetPackage))
        .name(type, type);
  }

  /**
   * The name of a member's type read under an owner, with the type-use annotations its declaration
   * wrote kept, for a file written into {@code targetPackage}.
   *
   * <p>{@link Types#asMemberOf} replaces each type variable a member's type names with what the
   * owner binds it to, and javac's substitution drops any annotation written on that use of the
   * variable: {@code ValidatedPrism<String, @Nullable T>} comes back as {@code
   * ValidatedPrism<String, T>}, even under the declaring type itself, where {@code T} is bound to
   * {@code T}. An annotation on a variable's use applies to whatever replaces the variable, so this
   * walks the substituted type beside the declared one and writes each such annotation onto the
   * replacement, unless the replacement already carries one of the same annotation type.
   *
   * <p>The variable may be bound on the way, by a supertype clause between the member and the
   * owner: under {@code Mid<M> extends NameLeaf<@Nullable M>}, the member's {@code N} is Mid's
   * {@code @Nullable M} before it is the owner's anything. The walk follows the owner's
   * superinterface clauses as written, from the variable to what each clause binds it to, and keeps
   * what each of them wrote. Where the declaration and the substituted type part company, below a
   * replaced variable, it goes on with the substituted type alone. Annotations are kept as {@link
   * #typeNameOf(TypeMirror, String)} keeps them.
   *
   * @param type the member's type as the owner has it, {@code asMemberOf}'s answer; must not be
   *     null
   * @param declared the same type as the member declares it, such as its own {@code
   *     getReturnType()}; must not be null
   * @param owner the instantiation the member was read under; must not be null
   * @param targetPackage the package the generated file is written into; must not be null
   * @return its name, annotated as the source annotated it wherever the file can say so (non-null)
   * @since 0.4.11
   */
  public static TypeName typeNameOf(
      TypeMirror type, TypeMirror declared, DeclaredType owner, String targetPackage) {
    return new Naming(
            clausesOf(owner),
            annotation -> writableFrom(annotation.getAnnotationType(), targetPackage))
        .name(type, declared);
  }

  /**
   * Whether some generated file can write this annotation type cleanly: resolved, and neither
   * private nor deprecated anywhere in its nesting.
   */
  private static boolean writableSomewhere(DeclaredType annotationType) {
    return writable(annotationType, element -> true);
  }

  /**
   * Whether a generated file in {@code targetPackage} can write this annotation type cleanly:
   * resolved, and every type in its nesting public or declared in that package, and neither private
   * nor deprecated.
   */
  private static boolean writableFrom(DeclaredType annotationType, String targetPackage) {
    return writable(
        annotationType,
        element ->
            element.getModifiers().contains(Modifier.PUBLIC)
                || packageOf(element).equals(targetPackage));
  }

  private static boolean writable(DeclaredType annotationType, Predicate<Element> visible) {
    if (annotationType.getKind() == TypeKind.ERROR) {
      return false;
    }
    for (Element current = annotationType.asElement();
        current.getKind() != ElementKind.PACKAGE;
        current = current.getEnclosingElement()) {
      if (current.getModifiers().contains(Modifier.PRIVATE)
          || current.getAnnotation(Deprecated.class) != null
          || !visible.test(current)) {
        return false;
      }
    }
    return true;
  }

  private static String packageOf(Element element) {
    Element current = element;
    while (current.getKind() != ElementKind.PACKAGE) {
      current = current.getEnclosingElement();
    }
    return ((PackageElement) current).getQualifiedName().toString();
  }

  /**
   * What the owner, and each superinterface clause below it as written, binds each type parameter
   * to: the owner's own arguments, then every clause reached from its type, the first binding of a
   * parameter kept. A variable bound to itself, as the declaring type's own instantiation binds
   * each, leads nowhere and is left out; so is a raw clause, which binds nothing.
   */
  private static Map<Element, TypeMirror> clausesOf(DeclaredType owner) {
    Map<Element, TypeMirror> clauses = new HashMap<>();
    bindClause(owner, clauses);
    bindSupertypeClauses((TypeElement) owner.asElement(), new HashSet<>(), clauses);
    return clauses;
  }

  private static void bindSupertypeClauses(
      TypeElement type, Set<TypeElement> visited, Map<Element, TypeMirror> clauses) {
    if (!visited.add(type)) {
      return;
    }
    for (TypeMirror parent : type.getInterfaces()) {
      DeclaredType clause = (DeclaredType) parent;
      bindClause(clause, clauses);
      bindSupertypeClauses((TypeElement) clause.asElement(), visited, clauses);
    }
  }

  private static void bindClause(DeclaredType clause, Map<Element, TypeMirror> clauses) {
    List<? extends TypeParameterElement> parameters =
        ((TypeElement) clause.asElement()).getTypeParameters();
    List<? extends TypeMirror> arguments = clause.getTypeArguments();
    if (arguments.size() != parameters.size()) {
      return;
    }
    for (int index = 0; index < parameters.size(); index++) {
      TypeMirror argument = arguments.get(index);
      if (!(argument instanceof TypeVariable variable
          && variable.asElement().equals(parameters.get(index)))) {
        clauses.putIfAbsent(parameters.get(index), argument);
      }
    }
  }

  /**
   * One naming walk: the supertype clauses a variable may be bound through, and which annotations
   * the file being written can carry.
   */
  private record Naming(
      Map<Element, TypeMirror> clauses, Predicate<? super AnnotationMirror> writable) {

    /**
     * The declaration's counterpart of {@code type}, where it is of the same kind, or {@code type}
     * itself where the two part company: below a variable nothing binds, the declaration has no
     * parts of its own, and the walk goes on with the substituted type alone.
     */
    private static TypeMirror sameKindOr(TypeMirror declared, TypeMirror type) {
      return declared.getKind() == type.getKind() ? declared : type;
    }

    TypeName name(TypeMirror type, TypeMirror declared) {
      Map<Element, AnnotationMirror> annotations = new LinkedHashMap<>();
      type.getAnnotationMirrors()
          .forEach(
              annotation ->
                  annotations.putIfAbsent(annotation.getAnnotationType().asElement(), annotation));
      // A variable's use keeps what was written on it, and so does each clause binding it on the
      // way to the owner; the last of them is what the substituted type stands in for.
      TypeMirror twin = declared;
      while (twin.getKind() == TypeKind.TYPEVAR) {
        twin.getAnnotationMirrors()
            .forEach(
                annotation ->
                    annotations.putIfAbsent(
                        annotation.getAnnotationType().asElement(), annotation));
        TypeMirror bound = clauses.get(((TypeVariable) twin).asElement());
        if (bound == null) {
          break;
        }
        twin = bound;
      }
      List<AnnotationSpec> specs =
          annotations.values().stream().filter(writable).map(AnnotationSpec::get).toList();
      // Dispatch on the kind, as javapoet's own visitor does, rather than on the interface: javac's
      // intersection implements DeclaredType, so a pattern switch would send one down the declared
      // arm and ask it for a class element it does not have. Everything this does not rebuild -
      // a primitive, a type variable, void, and the kinds that have no name at all - javapoet
      // names from the mirror alone, and it stays javapoet's call which of those it refuses.
      TypeName name =
          switch (type.getKind()) {
            case ARRAY -> arrayNameOf((ArrayType) type, twin);
            case WILDCARD -> wildcardNameOf((WildcardType) type, twin);
            case DECLARED, ERROR -> declaredNameOf((DeclaredType) type, twin);
            default -> TypeName.get(type);
          };
      return specs.isEmpty() ? name : name.annotated(specs);
    }

    private TypeName arrayNameOf(ArrayType array, TypeMirror declared) {
      ArrayType twin = (ArrayType) sameKindOr(declared, array);
      return ArrayTypeName.of(name(array.getComponentType(), twin.getComponentType()));
    }

    private TypeName declaredNameOf(DeclaredType declared, TypeMirror written) {
      // The declaration corresponds part for part only where it names the same shape: a raw read
      // of a generic type drops the arguments.
      DeclaredType sameKind = (DeclaredType) sameKindOr(written, declared);
      DeclaredType twin =
          sameKind.getTypeArguments().size() == declared.getTypeArguments().size()
              ? sameKind
              : declared;
      ClassName rawType = ClassName.get((TypeElement) declared.asElement());
      TypeMirror enclosingType = declared.getEnclosingType();
      // A static member has no enclosing instance type, so javac reports NONE for it and the kind
      // test alone settles both cases.
      TypeName enclosing =
          enclosingType.getKind() == TypeKind.NONE
              ? null
              : name(enclosingType, twin.getEnclosingType());
      List<? extends TypeMirror> arguments = declared.getTypeArguments();
      List<? extends TypeMirror> twinArguments = twin.getTypeArguments();
      List<TypeName> argumentNames =
          IntStream.range(0, arguments.size())
              .mapToObj(index -> name(arguments.get(index), twinArguments.get(index)))
              .toList();
      if (enclosing instanceof ParameterizedTypeName parameterised) {
        return parameterised.nestedClass(rawType.simpleName(), argumentNames);
      }
      // An annotation on the enclosing type is written before it - `@Marker Outer.Inner` annotates
      // Outer, not Inner - and ClassName.get(element) names the whole nesting from the element
      // alone, so it carries none of it. Rebuilding the name under the enclosing keeps what was
      // written there; for an unannotated enclosing it reproduces the same name.
      if (enclosing instanceof ClassName enclosingName) {
        rawType = enclosingName.nestedClass(rawType.simpleName());
      }
      return argumentNames.isEmpty()
          ? rawType
          : ParameterizedTypeName.get(rawType, argumentNames.toArray(new TypeName[0]));
    }

    private TypeName wildcardNameOf(WildcardType wildcard, TypeMirror declared) {
      // A substituted wildcard keeps the declared one's kind, so an extends bound pairs with an
      // extends bound and a super bound with a super bound.
      WildcardType twin = (WildcardType) sameKindOr(declared, wildcard);
      TypeMirror extendsBound = wildcard.getExtendsBound();
      if (extendsBound != null) {
        return WildcardTypeName.subtypeOf(name(extendsBound, twin.getExtendsBound()));
      }
      TypeMirror superBound = wildcard.getSuperBound();
      return superBound == null
          ? WildcardTypeName.subtypeOf(ClassName.OBJECT)
          : WildcardTypeName.supertypeOf(name(superBound, twin.getSuperBound()));
    }
  }

  /**
   * The declaration of a type parameter, with the annotations on its bounds kept.
   *
   * <p>{@link TypeVariableName#get(TypeParameterElement)} names each bound through {@link
   * TypeName#get(TypeMirror)}, which drops the annotation, and then removes any bound that is bare
   * {@code Object}. Between them {@code <T extends @Nullable Object>} becomes {@code <T>}, which is
   * the narrower declaration: the generated type no longer admits an instantiation the type it
   * wraps permits.
   *
   * <p>Naming the bounds through {@link #typeNameOf(TypeMirror)} is enough to fix both halves. An
   * annotated {@code Object} is not equal to the bare one, so it survives the removal that the bare
   * bound is still rightly subject to.
   *
   * <p>The bounds are all that is copied. An annotation written on the parameter itself, as in
   * {@code <@Marker T>}, is left behind: one {@code TypeVariableName} both declares a parameter and
   * is written wherever that parameter is named, and a generator reuses the same one for both. An
   * annotation that is legal on the declaration need not be legal at a use - a {@code
   * TYPE_PARAMETER} one is rejected outright as a type argument - so carrying it would emit source
   * the consuming build cannot compile. Nothing is lost for nullness: JSpecify states a nullable
   * parameter as {@code <T extends @Nullable Object>}, which is a bound.
   *
   * @param parameter the type parameter to name; must not be null
   * @return its name, with its bounds annotated as the source annotated them (non-null)
   * @since 0.4.10
   */
  public static TypeVariableName typeVariableOf(TypeParameterElement parameter) {
    TypeName[] bounds =
        parameter.getBounds().stream().map(ProcessorUtils::typeNameOf).toArray(TypeName[]::new);
    return TypeVariableName.get(parameter.getSimpleName().toString(), bounds);
  }

  /**
   * {@link #typeVariableOf(TypeParameterElement)} for a file written into {@code targetPackage},
   * its bounds named as {@link #typeNameOf(TypeMirror, String)} names a type.
   *
   * @param parameter the type parameter to name; must not be null
   * @param targetPackage the package the generated file is written into; must not be null
   * @return its name, with its bounds annotated wherever the file can say so (non-null)
   * @since 0.4.11
   */
  public static TypeVariableName typeVariableOf(
      TypeParameterElement parameter, String targetPackage) {
    TypeName[] bounds =
        parameter.getBounds().stream()
            .map(bound -> typeNameOf(bound, targetPackage))
            .toArray(TypeName[]::new);
    return TypeVariableName.get(parameter.getSimpleName().toString(), bounds);
  }

  /**
   * Renders a type for a diagnostic, with package qualifiers dropped and type arguments spaced.
   *
   * <p>Type arguments and enclosing types are kept, so {@code java.util.List<java.lang.String>}
   * reads as {@code List<String>} and {@code com.external.Outer.Inner} as {@code Outer.Inner}. A
   * diagnostic that offers a corrected declaration needs both: a rendering that drops either one
   * suggests source that does not compile.
   *
   * <p>A declared type, array, wildcard, type variable or primitive is built from its element,
   * resolved arguments or kind rather than {@code toString()}, so a type-use annotation never
   * enters: {@code @Nullable Set<?>} reads as {@code Set<?>}, the same way the corrected
   * declaration beside it is rendered. The annotation is not what a diagnostic about the type is
   * about, and a message that prints it in one half and drops it in the other reads as advice to
   * remove it (#759). The remaining kinds, an unresolvable type or an intersection, render from
   * {@code toString()} with annotations stripped. Generated source is the opposite decision: {@link
   * #typeNameOf} keeps type-use annotations, because there the annotation is part of the source
   * being emitted (#750).
   *
   * @param type the type to render; must not be null
   * @return the rendered name (non-null)
   * @since 0.4.10
   */
  public static String simpleTypeName(TypeMirror type) {
    return switch (type.getKind()) {
      case DECLARED -> declaredName((DeclaredType) type);
      case ARRAY -> simpleTypeName(((ArrayType) type).getComponentType()) + "[]";
      case WILDCARD -> wildcardName((WildcardType) type);
      case TYPEVAR -> ((TypeVariable) type).asElement().getSimpleName().toString();
      // The kind names the type on its own; toString would carry any type-use annotation.
      case BOOLEAN, BYTE, SHORT, INT, LONG, CHAR, FLOAT, DOUBLE ->
          type.getKind().name().toLowerCase(Locale.ROOT);
      // The lossy pre-#759 renderer, for what remains: an unresolvable type, whose element walk
      // loses the qualifier on a nested name that the string keeps, and an intersection, which a
      // structural arm would have to render bound by bound. Annotations are stripped, argument
      // lists and all.
      default ->
          stripAnnotations(type.toString())
              .replaceAll("\\b(?:[a-z][\\p{Alnum}_]*\\.)+", "")
              .replace(",", ", ");
    };
  }

  /**
   * Strips annotation tokens from a type's string form, for the shapes that render through {@code
   * toString()}.
   *
   * <p>A scan rather than a pattern, because an annotation argument may contain the very characters
   * a pattern would stop at: a bracket or a quote inside a string ({@code @Marker(")")}) or a
   * nested annotation ({@code @Outer(@Inner(1))}). The scan tracks parenthesis depth and string and
   * character literals, escapes included. A dot directly before the annotation goes with it: javac
   * prints an annotated type as {@code enclosing.@Anno Simple}, and keeps the dot even where the
   * enclosing is empty.
   *
   * @param rendered the type's string form; must not be null
   * @return the string with annotation tokens and their trailing spaces removed (non-null)
   */
  static String stripAnnotations(String rendered) {
    StringBuilder out = new StringBuilder();
    int i = 0;
    while (i < rendered.length()) {
      char c = rendered.charAt(i);
      if (c == '@') {
        if (!out.isEmpty() && out.charAt(out.length() - 1) == '.') {
          out.setLength(out.length() - 1);
        }
        i = skipAnnotation(rendered, i);
      } else {
        out.append(c);
        i++;
      }
    }
    return out.toString();
  }

  /** Consumes one annotation token starting at the {@code '@'}, returning the next index. */
  private static int skipAnnotation(String rendered, int at) {
    int i = at + 1;
    while (i < rendered.length()
        && (Character.isLetterOrDigit(rendered.charAt(i))
            || rendered.charAt(i) == '_'
            || rendered.charAt(i) == '.')) {
      i++;
    }
    if (i < rendered.length() && rendered.charAt(i) == '(') {
      int depth = 0;
      char quote = 0;
      do {
        char c = rendered.charAt(i);
        if (quote != 0) {
          if (c == '\\') {
            i++;
          } else if (c == quote) {
            quote = 0;
          }
        } else if (c == '"' || c == '\'') {
          quote = c;
        } else if (c == '(') {
          depth++;
        } else if (c == ')') {
          depth--;
        }
        i++;
      } while (i < rendered.length() && depth > 0);
    }
    while (i < rendered.length() && rendered.charAt(i) == ' ') {
      i++;
    }
    return i;
  }

  /** The declared type's name: enclosing chain, simple name, and resolved arguments. */
  private static String declaredName(DeclaredType declared) {
    String arguments =
        declared.getTypeArguments().isEmpty()
            ? ""
            : declared.getTypeArguments().stream()
                .map(ProcessorUtils::simpleTypeName)
                .collect(Collectors.joining(", ", "<", ">"));
    return declaredHead(declared) + arguments;
  }

  /**
   * The declared type's name without its arguments: the enclosing chain and the simple name.
   *
   * <p>For a diagnostic that rebuilds the argument list itself, the way {@code @GenerateFocus}
   * suggests a concrete alternative to a wildcard: the head has to keep the nesting the full
   * rendering keeps, or the suggestion names a type that does not compile.
   *
   * @param declared the type whose head to render; must not be null
   * @return the enclosing chain and simple name, without arguments (non-null)
   * @since 0.4.11
   */
  public static String declaredHead(DeclaredType declared) {
    String prefix = "";
    if (declared.getEnclosingType().getKind() == TypeKind.DECLARED) {
      // A member reached through an instance carries the enclosing type, arguments and all:
      // Outer<String>.Holder, or Outer.Holder where the outer level is written raw.
      prefix = simpleTypeName(declared.getEnclosingType()) + ".";
    } else {
      // A static or top-level nesting has a NoType enclosing type, but the enclosing classes
      // still print: Registry.Tag, as the declaration site would write it.
      for (Element outer = declared.asElement().getEnclosingElement();
          outer instanceof TypeElement typeElement;
          outer = typeElement.getEnclosingElement()) {
        prefix = typeElement.getSimpleName() + "." + prefix;
      }
    }
    return prefix + declared.asElement().getSimpleName();
  }

  /** The wildcard as written: bare, extends-bounded or super-bounded. */
  private static String wildcardName(WildcardType wildcard) {
    if (wildcard.getExtendsBound() != null) {
      return "? extends " + simpleTypeName(wildcard.getExtendsBound());
    }
    if (wildcard.getSuperBound() != null) {
      return "? super " + simpleTypeName(wildcard.getSuperBound());
    }
    return "?";
  }

  /**
   * The name the effect's type variable takes in a traversal generated for this record, which the
   * record must not have taken for itself.
   *
   * <p>{@code modifyF} is generated inside a method that carries the record's type variables, so a
   * record declaring its own {@code F} would have the effect shadowed by it, and the traversal
   * would then be written in terms of the wrong one. Both the processor, which declares the
   * variable, and the generators, which write uses of it into the body, read the name from here so
   * that they cannot disagree about it.
   *
   * @param recordElement the annotated record
   * @return {@code F}, or {@code F} followed by the first number the record leaves free
   * @since 0.4.10
   */
  public static String effectVariableName(TypeElement recordElement) {
    return freeTypeVariableName("F", recordElement);
  }

  /**
   * A type-variable name a method generated inside the record's own type variables can declare
   * without shadowing one of them.
   *
   * @param preferred the name to use when the record has not taken it
   * @param recordElement the annotated record
   * @return {@code preferred}, or {@code preferred} followed by the first number the record leaves
   *     free
   * @since 0.4.11
   */
  public static String freeTypeVariableName(String preferred, TypeElement recordElement) {
    Set<String> taken =
        recordElement.getTypeParameters().stream()
            .map(parameter -> parameter.getSimpleName().toString())
            .collect(Collectors.toSet());
    String name = preferred;
    for (int suffix = 1; taken.contains(name); suffix++) {
      name = preferred + suffix;
    }
    return name;
  }

  /**
   * Whether a container's type arguments leave an optic instance composed over it undenotable.
   *
   * <p>An optic handed to a Focus path — {@code .some(Affines.eitherRight())}, {@code
   * .each(EachInstances.mapValuesEach())} — has its own type arguments inferred from the field
   * type. A raw container offers none to infer them from and a wildcard has no ground
   * instantiation, so in either case javac cannot instantiate the optic's own type variables and
   * the composition call does not apply to the path.
   *
   * <p>Only the container's own arguments count: {@code Either<String, ? extends Leaf>} is
   * undenotable, {@code Either<String, List<? extends Leaf>>} is not, because the wildcard there
   * belongs to the {@code List} and {@code Either} still has a ground instantiation.
   *
   * @param type the type to inspect
   * @return true when {@code type} is a declared generic type that is raw or carries a wildcard
   *     type argument
   * @since 0.4.10
   */
  public static boolean hasUndenotableTypeArguments(TypeMirror type) {
    if (type.getKind() != TypeKind.DECLARED) {
      return false;
    }
    DeclaredType declaredType = (DeclaredType) type;
    List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
    if (typeArguments.isEmpty()) {
      // A generic element with no arguments is raw; a non-generic one simply has none to give.
      return isRaw(declaredType);
    }
    return typeArguments.stream().anyMatch(arg -> arg.getKind() == TypeKind.WILDCARD);
  }

  /**
   * Converts a string to camelCase.
   *
   * <p>Handles various input formats:
   *
   * <ul>
   *   <li>SNAKE_CASE: "MY_CONSTANT" → "myConstant"
   *   <li>ALL_CAPS: "MONDAY" → "monday"
   *   <li>PascalCase: "MyClass" → "myClass"
   *   <li>Already camelCase: "myMethod" → "myMethod"
   * </ul>
   *
   * @param s the string to convert
   * @return the camelCase version of the string
   */
  public static String toCamelCase(String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }

    // Handle SNAKE_CASE (with underscores)
    if (s.contains("_")) {
      String[] parts = s.split("_");
      StringBuilder camelCaseString = new StringBuilder(parts[0].toLowerCase(Locale.ROOT));
      for (int i = 1; i < parts.length; i++) {
        if (!parts[i].isEmpty()) {
          camelCaseString
              .append(parts[i].substring(0, 1).toUpperCase(Locale.ROOT))
              .append(parts[i].substring(1).toLowerCase(Locale.ROOT));
        }
      }
      return camelCaseString.toString();
    }

    // Handle ALL_CAPS (no underscores but all uppercase letters)
    if (isAllUpperCase(s)) {
      return s.toLowerCase(Locale.ROOT);
    }

    // Handle PascalCase
    if (Character.isUpperCase(s.charAt(0))) {
      return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    return s;
  }

  /**
   * Checks if a string contains only uppercase letters.
   *
   * <p>Non-letter characters are ignored in the check.
   *
   * @param s the string to check
   * @return true if all letter characters are uppercase, false otherwise
   */
  public static boolean isAllUpperCase(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isLetter(c) && !Character.isUpperCase(c)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Capitalises the first character, locale-neutrally; null and empty inputs pass through.
   *
   * @param s the string, may be null
   * @return the capitalised string, or {@code s} unchanged when null or empty
   */
  public static String capitalise(String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
  }

  /**
   * The modules compiled from source here that declare {@code packageName}, in name order. javac
   * writes a generated file into the module that declares its package, and a filer refuses a file
   * whose package more than one of them declares just as it refuses a name already taken, so a
   * write handler asks this before reporting a refused file as a collision. A module declares the
   * package in source through its {@code package-info.java} as surely as through a type, so either
   * counts.
   *
   * @param elements the compilation's element utilities
   * @param packageName the generated file's package
   * @return the declaring modules' names (non-null; at most one outside a multi-module compilation)
   */
  public static List<String> compiledModulesDeclaring(Elements elements, String packageName) {
    return elements.getAllPackageElements(packageName).stream()
        .filter(
            declared ->
                Stream.concat(
                        Stream.of(declared),
                        ElementFilter.typesIn(declared.getEnclosedElements()).stream())
                    .anyMatch(element -> compiledFromSource(elements, element)))
        .map(declared -> elements.getModuleOf(declared).getQualifiedName().toString())
        .sorted()
        .toList();
  }

  /** Whether an element is read from a source file: a type's own, or a package's package-info. */
  private static boolean compiledFromSource(Elements elements, Element element) {
    return Optional.ofNullable(elements.getFileObjectOf(element))
        .map(JavaFileObject::getKind)
        .filter(JavaFileObject.Kind.SOURCE::equals)
        .isPresent();
  }
}
