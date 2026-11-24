// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client.processor;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import com.palantir.javapoet.TypeVariableName;
import com.palantir.javapoet.WildcardTypeName;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
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
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
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
 *       methods, the return type unwrapped to {@code ResponseEntity<T>} and the mapping annotations
 *       copied through, wherever the client's package can write them. This is the piece Spring's
 *       {@code HttpServiceProxyFactory} proxies.
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

  /** How a type javac could not resolve is reported, as the processor's other diagnostics say. */
  private static final String NOT_ON_CLASSPATH =
      " is not on this compilation's classpath; add the dependency that declares it";

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

  /** The two aliases a name-binding annotation's name is written in. */
  private static final Set<String> BINDING_NAME_MEMBERS = Set.of("value", "name");

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

  /**
   * A client waiting for a later round, by its module's name and its own, since an element is a
   * round's own and is looked up again in the next, and two modules compiled together may declare
   * the same name. The unnamed module's name is empty, as is any module's where the compilation has
   * none. hkj-processor's {@code TypeKey} keys its waiting specs by the same two parts; this
   * processor does not depend on that module.
   */
  private record Waiting(String module, String name) {}

  /** The clients waiting for a later round, in the order they were met. */
  private final Set<Waiting> waiting = new LinkedHashSet<>();

  /** Creates a new processor. */
  public HkjHttpClientProcessor() {}

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  /**
   * Processes each client this round can: one whose annotations, interfaces or signatures name a
   * type javac could not resolve waits for the round that brings it (see {@link #awaited}). Nothing
   * is written in the last round. There a client still waiting is reported with what never
   * resolved, unless an error ended the rounds early: javac makes the round after one with an error
   * the last, and then the client is not generated for that error, which javac reports.
   * hkj-processor's mapping specs stay silent in the last round instead; this processor reports, as
   * it takes an element it cannot place as source (see {@link ClassFiles#readFrom}).
   */
  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    Elements elements = processingEnv.getElementUtils();
    TypeElement marker = elements.getTypeElement(HKJ_HTTP_CLIENT);
    if (marker == null) {
      return false;
    }
    List<TypeElement> waited = new ArrayList<>();
    for (Waiting client : waiting) {
      lookUp(client).ifPresent(waited::add);
    }
    waiting.clear();
    if (roundEnv.processingOver()) {
      if (!roundEnv.errorRaised()) {
        waited.forEach(this::reportStillWaiting);
      }
      return true;
    }
    List<TypeElement> clients =
        Stream.concat(
                waited.stream(),
                ElementFilter.typesIn(roundEnv.getElementsAnnotatedWith(marker)).stream())
            .distinct()
            .toList();
    for (TypeElement type : clients) {
      if (type.getKind() != ElementKind.INTERFACE) {
        error("@HkjHttpClient can only be applied to interfaces.", type);
        continue;
      }
      try {
        if (!generateClient(type)) {
          waiting.add(
              new Waiting(
                  Optional.ofNullable(elements.getModuleOf(type))
                      .map(module -> module.getQualifiedName().toString())
                      .orElse(""),
                  type.getQualifiedName().toString()));
        }
      } catch (RuntimeException e) {
        // Never abort the whole round with an internal stack trace: report and move on.
        error("Failed to generate @HkjHttpClient client: " + e, type);
      }
    }
    return true;
  }

  /** A waiting client, looked up again in this round, or reported where it cannot be. */
  private Optional<TypeElement> lookUp(Waiting client) {
    Elements elements = processingEnv.getElementUtils();
    ModuleElement module = elements.getModuleElement(client.module());
    Optional<TypeElement> found =
        Optional.ofNullable(
            module == null
                ? elements.getTypeElement(client.name())
                : elements.getTypeElement(module, client.name()));
    if (found.isEmpty()) {
      processingEnv
          .getMessager()
          .printMessage(
              Diagnostic.Kind.ERROR,
              "@HkjHttpClient could not find "
                  + client.name()
                  + " again after it waited a round for a type it names, so it generated nothing"
                  + " for it.");
    }
    return found;
  }

  /**
   * Reports, in the last round, a client still waiting, with what it waits for. javac reports a
   * name its source wrote and could not resolve as well; this says why the client has none of its
   * generated types.
   */
  private void reportStillWaiting(TypeElement iface) {
    try {
      String packageName =
          processingEnv.getElementUtils().getPackageOf(iface).getQualifiedName().toString();
      List<String> awaited = awaited(iface, inheritedBy(iface), declarationsOf(iface), packageName);
      if (awaited.isEmpty()) {
        return;
      }
      error(
          "@HkjHttpClient generated nothing for "
              + iface.getSimpleName()
              + ", as no round of this compilation resolved what it names: "
              + String.join(", ", awaited)
              + ". Correct each name or make it accessible, or add the dependency or the processor"
              + " that writes it.",
          iface);
    } catch (RuntimeException e) {
      // Never abort the last round with an internal stack trace: report and move on.
      error("Failed to report a waiting @HkjHttpClient client: " + e, iface);
    }
  }

  /** Every declaration of each method a client implements, in the order the groups are read. */
  private List<ClientMethod> declarationsOf(TypeElement iface) {
    return ClientMethods.inheritedBy(
            iface, processingEnv.getElementUtils(), processingEnv.getTypeUtils())
        .stream()
        .flatMap(List::stream)
        .toList();
  }

  /** The type-level {@code @HttpExchange} a client inherits, as {@link HttpExchanges} finds it. */
  private HttpExchanges.Inherited inheritedBy(TypeElement iface) {
    return HttpExchanges.inheritedBy(
        iface, (writer, missing) -> !classFiles().keepsForTheCompilerAlone(writer, missing));
  }

  /**
   * Generates a client, or reports why it cannot be, and returns whether this round settled it:
   * false where it waits for another round.
   */
  private boolean generateClient(TypeElement iface) {
    String packageName =
        processingEnv.getElementUtils().getPackageOf(iface).getQualifiedName().toString();
    List<ReturnInfo> infos = new ArrayList<>();
    HttpExchanges.Inherited inherited = inheritedBy(iface);
    List<List<ClientMethod>> groups =
        ClientMethods.inheritedBy(
            iface, processingEnv.getElementUtils(), processingEnv.getTypeUtils());
    List<ClientMethod> methods = groups.stream().map(List::getFirst).toList();
    if (!awaited(iface, inherited, groups.stream().flatMap(List::stream).toList(), packageName)
        .isEmpty()) {
      return false;
    }
    boolean valid = !refusesTypeLevelAnnotations(iface, inherited, packageName);
    for (List<ClientMethod> declarations : groups) {
      boolean namesLost = false;
      for (ClientMethod declaration : declarations) {
        namesLost |= refusesLostBindingNames(iface, declaration);
      }
      // A lost name makes two declarations differ; its own refusal says why.
      if (namesLost || refusesDivergentDeclarations(iface, declarations, packageName)) {
        valid = false;
      }
    }
    for (ClientMethod method : methods) {
      if (refusesUnwritableAnnotations(iface, method, packageName)) {
        valid = false;
      }
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
      return true;
    }
    warnOfIneffectiveOverrides(iface, groups, infos);

    String simpleName = iface.getSimpleName().toString();
    List<TypeVariableName> typeVars =
        iface.getTypeParameters().stream()
            .map(
                parameter ->
                    TypeNames.typeVariableOf(
                        parameter, packageName, processingEnv.getElementUtils()))
            .toList();
    boolean generic = !typeVars.isEmpty();

    ClassName nativeName = ClassName.get(packageName, nativeNameOf(iface));
    ClassName facadeName = ClassName.get(packageName, simpleName + "Client");
    TypeName nativeType = parameterise(nativeName, typeVars);
    List<ClassName> written =
        generic
            ? List.of(nativeName, facadeName)
            : List.of(
                nativeName,
                facadeName,
                ClassName.get(packageName, simpleName + "ClientConfiguration"));
    if (refusesReplacing(iface, written)) {
      return true;
    }

    List<AnnotationMirror> inheritedExchange =
        inherited instanceof HttpExchanges.Found found ? found.annotations() : List.of();
    writeFile(
        packageName,
        buildNativeInterface(iface, nativeName, typeVars, methods, infos, inheritedExchange),
        iface);
    writeFile(
        packageName,
        buildFacade(iface, nativeName, nativeType, facadeName, typeVars, groups, infos),
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
          packageName, buildConfiguration(iface, nativeName, facadeName, simpleName, group), iface);
    }
    return true;
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
      List<ReturnInfo> infos,
      List<AnnotationMirror> inheritedExchange) {
    String packageName = nativeName.packageName();
    TypeSpec.Builder builder =
        TypeSpec.interfaceBuilder(nativeName)
            .addOriginatingElement(iface)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(GENERATED)
            .addTypeVariables(typeVars)
            .addJavadoc(
                "Native Spring {@link $T}-style interface proxied for {@link $T}. Generated; do not edit.\n",
                IMPORT_HTTP_SERVICES,
                ClassName.get(iface));

    // Spring proxies the native interface, which extends nothing, so a client with no type-level
    // @HttpExchange of its own is given those Spring would have read from its bases.
    Elements elements = processingEnv.getElementUtils();
    List<AnnotationMirror> typeLevel =
        Stream.concat(copiedAnnotationsOf(iface).stream(), inheritedExchange.stream()).toList();
    builder.addAnnotations(
        AnnotationSpecs.copiesOf(typeLevel, packageName, elements, drawnByTypeParameters(iface)));

    for (int i = 0; i < methods.size(); i++) {
      ClientMethod clientMethod = methods.get(i);
      ExecutableElement method = clientMethod.element();
      ReturnInfo info = infos.get(i);
      TypeName successType =
          info.ignoresBody()
              ? ClassName.get(Void.class)
              : info.successNameIn(clientMethod.returnTypeName(packageName, elements));
      MethodSpec.Builder nativeMethod =
          MethodSpec.methodBuilder(method.getSimpleName().toString())
              .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
              .returns(ParameterizedTypeName.get(RESPONSE_ENTITY, successType))
              .addTypeVariables(clientMethod.typeVariableNames(packageName, elements));
      // The native method writes the success type alone of the return type, and overrides
      // nothing, as the native interface extends nothing.
      Stream<TypeMirror> written =
          Stream.concat(
              info.ignoresBody() ? Stream.empty() : Stream.of(info.success()),
              clientMethod.parameterTypesAndBounds().stream());
      nativeMethod.addAnnotations(
          AnnotationSpecs.copiesOf(
              copiedAnnotationsOf(method),
              packageName,
              elements,
              Deprecations.namedBy(written, elements)));
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
      List<List<ClientMethod>> groups,
      List<ReturnInfo> infos) {
    String packageName = facadeName.packageName();
    Elements elements = processingEnv.getElementUtils();
    List<ClientMethod> methods = groups.stream().map(List::getFirst).toList();
    // The facade implements the client and redeclares its type parameters, bounds and all, and a
    // deprecated client's native interface carries the copied @Deprecated it names in a field.
    Set<Lint> classDrawn =
        Stream.of(drawnByClient(iface), drawnByTypeParameters(iface))
            .flatMap(Set::stream)
            .collect(Lint.inNameOrder());
    TypeSpec.Builder builder =
        TypeSpec.classBuilder(facadeName)
            .addOriginatingElement(iface)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(GENERATED)
            .addAnnotations(AnnotationSpecs.suppressing(classDrawn, List.of()))
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
    // Each decoder names its error classes, in its field's type and in the constructor that
    // builds it.
    Set<Lint> constructorDrawn = EnumSet.noneOf(Lint.class);
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
      TypeMirror error = Objects.requireNonNull(info.error());
      List<StatusOverride> overrides = readStatusOverrides(iface, method, error);
      builder.addField(
          FieldSpec.builder(
                  ParameterizedTypeName.get(RESPONSE_ERROR_DECODER, rawClass(error)),
                  fieldName,
                  Modifier.PRIVATE,
                  Modifier.FINAL)
              .addAnnotations(
                  AnnotationSpecs.suppressing(drawnByClassLiterals(Stream.of(error)), List.of()))
              .build());
      constructorDrawn.addAll(
          drawnByClassLiterals(
              Stream.concat(Stream.of(error), overrides.stream().map(StatusOverride::errorType))));
      constructor.addStatement("this.$L = $L", fieldName, decoderExpression(error, overrides));
      decoderFields.add(fieldName);
    }

    builder.addMethod(
        constructor
            .addAnnotations(AnnotationSpecs.suppressing(constructorDrawn, List.of()))
            .build());

    for (int i = 0; i < methods.size(); i++) {
      builder.addMethod(
          buildFacadeMethod(iface, groups.get(i), infos.get(i), decoderFields.get(i), packageName));
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

  /**
   * The facade's implementation of one method. It overrides each of the method's declarations, and
   * each method one of them overrides in turn, and calls the native method, which carries the first
   * declaration's annotations, {@code @Deprecated} among them; it writes the client's return type,
   * parameter types and type parameters. A deprecated one among them is suppressed on it.
   */
  private MethodSpec buildFacadeMethod(
      TypeElement iface,
      List<ClientMethod> declarations,
      ReturnInfo info,
      @Nullable String decoderField,
      String packageName) {
    Elements elements = processingEnv.getElementUtils();
    ClientMethod clientMethod = declarations.getFirst();
    ExecutableElement method = clientMethod.element();
    String name = method.getSimpleName().toString();
    Set<Lint> drawn =
        Stream.of(
                Deprecations.drawnBy(
                    ClientMethods.overriddenBy(iface, declarations, elements), elements),
                Deprecations.namedBy(clientMethod.written().stream(), elements))
            .flatMap(Set::stream)
            .collect(Lint.inNameOrder());
    MethodSpec.Builder facadeMethod =
        MethodSpec.methodBuilder(name)
            .addAnnotations(
                AnnotationSpecs.suppressing(
                    drawn, List.of(AnnotationSpec.builder(Override.class).build())))
            .addModifiers(Modifier.PUBLIC)
            .returns(clientMethod.returnTypeName(packageName, elements))
            .addTypeVariables(clientMethod.typeVariableNames(packageName, elements));
    List<ClientMethod.Parameter> parameters = clientMethod.parameters();
    String args =
        parameters.stream().map(ClientMethod.Parameter::name).collect(Collectors.joining(", "));
    for (ClientMethod.Parameter parameter : parameters) {
      facadeMethod.addParameter(
          ParameterSpec.builder(parameter.typeName(packageName, elements), parameter.name())
              .build());
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
  private static CodeBlock decoderExpression(TypeMirror error, List<StatusOverride> overrides) {
    ClassName errorRaw = rawClass(error);
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

  /**
   * Reads valid {@link org.higherkindedj.spring.client.OnStatus} overrides off a method, reporting
   * each that is not, so it is read once per method.
   */
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
    return List.copyOf(result);
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
      TypeElement iface,
      ClassName nativeName,
      ClassName facadeName,
      String simpleName,
      String group) {
    ClassName ifaceName = ClassName.get(iface);
    MethodSpec bean =
        MethodSpec.methodBuilder(decapitalise(simpleName) + "Client")
            .addAnnotation(BEAN)
            .addModifiers(Modifier.PUBLIC)
            .returns(ifaceName)
            .addParameter(nativeName, "http")
            .addParameter(DECODER_FACTORY, "decoderFactory")
            .addStatement("return new $T(http, decoderFactory)", facadeName)
            .build();

    // The configuration names the client, and the native interface, which carries a deprecated
    // client's copied @Deprecated, in its annotations as well as its bean method.
    return TypeSpec.classBuilder(simpleName + "ClientConfiguration")
        .addOriginatingElement(iface)
        .addModifiers(Modifier.PUBLIC)
        .addAnnotation(GENERATED)
        .addAnnotations(AnnotationSpecs.suppressing(drawnByClient(iface), List.of()))
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
   * consuming build does not compile with {@code -parameters}. An empty name it was written with
   * gives way to the injected one, so the copy carries one name.
   */
  private ParameterSpec copyParameter(ClientMethod.Parameter parameter, String packageName) {
    String parameterName = parameter.name();
    return ParameterSpec.builder(
            parameter.typeName(packageName, processingEnv.getElementUtils()), parameterName)
        .addAnnotations(
            AnnotationSpecs.copiesOf(
                parameter.element().getAnnotationMirrors(),
                packageName,
                processingEnv.getElementUtils(),
                mirror ->
                    needsExplicitName(mirror, parameter)
                        ? named(AnnotationSpecs.copyOf(mirror), parameterName)
                        : AnnotationSpecs.copyOf(mirror)))
        .build();
  }

  /**
   * A copied name-binding annotation with {@code name} as its {@code value}, in place of the empty
   * {@code value} or {@code name} it may have been written with: javapoet would write a second
   * {@code value} beside an empty one as an array, which does not compile.
   */
  private static AnnotationSpec named(AnnotationSpec copy, String name) {
    AnnotationSpec.Builder named = AnnotationSpec.builder((ClassName) copy.type());
    copy.members()
        .forEach(
            (member, blocks) -> {
              if (!BINDING_NAME_MEMBERS.contains(member)) {
                blocks.forEach(block -> named.addMember(member, block));
              }
            });
    return named.addMember("value", "$S", name).build();
  }

  /**
   * Refuses each name the client's generated classes would take that a class no HKJ generator wrote
   * already has, read from a class file, and returns whether it refused any. javac's Filer refuses
   * a name a source of this compilation declares, but writes over a class file with no more than a
   * warning, and Gradle reads a hand-written class it did not recompile with the client from its
   * class file.
   */
  private boolean refusesReplacing(TypeElement iface, List<ClassName> written) {
    List<String> taken =
        written.stream()
            .map(ClassName::canonicalName)
            .filter(classFiles()::replacesUngeneratedClass)
            .toList();
    for (String name : taken) {
      // Worded as hkj-processor's GeneratedNames.refusesReplacing words it, so that one quoted
      // fragment finds both in the book.
      error(
          "@HkjHttpClient: the generated class '"
              + name
              + "' has the name of a class on the classpath. javac reads that class from a class"
              + " file without HKJ's @Generated, so it is a dependency's, another processor's"
              + " output from an earlier build, or a hand-written class Gradle's incremental build"
              + " did not recompile, and the generated class would replace it. Rename the class on"
              + " the classpath where it is yours, or rename '"
              + iface.getSimpleName()
              + "', whose generated classes take their names from it, or rebuild from clean where"
              + " it is an earlier build's output.",
          iface);
    }
    return !taken.isEmpty();
  }

  /**
   * Refuses a method whose binding names would come from names its class file did not keep, and
   * returns whether it refused. A name-binding annotation written without a name takes the
   * parameter's (see {@link #copyParameter}), and in a class file compiled without {@code
   * -parameters} javac reads only {@code arg0}, {@code arg1}, which Spring would then bind. A
   * method is read from a class file where its interface is in a dependency, and, in Gradle's
   * incremental build, where a base interface of this compilation has not changed since the last
   * build.
   */
  private boolean refusesLostBindingNames(TypeElement iface, ClientMethod method) {
    if (classFiles().keepsParameterNames(method.element())) {
      return false;
    }
    List<String> unnamed =
        method.parameters().stream()
            .filter(
                parameter ->
                    parameter.element().getAnnotationMirrors().stream()
                        .anyMatch(mirror -> needsExplicitName(mirror, parameter)))
            .map(parameter -> "'" + parameter.name() + "'")
            .toList();
    if (unnamed.isEmpty()) {
      return false;
    }
    String base = method.declaring().getSimpleName().toString();
    error(
        "@HkjHttpClient cannot name the binding of "
            + (unnamed.size() == 1 ? "parameter " : "parameters ")
            + String.join(", ", unnamed)
            + ": "
            + base
            + " is read from a class file that records no parameter names, as one compiled without"
            + " -parameters does, whether it is a jar's or a base Gradle's incremental build did not"
            + " recompile. javac reads the names as 'arg0', 'arg1', and Spring would bind those."
            + " Compile "
            + base
            + " with -parameters, write each binding's name, as in @PathVariable(\"id\"), or"
            + " redeclare '"
            + method.element().getSimpleName()
            + "' on the client.",
        iface,
        method.element());
    return true;
  }

  /**
   * The annotations a method's native counterpart is given: the mapping annotations
   * ({@code @GetExchange}, {@code @PostExchange}, version attributes, …) as written, but not the
   * HKJ client meta-annotations ({@code @OnStatus}/{@code @OnStatuses}) nor {@code @Override}. The
   * generated native interface extends nothing, so a copied {@code @Override} would not compile.
   */
  private static List<? extends AnnotationMirror> copiedAnnotationsOf(ExecutableElement method) {
    return method.getAnnotationMirrors().stream()
        .filter(mirror -> !isClientMetaAnnotation(mirror) && !isOverride(mirror))
        .toList();
  }

  /**
   * The client's own type-level annotations its native interface is given: all but the HKJ client
   * meta-annotations.
   */
  private static List<? extends AnnotationMirror> copiedAnnotationsOf(TypeElement iface) {
    return iface.getAnnotationMirrors().stream()
        .filter(mirror -> !isClientMetaAnnotation(mirror))
        .toList();
  }

  /**
   * Reports each type-level annotation the native interface cannot carry, and returns whether it
   * reported any. One the client writes may name a type the generated interfaces cannot, such as
   * one nested privately where the client is. One it inherits may be written through an annotation
   * its package cannot write. Or the processor cannot see which it inherits, since a base carries
   * an annotation javac could not resolve, through which Spring may read one. The generated client
   * would then not compile, or would call another URL than Spring would, so the author says which
   * it is.
   */
  private boolean refusesTypeLevelAnnotations(
      TypeElement iface, HttpExchanges.Inherited inherited, String packageName) {
    String client = iface.getSimpleName().toString();
    String declare = "declare @HttpExchange on " + client + " as the client should have it.";
    Stream<String> inheritedRefusals =
        switch (inherited) {
          case HttpExchanges.None _ -> Stream.empty();
          case HttpExchanges.Found(TypeElement base, List<AnnotationMirror> annotations) ->
              refusalsOf(
                  iface,
                  annotations,
                  annotation ->
                      nameOf(annotation)
                          + ", the type-level @HttpExchange "
                          + client
                          + " inherits from "
                          + base.getSimpleName()
                          + ",",
                  base,
                  packageName,
                  _ -> declare);
          // In source, javac reports the name it cannot resolve.
          case HttpExchanges.Unreadable(
                  TypeElement base,
                  AnnotationMirror annotation,
                  DeclaredType missing,
                  TypeElement writer) ->
              classFiles().readFrom(writer)
                  ? Stream.of(unreadableRefusal(iface, base, annotation, missing, writer, declare))
                  : Stream.empty();
        };
    List<String> refusals =
        Stream.concat(
                refusalsOf(
                    iface,
                    copiedAnnotationsOf(iface),
                    HkjHttpClientProcessor::nameOf,
                    iface,
                    packageName,
                    HkjHttpClientProcessor::ownRemedy),
                inheritedRefusals)
            .toList();
    refusals.forEach(refusal -> error(refusal, iface));
    return !refusals.isEmpty();
  }

  /**
   * The refusal of a type-level {@code @HttpExchange} the processor cannot see, as {@code base}
   * carries an annotation leading to a type javac could not resolve.
   */
  private static String unreadableRefusal(
      TypeElement iface,
      TypeElement base,
      AnnotationMirror annotation,
      DeclaredType missing,
      TypeElement writer,
      String remedy) {
    // The base names the missing type itself exactly when its own annotation is the one missing.
    String carried =
        writer.equals(base)
            ? nameOf(annotation) + ", which"
            : nameOf(annotation) + ", composed from @" + missing + ", which";
    return "@HkjHttpClient cannot tell whether "
        + iface.getSimpleName()
        + " inherits a type-level @HttpExchange from "
        + base.getSimpleName()
        + ": "
        + base.getSimpleName()
        + " carries "
        + carried
        + NOT_ON_CLASSPATH
        + ", or "
        + remedy;
  }

  /**
   * Reports each annotation a method carries, on itself or on a parameter, that its native
   * counterpart cannot carry, and returns whether it reported any. Spring reads each one retained
   * at run time on the request, so none of those can be left off. A method the client declares is
   * refused only for a type the generated interfaces cannot name, such as one nested privately
   * where the client is: javac reports any other name the client's own source cannot reach.
   */
  private boolean refusesUnwritableAnnotations(
      TypeElement iface, ClientMethod method, String packageName) {
    ExecutableElement element = method.element();
    String redeclare =
        "redeclare '"
            + element.getSimpleName()
            + "' on "
            + iface.getSimpleName()
            + " as the client should have it.";
    Function<AnnotationSpecs.Unwritable, String> remedy =
        method.declaring().equals(iface) ? HkjHttpClientProcessor::ownRemedy : _ -> redeclare;
    List<String> refusals =
        Stream.concat(
                refusalsOf(
                    iface,
                    copiedAnnotationsOf(element),
                    HkjHttpClientProcessor::nameOf,
                    element,
                    packageName,
                    remedy),
                method.parameters().stream()
                    .flatMap(
                        parameter ->
                            refusalsOf(
                                iface,
                                parameter.element().getAnnotationMirrors(),
                                annotation ->
                                    nameOf(annotation) + " on parameter '" + parameter.name() + "'",
                                element,
                                packageName,
                                remedy)))
            .toList();
    refusals.forEach(refusal -> error(refusal, iface, element));
    return !refusals.isEmpty();
  }

  /**
   * The remedy for an annotation the client writes itself that its package cannot name: one nested
   * privately where the client is needs package access, and one in another package, such as a
   * protected one a class the client is nested in inherits, needs to be public.
   */
  private static String ownRemedy(AnnotationSpecs.Unwritable obstacle) {
    return switch (obstacle) {
      case AnnotationSpecs.NotPublic(String _, boolean inPackage) ->
          inPackage ? "make it package-private or public." : "make it public.";
      // The client is source, where javac reports a name it cannot resolve, so none of these is
      // ever reported on the client's own annotation; obstacleTo leaves an Unresolved one out
      // before it asks for a remedy.
      case AnnotationSpecs.Missing _, AnnotationSpecs.Unresolved _, AnnotationSpecs.Skewed _ ->
          "add the dependency it names.";
    };
  }

  /**
   * The refusal of each of {@code annotations}, which {@code writer} carries, that the client's
   * package cannot write and Spring could read, each labelled as {@code label} names it.
   */
  private Stream<String> refusalsOf(
      TypeElement iface,
      List<? extends AnnotationMirror> annotations,
      Function<AnnotationMirror, String> label,
      Element writer,
      String packageName,
      Function<AnnotationSpecs.Unwritable, String> remedy) {
    return annotations.stream()
        .flatMap(
            annotation ->
                obstacleTo(iface, annotation, writer, packageName, remedy)
                    .map(
                        obstacle ->
                            "@HkjHttpClient cannot copy "
                                + label.apply(annotation)
                                + " onto "
                                + nativeNameOf(iface)
                                + ", the interface Spring proxies: "
                                + obstacle)
                    .stream());
  }

  /**
   * Why the client's package cannot write an annotation that {@code writer} carries, ending with
   * the remedy, or nothing where it can, or where it need not: one Spring never reads, kept for the
   * compiler alone, is left out of the copy instead, which changes nothing Spring sends. A type
   * missing from the classpath, or another version of one, is the processor's to report only where
   * a class file names it: in source, javac reports a name it cannot resolve.
   */
  private Optional<String> obstacleTo(
      TypeElement iface,
      AnnotationMirror annotation,
      Element writer,
      String packageName,
      Function<AnnotationSpecs.Unwritable, String> remedy) {
    if (!seenBySpring(annotation, writer)) {
      return Optional.empty();
    }
    boolean fromClassFile = classFiles().readFrom(writer);
    return switch (AnnotationSpecs.writabilityIn(
        annotation, packageName, processingEnv.getElementUtils())) {
      case AnnotationSpecs.Writable _ -> Optional.empty();
      case AnnotationSpecs.NotPublic notPublic ->
          Optional.of(
              itOr(notPublic.named(), annotation)
                  + " is not public, so "
                  + nativeNameOf(iface)
                  + " cannot name it; "
                  + remedy.apply(notPublic));
      case AnnotationSpecs.Missing missing ->
          fromClassFile
              ? Optional.of(
                  itOr(missing.named(), annotation)
                      + NOT_ON_CLASSPATH
                      + ", or "
                      + remedy.apply(missing))
              : Optional.empty();
      // javac hands over this mark only in source, where it reports the name itself, and the
      // client waits for the round that may bring it.
      case AnnotationSpecs.Unresolved _ -> Optional.empty();
      case AnnotationSpecs.Skewed skewed ->
          fromClassFile
              ? Optional.of(
                  skewed.type()
                      + " on this compilation's classpath "
                      + skewed.differs()
                      + "; build against the version of it "
                      + declaringTypeOf(writer).getSimpleName()
                      + " was compiled with, or "
                      + remedy.apply(skewed))
              : Optional.empty();
    };
  }

  /**
   * Whether Spring could read an annotation {@code writer} carries: one whose type is retained at
   * run time, which Spring reads by reflection. For a type javac could not resolve, the writer's
   * class file says, as {@link ClassFiles#keepsForTheCompilerAlone} reads it.
   */
  private boolean seenBySpring(AnnotationMirror annotation, Element writer) {
    DeclaredType type = annotation.getAnnotationType();
    return type.getKind() == TypeKind.ERROR
        ? !classFiles().keepsForTheCompilerAlone(writer, type)
        : HttpExchanges.retainedAtRunTime((TypeElement) type.asElement());
  }

  /**
   * What a client waits for another round to bring, by name, or nothing where it is ready. A type
   * another processor writes in a later round may resolve what javac could not resolve this round:
   *
   * <ul>
   *   <li>a type, or another version of one, that an annotation names where the client or a base
   *       writes it in source. Read this round, a copied annotation would be left off, and an HKJ
   *       client meta-annotation, read in place of being copied, would give a wrong value: a group
   *       of {@code "<error>"}, or a status or an error class the processor cannot read;
   *   <li>an interface the client extends;
   *   <li>a type the client restates from source, which its generated sources would write by a name
   *       they cannot resolve, or a type-use annotation on one, which would be left off.
   * </ul>
   *
   * <p>Every declaration of each method counts, as the client may follow any of them. In the last
   * round javac reports a name its source wrote that is still unresolved, and nothing is written.
   */
  private List<String> awaited(
      TypeElement iface,
      HttpExchanges.Inherited inherited,
      List<ClientMethod> declarations,
      String packageName) {
    record Written(AnnotationMirror annotation, Element writer) {}
    // Every annotation the client and each declaration carry, the HKJ client meta-annotations
    // among them.
    Stream<Written> typeLevel =
        Stream.concat(
            iface.getAnnotationMirrors().stream().map(annotation -> new Written(annotation, iface)),
            inherited
                    instanceof
                    HttpExchanges.Found(TypeElement base, List<AnnotationMirror> annotations)
                ? annotations.stream().map(annotation -> new Written(annotation, base))
                : Stream.empty());
    Stream<Written> onMethods =
        declarations.stream()
            .flatMap(
                method ->
                    Stream.concat(
                        method.element().getAnnotationMirrors().stream()
                            .map(annotation -> new Written(annotation, method.element())),
                        method.parameters().stream()
                            .flatMap(
                                parameter -> parameter.element().getAnnotationMirrors().stream())
                            .map(annotation -> new Written(annotation, method.element()))));
    Stream<String> annotations =
        Stream.concat(typeLevel, onMethods)
            .filter(written -> !classFiles().readFrom(written.writer()))
            .flatMap(written -> awaitedBy(written.annotation(), packageName));
    Stream<String> unreadable =
        inherited instanceof HttpExchanges.Unreadable found
                && !classFiles().readFrom(found.writer())
            ? Stream.of(found.missing().toString())
            : Stream.empty();
    Stream<String> restated =
        Stream.of(
                iface.getTypeParameters().stream()
                    .flatMap(parameter -> parameter.getBounds().stream()),
                declarations.stream()
                    .filter(method -> !classFiles().readFrom(method.declaring()))
                    .flatMap(method -> restatedTypesOf(method.element())),
                // The clauses the client and each base in source write, whose type arguments the
                // client restates where it binds a base's type variables.
                sourceHierarchyOf(iface).stream().flatMap(type -> type.getInterfaces().stream()))
            .flatMap(types -> types)
            .flatMap(type -> unresolvedIn(type, packageName));
    return Stream.of(annotations, unreadable, unresolvedInterfacesOf(iface), restated)
        .flatMap(names -> names)
        .distinct()
        .toList();
  }

  /**
   * What an annotation written in source names that a later round could resolve: a type javac could
   * not resolve, or another version of one.
   */
  private Stream<String> awaitedBy(AnnotationMirror annotation, String packageName) {
    return switch (AnnotationSpecs.writabilityIn(
        annotation, packageName, processingEnv.getElementUtils())) {
      case AnnotationSpecs.Missing missing -> Stream.of(missing.named());
      case AnnotationSpecs.Unresolved unresolved ->
          Stream.of("a value of @" + unresolved.annotation() + "'s " + unresolved.member());
      case AnnotationSpecs.Skewed skewed -> Stream.of(skewed.type());
      case AnnotationSpecs.NotPublic _, AnnotationSpecs.Writable _ -> Stream.empty();
    };
  }

  /** The types a method declares that the generated client restates. */
  private static Stream<TypeMirror> restatedTypesOf(ExecutableElement method) {
    return Stream.of(
            Stream.of(method.getReturnType()),
            method.getParameters().stream().map(Element::asType),
            method.getTypeParameters().stream()
                .flatMap(parameter -> parameter.getBounds().stream()))
        .flatMap(types -> types);
  }

  /**
   * What a type, and each type it is made of, names that javac could not resolve: the type itself,
   * by the name javac gives it, without its type arguments, or what a type-use annotation on it
   * names, as its type or in a value. javac keeps the type arguments of a type it could not
   * resolve, so they are read too. A type variable's bounds are read where the variable is
   * declared.
   */
  private Stream<String> unresolvedIn(TypeMirror type, String packageName) {
    Stream<String> own =
        Stream.concat(
            type.getKind() == TypeKind.ERROR
                ? Stream.of(((DeclaredType) type).asElement().toString())
                : Stream.empty(),
            type.getAnnotationMirrors().stream()
                .flatMap(annotation -> awaitedBy(annotation, packageName)));
    Stream<TypeMirror> parts =
        switch (type.getKind()) {
          case DECLARED, ERROR ->
              Stream.concat(
                  Stream.of(((DeclaredType) type).getEnclosingType())
                      .filter(enclosing -> enclosing.getKind() == TypeKind.DECLARED),
                  ((DeclaredType) type).getTypeArguments().stream().map(TypeMirror.class::cast));
          case ARRAY -> Stream.of(((ArrayType) type).getComponentType());
          case WILDCARD ->
              Stream.of(
                      ((WildcardType) type).getExtendsBound(),
                      ((WildcardType) type).getSuperBound())
                  .filter(Objects::nonNull);
          default -> Stream.empty();
        };
    return Stream.concat(own, parts.flatMap(part -> unresolvedIn(part, packageName)));
  }

  /**
   * The interfaces in a type's hierarchy that javac could not resolve, each by the name javac gives
   * it, without its type arguments, read level by level in the order each clause names them.
   */
  private static Stream<String> unresolvedInterfacesOf(TypeElement type) {
    List<String> unresolved = new ArrayList<>();
    Set<TypeElement> visited = new HashSet<>();
    List<TypeElement> pending = new ArrayList<>(List.of(type));
    while (!pending.isEmpty()) {
      TypeElement current = pending.removeFirst();
      if (visited.add(current)) {
        for (TypeMirror parent : current.getInterfaces()) {
          if (parent.getKind() == TypeKind.DECLARED) {
            pending.add((TypeElement) ((DeclaredType) parent).asElement());
          } else if (parent.getKind() == TypeKind.ERROR) {
            unresolved.add(((DeclaredType) parent).asElement().toString());
          }
        }
      }
    }
    return unresolved.stream();
  }

  /** The client and every interface in its hierarchy read from source. */
  private List<TypeElement> sourceHierarchyOf(TypeElement iface) {
    List<TypeElement> found = new ArrayList<>();
    List<TypeElement> pending = new ArrayList<>(List.of(iface));
    while (!pending.isEmpty()) {
      TypeElement current = pending.removeFirst();
      if (!found.contains(current) && !classFiles().readFrom(current)) {
        found.add(current);
        for (TypeMirror parent : current.getInterfaces()) {
          if (parent.getKind() == TypeKind.DECLARED) {
            pending.add((TypeElement) ((DeclaredType) parent).asElement());
          }
        }
      }
    }
    return found;
  }

  /** "it" for the annotation itself, since the refusal has just named it, or else the name. */
  private static String itOr(String named, AnnotationMirror annotation) {
    return named.equals(String.valueOf(annotation.getAnnotationType())) ? "it" : named;
  }

  /** An annotation's type as a diagnostic names it, such as {@code @com.upstream.LocalGet}. */
  private static String nameOf(AnnotationMirror annotation) {
    return "@" + annotation.getAnnotationType();
  }

  private static String nativeNameOf(TypeElement iface) {
    return iface.getSimpleName() + "HttpExchange";
  }

  /** The type an element is declared in, or the type itself. */
  private static TypeElement declaringTypeOf(Element element) {
    return element instanceof TypeElement type ? type : (TypeElement) element.getEnclosingElement();
  }

  /** The compilation's class files, as {@link ClassFiles} reads them. */
  private ClassFiles classFiles() {
    return new ClassFiles(processingEnv.getElementUtils());
  }

  /**
   * True for a name-binding annotation that names no binding and is not an <em>aggregate</em>
   * binder. One names none where it writes neither {@code value} nor {@code name}, or writes them
   * empty, which Spring reads as no name and binds by the parameter's. A {@code Map}/{@code
   * MultiValueMap} parameter on {@code @RequestParam}/{@code @RequestHeader}/{@code @CookieValue},
   * or a {@code Map} on {@code @PathVariable}, binds <em>every</em> name/value pair when left
   * unnamed — injecting a name would change it into a single-entry binder, so it must stay unnamed.
   * The parameter's type is the one the client has, so a base's {@code P} that the client binds to
   * a {@code Map} is an aggregate binder too.
   */
  private boolean needsExplicitName(AnnotationMirror mirror, ClientMethod.Parameter parameter) {
    String annotationType = mirror.getAnnotationType().toString();
    if (!NAME_BINDING_ANNOTATIONS.contains(annotationType)) {
      return false;
    }
    if (isMapType(ClientMethods.erasureOf(parameter, processingEnv.getTypeUtils()))) {
      return false;
    }
    return mirror.getElementValues().entrySet().stream()
        .noneMatch(
            member ->
                BINDING_NAME_MEMBERS.contains(member.getKey().getSimpleName().toString())
                    && !"".equals(member.getValue().getValue()));
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
    List<? extends AnnotationMirror> methodAnnotations =
        method.element().getAnnotationMirrors().stream()
            .filter(mirror -> !isOverride(mirror) && !isStatusOverride(mirror))
            .filter(mirror -> isCopied(mirror, method.element(), packageName))
            .toList();
    for (AnnotationMirror mirror : methodAnnotations) {
      parts.put("the method's " + labelOf(mirror, methodAnnotations), normalised(mirror, null));
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
    Elements elements = processingEnv.getElementUtils();
    parts.put(
        "the method's return type",
        positional(method.returnTypeName(packageName, elements), ownVariables));
    List<TypeVariableName> typeVariables = method.typeVariableNames(packageName, elements);
    for (int index = 0; index < typeVariables.size(); index++) {
      TypeVariableName variable = typeVariables.get(index);
      parts.put(
          "the method's type parameter " + (index + 1),
          positionalParameter(variable, ownVariables));
    }
    List<ClientMethod.Parameter> parameters = method.parameters();
    for (int index = 0; index < parameters.size(); index++) {
      ClientMethod.Parameter parameter = parameters.get(index);
      String label = "parameter " + (index + 1) + "'s ";
      parts.put(
          label + "type", positional(parameter.typeName(packageName, elements), ownVariables));
      List<? extends AnnotationMirror> parameterAnnotations =
          parameter.element().getAnnotationMirrors().stream()
              .filter(mirror -> isCopied(mirror, method.element(), packageName))
              .toList();
      for (AnnotationMirror mirror : parameterAnnotations) {
        parts.put(
            label + labelOf(mirror, parameterAnnotations),
            normalised(mirror, needsExplicitName(mirror, parameter) ? parameter.name() : null));
      }
    }
    return parts;
  }

  /**
   * Whether the generated client copies an annotation: one its package can write, or one Spring
   * reads, which the processor refuses where it cannot be written. One Spring never reads, left off
   * where it cannot be written, plays no part in what the client writes.
   */
  private boolean isCopied(AnnotationMirror annotation, Element writer, String packageName) {
    return seenBySpring(annotation, writer)
        || AnnotationSpecs.writabilityIn(annotation, packageName, processingEnv.getElementUtils())
            instanceof AnnotationSpecs.Writable;
  }

  /**
   * An annotation as a part's label names it: by its simple name, or by its qualified name where
   * another the client copies from the same declaration shares the simple name, so neither takes
   * the other's place. One left off is not counted, or it would label the same copy differently on
   * two declarations.
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
   * A type the generated client writes, for comparison, with each of the method's own type
   * variables written as its position, so a renamed variable reads the same. The type is walked,
   * not its text rewritten, so only a variable is renumbered: a name that merely matches one, in an
   * annotation or a qualified class name, is left as it is. Neither can hold a variable, as no
   * annotation can name one and a class name is a class's alone.
   */
  private static String positional(TypeName type, List<String> ownVariables) {
    String annotations =
        type.annotations().stream()
            .map(annotation -> annotation + " ")
            .collect(Collectors.joining());
    return switch (type) {
      case TypeVariableName variable -> annotations + positionOf(variable, ownVariables);
      case ParameterizedTypeName parameterised ->
          annotations
              + (parameterised.enclosingType() == null
                  ? parameterised.rawType().toString()
                  : positional(parameterised.enclosingType(), ownVariables)
                      + "."
                      + parameterised.rawType().simpleName())
              + parameterised.typeArguments().stream()
                  .map(argument -> positional(argument, ownVariables))
                  .collect(Collectors.joining(", ", "<", ">"));
      case WildcardTypeName wildcard ->
          annotations
              + (wildcard.lowerBounds().isEmpty()
                  ? "? extends " + positional(wildcard.upperBounds().getFirst(), ownVariables)
                  : "? super " + positional(wildcard.lowerBounds().getFirst(), ownVariables));
      case ArrayTypeName array ->
          positional(array.componentType(), ownVariables) + " " + annotations + "[]";
      // A class or a primitive, annotations included, holds no variable.
      default -> type.toString();
    };
  }

  /**
   * A type parameter of the method's own, for comparison: its position, its annotations and its
   * bounds, each bound as {@link #positional(TypeName, List)} writes it.
   */
  private static String positionalParameter(TypeVariableName parameter, List<String> ownVariables) {
    String bounds =
        parameter.bounds().stream()
            .map(bound -> positional(bound, ownVariables))
            .collect(Collectors.joining(" & "));
    return parameter.annotations().stream()
            .map(annotation -> annotation + " ")
            .collect(Collectors.joining())
        + positionOf(parameter, ownVariables)
        + (bounds.isEmpty() ? "" : " extends " + bounds);
  }

  private static String positionOf(TypeVariableName variable, List<String> ownVariables) {
    int index = ownVariables.indexOf(variable.name());
    return index < 0 ? variable.name() : "#" + (index + 1);
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
    if (onlyVoidFits(success)) {
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

  /**
   * Whether only {@code Void} fits a success type: {@code Void} itself, a wildcard whose upper
   * bound only {@code Void} fits, or a type variable whose bound only {@code Void} fits.
   */
  private static boolean onlyVoidFits(TypeMirror type) {
    return switch (type.getKind()) {
      case WILDCARD -> {
        TypeMirror bound = ((WildcardType) type).getExtendsBound();
        yield bound != null && onlyVoidFits(bound);
      }
      case TYPEVAR -> onlyVoidFits(((TypeVariable) type).getUpperBound());
      case INTERSECTION ->
          ((IntersectionType) type)
              .getBounds().stream().anyMatch(HkjHttpClientProcessor::onlyVoidFits);
      default -> isVoid(type);
    };
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
    return type.getKind() == TypeKind.ERROR && classFiles().readFrom(writer);
  }

  private static String notOnClasspath(String annotation, TypeMirror type) {
    return annotation + " error type " + type + NOT_ON_CLASSPATH + ".";
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

  /**
   * What naming the client draws: the client, or a class it is nested in, deprecated. The facade
   * implements it and the configuration names it.
   */
  private Set<Lint> drawnByClient(TypeElement iface) {
    return Deprecations.drawnBy(
        Deprecations.enclosingTypes(iface), processingEnv.getElementUtils());
  }

  /**
   * What redeclaring the client's type parameters draws: a deprecated class among their bounds. The
   * facade and the native interface both declare them.
   */
  private Set<Lint> drawnByTypeParameters(TypeElement iface) {
    return Deprecations.namedBy(
        iface.getTypeParameters().stream().flatMap(parameter -> parameter.getBounds().stream()),
        processingEnv.getElementUtils());
  }

  /** What writing each type as a class literal draws, as {@link #rawClass} writes it. */
  private Set<Lint> drawnByClassLiterals(Stream<? extends TypeMirror> types) {
    Types typeUtils = processingEnv.getTypeUtils();
    return Deprecations.namedBy(types.map(typeUtils::erasure), processingEnv.getElementUtils());
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

  /**
   * Writes one generated type, which names the client it was generated from as its only originating
   * element: Gradle registers this processor as isolating, and recompiling the client is then what
   * regenerates the type.
   */
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
