// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import java.util.*;
import java.util.stream.Stream;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
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
  private final CopyStrategyChecks copyStrategyChecks;

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
    this.copyStrategyChecks = new CopyStrategyChecks(typeUtils, elementUtils, messager);
  }

  /**
   * Analyses a spec interface to determine what optics to generate.
   *
   * @param specInterface the type carrying {@code @ImportOptics} that reaches {@code
   *     OpticsSpec<S>}; one that is not an interface is refused. Its clause, supertypes and
   *     abstract methods resolve, and so does its source type's declaration where it is in source:
   *     the processor holds an importer back until they do
   * @param targetPackage the package the optics class is generated into, which decides what the
   *     generated code is allowed to name
   * @return the analysis result, or empty if the interface is invalid
   */
  public Optional<SpecAnalysis> analyse(TypeElement specInterface, String targetPackage) {
    if (specInterface.getKind() != ElementKind.INTERFACE) {
      String clause = pinnedClause(opticsSpecReachedFrom(typeUtils, specInterface.asType()));
      Diagnostics.error(
          messager,
          specInterface,
          "@ImportOptics",
          "'" + specInterface.getSimpleName() + "' implements OpticsSpec but is not an interface.",
          "A spec is an interface: its abstract methods are the optics to generate, and the"
              + " generated class stands in for it rather than extending it.",
          clause == null
              ? "Declare it as an interface extending OpticsSpec<S>, naming the type its optics are"
                  + " for, in place of the class."
              : "Declare it as an interface extending " + clause + ", in place of the class.");
      return Optional.empty();
    }

    DeclaredType clause = declaredOpticsSpec(specInterface);
    if (clause == null) {
      // Reported together, so moving everything onto the spec takes one round.
      reportIndirectSpec(specInterface);
      reportInheritedOptics(specInterface);
      return Optional.empty();
    }

    // A raw clause names no source type.
    TypeMirror sourceType =
        clause.getTypeArguments().isEmpty() ? null : clause.getTypeArguments().getFirst();
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
    if (reportInheritedOptics(specInterface)) {
      rejected = true;
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
   * The {@code OpticsSpec} clause an interface declares among its own super-interfaces, or null
   * where it declares none.
   *
   * @param type the type to read
   * @return the clause as written, or null
   */
  public static DeclaredType declaredOpticsSpec(TypeElement type) {
    for (TypeMirror superInterface : type.getInterfaces()) {
      // Super-interfaces returned by getInterfaces() are always declared types.
      DeclaredType declared = (DeclaredType) superInterface;
      if (((TypeElement) declared.asElement()).getQualifiedName().contentEquals(OPTICS_SPEC_FQN)) {
        return declared;
      }
    }
    return null;
  }

  /**
   * Whether a type is a spec interface: an interface declaring {@code OpticsSpec<S>} among its own
   * super-interfaces.
   *
   * @param type the type to ask about
   * @return true for a spec interface
   */
  public static boolean isSpecInterface(TypeElement type) {
    return type.getKind() == ElementKind.INTERFACE && declaredOpticsSpec(type) != null;
  }

  /**
   * Whether a type reaches {@code OpticsSpec} through its supertypes, and so is read as a spec:
   * analysed where it declares {@code OpticsSpec<S>} itself, and refused where it cannot be one.
   *
   * @param typeUtils the type utilities from the processing environment
   * @param type the type to ask about
   * @return true where a supertype of the type is {@code OpticsSpec}
   */
  public static boolean reachesOpticsSpec(Types typeUtils, TypeElement type) {
    return opticsSpecReachedFrom(typeUtils, type.asType()) != null;
  }

  /**
   * The {@code OpticsSpec} a type reaches through its supertypes, under the type arguments it is
   * reached with, or null where it reaches none. The type itself is not asked, and each supertype
   * is walked once however many paths lead to it.
   */
  private static DeclaredType opticsSpecReachedFrom(Types typeUtils, TypeMirror type) {
    Deque<TypeMirror> pending = new ArrayDeque<>(List.of(type));
    Set<Element> walked = new HashSet<>();
    while (!pending.isEmpty()) {
      for (TypeMirror supertype : typeUtils.directSupertypes(pending.pop())) {
        // The supertypes of a class or interface are classes or interfaces.
        DeclaredType declared = (DeclaredType) supertype;
        TypeElement element = (TypeElement) declared.asElement();
        if (element.getQualifiedName().contentEquals(OPTICS_SPEC_FQN)) {
          return declared;
        }
        if (walked.add(element)) {
          pending.add(declared);
        }
      }
    }
    return null;
  }

  /**
   * {@code OpticsSpec<X>} for the clause a type reaches, where it names a class or interface as the
   * source type, or null where it names none: a raw clause, a type variable, or no clause at all.
   */
  private static String pinnedClause(DeclaredType reached) {
    List<? extends TypeMirror> arguments = reached == null ? List.of() : reached.getTypeArguments();
    return arguments.size() == 1 && arguments.getFirst().getKind() == TypeKind.DECLARED
        ? ProcessorUtils.simpleTypeName(reached)
        : null;
  }

  /**
   * Reports an interface declaring no {@code OpticsSpec} clause of its own. One reaching it through
   * another interface is told which, and the fix names the clause to declare where the interface in
   * between names the source type, and otherwise asks for one in place of that interface, since the
   * two clauses would disagree. The processor sends an interface here only when it reaches {@code
   * OpticsSpec}, so one reaching none comes from a direct caller of the analyser.
   */
  private void reportIndirectSpec(TypeElement specInterface) {
    String name = specInterface.getSimpleName().toString();
    record Path(TypeMirror through, DeclaredType reached) {}
    Optional<Path> found =
        specInterface.getInterfaces().stream()
            .map(
                superInterface ->
                    new Path(superInterface, opticsSpecReachedFrom(typeUtils, superInterface)))
            .filter(candidate -> candidate.reached() != null)
            .findFirst();
    if (found.isEmpty()) {
      Diagnostics.error(
          messager,
          specInterface,
          "@ImportOptics",
          "'" + name + "' does not extend OpticsSpec.",
          "A spec interface is read from the OpticsSpec<S> it declares, whose argument names the"
              + " type its optics are for.",
          "Declare OpticsSpec<S> on '" + name + "', naming the type its optics are for.");
      return;
    }
    Path path = found.get();
    String through = ProcessorUtils.simpleTypeName(path.through());
    String clause = pinnedClause(path.reached());
    Diagnostics.error(
        messager,
        specInterface,
        "@ImportOptics",
        "'" + name + "' extends OpticsSpec only through '" + through + "'.",
        "A spec interface is read from the OpticsSpec<S> it declares itself, and reaching it"
            + " through another interface is not supported yet, so it is not read as a spec.",
        clause == null
            ? "Declare OpticsSpec on '"
                + name
                + "' itself, naming the type its optics are for, in place of '"
                + through
                + "'."
            : "Declare " + clause + " on '" + name + "' itself, beside '" + through + "'.");
  }

  /**
   * Reports each optic method a spec inherits, and returns whether there was one. A member counts
   * as the spec sees it: an abstract or {@code default} method another interface declares, taking
   * no arguments and declaring no type parameters, whose return type, under the spec's own type
   * arguments, is an optic. A method with arguments or type parameters could not be an optic the
   * spec declares either, so it is left alone. The generated class carries only what the spec
   * declares, so an inherited optic would be missing from it, and a {@code ThroughField} traversal
   * composing through one would call a method that is not there.
   */
  private boolean reportInheritedOptics(TypeElement specInterface) {
    DeclaredType specType = (DeclaredType) specInterface.asType();
    boolean reported = false;
    for (ExecutableElement member :
        ElementFilter.methodsIn(elementUtils.getAllMembers(specInterface))) {
      // An interface's static methods are not members of the interfaces extending it, so every
      // member declared elsewhere is inherited.
      if (!member.getEnclosingElement().equals(specInterface)
          && member.getParameters().isEmpty()
          && member.getTypeParameters().isEmpty()
          && ((ExecutableType) typeUtils.asMemberOf(specType, member)).getReturnType()
              instanceof DeclaredType returned
          && determineOpticKind(returned) != null) {
        reportInheritedOptic(specInterface, member, returned);
        reported = true;
      }
    }
    return reported;
  }

  /**
   * Reports one optic method a spec inherits, asking for it on the spec under the spec's own type
   * arguments where the spec's view of it has them.
   *
   * @param specInterface the spec interface inheriting the method
   * @param method the inherited optic method
   * @param returned its return type as the spec sees it
   */
  private void reportInheritedOptic(
      TypeElement specInterface, ExecutableElement method, DeclaredType returned) {
    String spec = specInterface.getSimpleName().toString();
    String name = method.getSimpleName().toString();
    String from = method.getEnclosingElement().getSimpleName().toString();
    // A raw clause on the way erases what the spec sees; the declaration still has it, and serves
    // where it names none of its interface's own type parameters, which the spec cannot see.
    TypeMirror declared = method.getReturnType();
    TypeMirror written =
        ProcessorUtils.firstRawIn(returned) != null
                && ProcessorUtils.firstRawIn(declared) == null
                // A method's enclosing element is the class or interface declaring it.
                && ((TypeElement) method.getEnclosingElement())
                    .getTypeParameters().stream()
                        .noneMatch(parameter -> ProcessorUtils.mentions(declared, parameter))
            ? declared
            : returned;
    // A raw view has no type arguments to paste, so the fix asks for them instead.
    String declaration =
        ProcessorUtils.firstRawIn(written) == null
            ? "'" + ProcessorUtils.simpleTypeName(written) + " " + name + "()'"
            : "'" + name + "' with its type arguments";
    Diagnostics.error(
        messager,
        specInterface,
        "@ImportOptics",
        "'" + spec + "' inherits the optic method '" + name + "' from '" + from + "'.",
        "A spec generates optics from the methods it declares itself, and reading them from"
            + " another interface is not supported yet, so '"
            + name
            + "' would be missing from the generated class.",
        method.isDefault()
            ? "Declare "
                + declaration
                + " on '"
                + spec
                + "' itself as an abstract method carrying a copy strategy or hint annotation, or"
                + " move the default out of '"
                + from
                + "' into a static method that calls the generated statics."
            : "Declare "
                + declaration
                + " on '"
                + spec
                + "' itself, annotated as it is on '"
                + from
                + "'.");
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
    // reference type, so a wildcard or primitive argument never compiles; an unresolvable one never
    // arrives, since the processor holds the spec back until it resolves; and an intersection
    // cannot be written as a type argument at all.
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
        && !typeUtils.isSameType(bound, elementUtils.getTypeElement(OBJECT_FQN).asType())
        && !ProcessorUtils.mentions(bound, typeVariable.asElement())
        // A hint naming a raw bound would steer straight into the raw-source refusal.
        && ProcessorUtils.firstRawIn(bound) == null) {
      return ": 'OpticsSpec<" + ProcessorUtils.simpleTypeName(bound) + ">'";
    }
    return "";
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
              + ProcessorUtils.qualifiedTypeName(returnType),
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
            copyStrategyChecks.parse(
                method, sourceType, sourceTypeElement, focusType, targetPackage);
        if (copyResult.isEmpty()) {
          // The checks have reported why: either no strategy annotation at all, or one whose
          // values were rejected.
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

  // ----- Prism Hint Parsing -----

  private record PrismHintResult(PrismHintKind kind, PrismHintInfo info) {}

  private Optional<PrismHintResult> parsePrismHint(
      ExecutableElement method,
      TypeMirror sourceType,
      TypeElement sourceTypeElement,
      TypeMirror focusType,
      TypeElement specInterface) {
    // Check for @InstanceOf
    AnnotationMirror instanceOf = ProcessorUtils.findAnnotation(method, INSTANCE_OF_FQN);
    if (instanceOf != null) {
      // @InstanceOf.value() is mandatory, but an unresolvable class constant (a typo, or a
      // not-yet-generated type) is modelled as an erroneous attribute whose value is a String,
      // not a TypeMirror - so this CAN be null and must fall through to the hint diagnostic.
      TypeMirror targetType = ProcessorUtils.getAnnotationTypeMirror(instanceOf, "value");
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
                + ProcessorUtils.qualifiedTypeName(targetType)
                + "' is not a subtype of source type '"
                + ProcessorUtils.qualifiedTypeName(sourceType)
                + "'. "
                + "Only subtypes of '"
                + ProcessorUtils.qualifiedTypeName(sourceType)
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
    AnnotationMirror matchWhen = ProcessorUtils.findAnnotation(method, MATCH_WHEN_FQN);
    if (matchWhen != null) {
      String predicate = ProcessorUtils.getAnnotationString(matchWhen, "predicate", "");
      String getter = ProcessorUtils.getAnnotationString(matchWhen, "getter", "");
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
    AnnotationMirror traverseWith = ProcessorUtils.findAnnotation(method, TRAVERSE_WITH_FQN);
    if (traverseWith != null) {
      String traversalReference = ProcessorUtils.getAnnotationString(traverseWith, "value", "");
      return Optional.of(
          new TraversalHintResult(
              TraversalHintKind.TRAVERSE_WITH,
              TraversalHintInfo.forTraverseWith(traversalReference)));
    }

    // Check for @ThroughField
    AnnotationMirror throughField = ProcessorUtils.findAnnotation(method, THROUGH_FIELD_FQN);
    if (throughField != null) {
      String fieldName = ProcessorUtils.getAnnotationString(throughField, "field", "");
      String traversal = ProcessorUtils.getAnnotationString(throughField, "traversal", "");

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
    // A raw lens is declared but has no focus, so there is nothing for the container traversal to
    // compose onto, and a lens with a body stays on the spec, so the generated class has no lens to
    // call; each is refused where a missing one is, and told apart.
    boolean hasBody = lens != null && !lens.method().getModifiers().contains(Modifier.ABSTRACT);
    boolean raw = lens != null && lens.type().getTypeArguments().size() != 2;
    if (lens == null || hasBody || raw) {
      String problem;
      String why =
          "The generated traversal calls the spec's own lens for the field and composes the"
              + " container traversal after it, so the lens has to say what it focuses on";
      String fix;
      if (hasBody) {
        // An interface method with a body is static, private or default (JLS 9.4), and the one it
        // is gets named, whichever reaches here.
        String declaredAs =
            Stream.of(Modifier.STATIC, Modifier.PRIVATE, Modifier.DEFAULT)
                .filter(lens.method().getModifiers()::contains)
                .findFirst()
                .orElseThrow()
                .toString();
        problem = "', which the spec declares " + declaredAs;
        why =
            "The generated traversal composes through the generated class's lens for the field,"
                + " and a "
                + declaredAs
                + " method stays on the spec, so the generated class has none to call";
        fix =
            "Declare '"
                + fieldName
                + "' as an abstract Lens method with its copy strategy, which the generated class"
                + " carries, or use @TraverseWith for a traversal that stands on its own";
      } else if (raw) {
        problem = "', which the spec declares raw";
        fix =
            "Declare '"
                + fieldName
                + "' with both its type arguments, as Lens<Source, Focus>, or use @TraverseWith for"
                + " a traversal that stands on its own";
      } else {
        problem = "', which the spec does not declare";
        fix =
            "Declare a Lens method named '"
                + fieldName
                + "' on the spec, with its copy strategy, or use @TraverseWith for a traversal that"
                + " stands on its own";
      }
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
              + problem,
          why,
          fix);
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

    return Optional.of(new AutoDetectedTraversal(traversalRef, checked, lens));
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
   * @param lens the spec's lens for the field, as the spec has it and as its method declares it,
   *     which is where its focus is read from
   */
  private record AutoDetectedTraversal(
      String reference, boolean checkedComposition, LensMember lens) {}

  /**
   * A lens method of the spec: its return type read under the spec's instantiation, and as the
   * method declares it, which keeps what reading under the instantiation drops.
   *
   * @param type the lens type under the spec's instantiation
   * @param declared the lens type as its method declares it
   * @param method the lens method, abstract when the generated class carries it
   */
  private record LensMember(DeclaredType type, TypeMirror declared, ExecutableElement method) {}

  /**
   * The spec's own lens for {@code fieldName}, or null when the spec declares no lens by that name.
   * A raw one is answered as it is declared; it has no focus, and the caller says so. Inherited
   * optics are refused before this is asked, so the lens found is the spec's own: generated when
   * abstract, and left on the spec when it has a body. A method taking arguments or declaring type
   * parameters is not a lens for a field, whatever it returns.
   */
  private LensMember declaredLens(TypeElement specInterface, String fieldName) {
    DeclaredType specType = (DeclaredType) specInterface.asType();
    for (ExecutableElement member :
        ElementFilter.methodsIn(elementUtils.getAllMembers(specInterface))) {
      if (!member.getSimpleName().contentEquals(fieldName)
          || !member.getParameters().isEmpty()
          || !member.getTypeParameters().isEmpty()) {
        continue;
      }
      TypeMirror returned =
          ((ExecutableType) typeUtils.asMemberOf(specType, member)).getReturnType();
      if (returned instanceof DeclaredType optic && determineOpticKind(optic) == OpticKind.LENS) {
        return new LensMember(optic, member.getReturnType(), member);
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
   * The type a named field has on the instantiated source type.
   *
   * @param sourceType the instantiated source type to search
   * @param fieldName the field name to find
   * @return the field's type under that instantiation, or null if not found
   */
  private TypeMirror findFieldType(TypeMirror sourceType, String fieldName) {
    // analyse() admits a source type only when asElement gives a TypeElement, and the processor
    // holds a spec back until its source type resolves, which leaves DECLARED alone. That is what
    // makes the cast total, the same reasoning parseCopyStrategy's spells out.
    DeclaredType declaredSource = (DeclaredType) sourceType;
    TypeElement typeElement = (TypeElement) declaredSource.asElement();

    // For records, check record components first
    if (typeElement.getKind() == ElementKind.RECORD) {
      for (var component : typeElement.getRecordComponents()) {
        if (component.getSimpleName().contentEquals(fieldName)) {
          return ProcessorUtils.memberTypeOf(typeUtils, declaredSource, component.getAccessor());
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
        return ProcessorUtils.memberTypeOf(typeUtils, declaredSource, method);
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

  private void error(String msg, Element element) {
    messager.printMessage(Diagnostic.Kind.ERROR, msg, element);
  }
}
