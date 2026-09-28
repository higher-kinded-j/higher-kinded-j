// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.palantir.javapoet.ClassName;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import org.higherkindedj.optics.processing.util.Diagnostics;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * Warns where a spec, or an interface a spec extends, holds the spec's generated Impl in a
 * constant: the MapStruct idiom {@code CustomerMappingImpl MAPPER = CustomerMappingImpl.INSTANCE;}.
 *
 * <p>Such a constant can read {@code null}. The Impl implements every interface its spec extends,
 * and initialising a class first initialises each interface it implements that declares an instance
 * method with a body, such as a {@code default} leaf. A program that uses the Impl first therefore
 * initialises the interface holding the constant before {@code INSTANCE} is assigned, and the
 * constant keeps the {@code null} it read. An interface without such a method is safe until one is
 * added, so the warning does not ask whether it has one.
 *
 * <p>Only the spec's own Impl is looked for. Another spec's Impl is initialised on its own, and
 * reads {@code null} in a constant only where specs hold each other's, a cycle this does not trace.
 * Only the field's type is read, never its initialiser: it names the Impl resolved, where an
 * earlier build's Impl is on the classpath, or unresolved, in the round that writes it, and an
 * array of the Impl counts. A constant typed as the spec, which reads {@code null} the same way,
 * draws no warning, nor does one typed as a surface, whose initialiser fails the Impl's
 * initialisation instead.
 *
 * <p>{@code @SuppressWarnings("impl-constant")} on the field, or on a declaration enclosing it,
 * keeps a deliberate constant quiet.
 */
final class ImplConstants {

  /** The {@code @SuppressWarnings} value that keeps a deliberate constant quiet. */
  static final String SUPPRESSION = "impl-constant";

  private ImplConstants() {}

  /**
   * Warns at each constant {@code spec}, or an interface it extends, declares whose type is {@code
   * spec}'s Impl. The spec's own declaration is read whatever the compiler says of its file; an
   * interface it extends is read only from source, since one compiled into a dependency carries no
   * suppression and no position to report at. A constant names one Impl, and each spec is checked
   * once, so each constant is reported at most once.
   *
   * @param env the processing environment
   * @param spec the spec about to be processed
   * @param tag the annotation the warning is reported under
   */
  static void check(ProcessingEnvironment env, TypeElement spec, String tag) {
    ClassName impl = MappingProcessor.implClassName(spec);
    for (TypeElement declaring : declaringInterfaces(spec)) {
      if (declaring != spec
          && !ProcessorUtils.compiledFromSource(env.getElementUtils(), declaring)) {
        continue;
      }
      for (VariableElement field : ElementFilter.fieldsIn(declaring.getEnclosedElements())) {
        if (names(field.asType(), impl) && !Diagnostics.suppressed(field, SUPPRESSION)) {
          report(env, tag, declaring, field, impl.simpleName());
        }
      }
    }
  }

  private static void report(
      ProcessingEnvironment env,
      String tag,
      TypeElement declaring,
      VariableElement field,
      String impl) {
    String holder = "'" + declaring.getSimpleName() + "'";
    Diagnostics.warning(
        env.getMessager(),
        field,
        tag,
        "'"
            + declaring.getSimpleName()
            + "."
            + field.getSimpleName()
            + "' holds the generated "
            + impl
            + " in a constant, which can read null.",
        impl
            + " implements "
            + holder
            + ", so once "
            + holder
            + " declares a default method, such as a leaf, or a private instance method, a program"
            + " that uses "
            + impl
            + ".INSTANCE before reading the constant leaves it null for good.",
        "Keep the Impl in the calling code: a local, or a private static final field on the class"
            + " that calls it. To keep this constant anyway, annotate it @SuppressWarnings(\""
            + SUPPRESSION
            + "\").");
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
   * Whether {@code type} names {@code impl}, or is an array of it: a resolved class by its name,
   * and an unresolved one as written, qualified or, left simple, by its simple name, since javac
   * leaves an unresolved simple name unqualified whatever import brought it in.
   */
  private static boolean names(TypeMirror type, ClassName impl) {
    return switch (type.getKind()) {
      case DECLARED -> ClassName.get((TypeElement) ((DeclaredType) type).asElement()).equals(impl);
      case ERROR -> {
        String written =
            ((TypeElement) ((DeclaredType) type).asElement()).getQualifiedName().toString();
        yield written.equals(written.indexOf('.') < 0 ? impl.simpleName() : impl.canonicalName());
      }
      case ARRAY -> names(((ArrayType) type).getComponentType(), impl);
      default -> false;
    };
  }
}
