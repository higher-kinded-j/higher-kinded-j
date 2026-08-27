// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import java.util.*;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import org.higherkindedj.optics.processing.external.SpecAnalysis.CopyStrategyInfo;
import org.higherkindedj.optics.processing.external.SpecAnalysis.CopyStrategyKind;
import org.higherkindedj.optics.processing.external.SpecAnalysis.OpticKind;
import org.higherkindedj.optics.processing.external.SpecAnalysis.OpticMethodInfo;
import org.higherkindedj.optics.processing.external.SpecAnalysis.PrismHintInfo;
import org.higherkindedj.optics.processing.external.SpecAnalysis.PrismHintKind;
import org.higherkindedj.optics.processing.external.SpecAnalysis.TraversalHintInfo;
import org.higherkindedj.optics.processing.external.SpecAnalysis.TraversalHintKind;
import org.higherkindedj.optics.processing.util.Diagnostics;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * Analyses spec interfaces extending {@code OpticsSpec<S>} to determine what optics to generate.
 *
 * <p>This analyser examines a spec interface and produces a {@link SpecAnalysis} that describes:
 *
 * <ul>
 *   <li>The source type {@code S} from {@code OpticsSpec<S>}
 *   <li>Abstract methods that need optic implementations generated
 *   <li>Annotations and their parsed values for each method
 * </ul>
 *
 * <p>A {@code default} method is rejected: its body cannot be read during annotation processing, so
 * it has no home in the generated class. Composition belongs in a static method or an ordinary
 * utility class that calls the generated statics.
 */
public class SpecInterfaceAnalyser {

  private static final String OPTICS_SPEC_FQN = "org.higherkindedj.optics.annotations.OpticsSpec";
  private static final String OBJECT_FQN = "java.lang.Object";
  private static final String LENS_FQN = "org.higherkindedj.optics.Lens";
  private static final String PRISM_FQN = "org.higherkindedj.optics.Prism";
  private static final String TRAVERSAL_FQN = "org.higherkindedj.optics.Traversal";
  private static final String AFFINE_FQN = "org.higherkindedj.optics.Affine";
  private static final String ISO_FQN = "org.higherkindedj.optics.Iso";
  private static final String GETTER_FQN = "org.higherkindedj.optics.Getter";
  private static final String FOLD_FQN = "org.higherkindedj.optics.Fold";

  private static final String VIA_BUILDER_FQN = "org.higherkindedj.optics.annotations.ViaBuilder";
  private static final String WITHER_FQN = "org.higherkindedj.optics.annotations.Wither";
  private static final String VIA_CONSTRUCTOR_FQN =
      "org.higherkindedj.optics.annotations.ViaConstructor";
  private static final String VIA_COPY_AND_SET_FQN =
      "org.higherkindedj.optics.annotations.ViaCopyAndSet";
  private static final String INSTANCE_OF_FQN = "org.higherkindedj.optics.annotations.InstanceOf";
  private static final String MATCH_WHEN_FQN = "org.higherkindedj.optics.annotations.MatchWhen";
  private static final String TRAVERSE_WITH_FQN =
      "org.higherkindedj.optics.annotations.TraverseWith";
  private static final String THROUGH_FIELD_FQN =
      "org.higherkindedj.optics.annotations.ThroughField";

  private final Types typeUtils;
  private final Elements elementUtils;
  private final Messager messager;

  /**
   * Creates a new SpecInterfaceAnalyser.
   *
   * @param typeUtils the type utilities from the processing environment
   * @param elementUtils the element utilities from the processing environment
   * @param messager the messager for reporting diagnostics
   */
  public SpecInterfaceAnalyser(Types typeUtils, Elements elementUtils, Messager messager) {
    this.typeUtils = typeUtils;
    this.elementUtils = elementUtils;
    this.messager = messager;
  }

  /**
   * Analyses a spec interface to determine what optics to generate.
   *
   * @param specInterface the interface extending {@code OpticsSpec<S>}
   * @param targetPackage the package the optics class is generated into, which decides what the
   *     generated code is allowed to name
   * @return the analysis result, or empty if the interface is invalid
   */
  public Optional<SpecAnalysis> analyse(TypeElement specInterface, String targetPackage) {
    if (specInterface.getKind() != ElementKind.INTERFACE) {
      Diagnostics.error(
          messager,
          specInterface,
          "@ImportOptics",
          "'" + specInterface.getSimpleName() + "' implements OpticsSpec but is not an interface.",
          "A spec is read for its abstract methods, one per optic to generate, which is a shape"
              + " only an interface has.",
          "Declare it as an interface.");
      return Optional.empty();
    }

    // The processor only routes a type here when OpticsSpec is one of its direct super-interfaces,
    // so the one way to reach this is to have named it raw.
    TypeMirror sourceType = extractSourceType(specInterface);
    if (sourceType == null) {
      Diagnostics.error(
          messager,
          specInterface,
          "@ImportOptics",
          "'" + specInterface.getSimpleName() + "' extends OpticsSpec with no type argument.",
          "The source type the optics are generated against is read from that argument, and a raw"
              + " OpticsSpec names none.",
          "Name the type the optics are for: 'OpticsSpec<Box>'.");
      return Optional.empty();
    }

    // A type variable and an array each fail here, in different ways: asElement returns a
    // TypeParameterElement for the first and null for the second. The pattern match covers both,
    // and keeps a source type that is not a declared type out of the analysis that follows.
    if (!(typeUtils.asElement(sourceType) instanceof TypeElement sourceTypeElement)) {
      reportUnusableSourceType(specInterface, sourceType);
      return Optional.empty();
    }

    List<ExecutableElement> methods = ElementFilter.methodsIn(specInterface.getEnclosedElements());

    // A default method has no home in the generated class: a body cannot be read during
    // annotation processing, so reject it here rather than generating a stub that throws.
    boolean rejected = false;
    for (ExecutableElement method : methods) {
      if (method.isDefault()) {
        reportDefaultMethod(specInterface, method);
        rejected = true;
      }
    }
    if (rejected) {
      return Optional.empty();
    }

    List<OpticMethodInfo> opticMethods = new ArrayList<>();
    for (ExecutableElement method : methods) {
      // Static methods are left alone: they stay on the interface and can call the generated
      // statics, so they are a home for composition rather than something to generate.
      if (method.getModifiers().contains(Modifier.ABSTRACT)) {
        Optional<OpticMethodInfo> opticInfo =
            analyseOpticMethod(method, sourceType, sourceTypeElement, specInterface, targetPackage);
        if (opticInfo.isPresent()) {
          opticMethods.add(opticInfo.get());
        } else {
          // Error already reported in analyseOpticMethod
          return Optional.empty();
        }
      }
    }

    return Optional.of(
        new SpecAnalysis(specInterface, sourceType, sourceTypeElement, opticMethods));
  }

  /**
   * Reports a {@code default} method on a spec interface, naming the two places composition can
   * live instead.
   *
   * @param specInterface the spec interface declaring the method
   * @param method the offending default method
   */
  private void reportDefaultMethod(TypeElement specInterface, ExecutableElement method) {
    Diagnostics.error(
        messager,
        method,
        "@ImportOptics",
        "'"
            + specInterface.getSimpleName()
            + "."
            + method.getSimpleName()
            + "' is a default method.",
        "A spec interface declares the optics to generate, and a method body cannot be read during"
            + " annotation processing, so the generated class could only carry a stub that throws.",
        "Make it an abstract method carrying a copy strategy or hint annotation, or move the"
            + " composition to a static method on this interface or to an ordinary utility class"
            + " that calls the generated statics.");
  }

  /**
   * Reports a source type that no optic can be generated against, naming the kind it turned out to
   * be so that a bare {@code S} does not read as a class name.
   *
   * @param specInterface the spec interface declaring the source type
   * @param sourceType the offending type argument to {@code OpticsSpec}
   */
  private void reportUnusableSourceType(TypeElement specInterface, TypeMirror sourceType) {
    // A type variable and an array are the only two kinds that reach here. OpticsSpec takes a
    // reference type, so a wildcard or primitive argument never compiles; an unresolvable one
    // resolves to an element that is still a TypeElement, and javac reports it first; and an
    // intersection cannot be written as a type argument at all.
    String kind = sourceType.getKind() == TypeKind.TYPEVAR ? "a type variable" : "an array type";
    Diagnostics.error(
        messager,
        specInterface,
        "@ImportOptics",
        "'"
            + specInterface.getSimpleName()
            + "' declares OpticsSpec<"
            + ProcessorUtils.simpleTypeName(sourceType)
            + ">, which is "
            + kind
            + ".",
        "An optic reads the members of its source type and rebuilds it through a constructor,"
            + " wither or setter, so that type has to be a class, record or interface named at the"
            + " declaration.",
        "Name the type the optics are for as the type argument" + boundHint(sourceType) + ".");
  }

  /**
   * Names the type a variable is bounded by, so that {@code <S extends Box>} is answered with the
   * declaration that bound points at.
   *
   * <p>A bound only answers the question when it names one type that could stand in the variable's
   * place. Several cannot, and each is left unanswered rather than guessed at: {@code Object},
   * which every variable is bounded by and which says nothing; an intersection, {@code Box &
   * Serializable}, which names two; another type variable, which moves the question rather than
   * settling it; and a bound that names the variable it bounds, {@code Comparable<S>}, which is
   * circular.
   *
   * @param sourceType the offending type argument to {@code OpticsSpec}
   * @return the hint to append to the fix sentence, or the empty string when the bound has no one
   *     type to offer
   */
  private String boundHint(TypeMirror sourceType) {
    // getKind(), not instanceof: javac's intersection type implements DeclaredType, and asking it
    // for a simple name yields the empty string. That is the mistake #728 was, one bound along.
    if (sourceType instanceof TypeVariable typeVariable
        && typeVariable.getUpperBound() instanceof DeclaredType bound
        && bound.getKind() == TypeKind.DECLARED
        // isSameType, not a name comparison: it also answers true for an unresolvable bound, whose
        // own 'cannot find symbol' is the error worth reading.
        && !typeUtils.isSameType(bound, elementUtils.getTypeElement(OBJECT_FQN).asType())
        && !ProcessorUtils.mentions(bound, typeVariable.asElement())) {
      return ": 'OpticsSpec<" + ProcessorUtils.simpleTypeName(bound) + ">'";
    }
    return "";
  }

  /**
   * Extracts the source type {@code S} from {@code OpticsSpec<S>}.
   *
   * @param specInterface the interface to analyse
   * @return the source type, or null if not found
   */
  private TypeMirror extractSourceType(TypeElement specInterface) {
    for (TypeMirror superInterface : specInterface.getInterfaces()) {
      // Super-interfaces returned by getInterfaces() are always declared types.
      DeclaredType declaredType = (DeclaredType) superInterface;

      TypeElement interfaceElement = (TypeElement) declaredType.asElement();
      if (interfaceElement.getQualifiedName().contentEquals(OPTICS_SPEC_FQN)) {
        List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
        if (!typeArgs.isEmpty()) {
          return typeArgs.get(0);
        }
      }
    }
    return null;
  }

  /**
   * Analyses an abstract method to determine what optic it defines.
   *
   * @param method the abstract method
   * @param sourceType the source type S
   * @param sourceTypeElement the resolved element for the source type
   * @param specInterface the spec interface (for error reporting)
   * @param targetPackage the package the optics class is generated into
   * @return the optic method info, or empty if invalid
   */
  private Optional<OpticMethodInfo> analyseOpticMethod(
      ExecutableElement method,
      TypeMirror sourceType,
      TypeElement sourceTypeElement,
      TypeElement specInterface,
      String targetPackage) {

    // Validate method signature: no parameters allowed
    if (!method.getParameters().isEmpty()) {
      error(
          "Optic method '"
              + method.getSimpleName()
              + "' must have no parameters. "
              + "Found: "
              + method.getParameters().size()
              + " parameter(s)",
          method);
      return Optional.empty();
    }

    // A parameter of the method's own can only appear in the focus, and the focus is reached from
    // a source type the spec has already fixed, so nothing could ever bind it.
    if (!method.getTypeParameters().isEmpty()) {
      Diagnostics.error(
          messager,
          method,
          "@ImportOptics",
          "'"
              + specInterface.getSimpleName()
              + "."
              + method.getSimpleName()
              + "' declares its own type parameters.",
          "An optic is generated against the source type the spec names, so the only types in play"
              + " are that type's and the spec's own; a parameter declared here has nothing that"
              + " could infer it.",
          "Move the parameter to the spec interface, where the source type can name it, or drop"
              + " it.");
      return Optional.empty();
    }

    // Validate return type is an optic type
    TypeMirror returnType = method.getReturnType();
    if (!(returnType instanceof DeclaredType declaredReturn)) {
      error(
          "Optic method '"
              + method.getSimpleName()
              + "' must return an optic type (Lens, Prism, Traversal, etc.)",
          method);
      return Optional.empty();
    }

    OpticKind opticKind = determineOpticKind(declaredReturn);
    if (opticKind == null) {
      error(
          "Method '"
              + method.getSimpleName()
              + "' must return Lens, Prism, Traversal, Affine, Iso, Getter, or Fold. "
              + "Found: "
              + returnType,
          method);
      return Optional.empty();
    }

    // Extract focus type A from Optic<S, A>
    TypeMirror focusType = extractFocusType(declaredReturn);
    if (focusType == null) {
      error(
          "Cannot determine focus type for method '"
              + method.getSimpleName()
              + "'. "
              + "Expected Optic<S, A> with type arguments",
          method);
      return Optional.empty();
    }

    // Parse annotations based on optic kind
    CopyStrategyKind copyStrategy = CopyStrategyKind.NONE;
    CopyStrategyInfo copyStrategyInfo = CopyStrategyInfo.empty();
    PrismHintKind prismHint = PrismHintKind.NONE;
    PrismHintInfo prismHintInfo = PrismHintInfo.empty();
    TraversalHintKind traversalHint = TraversalHintKind.NONE;
    TraversalHintInfo traversalHintInfo = TraversalHintInfo.empty();

    switch (opticKind) {
      case LENS -> {
        var copyResult = parseCopyStrategy(method, sourceType, sourceTypeElement, targetPackage);
        if (copyResult.isEmpty()) {
          // parseCopyStrategy has reported why: either no strategy annotation at all, or one
          // whose values were rejected.
          return Optional.empty();
        }
        copyStrategy = copyResult.get().kind();
        copyStrategyInfo = copyResult.get().info();
      }
      case PRISM -> {
        var prismResult = parsePrismHint(method, sourceType, specInterface);
        if (prismResult.isEmpty()) {
          // parsePrismHint has reported why.
          return Optional.empty();
        }
        prismHint = prismResult.get().kind();
        prismHintInfo = prismResult.get().info();
      }
      case TRAVERSAL -> {
        var traversalResult = parseTraversalHint(method, sourceType, specInterface);
        if (traversalResult.isEmpty()) {
          // parseTraversalHint has reported why.
          return Optional.empty();
        }
        traversalHint = traversalResult.get().kind();
        traversalHintInfo = traversalResult.get().info();
      }
      case AFFINE, ISO, GETTER, FOLD -> {
        // These may have various annotations, handle later if needed
      }
    }

    return Optional.of(
        new OpticMethodInfo(
            method,
            opticKind,
            focusType,
            copyStrategy,
            copyStrategyInfo,
            prismHint,
            prismHintInfo,
            traversalHint,
            traversalHintInfo));
  }

  /**
   * Determines the optic kind from a return type.
   *
   * @param declaredType the declared return type
   * @return the optic kind, or null if not an optic type
   */
  private OpticKind determineOpticKind(DeclaredType declaredType) {
    TypeElement typeElement = (TypeElement) declaredType.asElement();
    String qualifiedName = typeElement.getQualifiedName().toString();

    return switch (qualifiedName) {
      case LENS_FQN -> OpticKind.LENS;
      case PRISM_FQN -> OpticKind.PRISM;
      case TRAVERSAL_FQN -> OpticKind.TRAVERSAL;
      case AFFINE_FQN -> OpticKind.AFFINE;
      case ISO_FQN -> OpticKind.ISO;
      case GETTER_FQN -> OpticKind.GETTER;
      case FOLD_FQN -> OpticKind.FOLD;
      default -> null;
    };
  }

  /**
   * Extracts the focus type A from Optic<S, A>.
   *
   * @param opticType the optic type
   * @return the focus type, or null if not found
   */
  private TypeMirror extractFocusType(DeclaredType opticType) {
    List<? extends TypeMirror> typeArgs = opticType.getTypeArguments();
    if (typeArgs.size() >= 2) {
      return typeArgs.get(1); // A in Optic<S, A>
    }
    return null;
  }

  // ----- Copy Strategy Parsing -----

  private record CopyStrategyResult(CopyStrategyKind kind, CopyStrategyInfo info) {}

  /**
   * Reads the copy strategy annotation on a lens method.
   *
   * @param method the abstract lens method
   * @param sourceType the source type {@code S}, which annotation values are resolved against
   * @param sourceTypeElement the resolved element for {@code S}
   * @param targetPackage the package the optics class is generated into
   * @return the strategy and its values, or empty if the method carries no strategy annotation or
   *     one whose values were rejected; either way an error has been reported
   */
  private Optional<CopyStrategyResult> parseCopyStrategy(
      ExecutableElement method,
      TypeMirror sourceType,
      TypeElement sourceTypeElement,
      String targetPackage) {
    // Check for @ViaBuilder
    AnnotationMirror viaBuilder = findAnnotation(method, VIA_BUILDER_FQN);
    if (viaBuilder != null) {
      String getter = getAnnotationString(viaBuilder, "getter", "");
      String toBuilder = getAnnotationString(viaBuilder, "toBuilder", "toBuilder");
      String setter = getAnnotationString(viaBuilder, "setter", "");
      String build = getAnnotationString(viaBuilder, "build", "build");
      return Optional.of(
          new CopyStrategyResult(
              CopyStrategyKind.VIA_BUILDER,
              CopyStrategyInfo.forBuilder(getter, toBuilder, setter, build)));
    }

    // Check for @Wither
    AnnotationMirror wither = findAnnotation(method, WITHER_FQN);
    if (wither != null) {
      String getter = getAnnotationString(wither, "getter", "");
      String witherMethod = getAnnotationString(wither, "value", "");
      return Optional.of(
          new CopyStrategyResult(
              CopyStrategyKind.WITHER, CopyStrategyInfo.forWither(getter, witherMethod)));
    }

    // analyse() admits a source type only when asElement gives a TypeElement, which on javac
    // leaves DECLARED, ERROR and INTERSECTION - every one of them a DeclaredType. That is what
    // makes the cast total; 'is a declared type' on its own would not, as #728 found.
    DeclaredType declaredSource = (DeclaredType) sourceType;

    // Check for @ViaConstructor
    AnnotationMirror viaConstructor = findAnnotation(method, VIA_CONSTRUCTOR_FQN);
    if (viaConstructor != null) {
      if (rebuildsThroughUnwritableConstructor(method, declaredSource, "@ViaConstructor")) {
        return Optional.empty();
      }
      String[] parameterOrder = getAnnotationStringArray(viaConstructor, "parameterOrder");
      return Optional.of(
          new CopyStrategyResult(
              CopyStrategyKind.VIA_CONSTRUCTOR, CopyStrategyInfo.forConstructor(parameterOrder)));
    }

    // Check for @ViaCopyAndSet
    AnnotationMirror viaCopyAndSet = findAnnotation(method, VIA_COPY_AND_SET_FQN);
    if (viaCopyAndSet != null) {
      if (rebuildsThroughUnwritableConstructor(method, declaredSource, "@ViaCopyAndSet")) {
        return Optional.empty();
      }
      String copyConstructor = getAnnotationString(viaCopyAndSet, "copyConstructor", "");
      String setter = getAnnotationString(viaCopyAndSet, "setter", "");
      if (copyConstructor.isEmpty()) {
        return Optional.of(
            new CopyStrategyResult(
                CopyStrategyKind.VIA_COPY_AND_SET, CopyStrategyInfo.forCopyAndSet(null, setter)));
      }
      return resolveCopyConstructorParameterType(
              method, declaredSource, targetPackage, copyConstructor)
          .map(
              parameterType ->
                  new CopyStrategyResult(
                      CopyStrategyKind.VIA_COPY_AND_SET,
                      // Naming S itself is honoured by casting to nothing: a cast to the
                      // argument's own type says nothing, and javac reports it as redundant.
                      // Answering it here, with Types, leaves the generator one rule - a null
                      // parameter type means no cast - rather than a comparison of rendered names.
                      CopyStrategyInfo.forCopyAndSet(
                          typeUtils.isSameType(parameterType, sourceType) ? null : parameterType,
                          setter)));
    }

    Diagnostics.error(
        messager,
        method,
        "@ImportOptics",
        "Lens method '" + method.getSimpleName() + "' carries no copy strategy annotation.",
        "A lens has to rebuild '"
            + sourceType
            + "' to set through it, and only the strategy says how that type is copied.",
        "Add @ViaBuilder, @Wither, @ViaConstructor, or @ViaCopyAndSet to the method.");
    return Optional.empty();
  }

  /**
   * Reports a source type whose own constructor call cannot be written, and returns whether it did.
   *
   * <p>A strategy that rebuilds through a constructor emits {@code new S(...)}, and a wildcard
   * cannot be written as a type argument there: {@code new Node<?>(...)} is not Java, whatever the
   * arguments. The strategies that rebuild through a wither or a builder name no constructor and
   * are unaffected, so this is asked per strategy rather than of the source type as a whole.
   *
   * <p>Only the outermost arguments matter. A wildcard nested inside one, {@code Node<List<?>>},
   * writes perfectly well.
   *
   * @param method the annotated optic method, for error reporting
   * @param declared the source type {@code S}
   * @param annotation the strategy annotation tag, for the diagnostic
   * @return true when the source type was rejected and an error reported
   */
  private boolean rebuildsThroughUnwritableConstructor(
      ExecutableElement method, DeclaredType declared, String annotation) {

    boolean wildcard =
        declared.getTypeArguments().stream()
            .anyMatch(argument -> argument.getKind() == TypeKind.WILDCARD);
    // A DeclaredType's element is always a TypeElement. Only a member type that is not static
    // carries an enclosing instance; a nested interface, enum or record is implicitly static.
    TypeElement element = (TypeElement) declared.asElement();
    boolean innerClass =
        element.getNestingKind() == NestingKind.MEMBER
            && !element.getModifiers().contains(Modifier.STATIC);
    if (!wildcard && !innerClass) {
      return false;
    }
    String name = ProcessorUtils.simpleTypeName(declared);
    Diagnostics.error(
        messager,
        method,
        annotation,
        "'"
            + name
            + "' is "
            + (wildcard ? "written with a wildcard type argument" : "an inner class")
            + ", and this strategy rebuilds it through a constructor.",
        "The generated set function calls 'new "
            + name
            + "(...)', which is not something that can be written for "
            + (wildcard
                ? "a wildcard: a constructor call has to name the type argument."
                : "an inner class: the call needs an enclosing instance the generated class has"
                    + " no way to reach."),
        wildcard
            ? "Name the type the wildcard stands for, or use @Wither, which rebuilds through a"
                + " method and needs no constructor."
            : "Declare the source type static, or use @Wither, which rebuilds through a method and"
                + " needs no constructor.");
    return true;
  }

  /**
   * Resolves the type named by {@code @ViaCopyAndSet(copyConstructor = ...)} to the supertype of
   * {@code S} that the generated cast will name.
   *
   * <p>The attribute names the copy constructor's <em>parameter</em> type, so the emitted argument
   * is {@code (ParameterType) source}. Four things have to hold for that to compile, and each is
   * checked here rather than left to javac, which would report it inside a generated file the user
   * did not write: the name resolves, it names a supertype of {@code S}, the generated class is
   * allowed to name it, and {@code S} has a constructor that accepts it.
   *
   * <p>The supertype is returned as {@code S}'s own {@code extends}/{@code implements} clause
   * instantiates it, so a base declared {@code Holder<String>} is named with its argument rather
   * than raw. A clause that is itself raw is named raw, which is what the source says.
   *
   * <p>Naming {@code S} itself resolves to {@code S}; the generator then emits no cast, since a
   * cast to the argument's own type says nothing.
   *
   * @param method the annotated optic method, for error reporting
   * @param sourceType the source type {@code S}
   * @param targetPackage the package the optics class is generated into
   * @param copyConstructor the fully qualified name from the annotation; never empty
   * @return the resolved supertype, or empty if it was rejected (an error has been reported)
   */
  private Optional<TypeMirror> resolveCopyConstructorParameterType(
      ExecutableElement method,
      DeclaredType sourceType,
      String targetPackage,
      String copyConstructor) {

    TypeElement parameterElement = elementUtils.getTypeElement(copyConstructor);
    if (parameterElement == null) {
      Diagnostics.error(
          messager,
          method,
          "@ViaCopyAndSet",
          "copyConstructor names '" + copyConstructor + "', which does not resolve to a type.",
          "The attribute is a plain string, so it is not resolved against the spec interface's"
              + " imports, and it takes no type arguments.",
          "Give the copy constructor's parameter type as a fully qualified class name - a nested"
              + " class as 'com.example.Outer.Base', a generic base as the class alone - or drop"
              + " the attribute to pass '"
              + sourceType
              + "' unchanged.");
      return Optional.empty();
    }

    Optional<TypeMirror> supertype = resolveSupertype(method, sourceType, parameterElement);
    if (supertype.isEmpty()) {
      return Optional.empty();
    }

    if (!isVisibleFrom(parameterElement, targetPackage)) {
      Diagnostics.error(
          messager,
          method,
          "@ViaCopyAndSet",
          "copyConstructor names '"
              + parameterElement.getQualifiedName()
              + "', which is not public and so cannot be named from '"
              + targetPackage
              + "'.",
          "The generated optics class writes the cast as '("
              + parameterElement.getSimpleName()
              + ") source', so it has to be able to name the type; passing the source unchanged"
              + " never names it.",
          "Name a public supertype, generate into '"
              + elementUtils.getPackageOf(parameterElement).getQualifiedName()
              + "' with @ImportOptics(targetPackage = ...), or drop the attribute.");
      return Optional.empty();
    }

    // The cast that will be emitted, not the name the attribute gave: where S pins the supertype's
    // arguments the two differ, and only the emitted one explains the rejection.
    if (!hasConstructorAccepting(sourceType, supertype.get(), targetPackage)) {
      Diagnostics.error(
          messager,
          method,
          "@ViaCopyAndSet",
          "copyConstructor names '"
              + parameterElement.getQualifiedName()
              + "', which '"
              + ProcessorUtils.simpleTypeName(sourceType)
              + "' reaches as '"
              + ProcessorUtils.simpleTypeName(supertype.get())
              + "', and no constructor accepts.",
          "The generated set function calls 'new "
              + ProcessorUtils.simpleTypeName(sourceType)
              + "(("
              + ProcessorUtils.simpleTypeName(supertype.get())
              + ") source)'. Found "
              + describeSingleArgumentConstructors(sourceType, targetPackage)
              + ".",
          "Name a supertype of '"
              + ProcessorUtils.simpleTypeName(sourceType)
              + "' that one of those constructors takes, as the class alone without type"
              + " arguments, or drop the attribute to pass the source unchanged.");
      return Optional.empty();
    }

    return supertype;
  }

  /**
   * Finds the supertype relation the cast depends on, reporting when it does not hold.
   *
   * <p>A hierarchy containing a type this round cannot resolve - one another processor has yet to
   * generate, say - reads as having no supertypes at all, which would make every name look wrong.
   * The compiler is asked directly before any name is rejected, so an unreadable hierarchy costs
   * the instantiation rather than drawing an error that blames the attribute for a missing type
   * javac is already reporting.
   *
   * @param method the annotated optic method, for error reporting
   * @param sourceType the source type {@code S}
   * @param parameterElement the resolved element the attribute names
   * @return the supertype to name, or empty if it was rejected (an error has been reported)
   */
  private Optional<TypeMirror> resolveSupertype(
      ExecutableElement method, TypeMirror sourceType, TypeElement parameterElement) {

    TypeMirror walked = findSupertype(sourceType, parameterElement);
    if (walked != null) {
      return Optional.of(walked);
    }

    TypeMirror erased = typeUtils.erasure(parameterElement.asType());
    if (typeUtils.isAssignable(typeUtils.erasure(sourceType), erased)) {
      return Optional.of(erased);
    }

    Diagnostics.error(
        messager,
        method,
        "@ViaCopyAndSet",
        "copyConstructor names '"
            + parameterElement.getQualifiedName()
            + "', which '"
            + sourceType
            + "' does not extend or implement.",
        "The generated set function passes the source to the copy constructor as '("
            + parameterElement.getSimpleName()
            + ") source', and only a supertype of the source can be cast to there.",
        "Name a supertype of '" + sourceType + "', or drop the attribute to pass it unchanged.");
    return Optional.empty();
  }

  /**
   * Returns whether the generated class may name {@code type}.
   *
   * @param type the type the generated cast would name
   * @param targetPackage the package the optics class is generated into
   * @return true if {@code type} is public, or package-private in the generated class's own package
   */
  private boolean isVisibleFrom(TypeElement type, String targetPackage) {
    for (Element enclosing = type; enclosing instanceof TypeElement nested; ) {
      if (!nested.getModifiers().contains(Modifier.PUBLIC)) {
        return elementUtils.getPackageOf(type).getQualifiedName().contentEquals(targetPackage);
      }
      enclosing = nested.getEnclosingElement();
    }
    return true;
  }

  /**
   * Returns whether the generated class may call a constructor of {@code member}'s kind.
   *
   * <p>{@code protected} is package access here: it reaches a subclass, and the generated optics
   * class is not one.
   *
   * @param member the constructor being considered
   * @param targetPackage the package the optics class is generated into
   * @return true if the generated class can call it
   */
  private boolean isAccessibleFrom(Element member, String targetPackage) {
    Set<Modifier> modifiers = member.getModifiers();
    if (modifiers.contains(Modifier.PRIVATE)) {
      return false;
    }
    if (modifiers.contains(Modifier.PUBLIC)) {
      return true;
    }
    return elementUtils.getPackageOf(member).getQualifiedName().contentEquals(targetPackage);
  }

  /**
   * Returns whether {@code sourceType} declares a constructor the generated class can call a single
   * {@code argument} through.
   *
   * @param sourceType the instantiated source type {@code S}, which the constructors are read under
   * @param argument the type the generated cast produces
   * @param targetPackage the package the optics class is generated into
   * @return true if some constructor it can reach accepts it
   */
  private boolean hasConstructorAccepting(
      DeclaredType sourceType, TypeMirror argument, String targetPackage) {
    for (ExecutableElement constructor :
        ElementFilter.constructorsIn(sourceType.asElement().getEnclosedElements())) {
      List<? extends VariableElement> parameters = constructor.getParameters();
      // A constructor the generated class cannot call is no use, however well it fits.
      if (parameters.size() != 1 || !isAccessibleFrom(constructor, targetPackage)) {
        continue;
      }
      TypeMirror parameterType = constructorParameterType(sourceType, constructor);
      if (typeUtils.isAssignable(argument, parameterType)) {
        return true;
      }
      // A varargs parameter is always an array type, so the component is there to read.
      if (constructor.isVarArgs()
          && typeUtils.isAssignable(argument, ((ArrayType) parameterType).getComponentType())) {
        return true;
      }
    }
    return false;
  }

  /**
   * The constructor's one parameter as seen under the source type's instantiation.
   *
   * <p>Read off the constructor directly, the parameter speaks the source type's own declaration:
   * {@code Node<X>} declaring {@code Node(Base<X> other)} gives {@code Base<X>}. The argument it is
   * compared against comes from a supertype walk over the instantiated type, so it speaks the
   * spec's variables, {@code Base<U>}. Where the source type declares parameters of its own the two
   * can never match until one is rewritten in the other's terms; where it declares none, the
   * rewrite is a no-op and the declared parameter was already the answer.
   *
   * <p>Only the class's own variables are substituted. A constructor that declares parameters of
   * its own keeps them, and is left to be rejected.
   *
   * @param sourceType the instantiated source type {@code S}
   * @param constructor a single-argument constructor, whose one parameter is read
   * @return the parameter type under {@code sourceType}'s instantiation
   */
  private TypeMirror constructorParameterType(
      DeclaredType sourceType, ExecutableElement constructor) {
    // Total: asMemberOf answers with an ExecutableType for an executable member, and the only
    // shape that would not - an unresolvable source type, whose members resolve to itself - never
    // reaches here, because such a type enumerates no constructors for the caller to loop over.
    ExecutableType asMember = (ExecutableType) typeUtils.asMemberOf(sourceType, constructor);
    return asMember.getParameterTypes().getFirst();
  }

  /**
   * Names the single-argument constructors the generated class can call, for the rejection message.
   *
   * <p>Only the reachable ones: naming a constructor the generated class cannot call would send the
   * reader after a type that fails the same way.
   *
   * @param sourceType the instantiated source type {@code S}, so the list names the parameters as
   *     it sees them
   * @param targetPackage the package the optics class is generated into
   * @return the parameter types it takes one at a time, or a phrase saying it takes none
   */
  private String describeSingleArgumentConstructors(DeclaredType sourceType, String targetPackage) {
    // The same instantiation the check uses, so a reader comparing the list against the name they
    // gave is comparing like with like. The names carry type arguments and the attribute does not,
    // so the list is there to be recognised rather than copied from.
    List<String> parameterTypes =
        ElementFilter.constructorsIn(sourceType.asElement().getEnclosedElements()).stream()
            .filter(constructor -> constructor.getParameters().size() == 1)
            .filter(constructor -> isAccessibleFrom(constructor, targetPackage))
            .map(
                constructor ->
                    ProcessorUtils.simpleTypeName(
                        constructorParameterType(sourceType, constructor)))
            .toList();
    return parameterTypes.isEmpty()
        ? "no single-argument constructor it can call"
        : "single-argument constructors taking " + parameterTypes;
  }

  /**
   * Finds the supertype of {@code type} declared by {@code target}, instantiated with the type
   * arguments it is reached by.
   *
   * @param type the type to search from; {@code type} itself counts as a match
   * @param target the declaring element to look for
   * @return the instantiated supertype, or null if {@code target} is not a supertype
   */
  private TypeMirror findSupertype(TypeMirror type, TypeElement target) {
    Name targetName = target.getQualifiedName();
    Deque<TypeMirror> queue = new ArrayDeque<>();
    Set<String> seen = new HashSet<>();
    queue.add(type);
    while (!queue.isEmpty()) {
      TypeMirror current = queue.poll();
      if (!seen.add(current.toString())) {
        continue;
      }
      // The search starts at the source type, which the caller has already resolved to a
      // TypeElement, and every supertype of a declared type is itself declared.
      TypeElement element = (TypeElement) ((DeclaredType) current).asElement();
      if (element.getQualifiedName().contentEquals(targetName)) {
        return current;
      }
      queue.addAll(typeUtils.directSupertypes(current));
    }
    return null;
  }

  // ----- Prism Hint Parsing -----

  private record PrismHintResult(PrismHintKind kind, PrismHintInfo info) {}

  private Optional<PrismHintResult> parsePrismHint(
      ExecutableElement method, TypeMirror sourceType, TypeElement specInterface) {
    // Check for @InstanceOf
    AnnotationMirror instanceOf = findAnnotation(method, INSTANCE_OF_FQN);
    if (instanceOf != null) {
      // @InstanceOf.value() is mandatory, but an unresolvable class constant (a typo, or a
      // not-yet-generated type) is modelled as an erroneous attribute whose value is a String,
      // not a TypeMirror - so this CAN be null and must fall through to the hint diagnostic.
      TypeMirror targetType = getAnnotationTypeMirror(instanceOf, "value");
      if (targetType == null) {
        reportMissingPrismHint(method);
        return Optional.empty();
      }
      // Erasures, because that is what the generated 'source instanceof Target' tests. The
      // annotation carries a class constant, which is always raw, so comparing it against an
      // instantiated source would reject every generic hierarchy.
      if (!typeUtils.isSubtype(typeUtils.erasure(targetType), typeUtils.erasure(sourceType))) {
        error(
            "@InstanceOf target '"
                + targetType
                + "' is not a subtype of source type '"
                + sourceType
                + "'. "
                + "Only subtypes of '"
                + sourceType
                + "' can be used with @InstanceOf.",
            method);
        return Optional.empty();
      }
      return Optional.of(
          new PrismHintResult(PrismHintKind.INSTANCE_OF, PrismHintInfo.forInstanceOf(targetType)));
    }

    // Check for @MatchWhen
    AnnotationMirror matchWhen = findAnnotation(method, MATCH_WHEN_FQN);
    if (matchWhen != null) {
      String predicate = getAnnotationString(matchWhen, "predicate", "");
      String getter = getAnnotationString(matchWhen, "getter", "");
      return Optional.of(
          new PrismHintResult(
              PrismHintKind.MATCH_WHEN, PrismHintInfo.forMatchWhen(predicate, getter)));
    }

    reportMissingPrismHint(method);
    return Optional.empty();
  }

  /**
   * Reports that a prism method carries no hint annotation.
   *
   * @param method the offending method
   */
  private void reportMissingPrismHint(ExecutableElement method) {
    error(
        "Prism method '"
            + method.getSimpleName()
            + "' requires a prism hint annotation: "
            + "@InstanceOf or @MatchWhen",
        method);
  }

  // ----- Traversal Hint Parsing -----

  private record TraversalHintResult(TraversalHintKind kind, TraversalHintInfo info) {}

  private Optional<TraversalHintResult> parseTraversalHint(
      ExecutableElement method, TypeMirror sourceType, TypeElement specInterface) {
    // Check for @TraverseWith
    AnnotationMirror traverseWith = findAnnotation(method, TRAVERSE_WITH_FQN);
    if (traverseWith != null) {
      String traversalReference = getAnnotationString(traverseWith, "value", "");
      return Optional.of(
          new TraversalHintResult(
              TraversalHintKind.TRAVERSE_WITH,
              TraversalHintInfo.forTraverseWith(traversalReference)));
    }

    // Check for @ThroughField
    AnnotationMirror throughField = findAnnotation(method, THROUGH_FIELD_FQN);
    if (throughField != null) {
      String fieldName = getAnnotationString(throughField, "field", "");
      String traversal = getAnnotationString(throughField, "traversal", "");

      // Auto-detect traversal if not specified
      if (traversal.isEmpty()) {
        Optional<String> autoDetected = autoDetectTraversalForField(fieldName, sourceType, method);
        if (autoDetected.isEmpty()) {
          // Error already reported in autoDetectTraversalForField
          return Optional.empty();
        }
        traversal = autoDetected.get();
      }

      return Optional.of(
          new TraversalHintResult(
              TraversalHintKind.THROUGH_FIELD,
              TraversalHintInfo.forThroughField(fieldName, traversal)));
    }

    error(
        "Traversal method '"
            + method.getSimpleName()
            + "' requires a traversal hint annotation: "
            + "@TraverseWith or @ThroughField",
        method);
    return Optional.empty();
  }

  /**
   * Auto-detects the appropriate traversal for a field based on its type.
   *
   * @param fieldName the name of the field to look up
   * @param sourceType the instantiated source type containing the field
   * @param method the method element (for error reporting)
   * @return the traversal reference string, or empty if detection failed
   */
  private Optional<String> autoDetectTraversalForField(
      String fieldName, TypeMirror sourceType, ExecutableElement method) {

    // Look up the field's type on the source type
    TypeMirror fieldType = findFieldType(sourceType, fieldName);
    if (fieldType == null) {
      error(
          "Cannot auto-detect traversal: field '"
              + fieldName
              + "' not found on type '"
              + ProcessorUtils.simpleTypeName(sourceType)
              + "'. "
              + "Check that the field name matches an accessor method or record component.",
          method);
      return Optional.empty();
    }

    // Detect the container type using type hierarchy checking
    TypeKindAnalyser typeAnalyser = new TypeKindAnalyser(typeUtils);
    Optional<ContainerType> containerType =
        typeAnalyser.detectContainerTypeWithSubtypes(fieldType, elementUtils);

    if (containerType.isEmpty()) {
      error(
          "Cannot auto-detect traversal for field '"
              + fieldName
              + "' of type '"
              + ProcessorUtils.simpleTypeName(fieldType)
              + "'. "
              + "Supported types: List, Set, Optional, Map, arrays. "
              + "Please specify traversal() explicitly, e.g.: "
              + "@ThroughField(field = \""
              + fieldName
              + "\", traversal = \"MyTraversals.custom()\")",
          method);
      return Optional.empty();
    }

    // Get the standard traversal reference for this container type
    TraversalCodeGenerator traversalGenerator = new TraversalCodeGenerator();
    String traversalRef = traversalGenerator.getStandardTraversal(containerType.get().kind());

    return Optional.of(traversalRef);
  }

  /**
   * A member's type as the instantiated source type sees it, unwrapping an accessor's return.
   *
   * <p>Read off the element, a member of {@code Holder<T>} speaks {@code T}; the spec instantiated
   * it as {@code Holder<List<String>>}, so what the traversal has to be detected for is {@code
   * List<String>}. Reading the declaration instead both rejects a container it could have found and
   * names a variable the spec never wrote.
   *
   * @param sourceType the instantiated source type {@code S}
   * @param member the accessor to read
   * @return the member's type under {@code sourceType}'s instantiation
   */
  private TypeMirror memberTypeOf(DeclaredType sourceType, ExecutableElement member) {
    if (!ProcessorUtils.carriesInstantiation(sourceType)) {
      return member.getReturnType();
    }
    // Total: asMemberOf answers with an ExecutableType for an executable member, and the one shape
    // that would not - an unresolvable source type, whose members resolve to itself - enumerates
    // no members for the caller to have found.
    return ((ExecutableType) typeUtils.asMemberOf(sourceType, member)).getReturnType();
  }

  /**
   * The type a named field has on the instantiated source type.
   *
   * @param sourceType the instantiated source type to search
   * @param fieldName the field name to find
   * @return the field's type under that instantiation, or null if not found
   */
  private TypeMirror findFieldType(TypeMirror sourceType, String fieldName) {
    // analyse() admits a source type only when asElement gives a TypeElement, which on javac
    // leaves DECLARED, ERROR and INTERSECTION - every one of them a DeclaredType. That is what
    // makes the cast total, the same reasoning parseCopyStrategy's spells out.
    DeclaredType declaredSource = (DeclaredType) sourceType;
    TypeElement typeElement = (TypeElement) declaredSource.asElement();

    // For records, check record components first
    if (typeElement.getKind() == ElementKind.RECORD) {
      for (var component : typeElement.getRecordComponents()) {
        if (component.getSimpleName().contentEquals(fieldName)) {
          return memberTypeOf(declaredSource, component.getAccessor());
        }
      }
    }

    // Look for accessor method (record-style: fieldName() or JavaBean-style: getFieldName())
    String getterName = "get" + ProcessorUtils.capitalise(fieldName);
    String isGetterName = "is" + ProcessorUtils.capitalise(fieldName); // For booleans

    for (var enclosed : typeElement.getEnclosedElements()) {
      if (enclosed.getKind() != ElementKind.METHOD) {
        continue;
      }

      ExecutableElement method = (ExecutableElement) enclosed;
      String methodName = method.getSimpleName().toString();

      // Check for record-style accessor (e.g., players())
      // or JavaBean-style getter (e.g., getPlayers())
      if ((methodName.equals(fieldName)
              || methodName.equals(getterName)
              || methodName.equals(isGetterName))
          && method.getParameters().isEmpty()
          && method.getModifiers().contains(Modifier.PUBLIC)
          && !method.getModifiers().contains(Modifier.STATIC)) {
        return memberTypeOf(declaredSource, method);
      }
    }

    // Look for public field directly
    for (var enclosed : typeElement.getEnclosedElements()) {
      if (enclosed.getKind() == ElementKind.FIELD) {
        VariableElement field = (VariableElement) enclosed;
        if (field.getSimpleName().contentEquals(fieldName)
            && field.getModifiers().contains(Modifier.PUBLIC)) {
          return ProcessorUtils.carriesInstantiation(declaredSource)
              ? typeUtils.asMemberOf(declaredSource, field)
              : field.asType();
        }
      }
    }

    return null;
  }

  // ----- Annotation Utility Methods -----

  private AnnotationMirror findAnnotation(Element element, String annotationFqn) {
    for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
      TypeElement annotationType = (TypeElement) mirror.getAnnotationType().asElement();
      if (annotationType.getQualifiedName().contentEquals(annotationFqn)) {
        return mirror;
      }
    }
    return null;
  }

  private String getAnnotationString(
      AnnotationMirror annotation, String elementName, String defaultValue) {
    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
        annotation.getElementValues().entrySet()) {
      if (entry.getKey().getSimpleName().contentEquals(elementName)) {
        // getValue() never returns null for a present annotation element.
        return entry.getValue().getValue().toString();
      }
    }
    return defaultValue;
  }

  // Package-private for tests.
  String[] getAnnotationStringArray(AnnotationMirror annotation, String elementName) {
    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
        annotation.getElementValues().entrySet()) {
      if (entry.getKey().getSimpleName().contentEquals(elementName)) {
        Object value = entry.getValue().getValue();
        if (value instanceof List<?> list) {
          return list.stream()
              .map(v -> ((AnnotationValue) v).getValue().toString())
              .toArray(String[]::new);
        }
      }
    }
    return new String[0];
  }

  // Package-private for tests.
  TypeMirror getAnnotationTypeMirror(AnnotationMirror annotation, String elementName) {
    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
        annotation.getElementValues().entrySet()) {
      if (entry.getKey().getSimpleName().contentEquals(elementName)) {
        Object value = entry.getValue().getValue();
        if (value instanceof TypeMirror typeMirror) {
          return typeMirror;
        }
      }
    }
    return null;
  }

  private void error(String msg, Element element) {
    messager.printMessage(Diagnostic.Kind.ERROR, msg, element);
  }
}
