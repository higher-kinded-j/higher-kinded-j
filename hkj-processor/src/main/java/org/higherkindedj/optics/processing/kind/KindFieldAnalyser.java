// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.kind;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import java.util.List;
import java.util.Optional;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.higherkindedj.optics.annotations.TraverseField;
import org.higherkindedj.optics.processing.kind.KindRegistry.KindMapping;
import org.higherkindedj.optics.processing.util.Diagnostics;
import org.higherkindedj.optics.processing.util.ExcludeFromJacocoGeneratedReport;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

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
 *   <li>Resolve a wildcard in either type argument, and in a parameterised witness's own type
 *       arguments, to the type it stands for; a witness that resolves to no type at all leaves the
 *       field alone
 *   <li>If annotated with {@code @TraverseField}, use the explicit configuration
 *   <li>Otherwise, look up the witness type in {@link KindRegistry}
 *   <li>If not found, return empty (field will use standard FocusPath generation)
 * </ol>
 *
 * <p>What the analysis passes over without acting on, a {@code @TraverseField} it cannot apply or a
 * library witness it does not know, is said in a note by {@link #reportPassedOver}, which the
 * processor calls once per component.
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
   * <p>Both type arguments are written out as explicit type arguments of the generated {@code
   * traverseOver} call, and a parameterised witness's own arguments as those of its {@code
   * Traverse} factory; an explicit type argument cannot be a wildcard, so each is resolved to the
   * type it stands for first. That is sound because a lawful {@code Traverse} rebuilds the
   * container rather than writing into it, so a component declared {@code Kind<F, ? extends A>}
   * comes back holding a {@code Kind<F, A>}, which its declaration admits; and a witness bound
   * names the witness exactly, since every witness the registry knows is a final class. A witness
   * that resolves to no type, an unbounded or super-bounded wildcard, names no {@code Traverse}
   * instance, and the field keeps its plain path whether or not it carries {@code @TraverseField},
   * as any component that is not a Kind field does; {@link #reportPassedOver} is where the author
   * hears about it.
   *
   * @param component the record component to analyse
   * @return an Optional containing the analysis result, or empty if not a recognised Kind field
   */
  public Optional<KindFieldInfo> analyse(RecordComponentElement component) {
    if (!(shapeOf(component.asType()) instanceof Shape.KindOf kind)) {
      return Optional.empty();
    }

    // Check for explicit @TraverseField annotation first
    TraverseField traverseFieldAnnotation = component.getAnnotation(TraverseField.class);
    if (traverseFieldAnnotation != null) {
      return Optional.of(
          createFromAnnotation(traverseFieldAnnotation, kind.witnessType(), kind.elementType()));
    }

    // Try to look up in registry
    return createFromRegistry(kind.witnessType(), kind.elementType());
  }

  /**
   * Says, once, what the analysis passed over on this component without acting on it: a
   * {@code @TraverseField} it could not apply, or a library witness it does not know.
   *
   * <p>Either way the component is handed to the ordinary path, which compiles and is correct as
   * far as it goes, so the author hears about it as a note rather than an error: nothing is broken,
   * and nothing was applied. It is reported from here rather than from {@link #analyse}, which the
   * processor runs again for every navigator that reaches the component, and which never reaches
   * this analyser for a recognised container or a primitive. A type javac could not resolve is
   * passed over in silence, since javac has already reported it.
   *
   * @param component the record component to inspect
   */
  public void reportPassedOver(RecordComponentElement component) {
    TypeMirror fieldType = component.asType();
    if (fieldType.getKind() == TypeKind.ERROR) {
      return;
    }
    Shape shape = shapeOf(fieldType);
    if (component.getAnnotation(TraverseField.class) != null) {
      reportUnappliedTraverseField(component, fieldType, shape);
    } else if (shape instanceof Shape.KindOf kind) {
      reportUnregisteredLibraryWitness(component, kind.witnessType());
    }
  }

  /**
   * Says why a {@code @TraverseField} on the component is not applied, when its shape is not one.
   */
  private void reportUnappliedTraverseField(
      RecordComponentElement component, TypeMirror fieldType, Shape shape) {
    switch (shape) {
      case Shape.NotAKind _ ->
          noteNotApplied(
              component,
              ProcessorUtils.simpleTypeName(fieldType)
                  + " is not declared as a Kind<F, A> component, and the annotation names a"
                  + " Traverse for one.",
              "Drop the annotation, or declare the component as the Kind<F, A> the Traverse is"
                  + " written for.");
      case Shape.RawKind _ ->
          noteNotApplied(
              component,
              "Kind is written raw, so it names neither a witness to find a Traverse for nor an"
                  + " element to focus.",
              "Declare both type arguments, such as Kind<TreeKind.Witness, Tree> for a"
                  + " Traverse<TreeKind.Witness>.");
      case Shape.WildcardWitness witness ->
          noteNotApplied(
              component,
              "The witness of "
                  + ProcessorUtils.simpleTypeName(fieldType)
                  + " is a wildcard that stands for no type, so no Traverse instance can be named"
                  + " for it.",
              "Declare the witness the Traverse is written for in place of the wildcard, such as"
                  + " Kind<TreeKind.Witness, "
                  + witness.element()
                  + "> for a Traverse<TreeKind.Witness>.");
      case Shape.KindOf _ -> {
        // Applied: analyse() reads the annotation for this shape.
      }
    }
  }

  /** Writes the note that a {@code @TraverseField} on {@code component} is not applied. */
  private void noteNotApplied(RecordComponentElement component, String why, String fix) {
    Diagnostics.note(
        processingEnv.getMessager(),
        component,
        "@TraverseField",
        "the annotation on record component '" + qualifiedName(component) + "' is not applied.",
        why,
        fix);
  }

  /**
   * Says that a {@code Kind} component names one of Higher-Kinded-J's own witnesses that the
   * registry does not know, so nothing widens it.
   *
   * <p>Only a library witness draws the note. A witness of the author's own without
   * {@code @TraverseField} is an ordinary component they chose not to traverse; a library witness
   * with no registered {@code Traverse} is a gap they would want to hear about.
   */
  private void reportUnregisteredLibraryWitness(
      RecordComponentElement component, String witnessType) {
    String baseWitness = KindRegistry.extractBaseWitnessType(witnessType);
    if (KindRegistry.lookup(baseWitness).isPresent()
        || !KindRegistry.isLibraryWitness(baseWitness)) {
      return;
    }
    Diagnostics.note(
        processingEnv.getMessager(),
        component,
        "@GenerateFocus",
        "record component '"
            + qualifiedName(component)
            + "' names a witness the processor does not recognise.",
        baseWitness
            + " is a Higher-Kinded-J witness with no registered Traverse, so the component keeps a"
            + " plain FocusPath focusing the Kind.",
        "Add @TraverseField naming the Traverse for it, or apply traverseOver to the path"
            + " yourself.");
  }

  /** The component as a diagnostic names it: {@code Record.component}. */
  private static String qualifiedName(RecordComponentElement component) {
    return component.getEnclosingElement().getSimpleName() + "." + component.getSimpleName();
  }

  /**
   * What the analyser makes of a component's declared type, before any annotation or registry is
   * consulted.
   *
   * <p>One classification serves both {@link #analyse}, which acts on a {@link KindOf}, and {@link
   * #reportPassedOver}, which explains every other shape, so the two cannot disagree about which
   * components a {@code @TraverseField} applies to.
   */
  private sealed interface Shape {

    /** Not declared as a {@code Kind} at all. */
    record NotAKind() implements Shape {}

    /** A raw {@code Kind}, naming neither a witness nor an element. */
    record RawKind() implements Shape {}

    /**
     * A {@code Kind} whose witness is a wildcard that stands for no type.
     *
     * @param element the element type argument as the declaration wrote it, for the note's remedy
     */
    record WildcardWitness(String element) implements Shape {}

    /**
     * A {@code Kind} whose witness and element are named, with any wildcard resolved to the type it
     * stands for.
     *
     * @param witnessType the witness as the generated code names it
     * @param elementType the element the traversal focuses on, boxed
     */
    record KindOf(String witnessType, TypeName elementType) implements Shape {}
  }

  /** Classifies a component's declared type. */
  private static Shape shapeOf(TypeMirror fieldType) {
    if (!isKindType(fieldType)) {
      return new Shape.NotAKind();
    }
    List<? extends TypeMirror> typeArgs = ((DeclaredType) fieldType).getTypeArguments();
    // The interface has exactly two type parameters, so only a raw Kind arrives without them.
    if (typeArgs.size() != 2) {
      return new Shape.RawKind();
    }
    TypeMirror witness = ProcessorUtils.resolveWildcard(typeArgs.get(0));
    if (witness == null) {
      return new Shape.WildcardWitness(ProcessorUtils.simpleTypeName(typeArgs.get(1)));
    }
    return new Shape.KindOf(
        witnessNameOf(witness), ProcessorUtils.resolvedTypeNameOf(typeArgs.get(1)));
  }

  /**
   * The witness as the generated code names it.
   *
   * <p>A parameterised witness has its type arguments written out again, as explicit type arguments
   * of the {@code Traverse} factory, so each is resolved to the type it stands for first: {@code
   * EitherKind.Witness<? extends CharSequence>} names {@code
   * EitherTraverse.<CharSequence>instance()}. A witness that is not a declared type, one of the
   * record's own type variables, is named as written.
   *
   * @param witness the witness type argument, with a wildcard of its own already resolved
   * @return the witness as the generated code names it
   */
  private static String witnessNameOf(TypeMirror witness) {
    if (witness.getKind() != TypeKind.DECLARED) {
      return witness.toString();
    }
    DeclaredType declared = (DeclaredType) witness;
    ClassName raw = ClassName.get((TypeElement) declared.asElement());
    List<? extends TypeMirror> arguments = declared.getTypeArguments();
    if (arguments.isEmpty()) {
      return raw.toString();
    }
    return ParameterizedTypeName.get(
            raw,
            arguments.stream().map(ProcessorUtils::resolvedTypeNameOf).toArray(TypeName[]::new))
        .toString();
  }

  /**
   * Checks if a type is the {@code Kind<F, A>} interface.
   *
   * @param type the type to check
   * @return true if this is a Kind type
   */
  private static boolean isKindType(TypeMirror type) {
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
   * @param witnessType the witness as the generated code names it
   * @param elementType the element type
   * @return the KindFieldInfo
   */
  private KindFieldInfo createFromAnnotation(
      TraverseField annotation, String witnessType, TypeName elementType) {

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
   * <p>A witness the registry does not know yields empty, and the field keeps its plain path;
   * {@link #reportPassedOver} says so when the witness is one of the library's own.
   *
   * @param witnessType the witness as the generated code names it
   * @param elementType the element type
   * @return Optional containing the KindFieldInfo, or empty if not registered
   */
  private Optional<KindFieldInfo> createFromRegistry(String witnessType, TypeName elementType) {

    String baseWitness = KindRegistry.extractBaseWitnessType(witnessType);
    String typeArgs = KindRegistry.extractWitnessTypeArgs(witnessType);

    Optional<KindMapping> mapping = KindRegistry.lookup(baseWitness);
    if (mapping.isEmpty()) {
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
   * <p>The two early-return guards ({@code parenPos <= 0} and {@code lastDot < 0}) are defensive
   * fall-backs against malformed traverse expressions. They are structurally unreachable from the
   * current call site: the only caller ({@link #createFromRegistry}) feeds expressions that
   * originate from {@link KindRegistry}'s hardcoded {@code KNOWN_KINDS} map, which always uses the
   * {@code ClassName.instance()} factory form containing both a {@code .} and a {@code (}. The
   * guards remain as documentation and a safety net for future refactorings.
   *
   * @param expression the original expression
   * @param typeArgs the type arguments to inject
   * @return the modified expression
   */
  @ExcludeFromJacocoGeneratedReport
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
}
