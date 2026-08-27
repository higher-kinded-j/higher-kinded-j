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
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
import org.higherkindedj.hkt.effect.annotation.PathVia;
import org.higherkindedj.optics.processing.util.ExcludeFromJacocoGeneratedReport;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * Annotation processor that generates Path bridge classes for service interfaces.
 *
 * <p>This processor handles {@link GeneratePathBridge} annotations on interfaces and generates
 * companion classes that wrap service methods returning effect types (Optional, Either, Try, etc.)
 * into corresponding Path types.
 *
 * <h2>Generated Code Structure</h2>
 *
 * <p>For an interface {@code UserService}, the processor generates {@code UserServicePaths} with:
 *
 * <ul>
 *   <li>A constructor taking the delegate interface
 *   <li>Methods for each {@link PathVia}-annotated method, returning the appropriate Path type
 * </ul>
 *
 * @see GeneratePathBridge
 * @see PathVia
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("org.higherkindedj.hkt.effect.annotation.GeneratePathBridge")
public class PathProcessor extends AbstractProcessor {

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  /** Creates a new PathProcessor. */
  public PathProcessor() {}

  private static final ClassName OBJECTS = ClassName.get("java.util", "Objects");
  private static final ClassName PATH = ClassName.get("org.higherkindedj.hkt.effect", "Path");
  private static final ClassName SEMIGROUP = ClassName.get("org.higherkindedj.hkt", "Semigroup");
  private static final ClassName GENERATED =
      ClassName.get("org.higherkindedj.optics.annotations", "Generated");

  // Path type class names
  private static final ClassName MAYBE_PATH =
      ClassName.get("org.higherkindedj.hkt.effect", "MaybePath");
  private static final ClassName EITHER_PATH =
      ClassName.get("org.higherkindedj.hkt.effect", "EitherPath");
  private static final ClassName TRY_PATH =
      ClassName.get("org.higherkindedj.hkt.effect", "TryPath");
  private static final ClassName VALIDATION_PATH =
      ClassName.get("org.higherkindedj.hkt.effect", "ValidationPath");
  private static final ClassName OPTIONAL_PATH =
      ClassName.get("org.higherkindedj.hkt.effect", "OptionalPath");
  private static final ClassName IO_PATH = ClassName.get("org.higherkindedj.hkt.effect", "IOPath");

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (TypeElement annotation : annotations) {
      Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
      for (Element element : annotatedElements) {
        if (element.getKind() != ElementKind.INTERFACE) {
          error("@GeneratePathBridge can only be applied to interfaces.", element);
          continue;
        }
        writeBridgeClass((TypeElement) element);
      }
    }
    return true;
  }

  @ExcludeFromJacocoGeneratedReport
  private void writeBridgeClass(TypeElement element) {
    try {
      generateBridgeClass(element);
    } catch (IOException e) {
      error("Could not generate Path bridge class: " + e.getMessage(), element);
    }
  }

  private void generateBridgeClass(TypeElement interfaceElement) throws IOException {
    String interfaceName = interfaceElement.getSimpleName().toString();
    String defaultPackage =
        processingEnv
            .getElementUtils()
            .getPackageOf(interfaceElement)
            .getQualifiedName()
            .toString();

    GeneratePathBridge annotation = interfaceElement.getAnnotation(GeneratePathBridge.class);
    String targetPackage = annotation.targetPackage();
    String packageName = targetPackage.isEmpty() ? defaultPackage : targetPackage;
    String suffix = annotation.suffix();
    String bridgeClassName = interfaceName + suffix;

    ClassName interfaceClassName = ClassName.get(interfaceElement);
    // The bridge holds one delegate of the annotated interface, so it declares whatever that
    // interface declares. Naming it raw instead would leave every method that mentions one of
    // those parameters pointing at a variable the bridge never brings into scope.
    List<TypeVariableName> interfaceVariables =
        interfaceElement.getTypeParameters().stream().map(TypeVariableName::get).toList();
    TypeName delegateType =
        interfaceVariables.isEmpty()
            ? interfaceClassName
            : ParameterizedTypeName.get(
                interfaceClassName, interfaceVariables.toArray(TypeName[]::new));

    // A bound is written into the bridge's own declaration, so it has to be nameable there. Only
    // targetPackage can make that false: written beside the interface, whatever the interface can
    // name the bridge can name too.
    if (rejectsUnnameableBounds(interfaceElement, interfaceElement, packageName)) {
      return;
    }

    // Build the bridge class
    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(bridgeClassName)
            .addAnnotation(GENERATED)
            .addJavadoc(
                "Generated Path bridge for {@link $T}.\n\n<p>Do not edit.\n", interfaceClassName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addOriginatingElement(interfaceElement);
    interfaceVariables.forEach(
        variable -> {
          classBuilder.addTypeVariable(variable);
          // Doclint wants one per parameter, and nobody can add it to a generated file by hand.
          classBuilder.addJavadoc("\n@param <$L> as declared by the delegate\n", variable.name());
        });

    // Add delegate field
    classBuilder.addField(
        FieldSpec.builder(delegateType, "delegate", Modifier.PRIVATE, Modifier.FINAL).build());

    // Add constructor
    classBuilder.addMethod(
        MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(delegateType, "delegate")
            .addJavadoc(
                "Creates a new Path bridge wrapping the given delegate.\n\n"
                    + "@param delegate the service to wrap; must not be null\n")
            .addStatement(
                "this.delegate = $T.requireNonNull(delegate, $S)",
                OBJECTS,
                "delegate must not be null")
            .build());

    // Process @PathVia methods
    List<ExecutableElement> pathViaMethods = pathViaMethodsOf(interfaceElement);
    if (pathViaMethods.isEmpty()) {
      // A bridge with a constructor and nothing else is never what was asked for, and silence
      // leaves the author reading the generated file to find that out.
      warn(
          "@GeneratePathBridge on '"
              + interfaceName
              + "': no @PathVia method was found, on the interface or on anything it extends, so"
              + " the bridge has a constructor and nothing else. Fix: put @PathVia on the methods"
              + " to bridge, or drop @GeneratePathBridge.",
          interfaceElement);
    }
    DeclaredType interfaceType = (DeclaredType) interfaceElement.asType();
    for (ExecutableElement method : pathViaMethods) {
      MethodSpec bridgeMethod =
          createBridgeMethod(method, interfaceType, interfaceElement, packageName);
      if (bridgeMethod != null) {
        classBuilder.addMethod(bridgeMethod);
      }
    }

    // Write the file
    JavaFile javaFile =
        JavaFile.builder(packageName, classBuilder.build())
            .addFileComment("Generated by PathProcessor. Do not edit.")
            .build();

    javaFile.writeTo(processingEnv.getFiler());
  }

  /**
   * The {@code @PathVia} methods the bridge is to wrap, inherited ones included.
   *
   * <p>Members, not enclosed elements: a bridge for {@code Derived extends Base<String>} is asked
   * for the methods {@code Derived} <em>has</em>, and reading only what it declares had produced a
   * bridge with a constructor and nothing else. Java's own precedence applies - an override hides
   * the method it overrides - and interface statics and privates are not inherited at all, so
   * anything reached here through a supertype is callable through the delegate. {@code Object}'s
   * members are filtered by the kind of what declares them.
   *
   * @param interfaceElement the annotated interface; must not be null
   * @return its own {@code @PathVia} methods first, then the inherited ones
   */
  private List<ExecutableElement> pathViaMethodsOf(TypeElement interfaceElement) {
    List<ExecutableElement> annotated =
        ElementFilter.methodsIn(processingEnv.getElementUtils().getAllMembers(interfaceElement))
            .stream()
            .filter(method -> method.getEnclosingElement().getKind() == ElementKind.INTERFACE)
            .filter(method -> method.getAnnotation(PathVia.class) != null)
            .toList();
    // Own before inherited, so the generated file reads in the order the author wrote, and a
    // supertype gaining a member does not reshuffle the methods already there.
    return Stream.concat(
            annotated.stream()
                .filter(method -> interfaceElement.equals(method.getEnclosingElement())),
            annotated.stream()
                .filter(method -> !interfaceElement.equals(method.getEnclosingElement())))
        .toList();
  }

  /**
   * Reports a type parameter whose bound the generated package cannot name, and returns whether it
   * did.
   *
   * <p>The bridge repeats its delegate's bounds verbatim, so a bound naming a package-private type
   * lands in a declaration that cannot see it. Only {@code targetPackage} makes this reachable: a
   * bridge written beside its interface can name everything the interface can.
   *
   * @param declarer the interface whose parameters are being copied
   * @param interfaceElement the annotated interface, which the diagnostic is reported against
   * @param packageName the package the bridge is written into
   * @return true when a bound was rejected and an error reported
   */
  private boolean rejectsUnnameableBounds(
      Parameterizable declarer, TypeElement interfaceElement, String packageName) {

    for (TypeParameterElement parameter : declarer.getTypeParameters()) {
      if (rejectsUnnameableBound(
          parameter.getSimpleName(),
          parameter.getBounds(),
          declarer,
          interfaceElement,
          packageName)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Reports one bound the generated package cannot name, and returns whether it did.
   *
   * @param parameterName the type parameter the bounds belong to; must not be null
   * @param bounds the bounds as the bridge will write them; must not be null
   * @param site the declaration the diagnostic is attached to; must not be null
   * @param interfaceElement the annotated interface, which the diagnostic names
   * @param packageName the package the bridge is written into
   * @return true when a bound was rejected and an error reported
   */
  private boolean rejectsUnnameableBound(
      CharSequence parameterName,
      List<? extends TypeMirror> bounds,
      Element site,
      TypeElement interfaceElement,
      String packageName) {

    Elements elements = processingEnv.getElementUtils();
    for (TypeMirror bound : bounds) {
      TypeElement unreachable = ProcessorUtils.firstUnreachableIn(elements, bound, packageName);
      if (unreachable != null) {
        error(
            "@GeneratePathBridge on '"
                + interfaceElement.getSimpleName()
                + "': the bound on '"
                + parameterName
                + "' names '"
                + unreachable.getSimpleName()
                + "', which cannot be reached from '"
                + packageName
                + "'. The bridge repeats the bound in its own declaration, and it is not visible"
                + " there. Fix: make '"
                + unreachable.getSimpleName()
                + "' public, or drop targetPackage so the bridge is written beside the"
                + " interface.",
            site);
        return true;
      }
    }
    return false;
  }

  private MethodSpec createBridgeMethod(
      ExecutableElement method,
      DeclaredType interfaceType,
      TypeElement interfaceElement,
      String packageName) {
    // The bridge calls the method through its delegate, which reaches an abstract or default
    // member and nothing else. Left ungated, both of these emitted a call javac refuses, against
    // a file the author never wrote.
    Set<Modifier> modifiers = method.getModifiers();
    if (modifiers.contains(Modifier.STATIC) || modifiers.contains(Modifier.PRIVATE)) {
      error(
          "@PathVia on '"
              + method.getSimpleName()
              + "': the bridge reaches the method through its delegate, and a "
              + (modifiers.contains(Modifier.STATIC) ? "static" : "private")
              + " interface method cannot be called that way. Fix: make it an abstract or default"
              + " instance method, or drop @PathVia from it.",
          method);
      return null;
    }

    // The method as the annotated interface has it, not as its declaration reads: a @PathVia
    // inherited from Base<T> into Derived extends Base<String> speaks String here, and copying
    // the declaration would put Base's own parameter into a bridge that never declares it.
    Types types = processingEnv.getTypeUtils();
    ExecutableType asMember = (ExecutableType) types.asMemberOf(interfaceType, method);

    // A generic delegate method's parameters are named by the return type and the arguments copied
    // below, so the bridge method has to declare them itself; the interface's own are in scope
    // already, from the class. Their bounds are read under the instantiation too - an inherited
    // `<R extends T>` is `<R extends String>` here, and `T` is a name the bridge cannot write.
    List<TypeVariableName> methodVariables = new ArrayList<>();
    for (TypeVariable variable : asMember.getTypeVariables()) {
      Name variableName = variable.asElement().getSimpleName();
      List<? extends TypeMirror> bounds = boundsOf(variable);
      if (rejectsUnnameableBound(variableName, bounds, method, interfaceElement, packageName)) {
        return null;
      }
      methodVariables.add(
          TypeVariableName.get(
              variableName.toString(),
              bounds.stream().map(TypeName::get).toArray(TypeName[]::new)));
    }

    TypeMirror returnType = asMember.getReturnType();
    Effect effect = effectFor(returnType);
    if (effect == null) {
      error(
          "Unsupported return type for @PathVia: "
              + returnType
              + ". Supported types: Optional, Maybe, Either, Try, Validated, IO.",
          method);
      return null;
    }

    // Cast, not a pattern: effectFor answers non-null only for a declared type it recognised.
    List<? extends TypeMirror> typeArgs = ((DeclaredType) returnType).getTypeArguments();

    // Every supported effect is generic, so no arguments means the return type was written raw.
    // Substituting Object had made the delegate call an unchecked conversion, and the warning
    // lands in a file whose only place for a suppression is generated too.
    if (typeArgs.isEmpty()) {
      error(
          "@PathVia on '"
              + method.getSimpleName()
              + "': the return type is a raw '"
              + effect.effectName()
              + "'. The bridge passes it to Path."
              + effect.factoryMethod()
              + ", which is an unchecked conversion, and the warning would land in generated"
              + " source that cannot carry a suppression. Fix: name the type argument, as '"
              + effect.effectName()
              + "<...>'.",
          method);
      return null;
    }

    // The Semigroup added below names the error type a second time, and a wildcard captures
    // separately at each mention, so no argument could satisfy both.
    if (effect.requiresSemigroup() && typeArgs.getFirst().getKind() == TypeKind.WILDCARD) {
      error(
          "@PathVia on '"
              + method.getSimpleName()
              + "': the error type of the returned '"
              + effect.effectName()
              + "' is the wildcard '"
              + typeArgs.getFirst()
              + "'. The bridge names it twice - in the "
              + effect.pathType().simpleName()
              + " it returns and in the Semigroup the caller supplies - and a wildcard is a"
              + " different captured type at each, so no caller could satisfy both. Fix: name the"
              + " error type.",
          method);
      return null;
    }

    PathVia pathVia = method.getAnnotation(PathVia.class);
    String methodName =
        pathVia.name().isEmpty() ? method.getSimpleName().toString() : pathVia.name();
    String doc = pathVia.doc();

    TypeName[] effectArguments = typeArgs.stream().map(TypeName::get).toArray(TypeName[]::new);
    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder(methodName)
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(effect.pathType(), effectArguments));
    methodVariables.forEach(methodBuilder::addTypeVariable);

    // Description first, then the block tags in order. A tag written before the description takes
    // the description into itself, which is what javadoc does with any text following a tag.
    if (!doc.isEmpty()) {
      methodBuilder.addJavadoc("$L\n\n", doc);
    }
    methodVariables.forEach(
        variable ->
            methodBuilder.addJavadoc(
                "@param <$L> as declared by the delegate method\n", variable.name()));
    // $T, not the simple name: an inherited method is declared elsewhere, and a link naming a
    // type the generated file never imports is one doclint rejects.
    methodBuilder.addJavadoc(
        "@return Path-wrapped result from {@link $T#$L}\n",
        ClassName.get((TypeElement) method.getEnclosingElement()),
        method.getSimpleName());

    // Copy parameters, under the instantiation as the signature above is.
    List<String> argumentNames = new ArrayList<>();
    List<? extends TypeMirror> parameterTypes = asMember.getParameterTypes();
    List<? extends VariableElement> parameters = method.getParameters();
    for (int index = 0; index < parameters.size(); index++) {
      String parameterName = parameters.get(index).getSimpleName().toString();
      argumentNames.add(parameterName);
      methodBuilder.addParameter(TypeName.get(parameterTypes.get(index)), parameterName);
    }

    methodBuilder.varargs(copiesVarargs(method, asMember, effect));

    // Add Semigroup parameter for Validated types. It is the bridge's own, not the delegate's,
    // so it stays out of the call built below.
    if (effect.requiresSemigroup()) {
      methodBuilder.addParameter(
          ParameterizedTypeName.get(SEMIGROUP, effectArguments[0]), "semigroup");
    }

    // Build the method body
    String delegateCall =
        "delegate." + method.getSimpleName() + "(" + String.join(", ", argumentNames) + ")";

    if (effect.requiresSemigroup()) {
      methodBuilder.addStatement(
          "return $T.$L($L, semigroup)", PATH, effect.factoryMethod(), delegateCall);
    } else {
      methodBuilder.addStatement("return $T.$L($L)", PATH, effect.factoryMethod(), delegateCall);
    }

    return methodBuilder.build();
  }

  /**
   * Whether the bridge method repeats the delegate's varargs, rather than taking the array.
   *
   * <p>The bridge mirrors the delegate, so a varargs delegate stays varargs and existing call sites
   * keep their shape. Two things stop that:
   *
   * <ul>
   *   <li>a {@code Semigroup} is appended after the caller's own arguments, which leaves the array
   *       no longer last - the one position the language reserves for it
   *   <li>a non-reifiable element type ({@code T...}, {@code List<String>...}) makes the bridge
   *       method a second "possible heap pollution" warning, in a file where the author cannot
   *       write the suppression they put on their own declaration
   * </ul>
   *
   * <p>Erasure is the reifiability test, and it is deliberately the conservative one: it also rules
   * out {@code List<?>...}, which the language does reify, in favour of an array the caller can
   * always write.
   *
   * @param method the delegate method; must not be null
   * @param asMember the same method under the annotated interface's instantiation; must not be null
   * @param effect the Path the return type bridges to; must not be null
   * @return true when the bridge method is to be declared varargs
   */
  private boolean copiesVarargs(ExecutableElement method, ExecutableType asMember, Effect effect) {
    if (!method.isVarArgs() || effect.requiresSemigroup()) {
      return false;
    }
    Types types = processingEnv.getTypeUtils();
    // Cast, not a pattern: a varargs method's last parameter is an array by definition, and one
    // that is not is a javac invariant broken rather than a shape to fall back from.
    TypeMirror element = ((ArrayType) asMember.getParameterTypes().getLast()).getComponentType();
    return types.isSameType(element, types.erasure(element));
  }

  /**
   * The bounds a type variable is written with, as the annotated interface sees them.
   *
   * <p>Kind, not {@code instanceof}: an intersection type implements {@link DeclaredType} too, so a
   * pattern would take the first arm of {@code A & B} for the whole bound.
   *
   * @param variable the type variable to read; must not be null
   * @return its upper bound, or the arms of it when that bound is an intersection
   */
  private static List<? extends TypeMirror> boundsOf(TypeVariable variable) {
    TypeMirror upperBound = variable.getUpperBound();
    return upperBound.getKind() == TypeKind.INTERSECTION
        ? ((IntersectionType) upperBound).getBounds()
        : List.of(upperBound);
  }

  /**
   * The Path a supported return type bridges to, or null when the type is not one of them.
   *
   * @param returnType the {@code @PathVia} method's return type under the interface's
   *     instantiation; must not be null
   * @return the mapping, or null when nothing is declared for the type
   */
  private static Effect effectFor(TypeMirror returnType) {
    if (!(returnType instanceof DeclaredType declaredType)) {
      return null;
    }

    // Cast, not a pattern: a declared type's element is a type element, an unresolved one
    // included, and there is no other kind for the fallback arm to have answered for.
    TypeElement typeElement = (TypeElement) declaredType.asElement();
    String typeName = typeElement.getSimpleName().toString();

    return switch (typeElement.getQualifiedName().toString()) {
      case "java.util.Optional" -> new Effect(typeName, OPTIONAL_PATH, "optional", false);
      case "org.higherkindedj.hkt.maybe.Maybe" -> new Effect(typeName, MAYBE_PATH, "maybe", false);
      case "org.higherkindedj.hkt.either.Either" ->
          new Effect(typeName, EITHER_PATH, "either", false);
      case "org.higherkindedj.hkt.trymonad.Try" -> new Effect(typeName, TRY_PATH, "tryPath", false);
      case "org.higherkindedj.hkt.validated.Validated" ->
          new Effect(typeName, VALIDATION_PATH, "validated", true);
      // ioPath, not io: Path.io takes a Supplier, and IO is not one - every IO bridge written
      // against that factory was source javac refused.
      case "org.higherkindedj.hkt.io.IO" -> new Effect(typeName, IO_PATH, "ioPath", false);
      default -> null;
    };
  }

  private void error(String message, Element element) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
  }

  private void warn(String message, Element element) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, message, element);
  }

  /**
   * A supported return type and the Path it bridges to.
   *
   * @param effectName the effect's simple name, as diagnostics name it
   * @param pathType the Path class the bridge method returns
   * @param factoryMethod the {@code Path} factory that wraps what the delegate answers
   * @param requiresSemigroup whether the bridge appends a {@code Semigroup} over the error type
   */
  private record Effect(
      String effectName, ClassName pathType, String factoryMethod, boolean requiresSemigroup) {}
}
