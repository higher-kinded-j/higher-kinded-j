// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.*;
import java.io.IOException;
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
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateFocus;

/**
 * Annotation processor for {@link GenerateFocus} that generates Focus DSL utility classes.
 *
 * <p>For each annotated record, this processor generates a companion class with the suffix "Focus"
 * containing static methods that return {@code FocusPath} instances for each record component.
 *
 * <h2>Generated Code Structure</h2>
 *
 * <p>For a record like:
 *
 * <pre>{@code
 * @GenerateFocus
 * record User(String name, Address address) {}
 * }</pre>
 *
 * <p>The processor generates:
 *
 * <pre>{@code
 * @Generated
 * public final class UserFocus {
 *     private UserFocus() {}
 *
 *     public static FocusPath<User, String> name() {
 *         return FocusPath.of(Lens.of(User::name, (source, newValue) -> new User(newValue, source.address())));
 *     }
 *
 *     public static FocusPath<User, Address> address() {
 *         return FocusPath.of(Lens.of(User::address, (source, newValue) -> new User(source.name(), newValue)));
 *     }
 * }
 * }</pre>
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("org.higherkindedj.optics.annotations.GenerateFocus")
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public class FocusProcessor extends AbstractProcessor {

  /** ClassName for FocusPath (in hkj-core, not available at processor compile time). */
  private static final ClassName FOCUS_PATH_CLASS =
      ClassName.get("org.higherkindedj.optics.focus", "FocusPath");

  /** ClassName for AffinePath (for Optional field widening). */
  private static final ClassName AFFINE_PATH_CLASS =
      ClassName.get("org.higherkindedj.optics.focus", "AffinePath");

  /** ClassName for TraversalPath (for collection field widening). */
  private static final ClassName TRAVERSAL_PATH_CLASS =
      ClassName.get("org.higherkindedj.optics.focus", "TraversalPath");

  /** Optional types that widen to AffinePath via .some(). */
  private static final Set<String> OPTIONAL_TYPES =
      Set.of("java.util.Optional", "org.higherkindedj.hkt.maybe.Maybe");

  /** Collection types that widen to TraversalPath via .each(). */
  private static final Set<String> COLLECTION_TYPES =
      Set.of("java.util.List", "java.util.Set", "java.util.Collection");

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    // First pass: collect all @GenerateFocus annotated types for navigator resolution
    Set<String> navigableTypes = new HashSet<>();
    Set<? extends Element> allAnnotated = roundEnv.getElementsAnnotatedWith(GenerateFocus.class);
    for (Element element : allAnnotated) {
      if (element.getKind() == ElementKind.RECORD) {
        TypeElement typeElement = (TypeElement) element;
        navigableTypes.add(typeElement.getQualifiedName().toString());
      }
    }

    // Second pass: generate Focus classes
    for (TypeElement annotation : annotations) {
      Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
      for (Element element : annotatedElements) {
        if (element.getKind() != ElementKind.RECORD) {
          error("The @GenerateFocus annotation can only be applied to records.", element);
          continue;
        }
        try {
          generateFocusFile((TypeElement) element, navigableTypes);
        } catch (IOException e) {
          error("Could not generate focus file: " + e.getMessage(), element);
        }
      }
    }
    return true;
  }

  private void generateFocusFile(TypeElement recordElement, Set<String> navigableTypes)
      throws IOException {
    String recordName = recordElement.getSimpleName().toString();
    String defaultPackage =
        processingEnv.getElementUtils().getPackageOf(recordElement).getQualifiedName().toString();

    // Check for annotation attributes
    GenerateFocus annotation = recordElement.getAnnotation(GenerateFocus.class);
    String targetPackage = annotation.targetPackage();
    String packageName = targetPackage.isEmpty() ? defaultPackage : targetPackage;
    boolean generateNavigators = annotation.generateNavigators();
    int maxNavigatorDepth = annotation.maxNavigatorDepth();
    String[] includeFields = annotation.includeFields();
    String[] excludeFields = annotation.excludeFields();

    String focusClassName = recordName + "Focus";

    final ClassName generatedAnnotation =
        ClassName.get("org.higherkindedj.optics.annotations", "Generated");

    String navigatorNote =
        generateNavigators
            ? "\n\n<p>This class includes navigator classes for fluent cross-type navigation."
            : "";

    TypeSpec.Builder focusClassBuilder =
        TypeSpec.classBuilder(focusClassName)
            .addAnnotation(generatedAnnotation)
            .addJavadoc(
                "Generated Focus DSL paths for {@link $T}.\n\n"
                    + "<p>This class provides type-safe navigation paths for accessing and modifying\n"
                    + "fields within {@code $L} instances using the Focus DSL.$L\n\n"
                    + "<p>Do not edit this file; it is automatically generated.",
                ClassName.get(recordElement),
                recordName,
                navigatorNote)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

    List<? extends RecordComponentElement> components = recordElement.getRecordComponents();
    TypeName recordTypeName = getParameterizedTypeName(recordElement);

    // Create navigator generator if enabled
    NavigatorClassGenerator navigatorGenerator = null;
    if (generateNavigators) {
      navigatorGenerator =
          new NavigatorClassGenerator(
              processingEnv, navigableTypes, maxNavigatorDepth, includeFields, excludeFields);
    }

    // Generate FocusPath methods for each component
    for (RecordComponentElement component : components) {
      MethodSpec method = null;

      // Try to create a navigator method if navigators are enabled
      if (navigatorGenerator != null) {
        method =
            navigatorGenerator.createNavigatorMethod(
                component, recordElement, components, recordTypeName);
      }

      // Fall back to standard FocusPath method if no navigator method was created
      if (method == null) {
        method = createFocusPathMethod(component, recordElement, components, recordTypeName);
      }

      focusClassBuilder.addMethod(method);
    }

    // Generate navigator inner classes if enabled
    if (navigatorGenerator != null) {
      navigatorGenerator.generateNavigators(focusClassBuilder, recordElement, 0);
    }

    JavaFile javaFile =
        JavaFile.builder(packageName, focusClassBuilder.build())
            .addFileComment("Generated by hkj-optics-processor. Do not edit.")
            .build();

    javaFile.writeTo(processingEnv.getFiler());
  }

  private TypeName getParameterizedTypeName(TypeElement typeElement) {
    List<? extends TypeParameterElement> typeParameters = typeElement.getTypeParameters();
    if (typeParameters.isEmpty()) {
      return ClassName.get(typeElement);
    } else {
      List<TypeVariableName> typeVars = typeParameters.stream().map(TypeVariableName::get).toList();
      return ParameterizedTypeName.get(
          ClassName.get(typeElement), typeVars.toArray(new TypeName[0]));
    }
  }

  private MethodSpec createFocusPathMethod(
      RecordComponentElement component,
      TypeElement recordElement,
      List<? extends RecordComponentElement> allComponents,
      TypeName recordTypeName) {

    String componentName = component.getSimpleName().toString();
    TypeMirror componentType = component.asType();
    TypeName componentTypeName = TypeName.get(componentType);

    // Detect path type widening based on field type and annotations
    PathTypeInfo pathTypeInfo = analyseFieldType(component, componentType);

    // Determine return type and inner type based on widening
    ClassName pathClass = pathTypeInfo.pathClass;
    TypeName innerTypeName =
        pathTypeInfo.innerType != null ? pathTypeInfo.innerType : componentTypeName.box();
    ParameterizedTypeName returnTypeName =
        ParameterizedTypeName.get(pathClass, recordTypeName, innerTypeName);

    String pathDescription = getPathDescription(pathTypeInfo);
    String getMethodName = getPathGetMethod(pathTypeInfo);

    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder(componentName)
            .addJavadoc(
                "Creates a {@link $T} for the {@code $L} field of a {@link $T}.\n\n"
                    + "<p>The returned path can be composed with other optics for deep navigation:\n"
                    + "<pre>{@code\n"
                    + "$L.$L().$L(instance);  // Get the $L value\n"
                    + "$L.$L().set(newValue, instance);  // Set the $L value\n"
                    + "$L.$L().modify(fn, instance);  // Transform the $L value\n"
                    + "}</pre>\n\n"
                    + "@return A non-null {@code $L<$T, $T>}.",
                pathClass,
                componentName,
                recordTypeName,
                recordElement.getSimpleName() + "Focus",
                componentName,
                getMethodName,
                componentName,
                recordElement.getSimpleName() + "Focus",
                componentName,
                componentName,
                recordElement.getSimpleName() + "Focus",
                componentName,
                componentName,
                pathDescription,
                recordTypeName,
                innerTypeName)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(returnTypeName);

    // Add type parameters if the record is generic
    for (TypeParameterElement typeParam : recordElement.getTypeParameters()) {
      methodBuilder.addTypeVariable(TypeVariableName.get(typeParam));
    }

    // Build the constructor arguments for the setter lambda
    String constructorArgs =
        allComponents.stream()
            .map(
                c ->
                    c.getSimpleName().toString().equals(componentName)
                        ? "newValue"
                        : "source." + c.getSimpleName() + "()")
            .collect(Collectors.joining(", "));

    // Generate code based on path type widening
    String baseLens =
        String.format(
            "$T.of($T.of($T::%s, (source, newValue) -> new $T(%s)))",
            componentName, constructorArgs);

    switch (pathTypeInfo.wideningType) {
      case OPTIONAL ->
          methodBuilder.addStatement(
              "return " + baseLens + ".some()",
              FOCUS_PATH_CLASS,
              Lens.class,
              recordTypeName,
              recordTypeName);
      case COLLECTION ->
          methodBuilder.addStatement(
              "return " + baseLens + ".each()",
              FOCUS_PATH_CLASS,
              Lens.class,
              recordTypeName,
              recordTypeName);
      case NULLABLE ->
          methodBuilder.addStatement(
              "return " + baseLens + ".nullable()",
              FOCUS_PATH_CLASS,
              Lens.class,
              recordTypeName,
              recordTypeName);
      default ->
          methodBuilder.addStatement(
              "return " + baseLens, FOCUS_PATH_CLASS, Lens.class, recordTypeName, recordTypeName);
    }

    return methodBuilder.build();
  }

  /** Represents the type of path widening to apply. */
  private enum WideningType {
    NONE,
    OPTIONAL,
    COLLECTION,
    NULLABLE
  }

  /** Holds information about path type analysis. */
  private record PathTypeInfo(ClassName pathClass, TypeName innerType, WideningType wideningType) {}

  /** Analyses a field type and annotations to determine path widening. */
  private PathTypeInfo analyseFieldType(RecordComponentElement component, TypeMirror type) {
    // Check for @Nullable annotation on the field first
    boolean isNullable = NullableAnnotations.hasNullableAnnotation(component);

    if (type.getKind() != TypeKind.DECLARED) {
      // Primitive types cannot be null, but boxed types can
      if (isNullable) {
        return new PathTypeInfo(AFFINE_PATH_CLASS, TypeName.get(type).box(), WideningType.NULLABLE);
      }
      return new PathTypeInfo(FOCUS_PATH_CLASS, null, WideningType.NONE);
    }

    DeclaredType declaredType = (DeclaredType) type;
    TypeElement typeElement = (TypeElement) declaredType.asElement();
    String qualifiedName = typeElement.getQualifiedName().toString();

    // Check for Optional types (takes precedence over @Nullable)
    if (OPTIONAL_TYPES.contains(qualifiedName)) {
      TypeName innerType = extractTypeArgument(declaredType);
      return new PathTypeInfo(AFFINE_PATH_CLASS, innerType, WideningType.OPTIONAL);
    }

    // Check for Collection types (takes precedence over @Nullable)
    if (COLLECTION_TYPES.contains(qualifiedName)) {
      TypeName innerType = extractTypeArgument(declaredType);
      return new PathTypeInfo(TRAVERSAL_PATH_CLASS, innerType, WideningType.COLLECTION);
    }

    // Check for @Nullable annotation
    if (isNullable) {
      return new PathTypeInfo(AFFINE_PATH_CLASS, TypeName.get(type).box(), WideningType.NULLABLE);
    }

    return new PathTypeInfo(FOCUS_PATH_CLASS, null, WideningType.NONE);
  }

  /** Extracts the first type argument from a parameterised type. */
  private TypeName extractTypeArgument(DeclaredType declaredType) {
    List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
    if (typeArgs.isEmpty()) {
      return ClassName.get(Object.class);
    }
    return TypeName.get(typeArgs.get(0)).box();
  }

  /** Gets the path class description for Javadoc. */
  private String getPathDescription(PathTypeInfo info) {
    return switch (info.wideningType) {
      case OPTIONAL, NULLABLE -> "AffinePath";
      case COLLECTION -> "TraversalPath";
      default -> "FocusPath";
    };
  }

  /** Gets the appropriate get method name for Javadoc examples. */
  private String getPathGetMethod(PathTypeInfo info) {
    return switch (info.wideningType) {
      case OPTIONAL, NULLABLE -> "getOptional";
      case COLLECTION -> "getAll";
      default -> "get";
    };
  }

  private void error(String msg, Element e) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, e);
  }
}
