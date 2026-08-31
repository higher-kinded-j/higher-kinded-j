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
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
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
   * depth, on an array's component or on the array, and on a wildcard bound.
   *
   * @param type the type to name; must not be null
   * @return its name, annotated as the source annotated it (non-null)
   * @since 0.4.10
   */
  public static TypeName typeNameOf(TypeMirror type) {
    List<AnnotationSpec> annotations =
        type.getAnnotationMirrors().stream().map(AnnotationSpec::get).toList();
    // Dispatch on the kind, as javapoet's own visitor does, rather than on the interface: javac's
    // intersection implements DeclaredType, so a pattern switch would send one down the declared
    // arm and ask it for a class element it does not have. Everything this does not rebuild -
    // a primitive, a type variable, void, and the kinds that have no name at all - javapoet names
    // from the mirror alone, and it stays javapoet's call which of those it refuses.
    TypeName name =
        switch (type.getKind()) {
          case ARRAY -> ArrayTypeName.of(typeNameOf(((ArrayType) type).getComponentType()));
          case WILDCARD -> wildcardNameOf((WildcardType) type);
          case DECLARED, ERROR -> declaredNameOf((DeclaredType) type);
          default -> TypeName.get(type);
        };
    return annotations.isEmpty() ? name : name.annotated(annotations);
  }

  private static TypeName declaredNameOf(DeclaredType declared) {
    ClassName rawType = ClassName.get((TypeElement) declared.asElement());
    TypeMirror enclosingType = declared.getEnclosingType();
    // A static member has no enclosing instance type, so javac reports NONE for it and the kind
    // test alone settles both cases.
    TypeName enclosing =
        enclosingType.getKind() == TypeKind.NONE ? null : typeNameOf(enclosingType);
    List<TypeName> argumentNames =
        declared.getTypeArguments().stream().map(ProcessorUtils::typeNameOf).toList();
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

  private static TypeName wildcardNameOf(WildcardType wildcard) {
    TypeMirror extendsBound = wildcard.getExtendsBound();
    if (extendsBound != null) {
      return WildcardTypeName.subtypeOf(typeNameOf(extendsBound));
    }
    TypeMirror superBound = wildcard.getSuperBound();
    return superBound == null
        ? WildcardTypeName.subtypeOf(ClassName.OBJECT)
        : WildcardTypeName.supertypeOf(typeNameOf(superBound));
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
   * Renders a type for a diagnostic, with package qualifiers dropped and type arguments spaced.
   *
   * <p>Type arguments and enclosing types are kept, so {@code java.util.List<java.lang.String>}
   * reads as {@code List<String>} and {@code com.external.Outer.Inner} as {@code Outer.Inner}. A
   * diagnostic that offers a corrected declaration needs both: a rendering that drops either one
   * suggests source that does not compile.
   *
   * @param type the type to render; must not be null
   * @return the rendered name (non-null)
   * @since 0.4.10
   */
  public static String simpleTypeName(TypeMirror type) {
    return type.toString().replaceAll("\\b(?:[a-z][\\p{Alnum}_]*\\.)+", "").replace(",", ", ");
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
    Set<String> taken =
        recordElement.getTypeParameters().stream()
            .map(parameter -> parameter.getSimpleName().toString())
            .collect(Collectors.toSet());
    String name = "F";
    for (int suffix = 1; taken.contains(name); suffix++) {
      name = "F" + suffix;
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
}
