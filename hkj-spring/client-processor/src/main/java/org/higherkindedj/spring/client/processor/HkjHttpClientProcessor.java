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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
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
 * MaybePath<T>}.
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("org.higherkindedj.spring.client.HkjHttpClient")
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public class HkjHttpClientProcessor extends AbstractProcessor {

  private static final String EITHER_PATH = "org.higherkindedj.hkt.effect.EitherPath";
  private static final String MAYBE_PATH = "org.higherkindedj.hkt.effect.MaybePath";
  private static final String VTASK_PATH = "org.higherkindedj.hkt.effect.VTaskPath";
  private static final String VSTREAM_PATH = "org.higherkindedj.hkt.effect.VStreamPath";
  private static final String EITHER = "org.higherkindedj.hkt.either.Either";
  private static final String HKJ_HTTP_CLIENT = "org.higherkindedj.spring.client.HkjHttpClient";
  private static final String ON_STATUS = "org.higherkindedj.spring.client.OnStatus";
  private static final String ON_STATUSES = "org.higherkindedj.spring.client.OnStatuses";

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

  /** The flavour of Effect Path a client method returns. */
  private enum PathKind {
    EITHER,
    EITHER_VTASK,
    MAYBE
  }

  /** A client method's analysed return type: its path flavour and error/success type arguments. */
  private record ReturnInfo(PathKind kind, @Nullable TypeMirror error, TypeMirror success) {}

  /** A validated {@link OnStatus} override: a status code mapped to an error subtype. */
  private record StatusOverride(int status, TypeMirror errorType) {}

  /** Creates a new processor. */
  public HkjHttpClientProcessor() {}

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    TypeElement marker = processingEnv.getElementUtils().getTypeElement(HKJ_HTTP_CLIENT);
    if (marker == null) {
      return false;
    }
    for (Element element : roundEnv.getElementsAnnotatedWith(marker)) {
      if (element.getKind() != ElementKind.INTERFACE) {
        error("@HkjHttpClient can only be applied to interfaces.", element);
        continue;
      }
      try {
        generateClient((TypeElement) element);
      } catch (RuntimeException e) {
        // Never abort the whole round with an internal stack trace: report and move on.
        error("Failed to generate @HkjHttpClient client: " + e, element);
      }
    }
    return true;
  }

  private void generateClient(TypeElement iface) {
    List<ExecutableElement> methods = abstractMethods(iface);
    List<ReturnInfo> infos = new ArrayList<>();
    boolean valid = true;
    for (ExecutableElement method : methods) {
      ReturnInfo info = analyseReturnType(method);
      if (info == null) {
        valid = false;
      }
      infos.add(info);
    }
    if (!valid) {
      // Errors already reported per offending method; skip generating an incomplete client.
      return;
    }

    String packageName =
        processingEnv.getElementUtils().getPackageOf(iface).getQualifiedName().toString();
    String simpleName = iface.getSimpleName().toString();
    List<TypeVariableName> typeVars =
        iface.getTypeParameters().stream().map(TypeVariableName::get).toList();
    boolean generic = !typeVars.isEmpty();

    ClassName nativeName = ClassName.get(packageName, simpleName + "HttpExchange");
    ClassName facadeName = ClassName.get(packageName, simpleName + "Client");
    TypeName ifaceType = parameterise(ClassName.get(iface), typeVars);
    TypeName nativeType = parameterise(nativeName, typeVars);

    writeFile(
        packageName, buildNativeInterface(iface, nativeName, typeVars, methods, infos), iface);
    writeFile(
        packageName,
        buildFacade(ifaceType, nativeName, nativeType, facadeName, typeVars, methods, infos),
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
              + " for a concrete type argument — see the Generics section of the docs.",
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
      List<ExecutableElement> methods,
      List<ReturnInfo> infos) {
    TypeSpec.Builder builder =
        TypeSpec.interfaceBuilder(nativeName)
            .addModifiers(Modifier.PUBLIC)
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
      ExecutableElement method = methods.get(i);
      TypeName successType = TypeName.get(infos.get(i).success());
      MethodSpec.Builder nativeMethod =
          MethodSpec.methodBuilder(method.getSimpleName().toString())
              .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
              .returns(ParameterizedTypeName.get(RESPONSE_ENTITY, successType));
      for (var typeParam : method.getTypeParameters()) {
        nativeMethod.addTypeVariable(TypeVariableName.get(typeParam));
      }
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
      for (VariableElement parameter : method.getParameters()) {
        nativeMethod.addParameter(copyParameter(parameter));
      }
      builder.addMethod(nativeMethod.build());
    }
    return builder.build();
  }

  // ---- the …Client facade -------------------------------------------------------------------

  private TypeSpec buildFacade(
      TypeName ifaceType,
      ClassName nativeName,
      TypeName nativeType,
      ClassName facadeName,
      List<TypeVariableName> typeVars,
      List<ExecutableElement> methods,
      List<ReturnInfo> infos) {
    TypeSpec.Builder builder =
        TypeSpec.classBuilder(facadeName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addTypeVariables(typeVars)
            .addSuperinterface(ifaceType)
            .addJavadoc("Generated Effect-Path client. Do not edit.\n")
            .addField(nativeType, "http", Modifier.PRIVATE, Modifier.FINAL)
            .addField(DECODER_FACTORY, "decoderFactory", Modifier.PRIVATE, Modifier.FINAL)
            .addMethod(
                MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(nativeType, "http")
                    .addParameter(DECODER_FACTORY, "decoderFactory")
                    .addStatement("this.http = http")
                    .addStatement("this.decoderFactory = decoderFactory")
                    .build());

    for (int i = 0; i < methods.size(); i++) {
      builder.addMethod(buildFacadeMethod(methods.get(i), infos.get(i)));
    }
    return builder.build();
  }

  private MethodSpec buildFacadeMethod(ExecutableElement method, ReturnInfo info) {
    String name = method.getSimpleName().toString();
    MethodSpec.Builder facadeMethod =
        MethodSpec.methodBuilder(name)
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.get(method.getReturnType()));
    for (var typeParam : method.getTypeParameters()) {
      facadeMethod.addTypeVariable(TypeVariableName.get(typeParam));
    }
    String args =
        method.getParameters().stream()
            .map(p -> p.getSimpleName().toString())
            .collect(Collectors.joining(", "));
    for (VariableElement parameter : method.getParameters()) {
      facadeMethod.addParameter(
          ParameterSpec.builder(
                  TypeName.get(parameter.asType()), parameter.getSimpleName().toString())
              .build());
    }

    if (info.kind() == PathKind.MAYBE && method.getAnnotationsByType(OnStatus.class).length > 0) {
      warn("@OnStatus has no effect on a MaybePath method (it has no error channel).", method);
    }

    CodeBlock call = CodeBlock.of("() -> this.http.$L($L)", name, args);
    facadeMethod.addStatement(
        switch (info.kind()) {
          case EITHER ->
              CodeBlock.of(
                  "return $T.either($L, $L)",
                  HKJ_CLIENT_EXCHANGE,
                  call,
                  decoderExpression(method, info));
          case EITHER_VTASK ->
              CodeBlock.of(
                  "return $T.eitherVTask($L, $L)",
                  HKJ_CLIENT_EXCHANGE,
                  call,
                  decoderExpression(method, info));
          case MAYBE -> CodeBlock.of("return $T.maybe($L)", HKJ_CLIENT_EXCHANGE, call);
        });
    return facadeMethod.build();
  }

  /**
   * Builds the {@link ResponseErrorDecoder} expression for a method: {@code
   * decoderFactory.create(E.class)} by default, or a {@code ResponseErrorDecoders} status-dispatch
   * chain when the method carries {@link org.higherkindedj.spring.client.OnStatus} overrides.
   */
  private CodeBlock decoderExpression(ExecutableElement method, ReturnInfo info) {
    ClassName errorRaw = rawClass(info.error());
    List<StatusOverride> overrides = readStatusOverrides(method, info.error());
    if (overrides.isEmpty()) {
      return CodeBlock.of("this.decoderFactory.create($T.class)", errorRaw);
    }
    CodeBlock.Builder builder =
        CodeBlock.builder()
            .add(
                "$T.<$T>forDefault(this.decoderFactory, $T.class)",
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
      ExecutableElement method, TypeMirror declaredError) {
    List<StatusOverride> result = new ArrayList<>();
    Set<Integer> seen = new HashSet<>();
    for (OnStatus annotation : method.getAnnotationsByType(OnStatus.class)) {
      TypeMirror errorType = onStatusErrorType(annotation);
      if (!isConcreteClass(errorType)) {
        error(
            "@OnStatus error type must be a concrete, non-generic class (so the decoder can bind it "
                + "with E.class); found: "
                + errorType,
            method);
        continue;
      }
      if (!processingEnv.getTypeUtils().isSubtype(errorType, declaredError)) {
        error(
            "@OnStatus error type "
                + errorType
                + " is not assignable to the method's declared error type "
                + declaredError
                + ".",
            method);
        continue;
      }
      if (!seen.add(annotation.value())) {
        warn(
            "Duplicate @OnStatus for status " + annotation.value() + "; the last one wins.",
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
        .addJavadoc(
            "Registers the {@link $T} HTTP Service group and the client bean. Generated; do not edit.\n",
            nativeName)
        .addAnnotation(CONFIGURATION)
        .addAnnotation(
            AnnotationSpec.builder(IMPORT_HTTP_SERVICES)
                .addMember("group", "$S", group)
                .addMember("types", "$T.class", nativeName)
                .build())
        .addMethod(bean)
        .build();
  }

  // ---- analysis helpers ---------------------------------------------------------------------

  /** Copies a parameter's type, name and binding annotations onto the native interface method. */
  private static ParameterSpec copyParameter(VariableElement parameter) {
    ParameterSpec.Builder builder =
        ParameterSpec.builder(
            TypeName.get(parameter.asType()), parameter.getSimpleName().toString());
    for (AnnotationMirror mirror : parameter.getAnnotationMirrors()) {
      builder.addAnnotation(AnnotationSpec.get(mirror));
    }
    return builder.build();
  }

  /**
   * The abstract methods the generated client must implement — including those inherited from
   * super-interfaces, so a client interface that {@code extends} a shared base still generates a
   * complete native interface and facade. {@code default}/{@code static}/{@link Object} methods are
   * excluded, and methods are deduplicated by erased signature: {@code getAllMembers} does NOT
   * collapse the same method declared independently in two unrelated super-interfaces (a diamond),
   * which would otherwise emit a duplicate method into the native interface and facade.
   */
  private List<ExecutableElement> abstractMethods(TypeElement iface) {
    List<ExecutableElement> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (Element member : processingEnv.getElementUtils().getAllMembers(iface)) {
      if (member.getKind() == ElementKind.METHOD
          && member.getModifiers().contains(Modifier.ABSTRACT)
          && !isObjectMethod(member)) {
        ExecutableElement method = (ExecutableElement) member;
        if (seen.add(erasedSignature(method))) {
          result.add(method);
        }
      }
    }
    // getAllMembers has no guaranteed order across compilers/runs; sort by a stable key so the
    // generated sources are deterministic (reproducible builds, no spurious diffs / cache misses).
    result.sort(Comparator.comparing(this::erasedSignature));
    return result;
  }

  /**
   * A name + erased-parameter-types key, so diamond-inherited duplicates collapse to one method.
   */
  private String erasedSignature(ExecutableElement method) {
    StringBuilder key = new StringBuilder(method.getSimpleName().toString()).append('(');
    for (VariableElement parameter : method.getParameters()) {
      key.append(processingEnv.getTypeUtils().erasure(parameter.asType())).append(',');
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

  private @Nullable ReturnInfo analyseReturnType(ExecutableElement method) {
    TypeMirror returnType = method.getReturnType();
    if (!(returnType instanceof DeclaredType declared)) {
      return unsupported(method);
    }
    String qualified = qualifiedName(declared);
    List<? extends TypeMirror> args = declared.getTypeArguments();
    switch (qualified) {
      case EITHER_PATH:
        if (args.size() == 2) {
          return eitherInfo(PathKind.EITHER, method, args.get(0), args.get(1));
        }
        break;
      case MAYBE_PATH:
        if (args.size() == 1) {
          return new ReturnInfo(PathKind.MAYBE, null, args.get(0));
        }
        break;
      case VTASK_PATH:
        if (args.size() == 1 && args.get(0) instanceof DeclaredType inner) {
          if (qualifiedName(inner).equals(EITHER) && inner.getTypeArguments().size() == 2) {
            return eitherInfo(
                PathKind.EITHER_VTASK,
                method,
                inner.getTypeArguments().get(0),
                inner.getTypeArguments().get(1));
          }
        }
        break;
      case VSTREAM_PATH:
        error(
            "VStreamPath is not generated automatically. Consume the server's SSE stream directly "
                + "with HkjClientExchange.vstream(source, ElementType.class, jsonMapper).",
            method);
        return null;
      default:
        break;
    }
    return unsupported(method);
  }

  /**
   * Builds an Either-flavoured {@link ReturnInfo}, rejecting an error type that is itself a type
   * variable — the decoder needs a concrete {@code E.class}, which a type variable cannot provide.
   */
  private @Nullable ReturnInfo eitherInfo(
      PathKind kind, ExecutableElement method, TypeMirror error, TypeMirror success) {
    if (error.getKind() == TypeKind.TYPEVAR) {
      error(
          "@HkjHttpClient error type cannot be a type variable; it must be a concrete class so the "
              + "decoder can bind it.",
          method);
      return null;
    }
    // The decoder binds the error via E.class, so the error type must be a concrete, non-generic
    // class — not an array, wildcard, or parameterized type (which would crash rawClass or emit an
    // uncompilable create(Raw.class) call).
    if (!isConcreteClass(error)) {
      error(
          "@HkjHttpClient error type must be a concrete, non-generic class (so the decoder can bind "
              + "it with E.class); found: "
              + error,
          method);
      return null;
    }
    return new ReturnInfo(kind, error, success);
  }

  /** A concrete, non-generic class/interface — bindable as {@code E.class} by the decoder. */
  private static boolean isConcreteClass(TypeMirror type) {
    return type instanceof DeclaredType declared && declared.getTypeArguments().isEmpty();
  }

  private @Nullable ReturnInfo unsupported(ExecutableElement method) {
    error(
        "Unsupported @HkjHttpClient return type. Expected EitherPath<E, T>, "
            + "VTaskPath<Either<E, T>>, or MaybePath<T>.",
        method);
    return null;
  }

  private static String qualifiedName(DeclaredType type) {
    return ((TypeElement) type.asElement()).getQualifiedName().toString();
  }

  /**
   * The raw {@link ClassName} of an error type, for use in {@code decoderFactory.create(E.class)}.
   */
  private static ClassName rawClass(TypeMirror error) {
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

  private void writeFile(String packageName, TypeSpec type, Element originating) {
    try {
      JavaFile.builder(packageName, type)
          .addFileComment("Generated by hkj-spring-client-processor. Do not edit.")
          .build()
          .writeTo(processingEnv.getFiler());
    } catch (IOException e) {
      error("Could not write generated client: " + e.getMessage(), originating);
    }
  }

  private void error(String message, Element element) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
  }

  private void warn(String message, Element element) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, message, element);
  }

  private void note(String message, Element element) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, message, element);
  }
}
