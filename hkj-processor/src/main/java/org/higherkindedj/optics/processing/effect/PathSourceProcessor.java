// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.effect;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.*;
import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import org.higherkindedj.hkt.effect.annotation.PathSource;
import org.higherkindedj.optics.processing.util.Diagnostics;
import org.higherkindedj.optics.processing.util.ExcludeFromJacocoGeneratedReport;
import org.higherkindedj.optics.processing.util.ProcessorUtils;
import org.higherkindedj.optics.processing.util.Reachability;
import org.higherkindedj.optics.processing.util.Reachability.Crossing;

/**
 * Annotation processor that generates Path wrapper classes for custom effect types.
 *
 * <p>This processor handles {@link PathSource} annotations on types and generates corresponding
 * Path classes with fluent composition methods.
 *
 * <h2>Generated Code Structure</h2>
 *
 * <p>For a type {@code ApiResult} annotated with {@code @PathSource}, the processor generates
 * {@code ApiResultPath} with:
 *
 * <ul>
 *   <li>Factory methods (of, pure)
 *   <li>Composition methods for its capability: map and peek, then zipWith, then via, then and
 *       flatMap
 *   <li>Error recovery methods where the capability is RECOVERABLE and an errorType is given
 * </ul>
 *
 * <p>An errorType with a capability that generates no recovery methods, and RECOVERABLE without an
 * errorType, each draw a note: the Path is generated, without recovery methods.
 *
 * @see PathSource
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes(PathSourceProcessor.PATH_SOURCE)
public class PathSourceProcessor extends AbstractProcessor {

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  /** Creates a new PathSourceProcessor. */
  public PathSourceProcessor() {}

  private static final ClassName OBJECTS = ClassName.get("java.util", "Objects");
  private static final ClassName FUNCTION = ClassName.get("java.util.function", "Function");
  private static final ClassName CONSUMER = ClassName.get("java.util.function", "Consumer");
  private static final ClassName SUPPLIER = ClassName.get("java.util.function", "Supplier");
  private static final ClassName BI_FUNCTION = ClassName.get("java.util.function", "BiFunction");

  static final String PATH_SOURCE = "org.higherkindedj.hkt.effect.annotation.PathSource";

  /** Names Java permits as identifiers but not as the name of a class. */
  private static final Set<String> RESTRICTED_TYPE_NAMES =
      Set.of("permits", "record", "sealed", "var", "yield");

  private static final ClassName GENERATED =
      ClassName.get("org.higherkindedj.optics.annotations", "Generated");

  // Capability interfaces
  private static final ClassName COMPOSABLE =
      ClassName.get("org.higherkindedj.hkt.effect.capability", "Composable");
  private static final ClassName COMBINABLE =
      ClassName.get("org.higherkindedj.hkt.effect.capability", "Combinable");
  private static final ClassName CHAINABLE =
      ClassName.get("org.higherkindedj.hkt.effect.capability", "Chainable");
  private static final ClassName RECOVERABLE =
      ClassName.get("org.higherkindedj.hkt.effect.capability", "Recoverable");

  // HKT types
  private static final ClassName KIND = ClassName.get("org.higherkindedj.hkt", "Kind");
  private static final ClassName MONAD = ClassName.get("org.higherkindedj.hkt", "Monad");
  private static final ClassName MONAD_ERROR = ClassName.get("org.higherkindedj.hkt", "MonadError");

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (TypeElement annotation : annotations) {
      Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
      for (Element element : annotatedElements) {
        if (element.getKind() != ElementKind.CLASS && element.getKind() != ElementKind.INTERFACE) {
          error("@PathSource can only be applied to classes or interfaces.", element);
          continue;
        }
        writePathClass((TypeElement) element);
      }
    }
    return true;
  }

  @ExcludeFromJacocoGeneratedReport
  private void writePathClass(TypeElement element) {
    try {
      generatePathClass(element);
    } catch (IOException e) {
      error("Could not generate Path class: " + e.getMessage(), element);
    }
  }

  private void generatePathClass(TypeElement sourceElement) throws IOException {
    String sourceName = sourceElement.getSimpleName().toString();
    String defaultPackage =
        processingEnv.getElementUtils().getPackageOf(sourceElement).getQualifiedName().toString();

    PathSource annotation = sourceElement.getAnnotation(PathSource.class);
    AnnotationMirror mirror = ProcessorUtils.findAnnotation(sourceElement, PATH_SOURCE);

    // Get annotation values
    String targetPackage = annotation.targetPackage();
    String packageName = targetPackage.isEmpty() ? defaultPackage : targetPackage;
    String suffix = annotation.suffix();
    String pathClassName = sourceName + suffix;
    PathSource.Capability written = annotation.capability();
    PathSource.Capability capability = generatedLevel(written);

    // Get witness type (using MirroredTypeException pattern)
    TypeMirror witnessTypeMirror = getWitnessType(annotation);
    TypeName witnessType = TypeName.get(witnessTypeMirror);

    // Get error type if specified
    TypeMirror errorTypeMirror = getErrorType(annotation);
    TypeName errorType = TypeName.get(errorTypeMirror);
    boolean hasErrorType = !errorType.toString().equals("java.lang.Void");
    boolean recovers = hasErrorType && isRecoverable(capability);

    // Every check runs, so each refused attribute is reported in the one compilation.
    boolean targetPackageValid = checkTargetPackage(sourceElement, mirror, targetPackage);
    boolean suffixValid = checkSuffix(sourceElement, mirror, suffix, pathClassName);
    boolean witnessValid = checkWitness(sourceElement, mirror, witnessTypeMirror);
    boolean errorTypeValid = !recovers || checkErrorType(sourceElement, mirror, errorTypeMirror);
    if (!(targetPackageValid && suffixValid && witnessValid && errorTypeValid)
        || !checkNameIsFree(sourceElement, mirror, suffix, packageName, pathClassName)) {
      return;
    }
    // The Kind the path wraps names the witness; the error type is named only where the
    // capability recovers, and the source type only in the Javadoc.
    if (!Reachability.check(
        processingEnv,
        "@PathSource",
        sourceElement,
        Reachability.companion(packageName, defaultPackage),
        Stream.concat(
            Stream.of(
                Crossing.over(
                    "witness '" + ProcessorUtils.simpleTypeName(witnessTypeMirror) + "'",
                    witnessTypeMirror)),
            recovers
                ? Stream.of(
                    Crossing.over(
                        "error type '" + ProcessorUtils.simpleTypeName(errorTypeMirror) + "'",
                        errorTypeMirror))
                : Stream.empty()))) {
      return;
    }
    noteRecoveryMismatch(
        sourceElement,
        mirror,
        written,
        hasErrorType,
        witnessTypeMirror,
        errorTypeMirror,
        pathClassName);

    ClassName sourceClassName = ClassName.get(sourceElement);

    // Type variable for the value type
    TypeVariableName typeA = TypeVariableName.get("A");

    // Build the path class
    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(pathClassName)
            .addAnnotation(GENERATED)
            .addJavadoc(
                "Generated Path wrapper for {@link $T}.\n\n"
                    + "<p>Provides fluent composition methods for working with $L values.\n\n"
                    + "<p>Do not edit - generated by PathSourceProcessor.\n",
                sourceClassName,
                sourceName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addTypeVariable(typeA)
            .addOriginatingElement(sourceElement);

    // Add the interface implementation for the capability
    classBuilder.addSuperinterface(capabilityInterface(capability, typeA));

    // Add the wrapped value field
    ParameterizedTypeName kindType = ParameterizedTypeName.get(KIND, witnessType, typeA);
    classBuilder.addField(
        FieldSpec.builder(kindType, "kind", Modifier.PRIVATE, Modifier.FINAL).build());

    // Add monad field
    ParameterizedTypeName monadType = ParameterizedTypeName.get(MONAD, witnessType);
    classBuilder.addField(
        FieldSpec.builder(monadType, "monad", Modifier.PRIVATE, Modifier.FINAL).build());

    // Add monadError field if recoverable
    if (recovers) {
      ParameterizedTypeName monadErrorType =
          ParameterizedTypeName.get(MONAD_ERROR, witnessType, errorType);
      classBuilder.addField(
          FieldSpec.builder(monadErrorType, "monadError", Modifier.PRIVATE, Modifier.FINAL)
              .build());
    }

    // Add constructor
    classBuilder.addMethod(buildConstructor(kindType, monadType, recovers, witnessType, errorType));

    // Add factory methods
    classBuilder.addMethod(
        buildOfFactory(
            pathClassName, kindType, monadType, recovers, witnessType, errorType, typeA));
    classBuilder.addMethod(
        buildPureFactory(pathClassName, monadType, recovers, witnessType, errorType, typeA));

    // Add run() method
    classBuilder.addMethod(buildRunMethod(kindType));

    // Add runKind() method
    classBuilder.addMethod(buildRunKindMethod(kindType));

    // Determine if monadError should be passed to constructor
    boolean includeMonadError = recovers;

    // Add Composable methods: map, peek
    classBuilder.addMethod(buildMapMethod(pathClassName, witnessType, typeA, includeMonadError));
    classBuilder.addMethod(buildPeekMethod(pathClassName, kindType, typeA));

    // Add Chainable methods if applicable: via, then, flatMap
    if (isChainable(capability)) {
      classBuilder.addMethod(buildViaMethod(pathClassName, witnessType, typeA, includeMonadError));
      classBuilder.addMethod(buildThenMethod(pathClassName, typeA));
      classBuilder.addMethod(buildFlatMapMethod(pathClassName, typeA));
    }

    // Add Combinable methods if applicable: zipWith
    if (isCombinable(capability)) {
      classBuilder.addMethod(
          buildZipWithMethod(pathClassName, witnessType, typeA, includeMonadError));
    }

    // Add Recoverable methods if applicable: recover, recoverWith, mapError
    if (recovers) {
      classBuilder.addMethod(buildRecoverMethod(pathClassName, errorType, typeA));
      classBuilder.addMethod(buildRecoverWithMethod(pathClassName, errorType, typeA));
      classBuilder.addMethod(buildMapErrorMethod(pathClassName, errorType, typeA));
    }

    // Add equals, hashCode, toString
    classBuilder.addMethod(buildEqualsMethod(pathClassName, typeA));
    classBuilder.addMethod(buildHashCodeMethod());
    classBuilder.addMethod(buildToStringMethod(pathClassName));

    // Write the file
    JavaFile javaFile =
        JavaFile.builder(packageName, classBuilder.build())
            .addFileComment("Generated by PathSourceProcessor. Do not edit.")
            .build();

    javaFile.writeTo(processingEnv.getFiler());
  }

  /**
   * Refuses a suffix that cannot name a class: one that does not continue a Java identifier, and
   * one that makes a name Java reserves.
   */
  private boolean checkSuffix(
      TypeElement source, AnnotationMirror mirror, String suffix, String pathClassName) {
    String problem;
    String fix;
    if (!SourceVersion.isIdentifier(pathClassName)
        || pathClassName.codePoints().anyMatch(Character::isIdentifierIgnorable)) {
      problem = "which is not a Java identifier";
      fix =
          "Remove suffix to use the default \"Path\", or give a suffix of letters, digits, '_' or"
              + " '$'.";
    } else if (SourceVersion.isKeyword(pathClassName)
        || RESTRICTED_TYPE_NAMES.contains(pathClassName)) {
      problem = "which Java does not allow as the name of a class";
      fix = "Remove suffix to use the default \"Path\", or give a suffix that makes another name.";
    } else {
      return true;
    }
    report(
        Diagnostic.Kind.ERROR,
        source,
        mirror,
        "suffix",
        "suffix "
            + processingEnv.getElementUtils().getConstantExpression(suffix)
            + " would name the generated Path '"
            + pathClassName
            + "', "
            + problem,
        "The Path class is named with the annotated type's name followed by the suffix.",
        fix);
    return false;
  }

  /**
   * Refuses a Path class name a type compiled from source here already has: the annotated type's
   * own, where an empty suffix leaves it in its package, or any other. A class file of that name,
   * such as one a previous build generated, is not in the way, since the Filer writes a source.
   */
  private boolean checkNameIsFree(
      TypeElement source,
      AnnotationMirror mirror,
      String suffix,
      String packageName,
      String pathClassName) {
    Elements elements = processingEnv.getElementUtils();
    TypeElement taken = elements.getTypeElement(packageName + "." + pathClassName);
    if (taken == null || !ProcessorUtils.compiledFromSource(elements, taken)) {
      return true;
    }
    boolean itself = taken.equals(source);
    report(
        Diagnostic.Kind.ERROR,
        source,
        mirror,
        "suffix",
        (ProcessorUtils.getAnnotationValue(mirror, "suffix") == null
                ? "the default suffix "
                : "suffix ")
            + elements.getConstantExpression(suffix)
            + " would name the generated Path '"
            + taken.getQualifiedName()
            + "'",
        itself
            ? "That is the type it is generated for."
            : "A type of that name is already declared in the compilation.",
        itself
            ? "Give a non-empty suffix, remove suffix to use the default \"Path\", or set a"
                + " targetPackage to write the Path elsewhere."
            : "Set a suffix that names a free class, or rename '"
                + taken.getQualifiedName()
                + "'.");
    return false;
  }

  /** Refuses a targetPackage that is not a package name. */
  private boolean checkTargetPackage(
      TypeElement source, AnnotationMirror mirror, String targetPackage) {
    if (targetPackage.isEmpty() || SourceVersion.isName(targetPackage)) {
      return true;
    }
    report(
        Diagnostic.Kind.ERROR,
        source,
        mirror,
        "targetPackage",
        "targetPackage "
            + processingEnv.getElementUtils().getConstantExpression(targetPackage)
            + " is not a package name",
        "The generated Path is written into that package.",
        "Remove targetPackage to write it beside '"
            + source.getSimpleName()
            + "', or give a package name such as \"com.example.paths\".");
    return false;
  }

  /** Refuses a witness that is not a class, such as {@code int.class}: no Kind is indexed by it. */
  private boolean checkWitness(TypeElement source, AnnotationMirror mirror, TypeMirror witness) {
    if (!notAClass(witness)) {
      return true;
    }
    report(
        Diagnostic.Kind.ERROR,
        source,
        mirror,
        "witness",
        "witness '" + ProcessorUtils.simpleTypeName(witness) + "' is not a class",
        "The witness is the marker class the effect's Kind is indexed by, and the generated Path"
            + " wraps a Kind of it.",
        "Name the effect's witness marker, such as BoxKind.Witness.class.");
    return false;
  }

  /**
   * Refuses a primitive or {@code void} errorType on a capability that recovers: the Path takes a
   * MonadError over it, whose error type must be a reference type.
   */
  private boolean checkErrorType(
      TypeElement source, AnnotationMirror mirror, TypeMirror errorType) {
    if (usableErrorType(errorType)) {
      return true;
    }
    report(
        Diagnostic.Kind.ERROR,
        source,
        mirror,
        "errorType",
        "errorType '" + ProcessorUtils.simpleTypeName(errorType) + "' is not a reference type",
        "Recovery takes a MonadError over the error type, and a type argument must be a reference"
            + " type.",
        "Use a reference type for the error, such as a record describing it, or remove errorType.");
    return false;
  }

  private static boolean notAClass(TypeMirror type) {
    return type.getKind().isPrimitive()
        || type.getKind() == TypeKind.VOID
        || type.getKind() == TypeKind.ARRAY;
  }

  private static boolean usableErrorType(TypeMirror type) {
    return !type.getKind().isPrimitive() && type.getKind() != TypeKind.VOID;
  }

  /**
   * Notes an errorType and a capability that do not generate recovery methods together: an
   * errorType with a capability below RECOVERABLE is not used, and RECOVERABLE without one has no
   * error type to recover from. A note rather than a warning, since the Path generated either way
   * is sound, and a processor's warning cannot be suppressed under {@code -Werror}.
   */
  private void noteRecoveryMismatch(
      TypeElement source,
      AnnotationMirror mirror,
      PathSource.Capability written,
      boolean hasErrorType,
      TypeMirror witness,
      TypeMirror errorType,
      String pathClassName) {
    boolean recoverable = isRecoverable(generatedLevel(written));
    if (hasErrorType && !recoverable) {
      String errorName = ProcessorUtils.simpleTypeName(errorType);
      report(
          Diagnostic.Kind.NOTE,
          source,
          mirror,
          "errorType",
          "errorType '" + errorName + "' has no effect on the generated '" + pathClassName + "'",
          (ProcessorUtils.getAnnotationValue(mirror, "capability") == null
                  ? "The default capability, " + written + ","
                  : "Capability " + written)
              + " generates no recovery methods; recover, recoverWith and mapError are generated"
              + " only for RECOVERABLE.",
          usableErrorType(errorType)
              ? "For them, set capability = PathSource.Capability.RECOVERABLE: of and pure then"
                  + " take a MonadError<"
                  + ProcessorUtils.simpleTypeName(witness)
                  + ", "
                  + errorName
                  + ">, so existing calls must pass one. Otherwise remove errorType."
              : "Remove errorType.");
    } else if (!hasErrorType && recoverable) {
      report(
          Diagnostic.Kind.NOTE,
          source,
          mirror,
          "capability",
          "capability "
              + written
              + " generates no recovery methods on '"
              + pathClassName
              + "' without an errorType",
          "The methods recover, recoverWith and mapError are typed by the error type, so the Path"
              + " gets CHAINABLE's methods only.",
          "Set errorType to the effect's error type, or use capability ="
              + " PathSource.Capability.CHAINABLE, which generates the same class.");
    }
  }

  /** Reports in the what/why/fix format, at the value the attribute is written with. */
  private void report(
      Diagnostic.Kind kind,
      TypeElement source,
      AnnotationMirror mirror,
      String attribute,
      String what,
      String why,
      String fix) {
    processingEnv
        .getMessager()
        .printMessage(
            kind,
            Diagnostics.format("@PathSource", what, why, fix),
            source,
            mirror,
            ProcessorUtils.getAnnotationValue(mirror, attribute));
  }

  /**
   * The level whose methods a capability generates. The deprecated levels generate exactly what
   * CHAINABLE and RECOVERABLE do, so replacing them changes nothing generated.
   */
  @SuppressWarnings("removal") // the deprecated levels generate until they are removed
  static PathSource.Capability generatedLevel(PathSource.Capability capability) {
    return switch (capability) {
      case EFFECTFUL -> PathSource.Capability.CHAINABLE;
      case ACCUMULATING -> PathSource.Capability.RECOVERABLE;
      case COMPOSABLE, COMBINABLE, CHAINABLE, RECOVERABLE -> capability;
    };
  }

  // Package-private for tests.
  static TypeMirror getWitnessType(PathSource annotation) {
    try {
      annotation.witness();
      throw new AssertionError("Should have thrown MirroredTypeException");
    } catch (MirroredTypeException e) {
      return e.getTypeMirror();
    }
  }

  // Package-private for tests.
  static TypeMirror getErrorType(PathSource annotation) {
    try {
      annotation.errorType();
      throw new AssertionError("Should have thrown MirroredTypeException");
    } catch (MirroredTypeException e) {
      return e.getTypeMirror();
    }
  }

  /**
   * The capability interface a generated Path implements. Chainable and Recoverable are sealed, so
   * above COMPOSABLE it implements Combinable, and declares via/flatMap/then (and
   * recover/recoverWith/mapError) itself.
   */
  private static TypeName capabilityInterface(
      PathSource.Capability capability, TypeVariableName typeA) {
    return ParameterizedTypeName.get(
        capability == PathSource.Capability.COMPOSABLE ? COMPOSABLE : COMBINABLE, typeA);
  }

  // Each takes a level from generatedLevel, which never answers a deprecated one.
  private boolean isChainable(PathSource.Capability capability) {
    return capability == PathSource.Capability.CHAINABLE
        || capability == PathSource.Capability.RECOVERABLE;
  }

  private boolean isCombinable(PathSource.Capability capability) {
    return capability != PathSource.Capability.COMPOSABLE;
  }

  private boolean isRecoverable(PathSource.Capability capability) {
    return capability == PathSource.Capability.RECOVERABLE;
  }

  private MethodSpec buildConstructor(
      ParameterizedTypeName kindType,
      ParameterizedTypeName monadType,
      boolean recovers,
      TypeName witnessType,
      TypeName errorType) {
    MethodSpec.Builder builder =
        MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PRIVATE)
            .addParameter(kindType, "kind")
            .addParameter(monadType, "monad")
            .addStatement(
                "this.kind = $T.requireNonNull(kind, $S)", OBJECTS, "kind must not be null")
            .addStatement(
                "this.monad = $T.requireNonNull(monad, $S)", OBJECTS, "monad must not be null");

    if (recovers) {
      ParameterizedTypeName monadErrorType =
          ParameterizedTypeName.get(MONAD_ERROR, witnessType, errorType);
      builder.addParameter(monadErrorType, "monadError");
      builder.addStatement(
          "this.monadError = $T.requireNonNull(monadError, $S)",
          OBJECTS,
          "monadError must not be null");
    }

    return builder.build();
  }

  private MethodSpec buildOfFactory(
      String pathClassName,
      ParameterizedTypeName kindType,
      ParameterizedTypeName monadType,
      boolean recovers,
      TypeName witnessType,
      TypeName errorType,
      TypeVariableName typeA) {
    ClassName pathClass = ClassName.get("", pathClassName);

    MethodSpec.Builder builder =
        MethodSpec.methodBuilder("of")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addTypeVariable(typeA)
            .returns(ParameterizedTypeName.get(pathClass, typeA))
            .addParameter(kindType, "kind")
            .addParameter(monadType, "monad")
            .addJavadoc(
                "Creates a new $L wrapping the given Kind value.\n\n"
                    + "@param kind the Kind value to wrap; must not be null\n"
                    + "@param monad the Monad instance; must not be null\n"
                    + "@param <A> the value type\n"
                    + "@return a new $L\n",
                pathClassName,
                pathClassName);

    if (recovers) {
      ParameterizedTypeName monadErrorType =
          ParameterizedTypeName.get(MONAD_ERROR, witnessType, errorType);
      builder.addParameter(monadErrorType, "monadError");
      builder.addJavadoc("@param monadError the MonadError instance; must not be null\n");
      builder.addStatement("return new $L<>(kind, monad, monadError)", pathClassName);
    } else {
      builder.addStatement("return new $L<>(kind, monad)", pathClassName);
    }

    return builder.build();
  }

  private MethodSpec buildPureFactory(
      String pathClassName,
      ParameterizedTypeName monadType,
      boolean recovers,
      TypeName witnessType,
      TypeName errorType,
      TypeVariableName typeA) {
    ClassName pathClass = ClassName.get("", pathClassName);

    MethodSpec.Builder builder =
        MethodSpec.methodBuilder("pure")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addTypeVariable(typeA)
            .returns(ParameterizedTypeName.get(pathClass, typeA))
            .addParameter(typeA, "value")
            .addParameter(monadType, "monad")
            .addJavadoc(
                "Creates a new $L containing the given value.\n\n"
                    + "@param value the value to wrap\n"
                    + "@param monad the Monad instance; must not be null\n"
                    + "@param <A> the value type\n"
                    + "@return a new $L\n",
                pathClassName,
                pathClassName);

    if (recovers) {
      ParameterizedTypeName monadErrorType =
          ParameterizedTypeName.get(MONAD_ERROR, witnessType, errorType);
      builder.addParameter(monadErrorType, "monadError");
      builder.addJavadoc("@param monadError the MonadError instance; must not be null\n");
      builder.addStatement("return new $L<>(monad.of(value), monad, monadError)", pathClassName);
    } else {
      builder.addStatement("return new $L<>(monad.of(value), monad)", pathClassName);
    }

    return builder.build();
  }

  private MethodSpec buildRunMethod(ParameterizedTypeName kindType) {
    return MethodSpec.methodBuilder("run")
        .addModifiers(Modifier.PUBLIC)
        .returns(kindType)
        .addJavadoc("Returns the underlying Kind value.\n\n@return the wrapped Kind\n")
        .addStatement("return kind")
        .build();
  }

  private MethodSpec buildRunKindMethod(ParameterizedTypeName kindType) {
    return MethodSpec.methodBuilder("runKind")
        .addModifiers(Modifier.PUBLIC)
        .returns(kindType)
        .addJavadoc(
            "Returns the underlying Kind value (alias for run).\n\n@return the wrapped Kind\n")
        .addStatement("return kind")
        .build();
  }

  private MethodSpec buildMapMethod(
      String pathClassName,
      TypeName witnessType,
      TypeVariableName typeA,
      boolean includeMonadError) {
    TypeVariableName typeB = TypeVariableName.get("B");
    ClassName pathClass = ClassName.get("", pathClassName);

    String constructorArgs =
        includeMonadError
            ? "return new $L<>(monad.map(mapper, kind), monad, monadError)"
            : "return new $L<>(monad.map(mapper, kind), monad)";

    return MethodSpec.methodBuilder("map")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .addTypeVariable(typeB)
        .returns(ParameterizedTypeName.get(pathClass, typeB))
        .addParameter(
            ParameterizedTypeName.get(
                FUNCTION, WildcardTypeName.supertypeOf(typeA), WildcardTypeName.subtypeOf(typeB)),
            "mapper")
        .addJavadoc(
            "Transforms the value using the given function.\n\n"
                + "@param mapper the transformation function; must not be null\n"
                + "@param <B> the result type\n"
                + "@return a new $L with the transformed value\n",
            pathClassName)
        .addStatement("$T.requireNonNull(mapper, $S)", OBJECTS, "mapper must not be null")
        .addStatement(constructorArgs, pathClassName)
        .build();
  }

  private MethodSpec buildPeekMethod(
      String pathClassName, ParameterizedTypeName kindType, TypeVariableName typeA) {
    ClassName pathClass = ClassName.get("", pathClassName);

    return MethodSpec.methodBuilder("peek")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .returns(ParameterizedTypeName.get(pathClass, typeA))
        .addParameter(
            ParameterizedTypeName.get(CONSUMER, WildcardTypeName.supertypeOf(typeA)), "consumer")
        .addJavadoc(
            "Performs an action on the value without transforming it.\n\n"
                + "@param consumer the action to perform; must not be null\n"
                + "@return this path unchanged\n")
        .addStatement("$T.requireNonNull(consumer, $S)", OBJECTS, "consumer must not be null")
        .addStatement("monad.map(a -> { consumer.accept(a); return a; }, kind)")
        .addStatement("return this")
        .build();
  }

  private MethodSpec buildViaMethod(
      String pathClassName,
      TypeName witnessType,
      TypeVariableName typeA,
      boolean includeMonadError) {
    TypeVariableName typeB = TypeVariableName.get("B");
    ClassName pathClass = ClassName.get("", pathClassName);

    String constructorArgs =
        includeMonadError
            ? "return new $L<>(result, monad, monadError)"
            : "return new $L<>(result, monad)";

    return MethodSpec.methodBuilder("via")
        .addModifiers(Modifier.PUBLIC)
        .addTypeVariable(typeB)
        .returns(ParameterizedTypeName.get(pathClass, typeB))
        .addParameter(
            ParameterizedTypeName.get(
                FUNCTION,
                WildcardTypeName.supertypeOf(typeA),
                WildcardTypeName.subtypeOf(ParameterizedTypeName.get(pathClass, typeB))),
            "mapper")
        .addJavadoc(
            "Chains a dependent computation.\n\n"
                + "@param mapper the function producing the next path; must not be null\n"
                + "@param <B> the result type\n"
                + "@return a new $L with the chained result\n",
            pathClassName)
        .addStatement("$T.requireNonNull(mapper, $S)", OBJECTS, "mapper must not be null")
        .addCode(
            CodeBlock.builder()
                .add("@SuppressWarnings(\"unchecked\")\n")
                .addStatement(
                    "$T<$T, $T> result = monad.flatMap(a -> {\n"
                        + "  $L<$T> next = mapper.apply(a);\n"
                        + "  if (next == null) {\n"
                        + "    throw new $T($S);\n"
                        + "  }\n"
                        + "  return next.kind;\n"
                        + "}, kind)",
                    KIND,
                    witnessType,
                    typeB,
                    pathClassName,
                    typeB,
                    NullPointerException.class,
                    "mapper must not return null")
                .build())
        .addStatement(constructorArgs, pathClassName)
        .build();
  }

  private MethodSpec buildThenMethod(String pathClassName, TypeVariableName typeA) {
    TypeVariableName typeB = TypeVariableName.get("B");
    ClassName pathClass = ClassName.get("", pathClassName);

    return MethodSpec.methodBuilder("then")
        .addModifiers(Modifier.PUBLIC)
        .addTypeVariable(typeB)
        .returns(ParameterizedTypeName.get(pathClass, typeB))
        .addParameter(
            ParameterizedTypeName.get(
                SUPPLIER, WildcardTypeName.subtypeOf(ParameterizedTypeName.get(pathClass, typeB))),
            "supplier")
        .addJavadoc(
            "Sequences a computation, discarding this path's value.\n\n"
                + "@param supplier the supplier of the next path; must not be null\n"
                + "@param <B> the result type\n"
                + "@return a new $L with the sequenced result\n",
            pathClassName)
        .addStatement("$T.requireNonNull(supplier, $S)", OBJECTS, "supplier must not be null")
        .addStatement("return via(ignored -> supplier.get())")
        .build();
  }

  private MethodSpec buildFlatMapMethod(String pathClassName, TypeVariableName typeA) {
    TypeVariableName typeB = TypeVariableName.get("B");
    ClassName pathClass = ClassName.get("", pathClassName);

    return MethodSpec.methodBuilder("flatMap")
        .addModifiers(Modifier.PUBLIC)
        .addTypeVariable(typeB)
        .returns(ParameterizedTypeName.get(pathClass, typeB))
        .addParameter(
            ParameterizedTypeName.get(
                FUNCTION,
                WildcardTypeName.supertypeOf(typeA),
                WildcardTypeName.subtypeOf(ParameterizedTypeName.get(pathClass, typeB))),
            "mapper")
        .addJavadoc(
            "Alias for {@link #via}.\n\n"
                + "@param mapper the function producing the next path; must not be null\n"
                + "@param <B> the result type\n"
                + "@return a new $L with the chained result\n",
            pathClassName)
        .addStatement("return via(mapper)")
        .build();
  }

  private MethodSpec buildZipWithMethod(
      String pathClassName,
      TypeName witnessType,
      TypeVariableName typeA,
      boolean includeMonadError) {
    TypeVariableName typeB = TypeVariableName.get("B");
    TypeVariableName typeC = TypeVariableName.get("C");
    ClassName pathClass = ClassName.get("", pathClassName);

    String constructorArgs =
        includeMonadError
            ? "return new $L<>(result, monad, monadError)"
            : "return new $L<>(result, monad)";

    return MethodSpec.methodBuilder("zipWith")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .addTypeVariable(typeB)
        .addTypeVariable(typeC)
        .returns(ParameterizedTypeName.get(pathClass, typeC))
        .addParameter(ParameterizedTypeName.get(COMBINABLE, typeB), "other")
        .addParameter(
            ParameterizedTypeName.get(
                BI_FUNCTION,
                WildcardTypeName.supertypeOf(typeA),
                WildcardTypeName.supertypeOf(typeB),
                WildcardTypeName.subtypeOf(typeC)),
            "combiner")
        .addJavadoc(
            "Combines this path with another using a binary function.\n\n"
                + "@param other the other path; must not be null\n"
                + "@param combiner the combining function; must not be null\n"
                + "@param <B> the other path's value type\n"
                + "@param <C> the result type\n"
                + "@return a new $L with the combined result\n",
            pathClassName)
        .addStatement("$T.requireNonNull(other, $S)", OBJECTS, "other must not be null")
        .addStatement("$T.requireNonNull(combiner, $S)", OBJECTS, "combiner must not be null")
        .addCode(
            CodeBlock.builder()
                .beginControlFlow("if (!(other instanceof $L<?> otherPath))", pathClassName)
                .addStatement(
                    "throw new $T($S + other.getClass())",
                    IllegalArgumentException.class,
                    "Cannot zipWith non-" + pathClassName + ": ")
                .endControlFlow()
                .add("@SuppressWarnings(\"unchecked\")\n")
                .addStatement(
                    "$L<$T> typedOther = ($L<$T>) otherPath",
                    pathClassName,
                    typeB,
                    pathClassName,
                    typeB)
                .build())
        .addStatement(
            "$T<$T, $T> result = monad.flatMap(a -> monad.map(b -> combiner.apply(a, b), typedOther.kind), kind)",
            KIND,
            witnessType,
            typeC)
        .addStatement(constructorArgs, pathClassName)
        .build();
  }

  private MethodSpec buildRecoverMethod(
      String pathClassName, TypeName errorType, TypeVariableName typeA) {
    ClassName pathClass = ClassName.get("", pathClassName);

    return MethodSpec.methodBuilder("recover")
        .addModifiers(Modifier.PUBLIC)
        .returns(ParameterizedTypeName.get(pathClass, typeA))
        .addParameter(
            ParameterizedTypeName.get(
                FUNCTION,
                WildcardTypeName.supertypeOf(errorType),
                WildcardTypeName.subtypeOf(typeA)),
            "recovery")
        .addJavadoc(
            "Recovers from an error by providing an alternative value.\n\n"
                + "@param recovery the function to produce a recovery value; must not be null\n"
                + "@return a new $L with the recovered value\n",
            pathClassName)
        .addStatement("$T.requireNonNull(recovery, $S)", OBJECTS, "recovery must not be null")
        .addStatement(
            "return new $L<>(monadError.handleErrorWith(kind, e -> monad.of(recovery.apply(e))), monad, monadError)",
            pathClassName)
        .build();
  }

  private MethodSpec buildRecoverWithMethod(
      String pathClassName, TypeName errorType, TypeVariableName typeA) {
    ClassName pathClass = ClassName.get("", pathClassName);

    return MethodSpec.methodBuilder("recoverWith")
        .addModifiers(Modifier.PUBLIC)
        .returns(ParameterizedTypeName.get(pathClass, typeA))
        .addParameter(
            ParameterizedTypeName.get(
                FUNCTION,
                WildcardTypeName.supertypeOf(errorType),
                WildcardTypeName.subtypeOf(ParameterizedTypeName.get(pathClass, typeA))),
            "recovery")
        .addJavadoc(
            "Recovers from an error by providing an alternative path.\n\n"
                + "@param recovery the function to produce a recovery path; must not be null\n"
                + "@return a new $L with the recovered result\n",
            pathClassName)
        .addStatement("$T.requireNonNull(recovery, $S)", OBJECTS, "recovery must not be null")
        .addCode(
            CodeBlock.builder()
                .add("@SuppressWarnings(\"unchecked\")\n")
                .addStatement(
                    "var result = monadError.handleErrorWith(kind, e -> {\n"
                        + "  $L<$T> recovered = recovery.apply(e);\n"
                        + "  if (recovered == null) {\n"
                        + "    throw new $T($S);\n"
                        + "  }\n"
                        + "  return recovered.kind;\n"
                        + "})",
                    pathClassName,
                    typeA,
                    NullPointerException.class,
                    "recovery must not return null")
                .build())
        .addStatement("return new $L<>(result, monad, monadError)", pathClassName)
        .build();
  }

  private MethodSpec buildMapErrorMethod(
      String pathClassName, TypeName errorType, TypeVariableName typeA) {
    ClassName pathClass = ClassName.get("", pathClassName);

    return MethodSpec.methodBuilder("mapError")
        .addModifiers(Modifier.PUBLIC)
        .returns(ParameterizedTypeName.get(pathClass, typeA))
        .addParameter(
            ParameterizedTypeName.get(
                FUNCTION,
                WildcardTypeName.supertypeOf(errorType),
                WildcardTypeName.subtypeOf(errorType)),
            "mapper")
        .addJavadoc(
            "Transforms an error using the given function.\n\n"
                + "@param mapper the error transformation function; must not be null\n"
                + "@return a new $L with the transformed error\n",
            pathClassName)
        .addStatement("$T.requireNonNull(mapper, $S)", OBJECTS, "mapper must not be null")
        .addStatement(
            "return new $L<>(monadError.handleErrorWith(kind, e -> monadError.raiseError(mapper.apply(e))), monad, monadError)",
            pathClassName)
        .build();
  }

  private MethodSpec buildEqualsMethod(String pathClassName, TypeVariableName typeA) {
    return MethodSpec.methodBuilder("equals")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .returns(boolean.class)
        .addParameter(Object.class, "obj")
        .addStatement("if (this == obj) return true")
        .addStatement("if (!(obj instanceof $L<?> other)) return false", pathClassName)
        .addStatement("return kind.equals(other.kind)")
        .build();
  }

  private MethodSpec buildHashCodeMethod() {
    return MethodSpec.methodBuilder("hashCode")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .returns(int.class)
        .addStatement("return kind.hashCode()")
        .build();
  }

  private MethodSpec buildToStringMethod(String pathClassName) {
    return MethodSpec.methodBuilder("toString")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .returns(String.class)
        .addStatement("return $S + kind + $S", pathClassName + "(", ")")
        .build();
  }

  private void error(String message, Element element) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
  }
}
