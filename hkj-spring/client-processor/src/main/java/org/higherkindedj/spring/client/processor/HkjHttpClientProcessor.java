// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client.processor;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import com.palantir.javapoet.TypeVariableName;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.higherkindedj.spring.client.OnStatus;
import org.jspecify.annotations.Nullable;

/**
 * Generates an Effect-Path HTTP client for each interface annotated with {@code @HkjHttpClient}.
 *
 * <p>For an interface {@code UserApi} whose methods return Effect Paths (e.g. {@code
 * EitherPath<UserError, UserDto>}) alongside Spring {@code @HttpExchange} annotations, three
 * sources are generated in the same package:
 *
 * <ul>
 *   <li>{@code UserApiHttpExchange} — a native {@code @HttpExchange} interface with the same
 *       methods, the return type unwrapped to {@code ResponseEntity<T>} and all mapping annotations
 *       copied through. This is the piece Spring's {@code HttpServiceProxyFactory} proxies.
 *   <li>{@code UserApiClient} — implements {@code UserApi} by delegating to the proxied native
 *       interface and folding each outcome into the declared Path via {@code HkjClientExchange},
 *       with a typed error decoded by an injected {@code ResponseErrorDecoderFactory}.
 *   <li>{@code UserApiClientConfiguration} — a {@code @Configuration} that registers the native
 *       interface as an {@code @ImportHttpServices} group and exposes the client as a bean. Base
 *       URL, timeouts and API versioning come from {@code spring.http.serviceclient.<group>.*}.
 * </ul>
 *
 * <p>Supported return types: {@code EitherPath<E, T>}, {@code VTaskPath<Either<E, T>>}, and {@code
 * MaybePath<T>}. For an endpoint that sends no body, {@code T} is {@code Unit}: the body is ignored
 * and a 2xx gives {@code Right(Unit.INSTANCE)}, or {@code Just(Unit.INSTANCE)} on a {@code
 * MaybePath}. For an endpoint whose errors carry nothing, {@code E} is {@code Unit}: the default
 * decoder reads no error body and gives {@code Left(Unit.INSTANCE)}. {@code Void} is refused as
 * either type, and as an {@code @OnStatus} error type, since a success and an error always hold a
 * value.
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("org.higherkindedj.spring.client.HkjHttpClient")
public class HkjHttpClientProcessor extends AbstractProcessor {

  private static final String EITHER_PATH = "org.higherkindedj.hkt.effect.EitherPath";
  private static final String MAYBE_PATH = "org.higherkindedj.hkt.effect.MaybePath";
  private static final String VTASK_PATH = "org.higherkindedj.hkt.effect.VTaskPath";
  private static final String VSTREAM_PATH = "org.higherkindedj.hkt.effect.VStreamPath";
  private static final String EITHER = "org.higherkindedj.hkt.either.Either";
  private static final String UNIT = "org.higherkindedj.hkt.Unit";
  private static final String VOID = "java.lang.Void";
  private static final String HKJ_HTTP_CLIENT = "org.higherkindedj.spring.client.HkjHttpClient";
  private static final String ON_STATUS = "org.higherkindedj.spring.client.OnStatus";
  private static final String ON_STATUSES = "org.higherkindedj.spring.client.OnStatuses";

  /** Marks generated types so HKJ tooling (the compile checker, JaCoCo) can skip them. */
  private static final ClassName GENERATED =
      ClassName.get("org.higherkindedj.optics.annotations", "Generated");

  private static final ClassName HKJ_CLIENT_EXCHANGE =
      ClassName.get("org.higherkindedj.spring.client", "HkjClientExchange");
  private static final ClassName DECODER_FACTORY =
      ClassName.get("org.higherkindedj.spring.client", "ResponseErrorDecoderFactory");
  private static final ClassName RESPONSE_ENTITY =
      ClassName.get("org.springframework.http", "ResponseEntity");
  private static final ClassName CONFIGURATION =
      ClassName.get("org.springframework.context.annotation", "Configuration");
  private static final ClassName BEAN =
      ClassName.get("org.springframework.context.annotation", "Bean");
  private static final ClassName IMPORT_HTTP_SERVICES =
      ClassName.get("org.springframework.web.service.registry", "ImportHttpServices");
  private static final ClassName RESPONSE_ERROR_DECODERS =
      ClassName.get("org.higherkindedj.spring.client", "ResponseErrorDecoders");
  private static final ClassName RESPONSE_ERROR_DECODER =
      ClassName.get("org.higherkindedj.spring.client", "ResponseErrorDecoder");

  /**
   * Spring binding annotations whose parameter-name attribute the processor makes explicit in
   * generated code, so the native interface works without {@code -parameters}.
   */
  private static final Set<String> NAME_BINDING_ANNOTATIONS =
      Set.of(
          "org.springframework.web.bind.annotation.PathVariable",
          "org.springframework.web.bind.annotation.RequestParam",
          "org.springframework.web.bind.annotation.RequestHeader",
          "org.springframework.web.bind.annotation.CookieValue");

  /**
   * The flavour of Effect Path a client method returns, with where its error and success types sit
   * in it: the type-argument index at each level, outermost first.
   */
  private enum PathKind {
    /** {@code EitherPath<E, T>}. */
    EITHER(List.of(0), List.of(1)),
    /** {@code VTaskPath<Either<E, T>>}. */
    EITHER_VTASK(List.of(0, 0), List.of(0, 1)),
    /** {@code MaybePath<T>}, which has no error channel, so its error path is never read. */
    MAYBE(List.of(), List.of(0));

    private final List<Integer> errorPath;
    private final List<Integer> successPath;

    PathKind(List<Integer> errorPath, List<Integer> successPath) {
      this.errorPath = errorPath;
      this.successPath = successPath;
    }
  }

  /**
   * A method the client must implement, as the client inherits it.
   *
   * <p>The declaration gives the method's name, its parameters' names and every annotation. Its
   * types are read as a member of the client, with each type variable of an interface the client
   * extends replaced by the type argument the client gives it: a client extending {@code
   * Repo<UserDto>} implements a {@code get} that returns {@code EitherPath<ApiErr, UserDto>}, not
   * the {@code EitherPath<ApiErr, T>} that {@code Repo} declares. A type variable of the method's
   * own is not the client's to bind and stays a variable, though a bound of it that names one of
   * {@code Repo}'s is read with the client's argument in its place.
   *
   * <p>A client that reaches the method's interface through a raw type has its signature read as
   * the method declares it. javac would read it erased, losing even the types the declaration
   * writes out in full; {@link #readsCleanly} refuses the method if the declaration names a type
   * variable the raw type leaves unbound.
   *
   * @param element the method as its interface declares it
   * @param signature the method's types as a member of the client
   * @param owner the client interface's type, the instantiation {@code signature} was read under
   * @param rawClause the generic interface the client names without type arguments on its way to
   *     the method's interface, if it does
   */
  private record ClientMethod(
      ExecutableElement element,
      ExecutableType signature,
      DeclaredType owner,
      Optional<TypeElement> rawClause) {

    TypeMirror returnType() {
      return signature.getReturnType();
    }

    /** The return type's name, keeping the annotations the declaration wrote on what it binds. */
    TypeName returnTypeName(String targetPackage) {
      return TypeNames.typeNameOf(returnType(), element.getReturnType(), owner, targetPackage);
    }

    List<ClientParameter> parameters() {
      List<? extends VariableElement> declared = element.getParameters();
      List<? extends TypeMirror> types = signature.getParameterTypes();
      return IntStream.range(0, declared.size())
          .mapToObj(index -> new ClientParameter(declared.get(index), types.get(index), owner))
          .toList();
    }

    /**
     * The method's own type parameters, each bound read as the client has it: {@code <X extends T>}
     * on a {@code Repo<T>} the client extends as {@code Repo<UserDto>} is {@code <X extends
     * UserDto>}.
     */
    List<TypeVariableName> typeVariableNames(String targetPackage) {
      List<? extends TypeVariable> variables = signature.getTypeVariables();
      List<? extends TypeParameterElement> declared = element.getTypeParameters();
      return IntStream.range(0, variables.size())
          .mapToObj(
              index ->
                  TypeNames.typeVariableOf(
                      variables.get(index), declared.get(index), owner, targetPackage))
          .toList();
    }
  }

  /**
   * One parameter of a {@link ClientMethod}: its declaration, for its name and annotations, and its
   * type as the client has it.
   */
  private record ClientParameter(VariableElement element, TypeMirror type, DeclaredType owner) {

    String name() {
      return element.getSimpleName().toString();
    }

    /** The type's name, keeping the annotations the declaration wrote on what it binds. */
    TypeName typeName(String targetPackage) {
      return TypeNames.typeNameOf(type, element.asType(), owner, targetPackage);
    }
  }

  /**
   * What an {@code extends} clause binds a type variable to, and the interface the clause belongs
   * to.
   */
  private record Binding(TypeElement writer, TypeMirror argument) {}

  /** A client method's analysed return type: its path flavour and error/success type arguments. */
  private record ReturnInfo(PathKind kind, @Nullable TypeMirror error, TypeMirror success) {

    /**
     * Whether the method's success type is {@code Unit}, so the body is ignored: the native method
     * returns {@code ResponseEntity<Void>} and a 2xx gives {@code Right(Unit.INSTANCE)} or {@code
     * Just(Unit.INSTANCE)}.
     */
    boolean ignoresBody() {
      return success instanceof DeclaredType declared && qualifiedName(declared).equals(UNIT);
    }

    /**
     * The success type's name, read out of the return type's name: the shape is the one the
     * analysis matched, and the return type's name already carries every annotation the declaration
     * wrote on a variable the client binds.
     */
    TypeName successNameIn(TypeName returnName) {
      TypeName name = returnName;
      for (int index : kind.successPath) {
        name = ((ParameterizedTypeName) name).typeArguments().get(index);
      }
      return name;
    }
  }

  /** A validated {@link OnStatus} override: a status code mapped to an error subtype. */
  private record StatusOverride(int status, TypeMirror errorType) {}

  /** Creates a new processor. */
  public HkjHttpClientProcessor() {}

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    TypeElement marker = processingEnv.getElementUtils().getTypeElement(HKJ_HTTP_CLIENT);
    if (marker == null) {
      return false;
    }
    for (TypeElement type : ElementFilter.typesIn(roundEnv.getElementsAnnotatedWith(marker))) {
      if (type.getKind() != ElementKind.INTERFACE) {
        error("@HkjHttpClient can only be applied to interfaces.", type);
        continue;
      }
      try {
        generateClient(type);
      } catch (RuntimeException e) {
        // Never abort the whole round with an internal stack trace: report and move on.
        error("Failed to generate @HkjHttpClient client: " + e, type);
      }
    }
    return true;
  }

  private void generateClient(TypeElement iface) {
    String packageName =
        processingEnv.getElementUtils().getPackageOf(iface).getQualifiedName().toString();
    List<ClientMethod> methods = new ArrayList<>();
    List<ReturnInfo> infos = new ArrayList<>();
    boolean valid = true;
    for (List<ClientMethod> declarations : abstractMethods(iface)) {
      if (!declaredAlike(iface, declarations, packageName)) {
        valid = false;
      }
      methods.add(declarations.getFirst());
    }
    for (ClientMethod method : methods) {
      ReturnInfo info = readsCleanly(iface, method) ? analyseReturnType(iface, method) : null;
      if (info == null) {
        valid = false;
      }
      infos.add(info);
    }
    if (!valid) {
      // Errors already reported per offending method; skip generating an incomplete client.
      return;
    }

    String simpleName = iface.getSimpleName().toString();
    List<TypeVariableName> typeVars =
        iface.getTypeParameters().stream()
            .map(parameter -> TypeNames.typeVariableOf(parameter, packageName))
            .toList();
    boolean generic = !typeVars.isEmpty();

    ClassName nativeName = ClassName.get(packageName, simpleName + "HttpExchange");
    ClassName facadeName = ClassName.get(packageName, simpleName + "Client");
    TypeName nativeType = parameterise(nativeName, typeVars);

    writeFile(
        packageName, buildNativeInterface(iface, nativeName, typeVars, methods, infos), iface);
    writeFile(
        packageName,
        buildFacade(iface, nativeName, nativeType, facadeName, typeVars, methods, infos),
        iface);

    // A generic client cannot be a singleton bean (no concrete type argument), so the
    // @ImportHttpServices + @Bean wiring is only generated for non-generic interfaces. Generic
    // clients are codegen-only: the user instantiates the facade for a concrete type.
    if (generic) {
      note(
          "Generic @HkjHttpClient '"
              + simpleName
              + "': bean wiring skipped (a generic client cannot be a singleton bean). Instantiate "
              + facadeName.simpleName()
              + " for a concrete type argument, or extend '"
              + simpleName
              + "' from a non-generic @HkjHttpClient interface that gives its type arguments, which"
              + " gets a bean. See the Generic Clients section of the docs.",
          iface);
    } else {
      String group = groupName(iface, simpleName);
      writeFile(
          packageName,
          buildConfiguration(ClassName.get(iface), nativeName, facadeName, simpleName, group),
          iface);
    }
  }

  /**
   * Parameterises {@code raw} with the given type variables, or returns it raw when there are none.
   */
  private static TypeName parameterise(ClassName raw, List<TypeVariableName> typeVars) {
    return typeVars.isEmpty()
        ? raw
        : ParameterizedTypeName.get(raw, typeVars.toArray(new TypeName[0]));
  }

  // ---- native @HttpExchange interface -------------------------------------------------------

  private TypeSpec buildNativeInterface(
      TypeElement iface,
      ClassName nativeName,
      List<TypeVariableName> typeVars,
      List<ClientMethod> methods,
      List<ReturnInfo> infos) {
    String packageName = nativeName.packageName();
    TypeSpec.Builder builder =
        TypeSpec.interfaceBuilder(nativeName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(GENERATED)
            .addTypeVariables(typeVars)
            .addJavadoc(
                "Native Spring {@link $T}-style interface proxied for {@link $T}. Generated; do not edit.\n",
                IMPORT_HTTP_SERVICES,
                ClassName.get(iface));

    // Copy class-level annotations (e.g. @HttpExchange) except the HKJ client meta-annotations.
    for (AnnotationMirror mirror : iface.getAnnotationMirrors()) {
      if (!isClientMetaAnnotation(mirror)) {
        builder.addAnnotation(AnnotationSpec.get(mirror));
      }
    }

    for (int i = 0; i < methods.size(); i++) {
      ClientMethod clientMethod = methods.get(i);
      ExecutableElement method = clientMethod.element();
      ReturnInfo info = infos.get(i);
      TypeName successType =
          info.ignoresBody()
              ? ClassName.get(Void.class)
              : info.successNameIn(clientMethod.returnTypeName(packageName));
      MethodSpec.Builder nativeMethod =
          MethodSpec.methodBuilder(method.getSimpleName().toString())
              .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
              .returns(ParameterizedTypeName.get(RESPONSE_ENTITY, successType))
              .addTypeVariables(clientMethod.typeVariableNames(packageName));
      // Copy the mapping annotations (@GetExchange, @PostExchange, version attrs, …) verbatim, but
      // not the HKJ client meta-annotations (@OnStatus/@OnStatuses) nor @Override — the generated
      // native interface extends nothing, so a copied @Override would not compile.
      for (AnnotationMirror mirror : method.getAnnotationMirrors()) {
        if (!isClientMetaAnnotation(mirror) && !isOverride(mirror)) {
          nativeMethod.addAnnotation(AnnotationSpec.get(mirror));
        }
      }
      // Copy parameters with their binding annotations (@PathVariable, @RequestBody, …).
      // ParameterSpec.get(VariableElement) drops annotations, so copy them explicitly.
      for (ClientParameter parameter : clientMethod.parameters()) {
        nativeMethod.addParameter(copyParameter(parameter, packageName));
      }
      builder.addMethod(nativeMethod.build());
    }
    return builder.build();
  }

  // ---- the …Client facade -------------------------------------------------------------------

  private TypeSpec buildFacade(
      TypeElement iface,
      ClassName nativeName,
      TypeName nativeType,
      ClassName facadeName,
      List<TypeVariableName> typeVars,
      List<ClientMethod> methods,
      List<ReturnInfo> infos) {
    String packageName = facadeName.packageName();
    TypeSpec.Builder builder =
        TypeSpec.classBuilder(facadeName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(GENERATED)
            .addTypeVariables(typeVars)
            .addSuperinterface(parameterise(ClassName.get(iface), typeVars))
            .addJavadoc("Generated Effect-Path client. Do not edit.\n")
            .addField(nativeType, "http", Modifier.PRIVATE, Modifier.FINAL);

    MethodSpec.Builder constructor =
        MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(nativeType, "http")
            .addParameter(DECODER_FACTORY, "decoderFactory")
            .addStatement("this.http = http");

    // Decoders are constructor-initialised final fields so the (stateless, thread-safe)
    // facade does not rebuild the decoder chain on every HTTP call.
    Set<String> usedFieldNames = new HashSet<>();
    List<@Nullable String> decoderFields = new ArrayList<>();
    for (int i = 0; i < methods.size(); i++) {
      ExecutableElement method = methods.get(i).element();
      ReturnInfo info = infos.get(i);
      if (info.kind() == PathKind.MAYBE) {
        decoderFields.add(null);
        continue;
      }
      String fieldName = method.getSimpleName() + "Decoder";
      int suffix = 2;
      while (!usedFieldNames.add(fieldName)) {
        fieldName = method.getSimpleName() + "Decoder" + suffix++;
      }
      builder.addField(
          ParameterizedTypeName.get(RESPONSE_ERROR_DECODER, rawClass(info.error())),
          fieldName,
          Modifier.PRIVATE,
          Modifier.FINAL);
      constructor.addStatement("this.$L = $L", fieldName, decoderExpression(iface, method, info));
      decoderFields.add(fieldName);
    }

    builder.addMethod(constructor.build());

    for (int i = 0; i < methods.size(); i++) {
      builder.addMethod(
          buildFacadeMethod(
              iface, methods.get(i), infos.get(i), decoderFields.get(i), packageName));
    }
    return builder.build();
  }

  private MethodSpec buildFacadeMethod(
      TypeElement iface,
      ClientMethod clientMethod,
      ReturnInfo info,
      @Nullable String decoderField,
      String packageName) {
    ExecutableElement method = clientMethod.element();
    String name = method.getSimpleName().toString();
    MethodSpec.Builder facadeMethod =
        MethodSpec.methodBuilder(name)
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(clientMethod.returnTypeName(packageName))
            .addTypeVariables(clientMethod.typeVariableNames(packageName));
    List<ClientParameter> parameters = clientMethod.parameters();
    String args = parameters.stream().map(ClientParameter::name).collect(Collectors.joining(", "));
    for (ClientParameter parameter : parameters) {
      facadeMethod.addParameter(
          ParameterSpec.builder(parameter.typeName(packageName), parameter.name()).build());
    }

    if (info.kind() == PathKind.MAYBE && method.getAnnotationsByType(OnStatus.class).length > 0) {
      warn(
          "@OnStatus has no effect on a MaybePath method (it has no error channel).",
          iface,
          method);
    }

    CodeBlock call = CodeBlock.of("() -> this.http.$L($L)", name, args);
    facadeMethod.addStatement(
        switch (info.kind()) {
          case EITHER ->
              CodeBlock.of(
                  "return $T.$L($L, this.$L)",
                  HKJ_CLIENT_EXCHANGE,
                  info.ignoresBody() ? "eitherUnit" : "either",
                  call,
                  decoderField);
          case EITHER_VTASK ->
              CodeBlock.of(
                  "return $T.$L($L, this.$L)",
                  HKJ_CLIENT_EXCHANGE,
                  info.ignoresBody() ? "eitherVTaskUnit" : "eitherVTask",
                  call,
                  decoderField);
          case MAYBE ->
              CodeBlock.of(
                  "return $T.$L($L)",
                  HKJ_CLIENT_EXCHANGE,
                  info.ignoresBody() ? "maybeUnit" : "maybe",
                  call);
        });
    return facadeMethod.build();
  }

  /**
   * Builds the {@link ResponseErrorDecoder} expression for a method: {@code
   * decoderFactory.create(E.class)} by default, or a {@code ResponseErrorDecoders} status-dispatch
   * chain when the method carries {@link org.higherkindedj.spring.client.OnStatus} overrides.
   */
  private CodeBlock decoderExpression(
      TypeElement iface, ExecutableElement method, ReturnInfo info) {
    ClassName errorRaw = rawClass(info.error());
    List<StatusOverride> overrides = readStatusOverrides(iface, method, info.error());
    if (overrides.isEmpty()) {
      return CodeBlock.of("decoderFactory.create($T.class)", errorRaw);
    }
    CodeBlock.Builder builder =
        CodeBlock.builder()
            .add(
                "$T.<$T>forDefault(decoderFactory, $T.class)",
                RESPONSE_ERROR_DECODERS,
                errorRaw,
                errorRaw);
    for (StatusOverride override : overrides) {
      builder.add(".on($L, $T.class)", override.status(), rawClass(override.errorType()));
    }
    return builder.add(".build()").build();
  }

  /** Reads valid {@link org.higherkindedj.spring.client.OnStatus} overrides off a method. */
  private List<StatusOverride> readStatusOverrides(
      TypeElement iface, ExecutableElement method, TypeMirror methodError) {
    List<StatusOverride> result = new ArrayList<>();
    Set<Integer> seen = new HashSet<>();
    for (OnStatus annotation : method.getAnnotationsByType(OnStatus.class)) {
      TypeMirror errorType = onStatusErrorType(annotation);
      // Checked first: the assignability check below would call a missing type not assignable.
      if (isMissingFromClasspath(errorType, method)) {
        error(notOnClasspath("@OnStatus", errorType), iface, method);
        continue;
      }
      if (!isConcreteClass(errorType)) {
        error(
            "@OnStatus error type must be a concrete, non-generic class (so the decoder can bind it "
                + "with E.class); found: "
                + errorType,
            iface,
            method);
        continue;
      }
      if (!processingEnv.getTypeUtils().isSubtype(errorType, methodError)) {
        error(
            "@OnStatus error type "
                + errorType
                + " is not assignable to the method's error type "
                + methodError
                + ".",
            iface,
            method);
        continue;
      }
      // After the assignability check, so the fix line names Unit only where a Unit fits: Void is
      // assignable only to Object, which takes a Unit too.
      if (isVoid(errorType)) {
        error(
            voidErrorMessage("@OnStatus", "every " + annotation.value() + " response"),
            iface,
            method);
        continue;
      }
      if (!seen.add(annotation.value())) {
        warn(
            "Duplicate @OnStatus for status " + annotation.value() + "; the last one wins.",
            iface,
            method);
      }
      result.add(new StatusOverride(annotation.value(), errorType));
    }
    return result;
  }

  /** Extracts the {@code error()} class of an {@link OnStatus} as a {@link TypeMirror}. */
  private static TypeMirror onStatusErrorType(OnStatus annotation) {
    try {
      annotation.error();
      throw new IllegalStateException("unreachable: Class member access should throw");
    } catch (MirroredTypeException e) {
      return e.getTypeMirror();
    }
  }

  // ---- the @Configuration wiring ------------------------------------------------------------

  private TypeSpec buildConfiguration(
      ClassName ifaceName,
      ClassName nativeName,
      ClassName facadeName,
      String simpleName,
      String group) {
    MethodSpec bean =
        MethodSpec.methodBuilder(decapitalise(simpleName) + "Client")
            .addAnnotation(BEAN)
            .addModifiers(Modifier.PUBLIC)
            .returns(ifaceName)
            .addParameter(nativeName, "http")
            .addParameter(DECODER_FACTORY, "decoderFactory")
            .addStatement("return new $T(http, decoderFactory)", facadeName)
            .build();

    return TypeSpec.classBuilder(simpleName + "ClientConfiguration")
        .addModifiers(Modifier.PUBLIC)
        .addAnnotation(GENERATED)
        .addJavadoc(
            "Registers the {@link $T} HTTP Service group and the client bean. Generated; do not edit.\n",
            nativeName)
        .addAnnotation(
            AnnotationSpec.builder(CONFIGURATION)
                .addMember("proxyBeanMethods", "$L", false)
                .build())
        .addAnnotation(
            AnnotationSpec.builder(IMPORT_HTTP_SERVICES)
                .addMember("group", "$S", group)
                .addMember("types", "$T.class", nativeName)
                .build())
        // A class in an exported package that declares no constructor exposes the default one,
        // which -Xlint:missing-explicit-ctor reports in a named module.
        .addMethod(
            MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("Creates the configuration Spring instantiates.\n")
                .build())
        .addMethod(bean)
        .build();
  }

  // ---- analysis helpers ---------------------------------------------------------------------

  /**
   * Copies a parameter's type, name and binding annotations onto the native interface method. A
   * name-binding annotation ({@code @PathVariable}, {@code @RequestParam}, …) without an explicit
   * name gets one injected from the source parameter — the processor knows the name even when the
   * consuming build does not compile with {@code -parameters}.
   */
  private ParameterSpec copyParameter(ClientParameter parameter, String packageName) {
    String parameterName = parameter.name();
    ParameterSpec.Builder builder =
        ParameterSpec.builder(parameter.typeName(packageName), parameterName);
    for (AnnotationMirror mirror : parameter.element().getAnnotationMirrors()) {
      AnnotationSpec spec = AnnotationSpec.get(mirror);
      if (needsExplicitName(mirror, parameter.type())) {
        spec = spec.toBuilder().addMember("value", "$S", parameterName).build();
      }
      builder.addAnnotation(spec);
    }
    return builder.build();
  }

  /**
   * True for a name-binding annotation that declares neither {@code value} nor {@code name} and is
   * not an <em>aggregate</em> binder. A {@code Map}/{@code MultiValueMap} parameter on
   * {@code @RequestParam}/{@code @RequestHeader}/{@code @CookieValue}, or a {@code Map} on
   * {@code @PathVariable}, binds <em>every</em> name/value pair when left unnamed — injecting a
   * name would change it into a single-entry binder, so it must stay unnamed. The parameter's type
   * is the one the client has, so a base's {@code P} that the client binds to a {@code Map} is an
   * aggregate binder too.
   */
  private boolean needsExplicitName(AnnotationMirror mirror, TypeMirror parameterType) {
    String annotationType = mirror.getAnnotationType().toString();
    if (!NAME_BINDING_ANNOTATIONS.contains(annotationType)) {
      return false;
    }
    if (isMapType(parameterType)) {
      return false;
    }
    return mirror.getElementValues().keySet().stream()
        .map(member -> member.getSimpleName().toString())
        .noneMatch(member -> member.equals("value") || member.equals("name"));
  }

  /**
   * True when {@code type} is (a subtype of) {@link java.util.Map} — including {@code
   * MultiValueMap}.
   */
  private boolean isMapType(TypeMirror type) {
    var types = processingEnv.getTypeUtils();
    TypeElement mapElement = processingEnv.getElementUtils().getTypeElement("java.util.Map");
    if (mapElement == null) {
      return false;
    }
    return types.isAssignable(types.erasure(type), types.erasure(mapElement.asType()));
  }

  /**
   * The abstract methods the generated client must implement — including those inherited from
   * super-interfaces, so a client interface that {@code extends} a shared base still generates a
   * complete native interface and facade. Each is read as a member of the client, so a generic
   * base's type variables come back as the type arguments the client gives it. {@code
   * default}/{@code static}/{@link Object} methods are excluded.
   *
   * <p>Each entry holds every declaration of one method the client implements, grouped by erased
   * signature: {@code getAllMembers} does NOT collapse the same method declared independently in
   * two unrelated super-interfaces (a diamond), nor two methods of one generic interface that the
   * client's type arguments make the same, and the client implements each only once. The
   * declaration {@code getAllMembers} lists first comes first.
   */
  private List<List<ClientMethod>> abstractMethods(TypeElement iface) {
    DeclaredType owner = (DeclaredType) iface.asType();
    // getAllMembers has no guaranteed order across compilers/runs; key the groups by a stable key
    // so the generated sources are deterministic (reproducible builds, no spurious diffs / cache
    // misses).
    Map<String, List<ClientMethod>> groups = new TreeMap<>();
    for (Element member : processingEnv.getElementUtils().getAllMembers(iface)) {
      if (member.getKind() == ElementKind.METHOD
          && member.getModifiers().contains(Modifier.ABSTRACT)
          && !isObjectMethod(member)) {
        ExecutableElement method = (ExecutableElement) member;
        Optional<TypeElement> rawClause =
            rawClauseTo(iface, (TypeElement) method.getEnclosingElement());
        // Total: asMemberOf answers with an ExecutableType for a method, and every member here is
        // one the client has, so it can always be read as one of the client's.
        ExecutableType signature =
            rawClause.isPresent()
                ? (ExecutableType) method.asType()
                : (ExecutableType) processingEnv.getTypeUtils().asMemberOf(owner, method);
        ClientMethod clientMethod = new ClientMethod(method, signature, owner, rawClause);
        groups
            .computeIfAbsent(erasedSignature(clientMethod), key -> new ArrayList<>())
            .add(clientMethod);
      }
    }
    return List.copyOf(groups.values());
  }

  /**
   * Reports a method the client inherits from two declarations that the generated client would
   * restate differently, and returns whether they all restate alike. The client implements such a
   * method once, so it could call only one of the endpoints they map, and which one is the author's
   * to say.
   */
  private boolean declaredAlike(
      TypeElement iface, List<ClientMethod> declarations, String packageName) {
    ClientMethod first = declarations.getFirst();
    String restated = restatement(first, packageName);
    for (ClientMethod other : declarations.subList(1, declarations.size())) {
      if (!restatement(other, packageName).equals(restated)) {
        error(
            "@HkjHttpClient implements '"
                + first.element().getSimpleName()
                + "' once for two methods "
                + iface.getSimpleName()
                + " inherits, "
                + qualifiedMethodName(first)
                + " and "
                + qualifiedMethodName(other)
                + ", which map it differently. Redeclare '"
                + first.element().getSimpleName()
                + "' on "
                + iface.getSimpleName()
                + " with the mapping it should have.",
            iface);
        return false;
      }
    }
    return true;
  }

  /**
   * What the generated client writes for a method, as text: its annotations, its return type, and
   * each parameter's annotations, type and name.
   */
  private static String restatement(ClientMethod method, String packageName) {
    StringBuilder text = new StringBuilder();
    method.element().getAnnotationMirrors().stream()
        .filter(mirror -> !isOverride(mirror))
        .forEach(mirror -> text.append(AnnotationSpec.get(mirror)).append(' '));
    text.append(method.returnTypeName(packageName)).append('(');
    for (ClientParameter parameter : method.parameters()) {
      parameter
          .element()
          .getAnnotationMirrors()
          .forEach(mirror -> text.append(AnnotationSpec.get(mirror)).append(' '));
      text.append(parameter.typeName(packageName)).append(' ').append(parameter.name()).append(',');
    }
    return text.append(')').toString();
  }

  /** A method as its interface declares it, such as {@code Repo.get(ID)}, for a diagnostic. */
  private static String qualifiedMethodName(ClientMethod method) {
    return method.element().getEnclosingElement().getSimpleName() + "." + method.element();
  }

  /**
   * A name + erased-parameter-types key, so diamond-inherited duplicates collapse to one method.
   * The parameter types are the client's: {@code get(ID)} in a {@code Repo<ID>} the client extends
   * as {@code Repo<String>} is the same method as another base's {@code get(String)}, though the
   * two declarations erase differently.
   */
  private String erasedSignature(ClientMethod method) {
    StringBuilder key = new StringBuilder(method.element().getSimpleName().toString()).append('(');
    for (TypeMirror parameterType : method.signature().getParameterTypes()) {
      key.append(processingEnv.getTypeUtils().erasure(parameterType)).append(',');
    }
    return key.append(')').toString();
  }

  private static boolean isObjectMethod(Element method) {
    return method.getEnclosingElement() instanceof TypeElement type
        && type.getQualifiedName().contentEquals("java.lang.Object");
  }

  private static boolean isOverride(AnnotationMirror mirror) {
    return qualifiedName((DeclaredType) mirror.getAnnotationType()).equals("java.lang.Override");
  }

  private @Nullable ReturnInfo analyseReturnType(TypeElement iface, ClientMethod clientMethod) {
    ExecutableElement method = clientMethod.element();
    TypeMirror returnType = clientMethod.returnType();
    if (!(returnType instanceof DeclaredType declared)) {
      return unsupported(iface, method);
    }
    String qualified = qualifiedName(declared);
    List<? extends TypeMirror> args = declared.getTypeArguments();
    switch (qualified) {
      case EITHER_PATH:
        if (args.size() == 2) {
          return eitherInfo(PathKind.EITHER, iface, clientMethod);
        }
        break;
      case MAYBE_PATH:
        if (args.size() == 1) {
          TypeMirror success = typeAt(returnType, PathKind.MAYBE.successPath);
          return refusesVoidSuccess(success, iface, method)
              ? null
              : new ReturnInfo(PathKind.MAYBE, null, success);
        }
        break;
      case VTASK_PATH:
        if (args.size() == 1 && args.get(0).getKind() == TypeKind.DECLARED) {
          DeclaredType inner = (DeclaredType) args.get(0);
          if (qualifiedName(inner).equals(EITHER) && inner.getTypeArguments().size() == 2) {
            return eitherInfo(PathKind.EITHER_VTASK, iface, clientMethod);
          }
        }
        break;
      case VSTREAM_PATH:
        error(
            "VStreamPath is not generated automatically. Consume the server's SSE stream directly "
                + "with HkjClientExchange.vstream(source, ElementType.class, jsonMapper).",
            iface,
            method);
        return null;
      default:
        break;
    }
    return unsupported(iface, method);
  }

  /**
   * The type at a position of a type the analysis has matched: the type-argument index at each
   * level, outermost first.
   */
  private static TypeMirror typeAt(TypeMirror type, List<Integer> path) {
    TypeMirror current = type;
    for (int index : path) {
      current = ((DeclaredType) current).getTypeArguments().get(index);
    }
    return current;
  }

  /**
   * Builds an Either-flavoured {@link ReturnInfo}, rejecting an error type that is itself a type
   * variable — the decoder needs a concrete {@code E.class}, which a type variable cannot provide —
   * and a {@code Void} on either side, which no {@code Left} or {@code Right} can hold. The error
   * and success types are the client's: a base's type variable the client binds to a concrete class
   * passes, and one a generic client leaves a type variable of its own is refused.
   */
  private @Nullable ReturnInfo eitherInfo(
      PathKind kind, TypeElement iface, ClientMethod clientMethod) {
    ExecutableElement method = clientMethod.element();
    TypeMirror error = typeAt(clientMethod.returnType(), kind.errorPath);
    TypeMirror success = typeAt(clientMethod.returnType(), kind.successPath);
    if (isMissingFromClasspath(error, writerOf(clientMethod, kind.errorPath))) {
      error(notOnClasspath("@HkjHttpClient", error), iface, method);
      return null;
    }
    if (error.getKind() == TypeKind.TYPEVAR) {
      error(
          "@HkjHttpClient error type cannot be a type variable; it must be a concrete class so the "
              + "decoder can bind it.",
          iface,
          method);
      return null;
    }
    // The decoder binds the error via E.class, so the error type must be a concrete, non-generic
    // class — not an array, wildcard, or parameterised type (which would crash rawClass or emit an
    // uncompilable create(Raw.class) call).
    if (!isConcreteClass(error)) {
      error(
          "@HkjHttpClient error type must be a concrete, non-generic class (so the decoder can bind "
              + "it with E.class); found: "
              + error,
          iface,
          method);
      return null;
    }
    // Both are checked, so a method that declares Void on each side hears about each.
    boolean voidError = refusesVoidError(error, iface, method);
    boolean voidSuccess = refusesVoidSuccess(success, iface, method);
    return voidError || voidSuccess ? null : new ReturnInfo(kind, error, success);
  }

  /**
   * Reports a {@code Void} success type, which has no value for a {@code Right} or a {@code Just}
   * to hold, and names {@code Unit} as the type that says the endpoint sends no body.
   */
  private boolean refusesVoidSuccess(
      TypeMirror success, TypeElement iface, ExecutableElement method) {
    if (isVoid(success)) {
      error(
          "@HkjHttpClient success type Void cannot hold a value, but a success always holds one."
              + " Replace Void with org.higherkindedj.hkt.Unit: the client then ignores the response"
              + " body and gives Right(Unit.INSTANCE), or Just(Unit.INSTANCE) on a MaybePath.",
          iface,
          method);
      return true;
    }
    return false;
  }

  /**
   * Reports a {@code Void} error type, which has no value for a {@code Left} to hold, and names
   * {@code Unit} as the type that says the endpoint's errors carry nothing.
   */
  private boolean refusesVoidError(TypeMirror error, TypeElement iface, ExecutableElement method) {
    if (isVoid(error)) {
      error(voidErrorMessage("@HkjHttpClient", "every error response"), iface, method);
      return true;
    }
    return false;
  }

  private static String voidErrorMessage(String annotation, String responses) {
    return annotation
        + " error type Void cannot hold a value, but a Left always holds one. Replace Void with"
        + " org.higherkindedj.hkt.Unit: the default decoder then reads no error body, and "
        + responses
        + " gives Left(Unit.INSTANCE).";
  }

  private static boolean isVoid(TypeMirror type) {
    return type.getKind() == TypeKind.DECLARED && qualifiedName((DeclaredType) type).equals(VOID);
  }

  /** A concrete, non-generic class/interface — bindable as {@code E.class} by the decoder. */
  private static boolean isConcreteClass(TypeMirror type) {
    return type instanceof DeclaredType declared && declared.getTypeArguments().isEmpty();
  }

  /**
   * Whether a type is absent from this compilation's classpath where only the processor can say so.
   * A class file can name a type its own module could see and this one cannot, and javac reports
   * nothing about it. In source, javac reports an unresolvable name itself, so a type written in
   * source is never treated as missing here.
   *
   * @param type the type to check
   * @param writer where the type was written: the method, for a type its annotations name, or the
   *     interface whose method declaration or {@code extends} clause names it, as {@link #writerOf}
   *     finds it
   */
  private boolean isMissingFromClasspath(TypeMirror type, Element writer) {
    return type.getKind() == TypeKind.ERROR && readFromClassFile(writer);
  }

  /**
   * The interface that wrote the type at one position of a method's return type.
   *
   * <p>Where the method's declaration names a type there, the method's own interface wrote it.
   * Where it names a type variable, the {@code extends} clause that binds the variable wrote what
   * stands in its place, and a clause that binds it to a variable of its own interface hands the
   * question on to the clause that binds that one. A client that binds a base's {@code E} to a type
   * it misspells wrote that type in source, where javac reports it, though the base and its
   * declaration were read from a class file.
   *
   * @param path the type-argument index at each level from the return type to the position,
   *     outermost first
   */
  private static TypeElement writerOf(ClientMethod method, List<Integer> path) {
    return writerOf(
        method.element().getReturnType(),
        path,
        (TypeElement) method.element().getEnclosingElement(),
        bindingsOf((TypeElement) method.owner().asElement()));
  }

  private static TypeElement writerOf(
      TypeMirror type, List<Integer> path, TypeElement writer, Map<Element, Binding> bindings) {
    if (type.getKind() == TypeKind.TYPEVAR) {
      Binding binding = bindings.get(((TypeVariable) type).asElement());
      return binding == null
          ? writer
          : writerOf(binding.argument(), path, binding.writer(), bindings);
    }
    if (path.isEmpty() || type.getKind() != TypeKind.DECLARED) {
      return writer;
    }
    List<? extends TypeMirror> arguments = ((DeclaredType) type).getTypeArguments();
    return path.getFirst() < arguments.size()
        ? writerOf(arguments.get(path.getFirst()), path.subList(1, path.size()), writer, bindings)
        : writer;
  }

  /**
   * What each {@code extends} clause among the client's interfaces, and theirs in turn, binds each
   * type parameter to, the first binding of a parameter kept. A raw clause binds nothing. A clause
   * names only the variables of the interface it belongs to, which a clause nearer the client binds
   * in turn, so following the bindings from any variable ends.
   */
  private static Map<Element, Binding> bindingsOf(TypeElement client) {
    Map<Element, Binding> bindings = new HashMap<>();
    bindClauses(client, new HashSet<>(), bindings);
    return Map.copyOf(bindings);
  }

  private static void bindClauses(
      TypeElement type, Set<TypeElement> visited, Map<Element, Binding> bindings) {
    if (!visited.add(type)) {
      return;
    }
    for (TypeMirror parent : type.getInterfaces()) {
      DeclaredType clause = (DeclaredType) parent;
      TypeElement extended = (TypeElement) clause.asElement();
      List<? extends TypeParameterElement> parameters = extended.getTypeParameters();
      List<? extends TypeMirror> arguments = clause.getTypeArguments();
      if (arguments.size() == parameters.size()) {
        for (int index = 0; index < parameters.size(); index++) {
          bindings.putIfAbsent(parameters.get(index), new Binding(type, arguments.get(index)));
        }
      }
      bindClauses(extended, visited, bindings);
    }
  }

  /**
   * Whether an element was read from a class file. {@code Elements.getFileObjectOf} may be
   * unsupported outside javac; there the element is treated as source, so an unresolvable type
   * falls through to the checks that follow rather than being reported as a missing dependency the
   * processor could not confirm.
   */
  private boolean readFromClassFile(Element element) {
    try {
      return Optional.ofNullable(processingEnv.getElementUtils().getFileObjectOf(element))
          .map(JavaFileObject::getKind)
          .filter(JavaFileObject.Kind.CLASS::equals)
          .isPresent();
    } catch (UnsupportedOperationException e) {
      return false;
    }
  }

  private static String notOnClasspath(String annotation, TypeMirror type) {
    return annotation
        + " error type "
        + type
        + " is not on this compilation's classpath; add the dependency that declares it.";
  }

  private @Nullable ReturnInfo unsupported(TypeElement iface, ExecutableElement method) {
    error(
        "Unsupported @HkjHttpClient return type. Expected EitherPath<E, T>, "
            + "VTaskPath<Either<E, T>>, or MaybePath<T>.",
        iface,
        method);
    return null;
  }

  /**
   * Reports a method whose signature the generated client cannot restate with the meaning it has
   * for the client, and returns whether it can.
   *
   * <p>Two shapes cannot be restated. Read through a raw type, a declaration that names a type
   * variable of its interface names one nothing binds, and the generated client cannot write it. A
   * type parameter of the method's own that has the name of one of the client's hides the client's
   * where the generated client declares both, so a signature that names the client's would come to
   * mean the method's. A method that hides a name its signature never uses restates as it is.
   */
  private boolean readsCleanly(TypeElement iface, ClientMethod method) {
    ExecutableElement element = method.element();
    if (method.rawClause().isPresent()) {
      TypeElement raw = method.rawClause().get();
      Optional<TypeVariable> unbound =
          signatureTypes(
                  element.getReturnType(),
                  element.getParameters().stream().map(VariableElement::asType),
                  element.getTypeParameters().stream()
                      .flatMap(parameter -> parameter.getBounds().stream()))
              .flatMap(HkjHttpClientProcessor::typeVariablesIn)
              .filter(variable -> !declaredByMethod(variable))
              .findFirst();
      if (unbound.isPresent()) {
        error(
            "@HkjHttpClient reads this method through the raw type "
                + raw.getSimpleName()
                + ", which leaves "
                + unbound.get().asElement().getEnclosingElement().getSimpleName()
                + "'s type variable "
                + unbound.get()
                + " unbound. Write "
                + raw.getSimpleName()
                + "'s type arguments in the extends clause that names it.",
            iface,
            element);
        return false;
      }
    }
    ExecutableType signature = method.signature();
    for (TypeVariable own : signature.getTypeVariables()) {
      Name name = own.asElement().getSimpleName();
      Optional<? extends TypeParameterElement> hidden =
          iface.getTypeParameters().stream()
              .filter(parameter -> parameter.getSimpleName().contentEquals(name))
              .filter(
                  parameter ->
                      signatureTypes(
                              signature.getReturnType(),
                              signature.getParameterTypes().stream(),
                              signature.getTypeVariables().stream()
                                  .map(TypeVariable::getUpperBound))
                          .flatMap(HkjHttpClientProcessor::typeVariablesIn)
                          .anyMatch(variable -> variable.asElement().equals(parameter)))
              .findFirst();
      if (hidden.isPresent()) {
        error(
            "@HkjHttpClient cannot restate this method: its type parameter "
                + name
                + " hides "
                + iface.getSimpleName()
                + "'s "
                + name
                + ", which the method's signature names once "
                + iface.getSimpleName()
                + "'s type arguments are in place. Rename one of the two.",
            iface,
            element);
        return false;
      }
    }
    return true;
  }

  /** A method's return type, then its parameter types, then the bounds of its type parameters. */
  private static Stream<TypeMirror> signatureTypes(
      TypeMirror returnType,
      Stream<? extends TypeMirror> parameterTypes,
      Stream<? extends TypeMirror> bounds) {
    return Stream.of(Stream.of(returnType), parameterTypes, bounds).flatMap(stream -> stream);
  }

  /**
   * Every type variable a type names, at any depth: in its type arguments, its enclosing type, an
   * array's component, a wildcard's bounds and an intersection's arms. A variable's own bounds are
   * not followed, as they belong to its declaration, and a recursive bound such as {@code <X
   * extends Comparable<X>>} would lead back to it.
   */
  private static Stream<TypeVariable> typeVariablesIn(TypeMirror type) {
    return switch (type.getKind()) {
      case TYPEVAR -> Stream.of((TypeVariable) type);
      case DECLARED -> {
        DeclaredType declared = (DeclaredType) type;
        yield Stream.concat(
                Stream.of(declared.getEnclosingType()), declared.getTypeArguments().stream())
            .flatMap(HkjHttpClientProcessor::typeVariablesIn);
      }
      case ARRAY -> typeVariablesIn(((ArrayType) type).getComponentType());
      case WILDCARD -> {
        WildcardType wildcard = (WildcardType) type;
        yield Stream.of(wildcard.getExtendsBound(), wildcard.getSuperBound())
            .filter(Objects::nonNull)
            .flatMap(HkjHttpClientProcessor::typeVariablesIn);
      }
      case INTERSECTION ->
          ((IntersectionType) type)
              .getBounds().stream().flatMap(HkjHttpClientProcessor::typeVariablesIn);
      default -> Stream.empty();
    };
  }

  /** Whether a type variable is one a method declares, rather than a class or an interface. */
  private static boolean declaredByMethod(TypeVariable variable) {
    return ((TypeParameterElement) variable.asElement()).getGenericElement().getKind()
        == ElementKind.METHOD;
  }

  /**
   * The first generic interface the client names without type arguments on its way to the interface
   * that declares an inherited method, if it names one. javac reads every member reached through a
   * raw type erased. A client's own methods are reached through nothing.
   */
  private Optional<TypeElement> rawClauseTo(TypeElement from, TypeElement declaring) {
    Types types = processingEnv.getTypeUtils();
    TypeMirror target = types.erasure(declaring.asType());
    for (TypeMirror parent : from.getInterfaces()) {
      if (!types.isSubtype(types.erasure(parent), target)) {
        continue;
      }
      TypeElement extended = (TypeElement) ((DeclaredType) parent).asElement();
      if (isRaw(parent)) {
        return Optional.of(extended);
      }
      Optional<TypeElement> deeper = rawClauseTo(extended, declaring);
      if (deeper.isPresent()) {
        return deeper;
      }
    }
    return Optional.empty();
  }

  /** Whether a type names a generic class or interface without its type arguments. */
  private static boolean isRaw(TypeMirror type) {
    return type.getKind() == TypeKind.DECLARED
        && ((DeclaredType) type).getTypeArguments().isEmpty()
        && !((TypeElement) ((DeclaredType) type).asElement()).getTypeParameters().isEmpty();
  }

  private static String qualifiedName(DeclaredType type) {
    return ((TypeElement) type.asElement()).getQualifiedName().toString();
  }

  /**
   * The raw {@link ClassName} of an error type, for use in {@code decoderFactory.create(E.class)}.
   */
  private static ClassName rawClass(TypeMirror error) {
    // Not TypeNames.typeNameOf: this name is emitted as a class literal, and `@Nullable E.class`
    // does not compile. The raw class is the whole point here, annotations included.
    TypeName name = TypeName.get(error);
    if (name instanceof ParameterizedTypeName parameterized) {
      return parameterized.rawType();
    }
    return (ClassName) name;
  }

  private String groupName(TypeElement iface, String simpleName) {
    for (AnnotationMirror mirror : iface.getAnnotationMirrors()) {
      if (isMarker(mirror)) {
        for (var entry : mirror.getElementValues().entrySet()) {
          if (entry.getKey().getSimpleName().contentEquals("group")) {
            String value = entry.getValue().getValue().toString();
            if (!value.isBlank()) {
              return value;
            }
          }
        }
      }
    }
    return decapitalise(simpleName);
  }

  private static boolean isMarker(AnnotationMirror mirror) {
    return qualifiedName((DeclaredType) mirror.getAnnotationType()).equals(HKJ_HTTP_CLIENT);
  }

  /** HKJ client meta-annotations that must not be copied onto the generated native interface. */
  private static boolean isClientMetaAnnotation(AnnotationMirror mirror) {
    String qualified = qualifiedName((DeclaredType) mirror.getAnnotationType());
    return qualified.equals(HKJ_HTTP_CLIENT)
        || qualified.equals(ON_STATUS)
        || qualified.equals(ON_STATUSES);
  }

  /**
   * Decapitalises following the JavaBeans rule ({@code java.beans.Introspector.decapitalize}): a
   * name whose first two characters are both upper case (an acronym like {@code URLClientApi}) is
   * left unchanged, so the derived group/bean name matches what Spring would compute.
   */
  private static String decapitalise(String value) {
    if (value.isEmpty()
        || (value.length() > 1
            && Character.isUpperCase(value.charAt(0))
            && Character.isUpperCase(value.charAt(1)))) {
      return value;
    }
    return Character.toLowerCase(value.charAt(0)) + value.substring(1);
  }

  private void writeFile(String packageName, TypeSpec type, TypeElement originating) {
    try {
      JavaFile.builder(packageName, type)
          .addFileComment("Generated by hkj-spring-client-processor. Do not edit.")
          .build()
          .writeTo(processingEnv.getFiler());
    } catch (IOException e) {
      error("Could not write generated client: " + e.getMessage(), originating);
    }
  }

  private void error(String message, TypeElement type) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, type);
  }

  private void error(String message, TypeElement iface, ExecutableElement method) {
    report(Diagnostic.Kind.ERROR, message, iface, method);
  }

  private void warn(String message, TypeElement iface, ExecutableElement method) {
    report(Diagnostic.Kind.WARNING, message, iface, method);
  }

  /**
   * Reports a problem with one of a client's methods somewhere the author can navigate to.
   *
   * <p>A method the client declares is reported at its declaration. An inherited method is reported
   * on the client interface, naming the method and the interface it came from: one read from a
   * compiled dependency has no file and no line of its own, and a base compiled alongside follows
   * the same rule, so where an inherited method's problem appears never depends on how its base was
   * built.
   */
  private void report(
      Diagnostic.Kind kind, String message, TypeElement iface, ExecutableElement method) {
    Element declaring = method.getEnclosingElement();
    if (iface.equals(declaring)) {
      processingEnv.getMessager().printMessage(kind, message, method);
    } else {
      processingEnv
          .getMessager()
          .printMessage(
              kind,
              "'"
                  + method.getSimpleName()
                  + "', inherited from '"
                  + declaring.getSimpleName()
                  + "': "
                  + message,
              iface);
    }
  }

  private void note(String message, TypeElement type) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, message, type);
  }
}
