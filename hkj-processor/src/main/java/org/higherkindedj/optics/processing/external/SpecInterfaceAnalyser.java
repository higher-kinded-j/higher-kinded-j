// Copyright (c) 2025 Magnus Smith
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
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
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

/**
 * Analyses spec interfaces extending {@code OpticsSpec<S>} to determine what optics to generate.
 *
 * <p>This analyser examines a spec interface and produces a {@link SpecAnalysis} that describes:
 *
 * <ul>
 *   <li>The source type {@code S} from {@code OpticsSpec<S>}
 *   <li>Abstract methods that need optic implementations generated
 *   <li>Default methods that should be copied to the generated class
 *   <li>Annotations and their parsed values for each method
 * </ul>
 */
public class SpecInterfaceAnalyser {

  private static final String OPTICS_SPEC_FQN = "org.higherkindedj.optics.annotations.OpticsSpec";
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
   * @param specInterface the interface extending OpticsSpec<S>
   * @return the analysis result, or empty if the interface is invalid
   */
  public Optional<SpecAnalysis> analyse(TypeElement specInterface) {
    // Verify it's an interface
    if (specInterface.getKind() != ElementKind.INTERFACE) {
      error("@ImportOptics on a type extending OpticsSpec must be an interface", specInterface);
      return Optional.empty();
    }

    // Extract source type from OpticsSpec<S>
    TypeMirror sourceType = extractSourceType(specInterface);
    if (sourceType == null) {
      error(
          "Cannot determine source type. Interface must extend OpticsSpec<S> "
              + "with a concrete type argument",
          specInterface);
      return Optional.empty();
    }

    TypeElement sourceTypeElement = (TypeElement) typeUtils.asElement(sourceType);
    if (sourceTypeElement == null) {
      error("Source type " + sourceType + " is not a valid type element", specInterface);
      return Optional.empty();
    }

    // Categorise methods
    List<OpticMethodInfo> opticMethods = new ArrayList<>();
    List<ExecutableElement> defaultMethods = new ArrayList<>();

    for (ExecutableElement method : ElementFilter.methodsIn(specInterface.getEnclosedElements())) {
      if (method.isDefault()) {
        defaultMethods.add(method);
      } else if (method.getModifiers().contains(Modifier.ABSTRACT)) {
        Optional<OpticMethodInfo> opticInfo = analyseOpticMethod(method, sourceType, specInterface);
        if (opticInfo.isPresent()) {
          opticMethods.add(opticInfo.get());
        } else {
          // Error already reported in analyseOpticMethod
          return Optional.empty();
        }
      }
      // Skip static methods, they're not relevant
    }

    return Optional.of(
        new SpecAnalysis(
            specInterface, sourceType, sourceTypeElement, opticMethods, defaultMethods));
  }

  /**
   * Extracts the source type {@code S} from {@code OpticsSpec<S>}.
   *
   * @param specInterface the interface to analyse
   * @return the source type, or null if not found
   */
  private TypeMirror extractSourceType(TypeElement specInterface) {
    for (TypeMirror superInterface : specInterface.getInterfaces()) {
      if (!(superInterface instanceof DeclaredType declaredType)) {
        continue;
      }

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
   * @param specInterface the spec interface (for error reporting)
   * @return the optic method info, or empty if invalid
   */
  private Optional<OpticMethodInfo> analyseOpticMethod(
      ExecutableElement method, TypeMirror sourceType, TypeElement specInterface) {

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
        var copyResult = parseCopyStrategy(method);
        if (copyResult.isEmpty()) {
          error(
              "Lens method '"
                  + method.getSimpleName()
                  + "' requires a copy strategy annotation: "
                  + "@ViaBuilder, @Wither, @ViaConstructor, or @ViaCopyAndSet",
              method);
          return Optional.empty();
        }
        copyStrategy = copyResult.get().kind();
        copyStrategyInfo = copyResult.get().info();
      }
      case PRISM -> {
        var prismResult = parsePrismHint(method, sourceType, specInterface);
        if (prismResult.isEmpty()) {
          error(
              "Prism method '"
                  + method.getSimpleName()
                  + "' requires a prism hint annotation: "
                  + "@InstanceOf or @MatchWhen",
              method);
          return Optional.empty();
        }
        prismHint = prismResult.get().kind();
        prismHintInfo = prismResult.get().info();
      }
      case TRAVERSAL -> {
        var traversalResult = parseTraversalHint(method, sourceType, specInterface);
        if (traversalResult.isEmpty()) {
          error(
              "Traversal method '"
                  + method.getSimpleName()
                  + "' requires a traversal hint annotation: "
                  + "@TraverseWith or @ThroughField",
              method);
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

  private Optional<CopyStrategyResult> parseCopyStrategy(ExecutableElement method) {
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

    // Check for @ViaConstructor
    AnnotationMirror viaConstructor = findAnnotation(method, VIA_CONSTRUCTOR_FQN);
    if (viaConstructor != null) {
      String[] parameterOrder = getAnnotationStringArray(viaConstructor, "parameterOrder");
      return Optional.of(
          new CopyStrategyResult(
              CopyStrategyKind.VIA_CONSTRUCTOR, CopyStrategyInfo.forConstructor(parameterOrder)));
    }

    // Check for @ViaCopyAndSet
    AnnotationMirror viaCopyAndSet = findAnnotation(method, VIA_COPY_AND_SET_FQN);
    if (viaCopyAndSet != null) {
      String copyConstructor = getAnnotationString(viaCopyAndSet, "copyConstructor", "");
      String setter = getAnnotationString(viaCopyAndSet, "setter", "");
      return Optional.of(
          new CopyStrategyResult(
              CopyStrategyKind.VIA_COPY_AND_SET,
              CopyStrategyInfo.forCopyAndSet(copyConstructor, setter)));
    }

    return Optional.empty();
  }

  // ----- Prism Hint Parsing -----

  private record PrismHintResult(PrismHintKind kind, PrismHintInfo info) {}

  private Optional<PrismHintResult> parsePrismHint(
      ExecutableElement method, TypeMirror sourceType, TypeElement specInterface) {
    // Check for @InstanceOf
    AnnotationMirror instanceOf = findAnnotation(method, INSTANCE_OF_FQN);
    if (instanceOf != null) {
      TypeMirror targetType = getAnnotationTypeMirror(instanceOf, "value");
      if (targetType != null) {
        // Validate subtype relationship (Decision 6)
        if (!typeUtils.isSubtype(targetType, sourceType)) {
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
            new PrismHintResult(
                PrismHintKind.INSTANCE_OF, PrismHintInfo.forInstanceOf(targetType)));
      }
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

    return Optional.empty();
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

    return Optional.empty();
  }

  /**
   * Auto-detects the appropriate traversal for a field based on its type.
   *
   * @param fieldName the name of the field to look up
   * @param sourceType the source type containing the field
   * @param method the method element (for error reporting)
   * @return the traversal reference string, or empty if detection failed
   */
  private Optional<String> autoDetectTraversalForField(
      String fieldName, TypeMirror sourceType, ExecutableElement method) {

    // Get the source type element
    TypeElement sourceTypeElement = (TypeElement) typeUtils.asElement(sourceType);
    if (sourceTypeElement == null) {
      error(
          "Cannot auto-detect traversal: source type '"
              + sourceType
              + "' is not a valid type element",
          method);
      return Optional.empty();
    }

    // Look up the field's type on the source type
    TypeMirror fieldType = findFieldType(sourceTypeElement, fieldName);
    if (fieldType == null) {
      error(
          "Cannot auto-detect traversal: field '"
              + fieldName
              + "' not found on type '"
              + sourceTypeElement.getQualifiedName()
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
              + fieldType
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
   * Finds the type of a field on a type element by looking for accessor methods or record
   * components.
   *
   * @param typeElement the type to search
   * @param fieldName the field name to find
   * @return the field's type, or null if not found
   */
  private TypeMirror findFieldType(TypeElement typeElement, String fieldName) {
    // For records, check record components first
    if (typeElement.getKind() == ElementKind.RECORD) {
      for (var component : typeElement.getRecordComponents()) {
        if (component.getSimpleName().contentEquals(fieldName)) {
          return component.asType();
        }
      }
    }

    // Look for accessor method (record-style: fieldName() or JavaBean-style: getFieldName())
    String getterName = "get" + capitalise(fieldName);
    String isGetterName = "is" + capitalise(fieldName); // For booleans

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
        return method.getReturnType();
      }
    }

    // Look for public field directly
    for (var enclosed : typeElement.getEnclosedElements()) {
      if (enclosed.getKind() == ElementKind.FIELD) {
        VariableElement field = (VariableElement) enclosed;
        if (field.getSimpleName().contentEquals(fieldName)
            && field.getModifiers().contains(Modifier.PUBLIC)) {
          return field.asType();
        }
      }
    }

    return null;
  }

  private String capitalise(String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
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
        Object value = entry.getValue().getValue();
        return value != null ? value.toString() : defaultValue;
      }
    }
    return defaultValue;
  }

  private String[] getAnnotationStringArray(AnnotationMirror annotation, String elementName) {
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

  private TypeMirror getAnnotationTypeMirror(AnnotationMirror annotation, String elementName) {
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
