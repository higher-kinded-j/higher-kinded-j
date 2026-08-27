// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.effect;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.*;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
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
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
import org.higherkindedj.hkt.effect.annotation.PathVia;
import org.higherkindedj.optics.processing.util.Diagnostics;
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
 * <h2>What the Bridge Refuses</h2>
 *
 * <p>The bridge is source the author never wrote and cannot correct, so every shape whose bridge
 * would not compile - or would compile with a warning - in the build that consumes it is refused at
 * the declaration, where the author can act. Which shapes those are is spelled out on {@link
 * GeneratePathBridge}.
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

  private static final String BRIDGE_TAG = "@GeneratePathBridge";
  private static final String VIA_TAG = "@PathVia";

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
          Diagnostics.error(
              processingEnv.getMessager(),
              element,
              BRIDGE_TAG,
              "'" + element.getSimpleName() + "' is not an interface.",
              "The bridge holds one delegate and calls it through an interface reference, which is"
                  + " the only shape it knows how to wrap.",
              "Move @GeneratePathBridge onto the service interface, or extract one.");
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
      Diagnostics.error(
          processingEnv.getMessager(),
          element,
          BRIDGE_TAG,
          "the bridge for '" + element.getSimpleName() + "' could not be written.",
          "The filer refused the file: " + e.getMessage() + ".",
          "Check for a second processor generating the same class name, and retry a clean build.");
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

    // The delegate field and the bridge's own bounds are written into the generated file, so both
    // have to be nameable there. Only targetPackage can make that false: written beside the
    // interface, whatever the interface can name the bridge can name too.
    if (rejectsUnnameable(
        interfaceElement.asType(), interfaceElement, interfaceElement, packageName, "delegate")) {
      return;
    }
    for (TypeParameterElement parameter : interfaceElement.getTypeParameters()) {
      if (rejectsUnnameableBound(
          parameter.getSimpleName(),
          parameter.getBounds(),
          interfaceElement,
          interfaceElement,
          packageName)) {
        return;
      }
      TypeElement rawBound = firstRawIn(parameter.getBounds());
      if (rawBound != null) {
        Diagnostics.error(
            processingEnv.getMessager(),
            interfaceElement,
            BRIDGE_TAG,
            "on '"
                + interfaceName
                + "', the bound on '"
                + parameter.getSimpleName()
                + "' names the raw type '"
                + rawBound.getSimpleName()
                + "'.",
            "The bridge repeats the bound in its own declaration, and a raw type in generated"
                + " source is a [rawtypes] warning that the suppression on your own declaration"
                + " does not cover.",
            "Name '" + rawBound.getSimpleName() + "'s type arguments.");
        return;
      }
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
      Diagnostics.warning(
          processingEnv.getMessager(),
          interfaceElement,
          BRIDGE_TAG,
          "no @PathVia method was found among '" + interfaceName + "'s members.",
          "The bridge is still written, with a constructor and nothing else; note that @PathVia is"
              + " not inherited by an override, so a method that overrides an annotated one hides"
              + " it unless it is annotated too.",
          "Put @PathVia on the methods to bridge, or drop @GeneratePathBridge.");
    }

    DeclaredType interfaceType = (DeclaredType) interfaceElement.asType();
    // Keyed by the signature the bridge will declare, not the delegate's: a renamed method and a
    // Validated's appended Semigroup both move it, and two methods landing on one signature are a
    // generated file that does not compile.
    Map<String, ExecutableElement> claimed = new LinkedHashMap<>();
    for (ExecutableElement method : pathViaMethods) {
      BridgeableMethod bridgeable =
          analyseBridgeMethod(method, interfaceType, interfaceElement, packageName);
      if (bridgeable == null) {
        continue;
      }
      ExecutableElement rival = claimed.putIfAbsent(bridgeable.signature(), method);
      if (rival != null) {
        rejectMethod(
            method,
            interfaceElement,
            "the bridge signature for " + names(method, interfaceElement) + " is already taken.",
            "'"
                + rival.getSimpleName()
                + "' bridges to the same name and parameter types, and one class cannot declare"
                + " both.",
            "Give one of them a distinct @PathVia(name = \"...\"), or drop @PathVia from it.");
        continue;
      }
      classBuilder.addMethod(emitBridgeMethod(bridgeable, interfaceElement, packageName));
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
   * bridge with a constructor and nothing else. Interface statics and privates are not inherited at
   * all, so anything reached here through a supertype is callable through the delegate.
   *
   * <p>Deduplicated by erased signature. Java's own precedence collapses an override, but two
   * <em>unrelated</em> superinterfaces declaring the same method override nothing and both arrive -
   * a legal declaration whose bridge would otherwise declare one method twice.
   *
   * @param interfaceElement the annotated interface; must not be null
   * @return its own {@code @PathVia} methods first, then the inherited ones
   */
  private List<ExecutableElement> pathViaMethodsOf(TypeElement interfaceElement) {
    Types types = processingEnv.getTypeUtils();
    List<ExecutableElement> annotated =
        ElementFilter.methodsIn(processingEnv.getElementUtils().getAllMembers(interfaceElement))
            .stream()
            .filter(method -> method.getAnnotation(PathVia.class) != null)
            .toList();
    // Own before inherited, so the generated file reads in the order the author wrote and a
    // supertype gaining a member does not reshuffle the methods already there. getAllMembers does
    // not specify an order, so this is imposed rather than relied upon.
    Map<String, ExecutableElement> distinct = new LinkedHashMap<>();
    Stream.concat(
            annotated.stream()
                .filter(method -> interfaceElement.equals(method.getEnclosingElement())),
            annotated.stream()
                .filter(method -> !interfaceElement.equals(method.getEnclosingElement())))
        .forEach(method -> distinct.putIfAbsent(erasedSignature(types, method), method));
    return List.copyOf(distinct.values());
  }

  /** A method's name and erased parameter types, which is what one class can declare once. */
  private static String erasedSignature(Types types, ExecutableElement method) {
    return method.getParameters().stream()
        .map(parameter -> types.erasure(parameter.asType()).toString())
        .collect(Collectors.joining(",", method.getSimpleName() + "(", ")"));
  }

  /**
   * A {@code @PathVia} method the bridge can write, read under the annotated interface's own
   * instantiation.
   *
   * @param method the delegate method
   * @param asMember its signature as the annotated interface has it
   * @param effect the Path its return type bridges to
   * @param effectArguments the return type's type arguments, which the Path repeats
   * @param typeVariables the method's own parameters, with their bounds under the instantiation
   * @param bridgeName the name the bridge method is declared with
   * @param signature the bridge method's name and erased parameter types
   */
  private record BridgeableMethod(
      ExecutableElement method,
      ExecutableType asMember,
      Effect effect,
      List<? extends TypeMirror> effectArguments,
      List<TypeVariableName> typeVariables,
      String bridgeName,
      String signature) {}

  /**
   * Reads a {@code @PathVia} method, or reports why no bridge for it can be written and answers
   * null.
   *
   * <p>Every gate here asks the same question: would the source this method produces compile,
   * without a warning, in the build that consumes it? The author cannot edit that file, so a
   * refusal at the declaration is the only place the answer can be acted on.
   */
  private BridgeableMethod analyseBridgeMethod(
      ExecutableElement method,
      DeclaredType interfaceType,
      TypeElement interfaceElement,
      String packageName) {

    // The bridge calls the method through its delegate, which reaches an abstract or default
    // member and nothing else. Left ungated, both of these emitted a call javac refuses.
    Set<Modifier> modifiers = method.getModifiers();
    if (modifiers.contains(Modifier.STATIC) || modifiers.contains(Modifier.PRIVATE)) {
      rejectMethod(
          method,
          interfaceElement,
          "the bridge cannot call " + names(method, interfaceElement) + ".",
          "It reaches the method through its delegate, and a "
              + (modifiers.contains(Modifier.STATIC) ? "static" : "private")
              + " interface method cannot be called that way.",
          "Make it an abstract or default instance method, or drop @PathVia from it.");
      return null;
    }

    // The method as the annotated interface has it, not as its declaration reads: a @PathVia
    // inherited from Base<T> into Derived extends Base<String> speaks String here, and copying
    // the declaration would put Base's own parameter into a bridge that never declares it.
    Types types = processingEnv.getTypeUtils();
    ExecutableType asMember = ProcessorUtils.memberOf(types, interfaceType, method);

    // Everything the bridge method writes down, in one list, so the gates below ask about all of
    // it rather than about the return type alone.
    List<TypeMirror> written = new ArrayList<>();
    written.add(asMember.getReturnType());
    written.addAll(asMember.getParameterTypes());
    written.addAll(asMember.getThrownTypes());
    for (TypeVariable variable : asMember.getTypeVariables()) {
      written.addAll(ProcessorUtils.boundsOf(variable));
    }

    // A generic delegate method's parameters are named by the return type and the arguments copied
    // below, so the bridge method has to declare them itself; the interface's own are in scope
    // already, from the class. That puts both sets in one scope, where a shared name is the
    // method's to keep and the class's to lose.
    if (rejectsShadowedTypeParameter(method, asMember, interfaceElement, written)) {
      return null;
    }

    List<TypeVariableName> typeVariables = new ArrayList<>();
    for (TypeVariable variable : asMember.getTypeVariables()) {
      Name variableName = variable.asElement().getSimpleName();
      List<? extends TypeMirror> bounds = ProcessorUtils.boundsOf(variable);
      if (rejectsUnnameableBound(variableName, bounds, method, interfaceElement, packageName)) {
        return null;
      }
      typeVariables.add(
          TypeVariableName.get(
              variableName.toString(),
              bounds.stream().map(TypeName::get).toArray(TypeName[]::new)));
    }

    TypeMirror returnType = asMember.getReturnType();
    Effect effect = effectFor(returnType);
    if (effect == null) {
      rejectMethod(
          method,
          interfaceElement,
          "the return type of "
              + names(method, interfaceElement)
              + " is '"
              + ProcessorUtils.simpleTypeName(returnType)
              + "', which no Path wraps.",
          "The bridged effects are Optional, Maybe, Either, Try, Validated and IO.",
          "Return one of those, or drop @PathVia and wrap the call by hand.");
      return null;
    }

    // Cast, not a pattern: effectFor answers non-null only for a declared type it recognised.
    List<? extends TypeMirror> effectArguments = ((DeclaredType) returnType).getTypeArguments();

    // A raw type is copied into the generated file as written, and lands there as a warning the
    // author's own @SuppressWarnings does not reach - their file is not the one that carries it.
    // Every supported effect is generic, so a bare 'Optional' head is caught by the same walk.
    TypeElement raw = firstRawIn(written);
    if (raw != null) {
      rejectMethod(
          method,
          interfaceElement,
          "the signature of "
              + names(method, interfaceElement)
              + " names the raw type '"
              + raw.getSimpleName()
              + "'.",
          "The bridge repeats it verbatim, and a raw type in generated source is a [rawtypes]"
              + " warning that the suppression on your own declaration does not cover.",
          "Name '" + raw.getSimpleName() + "'s type arguments, or drop @PathVia from this method.");
      return null;
    }

    // The Semigroup added below names the error type a second time, and a wildcard captures
    // separately at each mention, so no argument could satisfy both.
    if (effect.requiresSemigroup() && effectArguments.getFirst().getKind() == TypeKind.WILDCARD) {
      rejectMethod(
          method,
          interfaceElement,
          "the error type of the '"
              + effect.effectName()
              + "' returned by "
              + names(method, interfaceElement)
              + " is the wildcard '"
              + ProcessorUtils.simpleTypeName(effectArguments.getFirst())
              + "'.",
          "The bridge names it twice, in the "
              + effect.pathType().simpleName()
              + " it returns and in the Semigroup the caller supplies, and a wildcard is a"
              + " different captured type at each, so no caller could satisfy both.",
          "Name the error type.");
      return null;
    }

    // Everything written down has to be nameable from the package the bridge lands in. Only
    // targetPackage can make that false.
    for (TypeMirror type : written) {
      if (rejectsUnnameable(type, method, interfaceElement, packageName, "signature")) {
        return null;
      }
    }

    PathVia pathVia = method.getAnnotation(PathVia.class);
    String bridgeName =
        pathVia.name().isEmpty() ? method.getSimpleName().toString() : pathVia.name();
    if (!SourceVersion.isIdentifier(bridgeName) || SourceVersion.isKeyword(bridgeName)) {
      rejectMethod(
          method,
          interfaceElement,
          "@PathVia(name = \"" + bridgeName + "\") is not a method name.",
          "The bridge declares a method called exactly that, and javac accepts only an identifier"
              + " that is not a keyword.",
          "Give a plain Java identifier, or drop the name attribute to keep the delegate's own.");
      return null;
    }

    String signature =
        effect.requiresSemigroup()
            ? bridgeSignature(types, bridgeName, asMember) + "+Semigroup"
            : bridgeSignature(types, bridgeName, asMember);

    return new BridgeableMethod(
        method, asMember, effect, effectArguments, typeVariables, bridgeName, signature);
  }

  private static String bridgeSignature(Types types, String bridgeName, ExecutableType asMember) {
    return asMember.getParameterTypes().stream()
        .map(parameter -> types.erasure(parameter).toString())
        .collect(Collectors.joining(",", bridgeName + "(", ")"));
  }

  private MethodSpec emitBridgeMethod(
      BridgeableMethod bridgeable, TypeElement interfaceElement, String packageName) {

    ExecutableElement method = bridgeable.method();
    ExecutableType asMember = bridgeable.asMember();
    Effect effect = bridgeable.effect();
    PathVia pathVia = method.getAnnotation(PathVia.class);

    TypeName[] effectArguments =
        bridgeable.effectArguments().stream().map(TypeName::get).toArray(TypeName[]::new);
    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder(bridgeable.bridgeName())
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(effect.pathType(), effectArguments));
    bridgeable.typeVariables().forEach(methodBuilder::addTypeVariable);
    // The bridge only passes the call on, so whatever the delegate declares it can throw, the
    // bridge declares too. Dropping them left the caller with an unreported checked exception.
    asMember.getThrownTypes().forEach(thrown -> methodBuilder.addException(TypeName.get(thrown)));

    // Description first, then the block tags in order. A tag written before the description takes
    // the description into itself, which is what javadoc does with any text following a tag.
    String doc = pathVia.doc();
    if (!doc.isEmpty()) {
      methodBuilder.addJavadoc("$L\n\n", doc);
    }
    bridgeable
        .typeVariables()
        .forEach(
            variable ->
                methodBuilder.addJavadoc(
                    "@param <$L> as declared by the delegate method\n", variable.name()));
    // $T, not the simple name: an inherited method is declared elsewhere, and a link naming a type
    // the generated file never imports is one doclint rejects. The import is a real one, though,
    // so a declaring type the bridge's package cannot see is named through the interface that
    // does inherit it.
    TypeElement declarer = (TypeElement) method.getEnclosingElement();
    methodBuilder.addJavadoc(
        "@return Path-wrapped result from {@link $T#$L}\n",
        ProcessorUtils.reachableFrom(processingEnv.getElementUtils(), declarer, packageName)
            ? ClassName.get(declarer)
            : ClassName.get(interfaceElement),
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

    // Add Semigroup parameter for Validated types. It is the bridge's own, not the delegate's, so
    // it stays out of the call built below - and takes a name no parameter has already claimed.
    if (effect.requiresSemigroup()) {
      methodBuilder.addParameter(
          ParameterizedTypeName.get(SEMIGROUP, effectArguments[0]),
          freeName("semigroup", argumentNames));
    }

    // this.delegate, not delegate: the body is written as text, and a parameter named 'delegate'
    // would otherwise shadow the field and turn the call into a call on itself.
    // An explicit type witness pins the delegate's own inference, which a self-referential bound
    // ('<R extends Enum<R>>') cannot supply from the Path factory's argument position alone.
    String witness =
        bridgeable.typeVariables().isEmpty()
            ? ""
            : bridgeable.typeVariables().stream()
                .map(TypeVariableName::name)
                .collect(Collectors.joining(", ", "<", ">"));
    String delegateCall =
        "this.delegate."
            + witness
            + method.getSimpleName()
            + "("
            + String.join(", ", argumentNames)
            + ")";

    if (effect.requiresSemigroup()) {
      methodBuilder.addStatement(
          "return $T.$L($L, $L)",
          PATH,
          effect.factoryMethod(),
          delegateCall,
          freeName("semigroup", argumentNames));
    } else {
      methodBuilder.addStatement("return $T.$L($L)", PATH, effect.factoryMethod(), delegateCall);
    }

    return methodBuilder.build();
  }

  /** The wanted name, suffixed until it collides with none of the delegate's own parameters. */
  private static String freeName(String wanted, List<String> taken) {
    String candidate = wanted;
    for (int suffix = 2; taken.contains(candidate); suffix++) {
      candidate = wanted + suffix;
    }
    return candidate;
  }

  /**
   * Reports a method type parameter that hides one of the interface's that the bridge needs, and
   * returns whether it did.
   *
   * <p>The bridge declares both sets in one scope, which the delegate never does. Where the names
   * collide the method's wins, and anything the signature meant by the interface's now reads as the
   * method's: {@code <T extends U>} inherited into a {@code Derived<T>} becomes {@code <T extends
   * T>}, which is a cycle, and a parameter typed by the interface's {@code T} silently becomes the
   * method's.
   *
   * <p>Only a collision the signature actually depends on is refused. A method that shadows a name
   * it never needs - {@code <T> Optional<T> get(T t)} inherited into a {@code Derived<T>} - writes
   * down exactly as it did before.
   */
  private boolean rejectsShadowedTypeParameter(
      ExecutableElement method,
      ExecutableType asMember,
      TypeElement interfaceElement,
      List<TypeMirror> written) {

    for (TypeVariable variable : asMember.getTypeVariables()) {
      Name variableName = variable.asElement().getSimpleName();
      for (TypeParameterElement hidden : interfaceElement.getTypeParameters()) {
        if (!hidden.getSimpleName().contentEquals(variableName)) {
          continue;
        }
        if (written.stream().noneMatch(type -> ProcessorUtils.mentions(type, hidden))) {
          continue;
        }
        rejectMethod(
            method,
            interfaceElement,
            "the type parameter '"
                + variableName
                + "' on "
                + names(method, interfaceElement)
                + " has the same name as '"
                + interfaceElement.getSimpleName()
                + "'s.",
            "The bridge declares both in one scope, where the method's hides the interface's, and"
                + " this signature names the interface's.",
            "Rename the method's type parameter.");
        return true;
      }
    }
    return false;
  }

  /**
   * Reports a type the generated package cannot name, and returns whether it did.
   *
   * @param type the type as the bridge will write it; must not be null
   * @param site the declaration the diagnostic is attached to; must not be null
   * @param interfaceElement the annotated interface, which the diagnostic names
   * @param packageName the package the bridge is written into
   * @param position what the type is written as, for the message
   * @return true when the type was rejected and an error reported
   */
  private boolean rejectsUnnameable(
      TypeMirror type,
      Element site,
      TypeElement interfaceElement,
      String packageName,
      String position) {

    TypeElement unreachable =
        ProcessorUtils.firstUnreachableIn(processingEnv.getElementUtils(), type, packageName);
    if (unreachable == null) {
      return false;
    }
    reportUnreachable(unreachable, site, interfaceElement, packageName, "the " + position);
    return true;
  }

  /**
   * Reports a type parameter bound the generated package cannot name, and returns whether it did.
   *
   * <p>The bridge repeats its delegate's bounds verbatim, so a bound naming a package-private type
   * lands in a declaration that cannot see it. Only {@code targetPackage} makes this reachable: a
   * bridge written beside its interface can name everything the interface can.
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
        reportUnreachable(
            unreachable,
            site,
            interfaceElement,
            packageName,
            "the bound on '" + parameterName + "'");
        return true;
      }
    }
    return false;
  }

  private void reportUnreachable(
      TypeElement unreachable,
      Element site,
      TypeElement interfaceElement,
      String packageName,
      String position) {

    Diagnostics.error(
        processingEnv.getMessager(),
        site,
        BRIDGE_TAG,
        "on '"
            + interfaceElement.getSimpleName()
            + "', "
            + position
            + " names '"
            + unreachable.getSimpleName()
            + "', which cannot be reached from '"
            + packageName
            + "'.",
        "The bridge writes it down as it stands, and it is not visible there.",
        "Make '"
            + unreachable.getSimpleName()
            + "' public, or drop targetPackage so the bridge is written beside the interface.");
  }

  /**
   * Reports a problem with a {@code @PathVia} method somewhere the author can navigate to.
   *
   * <p>An inherited method's element can come from a compiled dependency, where a diagnostic has no
   * file and no line and the author is told only that something called 'get' is wrong. The
   * annotated interface is the declaration they wrote, so an inherited method is reported there and
   * the message says where it came from.
   */
  private void rejectMethod(
      ExecutableElement method, TypeElement interfaceElement, String what, String why, String fix) {

    Diagnostics.error(
        processingEnv.getMessager(),
        interfaceElement.equals(method.getEnclosingElement()) ? method : interfaceElement,
        VIA_TAG,
        what,
        why,
        fix);
  }

  /** Names a method for a diagnostic, saying where it was declared when that is somewhere else. */
  private static String names(ExecutableElement method, TypeElement interfaceElement) {
    return interfaceElement.equals(method.getEnclosingElement())
        ? "'" + method.getSimpleName() + "'"
        : "'"
            + method.getSimpleName()
            + "', inherited from '"
            + method.getEnclosingElement().getSimpleName()
            + "',";
  }

  /**
   * The first raw type named anywhere in the given types, or null when none is raw.
   *
   * <p>A generic type written without its arguments, at any depth: {@code Optional} as a return
   * type, {@code Optional<List>} as its argument, {@code List} as a parameter. The bridge copies
   * each verbatim, and every one of them is a {@code [rawtypes]} warning in the file it lands in.
   */
  private static TypeElement firstRawIn(List<? extends TypeMirror> written) {
    for (TypeMirror type : written) {
      TypeElement raw = firstRawIn(type);
      if (raw != null) {
        return raw;
      }
    }
    return null;
  }

  private static TypeElement firstRawIn(TypeMirror type) {
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
        TypeElement element = (TypeElement) declared.asElement();
        if (!element.getTypeParameters().isEmpty() && declared.getTypeArguments().isEmpty()) {
          return element;
        }
        for (TypeMirror argument : declared.getTypeArguments()) {
          TypeElement raw = firstRawIn(argument);
          if (raw != null) {
            return raw;
          }
        }
        // The enclosing link too: `Outer.Inner` is raw in `Outer` even where `Inner` declares
        // nothing of its own. An absent or static enclosing type is a NoType, which ends the walk.
        return firstRawIn(declared.getEnclosingType());
      }
      default -> {
        return null;
      }
    }
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
   * always write. A raw element type never reaches here - the whole signature is refused first.
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
    // included, and there is no other kind for a fallback arm to have answered for.
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
