// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.palantir.javapoet.ClassName;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import org.higherkindedj.optics.processing.util.Diagnostics;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * Warns where a spec, or an interface a spec extends, holds a generated Impl in a constant: the
 * MapStruct idiom {@code CustomerMappingImpl MAPPER = CustomerMappingImpl.INSTANCE;}.
 *
 * <p>Such a constant can read {@code null}. The Impl implements the interface, and initialising a
 * class first initialises each interface it implements that declares an instance method with a
 * body, such as a {@code default} leaf. A program that uses the Impl first therefore initialises
 * the interface before {@code INSTANCE} is assigned, and the constant keeps the {@code null} it
 * read. An interface without such a method is safe until one is added, so the warning does not ask
 * whether it has one.
 *
 * <p>Only the field's type is read, never its initialiser. A constant's type names the Impl in one
 * of two forms: resolved, once the Impl is written, or, in the round that writes it, an unresolved
 * name, which counts where it is the name of the Impl of a spec this compilation has met. So a
 * constant typed as the spec, which reads {@code null} the same way, draws no warning, nor does one
 * typed as a surface, whose initialiser fails the Impl's initialisation instead.
 *
 * <p>{@code @SuppressWarnings("impl-constant")} on the field, or on a declaration enclosing it,
 * keeps a deliberate constant quiet, as {@code hkj-checker} honours its own check ids.
 */
final class ImplConstants {

  /** The {@code @SuppressWarnings} value that keeps a deliberate constant quiet. */
  static final String SUPPRESSION = "impl-constant";

  private static final String TAG = "@GenerateMapping";

  /** The constants already reported, so a mix-in several specs extend is reported once. */
  private final Set<String> reported = new HashSet<>();

  /**
   * Warns at each constant {@code spec} or an interface it extends declares in source whose type is
   * a generated Impl. A constant compiled into a dependency was reported where it was compiled.
   *
   * @param env the processing environment
   * @param spec the spec about to be processed
   * @param specs every {@code @GenerateMapping} interface this compilation has met
   */
  void check(ProcessingEnvironment env, TypeElement spec, List<TypeElement> specs) {
    Set<ClassName> impls =
        specs.stream().map(MappingProcessor::implClassName).collect(Collectors.toSet());
    for (TypeElement declaring : declaringInterfaces(spec)) {
      if (!ProcessorUtils.compiledFromSource(env.getElementUtils(), declaring)) {
        continue;
      }
      for (VariableElement field : ElementFilter.fieldsIn(declaring.getEnclosedElements())) {
        String impl = implHeld(field.asType(), impls);
        if (impl != null
            && !suppressed(field)
            && reported.add(declaring.getQualifiedName() + "." + field.getSimpleName())) {
          Diagnostics.warning(
              env.getMessager(),
              field,
              TAG,
              "'"
                  + declaring.getSimpleName()
                  + "."
                  + field.getSimpleName()
                  + "' holds the generated "
                  + impl
                  + " in a constant, which can read null.",
              "Initialising "
                  + impl
                  + " first initialises '"
                  + declaring.getSimpleName()
                  + "' once it declares a default or private method, such as a leaf, so a program"
                  + " that uses the Impl first leaves the constant null for good.",
              "Keep the Impl in the calling code: a local, or a private static final field on the"
                  + " class that calls it. To keep this constant anyway, annotate it"
                  + " @SuppressWarnings(\""
                  + SUPPRESSION
                  + "\").");
        }
      }
    }
  }

  /** {@code spec} and every interface it extends, each once, the spec first. */
  private static Set<TypeElement> declaringInterfaces(TypeElement spec) {
    Set<TypeElement> interfaces = new LinkedHashSet<>();
    collect(spec, interfaces);
    return interfaces;
  }

  private static void collect(TypeElement type, Set<TypeElement> interfaces) {
    if (!interfaces.add(type)) {
      return;
    }
    // A superinterface mirror is a declared or an error type, both DeclaredType.
    type.getInterfaces()
        .forEach(parent -> collect((TypeElement) ((DeclaredType) parent).asElement(), interfaces));
  }

  /**
   * The simple name of the generated Impl {@code type} is, or null. A resolved class is one when it
   * implements a spec whose Impl it is named as; an unresolved name is one when it names the Impl
   * of a spec in {@code impls}, qualified as written or, left simple, by its simple name alone.
   */
  private static String implHeld(TypeMirror type, Set<ClassName> impls) {
    if (type.getKind() == TypeKind.ERROR) {
      String written =
          ((TypeElement) ((DeclaredType) type).asElement()).getQualifiedName().toString();
      boolean simple = written.indexOf('.') < 0;
      return impls.stream()
          .filter(impl -> written.equals(simple ? impl.simpleName() : impl.canonicalName()))
          .map(ClassName::simpleName)
          .findFirst()
          .orElse(null);
    }
    if (type.getKind() != TypeKind.DECLARED) {
      return null;
    }
    TypeElement held = (TypeElement) ((DeclaredType) type).asElement();
    ClassName name = ClassName.get(held);
    boolean isImpl =
        held.getInterfaces().stream()
            .map(parent -> (TypeElement) ((DeclaredType) parent).asElement())
            .anyMatch(
                parent ->
                    (MappingProcessor.findMappingSpec(parent) != null
                            || MappingProcessor.findUpdateSpec(parent) != null)
                        && MappingProcessor.implClassName(parent).equals(name));
    return isImpl ? name.simpleName() : null;
  }

  /**
   * Whether {@code @SuppressWarnings("impl-constant")} sits on {@code field} or on a declaration
   * enclosing it. {@code "all"} is not honoured, since it is the compiler's own lint switch.
   */
  private static boolean suppressed(Element field) {
    // A field's enclosing declarations end at its package: a top-level type's enclosing element.
    for (Element element = field;
        element.getKind() != ElementKind.PACKAGE;
        element = element.getEnclosingElement()) {
      SuppressWarnings suppression = element.getAnnotation(SuppressWarnings.class);
      if (suppression != null && Arrays.asList(suppression.value()).contains(SUPPRESSION)) {
        return true;
      }
    }
    return false;
  }
}
