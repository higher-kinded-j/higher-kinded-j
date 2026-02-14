// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.kind;

import com.palantir.javapoet.TypeName;
import java.util.List;
import java.util.Optional;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import org.higherkindedj.optics.annotations.TraverseField;
import org.higherkindedj.optics.processing.kind.KindRegistry.KindMapping;

/**
 * Analyses record fields to detect and extract information about {@code Kind<F, A>} types.
 *
 * <p>This analyser supports both library-provided Kind types (detected automatically via {@link
 * KindRegistry}) and user-defined Kind types (configured via {@link TraverseField} annotation).
 *
 * <h2>Detection Process</h2>
 *
 * <ol>
 *   <li>Check if the field type is {@code Kind<F, A>}
 *   <li>If annotated with {@code @TraverseField}, use the explicit configuration
 *   <li>Otherwise, look up the witness type in {@link KindRegistry}
 *   <li>If not found, return empty (field will use standard FocusPath generation)
 * </ol>
 *
 * <h2>Design for Nested Kind Support</h2>
 *
 * <p>This analyser is designed to support future nested Kind type detection. The current
 * implementation analyses only the outermost Kind layer, but the {@link KindFieldInfo} structure
 * and analysis methods are extensible for recursive analysis of types like {@code Kind<F, Kind<G,
 * A>>}.
 *
 * @see KindRegistry
 * @see KindFieldInfo
 * @see TraverseField
 */
public class KindFieldAnalyser {

  private final ProcessingEnvironment processingEnv;

  /**
   * Creates a new analyser.
   *
   * @param processingEnv the annotation processing environment
   */
  public KindFieldAnalyser(ProcessingEnvironment processingEnv) {
    this.processingEnv = processingEnv;
  }

  /**
   * Analyses a record component to determine if it is a Kind field.
   *
   * <p>If the field is a {@code Kind<F, A>} type and either:
   *
   * <ul>
   *   <li>Has a {@code @TraverseField} annotation with explicit configuration, or
   *   <li>Uses a witness type registered in {@link KindRegistry}
   * </ul>
   *
   * <p>then a {@link KindFieldInfo} is returned with the necessary information for code generation.
   *
   * @param component the record component to analyse
   * @return an Optional containing the analysis result, or empty if not a recognised Kind field
   */
  public Optional<KindFieldInfo> analyse(RecordComponentElement component) {
    TypeMirror fieldType = component.asType();

    // Check if this is a Kind<F, A> type
    if (!isKindType(fieldType)) {
      return Optional.empty();
    }

    // Extract Kind type arguments: Kind<WitnessType, ElementType>
    DeclaredType kindType = (DeclaredType) fieldType;
    List<? extends TypeMirror> typeArgs = kindType.getTypeArguments();

    if (typeArgs.size() != 2) {
      // Malformed Kind type
      return Optional.empty();
    }

    TypeMirror witnessTypeMirror = typeArgs.get(0);
    TypeMirror elementTypeMirror = typeArgs.get(1);
    TypeName elementType = TypeName.get(elementTypeMirror).box();

    // Check for explicit @TraverseField annotation first
    TraverseField traverseFieldAnnotation = component.getAnnotation(TraverseField.class);
    if (traverseFieldAnnotation != null) {
      return Optional.of(
          createFromAnnotation(traverseFieldAnnotation, witnessTypeMirror, elementType));
    }

    // Try to look up in registry
    return createFromRegistry(witnessTypeMirror, elementType, component);
  }

  /**
   * Checks if a type is the {@code Kind<F, A>} interface.
   *
   * @param type the type to check
   * @return true if this is a Kind type
   */
  private boolean isKindType(TypeMirror type) {
    if (type.getKind() != TypeKind.DECLARED) {
      return false;
    }

    DeclaredType declaredType = (DeclaredType) type;
    TypeElement typeElement = (TypeElement) declaredType.asElement();
    String qualifiedName = typeElement.getQualifiedName().toString();

    return KindRegistry.isKindInterface(qualifiedName);
  }

  /**
   * Creates KindFieldInfo from an explicit @TraverseField annotation.
   *
   * @param annotation the TraverseField annotation
   * @param witnessTypeMirror the witness type from the Kind
   * @param elementType the element type
   * @return the KindFieldInfo
   */
  private KindFieldInfo createFromAnnotation(
      TraverseField annotation, TypeMirror witnessTypeMirror, TypeName elementType) {

    String witnessType = witnessTypeMirror.toString();
    String baseWitness = KindRegistry.extractBaseWitnessType(witnessType);
    String typeArgs = KindRegistry.extractWitnessTypeArgs(witnessType);
    boolean isParameterised = !typeArgs.isEmpty();

    return new KindFieldInfo(
        baseWitness,
        elementType,
        annotation.traverse(),
        annotation.semantics(),
        isParameterised,
        typeArgs);
  }

  /**
   * Creates KindFieldInfo from registry lookup.
   *
   * @param witnessTypeMirror the witness type
   * @param elementType the element type
   * @param component the component (for error reporting)
   * @return Optional containing the KindFieldInfo, or empty if not registered
   */
  private Optional<KindFieldInfo> createFromRegistry(
      TypeMirror witnessTypeMirror, TypeName elementType, RecordComponentElement component) {

    String witnessType = witnessTypeMirror.toString();
    String baseWitness = KindRegistry.extractBaseWitnessType(witnessType);
    String typeArgs = KindRegistry.extractWitnessTypeArgs(witnessType);

    Optional<KindMapping> mapping = KindRegistry.lookup(baseWitness);

    if (mapping.isEmpty()) {
      // Not a known type - emit a note if it looks like a library type
      if (KindRegistry.isLibraryWitness(baseWitness)) {
        note(
            "Kind field with witness type '"
                + baseWitness
                + "' is not registered. "
                + "Consider adding @TraverseField annotation for explicit configuration.",
            component);
      }
      return Optional.empty();
    }

    KindMapping kindMapping = mapping.get();

    // Build the traverse expression
    String traverseExpression = kindMapping.traverseExpression();

    // For parameterised types, inject the type arguments
    if (kindMapping.isParameterised() && !typeArgs.isEmpty()) {
      // Transform "EitherTraverse.instance()" to "EitherTraverse.<String>instance()"
      traverseExpression = injectTypeArgs(traverseExpression, typeArgs);
    }

    return Optional.of(
        new KindFieldInfo(
            baseWitness,
            elementType,
            traverseExpression,
            kindMapping.semantics(),
            kindMapping.isParameterised(),
            typeArgs));
  }

  /**
   * Injects type arguments into a factory method call.
   *
   * <p>For example, transforms "EitherTraverse.instance()" to
   * "EitherTraverse.&lt;String&gt;instance()".
   *
   * @param expression the original expression
   * @param typeArgs the type arguments to inject
   * @return the modified expression
   */
  private String injectTypeArgs(String expression, String typeArgs) {
    // Find the method name position (last dot before parenthesis)
    int parenPos = expression.indexOf('(');
    if (parenPos <= 0) {
      return expression;
    }

    int lastDot = expression.lastIndexOf('.', parenPos);
    if (lastDot < 0) {
      return expression;
    }

    // Insert <typeArgs> after the dot
    return expression.substring(0, lastDot + 1)
        + "<"
        + typeArgs
        + ">"
        + expression.substring(lastDot + 1);
  }

  /**
   * Emits a note diagnostic.
   *
   * @param message the message
   * @param element the element
   */
  private void note(String message, RecordComponentElement element) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, message, element);
  }
}
