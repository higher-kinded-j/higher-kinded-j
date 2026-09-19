// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import java.util.*;
import java.util.Arrays;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.PrimitiveType;
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

  /** The container interfaces auto-detection rebuilds, most specific first. */
  private static final List<String> CONTAINER_INTERFACE_FQNS =
      List.of("java.util.List", "java.util.Set", "java.util.Map", "java.util.Collection");

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
  private final InstanceOfNarrowing instanceOfNarrowing;

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
    this.instanceOfNarrowing = new InstanceOfNarrowing(typeUtils);
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

    // A raw type anywhere in the source type passes the gate above - its element is still a
    // TypeElement - whether the source type itself is raw, an enclosing level is (JLS 4.8 makes
    // Outer.Holder raw in a generic Outer), or a type argument is. Every generated optic repeats
    // the source type as written, so each shape is refused here in its own right rather than
    // read for members javac would erase.
    TypeElement rawNamed = ProcessorUtils.firstRawIn(sourceType);
    if (rawNamed != null) {
      // The element gate above proves the source is a declared type: nothing else resolves to
      // a TypeElement.
      reportRawSourceType(specInterface, (DeclaredType) sourceType, rawNamed);
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
   * Reports a source type naming a raw type, which is a perfectly good source missing its
   * arguments; the remedy is to supply them, not to change the type.
   *
   * @param specInterface the spec interface declaring the source type
   * @param sourceType the offending type argument to {@code OpticsSpec}
   * @param rawNamed the element of the first raw type named in the source type
   */
  private void reportRawSourceType(
      TypeElement specInterface, DeclaredType sourceType, TypeElement rawNamed) {
    String rawName = rawNamed.getSimpleName().toString();
    // The generic-spec example only answers the source type itself being raw; a raw enclosing
    // type or argument has its arguments named in place. Asked of the root type, not by element:
    // OpticsSpec<Box<Box>> names the raw inner Box, whose element is the outer's too.
    boolean sourceItself = ProcessorUtils.isRaw(sourceType);
    Diagnostics.error(
        messager,
        specInterface,
        "@ImportOptics",
        "'"
            + specInterface.getSimpleName()
            + "' declares OpticsSpec<"
            + ProcessorUtils.simpleTypeName(sourceType)
            + ">, which names the raw type '"
            + rawName
            + "'.",
        "Every generated optic repeats the source type verbatim, and a raw type in generated"
            + " source is a [rawtypes] warning that the suppression on your own declaration does"
            + " not cover; a constructor read under a raw type erases its parameters besides.",
        "Name '"
            + rawName
            + "'s type arguments in the OpticsSpec clause"
            + (sourceItself
                ? "; a spec whose optics should stay generic declares its own type parameters"
                    + " and passes them on (OpticsSpec<"
                    + rawName
                    + "<U>>)."
                : "."));
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
        && !ProcessorUtils.mentions(bound, typeVariable.asElement())
        // A hint naming a raw bound would steer straight into the raw-source refusal.
        && ProcessorUtils.firstRawIn(bound) == null) {
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
        var copyResult =
            parseCopyStrategy(method, sourceType, sourceTypeElement, focusType, targetPackage);
        if (copyResult.isEmpty()) {
          // parseCopyStrategy has reported why: either no strategy annotation at all, or one
          // whose values were rejected.
          return Optional.empty();
        }
        copyStrategy = copyResult.get().kind();
        copyStrategyInfo = copyResult.get().info();
      }
      case PRISM -> {
        var prismResult =
            parsePrismHint(method, sourceType, sourceTypeElement, focusType, specInterface);
        if (prismResult.isEmpty()) {
          // parsePrismHint has reported why.
          return Optional.empty();
        }
        // Both hints generate the same build side, so the focus is held to the same requirement
        // whichever one narrowed it. Asked after the hint, so a method carrying none is told that
        // first: it is the nearer problem.
        if (!typeUtils.isAssignable(focusType, sourceType)) {
          reportFocusThatCannotBuildTheSource(method, specInterface, sourceType, focusType);
          return Optional.empty();
        }
        prismHint = prismResult.get().kind();
        prismHintInfo = prismResult.get().info();
      }
      case TRAVERSAL -> {
        var traversalResult = parseTraversalHint(method, sourceType, specInterface, focusType);
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
   * @param focusType the focus type {@code A}, which a wither is called with
   * @param targetPackage the package the optics class is generated into
   * @return the strategy and its values, or empty if the method carries no strategy annotation or
   *     one whose values were rejected; either way an error has been reported
   */
  private Optional<CopyStrategyResult> parseCopyStrategy(
      ExecutableElement method,
      TypeMirror sourceType,
      TypeElement sourceTypeElement,
      TypeMirror focusType,
      String targetPackage) {
    // analyse() admits a source type only when asElement gives a TypeElement, which on javac
    // leaves DECLARED, ERROR and INTERSECTION - every one of them a DeclaredType. That is what
    // makes the cast total; 'is a declared type' on its own would not.
    DeclaredType declaredSource = (DeclaredType) sourceType;
    // Every strategy reads through an accessor, and names it by the lens method where the
    // annotation does not.
    String fieldName = method.getSimpleName().toString();

    // Check for @ViaBuilder
    AnnotationMirror viaBuilder = findAnnotation(method, VIA_BUILDER_FQN);
    if (viaBuilder != null) {
      String getter = getAnnotationString(viaBuilder, "getter", "");
      String toBuilder = getAnnotationString(viaBuilder, "toBuilder", "toBuilder");
      String setter = getAnnotationString(viaBuilder, "setter", "");
      String build = getAnnotationString(viaBuilder, "build", "build");
      if (readsThroughUnusableGetter(
              method,
              "@ViaBuilder",
              declaredSource,
              sourceTypeElement,
              getter.isEmpty() ? fieldName : getter,
              focusType,
              targetPackage)
          || rebuildsThroughUnusableBuilder(
              method,
              declaredSource,
              sourceTypeElement,
              toBuilder,
              setter.isEmpty() ? fieldName : setter,
              build,
              focusType,
              targetPackage)) {
        return Optional.empty();
      }
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
      if (readsThroughUnusableGetter(
              method,
              "@Wither",
              declaredSource,
              sourceTypeElement,
              getter.isEmpty() ? fieldName : getter,
              focusType,
              targetPackage)
          || rebuildsThroughUnusableWither(
              method, declaredSource, sourceTypeElement, focusType, witherMethod, targetPackage)) {
        return Optional.empty();
      }
      return Optional.of(
          new CopyStrategyResult(
              CopyStrategyKind.WITHER, CopyStrategyInfo.forWither(getter, witherMethod)));
    }

    // Check for @ViaConstructor
    AnnotationMirror viaConstructor = findAnnotation(method, VIA_CONSTRUCTOR_FQN);
    if (viaConstructor != null) {
      if (rebuildsThroughUnwritableConstructor(method, declaredSource, "@ViaConstructor")) {
        return Optional.empty();
      }
      String[] parameterOrder = getAnnotationStringArray(viaConstructor, "parameterOrder");
      if (readsThroughUnusableGetter(
              method,
              "@ViaConstructor",
              declaredSource,
              sourceTypeElement,
              fieldName,
              focusType,
              targetPackage)
          || rebuildsThroughUnreadableParameter(
              method, declaredSource, sourceTypeElement, parameterOrder, targetPackage)) {
        return Optional.empty();
      }
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
      if (readsThroughUnusableGetter(
              method,
              "@ViaCopyAndSet",
              declaredSource,
              sourceTypeElement,
              fieldName,
              focusType,
              targetPackage)
          || setsThroughUnusableSetter(
              method,
              "@ViaCopyAndSet",
              declaredSource,
              sourceTypeElement,
              setter,
              focusType,
              targetPackage)) {
        return Optional.empty();
      }
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
   * The methods of a type the generated class can call, in the order {@code getAllMembers} reads
   * them.
   */
  private List<ExecutableElement> callableMembers(TypeElement type, String targetPackage) {
    return ElementFilter.methodsIn(elementUtils.getAllMembers(type)).stream()
        .filter(member -> callableOn(member, targetPackage))
        .toList();
  }

  /** The zero-parameter instance method of that name, which an accessor call needs, or null. */
  private ExecutableElement accessorNamed(List<ExecutableElement> members, String name) {
    return members.stream()
        .filter(
            member ->
                member.getSimpleName().contentEquals(name)
                    && member.getParameters().isEmpty()
                    && !member.getModifiers().contains(Modifier.STATIC))
        .findFirst()
        .orElse(null);
  }

  /** The element of a type a generated call is made on, or null where the type has none. */
  private TypeElement elementOf(TypeMirror type) {
    return typeUtils.asElement(type) instanceof TypeElement element ? element : null;
  }

  /**
   * The type a further call in a chain is made on: the step's own type, or for a step declared with
   * a type variable of its own the bound that variable carries, which is what javac infers it to
   * where nothing else pins it down. A bound naming more than one type leaves no single type to
   * read members on, and answers null for the caller to leave to javac.
   */
  private TypeMirror stepType(TypeMirror handedBack) {
    if (handedBack.getKind() != TypeKind.TYPEVAR) {
      return handedBack;
    }
    TypeMirror bound = ((TypeVariable) handedBack).getUpperBound();
    return bound.getKind() == TypeKind.INTERSECTION ? null : bound;
  }

  /**
   * The type a lens hands its focus back as, for checking what an accessor reads: the focus itself,
   * or null for a focus written as a wildcard, whose type javac infers from the accessor rather
   * than the other way about.
   */
  private TypeMirror focusRead(TypeMirror focusType) {
    return focusType.getKind() == TypeKind.WILDCARD ? null : focusType;
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
   * @param targetPackage the package the optics class is generated into
   */
  private void reportMissingAccessor(
      ExecutableElement method,
      String tag,
      TypeMirror owner,
      TypeElement ownerElement,
      String name,
      String purpose,
      String on,
      String fix,
      String targetPackage) {
    String ownerName = ProcessorUtils.simpleTypeName(owner);
    List<String> accessors =
        callableMembers(ownerElement, targetPackage).stream()
            .filter(
                member ->
                    member.getParameters().isEmpty()
                        && !member.getModifiers().contains(Modifier.STATIC))
            .map(member -> member.getSimpleName().toString())
            .distinct()
            .sorted()
            .toList();
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
            + ProcessorUtils.nearestName(name, accessors)
                .map(near -> " Did you mean '" + near + "'?")
                .orElse(""),
        fix);
  }

  /**
   * Reports a getter the generated lens cannot read its focus through, and returns whether it did.
   *
   * <p>Every strategy reads with the same lambda, {@code source -> source.getX()}, so the name has
   * to be a zero-parameter instance method the generated class can call, and the value it reads has
   * to be one the lens can hand back as its focus. A focus written as a wildcard is inferred from
   * this very accessor, so there is nothing to hold it to.
   *
   * @param method the annotated lens method, for error reporting
   * @param tag the strategy annotation, which the message names
   * @param sourceType the source type {@code S}, as the spec names it
   * @param sourceTypeElement the element of {@code S}, whose members are searched
   * @param getterName the accessor the strategy reads through
   * @param focusType the lens's focus
   * @param targetPackage the package the optics class is generated into
   * @return true when the lens cannot read through it, and an error was reported
   */
  private boolean readsThroughUnusableGetter(
      ExecutableElement method,
      String tag,
      DeclaredType sourceType,
      TypeElement sourceTypeElement,
      String getterName,
      TypeMirror focusType,
      String targetPackage) {
    if (sourceType.getKind() == TypeKind.ERROR) {
      return false;
    }
    ExecutableElement getter =
        accessorNamed(callableMembers(sourceTypeElement, targetPackage), getterName);
    String ownerName = ProcessorUtils.simpleTypeName(sourceType);
    // @ViaCopyAndSet and @ViaConstructor read through the lens method's own name, and carry no
    // attribute that could point anywhere else.
    boolean namesItsGetter = tag.equals("@Wither") || tag.equals("@ViaBuilder");
    if (getter == null) {
      reportMissingAccessor(
          method,
          tag,
          sourceType,
          sourceTypeElement,
          getterName,
          "read the value it focuses",
          "'source'",
          namesItsGetter
              ? "Set " + tag + "'s 'getter' to a method '" + ownerName + "' declares."
              : "Name the lens method after a zero-parameter method '"
                  + ownerName
                  + "' declares, which is the accessor this strategy reads through.",
          targetPackage);
      return true;
    }
    // Read on the captured source, as the call reads it: a value a wildcard types is a type of
    // its own there, and one the lens can hand back.
    TypeMirror read =
        ProcessorUtils.returnTypeIn(
            typeUtils, (DeclaredType) typeUtils.capture(sourceType), getter);
    TypeMirror focus = focusRead(focusType);
    if (focus == null || typeUtils.isAssignable(read, focus)) {
      return false;
    }
    // Named as the spec's own instantiation writes it, which is what the author reads, and in
    // full where a simple name would name both types.
    TypeMirror declaredRead = ProcessorUtils.returnTypeIn(typeUtils, sourceType, getter);
    boolean sameName =
        ProcessorUtils.simpleTypeName(declaredRead).equals(ProcessorUtils.simpleTypeName(focus));
    String focusName = sameName ? focus.toString() : ProcessorUtils.simpleTypeName(focus);
    String readName =
        sameName ? declaredRead.toString() : ProcessorUtils.simpleTypeName(declaredRead);
    // A focus is a type argument, so a primitive read is declared as its wrapper.
    String readAsFocus =
        declaredRead.getKind().isPrimitive()
            ? ProcessorUtils.simpleTypeName(
                typeUtils.boxedClass((PrimitiveType) declaredRead).asType())
            : readName;
    Diagnostics.error(
        messager,
        method,
        tag,
        "'"
            + getterName
            + "()' reads '"
            + readName
            + "', not the lens's focus '"
            + focusName
            + "'.",
        "The generated lens reads through 'source."
            + getterName
            + "()' and hands what it reads back as its focus, which '"
            + readName
            + "' is not.",
        (namesItsGetter
                ? "Point " + tag + "'s 'getter' at an accessor that reads '" + focusName + "'"
                : "Name the lens method after an accessor that reads '" + focusName + "'")
            // A lens can be declared over what an accessor reads, unless it reads nothing, or a
            // type only a wildcard stands for, neither of which can be written as a focus.
            + (declaredRead.getKind() == TypeKind.VOID
                    || declaredRead.getKind() == TypeKind.WILDCARD
                ? "."
                : ", or declare the lens over '"
                    + readAsFocus
                    + "' and rebuild it through a method that takes one."));
    return true;
  }

  /**
   * Reports a setter a generated lens cannot set through on the source type, and returns whether it
   * did.
   *
   * @param method the annotated lens method, for error reporting
   * @param tag the strategy annotation, which the message names
   * @param sourceType the source type {@code S}, as the spec names it
   * @param sourceTypeElement the element of {@code S}, whose members are searched
   * @param setterName the method the strategy sets through
   * @param focusType the lens's focus, which the call passes
   * @param targetPackage the package the optics class is generated into
   * @return true when no method of the name can take the value, and an error was reported
   */
  private boolean setsThroughUnusableSetter(
      ExecutableElement method,
      String tag,
      DeclaredType sourceType,
      TypeElement sourceTypeElement,
      String setterName,
      TypeMirror focusType,
      String targetPackage) {
    if (sourceType.getKind() == TypeKind.ERROR) {
      return false;
    }
    return boundSetter(
            method,
            tag,
            sourceType,
            sourceTypeElement,
            setterName,
            focusType,
            "setter",
            ProcessorUtils.simpleTypeName(sourceType),
            targetPackage)
        instanceof SetterCall.Refused;
  }

  /**
   * What a generated one-argument call on {@code owner} comes to.
   *
   * <p>A focus written as a wildcard leaves the choice to javac, as it does for a wither, so the
   * call is undecided there rather than refused, and so is one javac settles by inference.
   *
   * @param method the annotated lens method, for error reporting
   * @param tag the strategy annotation, which the message names
   * @param owner the type the call is made on
   * @param ownerElement that type's element, whose members are searched
   * @param setterName the method the strategy sets through
   * @param focusType the lens's focus, which the call passes
   * @param attribute the annotation attribute that names it
   * @param sourceName the source type, which a remedy offers another strategy for
   * @param targetPackage the package the optics class is generated into
   * @return the method the call binds, a refusal that has been reported, or undecided
   */
  private SetterCall boundSetter(
      ExecutableElement method,
      String tag,
      DeclaredType owner,
      TypeElement ownerElement,
      String setterName,
      TypeMirror focusType,
      String attribute,
      String sourceName,
      String targetPackage) {
    List<ExecutableElement> named =
        callableMembers(ownerElement, targetPackage).stream()
            .filter(member -> member.getSimpleName().contentEquals(setterName))
            .toList();
    List<ExecutableElement> oneParameter =
        named.stream().filter(member -> member.getParameters().size() == 1).toList();
    if (named.isEmpty()) {
      reportMissingSetter(method, tag, owner, ownerElement, setterName, attribute, targetPackage);
      return new SetterCall.Refused();
    }
    if (focusRead(focusType) == null) {
      if (oneParameter.isEmpty()) {
        reportSetterOfAnotherArity(method, tag, owner, setterName, named, attribute, sourceName);
        return new SetterCall.Refused();
      }
      // Which of several such methods the call binds is javac's to settle, and so is what it
      // hands back; where the name carries only one, that is the one.
      return oneParameter.size() == 1
          ? new SetterCall.Binds(oneParameter.getFirst())
          : new SetterCall.Undecided();
    }
    String call = "'" + setterName + "(newValue)'";
    return switch (WitherBinding.resolve(typeUtils, owner, focusType, named)) {
      case WitherBinding.Binds(ExecutableElement bound) -> {
        if (!bound.getModifiers().contains(Modifier.STATIC)) {
          yield new SetterCall.Binds(bound);
        }
        Diagnostics.error(
            messager,
            method,
            tag,
            "'"
                + signatureOn(owner, bound)
                + "' is static, so the generated lens cannot set through it on a '"
                + ProcessorUtils.simpleTypeName(owner)
                + "'.",
            "The generated lens sets through "
                + call
                + " with the new value typed '"
                + ProcessorUtils.simpleTypeName(focusType)
                + "', which binds that method, and a static method never reads the value it is"
                + " called on.",
            "Set " + tag + "'s '" + attribute + "' to an instance method.");
        yield new SetterCall.Refused();
      }
      case WitherBinding.NoneApplies() -> {
        Diagnostics.error(
            messager,
            method,
            tag,
            "No method '"
                + setterName
                + "' of '"
                + ProcessorUtils.simpleTypeName(owner)
                + "' takes the lens's focus type '"
                + ProcessorUtils.simpleTypeName(focusType)
                + "'.",
            "The generated lens sets through "
                + call
                + " with the new value typed '"
                + ProcessorUtils.simpleTypeName(focusType)
                + "'. Found on '"
                + ProcessorUtils.simpleTypeName(owner)
                + "': "
                + named.stream().map(member -> signatureOn(owner, member)).toList()
                + ".",
            "Set "
                + tag
                + "'s '"
                + attribute
                + "' to a method that takes the value the getter reads"
                + (tag.equals("@ViaBuilder")
                    ? ", or point @ViaBuilder's 'getter' at an accessor one of them takes and"
                        + " declare the focus as its type"
                    : "")
                + "; otherwise "
                + rebuildWith(sourceName, tag)
                + ".");
        yield new SetterCall.Refused();
      }
      case WitherBinding.Ambiguous(List<ExecutableElement> methods) -> {
        List<String> signatures =
            methods.stream().map(member -> "'" + signatureOn(owner, member) + "'").toList();
        Diagnostics.error(
            messager,
            method,
            tag,
            "The generated call to '"
                + setterName
                + "' on a '"
                + ProcessorUtils.simpleTypeName(owner)
                + "' cannot choose between "
                + String.join(", ", signatures.subList(0, signatures.size() - 1))
                + " and "
                + signatures.getLast()
                + ".",
            "The generated lens sets through "
                + call
                + " with the new value typed '"
                + ProcessorUtils.simpleTypeName(focusType)
                + "', which each of them takes, with no parameter more specific than every other.",
            "Declare the lens's focus as the parameter type of the one you mean.");
        yield new SetterCall.Refused();
      }
      case WitherBinding.Undecided(List<ExecutableElement> ignored) -> new SetterCall.Undecided();
    };
  }

  /**
   * What a generated setter call came to: the method it binds, a refusal already reported, or a
   * choice javac settles for itself.
   */
  private sealed interface SetterCall {

    /**
     * The call binds this method.
     *
     * @param method the method the call binds
     */
    record Binds(ExecutableElement method) implements SetterCall {}

    /** No method of the name can take the value, and an error was reported. */
    record Refused() implements SetterCall {}

    /** Which method the call binds rests on inference, so the rest is left to javac. */
    record Undecided() implements SetterCall {}
  }

  /** Reports a setter no overload of which takes one argument, whatever the lens's focus is. */
  private void reportSetterOfAnotherArity(
      ExecutableElement method,
      String tag,
      DeclaredType owner,
      String setterName,
      List<ExecutableElement> named,
      String attribute,
      String sourceName) {
    String ownerName = ProcessorUtils.simpleTypeName(owner);
    Diagnostics.error(
        messager,
        method,
        tag,
        "No method '" + setterName + "' of '" + ownerName + "' takes one argument.",
        "The generated lens sets through '"
            + setterName
            + "(newValue)', passing the one value it sets. Found on '"
            + ownerName
            + "': "
            + named.stream().map(member -> signatureOn(owner, member)).toList()
            + ".",
        "Set "
            + tag
            + "'s '"
            + attribute
            + "' to a method that takes the value the lens sets; otherwise "
            + rebuildWith(sourceName, tag)
            + ".");
  }

  /** Reports a setter the generated optic calls and the type does not have. */
  private void reportMissingSetter(
      ExecutableElement method,
      String tag,
      TypeMirror owner,
      TypeElement ownerElement,
      String setterName,
      String attribute,
      String targetPackage) {
    String ownerName = ProcessorUtils.simpleTypeName(owner);
    List<String> setters =
        callableMembers(ownerElement, targetPackage).stream()
            .filter(
                member ->
                    member.getParameters().size() == 1
                        && !member.getModifiers().contains(Modifier.STATIC))
            .map(member -> member.getSimpleName().toString())
            .distinct()
            .sorted()
            .toList();
    Diagnostics.error(
        messager,
        method,
        tag,
        "'"
            + ownerName
            + "' has no method '"
            + setterName
            + "' for the generated lens to set"
            + " through.",
        "The generated lens sets through '"
            + setterName
            + "(newValue)' on '"
            + ownerName
            + "', so it needs a method of that name there that the generated class in '"
            + targetPackage
            + "' can call."
            + ProcessorUtils.nearestName(setterName, setters)
                .map(near -> " Did you mean '" + near + "'?")
                .orElse(""),
        "Set " + tag + "'s '" + attribute + "' to a method '" + ownerName + "' declares.");
  }

  /**
   * Reports a {@code @ViaBuilder} whose chain the generated lens cannot walk, and returns whether
   * it did.
   *
   * <p>The lens rebuilds with {@code source.toBuilder().setter(newValue).build()}, so each step has
   * to exist where the one before it leads: the builder the source hands back, the setter that
   * takes the focus, and a {@code build} that hands the source type back.
   *
   * @param method the annotated lens method, for error reporting
   * @param sourceType the source type {@code S}, as the spec names it
   * @param sourceTypeElement the element of {@code S}, whose members are searched
   * @param toBuilderName the method that hands back the builder
   * @param setterName the builder method that takes the focus
   * @param buildName the builder method that hands the source type back
   * @param focusType the lens's focus
   * @param targetPackage the package the optics class is generated into
   * @return true when the chain cannot be walked, and an error was reported
   */
  private boolean rebuildsThroughUnusableBuilder(
      ExecutableElement method,
      DeclaredType sourceType,
      TypeElement sourceTypeElement,
      String toBuilderName,
      String setterName,
      String buildName,
      TypeMirror focusType,
      String targetPackage) {
    if (sourceType.getKind() == TypeKind.ERROR) {
      return false;
    }
    ExecutableElement toBuilder =
        accessorNamed(callableMembers(sourceTypeElement, targetPackage), toBuilderName);
    if (toBuilder == null) {
      reportMissingAccessor(
          method,
          "@ViaBuilder",
          sourceType,
          sourceTypeElement,
          toBuilderName,
          "rebuild through",
          "'source'",
          "Set @ViaBuilder's 'toBuilder' to a method '"
              + ProcessorUtils.simpleTypeName(sourceType)
              + "' declares.",
          targetPackage);
      return true;
    }
    TypeMirror builderType =
        stepType(ProcessorUtils.returnTypeIn(typeUtils, sourceType, toBuilder));
    if (builderType == null || builderType.getKind() == TypeKind.ERROR) {
      return false;
    }
    TypeElement builderElement = elementOf(builderType);
    if (builderElement == null) {
      reportBuilderStep(method, toBuilderName + "()", builderType, "a builder to set through");
      return true;
    }
    if (reportsUnnameableStep(
        method, builderElement, toBuilderName + "()", sourceType, targetPackage)) {
      return true;
    }
    SetterCall setter =
        boundSetter(
            method,
            "@ViaBuilder",
            (DeclaredType) builderType,
            builderElement,
            setterName,
            focusType,
            "setter",
            ProcessorUtils.simpleTypeName(sourceType),
            targetPackage);
    // Which method an undecided call binds is javac's to settle, so what it hands back is too.
    if (!(setter instanceof SetterCall.Binds(ExecutableElement bound))) {
      return setter instanceof SetterCall.Refused;
    }
    TypeMirror setType =
        stepType(ProcessorUtils.returnTypeIn(typeUtils, (DeclaredType) builderType, bound));
    if (setType == null || setType.getKind() == TypeKind.ERROR) {
      return false;
    }
    TypeElement setElement = elementOf(setType);
    if (setElement == null) {
      reportBuilderStep(
          method,
          signatureOn((DeclaredType) builderType, bound),
          setType,
          "a builder to build from");
      return true;
    }
    if (reportsUnnameableStep(
        method,
        setElement,
        signatureOn((DeclaredType) builderType, bound),
        sourceType,
        targetPackage)) {
      return true;
    }
    ExecutableElement build = accessorNamed(callableMembers(setElement, targetPackage), buildName);
    if (build == null) {
      reportMissingAccessor(
          method,
          "@ViaBuilder",
          setType,
          setElement,
          buildName,
          "finish the value it rebuilds",
          "the '" + ProcessorUtils.simpleTypeName(setType) + "' the setter hands back",
          "Set @ViaBuilder's 'build' to a method '"
              + ProcessorUtils.simpleTypeName(setType)
              + "' declares.",
          targetPackage);
      return true;
    }
    TypeMirror built = ProcessorUtils.returnTypeIn(typeUtils, (DeclaredType) setType, build);
    if (typeUtils.isAssignable(built, sourceType)) {
      return false;
    }
    String source = ProcessorUtils.simpleTypeName(sourceType);
    String builtName = ProcessorUtils.simpleTypeName(built);
    Diagnostics.error(
        messager,
        method,
        "@ViaBuilder",
        "'" + buildName + "()' returns '" + builtName + "', not the source type '" + source + "'.",
        "The generated lens finishes with '"
            + buildName
            + "()' and hands its result back as the source type '"
            + source
            + "', which '"
            + builtName
            + "' is not.",
        "Set @ViaBuilder's 'build' to the method that finishes a '"
            + source
            + "', or "
            + rebuildWith(source, "@ViaBuilder")
            + ".");
    return true;
  }

  /**
   * Reports a builder step that hands back a type the generated class cannot name, and returns
   * whether it did: its members may all be public, and a call on a type out of reach is a compile
   * error in a file its author never wrote.
   */
  private boolean reportsUnnameableStep(
      ExecutableElement method,
      TypeElement step,
      String stepCall,
      DeclaredType sourceType,
      String targetPackage) {
    if (isVisibleFrom(step, targetPackage)) {
      return false;
    }
    Diagnostics.error(
        messager,
        method,
        "@ViaBuilder",
        "'"
            + step.getSimpleName()
            + "', which '"
            + stepCall
            + "' hands back, cannot be named from '"
            + targetPackage
            + "'.",
        "The generated lens rebuilds through that type, and a class it cannot see is a compile"
            + " error in a file its author never wrote.",
        "Make '"
            + step.getSimpleName()
            + "' public, or "
            + rebuildWith(ProcessorUtils.simpleTypeName(sourceType), "@ViaBuilder")
            + ".");
    return true;
  }

  /** Reports a builder step that hands back something no further call can be made on. */
  private void reportBuilderStep(
      ExecutableElement method, String step, TypeMirror handedBack, String wanted) {
    String handedBackName = ProcessorUtils.simpleTypeName(handedBack);
    Diagnostics.error(
        messager,
        method,
        "@ViaBuilder",
        "'" + step + "' hands back '" + handedBackName + "', which is not " + wanted + ".",
        "The generated lens rebuilds with"
            + " 'source.toBuilder().setter(newValue).build()', and each step is called on what the"
            + " step before it hands back.",
        "Name the builder methods this type's chain declares, or rebuild it with @Wither,"
            + " @ViaConstructor or @ViaCopyAndSet.");
  }

  /**
   * Reports a {@code @ViaConstructor} parameter order naming an accessor the source type does not
   * have, and returns whether it did.
   *
   * <p>Every name but the lens's own reads an argument, {@code source.x()}, for the constructor
   * call the lens rebuilds with.
   *
   * @param method the annotated lens method, for error reporting
   * @param sourceType the source type {@code S}, as the spec names it
   * @param sourceTypeElement the element of {@code S}, whose members are searched
   * @param parameterOrder the names the annotation carries
   * @param targetPackage the package the optics class is generated into
   * @return true when one of them names no accessor, and an error was reported
   */
  private boolean rebuildsThroughUnreadableParameter(
      ExecutableElement method,
      DeclaredType sourceType,
      TypeElement sourceTypeElement,
      String[] parameterOrder,
      String targetPackage) {
    if (sourceType.getKind() == TypeKind.ERROR) {
      return false;
    }
    String fieldName = method.getSimpleName().toString();
    if (parameterOrder.length > 0
        && Arrays.stream(parameterOrder).noneMatch(parameter -> parameter.equals(fieldName))) {
      Diagnostics.error(
          messager,
          method,
          "@ViaConstructor",
          "'parameterOrder' names no argument for the lens's own '" + fieldName + "'.",
          "The generated lens rebuilds '"
              + ProcessorUtils.simpleTypeName(sourceType)
              + "' from the order given, and passes the value it sets where the lens's own name"
              + " stands; naming it nowhere would set nothing.",
          "Add '"
              + fieldName
              + "' to @ViaConstructor's 'parameterOrder', at the place the constructor takes it.");
      return true;
    }
    List<ExecutableElement> members = callableMembers(sourceTypeElement, targetPackage);
    for (String parameter : parameterOrder) {
      if (parameter.equals(fieldName) || accessorNamed(members, parameter) != null) {
        continue;
      }
      reportMissingAccessor(
          method,
          "@ViaConstructor",
          sourceType,
          sourceTypeElement,
          parameter,
          "read the argument '" + parameter + "'",
          "'source'",
          "Name in @ViaConstructor's 'parameterOrder' the accessors '"
              + ProcessorUtils.simpleTypeName(sourceType)
              + "' declares, in the order its constructor takes them.",
          targetPackage);
      return true;
    }
    return false;
  }

  /**
   * Reports a {@code @Wither} whose generated call does not bind a method that rebuilds the source
   * type, and returns whether it did.
   *
   * <p>The generated lens sets through {@code source.withX(newValue)}, with {@code newValue} typed
   * by the lens's focus, and javac picks among the methods of that name by that type. So it is the
   * method the call binds, as {@link WitherBinding} repeats javac's choice, that is checked, not
   * any method that carries the name. The call has to bind one method, an instance method, since a
   * static one never reads the value it is called on, and that method has to hand back the source
   * type. Every method of the name that the generated class can reach is weighed, of any arity,
   * static or not, as javac weighs them.
   *
   * <p>Where the choice rests on inference that is not repeated, the candidates are read leniently:
   * the name passes when one of them is an instance method that hands back the source type, and
   * javac settles the rest at the generated call. A wildcard focus is read that way too, since the
   * type such a lens infers is drawn from the getter as much as from the wildcard.
   *
   * @param method the annotated lens method, for error reporting
   * @param sourceType the source type {@code S}, as the spec names it
   * @param sourceTypeElement the element of {@code S}, whose members are searched
   * @param focusType the lens's focus type, which the wither is called with
   * @param witherName the method the annotation names
   * @param targetPackage the package the optics class is generated into
   * @return true when the call binds no such method, and an error was reported; a source type that
   *     did not resolve is left to javac
   */
  private boolean rebuildsThroughUnusableWither(
      ExecutableElement method,
      DeclaredType sourceType,
      TypeElement sourceTypeElement,
      TypeMirror focusType,
      String witherName,
      String targetPackage) {
    // A source type that did not resolve has no members to read, and javac's own error names the
    // type that is missing, which is the one worth reading.
    if (sourceType.getKind() == TypeKind.ERROR) {
      return false;
    }
    List<ExecutableElement> members =
        ElementFilter.methodsIn(elementUtils.getAllMembers(sourceTypeElement));
    List<ExecutableElement> callable =
        members.stream().filter(member -> callableOn(member, targetPackage)).toList();
    List<ExecutableElement> named =
        callable.stream()
            .filter(member -> member.getSimpleName().contentEquals(witherName))
            .toList();
    if (named.isEmpty()) {
      boolean declared =
          members.stream().anyMatch(member -> member.getSimpleName().contentEquals(witherName));
      reportMissingWither(
          method, sourceType, callable, focusType, witherName, targetPackage, declared);
      return true;
    }
    // A wildcard focus is inferred from the getter's type as much as from the wildcard itself, so
    // the type the call passes is not the one the spec writes, and which method it binds is
    // javac's to settle.
    if (focusType.getKind() == TypeKind.WILDCARD) {
      return noCandidateRebuilds(
          method,
          sourceType,
          named.stream().filter(candidate -> candidate.getParameters().size() == 1).toList(),
          null);
    }
    return switch (WitherBinding.resolve(typeUtils, sourceType, focusType, named)) {
      case WitherBinding.Binds(ExecutableElement bound) -> {
        if (bound.getModifiers().contains(Modifier.STATIC)) {
          reportStaticWither(method, sourceType, bound, focusType);
          yield true;
        }
        if (ProcessorUtils.returnsOwner(typeUtils, sourceType, bound)) {
          yield false;
        }
        // Only an overloaded name needs the focus to say which method it binds.
        reportWitherOfAnotherType(method, sourceType, bound, named.size() > 1 ? focusType : null);
        yield true;
      }
      case WitherBinding.NoneApplies() -> {
        reportInapplicableWither(method, sourceType, focusType, named);
        yield true;
      }
      case WitherBinding.Ambiguous(List<ExecutableElement> methods) -> {
        reportAmbiguousWither(method, sourceType, focusType, methods);
        yield true;
      }
      case WitherBinding.Undecided(List<ExecutableElement> candidates) ->
          noCandidateRebuilds(method, sourceType, candidates, focusType);
    };
  }

  /**
   * Reports a name none of whose candidates rebuilds the source type, and returns whether it did.
   *
   * <p>This is the lenient reading, for a call whose binding rests on inference the checks do not
   * repeat. Whichever candidate javac settles on has to be an instance method that hands the source
   * type back, so the name passes as soon as one of them is, and is refused when none is: a static
   * method never reads the value it is called on, and another type does not compile where the lens
   * hands its result back as the source type.
   *
   * @param method the annotated lens method, for error reporting
   * @param sourceType the source type {@code S}, as the spec names it
   * @param candidates the methods the call might bind; an empty list is left alone, since the call
   *     then binds nothing these checks can read and javac reports it
   * @param choosing the focus that chose among them, or null where no one type did
   * @return true when none of them rebuilds the source type, and an error was reported
   */
  private boolean noCandidateRebuilds(
      ExecutableElement method,
      DeclaredType sourceType,
      List<ExecutableElement> candidates,
      TypeMirror choosing) {
    if (candidates.isEmpty()) {
      return false;
    }
    List<ExecutableElement> rebuilding =
        candidates.stream()
            .filter(candidate -> ProcessorUtils.returnsOwner(typeUtils, sourceType, candidate))
            .toList();
    if (rebuilding.isEmpty()) {
      reportWitherOfAnotherType(method, sourceType, candidates.getFirst(), null);
      return true;
    }
    if (rebuilding.stream()
        .anyMatch(candidate -> !candidate.getModifiers().contains(Modifier.STATIC))) {
      return false;
    }
    reportStaticWither(method, sourceType, rebuilding.getFirst(), choosing);
    return true;
  }

  /**
   * Whether the generated class can call a member on the source type.
   *
   * <p>The call names no type but the source's own, so it is the member's own access that decides,
   * not where it was declared: a {@code public} method a package-private class declares is called
   * through the public type that inherits it, as javac calls it. {@code protected} counts as
   * package access, since a generated companion extends nothing.
   */
  private boolean callableOn(ExecutableElement member, String targetPackage) {
    Set<Modifier> modifiers = member.getModifiers();
    if (modifiers.contains(Modifier.PRIVATE)) {
      return false;
    }
    return modifiers.contains(Modifier.PUBLIC)
        || elementUtils.getPackageOf(member).getQualifiedName().contentEquals(targetPackage);
  }

  /**
   * Reports a {@code @Wither} naming no method the generated class can call, offering the source
   * type's withers: its one-parameter instance methods that hand it back.
   */
  private void reportMissingWither(
      ExecutableElement method,
      DeclaredType sourceType,
      List<ExecutableElement> callable,
      TypeMirror focusType,
      String witherName,
      String targetPackage,
      boolean declared) {
    String source = ProcessorUtils.simpleTypeName(sourceType);
    List<ExecutableElement> withers =
        callable.stream()
            .filter(
                member ->
                    member.getParameters().size() == 1
                        && !member.getModifiers().contains(Modifier.STATIC)
                        && ProcessorUtils.returnsOwner(typeUtils, sourceType, member))
            .toList();
    String sets =
        "The generated lens sets through 'source."
            + witherName
            + "(newValue)', so it needs a method of that name "
            + (declared
                ? "the generated class in '"
                    + targetPackage
                    + "' can call, and '"
                    + witherName
                    + "' is declared where it cannot."
                : "on '" + source + "', declared or inherited.");
    if (withers.isEmpty()) {
      Diagnostics.error(
          messager,
          method,
          "@Wither",
          "'" + source + "' has no method '" + witherName + "' for the generated lens to call.",
          sets + " No one-parameter instance method of '" + source + "' hands it back.",
          ProcessorUtils.capitalise(rebuildWith(source, "@Wither")) + ".");
      return;
    }
    List<String> names =
        withers.stream()
            .map(member -> member.getSimpleName().toString())
            .distinct()
            .sorted()
            .toList();
    List<String> signatures =
        withers.stream()
            .map(member -> signatureOn(sourceType, member))
            .distinct()
            .sorted()
            .toList();
    Diagnostics.error(
        messager,
        method,
        "@Wither",
        "'" + source + "' has no method '" + witherName + "' for the generated lens to call.",
        sets
            + ProcessorUtils.nearestName(witherName, names)
                .map(near -> " Did you mean '" + near + "'?")
                .orElse("")
            + " Withers found on '"
            + source
            + "': "
            + signatures
            + ".",
        "Name one of the withers found on '"
            + source
            + "' that takes the value '"
            + ProcessorUtils.simpleTypeName(focusType)
            + "' the getter reads, or "
            + rebuildWith(source, "@Wither")
            + ".");
  }

  /** Reports a {@code @Wither} none of whose methods takes the value the lens sets. */
  private void reportInapplicableWither(
      ExecutableElement method,
      DeclaredType sourceType,
      TypeMirror focusType,
      List<ExecutableElement> named) {
    String source = ProcessorUtils.simpleTypeName(sourceType);
    String witherName = named.getFirst().getSimpleName().toString();
    String found =
        settingThrough(witherName, focusType)
            + ". Found on '"
            + source
            + "': "
            + named.stream().map(member -> signatureOn(sourceType, member)).toList()
            + ".";
    // A parameter typed by one of the source's wildcards takes no value whatever the focus, so the
    // remedy there is the source type, not the focus.
    boolean wildcardSource =
        sourceType.getTypeArguments().stream()
            .anyMatch(typeArgument -> typeArgument.getKind() == TypeKind.WILDCARD);
    Diagnostics.error(
        messager,
        method,
        "@Wither",
        "No method '"
            + witherName
            + "' of '"
            + source
            + "' takes the lens's focus type '"
            + ProcessorUtils.simpleTypeName(focusType)
            + "'.",
        wildcardSource
            ? found
                + " A parameter a wildcard of '"
                + source
                + "' stands in takes no value at all, since the type it stands for is unknown."
            : found,
        (wildcardSource
                ? "Declare the spec over the type each wildcard stands for, or "
                    + rebuildWith(source, "@Wither")
                : "Name a wither that takes the value the getter reads, or point 'getter' at an"
                    + " accessor one of them takes and declare the focus as its type; otherwise "
                    + rebuildWith(source, "@Wither"))
            + ".");
  }

  /** Reports a {@code @Wither} whose call takes more than one method equally well. */
  private void reportAmbiguousWither(
      ExecutableElement method,
      DeclaredType sourceType,
      TypeMirror argument,
      List<ExecutableElement> methods) {
    String source = ProcessorUtils.simpleTypeName(sourceType);
    String witherName = methods.getFirst().getSimpleName().toString();
    List<String> signatures =
        methods.stream().map(member -> "'" + signatureOn(sourceType, member) + "'").toList();
    Diagnostics.error(
        messager,
        method,
        "@Wither",
        "The generated call to '"
            + witherName
            + "' cannot choose between "
            + String.join(", ", signatures.subList(0, signatures.size() - 1))
            + " and "
            + signatures.getLast()
            + ".",
        settingThrough(witherName, argument)
            + ", which each of them takes, with no parameter more specific than every other.",
        "Declare the lens's focus as the parameter type of the one you mean, or "
            + rebuildWith(source, "@Wither")
            + ".");
  }

  /**
   * Reports a {@code @Wither} whose call binds a static method: a static method never reads the
   * value it is called on, so it cannot rebuild it.
   */
  private void reportStaticWither(
      ExecutableElement method,
      DeclaredType sourceType,
      ExecutableElement bound,
      TypeMirror choosing) {
    String source = ProcessorUtils.simpleTypeName(sourceType);
    String signature = signatureOn(sourceType, bound);
    String sets =
        choosing == null
            ? "The generated lens sets through '" + signature + "'"
            : settingThrough(bound.getSimpleName().toString(), choosing)
                + ", which binds '"
                + signature
                + "'";
    Diagnostics.error(
        messager,
        method,
        "@Wither",
        "'"
            + signature
            + "' is static, so the generated lens cannot rebuild a '"
            + source
            + "' through it.",
        sets + ", and a static method never reads the '" + source + "' it is called on.",
        "Declare the lens's focus as the parameter type of an instance overload, name an instance"
            + " wither, or "
            + rebuildWith(source, "@Wither")
            + ".");
  }

  /**
   * Reports a {@code @Wither} whose call binds a method that hands back something other than the
   * source type.
   *
   * <p>The generated set function returns what the wither returns, as the source type, so the
   * wither has to hand back that type as {@link ProcessorUtils#returnsOwner} reads it. A supertype,
   * or the type under other arguments ({@code Draft<String>} read on a {@code Draft<T>}), does not
   * compile there, and a raw return is an unchecked conversion in a file the author cannot edit.
   * The wither is read on the source type as the spec names it, so {@code Draft<String>
   * withId(String)} serves an {@code OpticsSpec<Draft<String>>} as it should.
   *
   * @param method the annotated lens method, for error reporting
   * @param sourceType the source type {@code S}, as the spec names it
   * @param bound the method the call binds
   * @param choosing the argument that chose {@code bound} among overloads of its name, or null when
   *     it has none
   */
  private void reportWitherOfAnotherType(
      ExecutableElement method,
      DeclaredType sourceType,
      ExecutableElement bound,
      TypeMirror choosing) {
    String source = ProcessorUtils.simpleTypeName(sourceType);
    String signature = signatureOn(sourceType, bound);
    String returned =
        ProcessorUtils.simpleTypeName(ProcessorUtils.returnTypeIn(typeUtils, sourceType, bound));
    String sets =
        choosing == null
            ? "The generated lens sets through '" + signature + "'"
            : settingThrough(bound.getSimpleName().toString(), choosing)
                + ", which binds '"
                + signature
                + "',";
    Diagnostics.error(
        messager,
        method,
        "@Wither",
        "'" + signature + "' returns '" + returned + "', not the source type '" + source + "'.",
        sets
            + " and hands its result back as the source type '"
            + source
            + "', which '"
            + returned
            + "' is not.",
        "Name a wither that returns '"
            + source
            + "', or declare the spec over the type the wither does return when that is an"
            + " instantiation of the same class; otherwise "
            + rebuildWith(source, "@Wither")
            + ".");
  }

  /** The remedy every wither refusal ends on, for a diagnostic's fix: an unfinished clause. */
  private static String rebuildWith(String source, String tag) {
    List<String> others =
        List.of("@Wither", "@ViaBuilder", "@ViaConstructor", "@ViaCopyAndSet").stream()
            .filter(strategy -> !strategy.equals(tag))
            .toList();
    return "rebuild '"
        + source
        + "' with "
        + String.join(", ", others.subList(0, others.size() - 1))
        + " or "
        + others.getLast();
  }

  /** How the generated setter calls the wither, for a diagnostic's reason: an unfinished clause. */
  private static String settingThrough(String witherName, TypeMirror argument) {
    return "The generated lens sets through 'source."
        + witherName
        + "(newValue)' with the new value typed '"
        + ProcessorUtils.simpleTypeName(argument)
        + "'";
  }

  /** A method as the source type sees it, {@code withId(String)}, telling overloads apart. */
  private String signatureOn(DeclaredType sourceType, ExecutableElement member) {
    List<? extends TypeMirror> parameters =
        ProcessorUtils.memberOf(typeUtils, sourceType, member).getParameterTypes();
    List<String> names =
        new ArrayList<>(parameters.stream().map(ProcessorUtils::simpleTypeName).toList());
    if (member.isVarArgs()) {
      // The last parameter is an array the author wrote as a variable-arity one.
      String last = names.getLast();
      names.set(names.size() - 1, last.substring(0, last.length() - "[]".length()) + "...");
    }
    return member.getSimpleName()
        + String.join(", ", names).transform(joined -> "(" + joined + ")");
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

    TypeMirror walked = ProcessorUtils.supertypeOf(typeUtils, sourceType, parameterElement);
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
   * <p>Unlike {@link #memberTypeOf} this does not guard on {@link
   * ProcessorUtils#carriesInstantiation}, and needs no guard: a source type naming a raw type is
   * refused at the spec's declaration (#771) before any member is read, so every type reaching here
   * supplies its arguments or has none to supply. A guard would bury a fault the gate already
   * reports.
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

  // ----- Prism Hint Parsing -----

  private record PrismHintResult(PrismHintKind kind, PrismHintInfo info) {}

  private Optional<PrismHintResult> parsePrismHint(
      ExecutableElement method,
      TypeMirror sourceType,
      TypeElement sourceTypeElement,
      TypeMirror focusType,
      TypeElement specInterface) {
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
      TypeElement unnameable = InstanceOfNarrowing.unnameableElement(targetType);
      if (unnameable != null) {
        reportUnnameableInstanceOfTarget(method, specInterface, sourceType, targetType, unnameable);
        return Optional.empty();
      }
      // The class constant is raw, so the arguments of the type handed back are only ever the
      // ones the source pins down. What the test earns is what the prism may promise.
      var narrowing = instanceOfNarrowing.narrow(targetType, sourceType, sourceTypeElement);
      if (!typeUtils.isAssignable(narrowing.testedType(), focusType)) {
        reportUntestableInstanceOf(method, specInterface, sourceType, focusType, narrowing);
        return Optional.empty();
      }
      return Optional.of(
          new PrismHintResult(
              PrismHintKind.INSTANCE_OF, PrismHintInfo.forInstanceOf(narrowing.testedType())));
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

  /**
   * Reports a prism whose focus type cannot rebuild the source it was narrowed from.
   *
   * <p>A generated prism is built with identity as its build side, so it hands back exactly the
   * value the getter read. That only stands up {@code Prism.build} when the focus is a type the
   * source accepts. A focus that is a value rather than a variant of the source - {@code
   * Prism<JsonNode, String>} - has no build side the processor could write, and reaches javac as an
   * error inside a file the author never wrote (issue #755).
   *
   * @param method the offending optic method
   * @param specInterface the spec declaring it, for the name the user reads
   * @param sourceType the source type {@code S}
   * @param focusType the focus type the prism promises
   */
  private void reportFocusThatCannotBuildTheSource(
      ExecutableElement method,
      TypeElement specInterface,
      TypeMirror sourceType,
      TypeMirror focusType) {

    String source = ProcessorUtils.simpleTypeName(sourceType);
    Diagnostics.error(
        messager,
        method,
        "@ImportOptics",
        "'"
            + specInterface.getSimpleName()
            + "."
            + method.getSimpleName()
            + "' focuses '"
            + ProcessorUtils.simpleTypeName(focusType)
            + "', which is not a '"
            + source
            + "'.",
        "A prism runs both ways, and the generated one builds back with identity: it returns the"
            + " value it narrowed. That is only a '"
            + source
            + "' when the focus is one, and nothing else here could rebuild one.",
        "Focus a type that is a '"
            + source
            + "' - the variant, not the value it carries - or, where the value is the point, write"
            + " the prism by hand with Prism.of and a build side that constructs a '"
            + source
            + "'.");
  }

  /**
   * Reports an {@code @InstanceOf} target the generated test could not name.
   *
   * @param method the offending optic method
   * @param specInterface the spec declaring it, for the name the user reads
   * @param sourceType the source type {@code S}
   * @param targetType the class the annotation names
   * @param unnameable the element within it that cannot be named, which an array target holds one
   *     layer down
   */
  private void reportUnnameableInstanceOfTarget(
      ExecutableElement method,
      TypeElement specInterface,
      TypeMirror sourceType,
      TypeMirror targetType,
      TypeElement unnameable) {

    Diagnostics.error(
        messager,
        method,
        "@InstanceOf",
        "'"
            + specInterface.getSimpleName()
            + "."
            + method.getSimpleName()
            + "' names '"
            + ProcessorUtils.simpleTypeName(targetType)
            + "', which carries type parameters of its own and is a member of a generic type.",
        "The test names the type it checks, and a member of a generic type cannot be written with"
            + " its own type arguments unless the enclosing type is written with its, which an"
            + " instanceof cannot do.",
        "Declare '"
            + unnameable.getSimpleName()
            + "' static, so that it can be named on its own, or narrow through a predicate and"
            + " getter of '"
            + ProcessorUtils.simpleTypeName(sourceType)
            + "' with @MatchWhen.");
  }

  /**
   * Reports an {@code @InstanceOf} prism whose focus type the test cannot narrow to.
   *
   * <p>Two ways to get there, and they want different remedies. The class constant is raw, so a
   * target parameter the source type pins nothing to is checked by nothing: {@code Circle<X>
   * extends Shape} narrowed from {@code Shape} passes for every instantiation, and a focus of
   * {@code Circle<T>} would hand the wrong one back to fail on the first read (issue #733). The
   * other way is a target that simply is not one the focus accepts, which is a naming mistake.
   *
   * @param method the offending optic method
   * @param specInterface the spec declaring it, for the name the user reads
   * @param sourceType the source type {@code S}
   * @param focusType the focus type the prism promises
   * @param narrowing what the test earns, and what it left free
   */
  private void reportUntestableInstanceOf(
      ExecutableElement method,
      TypeElement specInterface,
      TypeMirror sourceType,
      TypeMirror focusType,
      InstanceOfNarrowing.Narrowing narrowing) {

    String declaration = specInterface.getSimpleName() + "." + method.getSimpleName();
    String tested = ProcessorUtils.simpleTypeName(narrowing.testedType());
    String focus = ProcessorUtils.simpleTypeName(focusType);

    if (!narrowing.freeParameters().isEmpty()
        && typeUtils.isAssignable(
            typeUtils.erasure(narrowing.testedType()), typeUtils.erasure(focusType))) {
      Diagnostics.error(
          messager,
          method,
          "@InstanceOf",
          "'"
              + declaration
              + "' declares its focus as '"
              + focus
              + "', which the test cannot"
              + " narrow to.",
          "@InstanceOf carries a class constant, which is raw, so the test runs after erasure and"
              + " '"
              + ProcessorUtils.simpleTypeName(sourceType)
              + "' pins nothing to "
              + String.join(", ", narrowing.freeParameters())
              + ": every instantiation passes it, and would be handed back as '"
              + focus
              + "' to fail on the first read.",
          "Narrow through a predicate and getter of '"
              + ProcessorUtils.simpleTypeName(sourceType)
              + "' with @MatchWhen, which reads the argument off the source rather than inventing"
              + " it, or declare the focus as '"
              + tested
              + "', which is what the test earns.");
      return;
    }

    Diagnostics.error(
        messager,
        method,
        "@InstanceOf",
        "'" + declaration + "' narrows to '" + tested + "', which is not a '" + focus + "'.",
        "The prism hands back the value the test narrowed, so the class the annotation names has"
            + " to be one the focus type accepts.",
        "Name the class the focus declares in @InstanceOf, or declare the focus as a supertype of"
            + " '"
            + tested
            + "'.");
  }

  // ----- Traversal Hint Parsing -----

  private record TraversalHintResult(TraversalHintKind kind, TraversalHintInfo info) {}

  private Optional<TraversalHintResult> parseTraversalHint(
      ExecutableElement method,
      TypeMirror sourceType,
      TypeElement specInterface,
      TypeMirror focusType) {
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
        Optional<AutoDetectedTraversal> autoDetected =
            autoDetectTraversalForField(fieldName, sourceType, method, specInterface, focusType);
        if (autoDetected.isEmpty()) {
          // Error already reported in autoDetectTraversalForField
          return Optional.empty();
        }
        return Optional.of(
            new TraversalHintResult(
                TraversalHintKind.THROUGH_FIELD,
                autoDetected.get().checkedComposition()
                    ? TraversalHintInfo.forCheckedThroughField(
                        fieldName,
                        autoDetected.get().reference(),
                        autoDetected.get().lensFocus(),
                        autoDetected.get().lens().type(),
                        autoDetected.get().lens().declared())
                    : TraversalHintInfo.forThroughField(
                        fieldName, autoDetected.get().reference())));
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
   * <p>The type detected from is the one the generated composition is typed against: the focus of
   * the spec's own lens for the field, which the composition calls. A getter may return {@code
   * ArrayList} behind a {@code Lens<S, List<String>>}, and it is the lens the traversal composes
   * with; a spec declaring no such lens is refused, since the generated file could only fail.
   *
   * @param fieldName the name of the field to look up
   * @param sourceType the instantiated source type containing the field
   * @param method the method element (for error reporting)
   * @param specInterface the spec interface, whose lens for the field the traversal composes with
   * @param focusType the focus the traversal method declares, held to the container's element
   * @return the traversal and whether its composition is javac-checked, or empty if detection
   *     failed
   */
  private Optional<AutoDetectedTraversal> autoDetectTraversalForField(
      String fieldName,
      TypeMirror sourceType,
      ExecutableElement method,
      TypeElement specInterface,
      TypeMirror focusType) {

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

    // The generated traversal composes through the spec's own lens for the field, so the lens
    // decides the type detected from: a getter may return ArrayList behind a Lens<S, List<String>>,
    // and it is the lens the traversal composes with.
    LensMember lens = declaredLens(specInterface, fieldName);
    if (lens == null) {
      Diagnostics.error(
          messager,
          method,
          "@ThroughField",
          "'"
              + method.getEnclosingElement().getSimpleName()
              + "."
              + method.getSimpleName()
              + "' composes through a lens named '"
              + fieldName
              + "', which the spec does not declare",
          "The generated traversal calls the spec's own lens for the field and composes the"
              + " container traversal after it",
          "Declare a Lens method named '"
              + fieldName
              + "' on the spec, with its copy strategy, or use @TraverseWith for a traversal that"
              + " stands on its own");
      return Optional.empty();
    }
    fieldType = extractFocusType(lens.type());

    // The match is exact: the standard traversal rebuilds the interface type, which a field
    // declared as a concrete container could not take back.
    TypeKindAnalyser typeAnalyser = new TypeKindAnalyser(typeUtils);
    Optional<ContainerType> containerType = typeAnalyser.detectContainerType(fieldType);

    if (containerType.isEmpty()) {
      String containerInterface = concreteContainerInterface(fieldType);
      if (containerInterface != null) {
        Diagnostics.error(
            messager,
            method,
            "@ThroughField",
            "'"
                + method.getEnclosingElement().getSimpleName()
                + "."
                + method.getSimpleName()
                + "' reaches field '"
                + fieldName
                + "', which is declared as '"
                + ProcessorUtils.simpleTypeName(fieldType)
                + "' rather than as the "
                + containerInterface
                + " interface",
            "The standard "
                + containerInterface
                + " traversal promises no more than a "
                + containerInterface
                + ", so what it rebuilds is not guaranteed to be "
                + withArticle(ProcessorUtils.simpleTypeName(typeUtils.erasure(fieldType)))
                + ", and a field it cannot be handed back to would throw ClassCastException on"
                + " first use",
            "Name a traversal that rebuilds it, for example @ThroughField(field = \""
                + fieldName
                + "\", traversal = \"com.example.MyTraversals.for"
                // A declared type: concreteContainerInterface answered, so asElement is not null.
                + typeUtils.asElement(fieldType).getSimpleName()
                + "()\") built with Traversals.forIterableCollecting or"
                + " Traversals.forMapValuesCollecting, or, where the type is yours, declare the"
                + " field as "
                + containerInterface);
        return Optional.empty();
      }
      error(
          "Cannot auto-detect traversal for field '"
              + fieldName
              + "' of type '"
              + ProcessorUtils.simpleTypeName(fieldType)
              + "'. "
              + "Supported types: List, Set, Collection, Optional, Map, arrays. "
              + "Please specify traversal() explicitly, e.g.: "
              + "@ThroughField(field = \""
              + fieldName
              + "\", traversal = \"MyTraversals.custom()\")",
          method);
      return Optional.empty();
    }

    // Traversals.forArray() traverses an Object[]; a primitive array is not one, and would throw
    // on first use exactly as a narrower container would.
    if (containerType.get().kind() == ContainerType.Kind.ARRAY
        && containerType.get().elementType().getKind().isPrimitive()) {
      Diagnostics.error(
          messager,
          method,
          "@ThroughField",
          "'"
              + method.getEnclosingElement().getSimpleName()
              + "."
              + method.getSimpleName()
              + "' reaches field '"
              + fieldName
              + "', which is declared as '"
              + ProcessorUtils.simpleTypeName(fieldType)
              + "', an array of a primitive",
          "The standard array traversal traverses an Object array, which "
              + withArticle(ProcessorUtils.simpleTypeName(fieldType))
              + " is not, so the generated traversal would throw ClassCastException on first use",
          "Name a traversal that rebuilds "
              + withArticle(ProcessorUtils.simpleTypeName(fieldType))
              + " with @ThroughField(field = \""
              + fieldName
              + "\", traversal = \"...\"), or, where the type is yours, declare the field as an"
              + " array of the boxed type");
      return Optional.empty();
    }

    // What the standard traversal hands back is what the method's focus must contain: any other
    // declaration compiles only through the raw cast, throwing ClassCastException on first use
    // where it narrows and letting ill-typed writes through where it widens. The element is read
    // through an extends-wildcard, since that bound is what the traversal focuses; a super or
    // unbounded wildcard reads back as Object, and the focus is held to that. Containment rather
    // than sameness, so a focus declared as a wildcard over the element ('? extends
    // CharSequence' over CharSequence elements) stays accepted, as it always compiled.
    TypeMirror element = ProcessorUtils.resolveWildcard(containerType.get().elementType());
    TypeMirror handedBack =
        element != null ? element : elementUtils.getTypeElement("java.lang.Object").asType();
    if (!typeUtils.contains(focusType, handedBack)) {
      Diagnostics.error(
          messager,
          method,
          "@ThroughField",
          "'"
              + method.getEnclosingElement().getSimpleName()
              + "."
              + method.getSimpleName()
              + "' declares focus '"
              + ProcessorUtils.simpleTypeName(focusType)
              + "' over field '"
              + fieldName
              + "' of type '"
              + ProcessorUtils.simpleTypeName(fieldType)
              + "', whose "
              + handedBackNoun(containerType.get().kind())
              + " the standard traversal hands back as '"
              + ProcessorUtils.simpleTypeName(handedBack)
              + "'",
          "A focus that does not contain that type could only compile through a cast, throwing"
              + " ClassCastException on first use where it narrows and letting ill-typed writes"
              + " through where it widens",
          "Declare the focus as '"
              + ProcessorUtils.simpleTypeName(handedBack)
              + "', or name a traversal of your own with @ThroughField(field = \""
              + fieldName
              + "\", traversal = \"...\")");
      return Optional.empty();
    }

    // Get the standard traversal reference for this container type
    TraversalCodeGenerator traversalGenerator = new TraversalCodeGenerator();
    String traversalRef = traversalGenerator.getStandardTraversal(containerType.get().kind());

    // A denotable lens focus is a candidate for the uncast composition, so javac checks what
    // the cast used to hide; one whose own type arguments carry a wildcard cannot name its
    // instantiation and keeps the cast (a nested List<List<?>> is denotable and stays checked).
    // Whether the candidate is taken is the generator's decision, made against the type
    // parameters it declares on the method.
    boolean checked = !ProcessorUtils.hasUndenotableTypeArguments(fieldType);

    return Optional.of(new AutoDetectedTraversal(traversalRef, checked, fieldType, lens));
  }

  /** The word the mismatch diagnostic uses for what a container's traversal hands back. */
  private static String handedBackNoun(ContainerType.Kind kind) {
    return switch (kind) {
      case MAP -> "values";
      case OPTIONAL -> "element";
      default -> "elements";
    };
  }

  /**
   * An auto-detected traversal and whether its composition may be emitted without the raw cast.
   *
   * @param reference the standard traversal for the field's container interface
   * @param checkedComposition true when the lens focus is denotable, making the method a candidate
   *     for the uncast composition
   * @param lensFocus the focus the spec's lens for the field declares
   * @param lens that lens, as the spec has it and as its method declares it
   */
  private record AutoDetectedTraversal(
      String reference, boolean checkedComposition, TypeMirror lensFocus, LensMember lens) {}

  /**
   * A lens method of the spec: its return type read under the spec's instantiation, and as the
   * method declares it, which keeps what reading under the instantiation drops.
   *
   * @param type the lens type under the spec's instantiation
   * @param declared the lens type as its method declares it
   */
  private record LensMember(DeclaredType type, TypeMirror declared) {}

  /**
   * The spec's own lens for {@code fieldName}, or null when the spec declares no lens by that name
   * (or a raw one).
   */
  private LensMember declaredLens(TypeElement specInterface, String fieldName) {
    DeclaredType specType = (DeclaredType) specInterface.asType();
    for (ExecutableElement member :
        ElementFilter.methodsIn(elementUtils.getAllMembers(specInterface))) {
      if (!member.getSimpleName().contentEquals(fieldName)) {
        continue;
      }
      TypeMirror returned =
          ((ExecutableType) typeUtils.asMemberOf(specType, member)).getReturnType();
      if (returned instanceof DeclaredType optic && determineOpticKind(optic) == OpticKind.LENS) {
        return new LensMember(optic, member.getReturnType());
      }
    }
    return null;
  }

  /** A type name with its indefinite article, for a diagnostic that reads as a sentence. */
  private static String withArticle(String typeName) {
    return ("AEIOUaeiou".indexOf(typeName.charAt(0)) >= 0 ? "an " : "a ") + typeName;
  }

  /**
   * The container interface a field of a narrower container type implements, in the order the
   * standard traversals distinguish them, or null when the field is not a container at all or is
   * raw (a raw container is refused for a different reason). A field declared as one of the
   * interfaces itself never reaches here: auto-detection accepted it.
   *
   * <p>A {@code Deque} answers {@code Collection}: it is a container, and the reason it cannot be
   * auto-detected is the same one an {@code ArrayList} has. A non-generic implementation ({@code
   * class Tags extends ArrayList<String>}) is not raw and answers too. The candidates are the JDK's
   * own types, which resolve in every round, so the lookups are not guarded.
   */
  private String concreteContainerInterface(TypeMirror fieldType) {
    if (fieldType.getKind() != TypeKind.DECLARED) {
      return null;
    }
    DeclaredType declared = (DeclaredType) fieldType;
    if (declared.getTypeArguments().isEmpty()
        && !((TypeElement) declared.asElement()).getTypeParameters().isEmpty()) {
      // Raw: a raw ArrayList sent to "declare it as List" would only meet the raw-List refusal
      // next, so the generic message names the whole remedy.
      return null;
    }
    TypeMirror erased = typeUtils.erasure(fieldType);
    for (String candidate : CONTAINER_INTERFACE_FQNS) {
      TypeElement candidateElement = elementUtils.getTypeElement(candidate);
      if (typeUtils.isSubtype(erased, typeUtils.erasure(candidateElement.asType()))) {
        return candidateElement.getSimpleName().toString();
      }
    }
    return null;
  }

  /**
   * A member's type as the instantiated source type sees it, unwrapping an accessor's return.
   *
   * <p>Read off the element, a member of {@code Holder<T>} speaks {@code T}; the spec instantiated
   * it as {@code Holder<List<String>>}, so what the traversal has to be detected for is {@code
   * List<String>}. Reading the declaration instead both rejects a container it could have found and
   * names a variable the spec never wrote.
   *
   * <p>The guard is why this is not {@link ProcessorUtils#memberOf} outright: that helper lets a
   * raw site erase, and erasing here rejected a container the spec had written (#738). The two
   * raw-site answers differ on purpose - see {@link ProcessorUtils#memberOf} for the map of which
   * reader wants which.
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
