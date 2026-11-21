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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
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
   * A string or character literal as javapoet writes one, escapes included. Each alternative
   * consumes one character or one escape at a time, so a match never backtracks over another.
   */
  private static final Pattern LITERAL =
      Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'");

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
    List<List<ClientMethod>> groups =
        ClientMethods.inheritedBy(
            iface, processingEnv.getElementUtils(), processingEnv.getTypeUtils());
    for (List<ClientMethod> declarations : groups) {
      if (refusesDivergentDeclarations(iface, declarations, packageName)) {
        valid = false;
      }
      methods.add(declarations.getFirst());
    }
    for (ClientMethod method : methods) {
      ReturnInfo info =
          refusesRawRead(iface, method) || refusesShadowedTypeParameter(iface, method)
              ? null
              : analyseReturnType(iface, method);
      if (info == null) {
        valid = false;
      }
      infos.add(info);
    }
    if (!valid) {
      // Errors already reported per offending method; skip generating an incomplete client.
      return;
    }
    warnOfIneffectiveOverrides(iface, groups, infos);

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
              + " for a concrete type argument, or declare a non-generic @HkjHttpClient interface"
              + " that extends '"
              + simpleName
              + "' with concrete type arguments; that interface gets a bean. See the Generic"
              + " Clients section of the docs.",
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
      for (ClientMethod.Parameter parameter : clientMethod.parameters()) {
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

  /**
   * Warns of each {@code @OnStatus} on a {@code MaybePath} method, which has no error channel for
   * it to decode into. Every declaration the client inherits a method from is named, though the
   * client follows one, since none of them has an effect.
   */
  private void warnOfIneffectiveOverrides(
      TypeElement iface, List<List<ClientMethod>> groups, List<ReturnInfo> infos) {
    for (int i = 0; i < groups.size(); i++) {
      if (infos.get(i).kind() == PathKind.MAYBE) {
        for (ClientMethod declaration : groups.get(i)) {
          if (declaration.element().getAnnotationsByType(OnStatus.class).length > 0) {
            warn(
                "@OnStatus has no effect on a MaybePath method (it has no error channel).",
                iface,
                declaration.element());
          }
        }
      }
    }
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
    List<ClientMethod.Parameter> parameters = clientMethod.parameters();
    String args =
        parameters.stream().map(ClientMethod.Parameter::name).collect(Collectors.joining(", "));
    for (ClientMethod.Parameter parameter : parameters) {
      facadeMethod.addParameter(
          ParameterSpec.builder(parameter.typeName(packageName), parameter.name()).build());
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
  private ParameterSpec copyParameter(ClientMethod.Parameter parameter, String packageName) {
    String parameterName = parameter.name();
    ParameterSpec.Builder builder =
        ParameterSpec.builder(parameter.typeName(packageName), parameterName);
    for (AnnotationMirror mirror : parameter.element().getAnnotationMirrors()) {
      AnnotationSpec spec = AnnotationSpec.get(mirror);
      if (needsExplicitName(mirror, parameter)) {
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
  private boolean needsExplicitName(AnnotationMirror mirror, ClientMethod.Parameter parameter) {
    String annotationType = mirror.getAnnotationType().toString();
    if (!NAME_BINDING_ANNOTATIONS.contains(annotationType)) {
      return false;
    }
    if (isMapType(ClientMethods.erasureOf(parameter, processingEnv.getTypeUtils()))) {
      return false;
    }
    return mirror.getElementValues().keySet().stream()
        .map(member -> member.getSimpleName().toString())
        .noneMatch(member -> member.equals("value") || member.equals("name"));
  }

  /**
   * True when an erased type is (a subtype of) {@link java.util.Map} — including {@code
   * MultiValueMap}.
   */
  private boolean isMapType(TypeMirror erasure) {
    var types = processingEnv.getTypeUtils();
    TypeElement mapElement = processingEnv.getElementUtils().getTypeElement("java.util.Map");
    if (mapElement == null) {
      return false;
    }
    return types.isAssignable(erasure, types.erasure(mapElement.asType()));
  }

  /**
   * Reports a method the client inherits from declarations the generated client would write
   * differently, and returns whether it did. The client implements such a method once, so it could
   * follow only one of them: call one of the endpoints they map, decode with one set of overrides,
   * or promise one contract, and which one is the author's to say.
   */
  private boolean refusesDivergentDeclarations(
      TypeElement iface, List<ClientMethod> declarations, String packageName) {
    ClientMethod first = declarations.getFirst();
    Map<String, String> written = writtenParts(first, packageName);
    for (ClientMethod other : declarations.subList(1, declarations.size())) {
      Map<String, String> otherWritten = writtenParts(other, packageName);
      Set<String> parts = new TreeSet<>(written.keySet());
      parts.addAll(otherWritten.keySet());
      Optional<String> differing =
          parts.stream()
              .filter(part -> !Objects.equals(written.get(part), otherWritten.get(part)))
              .findFirst();
      if (differing.isPresent()) {
        String name = first.element().getSimpleName().toString();
        error(
            "@HkjHttpClient implements '"
                + name
                + "' once for two methods "
                + iface.getSimpleName()
                + " inherits, "
                + qualifiedMethodName(first)
                + " and "
                + qualifiedMethodName(other)
                + ", which the generated client would write differently: "
                + differing.get()
                + " differs. Redeclare '"
                + name
                + "' on "
                + iface.getSimpleName()
                + " as the client should have it.",
            iface);
        return true;
      }
    }
    return false;
  }

  /**
   * What the generated client writes for a method, part by part: its annotations, its
   * {@code @OnStatus} overrides, its return type, its type parameters, and each parameter's type
   * and annotations.
   *
   * <p>Each part is read as the generated client means it rather than as it is spelt. A parameter's
   * name counts only where the processor injects it into a binding annotation. The overrides count
   * as the status-to-error map the decoder builds, the last one for a status winning, whatever
   * order they are written in, and not at all on a {@code MaybePath}, which builds no decoder. The
   * method's own type variables count by position, so {@code <X> X get()} and {@code <Y> Y get()},
   * one method to javac, are alike.
   */
  private Map<String, String> writtenParts(ClientMethod method, String packageName) {
    Map<String, String> parts = new TreeMap<>();
    List<? extends AnnotationMirror> methodAnnotations = method.element().getAnnotationMirrors();
    for (AnnotationMirror mirror : methodAnnotations) {
      if (!isOverride(mirror) && !isStatusOverride(mirror)) {
        parts.put("the method's " + labelOf(mirror, methodAnnotations), normalised(mirror, null));
      }
    }
    if (!returnsMaybePath(method)) {
      Map<Integer, String> overrides = new TreeMap<>();
      for (OnStatus annotation : method.element().getAnnotationsByType(OnStatus.class)) {
        overrides.put(annotation.value(), onStatusErrorType(annotation).toString());
      }
      parts.put("the method's @OnStatus overrides", overrides.toString());
    }
    List<String> ownVariables =
        method.element().getTypeParameters().stream()
            .map(parameter -> parameter.getSimpleName().toString())
            .toList();
    parts.put(
        "the method's return type",
        positional(method.returnTypeName(packageName).toString(), ownVariables));
    List<TypeVariableName> typeVariables = method.typeVariableNames(packageName);
    for (int index = 0; index < typeVariables.size(); index++) {
      TypeVariableName variable = typeVariables.get(index);
      parts.put(
          "the method's type parameter " + (index + 1),
          positional(variable + " extends " + variable.bounds(), ownVariables));
    }
    List<ClientMethod.Parameter> parameters = method.parameters();
    for (int index = 0; index < parameters.size(); index++) {
      ClientMethod.Parameter parameter = parameters.get(index);
      String label = "parameter " + (index + 1) + "'s ";
      parts.put(
          label + "type", positional(parameter.typeName(packageName).toString(), ownVariables));
      List<? extends AnnotationMirror> parameterAnnotations =
          parameter.element().getAnnotationMirrors();
      for (AnnotationMirror mirror : parameterAnnotations) {
        parts.put(
            label + labelOf(mirror, parameterAnnotations),
            normalised(mirror, needsExplicitName(mirror, parameter) ? parameter.name() : null));
      }
    }
    return parts;
  }

  /**
   * An annotation as a part's label names it: by its simple name, or by its qualified name where
   * another annotation on the same declaration shares the simple name, so neither takes the other's
   * place.
   */
  private static String labelOf(AnnotationMirror mirror, List<? extends AnnotationMirror> beside) {
    Name simpleName = mirror.getAnnotationType().asElement().getSimpleName();
    long sharing =
        beside.stream()
            .filter(
                other ->
                    other.getAnnotationType().asElement().getSimpleName().contentEquals(simpleName))
            .count();
    return "@"
        + (sharing > 1 ? qualifiedName((DeclaredType) mirror.getAnnotationType()) : simpleName);
  }

  /** Whether a method returns a {@code MaybePath}, which has no error channel to decode into. */
  private static boolean returnsMaybePath(ClientMethod method) {
    return method.returnType().getKind() == TypeKind.DECLARED
        && qualifiedName((DeclaredType) method.returnType()).equals(MAYBE_PATH);
  }

  /**
   * An annotation as the generated client applies it, for comparison: every attribute, defaults
   * included, in name order. A name-binding annotation's {@code value} and {@code name} are one
   * attribute, and a name the processor injects fills it.
   */
  private String normalised(AnnotationMirror mirror, @Nullable String injectedName) {
    Map<String, String> values = new TreeMap<>();
    processingEnv
        .getElementUtils()
        .getElementValuesWithDefaults(mirror)
        .forEach(
            (member, value) -> values.put(member.getSimpleName().toString(), value.toString()));
    String type = mirror.getAnnotationType().toString();
    if (NAME_BINDING_ANNOTATIONS.contains(type)) {
      String unnamed = "\"\"";
      String value = values.getOrDefault("value", unnamed);
      String name = values.getOrDefault("name", unnamed);
      String bound =
          !value.equals(unnamed)
              ? value
              : !name.equals(unnamed)
                  ? name
                  : injectedName == null ? unnamed : "\"" + injectedName + "\"";
      values.remove("value");
      values.put("name", bound);
    }
    return type + values;
  }

  /**
   * A type written out with each of the method's own type variables replaced by its position, so a
   * renamed variable reads the same. A variable is written bare, and a class name never is:
   * javapoet qualifies every class it writes, so a name preceded by a dot is a class's, not a
   * variable's. A type-use annotation's arguments are left as they are: a string or character
   * literal, which a value is written as, and a member name, which is followed by {@code =} where
   * no variable ever is. So {@code @Tag("X")} and {@code @Tag(X = "same")} keep their value and
   * member under {@code <X>}.
   */
  private static String positional(String written, List<String> ownVariables) {
    StringBuilder result = new StringBuilder();
    Matcher literal = LITERAL.matcher(written);
    int from = 0;
    while (literal.find()) {
      result
          .append(renumbered(written.substring(from, literal.start()), ownVariables))
          .append(literal.group());
      from = literal.end();
    }
    return result.append(renumbered(written.substring(from), ownVariables)).toString();
  }

  private static String renumbered(String code, List<String> ownVariables) {
    String result = code;
    for (int index = 0; index < ownVariables.size(); index++) {
      result =
          result.replaceAll(
              "(?<![\\w.$])" + Pattern.quote(ownVariables.get(index)) + "(?![\\w$]|\\s*=)",
              Matcher.quoteReplacement("#" + (index + 1)));
    }
    return result;
  }

  private static boolean isStatusOverride(AnnotationMirror mirror) {
    String qualified = qualifiedName((DeclaredType) mirror.getAnnotationType());
    return qualified.equals(ON_STATUS) || qualified.equals(ON_STATUSES);
  }

  /** A method as its interface declares it, such as {@code Repo.get(ID)}, for a diagnostic. */
  private static String qualifiedMethodName(ClientMethod method) {
    return method.declaring().getSimpleName() + "." + method.element();
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
    if (isMissingFromClasspath(error, ClientMethods.writerOf(clientMethod, kind.errorPath))) {
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

  /**
   * A concrete, non-generic class/interface — bindable as {@code E.class} by the decoder. An inner
   * class of a parameterised type, such as {@code Outer<String>.Problem}, has no type arguments of
   * its own but is not one: {@code Outer.Problem.class} names the raw {@code Outer}'s.
   */
  private static boolean isConcreteClass(TypeMirror type) {
    return type instanceof DeclaredType declared
        && declared.getTypeArguments().isEmpty()
        && !insideAParameterisedType(declared);
  }

  private static boolean insideAParameterisedType(DeclaredType type) {
    // A static member, or a top-level type, has no enclosing instance type: javac reports NONE.
    return type.getEnclosingType() instanceof DeclaredType enclosing
        && enclosing.getKind() == TypeKind.DECLARED
        && (!enclosing.getTypeArguments().isEmpty() || insideAParameterisedType(enclosing));
  }

  /**
   * Whether a type is absent from this compilation's classpath where only the processor can say so.
   * A class file can name a type its own module could see and this one cannot, and javac reports
   * nothing about it. In source, javac reports an unresolvable name itself, so a type written in
   * source is never treated as missing here.
   *
   * @param type the type to check
   * @param writer where the type was written: the method, for a type its annotations name, or the
   *     interface whose method declaration or {@code extends} clause names it, as {@link
   *     ClientMethods#writerOf} finds it
   */
  private boolean isMissingFromClasspath(TypeMirror type, Element writer) {
    return type.getKind() == TypeKind.ERROR && readFromClassFile(writer);
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
   * Reports a method the client reaches through a raw type that the generated client cannot
   * restate, and returns whether it did. javac reads such a member erased, and the generated client
   * restates the declaration, which overrides the erased member only where nothing in it needed the
   * erasure: no type variable of an interface, which nothing binds, no type parameter of the
   * method's own, and no parameter type that differs from its erasure. A return type may stay
   * parameterised, as it overrides covariantly.
   */
  private boolean refusesRawRead(TypeElement iface, ClientMethod method) {
    if (method.rawClause().isEmpty()) {
      return false;
    }
    Types types = processingEnv.getTypeUtils();
    Optional<String> reason =
        ClientMethods.interfaceVariableIn(method)
            .map(
                variable ->
                    "which leaves "
                        + variable.asElement().getEnclosingElement().getSimpleName()
                        + "'s type variable "
                        + variable.asElement().getSimpleName()
                        + " unbound")
            .or(
                () ->
                    method.element().getTypeParameters().isEmpty()
                            && method.parameters().stream()
                                .allMatch(
                                    parameter ->
                                        types.isSameType(
                                            parameter.type(), types.erasure(parameter.type())))
                        ? Optional.empty()
                        : Optional.of(
                            "which erases its signature, so the generated client could not"
                                + " override it"));
    if (reason.isEmpty()) {
      return false;
    }
    ClientMethod.RawClause clause = method.rawClause().get();
    error(
        "@HkjHttpClient reads this method through the raw type "
            + clause.raw().getSimpleName()
            + ", named in "
            + clause.writer().getSimpleName()
            + "'s extends clause, "
            + reason.get()
            + ". Give "
            + clause.raw().getSimpleName()
            + " its type arguments there, or redeclare '"
            + method.element().getSimpleName()
            + "' on "
            + iface.getSimpleName()
            + ".",
        iface,
        method.element());
    return true;
  }

  /**
   * Reports a type parameter of the method's own that hides one of the client's its signature
   * needs, and returns whether it did. The generated client declares both in one scope, where the
   * method's wins, so a signature that names the client's would come to mean the method's.
   */
  private boolean refusesShadowedTypeParameter(TypeElement iface, ClientMethod method) {
    Optional<TypeParameterElement> hidden = ClientMethods.hiddenClientParameter(method, iface);
    if (hidden.isEmpty()) {
      return false;
    }
    String name = hidden.get().getSimpleName().toString();
    String client = iface.getSimpleName().toString();
    error(
        "@HkjHttpClient cannot restate this method: its type parameter "
            + name
            + " hides "
            + client
            + "'s "
            + name
            + ", which the method's signature names once the type arguments "
            + client
            + " gives "
            + method.declaring().getSimpleName()
            + " are in place. Rename "
            + client
            + "'s type parameter "
            + name
            + ", or the method's.",
        iface,
        method.element());
    return true;
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
